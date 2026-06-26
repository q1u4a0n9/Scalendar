package com.scalendar.ui.screen.dayview

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.data.model.EntryCategory
import com.scalendar.data.model.TimeOfDay
import com.scalendar.ui.component.EntryFab
import com.scalendar.ui.component.EntryFabMenuOverlay
import com.scalendar.ui.component.InlineCalendarPicker
import com.scalendar.ui.component.WheelColumn
import com.scalendar.ui.component.WheelDatePicker
import com.scalendar.ui.component.WheelTimePicker
import com.scalendar.ui.component.nextRoundedTime
import com.scalendar.ui.shared.SharedCalendarViewModel
import com.scalendar.ui.theme.ENTRY_COLOR_OPTIONS
import com.scalendar.util.VietnamHolidays
import com.scalendar.ui.theme.cardColor
import com.scalendar.ui.theme.displayBgColor
import com.scalendar.ui.theme.displayFgColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.scalendar.R
import com.scalendar.data.model.localizedName
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

// ── Picker state for AddEntrySheet ────────────────────────────────────
private enum class ActivePicker { NONE, START_DATE, START_TIME, END_DATE, END_TIME }

// (FAB tokens, EntryCategory.fabIcon(), FAB_CATEGORY_ORDER, CategoryFabItem → moved to ui/component/EntryFab.kt)

// ── Screen ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayViewScreen(
    sharedVm      : SharedCalendarViewModel,
    onOpenDrawer  : () -> Unit,
    onOpenSearch  : () -> Unit         = {},
    holidayVnMode : String             = "ALL",
    holidayColor  : Color              = Color(0xFF26A69A),
    viewModel     : DayViewViewModel   = hiltViewModel(),
) {
    val selectedDate    by sharedVm.selectedDate.collectAsState()
    val categoryFilters by sharedVm.categoryFilters.collectAsState()
    val uiState         by viewModel.uiState.collectAsState()

    LaunchedEffect(selectedDate) { viewModel.setDate(selectedDate) }

    var showFabMenu      by remember { mutableStateOf(false) }
    var addEntryCategory by remember { mutableStateOf<EntryCategory?>(null) }
    var detailEntry      by remember { mutableStateOf<EntryEntity?>(null) }
    var editingEntry     by remember { mutableStateOf<EntryEntity?>(null) }

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val SWIPE_THRESHOLD = 80f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragOffset > SWIPE_THRESHOLD  -> sharedVm.previousDay()
                            dragOffset < -SWIPE_THRESHOLD -> sharedVm.nextDay()
                        }
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { _, delta -> dragOffset += delta }
                )
            }
    ) {
        Scaffold(
            topBar = {
                DayTopBar(
                    date              = selectedDate,
                    onOpenDrawer      = onOpenDrawer,
                    onOpenSearch      = onOpenSearch,
                    onNavigateToToday = { sharedVm.selectDate(LocalDate.now()) },
                )
            },
            floatingActionButton = {
                EntryFab(expanded = showFabMenu, onToggle = { showFabMenu = !showFabMenu })
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 88.dp),
            ) {
                // ── Holiday banner ────────────────────────────────────
                VietnamHolidays.getName(selectedDate, holidayVnMode)?.let { holidayName ->
                    Surface(
                        color    = holidayColor.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("🎉", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text  = holidayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = holidayColor,
                            )
                        }
                    }
                }

                TimeOfDay.entries.forEach { slot ->
                    val slotEntries = (uiState.entriesBySlot[slot] ?: emptyList())
                        .filter { it.category in categoryFilters }
                    TimeSlotSection(
                        slot     = slot,
                        entries  = slotEntries,
                        onToggle = viewModel::toggleComplete,
                        onEdit   = { detailEntry = it },
                    )
                }
            }
        }

        // ── FAB scrim + staggered category menu ──────────────────────
        EntryFabMenuOverlay(
            showMenu           = showFabMenu,
            onDismiss          = { showFabMenu = false },
            onCategorySelected = { cat -> addEntryCategory = cat; showFabMenu = false },
        )
    }

    // ── Detail / Add / Edit Entry bottom sheets ───────────────────────
    val notes        by viewModel.notes.collectAsState()
    val defaultNotifs by viewModel.defaultNotifs.collectAsState()

    if (detailEntry != null) {
        EntryDetailSheet(
            entry          = detailEntry!!,
            onEdit         = {
                val toEdit = detailEntry
                detailEntry = null
                editingEntry = toEdit
            },
            onDelete       = {
                detailEntry?.let { viewModel.deleteEntry(it) }
                detailEntry = null
            },
            onDeleteSeries = {
                detailEntry?.let { viewModel.deleteEntrySeries(it) }
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
                viewModel.saveEntry(entity, recType, recEnd)
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
                viewModel.saveEntry(entity, recType, recEnd)
                editingEntry = null
            },
            onDismiss = { editingEntry = null },
        )
    }
}

