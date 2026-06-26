package com.scalendar.ui.screen.weekview

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.data.model.EntryCategory
import com.scalendar.data.model.TimeOfDay
import com.scalendar.ui.component.EntryFab
import com.scalendar.ui.component.EntryFabMenuOverlay
import com.scalendar.ui.screen.dayview.AddEntrySheet
import com.scalendar.ui.screen.dayview.DayViewViewModel
import com.scalendar.ui.screen.dayview.EntryDetailSheet
import com.scalendar.ui.theme.displayBgColor
import com.scalendar.ui.theme.displayFgColor
import com.scalendar.ui.shared.SharedCalendarViewModel
import com.scalendar.util.VietnamHolidays
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.scalendar.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ANYTIME is shown as chips in the header — only these 3 appear as swim-lane rows
private val GRID_SLOTS = listOf(
    TimeOfDay.MORNING,
    TimeOfDay.AFTERNOON,
    TimeOfDay.EVENING,
)

@Composable
private fun TimeOfDay.shortLabel(): String = when (this) {
    TimeOfDay.ANYTIME   -> stringResource(R.string.slot_anytime_short)
    TimeOfDay.MORNING   -> stringResource(R.string.slot_morning_short)
    TimeOfDay.AFTERNOON -> stringResource(R.string.slot_afternoon_short)
    TimeOfDay.EVENING   -> stringResource(R.string.slot_evening_short)
}

private fun weekRangeLabel(
    start      : LocalDate,
    dayShort   : DateTimeFormatter,
    monthShort : DateTimeFormatter,
): String {
    val end = start.plusDays(6)
    return if (start.month == end.month) {
        "${start.format(dayShort)} - ${end.format(dayShort)} ${start.format(monthShort)}"
    } else {
        "${start.format(dayShort)} ${start.format(monthShort)} – ${end.format(dayShort)} ${end.format(monthShort)}"
    }
}

// ── Layout constants ──────────────────────────────────────────────────
private const val SWIM_LANE_HEIGHT  = 160  // dp per swim-lane row
private const val LABEL_COL_W      = 40   // dp — narrow left label column
private val       COL_WIDTH        = 108.dp

// Header = date number section + anytime chip section
private const val HEADER_DATE_H    = 72   // dp — abbrev + day number
private const val HEADER_ANYTIME_H = 44   // dp — anytime chips strip
private const val HEADER_ROW_H     = HEADER_DATE_H + HEADER_ANYTIME_H  // 116 dp total

