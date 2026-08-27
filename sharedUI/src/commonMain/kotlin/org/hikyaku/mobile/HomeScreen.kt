package org.hikyaku.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.kizitonwose.calendar.core.Week
import com.kizitonwose.calendar.core.WeekDay
import com.kizitonwose.calendar.core.WeekDayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.core.minusDays
import com.kizitonwose.calendar.core.minusMonths
import com.kizitonwose.calendar.core.now
import com.kizitonwose.calendar.core.plusDays
import com.kizitonwose.calendar.core.plusMonths
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_delete
import hikyaku.sharedui.generated.resources.action_ok
import hikyaku.sharedui.generated.resources.action_retry
import hikyaku.sharedui.generated.resources.action_undo
import hikyaku.sharedui.generated.resources.app_name
import hikyaku.sharedui.generated.resources.cd_next_week
import hikyaku.sharedui.generated.resources.cd_open_navigation_menu
import hikyaku.sharedui.generated.resources.cd_previous_week
import hikyaku.sharedui.generated.resources.home_delete_blocked_message
import hikyaku.sharedui.generated.resources.home_delete_blocked_title
import hikyaku.sharedui.generated.resources.home_no_shifts
import hikyaku.sharedui.generated.resources.home_no_shifts_for_date
import hikyaku.sharedui.generated.resources.home_packages_to_deliver_count
import hikyaku.sharedui.generated.resources.home_section_completed
import hikyaku.sharedui.generated.resources.home_stops_count
import hikyaku.sharedui.generated.resources.shift_deleted_snackbar
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import org.hikyaku.mobile.auth.HomeUiState
import org.hikyaku.mobile.auth.ShiftsUiState
import org.hikyaku.mobile.auth.model.AuthState
import org.hikyaku.mobile.organisation.model.Organisation
import org.hikyaku.mobile.shift.model.Shift
import org.hikyaku.mobile.shift.model.ShiftRoute
import org.hikyaku.mobile.shift.model.ShiftRouteStep
import org.hikyaku.mobile.shift.model.ShiftSolution
import org.hikyaku.mobile.theme.HikyakuTheme
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.maplibre.spatialk.geojson.Point

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    user: AuthState.Authenticated,
    homeState: HomeUiState,
    shiftState: ShiftsUiState,
    onOpenDrawer: () -> Unit,
    onSignOut: () -> Unit,
    onRetryOrgs: () -> Unit,
    onSelectOrg: (String) -> Unit,
    onRefreshShifts: () -> Unit,
    onSaveDisplayName: (name: String, onResult: (String?) -> Unit) -> Unit,
    onUploadAvatar: (bytes: ByteArray, onResult: (String?) -> Unit) -> Unit,
    onShiftClick: (String) -> Unit,
    onCreateShift: () -> Unit,
    onDeleteShift: (shiftId: String, onResult: (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSwitcher by remember { mutableStateOf(false) }
    var showDeleteBlockedDialog by remember { mutableStateOf(false) }
    var hiddenShiftIds by remember { mutableStateOf(emptySet<String>()) }
    // Bumped for a shift each time Undo restores it, so its DeletableShiftCard is keyed fresh —
    // rememberSwipeToDismissBoxState saves its value by slot, and without this a restored card
    // would reappear still showing the swiped-away "Dismissed" state instead of resetting.
    var swipeGeneration by remember { mutableStateOf(emptyMap<String, Int>()) }
    // The date picked on the calendar; shifts below are filtered to this. Since
    // [shiftState.shifts] is already scoped to the selected organisation (see AuthViewModel.loadShifts),
    // filtering it client-side by date keeps every date view scoped by org for free.
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val canDeleteShifts = homeState.selectedOrganisation?.isPersonal == true
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Swiping hides the shift immediately and offers an "Undo" snackbar; the actual delete only
    // fires once that snackbar is dismissed without the user undoing it, so the delete stays
    // reversible for the few seconds the snackbar is up.
    fun swipeToDelete(shift: Shift) {
        hiddenShiftIds = hiddenShiftIds + shift.id
        scope.launch {
            val message = getString(Res.string.shift_deleted_snackbar)
            val undo = getString(Res.string.action_undo)
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undo,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                hiddenShiftIds = hiddenShiftIds - shift.id
                swipeGeneration = swipeGeneration + (shift.id to (swipeGeneration[shift.id] ?: 0) + 1)
            } else {
                onDeleteShift(shift.id) { error ->
                    if (error != null) {
                        hiddenShiftIds = hiddenShiftIds - shift.id
                        swipeGeneration = swipeGeneration + (shift.id to (swipeGeneration[shift.id] ?: 0) + 1)
                        scope.launch { snackbarHostState.showSnackbar(error) }
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(homeState.selectedOrganisation?.displayName ?: stringResource(Res.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(Res.string.cd_open_navigation_menu))
                    }
                },
                actions = {
                    IconButton(onClick = { showSwitcher = true }) {
                        ProfileAvatar(
                            displayName = user.displayName,
                            email = user.email,
                            avatarUrl = user.avatarUrl,
                            size = 32.dp,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (homeState.selectedOrganisation?.isPersonal == true) {
                FloatingActionButton(onClick = onCreateShift) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
        },
    ) { padding ->
        val visibleShifts = remember(shiftState.shifts, hiddenShiftIds) {
            shiftState.shifts.filterNot { it.id in hiddenShiftIds }
        }
        val shiftDates = remember(visibleShifts) { visibleShifts.map { it.calendarDate }.toSet() }

        PullToRefreshBox(
            isRefreshing = shiftState.isRefreshing,
            onRefresh = onRefreshShifts,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ShiftCalendar(
                        selectedDate = selectedDate,
                        shiftDates = shiftDates,
                        onDayClick = { date -> selectedDate = date },
                    )
                }

                when {
                    homeState.isLoadingOrgs -> item { CenteredSpinner() }

                    homeState.orgError != null -> item {
                        OrgErrorCard(message = homeState.orgError, onRetry = onRetryOrgs)
                    }

                    shiftState.isLoading -> item { CenteredSpinner() }

                    shiftState.error != null -> item {
                        ShiftErrorCard(
                            message = shiftState.error,
                            onRetry = { homeState.selectedOrgId?.let(onSelectOrg) },
                        )
                    }

                    visibleShifts.isEmpty() -> item { NoShiftsFoundCard() }

                    else -> shiftListItems(
                        visibleShifts = visibleShifts,
                        selectedDate = selectedDate,
                        completedShiftIds = shiftState.completedShiftIds,
                        packageCounts = shiftState.packageCounts,
                        canDeleteShifts = canDeleteShifts,
                        nonDeletableShiftIds = shiftState.nonDeletableShiftIds,
                        swipeGeneration = swipeGeneration,
                        onShiftClick = onShiftClick,
                        onSwipeToDelete = ::swipeToDelete,
                        onDeleteBlocked = { showDeleteBlockedDialog = true },
                    )
                }
            }
        }
    }

    if (showSwitcher) {
        AccountSwitcherSheet(
            user = user,
            organisations = homeState.organisations,
            selectedOrgId = homeState.selectedOrgId,
            onSelectOrg = onSelectOrg,
            onSaveDisplayName = onSaveDisplayName,
            onUploadAvatar = onUploadAvatar,
            onSignOut = onSignOut,
            onDismiss = { showSwitcher = false },
        )
    }

    if (showDeleteBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteBlockedDialog = false },
            title = { Text(stringResource(Res.string.home_delete_blocked_title)) },
            text = { Text(stringResource(Res.string.home_delete_blocked_message)) },
            confirmButton = {
                TextButton(onClick = { showDeleteBlockedDialog = false }) {
                    Text(stringResource(Res.string.action_ok))
                }
            },
        )
    }
}

private val previewUser = AuthState.Authenticated(
    userId = "user-1",
    email = "jane.doe@example.com",
    displayName = "Jane Doe",
    avatarUrl = null,
)

private val previewOrganisations = listOf(
    Organisation(id = "org-1", name = "Personal", slug = "jane-doe", orgType = "personal", createdBy = "user-1"),
    Organisation(id = "org-2", name = "Acme Logistics", slug = "acme-logistics", orgType = "team", createdBy = "user-1"),
)

private fun previewRoute(vararg coordinates: Pair<Double, Double>): ShiftRoute = ShiftRoute(
    steps = coordinates.mapIndexed { index, (lng, lat) ->
        ShiftRouteStep(stepIndex = index, type = "job", location = Point(longitude = lng, latitude = lat))
    },
)

private val previewShifts = listOf(
    Shift(
        id = "shift-1",
        createdAt = "2026-07-25T08:00:00Z",
        provider = "optimizer",
        scheduledStart = "2026-07-25T08:00:00Z",
        solutions = listOf(
            ShiftSolution(
                routesCount = 4,
                unassignedCount = 1,
                duration = 3600,
                routes = listOf(
                    previewRoute(
                        103.8318 to 1.3048,
                        103.8390 to 1.3005,
                        103.8450 to 1.3100,
                        103.8500 to 1.2980,
                    ),
                ),
            ),
        ),
    ),
    Shift(
        id = "shift-2",
        createdAt = "2026-07-24T08:00:00Z",
        provider = "manual",
        scheduledStart = "2026-07-24T08:00:00Z",
        solutions = listOf(
            ShiftSolution(
                routesCount = 2,
                unassignedCount = 0,
                duration = 1800,
                routes = listOf(previewRoute(103.8200 to 1.2900, 103.8260 to 1.2950)),
            ),
        ),
    ),
)

@Preview
@Composable
private fun HomeScreenPreview() {
    HikyakuTheme {
        HomeScreen(
            user = previewUser,
            homeState = HomeUiState(
                isLoadingOrgs = false,
                organisations = previewOrganisations,
                orgError = null,
                selectedOrgId = "org-1",
            ),
            shiftState = ShiftsUiState(
                isLoading = false,
                isRefreshing = false,
                shifts = previewShifts,
                error = null,
                orgId = "org-1",
                completedShiftIds = setOf("shift-2"),
                packageCounts = mapOf("shift-1" to 12, "shift-2" to 6),
            ),
            onOpenDrawer = {},
            onSignOut = {},
            onRetryOrgs = {},
            onSelectOrg = {},
            onRefreshShifts = {},
            onSaveDisplayName = { _, _ -> },
            onUploadAvatar = { _, _ -> },
            onShiftClick = {},
            onCreateShift = {},
            onDeleteShift = { _, _ -> },
        )
    }
}

@Preview
@Composable
private fun HomeScreenEmptyPreview() {
    HikyakuTheme {
        HomeScreen(
            user = previewUser,
            homeState = HomeUiState(
                isLoadingOrgs = false,
                organisations = previewOrganisations,
                orgError = null,
                selectedOrgId = "org-1",
            ),
            shiftState = ShiftsUiState(
                isLoading = false,
                isRefreshing = false,
                shifts = emptyList(),
                error = null,
                orgId = "org-1",
                completedShiftIds = emptySet(),
            ),
            onOpenDrawer = {},
            onSignOut = {},
            onRetryOrgs = {},
            onSelectOrg = {},
            onRefreshShifts = {},
            onSaveDisplayName = { _, _ -> },
            onUploadAvatar = { _, _ -> },
            onShiftClick = {},
            onCreateShift = {},
            onDeleteShift = { _, _ -> },
        )
    }
}

/**
 * Renders [visibleShifts] filtered to [selectedDate] as a flat list.
 */
private fun LazyListScope.shiftListItems(
    visibleShifts: List<Shift>,
    selectedDate: LocalDate,
    completedShiftIds: Set<String>,
    packageCounts: Map<String, Int>,
    canDeleteShifts: Boolean,
    nonDeletableShiftIds: Set<String>,
    swipeGeneration: Map<String, Int>,
    onShiftClick: (String) -> Unit,
    onSwipeToDelete: (Shift) -> Unit,
    onDeleteBlocked: () -> Unit,
) {
    val dateShifts = visibleShifts.filter { it.calendarDate == selectedDate }.sortedBy { it.createdAt }

    if (dateShifts.isEmpty()) {
        item {
            Text(
                text = stringResource(Res.string.home_no_shifts_for_date),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(4.dp),
            )
        }
        return
    }

    for (shift in dateShifts) {
        item(key = shift.id) {
            key(shift.id, swipeGeneration[shift.id] ?: 0) {
                DeletableShiftCard(
                    shift = shift,
                    isCompleted = shift.id in completedShiftIds,
                    packageCount = packageCounts[shift.id] ?: 0,
                    canDelete = canDeleteShifts,
                    isDeleteBlocked = shift.id in nonDeletableShiftIds,
                    onClick = { onShiftClick(shift.id) },
                    onSwipeToDelete = { onSwipeToDelete(shift) },
                    onDeleteBlocked = onDeleteBlocked,
                )
            }
        }
    }
}

/**
 * A week-at-a-time calendar with a prev/next header. A tap selects a single date via [onDayClick];
 * unlike a month calendar there is no long-press and no multi-select.
 */
@Composable
private fun ShiftCalendar(
    selectedDate: LocalDate,
    shiftDates: Set<LocalDate>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val startDate = remember { today.minusMonths(24) }
    val endDate = remember { today.plusMonths(24) }
    val weekDays = remember { daysOfWeek() }
    val state = rememberWeekCalendarState(
        startDate = startDate,
        endDate = endDate,
        firstVisibleWeekDate = today,
        firstDayOfWeek = weekDays.first(),
    )
    val coroutineScope = rememberCoroutineScope()

    Column(modifier.fillMaxWidth()) {
        CalendarWeekHeader(
            week = state.firstVisibleWeek,
            onPrevious = {
                coroutineScope.launch {
                    state.animateScrollToWeek(state.firstVisibleWeek.days.first().date.minusDays(7))
                }
            },
            onNext = {
                coroutineScope.launch {
                    state.animateScrollToWeek(state.firstVisibleWeek.days.first().date.plusDays(7))
                }
            },
        )
        Row(Modifier.fillMaxWidth()) {
            for (dayOfWeek in weekDays) {
                Text(
                    text = dayOfWeek.name.take(3),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        WeekCalendar(
            state = state,
            dayContent = { day ->
                CalendarDayCell(
                    day = day,
                    selectedDate = selectedDate,
                    hasShifts = day.date in shiftDates,
                    onClick = onDayClick,
                )
            },
        )
    }
}

@Composable
private fun CalendarWeekHeader(
    week: Week,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(Res.string.cd_previous_week))
        }
        Text(
            text = monthDisplayText(week.days.first().date.yearMonth),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(Res.string.cd_next_week))
        }
    }
}

private fun monthDisplayText(yearMonth: YearMonth): String {
    val monthName = yearMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return "$monthName ${yearMonth.year}"
}

@Composable
private fun CalendarDayCell(
    day: WeekDay,
    selectedDate: LocalDate,
    hasShifts: Boolean,
    onClick: (LocalDate) -> Unit,
) {
    if (day.position != WeekDayPosition.RangeDate) {
        Box(Modifier.aspectRatio(1f))
        return
    }
    val isSelected = day.date == selectedDate

    val highlight = if (isSelected) {
        Modifier
            .padding(vertical = 4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onClick(day.date) }
            .then(highlight),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
            if (hasShifts) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(4.dp)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}

/**
 * Wraps [ShiftCard] with swipe-to-delete when [canDelete] is true (personal orgs only — shared
 * orgs' shifts belong to the whole team and shouldn't be deletable from this screen). A swipe
 * fires [onSwipeToDelete] immediately; the caller is responsible for undo/confirmation UX (a
 * snackbar), not this composable. When [isDeleteBlocked] is true (the shift has a delivered
 * package), the swipe is reverted instead and [onDeleteBlocked] fires so the caller can explain
 * why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeletableShiftCard(
    shift: Shift,
    isCompleted: Boolean,
    packageCount: Int,
    canDelete: Boolean,
    isDeleteBlocked: Boolean,
    onClick: () -> Unit,
    onSwipeToDelete: () -> Unit,
    onDeleteBlocked: () -> Unit,
) {
    if (!canDelete) {
        ShiftCard(shift, isCompleted = isCompleted, packageCount = packageCount, onClick = onClick)
        return
    }
    val dismissState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                if (isDeleteBlocked) {
                    scope.launch { dismissState.reset() }
                    onDeleteBlocked()
                } else {
                    onSwipeToDelete()
                }
            }
        },
        backgroundContent = {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.action_delete))
                }
            }
        },
    ) {
        ShiftCard(shift, isCompleted = isCompleted, packageCount = packageCount, onClick = onClick)
    }
}

@Composable
private fun ShiftCard(shift: Shift, isCompleted: Boolean, packageCount: Int, onClick: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(shift.displayTime, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (isCompleted) {
                    CompletedBadge()
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(Res.plurals.home_stops_count, shift.stopCount, shift.stopCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = " • ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = pluralStringResource(Res.plurals.home_packages_to_deliver_count, packageCount, packageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (shift.routePaths.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                ShiftRoutePreview(
                    routePaths = shift.routePaths,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                )
            }
        }
    }
}

/** A small "Completed" indicator shown once every package on a shift has been delivered. */
@Composable
private fun CompletedBadge() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(Res.string.home_section_completed),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * A compact, non-interactive preview of a shift's route shape: each route's stops connected by
 * straight lines and scaled to fit the available space. This is not a real map (no streets or
 * tiles) — just a quick visual cue built from the same stop coordinates already loaded for the
 * stop count, so it stays cheap enough to render inline for every row in the list.
 */
@Composable
private fun ShiftRoutePreview(routePaths: List<List<Point>>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val stopColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val allPoints = routePaths.flatten()
            if (allPoints.isEmpty()) return@Canvas
            val longitudes = allPoints.map { it.longitude }
            val latitudes = allPoints.map { it.latitude }
            val minLon = longitudes.min()
            val minLat = latitudes.min()
            val lonSpan = (longitudes.max() - minLon).takeIf { it > 0.0 } ?: 1.0
            val latSpan = (latitudes.max() - minLat).takeIf { it > 0.0 } ?: 1.0

            fun project(point: Point): Offset {
                val x = ((point.longitude - minLon) / lonSpan).toFloat() * size.width
                // Latitude increases north but the y axis increases downward, so flip it.
                val y = (1f - ((point.latitude - minLat) / latSpan).toFloat()) * size.height
                return Offset(x, y)
            }

            routePaths.forEach { path ->
                val offsets = path.map(::project)
                for (i in 0 until offsets.size - 1) {
                    drawLine(
                        color = lineColor,
                        start = offsets[i],
                        end = offsets[i + 1],
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                offsets.forEach { offset -> drawCircle(color = stopColor, radius = 2.5.dp.toPx(), center = offset) }
            }
        }
    }
}

@Composable
private fun NoShiftsFoundCard() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.home_no_shifts),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShiftErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
        }
    }
}

@Composable
private fun OrgErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
        }
    }
}
