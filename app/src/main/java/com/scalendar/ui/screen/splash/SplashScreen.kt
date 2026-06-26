package com.scalendar.ui.screen.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scalendar.R
import kotlinx.coroutines.delay

private const val APP_NAME = "Scalendar"
private const val LETTER_DELAY_MS = 80L   // delay between each letter fade-in
private const val TAGLINE_DELAY_MS = 1000L // tagline starts after all letters
private const val TOTAL_DURATION_MS = 2200L

@Composable
fun SplashScreen(onComplete: () -> Unit) {
    // Trigger the transition after total duration
    LaunchedEffect(Unit) {
        delay(TOTAL_DURATION_MS)
        onComplete()
    }

    // Logo fade-in
    val logoAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 600, easing = EaseOut),
        label = "logoAlpha",
    )

    // Per-letter alpha: each letter fades in with a staggered delay
    val letterAlphas = APP_NAME.indices.map { i ->
        val startDelay = 400 + i * LETTER_DELAY_MS
        val alphaAnim = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            delay(startDelay)
            alphaAnim.animateTo(1f, animationSpec = tween(120, easing = LinearEasing))
        }
        alphaAnim.value
    }

    // Tagline fade-in
    val taglineAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(TAGLINE_DELAY_MS)
        taglineAlpha.animateTo(1f, animationSpec = tween(500, easing = EaseOut))
    }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Logo circle
            Box(
                modifier         = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .alpha(logoAlpha),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Outlined.AutoStories,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(48.dp),
                )
            }

            // "Scalendar" typewriter
            Row {
                APP_NAME.forEachIndexed { i, ch ->
                    Text(
                        text      = ch.toString(),
                        style     = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.5).sp,
                        ),
                        color     = MaterialTheme.colorScheme.primary,
                        modifier  = Modifier.alpha(letterAlphas[i]),
                    )
                }
            }

            // Tagline
            Text(
                text     = stringResource(R.string.splash_tagline),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(taglineAlpha.value),
            )
        }
    }
}
