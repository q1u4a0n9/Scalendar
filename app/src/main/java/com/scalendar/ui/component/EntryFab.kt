package com.scalendar.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scalendar.R
import com.scalendar.data.model.EntryCategory
import com.scalendar.data.model.localizedName

// ── Stagger config ────────────────────────────────────────────────────
internal val FAB_CATEGORY_ORDER = listOf(
    EntryCategory.BIRTHDAY,
    EntryCategory.CLASS,
    EntryCategory.TASK,
    EntryCategory.SPORT,
    EntryCategory.EXAM,
    EntryCategory.EVENT,
)

// ── Design tokens ─────────────────────────────────────────────────────
internal val FabOverlayLabelBg   = Color(0xFF3C4043).copy(alpha = 0.85f)
internal val FabOverlayLabelText = Color(0xFFE4E2DD)
internal val FabIconBg           = Color(0xFF8AB4F8)   // light blue — shared by all items
internal val FabIconTint         = Color(0xFF202124)   // dark icon on light bg

// ── Category → icon mapping ───────────────────────────────────────────
internal fun EntryCategory.fabIcon(): ImageVector = when (this) {
    EntryCategory.TASK     -> Icons.Outlined.TaskAlt
    EntryCategory.CLASS    -> Icons.Outlined.School
    EntryCategory.SPORT    -> Icons.Outlined.SportsBasketball
    EntryCategory.EXAM     -> Icons.Outlined.Description
    EntryCategory.BIRTHDAY -> Icons.Outlined.Cake
    EntryCategory.EVENT    -> Icons.Outlined.Celebration
}

// ── Main FAB button (+) ────────────────────────────────────────────────
@Composable
internal fun EntryFab(expanded: Boolean, onToggle: () -> Unit) {
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
            contentDescription = if (expanded) stringResource(R.string.action_close) else stringResource(R.string.fab_add_entry),
            modifier           = Modifier.rotate(iconRotation),
        )
    }
}

// ── Individual category item ──────────────────────────────────────────
@Composable
internal fun CategoryFabItem(category: EntryCategory, onClick: () -> Unit) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier              = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text     = category.localizedName(),
            style    = MaterialTheme.typography.labelMedium,
            color    = FabOverlayLabelText,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(FabOverlayLabelBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Box(
            modifier         = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(FabIconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(category.fabIcon(), null, tint = FabIconTint, modifier = Modifier.size(28.dp))
        }
    }
}

/**
 * Semi-transparent scrim + staggered category menu overlay.
 * Must be placed inside a [BoxScope] (i.e., a Box that fills the screen).
 */
@Composable
internal fun BoxScope.EntryFabMenuOverlay(
    showMenu          : Boolean,
    onDismiss         : () -> Unit,
    onCategorySelected: (EntryCategory) -> Unit,
) {
    // Scrim
    AnimatedVisibility(
        visible = showMenu,
        enter   = fadeIn(tween(220)),
        exit    = fadeOut(tween(180)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable { onDismiss() }
        )
    }

    // Staggered category items
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
                visible = showMenu,
                enter   = fadeIn(tween(200, delayMillis = enterDelay)) +
                          slideInVertically(tween(240, delayMillis = enterDelay)) { it / 2 },
                exit    = fadeOut(tween(130, delayMillis = exitDelay)) +
                          slideOutVertically(tween(130, delayMillis = exitDelay)) { it / 3 },
            ) {
                CategoryFabItem(
                    category = cat,
                    onClick  = { onCategorySelected(cat) },
                )
            }
        }
    }
}
