package com.scalendar.ui.screen.monthview

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scalendar.ui.theme.displayBgColor
import com.scalendar.ui.shared.SharedCalendarViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_ONLY_FMT = DateTimeFormatter.ofPattern("MMM",      Locale.forLanguageTag("vi"))
private val YEAR_HDR_FMT   = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("vi"))
private val DOW_LABELS     = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

// ── Chip item types ───────────────────────────────────────────────────
// Month → tappable chip; YearLabel → non-tappable year separator
private sealed class ChipItem {
    data class Month(val ym: YearMonth) : ChipItem()
    data class YearLabel(val year: Int) : ChipItem()
}

/**
 * Build a flat ±[radius] month list with year-separator items injected
 * whenever the year rolls over.  The first month never gets a leading year label
 * (the user already knows the year from context); labels only appear at transitions.
 *
 * Result example around Dec 2026:
 *   … Month(Nov-26) · Month(Dec-26) · YearLabel(2027) · Month(Jan-27) …
 */
private fun buildChipList(today: YearMonth, radius: Int = 60): List<ChipItem> {
    val from = today.minusMonths(radius.toLong())
    val to   = today.plusMonths(radius.toLong())
    val list = mutableListOf<ChipItem>()
    var cur      = from
    var lastYear = -1
    while (!cur.isAfter(to)) {
        if (cur.year != lastYear) {
            if (list.isNotEmpty()) {           // skip leading year label
                list += ChipItem.YearLabel(cur.year)
            }
            lastYear = cur.year
        }
        list += ChipItem.Month(cur)
        cur = cur.plusMonths(1)
    }
    return list
}

