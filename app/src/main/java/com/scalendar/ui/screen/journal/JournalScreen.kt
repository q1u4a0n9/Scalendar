package com.scalendar.ui.screen.journal

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.ui.theme.displayBgColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_TITLE_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("vi"))

// ── Vietnamese short day names (T2…T7 / CN) ───────────────────────────
private fun LocalDate.shortDayName(): String = when (dayOfWeek) {
    DayOfWeek.MONDAY    -> "T2"
    DayOfWeek.TUESDAY   -> "T3"
    DayOfWeek.WEDNESDAY -> "T4"
    DayOfWeek.THURSDAY  -> "T5"
    DayOfWeek.FRIDAY    -> "T6"
    DayOfWeek.SATURDAY  -> "T7"
    DayOfWeek.SUNDAY    -> "CN"
}

// ── Month accent gradients ────────────────────────────────────────────
private val MONTH_GRADIENTS = listOf(
    listOf(Color(0xFF4A654E), Color(0xFF8BA88E)),  // forest green
    listOf(Color(0xFF7D562D), Color(0xFFFFCA98)),  // warm brown
    listOf(Color(0xFF7A5644), Color(0xFFEBBDA6)),  // earthy orange
    listOf(Color(0xFF334D38), Color(0xFFCCEACE)),  // deep green
    listOf(Color(0xFF623F18), Color(0xFFFFDCBD)),  // dark brown
    listOf(Color(0xFF603F2F), Color(0xFFFFDBCB)),  // muted terracotta
)

@Composable
private fun monthGradient(ym: YearMonth): Brush {
    val colors = MONTH_GRADIENTS[(ym.monthValue - 1) % MONTH_GRADIENTS.size]
    return Brush.horizontalGradient(colors)
}

// ── Screen ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    onDayClick: (LocalDate) -> Unit = {},
    viewModel : JournalViewModel = hiltViewModel(),
) {
    val uiState      by viewModel.uiState.collectAsState()
    val notesByDate  by viewModel.notesByDate.collectAsState()
    val today        = remember { LocalDate.now() }

    // Pre-group entries by date so we don't recompute on every frame
    val groupedData = remember(uiState.groups) {
        uiState.groups.map { group ->
            group to group.entries
                .groupBy { it.date }
                .entries
                .sortedBy { it.key }   // already sorted, but be explicit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = "Nhật ký",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (uiState.groups.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = "Chưa có sự kiện nào",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding      = PaddingValues(
                start  = 16.dp, end = 16.dp,
                top    = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            groupedData.forEach { (group, byDate) ->

                // Month header card
                item(key = "hdr_${group.yearMonth}") {
                    MonthGroupHeader(group.yearMonth)
                    Spacer(Modifier.height(16.dp))
                }

                // One DateGroup per unique date
                byDate.forEach { (date, dateEntries) ->
                    item(key = "${group.yearMonth}_$date") {
                        DateGroup(
                            date       = date,
                            entries    = dateEntries,
                            notes      = notesByDate[date] ?: emptyList(),
                            isToday    = date == today,
                            onToggle   = viewModel::toggleComplete,
                            onDayClick = onDayClick,
                        )
                    }
                }

                item(key = "gap_${group.yearMonth}") {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// ── Month header card ─────────────────────────────────────────────────
@Composable
private fun MonthGroupHeader(ym: YearMonth) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(monthGradient(ym))
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text  = ym.atDay(1).format(MONTH_TITLE_FMT).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
    }
}

// ── Date group: left date column + right entries column ───────────────
// Design: date col has day-abbr (small, uppercase) + day-number (30sp, light)
//         today: number in primary + small primary dot below
@Composable
private fun DateGroup(
    date      : LocalDate,
    entries   : List<EntryEntity>,
    notes     : List<com.scalendar.data.database.entity.NoteEntity>,  // same-day notes
    isToday   : Boolean,
    onToggle  : (EntryEntity) -> Unit,
    onDayClick: (LocalDate) -> Unit,
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // ── Date column (52 dp fixed width) — tappable → DayView ─────
        Column(
            modifier            = Modifier
                .width(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onDayClick(date) }
                .padding(top = 2.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Short day name: "T2" … "T7" / "CN"
            Text(
                text  = date.shortDayName(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight    = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                ),
                color = if (isToday) MaterialTheme.colorScheme.primary
                        else         MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            // Day number — large, light weight, matches design "text-3xl"
            Text(
                text       = date.dayOfMonth.toString(),
                fontSize   = 30.sp,
                fontWeight = FontWeight.Light,
                lineHeight = 34.sp,
                color      = if (isToday) MaterialTheme.colorScheme.primary
                             else         MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Today dot (small filled circle below the number)
            if (isToday) {
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        // ── Entries + same-day notes ──────────────────────────────────
        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            entries.forEach { entry ->
                JournalEntryCard(
                    entry    = entry,
                    onToggle = { onToggle(entry) },
                )
            }
            // Notes written on this date auto-show as snippets below entries
            notes.forEach { note ->
                NoteSnippet(note = note)
            }
        }
    }
}

// ── Entry card — no inline date (handled by DateGroup) ────────────────
@Composable
private fun JournalEntryCard(
    entry   : EntryEntity,
    onToggle: () -> Unit,
) {
    val alpha       = if (entry.isCompleted) 0.55f else 1f
    val accentColor = entry.displayBgColor()

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent stripe
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                .background(accentColor),
        )

        Spacer(Modifier.width(12.dp))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = entry.title,
                style    = MaterialTheme.typography.bodyLarge.let { s ->
                    if (entry.isCompleted) s.copy(textDecoration = TextDecoration.LineThrough) else s
                },
                color    = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            // Category badge
            Text(
                text     = entry.category.displayName(),
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor.copy(alpha = 0.25f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        Spacer(Modifier.width(8.dp))

        // Toggle complete
        IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
            if (entry.isCompleted) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Hoàn thành",
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = "Đánh dấu hoàn thành",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }

    HorizontalDivider(
        modifier  = Modifier.padding(start = 16.dp),
        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        thickness = 0.5.dp,
    )
}

// ── Note snippet card ─────────────────────────────────────────────────
// Design (gd_journalview): bg-[#F8F9F6] border rounded-2xl, file icon + text
// Shows the linked NoteEntity from NotesScreen
@Composable
private fun NoteSnippet(note: com.scalendar.data.database.entity.NoteEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Notes,
            contentDescription = null,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text  = note.title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (note.content.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = note.content,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
