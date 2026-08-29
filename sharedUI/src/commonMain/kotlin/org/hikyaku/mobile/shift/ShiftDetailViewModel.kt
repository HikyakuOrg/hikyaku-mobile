package org.hikyaku.mobile.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.error_load_route
import hikyaku.sharedui.generated.resources.error_load_routes
import hikyaku.sharedui.generated.resources.shift_error_advance_failed
import hikyaku.sharedui.generated.resources.shift_error_mark_delivered_failed
import hikyaku.sharedui.generated.resources.shift_error_photo_upload_failed
import hikyaku.sharedui.generated.resources.shift_error_start_failed
import io.github.jan.supabase.storage.StorageItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hikyaku.mobile.environment.EnvironmentStore
import org.hikyaku.mobile.environment.model.EnvironmentSource
import org.hikyaku.mobile.geocode.RoutePoiRepository
import org.hikyaku.mobile.geocode.model.RoutePoi
import org.hikyaku.mobile.geocode.model.RoutePoiKind
import org.hikyaku.mobile.routing.RoutingRepository
import org.hikyaku.mobile.shift.departure.DepartureWatcher
import org.hikyaku.mobile.shift.departure.PendingDepartureStore
import org.hikyaku.mobile.shift.departure.model.DepartureActivity
import org.hikyaku.mobile.shift.departure.model.PendingDeparture
import org.hikyaku.mobile.shift.detail.haversineMeters
import org.hikyaku.mobile.shift.detail.model.Customer
import org.hikyaku.mobile.shift.detail.model.RouteStep
import org.hikyaku.mobile.shift.detail.model.VrpRoute
import org.hikyaku.mobile.shift.detail.model.coordKey
import org.hikyaku.mobile.shift.location.LocationProvider
import org.hikyaku.mobile.shift.model.AddablePackage
import org.hikyaku.mobile.shift.scan.ScanDraft
import org.hikyaku.mobile.shift.scan.ScanFeedback
import org.hikyaku.mobile.shift.session.ShiftSessionState
import org.hikyaku.mobile.shift.session.ShiftSessionStore
import org.hikyaku.mobile.shift.session.ShiftStatus
import org.hikyaku.mobile.shift.session.WAREHOUSE_RADIUS_METERS
import org.hikyaku.mobile.shift.session.isWithinAutoStartWindow
import org.hikyaku.mobile.shift.session.model.ShiftPhase
import org.hikyaku.mobile.shift.session.model.ShiftSession
import org.hikyaku.mobile.shift.tracking.ShiftTracker
import org.hikyaku.mobile.tracking.buildTrackingUrl
import org.hikyaku.mobile.tracking.parseScannedTrackingNumber
import org.jetbrains.compose.resources.getString
import org.maplibre.spatialk.geojson.Point
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Margin applied to both ends of the [ShiftDetailViewModel.shiftStartedAt]..[ShiftDetailViewModel.shiftEndedAt]
 * window when querying `driver_location_history`, so a client/server clock skew of a few seconds
 * can't clip the first or last breadcrumb of the shift just driven.
 */
private val ROUTE_HISTORY_PADDING = 10.seconds

/**
 * Drives the shift-details screen: loads the shift's routes, and for the selected route its
 * ordered stops, road-snapped map line, and per-package proof-of-delivery photos.
 *
 * It also runs the shift: starting it moves the first stop to `IN_TRANSIT` and begins streaming
 * the driver's location; delivering a stop rolls the next stop to `IN_TRANSIT`; and the shift is
 * complete once the last stop is delivered and the driver is back at the depot.
 *
 * Every running-shift transition is persisted to [ShiftSessionStore] so the shift survives the
 * app being killed: on Android a foreground service (via [ShiftTracker]) owns background location
 * streaming and the depot/completion check, and on relaunch this ViewModel restores the persisted
 * session and resumes. On iOS/desktop it falls back to streaming in-process.
 */
