package com.scalendar.ui.screen.notes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scalendar.R
import com.scalendar.data.database.entity.NoteEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Design token
private val NoteFabBrown = Color(0xFF7A5B48)

// ── Screen ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel   : NotesViewModel = hiltViewModel(),
    onOpenSearch: () -> Unit     = {},
    noteToOpen  : NoteEntity?    = null,
    onNoteOpened: () -> Unit     = {},
) {
    val uiState    by viewModel.uiState.collectAsState()
    var isGridView by rememberSaveable { mutableStateOf(false) }

    // Editor overlay state — pinnedNote is NOT cleared on close so the exit-animation
    // still renders the correct note while it slides out.
    var isEditorOpen by remember { mutableStateOf(false) }
    var pinnedNote   by remember { mutableStateOf<NoteEntity?>(null) }

    fun openNew() {
        pinnedNote   = NoteEntity(id = 0L, title = "", content = "", date = LocalDate.now())
        isEditorOpen = true
    }
    fun openExisting(note: NoteEntity) {
        pinnedNote   = note
        isEditorOpen = true
    }

    // Open a specific note requested by the parent (e.g. tapped in search overlay).
    LaunchedEffect(noteToOpen) {
        val note = noteToOpen ?: return@LaunchedEffect
        openExisting(note)
        onNoteOpened()
    }

    Box(Modifier.fillMaxSize()) {

        // ── Main list scaffold ──────────────────────────────────────────
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text       = stringResource(R.string.nav_notes),
                            style      = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary,
                        )
                    },
                    actions = {
                        // List ↔ Grid toggle
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                imageVector        = if (isGridView) Icons.AutoMirrored.Outlined.ViewList
                                                     else            Icons.Outlined.GridView,
                                contentDescription = if (isGridView) stringResource(R.string.notes_view_list)
                                                     else            stringResource(R.string.notes_view_grid),
                                tint               = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->

            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                // ── Empty state ──────────────────────────────────────────
                if (uiState.sections.isEmpty() && !uiState.isLoading) {
                    Box(
                        modifier         = Modifier
                            .fillMaxSize()
                            .padding(bottom = 72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text  = stringResource(R.string.notes_empty_state),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    // ── List / Grid content ──────────────────────────────
                    AnimatedContent(
                        targetState    = isGridView,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label          = "notesViewMode",
                        modifier       = Modifier.fillMaxSize(),
                    ) { grid ->
                        if (!grid) {
                            LazyColumn(
                                contentPadding      = PaddingValues(
                                    start  = 20.dp, end = 20.dp,
                                    top    = 4.dp,  bottom = 88.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                uiState.sections.forEach { section ->
                                    item(key = "hdr_${section.group}") {
                                        NoteSectionHeader(section.group.localizedName())
                                    }
                                    items(section.notes, key = { it.id }) { note ->
                                        NoteCard(
                                            note     = note,
                                            onClick  = { openExisting(note) },
                                            onPin    = { viewModel.togglePin(note) },
                                            onDelete = { viewModel.deleteNote(note) },
                                        )
                                    }
                                    item(key = "div_${section.group}") {
                                        Spacer(Modifier.height(4.dp))
                                    }
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns               = GridCells.Fixed(2),
                                contentPadding        = PaddingValues(
                                    start  = 16.dp, end = 16.dp,
                                    top    = 4.dp,  bottom = 88.dp,
                                ),
                                verticalArrangement   = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                uiState.sections.forEach { section ->
                                    item(
                                        key  = "hdr_${section.group}",
                                        span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) },
                                    ) {
                                        NoteSectionHeader(section.group.localizedName())
                                    }
                                    items(section.notes, key = { it.id }) { note ->
                                        NoteGridCard(
                                            note     = note,
                                            onClick  = { openExisting(note) },
                                            onPin    = { viewModel.togglePin(note) },
                                            onDelete = { viewModel.deleteNote(note) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Bottom bar: search trigger + FAB ────────────────────
                Row(
                    modifier              = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Tappable search bar — opens full-screen overlay (same as calendar)
                    Row(
                        modifier          = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable { onOpenSearch() }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Search,
                            contentDescription = null,
                            modifier           = Modifier.size(20.dp),
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text  = stringResource(R.string.notes_search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Pencil FAB
                    Box(
                        modifier         = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(NoteFabBrown)
                            .clickable { openNew() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.notes_new_note),
                            tint               = Color.White,
                            modifier           = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        // ── Full-screen editor (slides in from right over the list) ─────
        AnimatedVisibility(
            visible = isEditorOpen,
            enter   = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(280)),
            exit    = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(220)),
        ) {
            pinnedNote?.let { note ->
                NoteEditorOverlay(
                    note     = note,
                    onClose  = { title, content ->
                        viewModel.saveNote(note, title, content)
                        isEditorOpen = false
                    },
                    onPin    = {
                        pinnedNote = note.copy(isPinned = !note.isPinned)
                        viewModel.togglePin(note)
                    },
                    onDelete = {
                        viewModel.deleteNote(note)
                        isEditorOpen = false
                    },
                )
            }
        }

    }
}

// ── Section header ────────────────────────────────────────────────────
@Composable
private fun NoteSectionHeader(label: String) {
    Text(
        text       = label,
        style      = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.primary,
        modifier   = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )
}

// ── Note card (LIST mode) ─────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note    : NoteEntity,
    onClick : () -> Unit,
    onPin   : () -> Unit,
    onDelete: () -> Unit,
) {
    val locale        = LocalConfiguration.current.locales[0]
    val NOTE_DATE_FMT = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val cardBg = MaterialTheme.colorScheme.surfaceContainerLow

    val haptic   = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick     = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    },
                ),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = note.title,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f),
                    )
                    if (note.isPinned) {
                        Icon(
                            imageVector        = Icons.Filled.PushPin,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier
                                .padding(start = 6.dp)
                                .size(14.dp),
                        )
                    }
                }

                if (note.content.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text     = note.content,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        modifier           = Modifier.size(11.dp),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text  = note.date.format(NOTE_DATE_FMT),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        DropdownMenu(
            expanded         = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text        = { Text(stringResource(if (note.isPinned) R.string.notes_unpin_note else R.string.notes_pin_note)) },
                onClick     = { showMenu = false; onPin() },
                leadingIcon = {
                    Icon(
                        imageVector        = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text        = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                onClick     = { showMenu = false; onDelete() },
                leadingIcon = {
                    Icon(
                        imageVector        = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
    }
}

// ── Note card (GRID mode) ─────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteGridCard(
    note    : NoteEntity,
    onClick : () -> Unit,
    onPin   : () -> Unit,
    onDelete: () -> Unit,
) {
    val locale        = LocalConfiguration.current.locales[0]
    val NOTE_DATE_FMT = remember(locale) { DateTimeFormatter.ofPattern("d/M", locale) }
    val cardBg = MaterialTheme.colorScheme.surfaceContainerLow

    val haptic   = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .combinedClickable(
                    onClick     = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    },
                ),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text       = note.title,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f),
                    )
                    if (note.isPinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(12.dp),
                        )
                    }
                }

                if (note.content.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text     = note.content,
                        style    = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text  = note.date.format(NOTE_DATE_FMT),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DropdownMenu(
            expanded         = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text        = { Text(stringResource(if (note.isPinned) R.string.notes_unpin_note else R.string.notes_pin_note)) },
                onClick     = { showMenu = false; onPin() },
                leadingIcon = {
                    Icon(
                        imageVector        = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text        = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                onClick     = { showMenu = false; onDelete() },
                leadingIcon = {
                    Icon(
                        imageVector        = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
    }
}

// ── Note search overlay — search bar pinned above keyboard ────────────
@Composable
internal fun NoteSearchOverlay(
    query        : String,
    results      : List<NoteEntity>,
    onQueryChange: (String) -> Unit,
    onDismiss    : () -> Unit,
    onNoteClick  : (NoteEntity) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            // ── Status bar spacer ────────────────────────────────────────
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            // ── Results / hint (fills remaining space above search bar) ──
            Box(modifier = Modifier.weight(1f)) {
                when {
                    query.isBlank() -> {
                        Box(
                            modifier         = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text  = stringResource(R.string.search_enter_keyword),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    results.isEmpty() -> {
                        Box(
                            modifier         = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text  = stringResource(R.string.notes_no_results),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            contentPadding      = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(results, key = { it.id }) { note ->
                                NoteSearchResultCard(
                                    note    = note,
                                    onClick = { onNoteClick(note) },
                                )
                            }
                        }
                    }
                }
            }

            // ── Search bar ───────────────────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                OutlinedTextField(
                    value         = query,
                    onValueChange = onQueryChange,
                    modifier      = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder   = { Text(stringResource(R.string.notes_search_hint)) },
                    singleLine    = true,
                    trailingIcon  = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.action_close),
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                )
            }

        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

// ── Note search result card ───────────────────────────────────────────
@Composable
internal fun NoteSearchResultCard(note: NoteEntity, onClick: () -> Unit) {
    val locale  = LocalConfiguration.current.locales[0]
    val dateFmt = remember(locale) { DateTimeFormatter.ofPattern("d MMM yyyy", locale) }

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Accent stripe (primary if pinned, surfaceVariant otherwise)
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                .background(
                    if (note.isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    else               MaterialTheme.colorScheme.surfaceVariant,
                ),
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = note.title,
                style    = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color    = MaterialTheme.colorScheme.onBackground,
            )
            if (note.content.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text     = note.content,
                    style    = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text  = note.date.format(dateFmt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (note.isPinned) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.PushPin,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ── Full-screen note editor (iPhone-style) ────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorOverlay(
    note    : NoteEntity,
    onClose : (title: String, content: String) -> Unit,
    onPin   : () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    var title   by remember(note.id) { mutableStateOf(note.title) }
    var content by remember(note.id) { mutableStateOf(note.content) }

    val locale  = LocalConfiguration.current.locales[0]
    val dateFmt = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }

    val titleFocusRequester = remember { FocusRequester() }

    // Show Done button only when user is actively editing a field.
    // New notes (id == 0L) start in editing mode immediately (auto-focus title).
    var isEditing         by remember(note.id) { mutableStateOf(note.id == 0L) }
    var showMenu          by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Intercept system back → auto-save then close
    BackHandler { onClose(title, content) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title            = { Text(stringResource(R.string.notes_delete_confirm)) },
            confirmButton    = {
                TextButton(
                    onClick = { showDeleteConfirm = false; onDelete() },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton    = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (note.id != 0L) {
                        Text(
                            text  = note.date.format(dateFmt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onClose(title, content) }) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    // ⋮ more-options button (always visible)
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector        = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.action_menu),
                            )
                        }
                        DropdownMenu(
                            expanded         = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text        = { Text(stringResource(if (note.isPinned) R.string.notes_unpin_note else R.string.notes_pin_note)) },
                                onClick     = { showMenu = false; onPin() },
                                leadingIcon = {
                                    Icon(
                                        imageVector        = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                        contentDescription = null,
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text        = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                                onClick     = { showMenu = false; showDeleteConfirm = true },
                                leadingIcon = {
                                    Icon(
                                        imageVector        = Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint               = MaterialTheme.colorScheme.error,
                                    )
                                },
                            )
                        }
                    }
                    // ✓ Done button — slides in from left when user starts editing
                    AnimatedVisibility(
                        visible = isEditing,
                        enter   = slideInHorizontally(tween(220)) { -it },
                        exit    = slideOutHorizontally(tween(180)) { -it },
                    ) {
                        IconButton(onClick = { onClose(title, content) }) {
                            Icon(
                                imageVector        = Icons.Default.Check,
                                contentDescription = stringResource(R.string.notes_editor_done),
                                tint               = MaterialTheme.colorScheme.primary,
                            )
                        }
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
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // ── Title field ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                if (title.isEmpty()) {
                    Text(
                        text  = stringResource(R.string.notes_add_title),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    )
                }
                BasicTextField(
                    value         = title,
                    onValueChange = { title = it },
                    singleLine    = true,
                    textStyle     = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush   = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier      = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester)
                        .onFocusChanged { if (it.isFocused) isEditing = true },
                )
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outlineVariant,
            )

            // ── Content field ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 400.dp)
                    .padding(top = 12.dp),
            ) {
                if (content.isEmpty()) {
                    Text(
                        text  = stringResource(R.string.notes_add_content),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    )
                }
                BasicTextField(
                    value         = content,
                    onValueChange = { content = it },
                    textStyle     = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush   = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier      = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) isEditing = true },
                )
            }
        }
    }

    // Auto-focus title when creating a new note
    LaunchedEffect(note.id) {
        if (note.id == 0L) titleFocusRequester.requestFocus()
    }
}
