package com.scalendar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// ── Font families ─────────────────────────────────────────────────────
// Placeholder system fonts. To use real fonts, replace with GoogleFont or
// bundled res/font/ files (Newsreader.ttf, PlusJakartaSans.ttf).
val NewsreaderFamily        = FontFamily.Serif      // → Newsreader
val PlusJakartaSansFamily   = FontFamily.Default    // → Plus Jakarta Sans

// ── Custom design scale ───────────────────────────────────────────────
// display-lg  : 40sp / 48 / Newsreader SemiBold / tracking -0.02em
// headline-md : 28sp / 36 / Newsreader Medium
// title-lg    : 20sp / 28 / Jakarta Sans SemiBold
// body-md     : 16sp / 24 / Jakarta Sans Regular
// label-sm    : 12sp / 16 / Jakarta Sans Medium / tracking +0.05em

val Typography = Typography(
    // Used for large calendar day numbers (week/month grid)
    displayLarge = TextStyle(
        fontFamily = NewsreaderFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.02).em
    ),
    // Used for screen titles (day name, month name, "Journal", etc.)
    headlineMedium = TextStyle(
        fontFamily = NewsreaderFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    // Used for section headers ("Morning", "Anytime", month section names)
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    // Used for note card titles, entry titles (slightly smaller)
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    // Used for entry text, note body
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    // Used for labels, timestamps, category badges
    labelSmall = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = (0.05).em
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = (0.05).em
    )
)
