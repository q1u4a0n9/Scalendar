package com.scalendar.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import java.time.LocalDate
import java.time.YearMonth

// ── Single wheel column ───────────────────────────────────────────────
/**
 * A drum-roll style scrollable picker column.
 *
 * [items]        – display strings for each selectable value
 * [startIndex]   – which item index to show initially at center
 * [onSelected]   – called with the index whenever scrolling stops
 * [itemHeight]   – height of each item row (default 44 dp)
 * [visibleCount] – how many items are visible at once; must be odd (default 5)
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
) {
    val halfCount = visibleCount / 2

    // Pad with empty sentinel strings so first/last items can be centered
    val displayItems = remember(items, halfCount) {
        buildList {
            repeat(halfCount) { add("") }
            addAll(items)
            repeat(halfCount) { add("") }
        }
    }

    val listState    = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // selectedIndex = firstVisibleItemIndex (see design comment in WheelPicker.kt)
    val selectedIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    // Notify caller when scrolling settles
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val idx = listState.firstVisibleItemIndex
            if (idx in items.indices) onSelected(idx)
        }
    }

    Box(
        modifier = modifier.height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center,
    ) {
        // Center-row highlight (two divider lines)
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
                val isSelected  = index == selectedIndex + halfCount
                val itemAlpha   = when {
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

// ── Three-column date picker ──────────────────────────────────────────
/**
 * Drum-roll date picker with three columns: Day / Month / Year.
 * The [date] parameter controls the initial position; [onDateChanged]
 * is called on every settlement.
 */
@Composable
fun WheelDatePicker(
    date          : LocalDate,
    onDateChanged : (LocalDate) -> Unit,
    modifier      : Modifier = Modifier,
    yearRange     : IntRange  = (date.year - 5)..(date.year + 5),
) {
    val months = remember {
        listOf("Thg 1","Thg 2","Thg 3","Thg 4","Thg 5","Thg 6",
               "Thg 7","Thg 8","Thg 9","Thg 10","Thg 11","Thg 12")
    }
    val years  = remember(yearRange) { yearRange.map { it.toString() } }

    // Mutable state for each wheel's current value
    var selectedDay   by remember { mutableIntStateOf(date.dayOfMonth) }
    var selectedMonth by remember { mutableIntStateOf(date.monthValue) }   // 1-based
    var selectedYear  by remember { mutableIntStateOf(date.year) }

    // Days in selected month/year (handles Feb, etc.)
    val daysInMonth = remember(selectedMonth, selectedYear) {
        YearMonth.of(selectedYear, selectedMonth).lengthOfMonth()
    }
    val days = remember(daysInMonth) { (1..daysInMonth).map { it.toString() } }

    // Clamp day if it exceeds new month length
    LaunchedEffect(daysInMonth) {
        if (selectedDay > daysInMonth) selectedDay = daysInMonth
    }

    Row(
        modifier            = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment   = Alignment.CenterVertically,
    ) {
        // Day wheel
        WheelColumn(
            items      = days,
            startIndex = (selectedDay - 1).coerceIn(0, days.lastIndex),
            onSelected = { idx ->
                selectedDay = idx + 1
                onDateChanged(safeDate(selectedYear, selectedMonth, selectedDay))
            },
            modifier = Modifier.weight(1f),
        )

        // Month wheel
        WheelColumn(
            items      = months,
            startIndex = selectedMonth - 1,
            onSelected = { idx ->
                selectedMonth = idx + 1
                val clamped   = (selectedDay).coerceIn(1, YearMonth.of(selectedYear, selectedMonth).lengthOfMonth())
                selectedDay   = clamped
                onDateChanged(safeDate(selectedYear, selectedMonth, selectedDay))
            },
            modifier = Modifier.weight(1.4f),
        )

        // Year wheel
        WheelColumn(
            items      = years,
            startIndex = years.indexOf(selectedYear.toString()).coerceAtLeast(0),
            onSelected = { idx ->
                selectedYear  = yearRange.first + idx
                val clamped   = (selectedDay).coerceIn(1, YearMonth.of(selectedYear, selectedMonth).lengthOfMonth())
                selectedDay   = clamped
                onDateChanged(safeDate(selectedYear, selectedMonth, selectedDay))
            },
            modifier = Modifier.weight(1f),
        )
    }
}

private fun safeDate(year: Int, month: Int, day: Int): LocalDate =
    LocalDate.of(year, month, day.coerceIn(1, YearMonth.of(year, month).lengthOfMonth()))
