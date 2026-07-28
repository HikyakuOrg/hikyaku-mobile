package org.hikyaku.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.core.minusMonths
import com.kizitonwose.calendar.core.now
import com.kizitonwose.calendar.core.plusMonths
import hikyaku.sharedui.generated.resources.Res
import hikyaku.sharedui.generated.resources.action_delete
import hikyaku.sharedui.generated.resources.action_ok
import hikyaku.sharedui.generated.resources.action_retry
import hikyaku.sharedui.generated.resources.action_undo
import hikyaku.sharedui.generated.resources.app_name
import hikyaku.sharedui.generated.resources.cd_next_month
import hikyaku.sharedui.generated.resources.cd_open_navigation_menu
import hikyaku.sharedui.generated.resources.cd_previous_month
import hikyaku.sharedui.generated.resources.home_delete_blocked_message
import hikyaku.sharedui.generated.resources.home_delete_blocked_title
import hikyaku.sharedui.generated.resources.home_no_shifts
import hikyaku.sharedui.generated.resources.home_no_shifts_for_date
import hikyaku.sharedui.generated.resources.home_packages_to_deliver_count
import hikyaku.sharedui.generated.resources.home_section_completed
import hikyaku.sharedui.generated.resources.shift_deleted_snackbar
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.hikyaku.mobile.util.formatDisplayDate
import org.hikyaku.mobile.auth.HomeUiState
import org.hikyaku.mobile.auth.ShiftsUiState
import org.hikyaku.mobile.auth.model.AuthState
import org.hikyaku.mobile.organisation.model.Organisation
import org.hikyaku.mobile.shift.model.Shift
import org.hikyaku.mobile.shift.model.ShiftSolution
import org.hikyaku.mobile.theme.HikyakuTheme
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
    // The date (or date range) picked on the calendar; shifts below are filtered to this. Since
    // [shiftState.shifts] is already scoped to the selected organisation (see AuthViewModel.loadShifts),
    // filtering it client-side by date keeps every date/range view scoped by org for free.
    var dateSelection by remember { mutableStateOf(DateSelection(startDate = LocalDate.now())) }
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
                        selection = dateSelection,
                        shiftDates = shiftDates,
                        onDayClick = { date -> dateSelection = DateSelection(startDate = date) },
                        onDayLongPress = { date -> dateSelection = dateSelection.extendedBy(date) },
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
                        selection = dateSelection,
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

