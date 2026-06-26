package com.scalendar.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle

// ── Utility ───────────────────────────────────────────────────────────
/**
 * Returns the next 5-minute boundary after [LocalTime.now()].
 * e.g. 21:53 → 21:55, 21:55 → 22:00, 23:58 → 00:00 (next day wraps).
 */
fun nextRoundedTime(): LocalTime {
    val now          = LocalTime.now()
    val remainder    = now.minute % 5
    val minutesToAdd = if (remainder == 0) 5L else (5 - remainder).toLong()
    return now.plusMinutes(minutesToAdd).withSecond(0).withNano(0)
}

// ── Single wheel column ───────────────────────────────────────────────
/**
 * A drum-roll style scrollable picker column.
 *
 * [items]        – display strings for each selectable value
 * [startIndex]   – which item index to show initially at center
 * [onSelected]   – called with the index whenever scrolling stops
 * [itemHeight]   – height of each item row (default 44 dp)
 * [visibleCount] – how many items are visible at once; must be odd (default 5)
 * [circular]     – when true the list wraps around infinitely (for hours/minutes)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelColumn(
    items        : List<String>,
    startIndex   : Int,
    onSelected   : (Int) -> Unit,
    modifier     : Modifier = Modifier,
    itemHeight   : Dp = 44.dp,
    visibleCount : Int = 5,
    circular     : Boolean = false,
) {
    val halfCount = visibleCount / 2

    if (circular && items.isNotEmpty()) {
        // ── Circular / infinite-scroll path ──────────────────────────
        // Build a large virtual list (1 000 × items.size) and start in the
        // middle so the user can scroll freely in both directions.
        val repeatCount  = 1_000
        val virtualCount = items.size * repeatCount

        // We want `startIndex` to appear at the CENTER on first draw.
        // center = firstVisibleItemIndex + halfCount
        // → firstVisible = (midCycle + startIndex) - halfCount
        val midCycle        = remember(items.size) { (repeatCount / 2) * items.size }
        val initialFirstVis = remember(items.size, startIndex, halfCount) {
            (midCycle + startIndex - halfCount).coerceAtLeast(0)
        }

        val listState     = rememberLazyListState(initialFirstVisibleItemIndex = initialFirstVis)
        val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

        // The virtual index of the center (highlighted) row
        val centerVirtIdx by remember { derivedStateOf { listState.firstVisibleItemIndex + halfCount } }
        // The real item index (0 … items.size-1)
        val selectedReal  by remember { derivedStateOf { centerVirtIdx % items.size } }

        // Notify caller when scroll settles
        LaunchedEffect(listState.isScrollInProgress) {
            if (!listState.isScrollInProgress) onSelected(selectedReal)
        }

        Box(
            modifier         = modifier.height(itemHeight * visibleCount),
            contentAlignment = Alignment.Center,
        ) {
            // Center-row highlight lines
            Column(modifier = Modifier.fillMaxWidth().align(Alignment.Center)) {
                HorizontalDivider(
                    color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    thickness = 1.dp,
                )
                Spacer(Modifier.height(itemHeight - 2.dp))
                HorizontalDivider(
                    color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    thickness = 1.dp,
                )
            }

            LazyColumn(
                state         = listState,
                flingBehavior = flingBehavior,
                modifier      = Modifier.fillMaxWidth(),
            ) {
                items(
                    count = virtualCount,
                    key   = { it },
                ) { index ->
                    val realIndex  = index % items.size
                    val item       = items[realIndex]
                    val isSelected = index == centerVirtIdx

                    Box(
                        modifier         = Modifier
                            .height(itemHeight)
                            .fillMaxWidth()
                            .alpha(if (isSelected) 1f else 0.38f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text       = item,
                            fontSize   = if (isSelected) 18.sp else 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color      = if (isSelected) MaterialTheme.colorScheme.onSurface
                                         else            MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

    } else {
        // ── Non-circular path (original implementation) ───────────────
        val displayItems = remember(items, halfCount) {
            buildList {
                repeat(halfCount) { add("") }
                addAll(items)
                repeat(halfCount) { add("") }
            }
        }

        val listState     = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
        val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

        val selectedIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

        LaunchedEffect(listState.isScrollInProgress) {
            if (!listState.isScrollInProgress) {
                val idx = listState.firstVisibleItemIndex
                if (idx in items.indices) onSelected(idx)
            }
        }

        Box(
            modifier         = modifier.height(itemHeight * visibleCount),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
            ) {
                HorizontalDivider(
                    color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    thickness = 1.dp,
                )
                Spacer(Modifier.height(itemHeight - 2.dp))
                HorizontalDivider(
                    color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    thickness = 1.dp,
                )
            }

            LazyColumn(
                state         = listState,
                flingBehavior = flingBehavior,
                modifier      = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(displayItems) { index, item ->
                    val isSelected = index == selectedIndex + halfCount
                    val itemAlpha  = when {
                        item.isEmpty() -> 0f
                        isSelected     -> 1f
                        else           -> 0.38f
                    }
                    Box(
                        modifier         = Modifier
                            .height(itemHeight)
                            .fillMaxWidth()
                            .alpha(itemAlpha),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.isNotEmpty()) {
                            Text(
                                text       = item,
                                fontSize   = if (isSelected) 18.sp else 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color      = if (isSelected) MaterialTheme.colorScheme.onSurface
                                             else            MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Three-column date picker ──────────────────────────────────────────
/**
 * Drum-roll date picker with three columns: Day / Month / Year.
 */
