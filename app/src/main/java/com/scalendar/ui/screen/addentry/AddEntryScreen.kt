package com.scalendar.ui.screen.addentry

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.data.database.entity.NoteEntity
import com.scalendar.data.model.EntryCategory
import com.scalendar.data.model.TimeOfDay
import com.scalendar.ui.component.InlineCalendarPicker
import com.scalendar.ui.component.WheelColumn
import com.scalendar.ui.component.WheelDatePicker
import com.scalendar.ui.component.WheelTimePicker
import com.scalendar.ui.component.nextRoundedTime
import com.scalendar.ui.theme.ENTRY_COLOR_OPTIONS
import com.scalendar.ui.theme.cardColor
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── Formatters ────────────────────────────────────────────────────────
private val DATE_CHIP_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("vi"))
private val DATE_ROW_FMT  = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.forLanguageTag("vi"))
private val TIME_FMT      = DateTimeFormatter.ofPattern("HH:mm")

// ── Picker state ──────────────────────────────────────────────────────
private enum class ActivePicker { NONE, START_DATE, START_TIME, END_TIME }

// ── Screen ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryScreen(
    initialCategory : EntryCategory,
    date            : LocalDate,
    onNavigateBack  : () -> Unit,
    viewModel       : AddEntryViewModel = hiltViewModel(),
) {
    val notes by viewModel.notes.collectAsState()

    // ── Form state ────────────────────────────────────────────────────
    var selectedCategory    by remember { mutableStateOf(initialCategory) }
    var title               by remember { mutableStateOf("") }
    var description         by remember { mutableStateOf("") }
    var selectedDate        by remember { mutableStateOf(date) }
    var showDatePicker      by remember { mutableStateOf(false) }
    var isAllDay            by remember { mutableStateOf(false) }
    val defaultStart        = remember { nextRoundedTime() }
    var startTime           by remember { mutableStateOf<LocalTime?>(defaultStart) }
    var endTime             by remember { mutableStateOf<LocalTime?>(null) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker   by remember { mutableStateOf(false) }
    var recurrenceType      by remember { mutableStateOf("NONE") }
    var recurrenceEndDate   by remember { mutableStateOf(date.plusMonths(4)) }
    var showRecEndPicker    by remember { mutableStateOf(false) }
    var showRecurrenceMenu  by remember { mutableStateOf(false) }
    var selectedColor       by remember { mutableStateOf("DEFAULT") }
    var linkedNoteId        by remember { mutableStateOf<Long?>(null) }
    var showNotePicker      by remember { mutableStateOf(false) }
    var isRecurring         by remember { mutableStateOf(false) }
    var reminderMinutes     by remember { mutableStateOf<Int?>(null) }
    var showReminderMenu    by remember { mutableStateOf(false) }
    var showCustomReminder  by remember { mutableStateOf(false) }
    var activePicker        by remember { mutableStateOf(ActivePicker.NONE) }

    // Clear times / pickers when switching to all-day
    LaunchedEffect(isAllDay) {
        if (isAllDay) { startTime = null; endTime = null; activePicker = ActivePicker.NONE }
    }

    val derivedTimeOfDay = remember(startTime, isAllDay) {
        if (isAllDay || startTime == null) TimeOfDay.ANYTIME
        else when {
            startTime!!.hour < 12 -> TimeOfDay.MORNING
            startTime!!.hour < 18 -> TimeOfDay.AFTERNOON
            else                  -> TimeOfDay.EVENING
        }
    }

    val linkedNote      = notes.firstOrNull { it.id == linkedNoteId }
    val isBirthday      = selectedCategory == EntryCategory.BIRTHDAY
    val hasRecurrence   = recurrenceType != "NONE"

    val recurrenceOptions = remember {
        listOf(
            "NONE"    to "Không lặp lại",
            "DAILY"   to "Hàng ngày",
            "WEEKLY"  to "Hàng tuần",
            "MONTHLY" to "Hàng tháng",
            "YEARLY"  to "Hàng năm",
        )
    }
    val recurrenceLabel = recurrenceOptions.find { it.first == recurrenceType }?.second ?: "Không lặp lại"

    val reminderPresets = remember {
        listOf(
            null to "Không có",
            10   to "Trước 10 phút",
            30   to "Trước 30 phút",
            60   to "Trước 1 giờ",
            1440 to "Trước 1 ngày",
        )
    }

    // ── Birthday-only time picker dialogs ─────────────────────────────
    if (isBirthday) {
        if (showStartTimePicker) {
            val tps = rememberTimePickerState(
                initialHour   = startTime?.hour   ?: LocalTime.now().hour,
                initialMinute = startTime?.minute ?: 0,
                is24Hour      = true,
            )
            AlertDialog(
                onDismissRequest = { showStartTimePicker = false },
                confirmButton    = {
                    TextButton(onClick = {
                        startTime = LocalTime.of(tps.hour, tps.minute)
                        showStartTimePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showStartTimePicker = false }) { Text("Hủy") }
                },
                text = { TimePicker(state = tps) },
            )
        }
        if (showEndTimePicker) {
            val tpe = rememberTimePickerState(
                initialHour   = endTime?.hour   ?: (startTime?.hour?.plus(1) ?: LocalTime.now().hour),
                initialMinute = endTime?.minute ?: 0,
                is24Hour      = true,
            )
            AlertDialog(
                onDismissRequest = { showEndTimePicker = false },
                confirmButton    = {
                    TextButton(onClick = {
                        endTime = LocalTime.of(tpe.hour, tpe.minute)
                        showEndTimePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showEndTimePicker = false }) { Text("Hủy") }
                },
                text = { TimePicker(state = tpe) },
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

    // ── Save helper ───────────────────────────────────────────────────
    val doSave: () -> Unit = {
        if (title.isNotBlank()) {
            viewModel.saveEntry(
                entity = EntryEntity(
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
                ),
                recurrenceType    = recurrenceType,
                recurrenceEndDate = if (hasRecurrence) recurrenceEndDate else null,
            )
            onNavigateBack()
        }
    }

    // ── UI ────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(
                            text  = "Hủy",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = doSave,
                        enabled = title.isNotBlank(),
                    ) {
                        Text(
                            text  = "Lưu",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (title.isNotBlank()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Title (GCal-style large field) ────────────────────────
            val namePlaceholder = if (isBirthday) "Thêm tên" else "Thêm tiêu đề"
            OutlinedTextField(
                value         = title,
                onValueChange = { title = it },
                placeholder   = {
                    Text(
                        text  = namePlaceholder,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                },
                textStyle  = MaterialTheme.typography.headlineMedium,
                modifier   = Modifier.fillMaxWidth(),
                singleLine = true,
                colors     = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor    = Color.Transparent,
                    focusedBorderColor      = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor   = Color.Transparent,
                ),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // ── Category chips ────────────────────────────────────────
            Row(
                modifier              = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EntryCategory.entries.forEach { cat ->
                    val isSel = cat == selectedCategory
                    FilterChip(
                        selected    = isSel,
                        onClick     = { selectedCategory = cat },
                        label       = {
                            Text(
                                cat.displayName(),
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        leadingIcon = if (isSel) ({
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                        }) else null,
                    )
                }
            }

            // ── Date / time ───────────────────────────────────────────
            if (isBirthday) {
                // Birthday: drum-roll date picker + dialog time pickers
                SheetInfoRow(
                    icon    = Icons.Outlined.CalendarToday,
                    onClick = { showDatePicker = !showDatePicker },
                ) {
                    Text(
                        text     = selectedDate.format(DATE_CHIP_FMT),
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (showDatePicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedVisibility(
                    visible = showDatePicker,
                    enter   = expandVertically() + fadeIn(),
                    exit    = shrinkVertically() + fadeOut(),
                ) {
                    Surface(
                        shape    = RoundedCornerShape(12.dp),
                        color    = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        WheelDatePicker(
                            date          = selectedDate,
                            onDateChanged = { selectedDate = it },
                            modifier      = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }

                // All-day toggle
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.WbSunny, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Cả ngày", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Switch(checked = isAllDay, onCheckedChange = { isAllDay = it })
                }

                AnimatedVisibility(
                    visible = !isAllDay,
                    enter   = expandVertically() + fadeIn(),
                    exit    = shrinkVertically() + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SheetInfoRow(icon = Icons.Outlined.AccessTime, onClick = { showStartTimePicker = true }) {
                            if (startTime == null) {
                                Text("Giờ bắt đầu", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            } else {
                                Text(startTime!!.format(TIME_FMT), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(6.dp))
                                Text("· ${derivedTimeOfDay.displayName()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                IconButton(onClick = { startTime = null; endTime = null }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = startTime != null,
                            enter   = expandVertically() + fadeIn(),
                            exit    = shrinkVertically() + fadeOut(),
                        ) {
                            SheetInfoRow(icon = Icons.Outlined.AlarmOff, onClick = { showEndTimePicker = true }) {
                                if (endTime == null) {
                                    Text("Giờ kết thúc (không bắt buộc)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                } else {
                                    Text(endTime!!.format(TIME_FMT), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { endTime = null }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Non-birthday: GCal-style inline pickers

                // All-day toggle
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.WbSunny, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Cả ngày", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Switch(checked = isAllDay, onCheckedChange = { isAllDay = it; activePicker = ActivePicker.NONE })
                }

                // Start card
                Surface(
                    shape    = RoundedCornerShape(12.dp),
                    color    = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            modifier          = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.CalendarToday, null,
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            // Date chip
                            Text(
                                text     = selectedDate.format(DATE_ROW_FMT).replaceFirstChar { it.uppercase() },
                                style    = MaterialTheme.typography.bodyMedium,
                                color    = if (activePicker == ActivePicker.START_DATE) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        activePicker = if (activePicker == ActivePicker.START_DATE)
                                            ActivePicker.NONE else ActivePicker.START_DATE
                                    }
                                    .padding(vertical = 2.dp),
                            )
                            // Time chip (hidden when all-day)
                            if (!isAllDay) {
                                Text(
                                    text  = startTime?.format(TIME_FMT) ?: "--:--",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        activePicker == ActivePicker.START_TIME -> MaterialTheme.colorScheme.primary
                                        startTime == null                       -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else                                    -> MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            activePicker = if (activePicker == ActivePicker.START_TIME)
                                                ActivePicker.NONE else ActivePicker.START_TIME
                                        }
                                        .padding(vertical = 2.dp, horizontal = 6.dp),
                                )
                            }
                        }
                        // Inline calendar
                        AnimatedVisibility(
                            visible = activePicker == ActivePicker.START_DATE,
                            enter   = expandVertically() + fadeIn(),
                            exit    = shrinkVertically() + fadeOut(),
                        ) {
                            InlineCalendarPicker(
                                selectedDate   = selectedDate,
                                onDateSelected = { selectedDate = it; activePicker = ActivePicker.NONE },
                            )
                        }
                        // Inline time wheel
                        AnimatedVisibility(
                            visible = !isAllDay && activePicker == ActivePicker.START_TIME,
                            enter   = expandVertically() + fadeIn(),
                            exit    = shrinkVertically() + fadeOut(),
                        ) {
                            WheelTimePicker(
                                time          = startTime ?: nextRoundedTime(),
                                onTimeChanged = { startTime = it },
                                modifier      = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }

                // End time card
                AnimatedVisibility(
                    visible = !isAllDay,
                    enter   = expandVertically() + fadeIn(),
                    exit    = shrinkVertically() + fadeOut(),
                ) {
                    Surface(
                        shape    = RoundedCornerShape(12.dp),
                        color    = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Row(
                                modifier          = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.AlarmOff, null,
                                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text     = "Kết thúc",
                                    style    = MaterialTheme.typography.bodySmall,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text  = endTime?.format(TIME_FMT) ?: "--:--",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        activePicker == ActivePicker.END_TIME -> MaterialTheme.colorScheme.primary
                                        endTime == null                       -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else                                  -> MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            activePicker = if (activePicker == ActivePicker.END_TIME)
                                                ActivePicker.NONE else ActivePicker.END_TIME
                                        }
                                        .padding(vertical = 2.dp, horizontal = 6.dp),
                                )
                                if (endTime != null) {
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick  = {
                                            endTime = null
                                            if (activePicker == ActivePicker.END_TIME) activePicker = ActivePicker.NONE
                                        },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            AnimatedVisibility(
                                visible = activePicker == ActivePicker.END_TIME,
                                enter   = expandVertically() + fadeIn(),
                                exit    = shrinkVertically() + fadeOut(),
                            ) {
                                WheelTimePicker(
                                    time          = endTime ?: (startTime?.plusHours(1) ?: nextRoundedTime()),
                                    onTimeChanged = { endTime = it },
                                    modifier      = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── Recurrence selector ───────────────────────────────────
            Box {
                SheetInfoRow(icon = Icons.Outlined.Autorenew, onClick = { showRecurrenceMenu = true }) {
                    Text(recurrenceLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.UnfoldMore, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(
                    expanded         = showRecurrenceMenu,
                    onDismissRequest = { showRecurrenceMenu = false },
                ) {
                    recurrenceOptions.forEach { (type, label) ->
                        DropdownMenuItem(
                            text        = { Text(label) },
                            onClick     = { recurrenceType = type; showRecurrenceMenu = false },
                            leadingIcon = if (recurrenceType == type) ({
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            }) else null,
                        )
                    }
                }
            }

            // ── Recurrence end date ───────────────────────────────────
            AnimatedVisibility(
                visible = hasRecurrence,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetInfoRow(
                        icon    = Icons.Outlined.EventRepeat,
                        onClick = { showRecEndPicker = !showRecEndPicker },
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Lặp đến", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(recurrenceEndDate.format(DATE_CHIP_FMT), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Icon(
                            if (showRecEndPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AnimatedVisibility(
                        visible = showRecEndPicker,
                        enter   = expandVertically() + fadeIn(),
                        exit    = shrinkVertically() + fadeOut(),
                    ) {
                        Surface(
                            shape    = RoundedCornerShape(12.dp),
                            color    = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            WheelDatePicker(
                                date          = recurrenceEndDate,
                                onDateChanged = { recurrenceEndDate = it },
                                yearRange     = selectedDate.year..(selectedDate.year + 3),
                                modifier      = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                    val occurrenceCount = remember(selectedDate, recurrenceEndDate, recurrenceType) {
                        if (recurrenceType == "NONE") 0
                        else {
                            var cnt = 0
                            var cur = selectedDate
                            while (!cur.isAfter(recurrenceEndDate) && cnt < 2000) {
                                cnt++
                                cur = when (recurrenceType) {
                                    "DAILY"   -> cur.plusDays(1)
                                    "WEEKLY"  -> cur.plusWeeks(1)
                                    "MONTHLY" -> cur.plusMonths(1)
                                    else      -> cur.plusYears(1)
                                }
                            }
                            cnt
                        }
                    }
                    if (occurrenceCount > 0) {
                        Text(
                            text     = "Tạo $occurrenceCount lần lặp",
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 34.dp),
                        )
                    }
                }
            }

            // ── Description (hidden for BIRTHDAY) ─────────────────────
            AnimatedVisibility(
                visible = !isBirthday,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
            ) {
                OutlinedTextField(
                    value         = description,
                    onValueChange = { description = it },
                    label         = { Text("Mô tả (không bắt buộc)") },
                    modifier      = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    maxLines      = 3,
                )
            }

            // ── "Bao gồm năm" toggle (BIRTHDAY only) ──────────────────
            AnimatedVisibility(
                visible = isBirthday,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Autorenew, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bao gồm năm", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Nhắc lại hàng năm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isRecurring, onCheckedChange = { isRecurring = it })
                }
            }

            // ── Color picker ──────────────────────────────────────────
            Text("Màu sắc", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ENTRY_COLOR_OPTIONS.forEach { opt ->
                    val isChosen    = selectedColor == opt.name
                    val circleColor = if (opt.bg == Color.Unspecified) selectedCategory.cardColor() else opt.bg
                    Box(
                        modifier = Modifier
                            .size(if (isChosen) 34.dp else 28.dp)
                            .clip(CircleShape)
                            .background(circleColor)
                            .border(
                                width  = if (isChosen) 2.5.dp else 0.dp,
                                color  = if (isChosen) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape  = CircleShape,
                            )
                            .clickable { selectedColor = opt.name },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isChosen) {
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint     = if (opt.fg != Color.Unspecified) opt.fg else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // ── Note attachment ───────────────────────────────────────
            Text("Đính kèm ghi chú", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (linkedNote != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Notes, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        linkedNote.title,
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { linkedNoteId = null }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                OutlinedButton(
                    onClick  = { showNotePicker = !showNotePicker },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Outlined.Notes, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (showNotePicker) "Đóng danh sách" else "Chọn ghi chú",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                AnimatedVisibility(visible = showNotePicker) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        if (notes.isEmpty()) {
                            Text(
                                "Chưa có ghi chú nào",
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                            )
                        } else {
                            notes.forEach { note ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { linkedNoteId = note.id; showNotePicker = false }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (note.isPinned) {
                                        Icon(Icons.Filled.PushPin, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(note.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (note.content.isNotBlank()) {
                                            Text(note.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 12.dp))
                            }
                        }
                    }
                }
            }

            // ── Reminder ──────────────────────────────────────────────
            Box {
                SheetInfoRow(icon = Icons.Outlined.NotificationsNone, onClick = { showReminderMenu = true }) {
                    Text(
                        text     = formatReminderMinutes(reminderMinutes),
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Default.UnfoldMore, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(
                    expanded         = showReminderMenu,
                    onDismissRequest = { showReminderMenu = false },
                ) {
                    reminderPresets.forEach { (mins, label) ->
                        DropdownMenuItem(
                            text        = { Text(label) },
                            onClick     = { reminderMinutes = mins; showReminderMenu = false },
                            leadingIcon = if (reminderMinutes == mins) ({
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            }) else null,
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text    = { Text("Tuỳ chỉnh...") },
                        onClick = { showReminderMenu = false; showCustomReminder = true },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Reminder label helper ─────────────────────────────────────────────
private fun formatReminderMinutes(minutes: Int?): String {
    if (minutes == null) return "Không có"
    return when {
        minutes == 0        -> "Lúc bắt đầu"
        minutes < 60        -> "Trước $minutes phút"
        minutes % 60 == 0   -> "Trước ${minutes / 60} giờ"
        minutes < 1440      -> "Trước ${minutes / 60} giờ ${minutes % 60} phút"
        minutes % 1440 == 0 -> "Trước ${minutes / 1440} ngày"
        else                -> "Trước ${minutes / 1440} ngày ${(minutes % 1440) / 60} giờ"
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

    val units       = remember { listOf("phút", "giờ", "ngày") }
    val numberItems = remember { (1..59).map { it.toString() } }
    var selectedNum  by remember { mutableIntStateOf(initNum.coerceIn(1, 59)) }
    var selectedUnit by remember { mutableIntStateOf(initUnitIdx) }

    val currentMinutes = remember(selectedNum, selectedUnit) {
        selectedNum * when (selectedUnit) {
            1    -> 60
            2    -> 1440
            else -> 1
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape          = RoundedCornerShape(16.dp),
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // Header
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        text      = "Tuỳ chỉnh",
                        style     = MaterialTheme.typography.titleMedium,
                        modifier  = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { onConfirm(currentMinutes) }) {
                        Text("Xong", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Summary row
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.NotificationsNone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text  = formatReminderMinutes(currentMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Two wheels
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    WheelColumn(
                        items      = numberItems,
                        startIndex = (selectedNum - 1).coerceIn(0, numberItems.lastIndex),
                        onSelected = { idx -> selectedNum = idx + 1 },
                        circular   = true,
                        modifier   = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    WheelColumn(
                        items      = units,
                        startIndex = selectedUnit,
                        onSelected = { idx -> selectedUnit = idx },
                        circular   = false,
                        modifier   = Modifier.weight(1f),
                    )
                }

                HorizontalDivider(
                    color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 4.dp),
                )

                // Footer
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.NotificationsNone, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Thông báo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ── Icon + tappable content row ───────────────────────────────────────
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