private val previewShifts = listOf(
    Shift(
        id = "shift-1",
        createdAt = "2026-07-25T08:00:00Z",
        provider = "optimizer",
        scheduledStart = "2026-07-25T08:00:00Z",
        solutions = listOf(ShiftSolution(routesCount = 4, unassignedCount = 1, duration = 3600)),
    ),
    Shift(
        id = "shift-2",
        createdAt = "2026-07-24T08:00:00Z",
        provider = "manual",
        scheduledStart = "2026-07-24T08:00:00Z",
        solutions = listOf(ShiftSolution(routesCount = 2, unassignedCount = 0, duration = 1800)),
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

/** Which single date, or inclusive date range, is picked on [ShiftCalendar]. */
private data class DateSelection(val startDate: LocalDate? = null, val endDate: LocalDate? = null)

/**
 * Applies a calendar long-press to the current selection, building a multi-day range: the
 * first long-press starts a new range anchor; a second long-press on a different date extends
 * it into a range (earlier dates extend the start, later dates extend the end); a third
 * long-press starts a fresh range anchored at the newly pressed date.
 */
private fun DateSelection.extendedBy(pressed: LocalDate): DateSelection {
    val start = startDate
    return when {
        start == null -> DateSelection(startDate = pressed)
        endDate != null -> DateSelection(startDate = pressed)
        pressed < start -> DateSelection(startDate = pressed, endDate = start)
        pressed != start -> DateSelection(startDate = start, endDate = pressed)
        else -> DateSelection(startDate = pressed)
    }
}

/**
 * Renders [visibleShifts] filtered to [selection]: a flat list for a single date, or a list
 * grouped under a date header per day for a range.
 */
private fun LazyListScope.shiftListItems(
    visibleShifts: List<Shift>,
    selection: DateSelection,
    completedShiftIds: Set<String>,
    packageCounts: Map<String, Int>,
    canDeleteShifts: Boolean,
    nonDeletableShiftIds: Set<String>,
    swipeGeneration: Map<String, Int>,
    onShiftClick: (String) -> Unit,
    onSwipeToDelete: (Shift) -> Unit,
    onDeleteBlocked: () -> Unit,
) {
    val start = selection.startDate
    val end = selection.endDate
    val groups = when {
        start == null -> emptyMap()
        end == null -> visibleShifts.filter { it.calendarDate == start }
            .sortedBy { it.createdAt }
            .let { if (it.isEmpty()) emptyMap() else mapOf(start to it) }
        else -> visibleShifts.filter { it.calendarDate in start..end }
            .groupBy { it.calendarDate }
            .toSortedMap()
            .mapValues { (_, shifts) -> shifts.sortedBy { it.createdAt } }
    }

    if (groups.isEmpty()) {
        item {
            Text(
                text = stringResource(Res.string.home_no_shifts_for_date),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(4.dp),
            )
        }
        return
    }

    val showHeaders = end != null
    groups.forEach { (date, dateShifts) ->
        if (showHeaders) {
            item(key = "header-$date") { DateHeader(date) }
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
}

/**
 * A month-at-a-time calendar with a prev/next header. A tap drives [selection] via [onDayClick]
 * (always a single date); a long-press drives it via [onDayLongPress] (builds a multi-day range).
 */
@Composable
private fun ShiftCalendar(
    selection: DateSelection,
    shiftDates: Set<LocalDate>,
    onDayClick: (LocalDate) -> Unit,
    onDayLongPress: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentMonth = remember { YearMonth.now() }
    val startMonth = remember { currentMonth.minusMonths(24) }
    val endMonth = remember { currentMonth.plusMonths(24) }
    val weekDays = remember { daysOfWeek() }
    val state = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = weekDays.first(),
    )
    val coroutineScope = rememberCoroutineScope()

    Column(modifier.fillMaxWidth()) {
        CalendarMonthHeader(
            yearMonth = state.firstVisibleMonth.yearMonth,
            onPrevious = {
                coroutineScope.launch {
                    state.animateScrollToMonth(state.firstVisibleMonth.yearMonth.minusMonths(1))
                }
            },
            onNext = {
                coroutineScope.launch {
                    state.animateScrollToMonth(state.firstVisibleMonth.yearMonth.plusMonths(1))
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
        HorizontalCalendar(
            state = state,
            dayContent = { day ->
                CalendarDayCell(
                    day = day,
                    selection = selection,
                    hasShifts = day.date in shiftDates,
                    onClick = onDayClick,
                    onLongPress = onDayLongPress,
                )
            },
        )
    }
}

@Composable
private fun CalendarMonthHeader(
    yearMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(Res.string.cd_previous_month))
        }
        Text(
            text = monthDisplayText(yearMonth),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(Res.string.cd_next_month))
        }
    }
}

private fun monthDisplayText(yearMonth: YearMonth): String {
    val monthName = yearMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return "$monthName ${yearMonth.year}"
}

@Composable
private fun CalendarDayCell(
    day: CalendarDay,
    selection: DateSelection,
    hasShifts: Boolean,
    onClick: (LocalDate) -> Unit,
    onLongPress: (LocalDate) -> Unit,
) {
    if (day.position != DayPosition.MonthDate) {
        Box(Modifier.aspectRatio(1f))
        return
    }
    val isStart = day.date == selection.startDate
    val isEnd = day.date == selection.endDate
    val isSingle = isStart && selection.endDate == null
    val start = selection.startDate
    val end = selection.endDate
    val isBetween = start != null && end != null && day.date > start && day.date < end

    val highlight = when {
        isSingle -> Modifier
            .padding(vertical = 4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)

        isStart -> Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50))
            .background(MaterialTheme.colorScheme.primary)

        isEnd -> Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50))
            .background(MaterialTheme.colorScheme.primary)

        isBetween -> Modifier
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.primaryContainer)

        else -> Modifier
    }
    val isEdge = isStart || isEnd

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = { onClick(day.date) },
                onLongClick = { onLongPress(day.date) },
            )
            .then(highlight),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isEdge) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
            if (hasShifts) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(4.dp)
                        .background(
                            color = if (isEdge) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    Text(
        text = formatDisplayDate(date),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
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
                    Text(
                        text = stringResource(Res.string.home_section_completed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = pluralStringResource(Res.plurals.home_packages_to_deliver_count, packageCount, packageCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
