package com.scalendar.ui.screen.notes

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scalendar.data.database.entity.NoteEntity
import java.time.format.DateTimeFormatter
import java.util.Locale

// Design tokens from gd_noteview.txt
private val NOTE_DATE_FMT = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("vi"))
private val NoteFabBrown  = Color(0xFF7A5B48)   // fabBg

// ── Screen ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(viewModel: NotesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val query   by viewModel.query.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Ghi chú",
                        style      = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary,
                    )
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
            // ── Note list ──────────────────────────────────────────────
            if (uiState.sections.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier         = Modifier
                        .fillMaxSize()
                        .padding(bottom = 72.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = if (query.isNotBlank()) "Không tìm thấy kết quả" else "Chưa có ghi chú",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(
                        start  = 20.dp,
                        end    = 20.dp,
                        top    = 4.dp,
                        bottom = 88.dp,   // leave space for bottom search bar
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.sections.forEach { section ->
                        item(key = "hdr_${section.group}") {
                            NoteSectionHeader(section.group.displayName())
                        }
                        items(section.notes, key = { it.id }) { note ->
                            NoteCard(
                                note     = note,
                                onPin    = { viewModel.togglePin(note) },
                                onDelete = { viewModel.deleteNote(note) },
                            )
                        }
                        item(key = "div_${section.group}") {
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }

            // ── Bottom search bar + FAB (sticky, above nav) ───────────
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Pill-shaped search field
                TextField(
                    value         = query,
                    onValueChange = viewModel::setQuery,
                    placeholder   = {
                        Text(
                            text  = "Tìm kiếm…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingIcon   = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon  = if (query.isNotEmpty()) ({
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Close, "Xóa", Modifier.size(18.dp))
                        }
                    }) else null,
                    modifier   = Modifier.weight(1f),
                    shape      = RoundedCornerShape(28.dp),
                    singleLine = true,
                    colors     = TextFieldDefaults.colors(
                        focusedContainerColor   = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor   = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor  = Color.Transparent,
                    ),
                )

                // Brown edit FAB
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(NoteFabBrown)
                        .clickable { showAddSheet = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Viết ghi chú mới",
                        tint     = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddNoteSheet(
            onSave    = { title, content ->
                viewModel.addNote(title, content)
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false },
        )
    }
}

// ── Section header ────────────────────────────────────────────────────
// Design: font-serif text-[26px] font-bold text-titleColor
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

// ── Note card ─────────────────────────────────────────────────────────
// Design: bg-cardBg rounded-2xl p-5 shadow-sm border
//         title (lg bold) · content (2-line preview) · date+icon at bottom
@Composable
private fun NoteCard(
    note    : NoteEntity,
    onPin   : () -> Unit,
    onDelete: () -> Unit,
) {
    val cardBg = if (note.isPinned)
        MaterialTheme.colorScheme.primaryFixed.copy(alpha = 0.35f)
    else
        MaterialTheme.colorScheme.surfaceContainerLow

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {

            // Title row with action icons
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
                IconButton(onClick = onPin, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (note.isPinned) "Bỏ ghim" else "Ghim",
                        tint     = if (note.isPinned) MaterialTheme.colorScheme.primary
                                   else               MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Xóa",
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Content preview — 2 lines (always visible)
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

            // Date with calendar icon at bottom-left
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
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
}

// ── Add note bottom sheet ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddNoteSheet(
    onSave   : (title: String, content: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title   by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Ghi chú mới",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            OutlinedTextField(
                value         = title,
                onValueChange = { title = it },
                label         = { Text("Tiêu đề") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
            )

            OutlinedTextField(
                value         = content,
                onValueChange = { content = it },
                label         = { Text("Nội dung") },
                modifier      = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                maxLines      = 8,
            )

            Button(
                onClick  = { if (title.isNotBlank()) onSave(title.trim(), content.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled  = title.isNotBlank(),
            ) {
                Text("Lưu")
            }
        }
    }
}
