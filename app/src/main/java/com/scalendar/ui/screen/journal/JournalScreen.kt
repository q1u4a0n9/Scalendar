package com.scalendar.ui.screen.journal

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.scalendar.R
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.data.model.localizedName
import com.scalendar.ui.theme.displayBgColor
import com.scalendar.util.VietnamHolidays
import androidx.compose.ui.platform.LocalConfiguration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// ── Locale-aware short day names ──────────────────────────────────────
private fun LocalDate.shortDayName(locale: java.util.Locale): String =
    if (locale.language == "vi") {
        when (dayOfWeek) {
            DayOfWeek.MONDAY    -> "T2"
            DayOfWeek.TUESDAY   -> "T3"
            DayOfWeek.WEDNESDAY -> "T4"
            DayOfWeek.THURSDAY  -> "T5"
            DayOfWeek.FRIDAY    -> "T6"
            DayOfWeek.SATURDAY  -> "T7"
            DayOfWeek.SUNDAY    -> "CN"
            else                -> ""
        }
    } else {
        DateTimeFormatter.ofPattern("EEE", locale).format(this).take(2)
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
    onDayClick     : (LocalDate) -> Unit = {},
    holidayVnMode  : String              = "ALL",
    holidayColor   : Color               = Color(0xFF26A69A),
    viewModel      : JournalViewModel    = hiltViewModel(),
) {
    val uiState      by viewModel.uiState.collectAsState()
    val notesByDate  by viewModel.notesByDate.collectAsState()
    val today        = remember { LocalDate.now() }
    val locale          = LocalConfiguration.current.locales[0]
    val MONTH_TITLE_FMT = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }

    // ±6-month window (mirrors ViewModel's query range)
    val windowStart = remember { today.minusMonths(6).withDayOfMonth(1) }
    val windowEnd   = remember {
        today.plusMonths(6).let { d -> d.withDayOfMonth(YearMonth.from(d).lengthOfMonth()) }
    }

    // All holiday dates in window, keyed by YearMonth
    val holidayByMonth: Map<YearMonth, List<LocalDate>> = remember(holidayVnMode) {
        buildMap<YearMonth, MutableList<LocalDate>> {
            var d = windowStart
            while (!d.isAfter(windowEnd)) {
                if (VietnamHolidays.getName(d, holidayVnMode) != null) {
                    getOrPut(YearMonth.from(d)) { mutableListOf() }.add(d)
                }
                d = d.plusDays(1)
            }
        }
    }

    // Union of entry months + holiday months, sorted chronologically
    val allYearMonths: List<YearMonth> = remember(uiState.groups, holidayByMonth) {
        (uiState.groups.map { it.yearMonth }.toSet() + holidayByMonth.keys)
            .toSortedSet().toList()
    }

    // Per-month: sorted (date → entries) pairs — includes holiday-only dates (empty entries)
    val groupedData: List<Pair<YearMonth, List<Pair<LocalDate, List<EntryEntity>>>>> =
        remember(uiState.groups, holidayByMonth) {
            allYearMonths.map { ym ->
                val entryByDate = uiState.groups.find { it.yearMonth == ym }
                    ?.entries?.groupBy { it.date } ?: emptyMap()
                val allDates = (entryByDate.keys + (holidayByMonth[ym] ?: emptyList()))
                    .toSortedSet()
                ym to allDates.map { date -> date to (entryByDate[date] ?: emptyList()) }
            }
        }

    // ── Auto-scroll to current month on first load ────────────────────
    val listState = rememberLazyListState()
    val todayYm   = remember { YearMonth.from(today) }
    var didScroll by remember { mutableStateOf(false) }

    // Key on both groupedData AND isLoading so this re-fires when loading finishes.
    // Guard on isLoading so we never scroll before LazyColumn is actually rendered.
    LaunchedEffect(groupedData, uiState.isLoading) {
        if (didScroll || groupedData.isEmpty() || uiState.isLoading) return@LaunchedEffect
        didScroll = true
        // Each month occupies: 1 (header) + byDate.size (date rows) + 1 (gap spacer) items
        var idx = 0
        for ((ym, byDate) in groupedData) {
            if (ym >= todayYm) break
            idx += 1 + byDate.size + 1
        }
        listState.scrollToItem(idx)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = stringResource(R.string.journal_title),
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
            Box(
                modifier         = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (groupedData.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = stringResource(R.string.journal_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            state               = listState,
            contentPadding      = PaddingValues(
                start  = 16.dp, end = 16.dp,
                top    = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            groupedData.forEach { (ym, byDate) ->

                // Month header card
                item(key = "hdr_$ym") {
                    MonthGroupHeader(ym, MONTH_TITLE_FMT)
                    Spacer(Modifier.height(16.dp))
                }

                // One DateGroup per unique date (entries may be empty for holiday-only rows)
                byDate.forEach { (date, dateEntries) ->
                    item(key = "${ym}_$date") {
                        DateGroup(
                            date         = date,
                            entries      = dateEntries,
                            notes        = notesByDate[date] ?: emptyList(),
                            isToday      = date == today,
                            holidayName  = VietnamHolidays.getName(date, holidayVnMode),
                            holidayColor = holidayColor,
                            onDayClick   = onDayClick,
                            locale       = locale,
                        )
                    }
                }

                item(key = "gap_$ym") {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// ── Month header card ─────────────────────────────────────────────────
@Composable
private fun MonthGroupHeader(ym: YearMonth, monthTitleFmt: DateTimeFormatter) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(monthGradient(ym))
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text  = ym.atDay(1).format(monthTitleFmt).replaceFirstChar { it.uppercase() },
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
    date         : LocalDate,
    entries      : List<EntryEntity>,
    notes        : List<com.scalendar.data.database.entity.NoteEntity>,
    isToday      : Boolean,
    holidayName  : String?,
    holidayColor : Color,
    onDayClick   : (LocalDate) -> Unit,
    locale       : java.util.Locale = java.util.Locale("vi"),
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
                text  = date.shortDayName(locale),
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
            // Holiday label (tiny, wraps within the 52dp column)
            if (holidayName != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text      = holidayName,
                    style     = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color     = holidayColor,
                    maxLines  = 2,
                    overflow  = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        // ── Entries + same-day notes (+ holiday chip if nothing else) ───
        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            entries.forEach { entry ->
                JournalEntryCard(entry = entry)
            }
            // Notes written on this date auto-show as snippets below entries
            notes.forEach { note ->
                NoteSnippet(note = note)
            }
            // Holiday-only row: show a prominent chip when no entries/notes
            if (entries.isEmpty() && notes.isEmpty() && holidayName != null) {
                HolidayChip(name = holidayName, color = holidayColor)
            }
        }
    }
}

// ── Entry card — no inline date (handled by DateGroup) ────────────────
@Composable
private fun JournalEntryCard(entry: EntryEntity) {
    val accentColor = entry.displayBgColor()

    Row(
        modifier          = Modifier.fillMaxWidth(),
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
                style    = MaterialTheme.typography.bodyLarge,
                color    = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            // Category badge
            Text(
                text     = entry.category.localizedName(),
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor.copy(alpha = 0.25f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            if (entry.location.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(text = entry.location, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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

// ── Holiday chip — shown when a date has no entries/notes ─────────────
@Composable
private fun HolidayChip(name: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text  = "🎉",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text  = name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = color,
        )
    }
}
