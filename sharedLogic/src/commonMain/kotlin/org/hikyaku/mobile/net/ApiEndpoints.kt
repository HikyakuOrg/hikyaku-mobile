package org.hikyaku.mobile.net

/**
 * Every HTTP endpoint path the app calls outside of Supabase (Postgrest/Auth/Storage), in one
 * place so a path or version bump doesn't require hunting through each repository. Each function
 * takes the resolved base URL rather than reading [ApiConfigProvider] itself, matching the
 * `apiUrl: () -> String` constructor param already used by these repositories.
 */
object ApiEndpoints {
    /** `GET {baseUrl}/api/environment` — resolves runtime config for a Hikyaku instance. */
    fun environment(baseUrl: String) = "${baseUrl.trimEnd('/')}/api/environment"

    /** `GET {apiUrl}/geocode/autocomplete?text=` — Pelias/Photon address autocomplete. */
    fun geocodeAutocomplete(apiUrl: String) = "${apiUrl.trimEnd('/')}/geocode/autocomplete"

    /**
     * `GET {apiUrl}/geocode/reverse?lat=&lon=&radius=&include=` — reverse geocode / nearby-POI
     * lookup (Photon). Used to find fuel stops (`include=osm.amenity.fuel`) around a point.
     */
    fun geocodeReverse(apiUrl: String) = "${apiUrl.trimEnd('/')}/geocode/reverse"

    /** `POST {apiUrl}/api/v1/routing/route` — road-snapped route preview. */
    fun routingRoute(apiUrl: String) = "${apiUrl.trimEnd('/')}/api/v1/routing/route"

    /**
     * `POST {apiUrl}/api/v1/optimisation/adhoc` — optimises and persists a shift from a
     * vehicle type, start location + time, and the package ids to deliver; returns the
     * `vrp_optimization` id. Used by the mobile create-shift flow.
     */
    fun optimisationAdhoc(apiUrl: String) = "${apiUrl.trimEnd('/')}/api/v1/optimisation/adhoc"

    /**
     * `POST {apiUrl}/api/v1/packages` — creates one package (row, dimensions, delivery window and
     * opening timeline entry) in a single transaction and, unless `autoAssign` is false, assigns it
     * to a shift straight away. Always 201 — a failed assignment comes back as an outcome on the
     * result, not an error — except 409 for a tracking-number collision with a different payload.
     */
    fun packages(apiUrl: String) = "${apiUrl.trimEnd('/')}/api/v1/packages"

    /**
     * `POST {apiUrl}/api/v1/packages/bulk` — up to 500 packages in one call, taking the
     * per-warehouse assignment lock once instead of once per package. Results are index-aligned
     * with the request; one bad entry doesn't fail the batch.
     */
    fun packagesBulk(apiUrl: String) = "${apiUrl.trimEnd('/')}/api/v1/packages/bulk"

    /**
     * `POST {apiUrl}/api/v1/shifts` — opens an empty `planned` shift for a driver/vehicle/warehouse
     * on a given service day. 409 when that driver or vehicle already has an open shift that day.
     */
    fun shifts(apiUrl: String) = "${apiUrl.trimEnd('/')}/api/v1/shifts"

    /**
     * `GET {apiUrl}/api/v1/shifts/{id}/version` — the shift's `revision`, `updatedAt`, `stopCount`
     * and `status`, and nothing else. Deliberately cheap: the driver app polls it while the shift
     * screen is resumed to notice a replan without refetching the route (see
     * [org.hikyaku.mobile.shift.ShiftVersionPoll]).
     */
    fun shiftVersion(apiUrl: String, id: String) = "${apiUrl.trimEnd('/')}/api/v1/shifts/$id/version"

    /**
     * `GET {apiUrl}/api/v1/invitations/pending` — team invitations awaiting the caller's
     * decision, matched by their own verified email (not org-scoped, no `X-Organisation-Slug`
     * needed).
     */
    fun invitationsPending(apiUrl: String) = "${apiUrl.trimEnd('/')}/api/v1/invitations/pending"

    /**
     * `POST {apiUrl}/api/v1/invitations/{id}/accept` — accepts a pending invitation, creating
     * the caller's `team_members` row in the invitation's organisation.
     */
    fun invitationAccept(apiUrl: String, id: String) = "${apiUrl.trimEnd('/')}/api/v1/invitations/$id/accept"

    /** `POST {apiUrl}/api/v1/invitations/{id}/decline` — declines a pending invitation. */
    fun invitationDecline(apiUrl: String, id: String) = "${apiUrl.trimEnd('/')}/api/v1/invitations/$id/decline"

    /**
     * `GET {apiUrl}/api/v1/vin/{vin}` — decodes a VIN into make/model/year/plant/engine data.
     * Runs fully offline server-side, so there's no rate limit. Always 200, even for a garbage
     * VIN — the response's `valid` flag and `components` say how much decoded.
     */
    fun vinDecode(apiUrl: String, vin: String) = "${apiUrl.trimEnd('/')}/api/v1/vin/$vin"
}
