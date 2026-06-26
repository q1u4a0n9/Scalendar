package com.scalendar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.data.model.EntryCategory

/**
 * Provides the user's category color overrides (from Settings → DataStore) to all
 * entry-card composables without threading params through every layer.
 *
 * Key   = EntryCategory.name  (e.g. "SPORT", "TASK")
 * Value = 6-char hex without '#'  (e.g. "9C27B0")
 */
val LocalColorOverrides = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

// ── Category default colors ───────────────────────────────────────────
// Hardcoded so cards always match the indicator colours in the drawer,
// regardless of the Material 3 theme seed (which is forest-green and would
// otherwise tint CLASS/SPORT/etc. cards green via primaryFixed/secondaryFixed).

@Composable
fun EntryCategory.cardColor(): Color = when (this) {
    EntryCategory.TASK     -> Color(0xFFFFCCBC)  // light deep-orange ← #F4511E
    EntryCategory.CLASS    -> Color(0xFFF5F5F5)  // light gray        ← #9E9E9E
    EntryCategory.SPORT    -> Color(0xFFF1F8E9)  // light green       ← #66BB6A
    EntryCategory.EXAM     -> Color(0xFFFFE082)  // light amber-gold  ← #FFA000
    EntryCategory.BIRTHDAY -> MaterialTheme.colorScheme.tertiaryFixed   // pink (theme)
    EntryCategory.EVENT    -> Color(0xFFE3F2FD)  // light blue    ← #1E88E5
}

@Composable
fun EntryCategory.onCardColor(): Color = when (this) {
    EntryCategory.TASK     -> Color(0xFF7B2C00)  // dark orange
    EntryCategory.CLASS    -> Color(0xFF424242)  // dark gray
    EntryCategory.SPORT    -> Color(0xFF2E7D32)  // dark green
    EntryCategory.EXAM     -> Color(0xFF7B5800)  // dark amber
    EntryCategory.BIRTHDAY -> MaterialTheme.colorScheme.onTertiaryFixed // pink (theme)
    EntryCategory.EVENT    -> Color(0xFF0D47A1)  // dark blue
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
 * Background color for an entry.
 *
 * Priority:
 * 1. Per-entry custom color (entry.color != "DEFAULT")
 * 2. Category-level color override from Settings (LocalColorOverrides)
 * 3. Hardcoded default pastel for the category
 */
@Composable
fun EntryEntity.displayBgColor(): Color {
    if (color != "DEFAULT") {
        return ENTRY_COLOR_OPTIONS.find { it.name == color }
            ?.bg?.takeIf { it != Color.Unspecified }
            ?: category.cardColor()
    }
    val overrideHex = LocalColorOverrides.current[category.name]
    if (overrideHex != null) {
        return try { Color(android.graphics.Color.parseColor("#$overrideHex")) }
               catch (_: Exception) { category.cardColor() }
    }
    return category.cardColor()
}

/**
 * Foreground (text/icon) color.
 *
 * Priority:
 * 1. Per-entry custom color → white text
 * 2. Category-level override present → white text (saturated background)
 * 3. Category default on-color
 */
@Composable
fun EntryEntity.displayFgColor(): Color {
    if (color != "DEFAULT") {
        val opt = ENTRY_COLOR_OPTIONS.find { it.name == color }
        return if (opt != null && opt.fg != Color.Unspecified) opt.fg
               else category.onCardColor()
    }
    if (LocalColorOverrides.current.containsKey(category.name)) return Color.White
    return category.onCardColor()
}