// ── Screen ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthViewScreen(
    sharedVm    : SharedCalendarViewModel,
    onOpenDrawer: () -> Unit,
    onDayClick  : (LocalDate) -> Unit,
    viewModel   : MonthViewViewModel = hiltViewModel(),
) {
    val selectedDate    by sharedVm.selectedDate.collectAsState()
    val categoryFilters by sharedVm.categoryFilters.collectAsState()
    val uiState         by viewModel.uiState.collectAsState()
    val today           = remember { LocalDate.now() }

    val filteredEntries = remember(uiState.entries, categoryFilters) {
        uiState.entries.mapValues { (_, list) ->
            list.filter { it.category in categoryFilters }
        }
    }

    LaunchedEffect(selectedDate) { viewModel.setMonth(selectedDate) }

    // ── Chip list: fixed at composition time, never rebuilt ───────────
    val chipList = remember {
        buildChipList(YearMonth.now())
    }

    // Start with current month visible (offset -2 so there's context to the left)
    val initialIndex = remember(chipList) {
        val idx = chipList.indexOfFirst { it is ChipItem.Month && it.ym == YearMonth.now() }
        maxOf(0, idx - 2)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    // When the displayed month changes (chip tap OR external nav), scroll to it
    LaunchedEffect(uiState.yearMonth) {
        val idx = chipList.indexOfFirst { it is ChipItem.Month && it.ym == uiState.yearMonth }
        if (idx >= 0) {
            listState.animateScrollToItem(maxOf(0, idx - 2))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = uiState.yearMonth.atDay(1).format(YEAR_HDR_FMT)
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.previousMonth() }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Tháng trước")
                    }
                    IconButton(onClick = { viewModel.nextMonth() }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Tháng sau")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // ── Month chip strip ──────────────────────────────────────
            LazyRow(
                state                 = listState,
                contentPadding        = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items = chipList,
                    key   = { item ->
                        when (item) {
                            is ChipItem.Month     -> "m_${item.ym}"
                            is ChipItem.YearLabel -> "y_${item.year}"
                        }
                    },
                ) { item ->
                    when (item) {
                        is ChipItem.Month ->
                            MonthChip(
                                ym         = item.ym,
                                isSelected = item.ym == uiState.yearMonth,
                                onClick    = { viewModel.setMonth(item.ym.atDay(1)) },
                            )
                        is ChipItem.YearLabel ->
                            YearSeparatorChip(year = item.year)
                    }
                }
            }

            // Day-of-week header
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                DOW_LABELS.forEach { label ->
                    Text(
                        text      = label,
                        modifier  = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style     = MaterialTheme.typography.labelSmall,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(
                color    = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            // Calendar grid — weight(1f) fills all remaining height; no scroll needed
            MonthGrid(
                yearMonth    = uiState.yearMonth,
                entries      = filteredEntries,
                today        = today,
                selectedDate = selectedDate,
                onDayClick   = onDayClick,
                modifier     = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

// ── Month chip — only shows short month name ("thg 5") ────────────────
@Composable
private fun MonthChip(ym: YearMonth, isSelected: Boolean, onClick: () -> Unit) {
    val bg     = if (isSelected) MaterialTheme.colorScheme.primary
                 else            MaterialTheme.colorScheme.surface
    val fg     = if (isSelected) MaterialTheme.colorScheme.onPrimary
                 else            MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                 else            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = ym.atDay(1).format(MONTH_ONLY_FMT),   // e.g. "thg 5"
            style      = MaterialTheme.typography.labelSmall,
            color      = fg,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ── Year separator — non-tappable label between Dec and Jan ───────────
@Composable
private fun YearSeparatorChip(year: Int) {
    Box(
        modifier         = Modifier.padding(horizontal = 2.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = year.toString(),
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.outline,
        )
    }
}

// ── Calendar grid ─────────────────────────────────────────────────────
@Composable
private fun MonthGrid(
    yearMonth   : YearMonth,
    entries     : Map<LocalDate, List<com.scalendar.data.database.entity.EntryEntity>>,
    today       : LocalDate,
    selectedDate: LocalDate,
    onDayClick  : (LocalDate) -> Unit,
    modifier    : Modifier = Modifier,
) {
    val firstDay    = yearMonth.atDay(1)
    val startOffset = (firstDay.dayOfWeek.value - 1).coerceIn(0, 6)
    val daysInMonth = yearMonth.lengthOfMonth()

    val cells: List<LocalDate?> = buildList {
        repeat(startOffset) { add(null) }
        (1..daysInMonth).forEach { add(yearMonth.atDay(it)) }
        val remainder = (7 - size % 7) % 7
        repeat(remainder) { add(null) }
    }

    // modifier already carries weight(1f)+fillMaxWidth from the caller
    Column(modifier = modifier.padding(horizontal = 8.dp)) {
        cells.chunked(7).forEach { week ->
            // weight(1f) inside ColumnScope → every week row gets equal share of height
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                week.forEach { day ->
                    DayCell(
                        date         = day,
                        isToday      = day == today,
                        isSelected   = day == selectedDate,
                        entries      = if (day != null) entries[day] ?: emptyList() else emptyList(),
                        onClick      = { if (day != null) onDayClick(day) },
                        modifier     = Modifier.weight(1f),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

// ── Day cell ──────────────────────────────────────────────────────────
@Composable
private fun DayCell(
    date      : LocalDate?,
    isToday   : Boolean,
    isSelected: Boolean,
    entries   : List<com.scalendar.data.database.entity.EntryEntity>,
    onClick   : () -> Unit,
    modifier  : Modifier = Modifier,
) {
    Column(
        modifier            = modifier
            .fillMaxHeight()
            .clickable(enabled = date != null, onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        if (date == null) return@Column

        val numBg = when {
            isToday    -> MaterialTheme.colorScheme.primary
            isSelected -> MaterialTheme.colorScheme.secondaryContainer
            else       -> Color.Transparent
        }
        val numFg = when {
            isToday    -> MaterialTheme.colorScheme.onPrimary
            isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
            else       -> MaterialTheme.colorScheme.onBackground
        }

        // ── Day number circle ─────────────────────────────────────────
        Box(
            modifier         = Modifier
                .size(24.dp)       // slightly smaller circle
                .clip(CircleShape)
                .background(numBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = numFg,
            )
        }

        Spacer(Modifier.height(2.dp))

        // ── Entry chips: fill remaining height dynamically ────────────
        // BoxWithConstraints measures the actual remaining height so we
        // show exactly as many chips as can fit, then "+N" for the rest.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val CHIP_H = 15.dp   // approximate height per chip (9sp text + clip padding)
            val GAP    = 1.dp
            val maxVisible = maxOf(0, ((maxHeight + GAP) / (CHIP_H + GAP)).toInt())
            val visible    = entries.take(maxVisible)
            val overflow   = entries.size - visible.size

            Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                visible.forEach { entry ->
                    Text(
                        text     = entry.title,
                        style    = MaterialTheme.typography.labelSmall.copy(
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color    = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(2.dp))
                            .background(entry.displayBgColor())
                            .padding(horizontal = 2.dp),
                    )
                }
                if (overflow > 0) {
                    Text(
                        text  = "+$overflow",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
