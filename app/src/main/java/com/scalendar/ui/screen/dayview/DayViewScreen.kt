package com.scalendar.ui.screen.dayview

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.data.model.EntryCategory
import com.scalendar.data.model.TimeOfDay
import com.scalendar.ui.component.WheelDatePicker
import com.scalendar.ui.shared.SharedCalendarViewModel
import com.scalendar.ui.theme.ENTRY_COLOR_OPTIONS
import com.scalendar.ui.theme.cardColor
import com.scalendar.ui.theme.displayBgColor
import com.scalendar.ui.theme.displayFgColor
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── Formatters ────────────────────────────────────────────────────────
private val DAY_NAME_FMT  = DateTimeFormatter.ofPattern("EEEE",        Locale.forLanguageTag("vi"))
private val DATE_SUB_FMT  = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("vi"))
private val DATE_CHIP_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("vi"))
private val TIME_FMT      = DateTimeFormatter.ofPattern("HH:mm")

// ── Category icon mapping ─────────────────────────────────────────────
private fun EntryCategory.fabIcon(): ImageVector = when (this) {
    EntryCategory.TASK     -> Icons.Outlined.TaskAlt
    EntryCategory.CLASS    -> Icons.Outlined.School
    EntryCategory.SPORT    -> Icons.Outlined.SportsBasketball
    EntryCategory.EXAM     -> Icons.Outlined.Description
    EntryCategory.BIRTHDAY -> Icons.Outlined.Cake
    EntryCategory.EVENT    -> Icons.Outlined.Celebration
}

// ── Design tokens for FAB overlay ────────────────────────────────────
private val FabOverlayLabelBg   = Color(0xFF3C4043).copy(alpha = 0.85f)
private val FabOverlayLabelText = Color(0xFFE4E2DD)
private val FabOverlayIconBg    = Color(0xFF3C4043)
private val FabOverlayIconTint  = Color(0xFF8AB4F8)
private val FabEventIconBg      = Color(0xFF8AB4F8)
private val FabEventIconTint    = Color(0xFF202124)

// ── Stagger config ────────────────────────────────────────────────────
private val FAB_CATEGORY_ORDER = listOf(
    EntryCategory.BIRTHDAY,
    EntryCategory.CLASS,
    EntryCategory.TASK,
    EntryCategory.SPORT,
    EntryCategory.EXAM,
    EntryCategory.EVENT,
)

// ── Screen ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayViewScreen(
    sharedVm    : SharedCalendarViewModel,
    onOpenDrawer: () -> Unit,
    viewModel   : DayViewViewModel = hiltViewModel(),
) {
    val selectedDate    by sharedVm.selectedDate.collectAsState()
    val categoryFilters by sharedVm.categoryFilters.collectAsState()
    val uiState         by viewModel.uiState.collectAsState()

    LaunchedEffect(selectedDate) { viewModel.setDate(selectedDate) }

    var showFabMenu      by remember { mutableStateOf(false) }
    var addEntryCategory by remember { mutableStateOf<EntryCategory?>(null) }

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
                    onNavigateToToday = { sharedVm.selectDate(LocalDate.now()) },
                )
            },
            floatingActionButton = {
                DayFab(expanded = showFabMenu, onToggle = { showFabMenu = !showFabMenu })
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
                TimeOfDay.entries.forEach { slot ->
                    val slotEntries = (uiState.entriesBySlot[slot] ?: emptyList())
                        .filter { it.category in categoryFilters }
                    TimeSlotSection(
                        slot     = slot,
                        entries  = slotEntries,
                        onToggle = viewModel::toggleComplete,
                        onDelete = viewModel::deleteEntry,
                    )
                }
            }
        }

        // ── Scrim ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showFabMenu,
            enter   = fadeIn(tween(220)),
            exit    = fadeOut(tween(180)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable { showFabMenu = false }
            )
        }

        // ── Staggered category picker ─────────────────────────────────
        Column(
            modifier            = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 88.dp)
                .padding(8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FAB_CATEGORY_ORDER.forEachIndexed { index, cat ->
                val enterDelay = (FAB_CATEGORY_ORDER.size - 1 - index) * 55
                val exitDelay  = index * 30

                AnimatedVisibility(
                    visible = showFabMenu,
                    enter   = fadeIn(tween(200, delayMillis = enterDelay)) +
                              slideInVertically(tween(240, delayMillis = enterDelay)) { it / 2 },
                    exit    = fadeOut(tween(130, delayMillis = exitDelay)) +
                              slideOutVertically(tween(130, delayMillis = exitDelay)) { it / 3 },
                ) {
                    CategoryFabItem(
                        category = cat,
                        onClick  = {
                            addEntryCategory = cat
                            showFabMenu = false
                        },
                    )
                }
            }
        }
    }

    // ── Add Entry bottom sheet ────────────────────────────────────────
    val notes by viewModel.notes.collectAsState()
    if (addEntryCategory != null) {
        AddEntrySheet(
            initialCategory = addEntryCategory!!,
            date            = selectedDate,
            notes           = notes,
            onSave          = { entity ->
                viewModel.addEntry(entity)
                addEntryCategory = null
            },
            onDismiss = { addEntryCategory = null },
        )
    }
}