@Composable
fun WheelDatePicker(
    date          : LocalDate,
    onDateChanged : (LocalDate) -> Unit,
    modifier      : Modifier = Modifier,
    yearRange     : IntRange  = (date.year - 5)..(date.year + 5),
) {
    val locale = LocalConfiguration.current.locales[0]
    val months = remember(locale) {
        Month.entries.map { m ->
            m.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercase(locale) }
        }
    }
    val years = remember(yearRange) { yearRange.map { it.toString() } }

    var selectedDay   by remember { mutableIntStateOf(date.dayOfMonth) }
    var selectedMonth by remember { mutableIntStateOf(date.monthValue) }
    var selectedYear  by remember { mutableIntStateOf(date.year) }

    val daysInMonth = remember(selectedMonth, selectedYear) {
        YearMonth.of(selectedYear, selectedMonth).lengthOfMonth()
    }
    val days = remember(daysInMonth) { (1..daysInMonth).map { it.toString() } }

    LaunchedEffect(daysInMonth) {
        if (selectedDay > daysInMonth) selectedDay = daysInMonth
    }

    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        WheelColumn(
            items      = days,
            startIndex = (selectedDay - 1).coerceIn(0, days.lastIndex),
            onSelected = { idx ->
                selectedDay = idx + 1
                onDateChanged(safeDate(selectedYear, selectedMonth, selectedDay))
            },
            modifier = Modifier.weight(1f),
        )
        WheelColumn(
            items      = months,
            startIndex = selectedMonth - 1,
            onSelected = { idx ->
                selectedMonth = idx + 1
                val clamped   = selectedDay.coerceIn(1, YearMonth.of(selectedYear, selectedMonth).lengthOfMonth())
                selectedDay   = clamped
                onDateChanged(safeDate(selectedYear, selectedMonth, selectedDay))
            },
            modifier = Modifier.weight(1.4f),
        )
        WheelColumn(
            items      = years,
            startIndex = years.indexOf(selectedYear.toString()).coerceAtLeast(0),
            onSelected = { idx ->
                selectedYear  = yearRange.first + idx
                val clamped   = selectedDay.coerceIn(1, YearMonth.of(selectedYear, selectedMonth).lengthOfMonth())
                selectedDay   = clamped
                onDateChanged(safeDate(selectedYear, selectedMonth, selectedDay))
            },
            modifier = Modifier.weight(1f),
        )
    }
}

private fun safeDate(year: Int, month: Int, day: Int): LocalDate =
    LocalDate.of(year, month, day.coerceIn(1, YearMonth.of(year, month).lengthOfMonth()))

// ── Two-column time picker (hours | minutes) ──────────────────────────
/**
 * Inline drum-roll time picker.
 * Hours  : 00–23, circular scroll.
 * Minutes: 00, 05, 10 … 55 (step 5), circular scroll — matches Google Calendar.
 */
@Composable
fun WheelTimePicker(
    time          : LocalTime,
    onTimeChanged : (LocalTime) -> Unit,
    modifier      : Modifier = Modifier,
    itemHeight    : Dp       = 44.dp,
) {
    // 24 items:  "00" … "23"
    val hours   = remember { (0..23).map { "%02d".format(it) } }
    // 12 items:  "00", "05", "10" … "55"
    val minutes = remember { (0..59 step 5).map { "%02d".format(it) } }

    // Snap the minute to the nearest 5-min grid position
    var currentHour      by remember(time) { mutableIntStateOf(time.hour) }
    var currentMinuteIdx by remember(time) { mutableIntStateOf(time.minute / 5) } // index 0-11

    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        // Hours wheel – circular
        WheelColumn(
            items      = hours,
            startIndex = currentHour,
            onSelected = { i ->
                currentHour = i
                onTimeChanged(LocalTime.of(i, currentMinuteIdx * 5))
            },
            itemHeight = itemHeight,
            circular   = true,
            modifier   = Modifier.weight(1f),
        )

        // Separator
        Text(
            text     = ":",
            style    = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp),
        )

        // Minutes wheel – circular, step 5
        WheelColumn(
            items      = minutes,
            startIndex = currentMinuteIdx,
            onSelected = { idx ->
                currentMinuteIdx = idx
                onTimeChanged(LocalTime.of(currentHour, idx * 5))
            },
            itemHeight = itemHeight,
            circular   = true,
            modifier   = Modifier.weight(1f),
        )
    }
}
