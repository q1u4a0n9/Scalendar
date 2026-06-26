package com.scalendar.ui.screen.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scalendar.R

// ── Step definition ───────────────────────────────────────────────────
private enum class OnboardingStep(val displayNumber: String) {
    NAME    ("1/7"),
    ROLE    ("2/7"),
    SOURCE  ("3/7"),
    CALENDAR("4/7"),
    JOURNAL ("5/7"),
    NOTES   ("6/7"),
}

// emoji paired with a @StringRes label — resolved inside composables via stringResource()
private val ROLE_OPTIONS: List<Pair<String, Int>> = listOf(
    "🎓" to R.string.onboarding_role_student,
    "💼" to R.string.onboarding_role_working,
    "✨" to R.string.onboarding_role_other,
)

private val SOURCE_OPTIONS: List<Pair<String, Int>> = listOf(
    "👥" to R.string.onboarding_source_friends,
    "📱" to R.string.onboarding_source_social,
    "🔍" to R.string.onboarding_source_search,
    "💬" to R.string.onboarding_source_other,
)

// ── Root composable ───────────────────────────────────────────────────
@Composable
fun OnboardingScreen(
    /** Called when the user finishes all steps. [name] may be blank if skipped. */
    onComplete: (name: String) -> Unit,
) {
    var step     by remember { mutableStateOf(OnboardingStep.NAME) }
    var userName by remember { mutableStateOf("") }
    var role     by remember { mutableStateOf<String?>(null) }
    var source   by remember { mutableStateOf<String?>(null) }

    val steps = OnboardingStep.entries
    val stepIndex = steps.indexOf(step)

    // Back navigation: go to previous step or do nothing on first step
    BackHandler(enabled = stepIndex > 0) {
        step = steps[stepIndex - 1]
    }

    fun goNext() {
        if (step == OnboardingStep.NOTES) {
            onComplete(userName.trim())
        } else {
            step = steps[stepIndex + 1]
        }
    }

    // Resolve all strings up front so they're available inside AnimatedContent.
    // calTitle re-resolves on recomposition whenever userName changes.
    val nameFallback    = stringResource(R.string.onboarding_name_fallback)
    val calTitle        = stringResource(R.string.onboarding_cal_title, userName.ifBlank { nameFallback })
    val calSubtitle     = stringResource(R.string.onboarding_cal_subtitle)
    val journalTitle    = stringResource(R.string.onboarding_journal_title)
    val journalSubtitle = stringResource(R.string.onboarding_journal_subtitle)
    val notesTitle      = stringResource(R.string.onboarding_notes_title)
    val notesSubtitle   = stringResource(R.string.onboarding_notes_subtitle)
    val btnContinue     = stringResource(R.string.action_next)
    val btnStart        = stringResource(R.string.action_start)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ── Header ────────────────────────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (stepIndex > 0) {
                IconButton(onClick = { step = steps[stepIndex - 1] }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text     = step.displayNumber,
                    style    = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        color      = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.size(48.dp))
        }

        // ── Progress bar ──────────────────────────────────────────────
        LinearProgressIndicator(
            progress   = { (stepIndex + 1f) / steps.size },
            modifier   = Modifier.fillMaxWidth(),
            color      = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        // ── Step content ──────────────────────────────────────────────
        AnimatedContent(
            targetState    = step,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith
                slideOutHorizontally { -it } + fadeOut()
            },
            label          = "onboardingStep",
            modifier       = Modifier.weight(1f),
        ) { currentStep ->
            when (currentStep) {
                OnboardingStep.NAME     -> NameStep(
                    name         = userName,
                    onNameChange = { userName = it },
                    onNext       = ::goNext,
                )
                OnboardingStep.ROLE     -> RoleStep(
                    selected     = role,
                    onSelectRole = { role = it },
                    onNext       = ::goNext,
                )
                OnboardingStep.SOURCE   -> SourceStep(
                    selected       = source,
                    onSelectSource = { source = it },
                    onNext         = ::goNext,
                )
                OnboardingStep.CALENDAR -> FeatureStep(
                    icon        = Icons.Filled.CalendarMonth,
                    iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint    = MaterialTheme.colorScheme.primary,
                    title       = calTitle,
                    subtitle    = calSubtitle,
                    buttonLabel = btnContinue,
                    isLast      = false,
                    dotIndex    = 0,
                    onNext      = ::goNext,
                )
                OnboardingStep.JOURNAL  -> FeatureStep(
                    icon        = Icons.AutoMirrored.Outlined.MenuBook,
                    iconBgColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint    = MaterialTheme.colorScheme.tertiary,
                    title       = journalTitle,
                    subtitle    = journalSubtitle,
                    buttonLabel = btnContinue,
                    isLast      = false,
                    dotIndex    = 1,
                    onNext      = ::goNext,
                )
                OnboardingStep.NOTES    -> FeatureStep(
                    icon        = Icons.Filled.EditNote,
                    iconBgColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint    = MaterialTheme.colorScheme.secondary,
                    title       = notesTitle,
                    subtitle    = notesSubtitle,
                    buttonLabel = btnStart,
                    isLast      = true,
                    dotIndex    = 2,
                    onNext      = ::goNext,
                )
            }
        }
    }
}