// ── Top bar ───────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayTopBar(
    date             : LocalDate,
    onOpenDrawer     : () -> Unit,
    onNavigateToToday: () -> Unit,
) {
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
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        actions = {
            IconButton(onClick = onNavigateToToday) {
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
}

// ── FAB ───────────────────────────────────────────────────────────────
@Composable
private fun DayFab(expanded: Boolean, onToggle: () -> Unit) {
    val iconRotation by animateFloatAsState(
        targetValue   = if (expanded) 45f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label         = "fab_icon_rotation",
    )
    FloatingActionButton(
        onClick        = onToggle,
        containerColor = if (expanded) MaterialTheme.colorScheme.surfaceContainerHighest
                         else          MaterialTheme.colorScheme.tertiary,
        contentColor   = if (expanded) MaterialTheme.colorScheme.onSurface
                         else          MaterialTheme.colorScheme.onTertiary,
        shape          = if (expanded) RoundedCornerShape(16.dp) else RoundedCornerShape(12.dp),
    ) {
        Icon(
            imageVector        = Icons.Default.Add,
            contentDescription = if (expanded) "Đóng" else "Thêm mục",
            modifier           = Modifier.rotate(iconRotation),
        )
    }
}

// ── Category FAB item ─────────────────────────────────────────────────
@Composable
private fun CategoryFabItem(category: EntryCategory, onClick: () -> Unit) {
    val isEvent      = category == EntryCategory.EVENT
    val iconBg       = if (isEvent) FabEventIconBg  else FabOverlayIconBg
    val iconTint     = if (isEvent) FabEventIconTint else FabOverlayIconTint
    val iconSize     = if (isEvent) 56.dp else 40.dp
    val iconShape    = if (isEvent) RoundedCornerShape(28.dp) else CircleShape
    val innerIcon    = if (isEvent) 28.dp else 22.dp

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier              = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text     = category.displayName(),
            style    = MaterialTheme.typography.labelMedium,
            color    = FabOverlayLabelText,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(FabOverlayLabelBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Box(
            modifier         = Modifier.size(iconSize).clip(iconShape).background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(category.fabIcon(), null, tint = iconTint, modifier = Modifier.size(innerIcon))
        }
    }
}

// ── Time-slot section ─────────────────────────────────────────────────
@Composable
private fun TimeSlotSection(
    slot    : TimeOfDay,
    entries : List<EntryEntity>,
    onToggle: (EntryEntity) -> Unit,
    onDelete: (EntryEntity) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text     = slot.displayName(),
            style    = MaterialTheme.typography.titleLarge,
            color    = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (entries.isEmpty()) {
            Text(
                text     = "Chưa có mục nào",
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        } else {
            entries.forEach { entry ->
                EntryCard(
                    entry    = entry,
                    onToggle = { onToggle(entry) },
                    onDelete = { onDelete(entry) },
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
    onDelete : () -> Unit,
    modifier : Modifier = Modifier,
) {
    val bg = entry.displayBgColor()
    val fg = entry.displayFgColor()

    Row(
        modifier          = modifier
            .fillMaxWidth()
            .alpha(if (entry.isCompleted) 0.55f else 1f)
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked         = entry.isCompleted,
            onCheckedChange = { onToggle() },
            colors          = CheckboxDefaults.colors(
                checkedColor   = fg.copy(alpha = 0.8f),
                uncheckedColor = fg.copy(alpha = 0.5f),
                checkmarkColor = bg,
            ),
            modifier = Modifier.size(20.dp),
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
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text     = entry.category.displayName(),
            style    = MaterialTheme.typography.labelSmall,
            color    = fg.copy(alpha = 0.8f),
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(fg.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, "Xóa", tint = fg.copy(alpha = 0.55f), modifier = Modifier.size(16.dp))
        }
    }
}

// ── Add Entry sheet ────────────────────────────────────────────────────
// Layout:
//   1. Name/Title field  ← always at top
//   2. Category chips
//   3. Date row  →  drum-roll picker
//   4. "Bao gồm năm" toggle  (BIRTHDAY only, right under date)
//   5. Time row  →  TimePicker dialog  (auto-assigns timeOfDay + startTime)
//   6. Description  (hidden for BIRTHDAY)
//   7. Color picker
//   8. Note attachment
//   9. Reminder chips
//  10. Save
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEntrySheet(
    initialCategory : EntryCategory,
    date            : LocalDate,
    notes           : List<com.scalendar.data.database.entity.NoteEntity>,
    onSave          : (EntryEntity) -> Unit,
    onDismiss       : () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ── Form state ────────────────────────────────────────────────────
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var title            by remember { mutableStateOf("") }
    var description      by remember { mutableStateOf("") }
    var selectedDate     by remember { mutableStateOf(date) }
    var showDatePicker   by remember { mutableStateOf(false) }
    var selectedColor    by remember { mutableStateOf("DEFAULT") }
    var selectedTime     by remember { mutableStateOf<LocalTime?>(null) }
    var showTimePicker   by remember { mutableStateOf(false) }
    var linkedNoteId     by remember { mutableStateOf<Long?>(null) }
    var showNotePicker   by remember { mutableStateOf(false) }
    var isRecurring      by remember { mutableStateOf(false) }
    val reminderSet      = remember { mutableStateListOf<Int>() }

    // Derive timeOfDay from the picked time; ANYTIME when no time selected
    val derivedTimeOfDay = remember(selectedTime) {
        selectedTime?.let {
            when {
                it.hour < 12 -> TimeOfDay.MORNING
                it.hour < 18 -> TimeOfDay.AFTERNOON
                else         -> TimeOfDay.EVENING
            }
        } ?: TimeOfDay.ANYTIME
    }

    val linkedNote     = notes.firstOrNull { it.id == linkedNoteId }
    val isBirthday     = selectedCategory == EntryCategory.BIRTHDAY
    val namePlaceholder = if (isBirthday) "Thêm tên" else "Tiêu đề"

    // ── TimePicker dialog ─────────────────────────────────────────────
    if (showTimePicker) {
        val tpState = rememberTimePickerState(
            initialHour   = selectedTime?.hour   ?: LocalTime.now().hour,
            initialMinute = selectedTime?.minute ?: 0,
            is24Hour      = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedTime   = LocalTime.of(tpState.hour, tpState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Hủy") }
            },
            text = {
                TimePicker(state = tpState)
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {

            // ── 1. Name / Title ───────────────────────────────────────
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
                    textStyle      = MaterialTheme.typography.titleMedium,
                    modifier       = Modifier.fillMaxWidth(),
                    singleLine     = true,
                    colors         = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor   = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    ),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // ── 2. Category chips ─────────────────────────────────────
            Row(
                modifier              = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EntryCategory.entries.forEach { cat ->
                    val isSelected = cat == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick  = { selectedCategory = cat },
                        label    = {
                            Text(
                                cat.displayName(),
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        leadingIcon = if (isSelected) ({
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                        }) else null,
                    )
                }
            }

            // ── 3. Date row + drum-roll ───────────────────────────────
            SheetInfoRow(
                icon    = Icons.Outlined.CalendarToday,
                onClick = { showDatePicker = !showDatePicker },
            ) {
                Text(
                    text  = selectedDate.format(DATE_CHIP_FMT),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector        = if (showDatePicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
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

            // ── 4. "Bao gồm năm" toggle (BIRTHDAY only) ──────────────
            AnimatedVisibility(
                visible = isBirthday,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    modifier          = Modifier
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

            // ── 5. Time row ───────────────────────────────────────────
            SheetInfoRow(
                icon    = Icons.Outlined.AccessTime,
                onClick = { showTimePicker = true },
            ) {
                if (selectedTime == null) {
                    Text(
                        text  = "Thêm thời gian",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text  = selectedTime!!.format(TIME_FMT),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text  = derivedTimeOfDay.displayName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick  = { selectedTime = null },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Default.Close, "Xóa thời gian", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // ── 6. Description (hidden for BIRTHDAY) ──────────────────
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

            // ── 7. Color picker ───────────────────────────────────────
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
                                Icons.Default.Check, null,
                                tint     = if (opt.fg != Color.Unspecified) opt.fg else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // ── 8. Note attachment ────────────────────────────────────
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
                    Text(linkedNote.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { linkedNoteId = null }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, "Bỏ đính kèm", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text(if (showNotePicker) "Đóng danh sách" else "Chọn ghi chú", style = MaterialTheme.typography.labelMedium)
                }
                AnimatedVisibility(visible = showNotePicker) {
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                        if (notes.isEmpty()) {
                            Text("Chưa có ghi chú nào", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                        } else {
                            notes.forEach { note ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { linkedNoteId = note.id; showNotePicker = false }.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
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

            // ── 9. Reminder chips ─────────────────────────────────────
            Text("Nhắc nhở", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val reminderOptions = listOf(0 to "Hôm đó", 1 to "1 ngày trước", 3 to "3 ngày trước", 7 to "1 tuần trước")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reminderOptions.forEach { (offset, label) ->
                    val isOn = offset in reminderSet
                    FilterChip(
                        selected = isOn,
                        onClick  = { if (isOn) reminderSet.remove(offset) else reminderSet.add(offset) },
                        label    = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = if (isOn) ({ Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(14.dp)) })
                                      else      ({ Icon(Icons.Outlined.NotificationsNone,  null, modifier = Modifier.size(14.dp)) }),
                    )
                }
            }
            if (reminderSet.isNotEmpty()) {
                Text("Thông báo lúc 09:00 vào ngày được chọn", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ── 10. Save ──────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(
                            EntryEntity(
                                title           = title.trim(),
                                category        = selectedCategory,
                                timeOfDay       = derivedTimeOfDay,
                                startTime       = selectedTime,
                                endTime         = null,
                                date            = selectedDate,
                                description     = if (isBirthday) "" else description.trim(),
                                linkedNoteId    = linkedNoteId,
                                color           = selectedColor,
                                reminderOffsets = reminderSet.sorted().joinToString(","),
                                isRecurring     = isRecurring,
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled  = title.isNotBlank(),
            ) {
                Text("Lưu", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp))
            }
        }
    }
}

// ── Helper: icon + tappable content row ───────────────────────────────
@Composable
private fun SheetInfoRow(
    icon    : ImageVector,
    onClick : () -> Unit,
    content : @Composable RowScope.() -> Unit,
) {
    Row(
        modifier          = Modifier
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
