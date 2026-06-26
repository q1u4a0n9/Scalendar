package com.scalendar.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.scalendar.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Compact inline calendar grid — tap a day to select, arrows to navigate months.
 *
 * [selectedDate] controls which day gets the filled-circle highlight.
 * [onDateSelected] is called every time the user taps a day.
 */
@Composable
fun InlineCalendarPicker(
    selectedDate   : LocalDate,
    onDateSelected : (LocalDate) -> Unit,
    modifier       : Modifier = Modifier,
) {
    var displayMonth by remember(selectedDate) {
        mutableStateOf(YearMonth.from(selectedDate))
    }
    val today = remember { LocalDate.now() }

    val locale       = LocalConfiguration.current.locales[0]
    val MONTH_HDR_FMT= remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }
    val DOW_LABELS   = remember(locale) {
        if (locale.language == "vi") listOf("T2","T3","T4","T5","T6","T7","CN")
        else listOf("Mo","Tu","We","Th","Fr","Sa","Su")
    }

    Column(modifier = modifier.fillMaxWidth()) {

        // ── Month navigation header ───────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { displayMonth = displayMonth.minusMonths(1) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.content_prev_month),
                     tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text      = displayMonth.atDay(1).format(MONTH_HDR_FMT)
                                .replaceFirstChar { it.uppercase() },
                style     = MaterialTheme.typography.titleSmall,
                color     = MaterialTheme.colorScheme.onSurface,
                modifier  = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { displayMonth = displayMonth.plusMonths(1) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.content_next_month),
                     tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ── Day-of-week header row ────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth()) {
            DOW_LABELS.forEach { label ->
                Text(
                    text      = label,
                    style     = MaterialTheme.typography.labelSmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier  = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── Calendar grid ─────────────────────────────────────────────
        // Monday = index 0 in grid row, Sunday = index 6
        val firstOfMonth = displayMonth.atDay(1)
        // dayOfWeek.value: Mon=1 … Sun=7  →  col index 0..6
        val startOffset  = firstOfMonth.dayOfWeek.value - 1
        val daysInMonth  = displayMonth.lengthOfMonth()
        val totalCells   = startOffset + daysInMonth
        val rowCount     = (totalCells + 6) / 7   // ceil

        repeat(rowCount) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            ) {
                repeat(7) { col ->
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - startOffset + 1

                    Box(
                        modifier         = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dayNumber in 1..daysInMonth) {
                            val day        = displayMonth.atDay(dayNumber)
                            val isSelected = day == selectedDate
                            val isToday    = day == today

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isToday    -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else       -> Color.Transparent
                                        }
                                    )
                                    .clickable { onDateSelected(day) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text      = dayNumber.toString(),
                                    fontSize  = 13.sp,
                                    fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                                    color     = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        isToday    -> MaterialTheme.colorScheme.primary
                                        else       -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