// ── Step 1: Name entry ────────────────────────────────────────────────
@Composable
private fun NameStep(
    name        : String,
    onNameChange: (String) -> Unit,
    onNext      : () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.25f))

        Text(
            text      = stringResource(R.string.onboarding_name_title),
            style     = MaterialTheme.typography.displaySmall.copy(
                fontWeight    = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp,
            ),
            color     = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = stringResource(R.string.onboarding_name_subtitle),
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(0.3f))

        OutlinedTextField(
            value         = name,
            onValueChange = onNameChange,
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            placeholder   = { Text(stringResource(R.string.onboarding_name_hint), textAlign = TextAlign.Center) },
            textStyle     = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
            shape         = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboard?.hide()
                if (name.isNotBlank()) onNext()
            }),
        )

        Spacer(Modifier.weight(0.3f))

        Button(
            onClick  = { keyboard?.hide(); onNext() },
            enabled  = name.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape    = RoundedCornerShape(50),
        ) {
            Text(stringResource(R.string.action_next), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── Step 2: Role / occupation ─────────────────────────────────────────
@Composable
private fun RoleStep(
    selected    : String?,
    onSelectRole: (String) -> Unit,
    onNext      : () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            text  = stringResource(R.string.onboarding_role_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text  = stringResource(R.string.onboarding_role_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        ROLE_OPTIONS.forEach { (emoji, labelResId) ->
            val label      = stringResource(labelResId)
            val isSelected = selected == label
            SelectionCard(
                emoji      = emoji,
                label      = label,
                isSelected = isSelected,
                onClick    = { onSelectRole(label) },
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick  = onNext,
            enabled  = selected != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape    = RoundedCornerShape(50),
        ) {
            Text(stringResource(R.string.action_next), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── Step 3: Source discovery ──────────────────────────────────────────
@Composable
private fun SourceStep(
    selected      : String?,
    onSelectSource: (String) -> Unit,
    onNext        : () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            text  = stringResource(R.string.onboarding_source_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text  = stringResource(R.string.onboarding_source_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        SOURCE_OPTIONS.forEach { (emoji, labelResId) ->
            val label      = stringResource(labelResId)
            val isSelected = selected == label
            SelectionCard(
                emoji      = emoji,
                label      = label,
                isSelected = isSelected,
                onClick    = { onSelectSource(label) },
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick  = onNext,
            enabled  = selected != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape    = RoundedCornerShape(50),
        ) {
            Text(stringResource(R.string.action_next), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── Shared selection card (used by RoleStep & SourceStep) ─────────────
@Composable
private fun SelectionCard(
    emoji      : String,
    label      : String,
    isSelected : Boolean,
    onClick    : () -> Unit,
) {
    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                   else MaterialTheme.colorScheme.surfaceContainerLow,
        border   = if (isSelected)
                       androidx.compose.foundation.BorderStroke(
                           1.5.dp, MaterialTheme.colorScheme.primary,
                       )
                   else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier         = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text     = label,
                style    = MaterialTheme.typography.titleMedium,
                color    = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (isSelected) {
                Box(
                    modifier         = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onPrimary,
                        modifier           = Modifier.size(14.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            }
        }
    }
}

// ── Steps 4–6: Feature introduction ──────────────────────────────────
@Composable
private fun FeatureStep(
    icon        : ImageVector,
    iconBgColor : Color,
    iconTint    : Color,
    title       : String,
    subtitle    : String,
    buttonLabel : String,
    isLast      : Boolean,   // true → show "Bắt đầu" without forward arrow
    dotIndex    : Int,       // 0 = first feature dot active, 1 = second, 2 = third
    onNext      : () -> Unit,
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.3f))

        Box(
            modifier         = Modifier
                .size(128.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = iconTint,
                modifier           = Modifier.size(64.dp),
            )
        }

        Spacer(Modifier.height(36.dp))

        Text(
            text      = title,
            style     = MaterialTheme.typography.displaySmall.copy(
                fontWeight    = FontWeight.SemiBold,
                fontSize      = 28.sp,
                lineHeight    = 36.sp,
                letterSpacing = (-0.3).sp,
            ),
            color     = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text      = subtitle,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier.widthIn(max = 280.dp),
        )

        Spacer(Modifier.weight(0.4f))

        // Dot indicators (3 dots for 3 feature steps)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier              = Modifier.padding(bottom = 20.dp),
        ) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(if (i == dotIndex) 24.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == dotIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        }

        Button(
            onClick  = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape    = RoundedCornerShape(50),
        ) {
            Text(buttonLabel, style = MaterialTheme.typography.titleMedium)
            if (!isLast) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
