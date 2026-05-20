package com.scalendar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.data.model.EntryCategory

// ── Category default colors ───────────────────────────────────────────
@Composable
fun EntryCategory.cardColor(): Color = when (this) {
    EntryCategory.TASK     -> MaterialTheme.colorScheme.surfaceContainerLow
    EntryCategory.CLASS    -> MaterialTheme.colorScheme.primaryFixed
    EntryCategory.SPORT    -> MaterialTheme.colorScheme.primaryFixed
    EntryCategory.EXAM     -> MaterialTheme.colorScheme.errorContainer
    EntryCategory.BIRTHDAY -> MaterialTheme.colorScheme.tertiaryFixed
    EntryCategory.EVENT    -> MaterialTheme.colorScheme.secondaryContainer
}

@Composable
fun EntryCategory.onCardColor(): Color = when (this) {
    EntryCategory.TASK     -> MaterialTheme.colorScheme.onSurface
    EntryCategory.CLASS    -> MaterialTheme.colorScheme.onPrimaryFixed
    EntryCategory.SPORT    -> MaterialTheme.colorScheme.onPrimaryFixed
    EntryCategory.EXAM     -> MaterialTheme.colorScheme.onErrorContainer
    EntryCategory.BIRTHDAY -> MaterialTheme.colorScheme.onTertiaryFixed
    EntryCategory.EVENT    -> MaterialTheme.colorScheme.onSecondaryContainer
}

// ── Custom color palette ──────────────────────────────────────────────
data class EntryColorOption(val name: String, val bg: Color, val fg: Color)

val ENTRY_COLOR_OPTIONS = listOf(
    EntryColorOption("DEFAULT", Color.Unspecified, Color.Unspecified),
    EntryColorOption("BLUE",    Color(0xFF4A90D9), Color.White),
    EntryColorOption("PURPLE",  Color(0xFF9C27B0), Color.White),
    EntryColorOption("RED",     Color(0xFFE53935), Color.White),
    EntryColorOption("ORANGE",  Color(0xFFFF7043), Color.White),
    EntryColorOption("PINK",    Color(0xFFEC407A), Color.White),
    EntryColorOption("BROWN",   Color(0xFF795548), Color.White),
    EntryColorOption("TEAL",    Color(0xFF00897B), Color.White),
    EntryColorOption("GREEN",   Color(0xFF43A047), Color.White),
)

/**
 * Background color for an entry: uses the custom color when set,
 * otherwise falls back to the category's Material theme color.
 */
@Composable
fun EntryEntity.displayBgColor(): Color {
    if (color == "DEFAULT") return category.cardColor()
    return ENTRY_COLOR_OPTIONS.find { it.name == color }
        ?.bg?.takeIf { it != Color.Unspecified }
        ?: category.cardColor()
}

/**
 * Foreground (text/icon) color: white for custom colors,
 * otherwise the category's on-color from Material theme.
 */
@Composable
fun EntryEntity.displayFgColor(): Color {
    if (color == "DEFAULT") return category.onCardColor()
    val opt = ENTRY_COLOR_OPTIONS.find { it.name == color }
    return if (opt != null && opt.fg != Color.Unspecified) opt.fg
           else category.onCardColor()
}
