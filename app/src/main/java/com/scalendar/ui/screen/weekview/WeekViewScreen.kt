package com.scalendar.ui.screen.weekview

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.CalendarToday
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
import com.scalendar.data.model.TimeOfDay
import com.scalendar.ui.theme.displayBgColor
import com.scalendar.ui.theme.displayFgColor
import com.scalendar.ui.shared.SharedCalendarViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── Formatters ────────────────────────────────────────────────────────
private val MONTH_FMT    = DateTimeFormatter.ofPattern("MMMM",  Locale.forLanguageTag("vi"))
private val DAY_ABBR_FMT = DateTimeFormatter.ofPattern("EEE",   Locale.forLanguageTag("vi"))
private val DAY_SHORT    = DateTimeFormatter.ofPattern("d",      Locale.forLanguageTag("vi"))
private val MONTH_SHORT  = DateTimeFormatter.ofPattern("MMM",   Locale.forLanguageTag("vi"))

private val SLOTS_ORDERED = listOf(
    TimeOfDay.ANYTIME,
    TimeOfDay.MORNING,
    TimeOfDay.AFTERNOON,
    TimeOfDay.EVENING,
)

// ── Short vertical labels ─────────────────────────────────────────────
private fun TimeOfDay.shortLabel(): String = when (this) {
    TimeOfDay.ANYTIME   -> "Bất kỳ"
    TimeOfDay.MORNING   -> "Sáng"
    TimeOfDay.AFTERNOON -> "Chiều"
    TimeOfDay.EVENING   -> "Tối"
}

// ── Week-range subtitle (e.g. "18 - 24 tháng 5" or "28 th5 - 3 th6") ─
private fun weekRangeLabel(start: LocalDate): String {
    val end = start.plusDays(6)
    return if (start.month == end.month) {
        "${start.format(DAY_SHORT)} - ${end.format(DAY_SHORT)} ${start.format(MONTH_SHORT)}"
    } else {
        "${start.format(DAY_SHORT)} ${start.format(MONTH_SHORT)} – ${end.format(DAY_SHORT)} ${end.format(MONTH_SHORT)}"
    }
}

// ── Layout constants ──────────────────────────────────────────────────
private const val SWIM_LANE_HEIGHT = 110   // dp per row
private const val LABEL_COL_W     = 40    // dp — narrow vertical-label column
private val       COL_WIDTH       = 108.dp // dp per day column  (design: (800-40)/7 ≈ 108px)

// ── Screen ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekViewScreen(
    sharedVm    : SharedCalendarViewModel,
    onOpenDrawer: () -> Unit,
    onDayClick  : (LocalDate) -> Unit,
    viewModel   : WeekViewViewModel = hiltViewModel(),
) {
    val selectedDate    by sharedVm.selectedDate.collectAsState()
    val categoryFilters by sharedVm.categoryFilters.collectAsState()
    val uiState         by viewModel.uiState.collectAsState()
    val today           = remember { LocalDate.now() }

    LaunchedEffect(selectedDate) { viewModel.setDate(selectedDate) }

    Scaffold(
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
                            text  = weekRangeLabel(uiState.weekStart),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { /* search – placeholder */ }) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "Tìm kiếm",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { sharedVm.selectDate(LocalDate.now()) }) {
                        Icon(
                            Icons.Outlined.CalendarToday,
                            contentDescription = "Hôm nay",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->

        val days = (0..6).map { uiState.weekStart.plusDays(it.toLong()) }

        val horizontalScroll = rememberScrollState()
        val verticalScroll   = rememberScrollState()

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Row(modifier = Modifier.fillMaxSize()) {

                // ── Narrow vertical-label column ──────────────────────
                Column(modifier = Modifier.verticalScroll(verticalScroll)) {
                    // Spacer matching sticky header row height
                    Spacer(Modifier.height(HEADER_ROW_HEIGHT.dp))
                    SLOTS_ORDERED.forEach { slot ->
                        SlotLabelCell(slot = slot, modifier = Modifier.height(SWIM_LANE_HEIGHT.dp))
                    }
                }

                // ── Day columns (horizontal + vertical scroll) ────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScroll)
                ) {
                    Column(modifier = Modifier.verticalScroll(verticalScroll)) {

                        // Sticky-style day header row
                        Row {
                            days.forEach { day ->
                                DayHeader(
                                    date       = day,
                                    isToday    = day == today,
                                    isSelected = day == selectedDate,
                                    onClick    = { onDayClick(day) },
                                    modifier   = Modifier.width(COL_WIDTH),
                                )
                            }
                        }

                        HorizontalDivider(
                            color     = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                        )

                        // Swimlane rows
                        SLOTS_ORDERED.forEach { slot ->
                            Row(modifier = Modifier.height(SWIM_LANE_HEIGHT.dp)) {
                                days.forEach { day ->
                                    val entries = (uiState.grid[day]?.get(slot) ?: emptyList())
                                        .filter { it.category in categoryFilters }
                                    DaySlotCell(
                                        entries  = entries,
                                        isToday  = day == today,
                                        modifier = Modifier
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
}

private const val HEADER_ROW_HEIGHT = 72  // dp

// ── Slot label cell (narrow, vertical text) ───────────────────────────
@Composable
private fun SlotLabelCell(slot: TimeOfDay, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(LABEL_COL_W.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text     = slot.shortLabel().uppercase(),
            style    = MaterialTheme.typography.labelSmall.copy(
                fontSize     = 9.sp,
                letterSpacing = 1.sp,
                fontWeight   = FontWeight.Medium,
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
    date      : LocalDate,
    isToday   : Boolean,
    isSelected: Boolean,
    onClick   : () -> Unit,
    modifier  : Modifier = Modifier,
) {
    val isWeekend  = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    val primaryClr = MaterialTheme.colorScheme.primary

    // Column bg: today = surfaceContainerLow, selected ≠ today = subtle tint
    val colBg = when {
        isToday    -> MaterialTheme.colorScheme.surfaceContainerLow
        isSelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
        else       -> Color.Transparent
    }

    // Abbrev + number colours
    val abbrColor = when {
        isToday -> MaterialTheme.colorScheme.primary
        else    -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val numColor = when {
        isToday    -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
        isWeekend  -> MaterialTheme.colorScheme.secondary
        else       -> MaterialTheme.colorScheme.primary
    }
    val numWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal

    Column(
        modifier            = modifier
            .height(HEADER_ROW_HEIGHT.dp)
            .background(colBg)
            // today: 2 dp primary underline
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
        verticalArrangement = Arrangement.Center,
    ) {
        // Abbreviated day name (Mon/Tue…)
        Text(
            text  = date.format(DAY_ABBR_FMT).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = abbrColor,
            fontWeight = if (isToday) FontWeight.Bold else null,
        )
        Spacer(Modifier.height(2.dp))
        // Day number — large display style
        Text(
            text       = date.dayOfMonth.toString(),
            fontSize   = 34.sp,
            fontWeight = numWeight,
            color      = numColor,
            lineHeight = 36.sp,
        )
    }
}

// ── Day-slot cell ─────────────────────────────────────────────────────
@Composable
private fun DaySlotCell(
    entries : List<EntryEntity>,
    isToday : Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                if (isToday) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        entries.take(3).forEach { entry ->
            val bg = entry.displayBgColor()
            val fg = entry.displayFgColor()
            Text(
                text     = entry.title,
                style    = MaterialTheme.typography.labelSmall,
                color    = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(bg)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        if (entries.size > 3) {
            Text(
                text  = "+${entries.size - 3}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