class ShiftDetailViewModel(
    private val shiftId: String,
    orgSlug: String,
    private val orgId: String = "",
    val orgName: String = "",
    private val repository: ShiftDetailRepository = ShiftDetailRepository(),
    private val routingRepository: RoutingRepository = RoutingRepository(),
    private val routePoiRepository: RoutePoiRepository = RoutePoiRepository(),
    private val actionsRepository: ShiftActionsRepository = ShiftActionsRepository(),
    private val editRepository: ShiftEditRepository = ShiftEditRepository(),
    private val routeHistoryRepository: ShiftRouteHistoryRepository = ShiftRouteHistoryRepository(),
    private val versionRepository: ShiftVersionRepository = ShiftVersionRepository(),
    private val statusCatalog: PackageStatusCatalog = PackageStatusCatalog(),
    private val locationProvider: LocationProvider = LocationProvider(),
    private val sessionStore: ShiftSessionStore = ShiftSessionStore(),
    private val tracker: ShiftTracker = ShiftTracker(),
    private val departureWatcher: DepartureWatcher = DepartureWatcher(),
    private val pendingDepartureStore: PendingDepartureStore = PendingDepartureStore(),
    environmentStore: EnvironmentStore = EnvironmentStore(),
) : ViewModel() {

    /** On a cold-start resume the passed slug may be blank; fall back to the persisted session's. */
    private val orgSlug: String = orgSlug.ifBlank { sessionStore.load()?.orgSlug.orEmpty() }

    private val environmentSource: EnvironmentSource = environmentStore.load()?.source ?: EnvironmentSource.Default

    data class UiState(
        val isLoadingRoutes: Boolean = true,
        val routes: List<VrpRoute> = emptyList(),
        val selectedRouteId: String? = null,
        val routesError: String? = null,
        val isLoadingRoute: Boolean = false,
        val steps: List<RouteStep> = emptyList(),
        /** Polyline to draw: road-snapped preview, or a straight-line fallback. `[lng, lat]`. */
        val routeLine: List<List<Double>> = emptyList(),
        /** POIs within ~2km of [routeLine]: fuel stations for motor vehicles, parking for bicycles. */
        val routePois: List<RoutePoi> = emptyList(),
        /** Which POI category [routePois] holds, so the map can pick the right marker. */
        val poiKind: RoutePoiKind = RoutePoiKind.Fuel,
        val routeError: String? = null,
        /** packageId -> photo StorageItems. */
        val images: Map<String, List<StorageItem>> = emptyMap(),
        /** packageId -> tracking number, resolved separately (not exposed by the route-steps select). */
        val trackingNumbers: Map<String, String> = emptyMap(),
        /**
         * Current status per packageId, last read from `packages_with_latest_status` — seeded from
         * the route load's embedded statuses and refreshed by re-querying just this map. This is
         * the sole source of truth for scan progress, which is why it survives process death with
         * no local persistence: reloading the route re-seeds it from the server.
         */
        val packageStatuses: Map<String, String> = emptyMap(),
        /** `package_status.status` human-readable label per raw enum, resolved best-effort via [PackageStatusCatalog]. */
        val statusLabels: Map<String, String> = emptyMap(),
        /** The scan-packages overlay's state; non-null while it's open. */
        val scan: ScanDraft? = null,
        // --- running a shift ---
        val shiftStarted: Boolean = false,
        /** The package currently being driven to, or null when none is in transit. */
        val inTransitPackageId: String? = null,
        /** Optimistic status per packageId (`IN_TRANSIT`/`DELIVERED`), layered over the fetched value. */
        val statusOverrides: Map<String, String> = emptyMap(),
        /** True once every stop has been delivered. */
        val deliveriesComplete: Boolean = false,
        /** True while the driver is within range of the depot. */
        val atWarehouse: Boolean = false,
        /** True once deliveries are done and the driver is back at the depot. */
        val shiftComplete: Boolean = false,
        /** True while the "route you travelled" map is open. */
        val showTravelledRoute: Boolean = false,
        val isLoadingTravelledRoute: Boolean = false,
        /** The completed shift's breadcrumb trail, oldest point first. */
        val travelledRoute: List<Point> = emptyList(),
        val travelledRouteError: String? = null,
        val isActionInProgress: Boolean = false,
        val actionError: String? = null,
        /** True when the auto-start safety net may be armed for this route (scheduled & within window). */
        val autoStartEligible: Boolean = false,
        // --- trip summary ---
        /** Indices into [routeLine] marking each visited stop; used to split outbound vs return. */
        val routeWayPoints: List<Int> = emptyList(),
        /** Total driving distance of the route in metres, if known. */
        val distanceMeters: Double? = null,
        /** Total driving duration of the route in seconds, if known. */
        val durationSeconds: Double? = null,
        /** The shift's scheduled start (ISO-8601), if scheduled. */
        val startDateTime: String? = null,
        /** Human label of the vehicle running the shift (e.g. "Car"), if known. */
        val vehicleLabel: String? = null,
        /** Recipient per stop, keyed by [RouteStep.id], recovered for ad-hoc shifts. */
        val recipients: Map<Long, Customer> = emptyMap(),
        // --- editing ---
        /** True while the route is in edit mode (stops can be added/removed, start rescheduled). */
        val editMode: Boolean = false,
        /** True while an edit (reschedule/add/remove) is being applied. */
        val isEditing: Boolean = false,
        val editError: String? = null,
        /** The add-stop form, non-null while the add-stop sheet is open. */
        val addStop: AddStopDraft? = null,
        // --- live-shift visibility ---
        /**
         * Set when the 30-second version poll notices the plan changed under the driver. Rendered as
         * a snackbar offering a reload — never acted on automatically, since silently re-routing a
         * driver mid-shift is a safety problem.
         */
        val shiftUpdate: ShiftUpdateNotice? = null,
    ) {
        val jobStops: List<RouteStep> get() = steps.filter { it.isJob }

        /** The route's assigned vehicle id, read off the first job stop that carries one. */
        val vehicleId: String? get() = jobStops.firstNotNullOfOrNull { it.assignment?.vehicle?.id }

        /** The route's assigned vehicle plate, read off the first job stop that carries one. */
        val vehiclePlate: String? get() = jobStops.firstNotNullOfOrNull { it.assignment?.vehicle?.plate }

        /**
         * The first not-yet-delivered job stop, in route order — the one currently being driven to
         * (or, before the shift starts, the next one up). Only this stop gets a Navigate button.
         */
        val currentJobStepId: Long? get() =
            jobStops.firstOrNull { step -> !statusFor(step).equals(ShiftStatus.DELIVERED, ignoreCase = true) }?.id

        /** Whether [step] is a delivered/in-transit/onboard stop that must not be removed. */
        fun isStopLocked(step: RouteStep): Boolean {
            val status = statusFor(step) ?: return false
            return status.equals(ShiftStatus.DELIVERED, ignoreCase = true) ||
                status.equals(ShiftStatus.IN_TRANSIT, ignoreCase = true) ||
                status.equals(ShiftStatus.ONBOARD_FOR_DELIVERY, ignoreCase = true)
        }

        /** Effective status for a package: the optimistic override, else the fetched status. */
        fun statusFor(step: RouteStep): String? = step.assignment?.packageId?.let { effectiveStatus(it) }

        /**
         * Human-readable status for [step]: the DB's own `package_status.status` label once
         * resolved, else a client-side "Onboard For Delivery"-style fallback so the badge never
         * shows a raw enum while [statusLabels] is still loading.
         */
        fun displayStatusFor(step: RouteStep): String? =
            statusFor(step)?.let { raw -> statusLabels[raw] ?: humanizeStatus(raw) }

        /**
         * Effective status for [packageId], layered newest-first: the optimistic override (set by
         * `startShift`/`markDelivered`), then the freshly re-read [packageStatuses] value (kept
         * current by the scan overlay), then the value embedded in the route load. Falling back this
         * way is what makes a resumed shift correct without a network round trip before it can render.
         */
        fun effectiveStatus(packageId: String): String? =
            statusOverrides[packageId]
                ?: packageStatuses[packageId]
                ?: jobStops.firstOrNull { it.assignment?.packageId == packageId }
                    ?.assignment?.packageInfo?.currentStatus

        /** Distinct package ids across the route's job stops (empty for an ad-hoc shift). */
        val packageIds: List<String> get() = jobStops.mapNotNull { it.assignment?.packageId }.distinct()

        /** Packages that are at least loaded onto the van (onboard, in transit, or delivered). */
        val scannedPackageIds: Set<String> get() =
            packageIds.filterTo(mutableSetOf()) { ShiftStatus.satisfiesScan(effectiveStatus(it)) }

        val scannedCount: Int get() = scannedPackageIds.size
        val scanTotal: Int get() = packageIds.size

        /** True once every package is loaded — trivially true for an ad-hoc shift, which has none. */
        val allPackagesScanned: Boolean get() = packageIds.isEmpty() || scannedCount == scanTotal

        /**
         * True once every package on this route is actually DELIVERED, per [effectiveStatus] — unlike
         * [deliveriesComplete] this is derived straight from package status rather than the in-memory
         * session, so it still holds after a fresh [loadRoute] resets `shiftStarted`/`deliveriesComplete`
         * to their defaults (e.g. revisiting an already-completed shift). False for an ad-hoc shift,
         * which has no packages to check.
         */
        val allPackagesDelivered: Boolean get() =
            packageIds.isNotEmpty() && packageIds.all { effectiveStatus(it).equals(ShiftStatus.DELIVERED, ignoreCase = true) }

        /** Job stops still awaiting a scan, in route order — the scan overlay's checklist. */
        val unscannedStops: List<RouteStep> get() =
            jobStops.filter { step -> step.assignment?.packageId?.let { it !in scannedPackageIds } == true }

        /** Recipient for a stop: the resolved ad-hoc recipient, else the embedded assignment's. */
        fun recipientFor(step: RouteStep): Customer? =
            recipients[step.id] ?: step.assignment?.packageInfo?.toCustomer

        /** Tracking number for a stop's package, once resolved by [loadTrackingNumbers]. */
        fun trackingNumberFor(step: RouteStep): String? =
            step.assignment?.packageId?.let { trackingNumbers[it] }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Streams the driver's location in-process (iOS/desktop only); cancelled on completion/clear. */
    private var locationJob: Job? = null

    /** Decides when to poll the shift version and whether an answer is worth reporting. */
    private val versionPoll = ShiftVersionPoll()

    /** The version-poll loop; runs only between [onResumed] and [onPaused]. */
    private var versionPollJob: Job? = null

    /** Dispatcher-set scheduled start (ISO-8601) for this shift, fetched once; gates auto-start. */
    private var scheduledStart: String? = null

    /**
     * Wall-clock start/end of the *running* shift (not [scheduledStart]), captured so a completed
     * shift can look up its own slice of `driver_location_history`. Set on [startShift] (or
     * restored from the persisted session on resume) and on completion; see [buildSession].
     */
    private var shiftStartedAt: Instant? = null
    private var shiftEndedAt: Instant? = null

    // Duplicate-frame guard for the QR scanner: `QrScanner.onCompletion` fires on every decoded
    // frame while a label is held in view, so one physical scan would otherwise fire a burst of
    // inserts without this. Deliberately plain fields, not UiState — this is scan-pipeline
    // bookkeeping, not something the UI renders.
    private var lastScanRaw: String? = null
    private var lastScanAt: Instant? = null

    init {
        loadRoutes()
        observeSessionCompletion()
    }

    fun loadRoutes() {
        _state.value = UiState(isLoadingRoutes = true)
        viewModelScope.launch {
            scheduledStart = repository.fetchShiftSchedule(shiftId).getOrNull()
            _state.value = _state.value.copy(
                autoStartEligible = computeAutoStartEligible(),
                startDateTime = scheduledStart,
            )
        }
        viewModelScope.launch {
            repository.fetchRoutes(shiftId)
                .onSuccess { routes ->
                    // Resume into the persisted route if one is active for this shift.
                    val resume = sessionStore.load()?.takeIf { it.shiftId == shiftId && it.isActive }
                    val targetRouteId = resume?.routeId?.takeIf { id -> routes.any { it.id == id } }
                        ?: routes.firstOrNull()?.id
                    _state.value = _state.value.copy(
                        isLoadingRoutes = false,
                        routes = routes,
                        selectedRouteId = targetRouteId,
                    )
                    targetRouteId?.let { loadRoute(it, resume) }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isLoadingRoutes = false,
                        routesError = it.message ?: getString(Res.string.error_load_routes),
                    )
                }
        }
    }

    fun selectRoute(routeId: String) {
        if (_state.value.selectedRouteId == routeId) return
        // Switching routes abandons any shift running on the previous route, and any auto-start
        // armed for it.
        if (_state.value.shiftStarted) {
            tracker.stop()
            sessionStore.clear()
        }
        departureWatcher.disarm()
        _state.value = _state.value.copy(selectedRouteId = routeId)
        loadRoute(routeId)
    }

    private fun loadRoute(routeId: String, resume: ShiftSession? = null) {
        locationJob?.cancel()
        locationJob = null
        shiftStartedAt = null
        shiftEndedAt = null
        // Whatever the server reports next is what the driver is now looking at, so it must not be
        // announced as a change.
        versionPoll.reset()
        _state.value = _state.value.copy(
            isLoadingRoute = true,
            routeError = null,
            steps = emptyList(),
            routeLine = emptyList(),
            routePois = emptyList(),
            images = emptyMap(),
            trackingNumbers = emptyMap(),
            packageStatuses = emptyMap(),
            scan = null,
            shiftStarted = false,
            inTransitPackageId = null,
            statusOverrides = emptyMap(),
            deliveriesComplete = false,
            atWarehouse = false,
            shiftComplete = false,
            showTravelledRoute = false,
            isLoadingTravelledRoute = false,
            travelledRoute = emptyList(),
            travelledRouteError = null,
            isActionInProgress = false,
            actionError = null,
            autoStartEligible = false,
            routeWayPoints = emptyList(),
            distanceMeters = null,
            durationSeconds = _state.value.routes.firstOrNull { it.id == routeId }?.duration?.toDouble(),
            vehicleLabel = null,
            recipients = emptyMap(),
            shiftUpdate = null,
        )
        viewModelScope.launch {
            repository.fetchRouteSteps(routeId)
                .onSuccess { steps ->
                    // Ignore a response for a route the user already switched away from.
                    if (_state.value.selectedRouteId != routeId) return@onSuccess
                    // Seed scan progress from the statuses embedded in the route load — this is what
                    // lets a resumed shift render "9 of 30" before the scan overlay ever re-queries.
                    val seededStatuses = steps.mapNotNull { step ->
                        val pkgId = step.assignment?.packageId ?: return@mapNotNull null
                        step.assignment?.packageInfo?.currentStatus?.let { pkgId to it }
                    }.toMap()
                    _state.value = _state.value.copy(
                        isLoadingRoute = false,
                        steps = steps,
                        packageStatuses = seededStatuses,
                    )
                    _state.value = _state.value.copy(autoStartEligible = computeAutoStartEligible())
                    loadRouteLine(routeId, steps)
                    loadImages(routeId, steps)
                    loadTrackingNumbers(routeId, steps)
                    loadMeta(routeId, steps)
                    loadStatusLabels(seededStatuses.values)
                    if (resume != null) applyResume(resume, steps)
                    // A watcher armed by a previous build (or before a stop was added) may no longer
                    // reflect an unmet scan gate; disarm it so it can't start the shift blind.
                    if (!_state.value.allPackagesScanned) {
                        val pending = pendingDepartureStore.load()
                        if (pending != null && pending.shiftId == shiftId && pending.routeId == routeId) {
                            departureWatcher.disarm()
                        }
                    }
                }
                .onFailure {
                    if (_state.value.selectedRouteId != routeId) return@onFailure
                    _state.value = _state.value.copy(
                        isLoadingRoute = false,
                        routeError = it.message ?: getString(Res.string.error_load_route),
                    )
                }
        }
    }

    /** Re-applies a persisted session over freshly-fetched (authoritative) steps and resumes tracking. */
    private fun applyResume(session: ShiftSession, steps: List<RouteStep>) {
        shiftStartedAt = session.startedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        _state.value = _state.value.copy(
            shiftStarted = true,
            inTransitPackageId = session.inTransitPackageId,
            statusOverrides = reconcileOverrides(session.statusOverrides, steps),
            deliveriesComplete = session.deliveriesComplete,
        )
        if (tracker.handlesLocationStreaming) tracker.start(session) else startLocationStream()
    }

    /**
     * Backend statuses (already in [steps]) are authoritative; keep an optimistic override only
     * where the backend hasn't already marked the package delivered (i.e. the override may be a
     * status set just before the kill that hasn't round-tripped yet).
     */
    private fun reconcileOverrides(
        overrides: Map<String, String>,
        steps: List<RouteStep>,
    ): Map<String, String> {
        val backend = steps.mapNotNull { step ->
            step.assignment?.packageId?.let { it to step.assignment?.packageInfo?.currentStatus }
        }.toMap()
        return overrides.filter { (pkgId, _) ->
            backend[pkgId]?.equals(ShiftStatus.DELIVERED, ignoreCase = true) != true
        }
    }

    /** Resolves the road-snapped line, falling back to straight segments through the stops. */
    private fun loadRouteLine(routeId: String, steps: List<RouteStep>) {
        val stopCoords = steps.mapNotNull { step ->
            val lng = step.location?.longitude
            val lat = step.location?.latitude
            if (lng != null && lat != null) listOf(lng, lat) else null
        }
        if (stopCoords.size < 2) return

        viewModelScope.launch {
            val profile = steps.firstNotNullOfOrNull {
                it.assignment?.vehicle?.vehicleType?.orsVehicleType
            } ?: "driving-car"
            val preview = routingRepository.fetchRoutePreview(profile, orgSlug, stopCoords).getOrNull()
            val line = preview?.coordinates?.takeIf { it.isNotEmpty() }
                ?: stopCoords // fallback: straight lines through the stops
            if (_state.value.selectedRouteId != routeId) return@launch
            _state.value = _state.value.copy(
                routeLine = line,
                routeWayPoints = preview?.wayPoints.orEmpty(),
                distanceMeters = preview?.summary?.distance ?: _state.value.distanceMeters,
                durationSeconds = preview?.summary?.duration ?: _state.value.durationSeconds,
            )
            // Bicycles look for bicycle parking; motorised vehicles for fuel stations.
            val kind = if (profile.startsWith("cycling", ignoreCase = true)) {
                RoutePoiKind.BicycleParking
            } else {
                RoutePoiKind.Fuel
            }
            loadRoutePois(routeId, line, kind)
        }
    }

    /** Loads the route's POIs ([kind]) within ~2km of the resolved route [line]. */
    private fun loadRoutePois(routeId: String, line: List<List<Double>>, kind: RoutePoiKind) {
        if (line.size < 2) return
        viewModelScope.launch {
            val pois = routePoiRepository.fetchPoisAlong(line, kind).getOrDefault(emptyList())
            if (_state.value.selectedRouteId != routeId) return@launch
            _state.value = _state.value.copy(routePois = pois, poiKind = kind)
        }
    }

    /**
     * Recovers the recipients and vehicle for an ad-hoc shift from the stored optimisation request
     * (its steps carry no package/customer link), matching each recipient onto a job stop by
     * coordinate. Best-effort: a failure or missing mapping simply leaves the fallbacks in place.
     */
    private fun loadMeta(routeId: String, steps: List<RouteStep>) {
        viewModelScope.launch {
            val meta = repository.fetchShiftMeta(shiftId).getOrNull() ?: return@launch
            if (_state.value.selectedRouteId != routeId) return@launch
            val recipients = steps.filter { it.isJob }.mapNotNull { step ->
                val lng = step.location?.longitude ?: return@mapNotNull null
                val lat = step.location?.latitude ?: return@mapNotNull null
                meta.recipientsByCoord[coordKey(lng, lat)]?.let { step.id to it }
            }.toMap()
            _state.value = _state.value.copy(
                recipients = if (recipients.isNotEmpty()) recipients else _state.value.recipients,
                vehicleLabel = meta.vehicleLabel ?: _state.value.vehicleLabel,
            )
        }
    }

    /** Loads proof-of-delivery photos for each package on the route, in parallel. */
    private fun loadImages(routeId: String, steps: List<RouteStep>) {
        val packageIds = steps.mapNotNull { it.assignment?.packageId }.distinct()
        if (packageIds.isEmpty()) return

        viewModelScope.launch {
            val results = coroutineScope {
                packageIds.map { id ->
                    async { id to repository.fetchPackageImages(id).getOrDefault(emptyList()) }
                }.map { it.await() }
            }
            if (_state.value.selectedRouteId != routeId) return@launch
            _state.value = _state.value.copy(
                images = results.filter { it.second.isNotEmpty() }.toMap(),
            )
        }
    }

    /** Resolves tracking numbers for each package on the route, so a package card can be tapped through. */
    private fun loadTrackingNumbers(routeId: String, steps: List<RouteStep>) {
        val packageIds = steps.mapNotNull { it.assignment?.packageId }.distinct()
        if (packageIds.isEmpty()) return

        viewModelScope.launch {
            val result = repository.fetchTrackingNumbers(packageIds)
            val trackingNumbers = result.getOrDefault(emptyMap())
            if (_state.value.selectedRouteId != routeId) return@launch
            _state.value = _state.value.copy(trackingNumbers = trackingNumbers)
        }
    }

    /**
     * Resolves [PackageStatusCatalog] labels for any of [statuses] not already in
     * [UiState.statusLabels]. [PackageStatusCatalog] caches the whole (small, static)
     * `package_status` table process-wide, so in practice this only ever fires one network call.
     */
    private fun loadStatusLabels(statuses: Collection<String>) {
        val missing = statuses.distinct().filterNot { it in _state.value.statusLabels }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val resolved = missing.mapNotNull { enum -> statusCatalog.labelFor(enum)?.let { enum to it } }.toMap()
            if (resolved.isNotEmpty()) {
                _state.value = _state.value.copy(statusLabels = _state.value.statusLabels + resolved)
            }
        }
    }

    /** Public tracking-page URL for [step]'s package, once its tracking number is resolved. */
    fun trackingUrlFor(step: RouteStep): String? {
        if (orgSlug.isBlank()) return null
        return _state.value.trackingNumberFor(step)?.let { buildTrackingUrl(environmentSource, orgSlug, it) }
    }

    // -----------------------------------------------------------------------
    // Load-scanning gate: scan every package's QR code before the shift may start. Progress is
    // server-only (see UiState.packageStatuses' doc) — there is deliberately no local queue, so a
    // failed scan simply stays unscanned until retried.
    // -----------------------------------------------------------------------

    /** Opens the scan overlay and refreshes progress against the server. */
    fun openScanner() {
        _state.value = _state.value.copy(scan = ScanDraft())
        refreshScanStatuses()
    }

    fun closeScanner() {
        _state.value = _state.value.copy(scan = null)
    }

    fun toggleFlashlight() = updateScan { it.copy(flashlightOn = !it.flashlightOn) }

    fun updateManualEntry(text: String) = updateScan { it.copy(manualEntry = text, feedback = null) }

    fun toggleManualEntry() = updateScan { it.copy(manualExpanded = !it.manualExpanded) }

    fun dismissScanFeedback() = updateScan { it.copy(feedback = null) }

    /** Re-submits the package from the current [ScanFeedback.Failed], if any, without re-scanning. */
    fun retryFailedScan() {
        val failed = _state.value.scan?.feedback as? ScanFeedback.Failed ?: return
        submitScan(failed.packageId, failed.trackingNumber)
    }

    fun submitManualEntry() {
        val text = _state.value.scan?.manualEntry.orEmpty()
        if (text.isBlank()) return
        onQrScanned(text)
    }

    /**
     * Re-reads current status for [packageIds] (default: this route's) and merges it into
     * [UiState.packageStatuses]. Called when the overlay opens and after each successful scan; kept
     * narrower than [loadRoute] so refreshing progress never refetches the route, map or photos. A
     * failure leaves progress at its last-known value — under-counting only ever blocks a start, so
     * it's the safe direction to fail in.
     */
    fun refreshScanStatuses(packageIds: List<String> = _state.value.packageIds) {
        if (packageIds.isEmpty()) return
        val routeId = _state.value.selectedRouteId
        updateScan { it.copy(refreshing = true, refreshError = null) }
        viewModelScope.launch {
            repository.fetchCurrentStatuses(packageIds)
                .onSuccess { statuses ->
                    if (_state.value.selectedRouteId != routeId) return@onSuccess
                    _state.value = _state.value.copy(packageStatuses = _state.value.packageStatuses + statuses)
                    updateScan { it.copy(refreshing = false) }
                    // The scan gate is one of computeAutoStartEligible's conditions; re-evaluate so
                    // the watcher can arm the moment the last package is scanned.
                    _state.value = _state.value.copy(autoStartEligible = computeAutoStartEligible())
                    loadStatusLabels(statuses.values)
                }
                .onFailure { error ->
                    if (_state.value.selectedRouteId != routeId) return@onFailure
                    updateScan {
                        it.copy(refreshing = false, refreshError = error.message ?: "Couldn't refresh scan progress.")
                    }
                }
        }
    }

    // -----------------------------------------------------------------------
    // Live-shift visibility: the app has no realtime and no push, so a package the backend assigns
    // to this shift after it was opened is invisible until a refresh. A 30-second poll of the cheap
    // GET /shifts/{id}/version endpoint closes that gap while the screen is on top.
    // -----------------------------------------------------------------------

    /**
     * Starts the version poll. Driven by the screen's `ON_RESUME`, so it costs nothing while the app
     * is backgrounded or the driver is on another screen. Polls once immediately — returning to the
     * screen is exactly when a stale plan matters most — then every [VERSION_POLL_INTERVAL].
     */
    fun onResumed() {
        val pollNow = versionPoll.resume()
        versionPollJob?.cancel()
        versionPollJob = viewModelScope.launch {
            if (pollNow) pollShiftVersion()
            while (true) {
                delay(VERSION_POLL_INTERVAL)
                pollShiftVersion()
            }
        }
    }

    /** Stops the version poll. Driven by the screen's `ON_PAUSE`; also called from [onCleared]. */
    fun onPaused() {
        versionPoll.pause()
        versionPollJob?.cancel()
        versionPollJob = null
    }

    /**
     * One version check. A failure is deliberately silent: this is a background nicety, and a
     * banner about a failed poll would be noise a driver can do nothing with. The route the driver
     * is looking at stays exactly as it is either way.
     */
    private suspend fun pollShiftVersion() {
        if (!versionPoll.shouldPoll(_state.value.shiftStarted)) return
        val version = versionRepository.fetchVersion(orgSlug, shiftId).getOrNull() ?: return
        val notice = versionPoll.observe(version) ?: return
        _state.value = _state.value.copy(shiftUpdate = notice)
    }

    /** Dismisses the update snackbar without reloading — the driver chose to keep their plan. */
    fun dismissShiftUpdate() {
        _state.value = _state.value.copy(shiftUpdate = null)
    }

    /**
     * Accepts the offered reload. Goes through [loadRoutes] rather than [loadRoute] so a shift that
     * is mid-run is restored from its persisted session instead of being reset to "not started".
     */
    fun reloadAfterShiftUpdate() {
        _state.value = _state.value.copy(shiftUpdate = null)
        loadRoutes()
    }

    /**
     * Validates and (if accepted) submits a scanned or manually-entered code. Order matters: each
     * step maps to one of the required rejection cases (unknown code, code for a package that isn't
     * on this shift, or a duplicate scan).
     */
    fun onQrScanned(raw: String) {
        val draft = _state.value.scan ?: return
        val now = Clock.System.now()
        // Duplicate-frame guard: QrScanner.onCompletion fires on every decoded frame while a label
        // is held in view, so ignore a repeat of the same payload within a couple of seconds, and
        // ignore any scan while a previous one is still in flight.
        if (draft.submitting != null) return
        if (raw == lastScanRaw && lastScanAt?.let { now - it < 2.seconds } == true) return
        lastScanRaw = raw
        lastScanAt = now

        val trackingNumber = parseScannedTrackingNumber(raw)
        if (trackingNumber == null) {
            updateScan { it.copy(feedback = ScanFeedback.Unrecognised(raw)) }
            return
        }

        val trackingNumbers = _state.value.trackingNumbers
        if (trackingNumbers.isEmpty()) {
            // loadTrackingNumbers hasn't landed yet; the scanner needs the packageId -> tracking
            // number map to resolve a scan, so every code would otherwise read as unknown.
            updateScan { it.copy(feedback = ScanFeedback.NotReady) }
            return
        }

        val packageId = trackingNumbers.entries
            .firstOrNull { (_, number) -> number.equals(trackingNumber, ignoreCase = true) }
            ?.key
        if (packageId == null) {
            updateScan { it.copy(feedback = ScanFeedback.NotOnThisShift(trackingNumber)) }
            return
        }

        if (ShiftStatus.satisfiesScan(_state.value.effectiveStatus(packageId))) {
            updateScan { it.copy(feedback = ScanFeedback.AlreadyScanned(trackingNumber)) }
            return
        }

        submitScan(packageId, trackingNumber)
    }

    private fun submitScan(packageId: String, trackingNumber: String) {
        updateScan { it.copy(submitting = trackingNumber, feedback = null) }
        viewModelScope.launch {
            actionsRepository.markOnboardForDelivery(packageId)
                .onSuccess {
                    // Server-confirmed, not an optimistic guess.
                    _state.value = _state.value.copy(
                        packageStatuses = _state.value.packageStatuses + (packageId to ShiftStatus.ONBOARD_FOR_DELIVERY),
                    )
                    if (_state.value.allPackagesScanned) {
                        // Last package just cleared the gate — nothing left to scan, so close rather
                        // than show a feedback banner no one's there to dismiss.
                        closeScanner()
                    } else {
                        updateScan {
                            it.copy(
                                submitting = null,
                                feedback = ScanFeedback.Accepted(trackingNumber),
                                manualEntry = "",
                            )
                        }
                    }
                    // Non-blocking confirm read, in case the package concurrently moved further on.
                    refreshScanStatuses(listOf(packageId))
                }
                .onFailure { error ->
                    // The package stays unscanned — no local queue, so it must be retried.
                    updateScan {
                        it.copy(
                            submitting = null,
                            feedback = ScanFeedback.Failed(
                                trackingNumber = trackingNumber,
                                packageId = packageId,
                                message = error.message ?: "Couldn't record $trackingNumber.",
                            ),
                        )
                    }
                }
        }
    }

    /** No-ops when the scan overlay isn't open, so a stray async update can't resurrect it. */
    private fun updateScan(transform: (ScanDraft) -> ScanDraft) {
        val current = _state.value.scan ?: return
        _state.value = _state.value.copy(scan = transform(current))
    }

    /**
     * Starts the shift and begins tracking. For a package-backed shift this also advances the first
     * stop to `IN_TRANSIT`; ad-hoc (personal-org) shifts have no packages — their route steps carry
     * a null `package_id` (see [ShiftDetailRepository.fetchShiftMeta]) — so there is no package
     * status to set and the shift simply starts streaming location.
     */
    fun startShift() {
        val current = _state.value
        if (current.shiftStarted || current.isActionInProgress) return
        if (current.jobStops.isEmpty()) return
        // Defensive: the UI only offers Start once every package is scanned, but the VM contract
        // should hold on its own too.
        if (!current.allPackagesScanned) return
        // Null for an ad-hoc shift, whose stops carry no package to mark in transit.
        val firstPackageId = current.jobStops.firstNotNullOfOrNull { it.assignment?.packageId }

        // A manual start supersedes the auto-start safety net.
        departureWatcher.disarm()
        _state.value = current.copy(isActionInProgress = true, actionError = null, autoStartEligible = false)
        viewModelScope.launch {
            // Advance the first stop for package-backed shifts; ad-hoc shifts have nothing to mark.
            val started = if (firstPackageId != null) {
                actionsRepository.markInTransit(firstPackageId)
            } else {
                Result.success(Unit)
            }
            started
                .onSuccess {
                    shiftStartedAt = Clock.System.now()
                    val overrides = firstPackageId
                        ?.let { _state.value.statusOverrides + (it to ShiftStatus.IN_TRANSIT) }
                        ?: _state.value.statusOverrides
                    _state.value = _state.value.copy(
                        isActionInProgress = false,
                        shiftStarted = true,
                        inTransitPackageId = firstPackageId,
                        statusOverrides = overrides,
                    )
                    val session = buildSession(
                        phase = ShiftPhase.IN_PROGRESS,
                        inTransit = firstPackageId,
                        overrides = overrides,
                        deliveriesComplete = false,
                    )
                    sessionStore.save(session)
                    if (tracker.handlesLocationStreaming) tracker.start(session) else startLocationStream()
                }
                .onFailure { fail(getString(Res.string.shift_error_start_failed), it) }
        }
    }

    /**
     * Marks [packageId] delivered (optionally attaching [photoBytes] as proof) and rolls the next
     * undelivered stop to in transit. When no stop remains, deliveries are complete.
     */
    fun markDelivered(packageId: String, photoBytes: ByteArray? = null) {
        val current = _state.value
        if (current.isActionInProgress) return
        _state.value = current.copy(isActionInProgress = true, actionError = null)

        viewModelScope.launch {
            if (photoBytes != null) {
                // A failed photo upload shouldn't block the delivery; surface it but continue.
                actionsRepository.uploadProofPhoto(packageId, photoBytes)
                    .onFailure { _state.value = _state.value.copy(actionError = it.message ?: getString(Res.string.shift_error_photo_upload_failed)) }
            }
            actionsRepository.markDelivered(packageId)
                .onSuccess {
                    var overrides = _state.value.statusOverrides + (packageId to ShiftStatus.DELIVERED)
                    val next = nextPackageAfter(packageId)
                    if (next != null) {
                        actionsRepository.markInTransit(next)
                            .onFailure { _state.value = _state.value.copy(actionError = it.message ?: getString(Res.string.shift_error_advance_failed)) }
                        overrides = overrides + (next to ShiftStatus.IN_TRANSIT)
                    }
                    val deliveriesComplete = next == null
                    _state.value = _state.value.copy(
                        isActionInProgress = false,
                        statusOverrides = overrides,
                        inTransitPackageId = next,
                        deliveriesComplete = deliveriesComplete,
                    )
                    sessionStore.save(
                        buildSession(
                            phase = if (deliveriesComplete) ShiftPhase.RETURNING_TO_DEPOT else ShiftPhase.IN_PROGRESS,
                            inTransit = next,
                            overrides = overrides,
                            deliveriesComplete = deliveriesComplete,
                        ),
                    )
                    recomputeCompletion()
                }
                .onFailure { fail(getString(Res.string.shift_error_mark_delivered_failed), it) }
        }
    }

    fun clearActionError() {
        _state.value = _state.value.copy(actionError = null)
    }

    // -----------------------------------------------------------------------
    // Editing: reschedule, remove stop, add stop.
    // In-place edits mirroring the web dashboard's route adjustment; only allowed before the shift
    // has started. Each edit reloads the route so the map, stop list and recipients stay in sync.
    // -----------------------------------------------------------------------

    fun toggleEditMode() {
        val s = _state.value
        _state.value = s.copy(editMode = !s.editMode, editError = null, addStop = null)
    }

    fun clearEditError() {
        _state.value = _state.value.copy(editError = null)
    }

    /** Reschedules the shift's start to [isoStart] (ISO-8601). */
    fun reschedule(isoStart: String) {
        if (_state.value.isEditing) return
        _state.value = _state.value.copy(isEditing = true, editError = null)
        viewModelScope.launch {
            editRepository.reschedule(shiftId, isoStart)
                .onSuccess {
                    scheduledStart = isoStart
                    _state.value = _state.value.copy(
                        isEditing = false,
                        startDateTime = isoStart,
                        autoStartEligible = computeAutoStartEligible(),
                    )
                }
                .onFailure { editFailed(it) }
        }
    }

    /** Removes [step] from the route. No-op for a delivered/in-transit (locked) stop. */
    fun removeStop(step: RouteStep) {
        val s = _state.value
        if (s.isEditing || s.isStopLocked(step)) return
        val routeId = s.selectedRouteId ?: return
        _state.value = s.copy(isEditing = true, editError = null)
        viewModelScope.launch {
            editRepository.removeStop(routeId, step, s.steps)
                .onSuccess { reloadAfterEdit(routeId) }
                .onFailure { editFailed(it) }
        }
    }

    // ----- Add-stop form (picks from the org's unassigned packages) -----

    fun openAddStop() {
        _state.value = _state.value.copy(addStop = AddStopDraft())
        viewModelScope.launch {
            editRepository.fetchAddablePackages(orgId)
                .onSuccess { packages -> updateAddStop { it.copy(loading = false, packages = packages) } }
                .onFailure { error ->
                    updateAddStop { it.copy(loading = false, error = error.message ?: "Couldn't load packages.") }
                }
        }
    }

    fun closeAddStop() {
        _state.value = _state.value.copy(addStop = null)
    }

    fun selectAddStopPackage(packageId: String) =
        updateAddStop { it.copy(selectedPackageId = packageId, error = null) }

    /** Inserts the selected package as a new stop on the route. */
    fun confirmAddStop() {
        val s = _state.value
        val draft = s.addStop ?: return
        if (draft.submitting) return
        val routeId = s.selectedRouteId ?: return
        val pkg = draft.packages.firstOrNull { it.packageId == draft.selectedPackageId }
        if (pkg == null) {
            updateAddStop { it.copy(error = "Pick a package to add.") }
            return
        }
        updateAddStop { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            editRepository.addStop(
                shiftId = shiftId,
                routeId = routeId,
                packageId = pkg.packageId,
                customerId = pkg.receiverCustomerId,
                longitude = pkg.longitude,
                latitude = pkg.latitude,
                allSteps = _state.value.steps,
            )
                .onSuccess {
                    _state.value = _state.value.copy(addStop = null)
                    reloadAfterEdit(routeId)
                }
                .onFailure { error ->
                    updateAddStop { it.copy(submitting = false, error = error.message ?: "Couldn't add the stop.") }
                }
        }
    }

    private fun updateAddStop(transform: (AddStopDraft) -> AddStopDraft) {
        val current = _state.value.addStop ?: return
        _state.value = _state.value.copy(addStop = transform(current))
    }

    /** Re-fetches the route after an edit so steps, line and recipients reflect the change. */
    private fun reloadAfterEdit(routeId: String) {
        _state.value = _state.value.copy(isEditing = false)
        loadRoute(routeId)
    }

    private fun editFailed(error: Throwable) {
        _state.value = _state.value.copy(
            isEditing = false,
            editError = error.message ?: "The edit couldn't be applied.",
        )
    }

    /** The next job stop's package after [packageId] in route order, or null if it's the last. */
    private fun nextPackageAfter(packageId: String): String? {
        val ordered = _state.value.jobStops.mapNotNull { it.assignment?.packageId }
        val index = ordered.indexOf(packageId)
        return if (index >= 0) ordered.getOrNull(index + 1) else null
    }

    /** Builds a [ShiftSession] snapshot of the current shift for persistence. */
    private fun buildSession(
        phase: ShiftPhase,
        inTransit: String?,
        overrides: Map<String, String>,
        deliveriesComplete: Boolean,
    ): ShiftSession {
        val depot = depotLatLng()
        return ShiftSession(
            shiftId = shiftId,
            routeId = _state.value.selectedRouteId.orEmpty(),
            orgSlug = orgSlug,
            phase = phase,
            inTransitPackageId = inTransit,
            statusOverrides = overrides,
            deliveriesComplete = deliveriesComplete,
            depotLat = depot?.first,
            depotLng = depot?.second,
            startedAt = shiftStartedAt?.toString(),
            endedAt = shiftEndedAt?.toString(),
        )
    }

    /** In-process location streaming for platforms where [ShiftTracker] doesn't (iOS/desktop). */
    private fun startLocationStream() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationProvider.locationUpdates().collect { loc ->
                actionsRepository.updateLocation(loc.lat, loc.lng, loc.speed)
                val depot = depotLatLng()
                if (depot != null) {
                    val meters = haversineMeters(loc.lat, loc.lng, depot.first, depot.second)
                    _state.value = _state.value.copy(atWarehouse = meters <= WAREHOUSE_RADIUS_METERS)
                    recomputeCompletion()
                }
            }
        }
    }

    /**
     * Arms the auto-start safety net for the selected route, if eligible. Idempotent: it won't
     * clobber an already-armed watcher for the same shift/route (which would reset accumulated
     * detection flags). Called by the screen once tracking permissions are present.
     */
    fun armAutoStart() {
        val s = _state.value
        if (s.shiftStarted || !computeAutoStartEligible()) return
        val scheduled = scheduledStart ?: return
        val depot = depotLatLng() ?: return
        val routeId = s.selectedRouteId ?: return
        val firstPackageId = s.jobStops.firstNotNullOfOrNull { it.assignment?.packageId } ?: return

        val existing = pendingDepartureStore.load()
        if (existing != null && existing.shiftId == shiftId && existing.routeId == routeId) return

        departureWatcher.arm(
            PendingDeparture(
                shiftId = shiftId,
                routeId = routeId,
                orgSlug = orgSlug,
                firstPackageId = firstPackageId,
                packageIds = s.packageIds,
                depotLat = depot.first,
                depotLng = depot.second,
                scheduledStart = scheduled,
                activity = departureActivity(),
            ),
        )
    }

    /** Whether the auto-start safety net may be armed for the current route right now. */
    private fun computeAutoStartEligible(): Boolean {
        val s = _state.value
        if (s.shiftStarted) return false
        if (!s.allPackagesScanned) return false
        if (!isWithinAutoStartWindow(scheduledStart)) return false
        if (depotLatLng() == null) return false
        return s.jobStops.any { it.assignment?.packageId != null }
    }

    /** The activity transition to watch for, derived from the route's assigned vehicle's ORS type. */
    private fun departureActivity(): DepartureActivity {
        val profile = _state.value.jobStops.firstNotNullOfOrNull {
            it.assignment?.vehicle?.vehicleType?.orsVehicleType
        }
        return if (profile?.startsWith("cycling", ignoreCase = true) == true) {
            DepartureActivity.ON_BICYCLE
        } else {
            DepartureActivity.IN_VEHICLE
        }
    }

    /** Depot coordinate `(lat, lng)` from the route's start/end step, if present. */
    private fun depotLatLng(): Pair<Double, Double>? {
        val steps = _state.value.steps
        val depot = steps.firstOrNull { it.type.equals("end", ignoreCase = true) }
            ?: steps.firstOrNull { it.type.equals("start", ignoreCase = true) }
        val lat = depot?.location?.latitude
        val lng = depot?.location?.longitude
        return if (lat != null && lng != null) lat to lng else null
    }

    private fun recomputeCompletion() {
        val s = _state.value
        if (s.deliveriesComplete && s.atWarehouse && !s.shiftComplete) {
            shiftEndedAt = Clock.System.now()
            _state.value = s.copy(shiftComplete = true)
            locationJob?.cancel()
            locationJob = null
            // Persist COMPLETE; the session observer clears it once the UI has reflected it.
            sessionStore.save(
                buildSession(
                    phase = ShiftPhase.COMPLETE,
                    inTransit = null,
                    overrides = s.statusOverrides,
                    deliveriesComplete = true,
                ),
            )
        }
    }

    // -----------------------------------------------------------------------
    // Post-shift breadcrumb trail: once complete, the driver can see the route they actually
    // drove, read from `driver_location_history` for [shiftStartedAt]..[shiftEndedAt].
    // -----------------------------------------------------------------------

    /** Opens the route-travelled map, fetching it the first time (or after a failed attempt). */
    fun openTravelledRoute() {
        _state.value = _state.value.copy(showTravelledRoute = true)
        if (_state.value.travelledRoute.isEmpty() && !_state.value.isLoadingTravelledRoute) loadTravelledRoute()
    }

    fun closeTravelledRoute() {
        _state.value = _state.value.copy(showTravelledRoute = false)
    }

    fun retryTravelledRoute() = loadTravelledRoute()

    private fun loadTravelledRoute() {
        val startedAt = shiftStartedAt
        if (startedAt == null) {
            // Shouldn't happen (shiftComplete only follows a real startShift/resume), but without a
            // start time there's no way to bound the query, so fail rather than guess a range.
            _state.value = _state.value.copy(travelledRouteError = "No start time recorded for this shift.")
            return
        }
        val endedAt = shiftEndedAt ?: Clock.System.now()
        _state.value = _state.value.copy(isLoadingTravelledRoute = true, travelledRouteError = null)
        viewModelScope.launch {
            routeHistoryRepository.fetchTravelledRoute(
                fromIso = (startedAt - ROUTE_HISTORY_PADDING).toString(),
                toIso = (endedAt + ROUTE_HISTORY_PADDING).toString(),
            )
                .onSuccess { points ->
                    _state.value = _state.value.copy(isLoadingTravelledRoute = false, travelledRoute = points)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoadingTravelledRoute = false,
                        travelledRouteError = error.message ?: "Couldn't load your route.",
                    )
                }
        }
    }

    /**
     * Reflects completion that may have happened in the background service: when the persisted
     * session for this shift reaches [ShiftPhase.COMPLETE], show the completed state and clear it.
     */
    private fun observeSessionCompletion() {
        viewModelScope.launch {
            ShiftSessionState.sessions.collect { session ->
                if (session != null &&
                    session.shiftId == shiftId &&
                    session.phase == ShiftPhase.COMPLETE &&
                    !_state.value.shiftComplete
                ) {
                    shiftStartedAt = session.startedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: shiftStartedAt
                    shiftEndedAt = session.endedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Clock.System.now()
                    _state.value = _state.value.copy(
                        deliveriesComplete = true,
                        atWarehouse = true,
                        shiftComplete = true,
                    )
                    sessionStore.clear()
                }
            }
        }
    }

    private fun fail(message: String, error: Throwable) {
        _state.value = _state.value.copy(
            isActionInProgress = false,
            actionError = error.message?.let { "$message: $it" } ?: message,
        )
    }

    override fun onCleared() {
        // On Android the foreground service must outlive the ViewModel/Activity (that's the point
        // of resume-on-kill), so only the in-process stream used elsewhere is cancelled here.
        if (!tracker.handlesLocationStreaming) locationJob?.cancel()
        // The version poll, by contrast, is purely a screen concern and dies with the screen.
        onPaused()
        super.onCleared()
    }

    private companion object {
        /**
         * How often the shift version is re-checked while the screen is resumed. Thirty seconds is
         * the plan's deliberate floor: the request is a single indexed row read, and anything slower
         * leaves a driver looking at a stale route for longer than they'd tolerate.
         */
        val VERSION_POLL_INTERVAL = 30.seconds
    }
}

/**
 * The add-stop form state: the org's unassigned [packages] to choose from, and which one is
 * currently [selectedPackageId].
 */
data class AddStopDraft(
    val loading: Boolean = true,
    val packages: List<AddablePackage> = emptyList(),
    val selectedPackageId: String? = null,
    val submitting: Boolean = false,
    val error: String? = null,
)

/**
 * Renders a machine status enum (e.g. `ONBOARD_FOR_DELIVERY`) as "Onboard For Delivery". Only used
 * as a fallback for [ShiftDetailViewModel.UiState.displayStatusFor] while the real
 * `package_status.status` label hasn't resolved yet (or for an enum with no matching row).
 */
private fun humanizeStatus(status: String): String =
    status.split('_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.lowercase().replaceFirstChar(Char::uppercase) }