// ── Top bar ───────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayTopBar(
    date             : LocalDate,
    onOpenDrawer     : () -> Unit,
    onOpenSearch     : () -> Unit,
    onNavigateToToday: () -> Unit,
) {
    val locale       = LocalConfiguration.current.locales[0]
    val DAY_NAME_FMT = remember(locale) { DateTimeFormatter.ofPattern("EEEE",        locale) }
    val DATE_SUB_FMT = remember(locale) { DateTimeFormatter.ofPattern("d MMMM yyyy", locale) }

    TopAppBar(
        title = {
            Column {
                Text(
                    text  = date.format(DAY_NAME_FMT).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text  = date.format(DATE_SUB_FMT),
                    style = MaterialTheme.typography.bodyMedium,
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
            IconButton(onClick = onNavigateToToday) {
                Icon(
                    Icons.Outlined.CalendarToday,
                    contentDescription = stringResource(R.string.dayview_today),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

// (DayFab and CategoryFabItem → see EntryFab and CategoryFabItem in ui/component/EntryFab.kt)

// ── Time-slot section ─────────────────────────────────────────────────
@Composable
private fun TimeSlotSection(
    slot    : TimeOfDay,
    entries : List<EntryEntity>,
    onToggle: (EntryEntity) -> Unit,
    onEdit  : (EntryEntity) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text     = slot.localizedName(),
            style    = MaterialTheme.typography.titleLarge,
            color    = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (entries.isEmpty()) {
            Text(
                text     = stringResource(R.string.slot_empty),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        } else {
            entries.forEach { entry ->
                EntryCard(
                    entry    = entry,
                    onToggle = { onToggle(entry) },
                    onEdit   = { onEdit(entry) },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        HorizontalDivider(
            color     = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp,
        )
    }
}

// ── Entry card (uses displayBgColor / displayFgColor) ─────────────────
@Composable
private fun EntryCard(
    entry    : EntryEntity,
    onToggle : () -> Unit,
    onEdit   : () -> Unit,
    modifier : Modifier = Modifier,
) {
    val TIME_FMT = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val bg = entry.displayBgColor()
    val fg = entry.displayFgColor()

    Row(
        modifier          = modifier
            .fillMaxWidth()
            .alpha(if (entry.isCompleted) 0.55f else 1f)
            .clip(MaterialTheme.shapes.medium)
            .clickable { onEdit() }
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector        = if (entry.isCompleted) Icons.Filled.CheckCircle
                                 else                   Icons.Outlined.RadioButtonUnchecked,
            contentDescription = if (entry.isCompleted) stringResource(R.string.cd_completed) else stringResource(R.string.cd_not_completed),
            tint               = if (entry.isCompleted) fg.copy(alpha = 0.8f)
                                 else                   fg.copy(alpha = 0.5f),
            modifier           = Modifier
                .size(22.dp)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication        = null,
                    onClick           = onToggle,
                ),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = entry.title,
                style    = MaterialTheme.typography.bodyLarge.let { s ->
                    if (entry.isCompleted) s.copy(textDecoration = TextDecoration.LineThrough) else s
                },
                color    = fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.startTime != null) {
                val time = entry.startTime.format(TIME_FMT) +
                        (entry.endTime?.let { " – ${it.format(TIME_FMT)}" } ?: "")
                Text(text = time, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.75f))
            }
            if (entry.location.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, null, tint = fg.copy(alpha = 0.65f), modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(text = entry.location, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text     = entry.category.localizedName(),
            style    = MaterialTheme.typography.labelSmall,
            color    = fg.copy(alpha = 0.8f),
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(fg.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// ── Add / Edit Entry sheet ────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddEntrySheet(
    editEntry       : EntryEntity?  = null,
    initialCategory : EntryCategory,
    date            : LocalDate,
    notes           : List<com.scalendar.data.database.entity.NoteEntity>,
    defaultNotifs   : Map<com.scalendar.data.model.EntryCategory, Int?>,
    onSave          : (EntryEntity, String, LocalDate?) -> Unit,
    onDismiss       : () -> Unit,
) {
    // Declared before sheetState so confirmValueChange lambda can reference them
    var title             by remember { mutableStateOf(editEntry?.title ?: "") }
    var showCancelConfirm by remember { mutableStateOf(false) }

    val sheetState     = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange    = { value ->
            // Block swipe / scrim-tap dismiss when user has typed content; show confirm dialog instead
            if (value == SheetValue.Hidden && title.isNotBlank()) {
                showCancelConfirm = true
                false
            } else {
                true
            }
        },
    )
    val coroutineScope = rememberCoroutineScope()

    // ── Locale-aware formatters ───────────────────────────────────────
    val locale        = LocalConfiguration.current.locales[0]
    val DATE_CHIP_FMT = remember(locale) { DateTimeFormatter.ofPattern("d MMMM yyyy", locale) }
    val DATE_ROW_FMT  = remember(locale) { DateTimeFormatter.ofPattern("EEEE, d MMM", locale) }
    val TIME_FMT      = remember { DateTimeFormatter.ofPattern("HH:mm") }

    // Animated dismiss: hide sheet first, then remove from composition
    fun dismissSheet() {
        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    // ── Form state ────────────────────────────────────────────────────
    var selectedCategory    by remember { mutableStateOf(editEntry?.category ?: initialCategory) }
    var description         by remember { mutableStateOf(editEntry?.description ?: "") }
    var selectedDate        by remember { mutableStateOf(editEntry?.date ?: date) }
    var showDatePicker      by remember { mutableStateOf(false) }
    var isAllDay            by remember { mutableStateOf(editEntry?.let { it.startTime == null } ?: (initialCategory == EntryCategory.TASK)) }
    val defaultStart        = remember { nextRoundedTime() }
    var startTime           by remember { mutableStateOf<LocalTime?>(editEntry?.startTime ?: defaultStart) }
    var endTime             by remember { mutableStateOf<LocalTime?>(editEntry?.endTime) }
    var recurrenceType      by remember { mutableStateOf(editEntry?.recurrenceType ?: "NONE") }
    var recurrenceEndDate   by remember { mutableStateOf((editEntry?.date ?: date).plusMonths(4)) }
    var showRecEndPicker    by remember { mutableStateOf(false) }
    var showRecurrenceMenu  by remember { mutableStateOf(false) }
    var selectedColor       by remember { mutableStateOf(editEntry?.color ?: "DEFAULT") }
    var linkedNoteId        by remember { mutableStateOf<Long?>(editEntry?.linkedNoteId) }
    var showNotePicker      by remember { mutableStateOf(false) }
    var isRecurring         by remember { mutableStateOf(false) }
    var reminderMinutes     by remember {
        mutableStateOf<Int?>(
            if (editEntry != null) {
                editEntry.reminderOffsets.split(",").firstOrNull { it.trim().isNotEmpty() }
                    ?.trim()?.toIntOrNull()
            } else {
                defaultNotifs[initialCategory]
            }
        )
    }
    var showReminderMenu    by remember { mutableStateOf(false) }
    var showCustomReminder  by remember { mutableStateOf(false) }
    var activePicker        by remember { mutableStateOf(ActivePicker.NONE) }
    var deadlineDate        by remember { mutableStateOf(editEntry?.deadlineDate) }   // TASK only
    var location            by remember { mutableStateOf(editEntry?.location ?: "") }
    // Prevents LaunchedEffect(selectedCategory) from resetting reminderMinutes on initial composition
    var skipNextCategoryReminderReset by remember { mutableStateOf(editEntry != null) }

    LaunchedEffect(isAllDay) {
        if (isAllDay) {
            startTime = null; endTime = null; activePicker = ActivePicker.NONE
        } else if (startTime == null) {
            startTime = defaultStart   // restore when un-checking all-day
        }
    }

    // When switching to TASK: default to all-day ON.
    // When switching away from TASK: if isAllDay was forced on (startTime==null), restore defaults.
    // Also auto-fill reminder from the category's default setting.
    LaunchedEffect(selectedCategory) {
        when (selectedCategory) {
            EntryCategory.TASK -> {
                isAllDay = true
                activePicker = ActivePicker.NONE
            }
            else -> {
                if (isAllDay && startTime == null) {
                    isAllDay = false   // LaunchedEffect(isAllDay) else-branch restores startTime
                }
            }
        }
        // Reset reminder to category default when switching categories.
        // Skip the first fire on initial composition for edits to preserve the saved value.
        if (!skipNextCategoryReminderReset) {
            reminderMinutes = defaultNotifs[selectedCategory]
        }
        skipNextCategoryReminderReset = false
    }

    val derivedTimeOfDay = remember(startTime, isAllDay) {
        if (isAllDay || startTime == null) TimeOfDay.ANYTIME
        else when {
            startTime!!.hour < 12 -> TimeOfDay.MORNING
            startTime!!.hour < 18 -> TimeOfDay.AFTERNOON
            else                  -> TimeOfDay.EVENING
        }
    }

    val linkedNote    = notes.firstOrNull { it.id == linkedNoteId }
    val isBirthday    = selectedCategory == EntryCategory.BIRTHDAY
    val isTask        = selectedCategory == EntryCategory.TASK
    val hasRecurrence = recurrenceType != "NONE"

    val recNone    = stringResource(R.string.rec_none)
    val recDaily   = stringResource(R.string.rec_daily)
    val recWeekly  = stringResource(R.string.rec_weekly)
    val recMonthly = stringResource(R.string.rec_monthly)
    val recYearly  = stringResource(R.string.rec_yearly)
    val recurrenceOptions = remember(recNone, recDaily, recWeekly, recMonthly, recYearly) {
        listOf(
            "NONE"    to recNone,
            "DAILY"   to recDaily,
            "WEEKLY"  to recWeekly,
            "MONTHLY" to recMonthly,
            "YEARLY"  to recYearly,
        )
    }
    val recurrenceLabel = recurrenceOptions.find { it.first == recurrenceType }?.second ?: recNone

    // ── Save action ───────────────────────────────────────────────────
    val doSave: () -> Unit = {
        if (title.isNotBlank()) {
            onSave(
                EntryEntity(
                    id              = editEntry?.id ?: 0L,
                    title           = title.trim(),
                    category        = selectedCategory,
                    timeOfDay       = derivedTimeOfDay,
                    startTime       = if (isAllDay) null else startTime,
                    endTime         = if (isAllDay) null else endTime,
                    date            = selectedDate,
                    description     = if (isBirthday) "" else description.trim(),
                    linkedNoteId    = linkedNoteId,
                    color           = selectedColor,
                    reminderOffsets = reminderMinutes?.toString() ?: "",
                    isRecurring     = isRecurring || hasRecurrence,
                    recurrenceType  = recurrenceType,
                    isImportant     = editEntry?.isImportant ?: false,
                    deadlineDate    = if (isTask) deadlineDate else null,
                    location        = location.trim(),
                ),
                recurrenceType,
                if (hasRecurrence) recurrenceEndDate else null,
            )
        }
    }

    // ── Custom reminder dialog ────────────────────────────────────────
    if (showCustomReminder) {
        CustomReminderDialog(
            initialMinutes = reminderMinutes ?: 30,
            onConfirm      = { mins -> reminderMinutes = mins; showCustomReminder = false },
            onDismiss      = { showCustomReminder = false },
        )
    }

    // ── Cancel confirmation dialog ────────────────────────────────────
    if (showCancelConfirm) {
        Dialog(onDismissRequest = { showCancelConfirm = false }) {
            Surface(
                shape          = RoundedCornerShape(16.dp),
                color          = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text      = if (editEntry != null) stringResource(R.string.sheet_discard_edit) else stringResource(R.string.sheet_discard_new, selectedCategory.localizedName().lowercase()),
                        style     = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color     = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    // Discard — red
                    TextButton(
                        onClick  = { showCancelConfirm = false; dismissSheet() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Text(
                            text  = stringResource(R.string.sheet_discard_confirm),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    // Continue editing — primary
                    TextButton(
                        onClick  = { showCancelConfirm = false },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Text(
                            text  = stringResource(R.string.sheet_keep_editing),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Header: Hủy / title / Lưu ────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    if (title.isNotBlank()) showCancelConfirm = true else dismissSheet()
                }) {
                    Text(
                        text  = stringResource(R.string.sheet_cancel),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (editEntry != null) {
                    Text(
                        text       = stringResource(R.string.sheet_edit_title, selectedCategory.localizedName().lowercase()),
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.weight(1f),
                        textAlign  = TextAlign.Center,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                val saveLabel = if (hasRecurrence) stringResource(R.string.sheet_save_recurring, recurrenceLabel.lowercase()) else stringResource(R.string.sheet_save)
                TextButton(
                    onClick  = { doSave(); onDismiss() },
                    enabled  = title.isNotBlank(),
                ) {
                    Text(
                        text  = saveLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (title.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
            }

            HorizontalDivider(
                color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            // ── Form content ──────────────────────────────────────────
            Column(
                modifier            = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {

                // ── 1. Name / Title ───────────────────────────────────
                val namePlaceholder = if (isBirthday) stringResource(R.string.field_name_hint) else stringResource(R.string.field_title_hint)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = if (isBirthday) Icons.Outlined.Cake else Icons.Outlined.Title,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value         = title,
                        onValueChange = { title = it },
                        placeholder   = {
                            Text(
                                namePlaceholder,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        },
                        textStyle  = MaterialTheme.typography.titleMedium,
                        modifier   = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors     = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor   = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        ),
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // ── 2. Category chips ─────────────────────────────────
                Row(
                    modifier              = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EntryCategory.entries.forEach { cat ->
                        val isSel = cat == selectedCategory
                        FilterChip(
                            selected    = isSel,
                            onClick     = { selectedCategory = cat },
                            label       = { Text(cat.localizedName(), style = MaterialTheme.typography.labelSmall, fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal) },
                            leadingIcon = if (isSel) ({ Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }) else null,
                        )
                    }
                }

                // ── 3. Date / time section ────────────────────────────
                if (isTask && isAllDay) {
                    // Task all-day: Cả ngày switch ON + tappable date row → InlineCalendarPicker.
                    // Deadline handled separately in Section 4c.
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WbSunny, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(R.string.field_allday), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Switch(checked = isAllDay, onCheckedChange = { isAllDay = it; activePicker = ActivePicker.NONE })
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                activePicker = if (activePicker == ActivePicker.START_DATE) ActivePicker.NONE else ActivePicker.START_DATE
                            }
                            .padding(start = 34.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text  = selectedDate.format(DATE_ROW_FMT).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (activePicker == ActivePicker.START_DATE)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    AnimatedVisibility(
                        visible = activePicker == ActivePicker.START_DATE,
                        enter   = expandVertically() + fadeIn(),
                        exit    = shrinkVertically() + fadeOut(),
                    ) {
                        InlineCalendarPicker(
                            selectedDate = selectedDate,
                            onDateSelected = { selectedDate = it },
                        )
                    }
                } else if (isBirthday) {
                    SheetInfoRow(icon = Icons.Outlined.CalendarToday, onClick = { showDatePicker = !showDatePicker }) {
                        Text(
                            text     = selectedDate.format(DATE_CHIP_FMT),
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (showDatePicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AnimatedVisibility(visible = showDatePicker, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                            WheelDatePicker(date = selectedDate, onDateChanged = { selectedDate = it }, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WbSunny, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(R.string.field_allday), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Switch(checked = isAllDay, onCheckedChange = { isAllDay = it })
                    }
                    AnimatedVisibility(visible = !isAllDay, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Start time card — same style as non-birthday
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.AccessTime, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(stringResource(R.string.time_start), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                        Text(
                                            text     = startTime?.format(TIME_FMT) ?: "--:--",
                                            style    = MaterialTheme.typography.bodyMedium,
                                            color    = when {
                                                activePicker == ActivePicker.START_TIME -> MaterialTheme.colorScheme.primary
                                                startTime == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                                else -> MaterialTheme.colorScheme.onSurface
                                            },
                                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable {
                                                activePicker = if (activePicker == ActivePicker.START_TIME) ActivePicker.NONE else ActivePicker.START_TIME
                                            }.padding(vertical = 2.dp, horizontal = 6.dp),
                                        )
                                        if (startTime != null) {
                                            Spacer(Modifier.width(4.dp))
                                            IconButton(onClick = { startTime = null; endTime = null; if (activePicker == ActivePicker.START_TIME) activePicker = ActivePicker.NONE }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    AnimatedVisibility(visible = activePicker == ActivePicker.START_TIME, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                        WheelTimePicker(time = startTime ?: nextRoundedTime(), onTimeChanged = { startTime = it }, modifier = Modifier.padding(vertical = 8.dp))
                                    }
                                }
                            }
                            // End time card
                            AnimatedVisibility(visible = startTime != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.AlarmOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(10.dp))
                                            Text(stringResource(R.string.time_end), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                            Text(
                                                text     = endTime?.format(TIME_FMT) ?: "--:--",
                                                style    = MaterialTheme.typography.bodyMedium,
                                                color    = when {
                                                    activePicker == ActivePicker.END_TIME -> MaterialTheme.colorScheme.primary
                                                    endTime == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                },
                                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable {
                                                    activePicker = if (activePicker == ActivePicker.END_TIME) ActivePicker.NONE else ActivePicker.END_TIME
                                                }.padding(vertical = 2.dp, horizontal = 6.dp),
                                            )
                                            if (endTime != null) {
                                                Spacer(Modifier.width(4.dp))
                                                IconButton(onClick = { endTime = null; if (activePicker == ActivePicker.END_TIME) activePicker = ActivePicker.NONE }, modifier = Modifier.size(28.dp)) {
                                                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                        AnimatedVisibility(visible = activePicker == ActivePicker.END_TIME, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                            WheelTimePicker(time = endTime ?: (startTime?.plusHours(1) ?: nextRoundedTime()), onTimeChanged = { endTime = it }, modifier = Modifier.padding(vertical = 8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Non-task, non-birthday: GCal-style inline pickers
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WbSunny, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(R.string.field_allday), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Switch(checked = isAllDay, onCheckedChange = { isAllDay = it; activePicker = ActivePicker.NONE })
                    }
                    // Start card
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CalendarToday, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text     = selectedDate.format(DATE_ROW_FMT).replaceFirstChar { it.uppercase() },
                                    style    = MaterialTheme.typography.bodyMedium,
                                    color    = if (activePicker == ActivePicker.START_DATE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).clickable {
                                        activePicker = if (activePicker == ActivePicker.START_DATE) ActivePicker.NONE else ActivePicker.START_DATE
                                    }.padding(vertical = 2.dp),
                                )
                                if (!isAllDay) {
                                    Text(
                                        text     = startTime?.format(TIME_FMT) ?: "--:--",
                                        style    = MaterialTheme.typography.bodyMedium,
                                        color    = when {
                                            activePicker == ActivePicker.START_TIME -> MaterialTheme.colorScheme.primary
                                            startTime == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable {
                                            activePicker = if (activePicker == ActivePicker.START_TIME) ActivePicker.NONE else ActivePicker.START_TIME
                                        }.padding(vertical = 2.dp, horizontal = 6.dp),
                                    )
                                }
                            }
                            AnimatedVisibility(visible = activePicker == ActivePicker.START_DATE, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                InlineCalendarPicker(selectedDate = selectedDate, onDateSelected = { selectedDate = it; activePicker = ActivePicker.NONE })
                            }
                            AnimatedVisibility(visible = !isAllDay && activePicker == ActivePicker.START_TIME, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                WheelTimePicker(time = startTime ?: nextRoundedTime(), onTimeChanged = { startTime = it }, modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                    // End time card
                    AnimatedVisibility(visible = !isAllDay, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.AlarmOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(stringResource(R.string.time_end), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    Text(
                                        text     = endTime?.format(TIME_FMT) ?: "--:--",
                                        style    = MaterialTheme.typography.bodyMedium,
                                        color    = when {
                                            activePicker == ActivePicker.END_TIME -> MaterialTheme.colorScheme.primary
                                            endTime == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable {
                                            activePicker = if (activePicker == ActivePicker.END_TIME) ActivePicker.NONE else ActivePicker.END_TIME
                                        }.padding(vertical = 2.dp, horizontal = 6.dp),
                                    )
                                    if (endTime != null) {
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(onClick = { endTime = null; if (activePicker == ActivePicker.END_TIME) activePicker = ActivePicker.NONE }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                AnimatedVisibility(visible = activePicker == ActivePicker.END_TIME, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                    WheelTimePicker(time = endTime ?: (startTime?.plusHours(1) ?: nextRoundedTime()), onTimeChanged = { endTime = it }, modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }
                        }
                    }
                }

                // ── 4a+4b. Recurrence (hidden for BIRTHDAY — it already has yearly toggle) ──
                if (!isBirthday) {
                Box {
                    SheetInfoRow(icon = Icons.Outlined.Autorenew, onClick = { showRecurrenceMenu = true }) {
                        Text(recurrenceLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.UnfoldMore, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showRecurrenceMenu, onDismissRequest = { showRecurrenceMenu = false }) {
                        recurrenceOptions.forEach { (type, label) ->
                            DropdownMenuItem(
                                text        = { Text(label) },
                                onClick     = { recurrenceType = type; showRecurrenceMenu = false },
                                leadingIcon = if (recurrenceType == type) ({ Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }) else null,
                            )
                        }
                    }
                }

                // ── 4b. Recurrence end date ───────────────────────────
                AnimatedVisibility(visible = hasRecurrence, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SheetInfoRow(icon = Icons.Outlined.EventRepeat, onClick = { showRecEndPicker = !showRecEndPicker }) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.field_repeat_until), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(recurrenceEndDate.format(DATE_CHIP_FMT), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Icon(if (showRecEndPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AnimatedVisibility(visible = showRecEndPicker, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                                WheelDatePicker(date = recurrenceEndDate, onDateChanged = { recurrenceEndDate = it }, yearRange = selectedDate.year..(selectedDate.year + 3), modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                        val occurrenceCount = remember(selectedDate, recurrenceEndDate, recurrenceType) {
                            if (recurrenceType == "NONE") 0
                            else {
                                var cnt = 0; var cur = selectedDate
                                while (!cur.isAfter(recurrenceEndDate) && cnt < 2000) {
                                    cnt++
                                    cur = when (recurrenceType) {
                                        "DAILY" -> cur.plusDays(1); "WEEKLY" -> cur.plusWeeks(1)
                                        "MONTHLY" -> cur.plusMonths(1); else -> cur.plusYears(1)
                                    }
                                }
                                cnt
                            }
                        }
                        if (occurrenceCount > 0) {
                            Text(stringResource(R.string.field_occurrences, occurrenceCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 34.dp))
                        }
                    }
                }
                } // end if (!isBirthday)

                // ── 4c. Task deadline (TASK only) ────────────────────
                if (isTask) {
                    if (deadlineDate == null) {
                        // "Thêm thời hạn" button
                        SheetInfoRow(
                            icon    = Icons.Outlined.TrackChanges,
                            onClick = {
                                deadlineDate = selectedDate
                                activePicker = ActivePicker.END_DATE
                            },
                        ) {
                            Text(
                                stringResource(R.string.field_deadline_add),
                                style    = MaterialTheme.typography.bodyMedium,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        // "Đến hạn lúc [day], [date]" row + X + inline calendar
                        SheetInfoRow(
                            icon    = Icons.Outlined.TrackChanges,
                            onClick = {
                                activePicker = if (activePicker == ActivePicker.END_DATE) ActivePicker.NONE else ActivePicker.END_DATE
                            },
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.field_deadline_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text  = deadlineDate!!.format(DATE_ROW_FMT).replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (activePicker == ActivePicker.END_DATE) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(
                                onClick  = { deadlineDate = null; activePicker = ActivePicker.NONE },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                        AnimatedVisibility(
                            visible = activePicker == ActivePicker.END_DATE,
                            enter   = expandVertically() + fadeIn(),
                            exit    = shrinkVertically() + fadeOut(),
                        ) {
                            Surface(
                                shape    = RoundedCornerShape(12.dp),
                                color    = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                InlineCalendarPicker(
                                    selectedDate = deadlineDate!!,
                                    onDateSelected = { deadlineDate = it; activePicker = ActivePicker.NONE },
                                )
                            }
                        }
                    }
                }

                // ── 5. Location ───────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    BasicTextField(
                        value          = location,
                        onValueChange  = { location = it },
                        modifier       = Modifier.weight(1f),
                        textStyle      = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        singleLine     = true,
                        decorationBox  = { innerTextField ->
                            Box {
                                if (location.isEmpty()) {
                                    Text(
                                        text  = stringResource(R.string.field_location_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    if (location.isNotBlank()) {
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick  = { location = "" },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Close,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier           = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                // ── 5b. Description (hidden for BIRTHDAY) ─────────────
                AnimatedVisibility(visible = !isBirthday, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.field_description_hint)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), maxLines = 3)
                }

                // ── 6. "Bao gồm năm" toggle (BIRTHDAY only) ───────────
                AnimatedVisibility(visible = isBirthday, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Autorenew, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.field_birthday_include_year), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.field_birthday_yearly), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isRecurring, onCheckedChange = { isRecurring = it })
                    }
                }

                // ── 7. Color picker ───────────────────────────────────
                Text(stringResource(R.string.field_color), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ENTRY_COLOR_OPTIONS.forEach { opt ->
                        val isChosen    = selectedColor == opt.name
                        val circleColor = if (opt.bg == Color.Unspecified) selectedCategory.cardColor() else opt.bg
                        Box(
                            modifier = Modifier
                                .size(if (isChosen) 34.dp else 28.dp)
                                .clip(CircleShape).background(circleColor)
                                .border(width = if (isChosen) 2.5.dp else 0.dp, color = if (isChosen) MaterialTheme.colorScheme.onSurface else Color.Transparent, shape = CircleShape)
                                .clickable { selectedColor = opt.name },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isChosen) Icon(Icons.Default.Check, null, tint = if (opt.fg != Color.Unspecified) opt.fg else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // ── 8. Note attachment ────────────────────────────────
                Text(stringResource(R.string.field_attach_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (linkedNote != null) {
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.Notes, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(linkedNote.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { linkedNoteId = null }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                } else {
                    OutlinedButton(onClick = { showNotePicker = !showNotePicker }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Notes, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (showNotePicker) stringResource(R.string.field_close_notes) else stringResource(R.string.field_pick_note), style = MaterialTheme.typography.labelMedium)
                    }
                    AnimatedVisibility(visible = showNotePicker) {
                        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                            if (notes.isEmpty()) {
                                Text(stringResource(R.string.field_no_notes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                            } else {
                                notes.forEach { note ->
                                    Row(modifier = Modifier.fillMaxWidth().clickable { linkedNoteId = note.id; showNotePicker = false }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (note.isPinned) { Icon(Icons.Filled.PushPin, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(4.dp)) }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(note.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (note.content.isNotBlank()) Text(note.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 12.dp))
                                }
                            }
                        }
                    }
                }

                // ── 9. Reminder ───────────────────────────────────────
                val rNone   = stringResource(R.string.reminder_none_short)
                val r10min  = stringResource(R.string.reminder_before_10min)
                val r30min  = stringResource(R.string.reminder_before_30min)
                val r1h     = stringResource(R.string.reminder_before_1h)
                val r1day   = stringResource(R.string.reminder_before_1day)
                val reminderPresets = remember(rNone, r10min, r30min, r1h, r1day) {
                    listOf(
                        null to rNone,
                        10   to r10min,
                        30   to r30min,
                        60   to r1h,
                        1440 to r1day,
                    )
                }
                Box {
                    SheetInfoRow(icon = Icons.Outlined.NotificationsNone, onClick = { showReminderMenu = true }) {
                        Text(formatReminderMinutes(reminderMinutes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.UnfoldMore, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showReminderMenu, onDismissRequest = { showReminderMenu = false }) {
                        reminderPresets.forEach { (mins, label) ->
                            DropdownMenuItem(
                                text        = { Text(label) },
                                onClick     = { reminderMinutes = mins; showReminderMenu = false },
                                leadingIcon = if (reminderMinutes == mins) ({ Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }) else null,
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.field_custom_reminder)) }, onClick = { showReminderMenu = false; showCustomReminder = true })
                    }
                }

            } // end form Column
        } // end outer Column
    } // end ModalBottomSheet
}

// ── Reminder label helper ─────────────────────────────────────────────
@Composable
private fun formatReminderMinutes(minutes: Int?): String {
    if (minutes == null) return stringResource(R.string.reminder_none_short)
    return when {
        minutes == 0        -> stringResource(R.string.reminder_at_start)
        minutes < 60        -> stringResource(R.string.reminder_custom_before_min, minutes)
        minutes % 60 == 0   -> stringResource(R.string.reminder_custom_before_h, minutes / 60)
        minutes < 1440      -> stringResource(R.string.reminder_custom_before_hm, minutes / 60, minutes % 60)
        minutes % 1440 == 0 -> stringResource(R.string.reminder_custom_before_days, minutes / 1440)
        else                -> stringResource(R.string.reminder_custom_before_dh, minutes / 1440, (minutes % 1440) / 60)
    }
}

// ── Custom reminder dialog ────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomReminderDialog(
    initialMinutes : Int,
    onConfirm      : (Int) -> Unit,
    onDismiss      : () -> Unit,
) {
    val (initNum, initUnitIdx) = remember(initialMinutes) {
        when {
            initialMinutes % 1440 == 0 -> Pair(initialMinutes / 1440, 2)
            initialMinutes % 60   == 0 -> Pair(initialMinutes / 60,   1)
            else                       -> Pair(initialMinutes,          0)
        }
    }
    val unitMin = stringResource(R.string.unit_minute)
    val unitH   = stringResource(R.string.unit_hour)
    val unitDay = stringResource(R.string.unit_day)
    val units   = remember(unitMin, unitH, unitDay) { listOf(unitMin, unitH, unitDay) }
    val numberItems = remember { (1..59).map { it.toString() } }
    var selectedNum  by remember { mutableIntStateOf(initNum.coerceIn(1, 59)) }
    var selectedUnit by remember { mutableIntStateOf(initUnitIdx) }
    val currentMinutes = remember(selectedNum, selectedUnit) {
        selectedNum * when (selectedUnit) { 1 -> 60; 2 -> 1440; else -> 1 }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurface) }
                    Text(stringResource(R.string.custom_reminder_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    TextButton(onClick = { onConfirm(currentMinutes) }) { Text(stringResource(R.string.custom_reminder_done), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.NotificationsNone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(formatReminderMinutes(currentMinutes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    WheelColumn(items = numberItems, startIndex = (selectedNum - 1).coerceIn(0, numberItems.lastIndex), onSelected = { idx -> selectedNum = idx + 1 }, circular = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    WheelColumn(items = units, startIndex = selectedUnit, onSelected = { idx -> selectedUnit = idx }, circular = false, modifier = Modifier.weight(1f))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(top = 4.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.NotificationsNone, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.custom_reminder_notification), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ── Helper: icon + tappable content row ──────────────────────────────
@Composable
private fun SheetInfoRow(
    icon    : ImageVector,
    onClick : () -> Unit,
    content : @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        content()
    }
}

// ── Entry detail sheet (read-only) ────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryDetailSheet(
    entry          : EntryEntity,
    onEdit         : () -> Unit,
    onDelete       : () -> Unit,
    onDeleteSeries : () -> Unit,
    onDismiss      : () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope      = rememberCoroutineScope()
    val locale     = LocalConfiguration.current.locales[0]
    val DATE_ROW_FMT = remember(locale) { DateTimeFormatter.ofPattern("EEEE, d MMM", locale) }
    val TIME_FMT     = remember { DateTimeFormatter.ofPattern("HH:mm") }
    fun animDismiss(then: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then(); onDismiss() }
    }

    var showDeleteDialog  by remember { mutableStateOf(false) }
    var deleteAllSelected by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_recurring_title)) },
            text  = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { deleteAllSelected = false }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = !deleteAllSelected, onClick = { deleteAllSelected = false })
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.detail_delete_this), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { deleteAllSelected = true }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = deleteAllSelected, onClick = { deleteAllSelected = true })
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.detail_delete_all), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        val action = if (deleteAllSelected) onDeleteSeries else onDelete
                        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {

            // ── Header: close / title / edit ─────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { animDismiss() }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.detail_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onEdit() } }) {
                    Text(
                        text  = stringResource(R.string.detail_edit),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // ── Title ────────────────────────────────────────────────
            Text(
                text       = entry.title,
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            // ── Date + time ──────────────────────────────────────────
            val dateStr = entry.date.format(DATE_ROW_FMT).replaceFirstChar { it.uppercase() }
            val timeStr = when {
                entry.startTime == null -> stringResource(R.string.detail_allday)
                entry.endTime != null   -> "${entry.startTime.format(TIME_FMT)} – ${entry.endTime.format(TIME_FMT)}"
                else                    -> entry.startTime.format(TIME_FMT)
            }
            Text(
                text     = dateStr,
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                text     = timeStr,
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ── Category ─────────────────────────────────────────────
            DetailInfoRow(icon = Icons.Outlined.Category, label = entry.category.localizedName())

            // ── Reminder ─────────────────────────────────────────────
            DetailInfoRow(icon = Icons.Outlined.NotificationsNone, label = formatReminderLabel(entry.reminderOffsets))

            // ── Recurrence ───────────────────────────────────────────
            if (entry.recurrenceType != "NONE") {
                val label = when (entry.recurrenceType) {
                    "DAILY"   -> stringResource(R.string.detail_rec_daily)
                    "WEEKLY"  -> stringResource(R.string.detail_rec_weekly)
                    "MONTHLY" -> stringResource(R.string.detail_rec_monthly)
                    "YEARLY"  -> stringResource(R.string.detail_rec_yearly)
                    else      -> ""
                }
                if (label.isNotEmpty()) DetailInfoRow(icon = Icons.Outlined.Repeat, label = label)
            }

            // ── Location ─────────────────────────────────────────────
            if (entry.location.isNotBlank()) {
                DetailInfoRow(icon = Icons.Outlined.LocationOn, label = entry.location)
            }

            // ── Description ──────────────────────────────────────────
            if (entry.description.isNotBlank()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    text     = entry.description,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            // ── Delete ───────────────────────────────────────────────
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            TextButton(
                onClick  = {
                    if (entry.recurrenceType != "NONE") {
                        // Recurring entry → ask "just this one" or "all"
                        showDeleteDialog = true
                    } else {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDelete() }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors   = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.detail_delete_event), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DetailInfoRow(icon: ImageVector, label: String) {
    Row(
        modifier              = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun formatReminderLabel(offsets: String): String {
    val mins = offsets.split(",").firstOrNull { it.trim().isNotEmpty() }?.trim()?.toIntOrNull()
        ?: return stringResource(R.string.reminder_no_alert)
    return when (mins) {
        0    -> stringResource(R.string.reminder_at_event)
        5    -> stringResource(R.string.reminder_5min)
        10   -> stringResource(R.string.reminder_10min)
        15   -> stringResource(R.string.reminder_15min)
        30   -> stringResource(R.string.reminder_30min)
        60   -> stringResource(R.string.reminder_1h)
        120  -> stringResource(R.string.reminder_2h)
        1440 -> stringResource(R.string.reminder_1day)
        else -> if (mins < 60) stringResource(R.string.reminder_custom_min, mins)
                else if (mins % 60 == 0) stringResource(R.string.reminder_custom_h, mins / 60)
                else if (mins < 1440) stringResource(R.string.reminder_custom_hm, mins / 60, mins % 60)
                else if (mins % 1440 == 0) stringResource(R.string.reminder_custom_days, mins / 1440)
                else stringResource(R.string.reminder_custom_before_dh, mins / 1440, (mins % 1440) / 60)
    }
}