// ── Screen ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekViewScreen(
    sharedVm      : SharedCalendarViewModel,
    onOpenDrawer  : () -> Unit,
    onDayClick    : (LocalDate) -> Unit,
    onOpenSearch  : () -> Unit         = {},
    holidayVnMode : String             = "ALL",
    holidayColor  : Color              = Color(0xFF26A69A),
    viewModel     : WeekViewViewModel  = hiltViewModel(),
    actionsVm     : DayViewViewModel   = hiltViewModel(),
) {
    val selectedDate    by sharedVm.selectedDate.collectAsState()
    val categoryFilters by sharedVm.categoryFilters.collectAsState()
    val uiState         by viewModel.uiState.collectAsState()
    val today           = remember { LocalDate.now() }
    val notes           by actionsVm.notes.collectAsState()
    val defaultNotifs   by actionsVm.defaultNotifs.collectAsState()

    val locale      = LocalConfiguration.current.locales[0]
    val MONTH_FMT   = remember(locale) { DateTimeFormatter.ofPattern("MMMM", locale) }
    val DAY_SHORT   = remember(locale) { DateTimeFormatter.ofPattern("d",    locale) }
    val MONTH_SHORT = remember(locale) { DateTimeFormatter.ofPattern("MMM",  locale) }

    val filteredDeadlines = remember(uiState.deadlineEntries, categoryFilters) {
        uiState.deadlineEntries.mapValues { (_, list) ->
            list.filter { it.category in categoryFilters }
        }
    }

    LaunchedEffect(selectedDate) { viewModel.setDate(selectedDate) }

    var showFabMenu      by remember { mutableStateOf(false) }
    var addEntryCategory by remember { mutableStateOf<EntryCategory?>(null) }
    var detailEntry      by remember { mutableStateOf<EntryEntity?>(null) }
    var editingEntry     by remember { mutableStateOf<EntryEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        floatingActionButton = {
            EntryFab(expanded = showFabMenu, onToggle = { showFabMenu = !showFabMenu })
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text  = uiState.weekStart.format(MONTH_FMT).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text  = weekRangeLabel(uiState.weekStart, DAY_SHORT, MONTH_SHORT),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_menu))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.action_search), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { sharedVm.previousWeek() }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.content_prev_week), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { sharedVm.nextWeek() }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.content_next_week), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { sharedVm.selectDate(LocalDate.now()) }) {
                        Icon(Icons.Outlined.CalendarToday, contentDescription = stringResource(R.string.weekview_today), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->

        val days             = (0..6).map { uiState.weekStart.plusDays(it.toLong()) }
        val horizontalScroll = rememberScrollState()
        val verticalScroll   = rememberScrollState()

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Row(modifier = Modifier.fillMaxSize()) {

                // ── Narrow left-label column ──────────────────────────
                Column(modifier = Modifier.verticalScroll(verticalScroll)) {
                    // Spacer aligned with the full header height
                    Spacer(Modifier.height(HEADER_ROW_H.dp))
                    // Only MORNING / AFTERNOON / EVENING labels
                    GRID_SLOTS.forEach { slot ->
                        SlotLabelCell(
                            slot     = slot,
                            modifier = Modifier.height(SWIM_LANE_HEIGHT.dp),
                        )
                    }
                }

                // ── Day columns ───────────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScroll)
                ) {
                    Column(modifier = Modifier.verticalScroll(verticalScroll)) {

                        // Day header row (date number + anytime chips)
                        Row {
                            days.forEach { day ->
                                val anytimeEntries = (uiState.grid[day]?.get(TimeOfDay.ANYTIME)
                                    ?: emptyList())
                                    .filter { it.category in categoryFilters }
                                val deadlineEntries = filteredDeadlines[day] ?: emptyList()
                                DayHeader(
                                    date            = day,
                                    isToday         = day == today,
                                    isSelected      = day == selectedDate,
                                    anytimeEntries  = anytimeEntries,
                                    deadlineEntries = deadlineEntries,
                                    onClick         = { onDayClick(day) },
                                    modifier        = Modifier.width(COL_WIDTH),
                                    holidayVnMode   = holidayVnMode,
                                    holidayColor    = holidayColor,
                                )
                            }
                        }

                        HorizontalDivider(
                            color     = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                        )

                        // Swim-lane rows — only Morning / Afternoon / Evening
                        GRID_SLOTS.forEach { slot ->
                            Row(modifier = Modifier.height(SWIM_LANE_HEIGHT.dp)) {
                                days.forEach { day ->
                                    val isWeekend = day.dayOfWeek == DayOfWeek.SATURDAY ||
                                                    day.dayOfWeek == DayOfWeek.SUNDAY
                                    val entries = (uiState.grid[day]?.get(slot) ?: emptyList())
                                        .filter { it.category in categoryFilters }
                                    DaySlotCell(
                                        entries      = entries,
                                        isToday      = day == today,
                                        isWeekend    = isWeekend,
                                        onEntryClick = { detailEntry = it },
                                        modifier     = Modifier
                                            .width(COL_WIDTH)
                                            .fillMaxHeight(),
                                    )
                                }
                            }
                            HorizontalDivider(
                                color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                thickness = 0.5.dp,
                            )
                        }
                    }
                }
            }
        }
    }

    // ── FAB scrim + staggered category menu ──────────────────────────
    EntryFabMenuOverlay(
        showMenu           = showFabMenu,
        onDismiss          = { showFabMenu = false },
        onCategorySelected = { cat -> addEntryCategory = cat; showFabMenu = false },
    )
    } // end Box

    // ── Detail / Add / Edit sheets ────────────────────────────────────
    if (detailEntry != null) {
        EntryDetailSheet(
            entry          = detailEntry!!,
            onEdit         = {
                val toEdit = detailEntry
                detailEntry = null
                editingEntry = toEdit
            },
            onDelete       = {
                detailEntry?.let { actionsVm.deleteEntry(it) }
                detailEntry = null
            },
            onDeleteSeries = {
                detailEntry?.let { actionsVm.deleteEntrySeries(it) }
                detailEntry = null
            },
            onDismiss      = { detailEntry = null },
        )
    }
    if (addEntryCategory != null) {
        AddEntrySheet(
            initialCategory = addEntryCategory!!,
            date            = selectedDate,
            notes           = notes,
            defaultNotifs   = defaultNotifs,
            onSave          = { entity, recType, recEnd ->
                actionsVm.saveEntry(entity, recType, recEnd)
                addEntryCategory = null
            },
            onDismiss = { addEntryCategory = null },
        )
    }
    if (editingEntry != null) {
        AddEntrySheet(
            editEntry       = editingEntry,
            initialCategory = editingEntry!!.category,
            date            = editingEntry!!.date,
            notes           = notes,
            defaultNotifs   = defaultNotifs,
            onSave          = { entity, recType, recEnd ->
                actionsVm.saveEntry(entity, recType, recEnd)
                editingEntry = null
            },
            onDismiss = { editingEntry = null },
        )
    }
}

// ── Slot label cell (narrow, rotated text) ────────────────────────────
@Composable
private fun SlotLabelCell(slot: TimeOfDay, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(LABEL_COL_W.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = slot.shortLabel().uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize      = 9.sp,
                letterSpacing = 1.sp,
                fontWeight    = FontWeight.Medium,
            ),
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.rotate(-90f),
        )
    }
}

