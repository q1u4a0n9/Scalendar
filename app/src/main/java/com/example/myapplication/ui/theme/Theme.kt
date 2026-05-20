package com.scalendar.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ── Color schemes ─────────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary                = Primary,
    onPrimary              = OnPrimary,
    primaryContainer       = PrimaryContainer,
    onPrimaryContainer     = OnPrimaryContainer,
    inversePrimary         = InversePrimary,
    secondary              = Secondary,
    onSecondary            = OnSecondary,
    secondaryContainer     = SecondaryContainer,
    onSecondaryContainer   = OnSecondaryContainer,
    tertiary               = Tertiary,
    onTertiary             = OnTertiary,
    tertiaryContainer      = TertiaryContainer,
    onTertiaryContainer    = OnTertiaryContainer,
    error                  = Error,
    onError                = OnError,
    errorContainer         = ErrorContainer,
    onErrorContainer       = OnErrorContainer,
    background             = Background,
    onBackground           = OnBackground,
    surface                = Surface,
    onSurface              = OnSurface,
    surfaceVariant         = SurfaceVariant,
    onSurfaceVariant       = OnSurfaceVariant,
    surfaceTint            = SurfaceTint,
    inverseSurface         = InverseSurface,
    inverseOnSurface       = InverseOnSurface,
    outline                = Outline,
    outlineVariant         = OutlineVariant,
    surfaceBright          = SurfaceBright,
    surfaceDim             = SurfaceDim,
    surfaceContainer       = SurfaceContainer,
    surfaceContainerHigh   = SurfaceContainerHigh,
    surfaceContainerHighest= SurfaceContainerHighest,
    surfaceContainerLow    = SurfaceContainerLow,
    surfaceContainerLowest = SurfaceContainerLowest,
    primaryFixed           = PrimaryFixed,
    primaryFixedDim        = PrimaryFixedDim,
    onPrimaryFixed         = OnPrimaryFixed,
    onPrimaryFixedVariant  = OnPrimaryFixedVariant,
    secondaryFixed         = SecondaryFixed,
    secondaryFixedDim      = SecondaryFixedDim,
    onSecondaryFixed       = OnSecondaryFixed,
    onSecondaryFixedVariant= OnSecondaryFixedVariant,
    tertiaryFixed          = TertiaryFixed,
    tertiaryFixedDim       = TertiaryFixedDim,
    onTertiaryFixed        = OnTertiaryFixed,
    onTertiaryFixedVariant = OnTertiaryFixedVariant,
)

private val DarkColorScheme = darkColorScheme(
    primary                = PrimaryFixedDim,
    onPrimary              = OnPrimaryFixed,
    primaryContainer       = OnPrimaryFixedVariant,
    onPrimaryContainer     = PrimaryFixed,
    secondary              = SecondaryFixedDim,
    onSecondary            = OnSecondaryFixed,
    secondaryContainer     = OnSecondaryFixedVariant,
    onSecondaryContainer   = SecondaryFixed,
    tertiary               = TertiaryFixedDim,
    onTertiary             = OnTertiaryFixed,
    tertiaryContainer      = OnTertiaryFixedVariant,
    onTertiaryContainer    = TertiaryFixed,
    background             = OnBackground,
    onBackground           = Background,
    surface                = InverseSurface,
    onSurface              = InverseOnSurface,
    outline                = Outline,
    outlineVariant         = OnSurfaceVariant,
)

// ── Shapes ────────────────────────────────────────────────────────────
// Extracted from design's borderRadius tokens:
// DEFAULT = 4dp, lg = 8dp, xl = 12dp, full = circle
val ScalendarShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // DEFAULT
    small      = RoundedCornerShape(4.dp),
    medium     = RoundedCornerShape(8.dp),   // lg
    large      = RoundedCornerShape(12.dp),  // xl
    extraLarge = RoundedCornerShape(16.dp),
)

// ── Theme ─────────────────────────────────────────────────────────────
@Composable
fun ScalendarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,           // keep brand colors consistent
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        shapes      = ScalendarShapes,
        content     = content
    )
}