// ── Day header cell ───────────────────────────────────────────────────
@Composable
private fun DayHeader(
    date            : LocalDate,
    isToday         : Boolean,
    isSelected      : Boolean,
    anytimeEntries  : List<EntryEntity>,
    deadlineEntries : List<EntryEntity>,
    onClick         : () -> Unit,
    modifier        : Modifier = Modifier,
    holidayVnMode   : String   = "ALL",
    holidayColor    : Color    = Color(0xFF26A69A),
) {
    val locale      = LocalConfiguration.current.locales[0]
    val DAY_ABBR_FMT = remember(locale) { DateTimeFormatter.ofPattern("EEE", locale) }
    val isWeekend  = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    val primaryClr = MaterialTheme.colorScheme.primary

    val colBg = when {
        isToday    -> MaterialTheme.colorScheme.surfaceContainerLow
        isSelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
        isWeekend  -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.4f)
        else       -> Color.Transparent
    }
    val abbrColor = if (isToday) MaterialTheme.colorScheme.primary
                    else         MaterialTheme.colorScheme.onSurfaceVariant
    val numColor  = when {
        isToday    -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
        isWeekend  -> MaterialTheme.colorScheme.secondary
        else       -> MaterialTheme.colorScheme.primary
    }
    val numWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal

    Column(
        modifier = modifier
            .height(HEADER_ROW_H.dp)
            .background(colBg)
            .then(
                if (isToday) Modifier.drawBehind {
                    val stroke = 2.dp.toPx()
                    drawLine(
                        color       = primaryClr,
                        start       = Offset(0f, size.height - stroke / 2),
                        end         = Offset(size.width, size.height - stroke / 2),
                        strokeWidth = stroke,
                    )
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Date number section (HEADER_DATE_H dp) ────────────────
        Spacer(Modifier.height(6.dp))
        Text(
            text       = date.format(DAY_ABBR_FMT).replaceFirstChar { it.uppercase() },
            style      = MaterialTheme.typography.labelSmall,
            color      = abbrColor,
            fontWeight = if (isToday) FontWeight.Bold else null,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text       = date.dayOfMonth.toString(),
            fontSize   = 34.sp,
            fontWeight = numWeight,
            color      = numColor,
            lineHeight = 36.sp,
        )

        // ── Holiday name (if any) ─────────────────────────────────
        VietnamHolidays.getName(date, holidayVnMode)?.let { name ->
            Text(
                text     = name,
                style    = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color    = holidayColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }

        // ── Anytime + deadline chips section (HEADER_ANYTIME_H dp) ──
        Spacer(Modifier.height(4.dp))
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .height(HEADER_ANYTIME_H.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Merge: anytime entries (false) + deadline entries (true)
            val allChips = anytimeEntries.map { it to false } + deadlineEntries.map { it to true }
            val visible  = allChips.take(3)
            val extra    = allChips.size - visible.size
            visible.forEach { (entry, isDeadline) ->
                val chipBg = if (isDeadline) MaterialTheme.colorScheme.errorContainer
                             else            entry.displayBgColor()
                val chipFg = if (isDeadline) MaterialTheme.colorScheme.onErrorContainer
                             else            entry.displayFgColor()
                Text(
                    text     = if (isDeadline) stringResource(R.string.deadline_prefix, entry.title) else entry.title,
                    style    = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color    = chipFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp))
                        .background(chipBg)
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
            if (extra > 0) {
                Text(
                    text  = "+$extra",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Day-slot cell ─────────────────────────────────────────────────────
@Composable
private fun DaySlotCell(
    entries      : List<EntryEntity>,
    isToday      : Boolean,
    isWeekend    : Boolean = false,
    onEntryClick : (EntryEntity) -> Unit = {},
    modifier     : Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                when {
                    isToday   -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.3f)
                    isWeekend -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.2f)
                    else      -> Color.Transparent
                }
            )
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        entries.forEach { entry -> WeekEntryCard(entry, onClick = { onEntryClick(entry) }) }
    }
}

// ── Single entry card inside a swim-lane cell ─────────────────────────
@Composable
private fun WeekEntryCard(entry: EntryEntity, onClick: () -> Unit = {}) {
    val TIME_FMT_W = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val bg = entry.displayBgColor()
    val fg = entry.displayFgColor()

    // Build time string: "09:20 – 12:00"  or  "09:20"  or nothing
    val timeStr = entry.startTime?.let { start ->
        val end = entry.endTime?.let { " – ${it.format(TIME_FMT_W)}" } ?: ""
        "${start.format(TIME_FMT_W)}$end"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text     = entry.title,
            style    = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize   = 11.sp,
                lineHeight = 14.sp,
            ),
            color    = fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (timeStr != null) {
            Text(
                text  = timeStr,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = fg.copy(alpha = 0.80f),
            )
        }
        if (entry.location.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, null, tint = fg.copy(alpha = 0.65f), modifier = Modifier.size(9.dp))
                Spacer(Modifier.width(2.dp))
                Text(
                    text     = entry.location,
                    style    = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color    = fg.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
