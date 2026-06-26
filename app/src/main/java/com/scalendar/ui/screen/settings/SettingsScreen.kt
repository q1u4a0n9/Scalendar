package com.scalendar.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.scalendar.R
import com.scalendar.data.database.entity.UserCalendarEntity
import com.scalendar.data.model.EntryCategory
import com.scalendar.data.model.localizedName
import com.scalendar.ui.screen.auth.AccountState
import com.scalendar.ui.screen.auth.AuthViewModel

// ── In-screen navigation ──────────────────────────────────────────────
private sealed interface SettingsNav {
    object Main    : SettingsNav
    object Account : SettingsNav
    object General : SettingsNav
    object About   : SettingsNav
    data class CalEdit(
        val id                 : String,
        val name               : String,
        val colorHex           : String,
        val isSpecial          : Boolean,
        val defaultNotifMinutes: Int?    = null,
    ) : SettingsNav
}

// ── Built-in & special calendar definitions ───────────────────────────
private val BUILT_IN = listOf(
    Triple("TASK",  "Việc cần làm",   "F4511E"),
    Triple("CLASS", "Lịch học",       "9E9E9E"),
    Triple("SPORT", "Thể thao",       "66BB6A"),
    Triple("EXAM",  "Bài kiểm tra",   "FFA000"),
    Triple("EVENT", "Lịch / Sự kiện", "1E88E5"),
)

private val SPECIAL = listOf(
    Triple("BIRTHDAY", "Sinh nhật", "EC4899"),
    Triple("HOLIDAY",  "Ngày lễ",   "26A69A"),
)

// ── Color palette for calendar color picker ───────────────────────────
// Pairs of (hex, stringResId) — names are looked up via stringResource in composables
private val CAL_COLORS: List<Pair<String, Int>> = listOf(
    "F4511E" to R.string.color_red_orange,
    "FF7043" to R.string.color_orange,
    "FFA000" to R.string.color_yellow,
    "66BB6A" to R.string.color_light_green,
    "43A047" to R.string.color_green,
    "26A69A" to R.string.color_teal,
    "1E88E5" to R.string.color_blue,
    "4A90D9" to R.string.color_sky_blue,
    "9C27B0" to R.string.color_purple,
    "E53935" to R.string.color_red,
    "EC4899" to R.string.color_pink,
    "795548" to R.string.color_brown,
    "9E9E9E" to R.string.color_gray,
)


private fun hexToColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor("#$hex"))
} catch (_: Exception) { Color(0xFF9E9E9E) }

@Composable
private fun colorName(hex: String): String =
    stringResource(CAL_COLORS.find { it.first.equals(hex, ignoreCase = true) }?.second ?: R.string.settings_color_custom)

@Composable
private fun formatReminderLabel(minutes: Int?): String = when (minutes) {
    null -> stringResource(R.string.reminder_no_alert)
    0    -> stringResource(R.string.reminder_at_event)
    5    -> stringResource(R.string.reminder_5min)
    10   -> stringResource(R.string.reminder_10min)
    15   -> stringResource(R.string.reminder_15min)
    30   -> stringResource(R.string.reminder_30min)
    60   -> stringResource(R.string.reminder_1h)
    120  -> stringResource(R.string.reminder_2h)
    1440 -> stringResource(R.string.reminder_1day)
    else -> stringResource(R.string.reminder_custom_min, minutes)
}

// ═══════════════════════════════════════════════════════════════════════
// Root composable — entry point from AppNavigation
// ═══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    authVm   : AuthViewModel,
    onSignOut: () -> Unit = {},
) {
    val uiState      by viewModel.uiState.collectAsState()
    val accountState by authVm.accountState.collectAsState()
    var nav          by remember { mutableStateOf<SettingsNav>(SettingsNav.Main) }
    var showAddCal   by remember { mutableStateOf(false) }

    BackHandler(enabled = nav != SettingsNav.Main) { nav = SettingsNav.Main }

    when (val cur = nav) {
        SettingsNav.Main    -> MainContent(
            colorOverrides = uiState.colorOverrides,
            defaultNotifs  = uiState.defaultNotifs,
            userCalendars  = uiState.userCalendars,
            accountState   = accountState,
            onNavigate     = { nav = it },
            onAddCalendar  = { showAddCal = true },
            onSignOut      = onSignOut,
        )
        SettingsNav.Account -> AccountScreen(
            accountState    = accountState,
            onUpdateName    = authVm::updateDisplayName,
            onDeleteAccount = authVm::deleteAccount,
            onBack          = { nav = SettingsNav.Main },
        )
        SettingsNav.General -> GeneralScreen(
            homeView      = uiState.homeView,
            theme         = uiState.theme,
            lang          = uiState.lang,
            onSetHomeView = viewModel::setHomeView,
            onSetTheme    = viewModel::setTheme,
            onSetLang     = viewModel::setLang,
            onBack        = { nav = SettingsNav.Main },
        )
        SettingsNav.About   -> AboutScreen(onBack = { nav = SettingsNav.Main })
        is SettingsNav.CalEdit -> {
            val calData = cur
            CalendarEditScreen(
                data             = calData,
                holidayVnMode    = uiState.holidayVnMode,
                onSetHolidayMode = viewModel::setHolidayVnMode,
                onBack           = { nav = SettingsNav.Main },
                onDelete         = {
                    viewModel.deleteUserCalendar(calData.id)
                    nav = SettingsNav.Main
                },
                onSave           = { id, hex, notifMinutes ->
                    if (id.startsWith("CUSTOM_")) {
                        val name = uiState.userCalendars.find { it.id == id }?.name ?: calData.name
                        viewModel.addUserCalendar(id, name, hex)
                    } else {
                        viewModel.setColorOverride(id, hex)
                        runCatching { EntryCategory.valueOf(id) }.getOrNull()
                            ?.let { cat -> viewModel.setDefaultNotif(cat, notifMinutes) }
                    }
                },
            )
        }
    }

    if (showAddCal) {
        AddCalendarSheet(
            onDismiss = { showAddCal = false },
            onSave    = { name, hex ->
                viewModel.addUserCalendar(
                    id       = "CUSTOM_${System.currentTimeMillis()}",
                    name     = name,
                    colorHex = hex,
                )
                showAddCal = false
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 1. Main settings list
// ═══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(
    colorOverrides: Map<String, String>,
    defaultNotifs : Map<EntryCategory, Int?>,
    userCalendars : List<UserCalendarEntity>,
    accountState  : AccountState,
    onNavigate    : (SettingsNav) -> Unit,
    onAddCalendar : () -> Unit,
    onSignOut     : () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title  = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding      = PaddingValues(vertical = 8.dp),
        ) {
            // ── Chung ──────────────────────────────────────────────
            item {
                SettingsGroup {
                    SettingsRow(
                        icon    = Icons.Outlined.Settings,
                        iconBg  = Color(0xFF8E8E93),
                        title   = stringResource(R.string.settings_general),
                        onClick = { onNavigate(SettingsNav.General) },
                    )
                }
            }

            // ── User calendars ─────────────────────────────────────
            item { SectionLabel(accountState.email.uppercase()) }
            item {
                SettingsGroup {
                    // Account row
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(SettingsNav.Account) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier         = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                accountState.initials,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color      = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 12.sp,
                                ),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                accountState.displayName.ifBlank { accountState.email },
                                style      = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color      = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(accountState.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null,
                            tint     = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    // Built-in calendars
                    BUILT_IN.forEach { (id, name, defaultHex) ->
                        val hex = colorOverrides[id] ?: defaultHex
                        val displayName = runCatching { EntryCategory.valueOf(id) }.getOrNull()?.localizedName() ?: name
                        SettingsDivider()
                        CalendarRow(displayName, hex) {
                            onNavigate(SettingsNav.CalEdit(
                                id                  = id,
                                name                = displayName,
                                colorHex            = hex,
                                isSpecial           = false,
                                defaultNotifMinutes = defaultNotifs[
                                    runCatching { EntryCategory.valueOf(id) }.getOrNull()
                                ],
                            ))
                        }
                    }

                    // User-created calendars
                    userCalendars.forEach { cal ->
                        SettingsDivider()
                        CalendarRow(cal.name, cal.colorHex) {
                            onNavigate(SettingsNav.CalEdit(
                                id       = cal.id,
                                name     = cal.name,
                                colorHex = cal.colorHex,
                                isSpecial = false,
                            ))
                        }
                    }

                    // Add calendar
                    SettingsDivider()
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onAddCalendar)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier         = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34C759)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Add, null,
                                tint     = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.settings_add_calendar),
                            style    = MaterialTheme.typography.bodyLarge,
                            color    = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Default.ChevronRight, null,
                            tint     = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // ── Special calendars ──────────────────────────────────
            item { SectionLabel(stringResource(R.string.settings_special_label)) }
            item {
                SettingsGroup {
                    SPECIAL.forEachIndexed { i, (id, name, defaultHex) ->
                        val hex = colorOverrides[id] ?: defaultHex
                        val displayName = if (id == "HOLIDAY") stringResource(R.string.cal_holiday)
                                          else runCatching { EntryCategory.valueOf(id) }.getOrNull()?.localizedName() ?: name
                        if (i > 0) SettingsDivider()
                        CalendarRow(displayName, hex) {
                            onNavigate(SettingsNav.CalEdit(
                                id                  = id,
                                name                = displayName,
                                colorHex            = hex,
                                isSpecial           = true,
                                defaultNotifMinutes = defaultNotifs[
                                    runCatching { EntryCategory.valueOf(id) }.getOrNull()
                                ],
                            ))
                        }
                    }
                }
            }

            // ── About ──────────────────────────────────────────────
            item {
                SettingsGroup {
                    SettingsRow(
                        icon    = Icons.Outlined.Info,
                        iconBg  = Color(0xFF007AFF),
                        title   = stringResource(R.string.settings_about),
                        onClick = { onNavigate(SettingsNav.About) },
                    )
                }
            }

            // ── Feedback ──────────────────────────────────────────
            item {
                SettingsGroup {
                    SettingsRow(
                        icon    = Icons.Outlined.Feedback,
                        iconBg  = Color(0xFF5AC8FA),
                        title   = stringResource(R.string.settings_feedback),
                        onClick = { /* TODO */ },
                    )
                }
            }

            // ── Logout ─────────────────────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                SettingsGroup {
                    TextButton(
                        onClick  = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            stringResource(R.string.action_sign_out),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 2. Account screen
// ═══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountScreen(
    accountState   : AccountState,
    onUpdateName   : (String) -> Unit,
    onDeleteAccount: () -> Unit,
    onBack         : () -> Unit,
) {
    // Initialise from real Firebase name; re-init if it changes after a save
    var fullName          by remember(accountState.displayName) { mutableStateOf(accountState.displayName) }
    var require2fa        by remember { mutableStateOf(false) }
    var showDeleteDialog  by remember { mutableStateOf(false) }

    val nameChanged = fullName.isNotBlank() && fullName != accountState.displayName

    // ── Delete-account confirmation dialog ────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title  = { Text(stringResource(R.string.account_delete_title)) },
            text   = { Text(stringResource(R.string.account_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteAccount()
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(stringResource(R.string.settings_account), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))

            // ── Avatar ────────────────────────────────────────────────
            Box(
                modifier         = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    accountState.initials,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color      = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 24.sp,
                    ),
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(20.dp))

            // ── Full Name ─────────────────────────────────────────────
            AccountFieldGroup {
                Text(
                    stringResource(R.string.account_full_name),
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                OutlinedTextField(
                    value         = fullName,
                    onValueChange = { fullName = it },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    shape         = RoundedCornerShape(12.dp),
                    trailingIcon  = if (nameChanged) ({
                        IconButton(onClick = { onUpdateName(fullName) }) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.account_save_name),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }) else null,
                )
                if (nameChanged) {
                    Text(
                        stringResource(R.string.account_save_hint),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // ── Email ─────────────────────────────────────────────────
            AccountFieldGroup {
                Text(
                    stringResource(R.string.auth_email),
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Surface(
                    shape    = RoundedCornerShape(12.dp),
                    color    = MaterialTheme.colorScheme.surfaceContainerLow,
                    border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        accountState.email,
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // ── Add Password (Google users only) ──────────────────────
            if (accountState.isGoogleUser) {
                AccountFieldGroup {
                    Text(
                        stringResource(R.string.auth_password),
                        style    = MaterialTheme.typography.labelMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    OutlinedButton(
                        onClick  = { /* TODO: link email/password credential */ },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.account_add_password), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── 2FA ───────────────────────────────────────────────────
            AccountFieldGroup {
                Text(
                    stringResource(R.string.account_2fa_title),
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Surface(
                    shape    = RoundedCornerShape(12.dp),
                    color    = MaterialTheme.colorScheme.surfaceContainerLow,
                    border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.account_require_2fa),
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = require2fa, onCheckedChange = { require2fa = it })
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            // ── Delete Account ────────────────────────────────────────
            TextButton(
                onClick  = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.account_delete),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AccountFieldGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), content = content)
}

// ═══════════════════════════════════════════════════════════════════════
// 3. General settings screen
// ═══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralScreen(
    homeView      : String,
    theme         : String,
    lang          : String,
    onSetHomeView : (String) -> Unit,
    onSetTheme    : (String) -> Unit,
    onSetLang     : (String) -> Unit,
    onBack        : () -> Unit,
) {
    var subScreen by remember { mutableStateOf("") }   // "" | "HOME_VIEW" | "THEME" | "LANG"

    BackHandler(enabled = subScreen.isNotEmpty()) { subScreen = "" }

    val homeViewOptions = listOf(
        "TODAY" to stringResource(R.string.settings_home_today_short),
        "WEEK"  to stringResource(R.string.settings_home_week_short),
        "MONTH" to stringResource(R.string.settings_home_month_short),
    )
    val themeOptions = listOf(
        "SYSTEM" to stringResource(R.string.settings_theme_system_short),
        "LIGHT"  to stringResource(R.string.settings_theme_light_short),
        "DARK"   to stringResource(R.string.settings_theme_dark_short),
    )
    val langOptions = listOf("VI" to "Tiếng Việt", "EN" to "English")

    val homeViewLabel = homeViewOptions.find { it.first == homeView }?.second ?: stringResource(R.string.settings_home_today_short)
    val themeLabel    = themeOptions.find    { it.first == theme }?.second    ?: stringResource(R.string.settings_theme_system_short)
    val langLabel     = langOptions.find     { it.first == lang }?.second     ?: "Tiếng Việt"

    when (subScreen) {
        "HOME_VIEW" -> PickerSubScreen(
            title    = stringResource(R.string.settings_home_view_label),
            options  = homeViewOptions,
            selected = homeView,
            onSelect = { onSetHomeView(it); subScreen = "" },
            onBack   = { subScreen = "" },
        )
        "THEME" -> PickerSubScreen(
            title    = stringResource(R.string.settings_theme_label),
            options  = themeOptions,
            selected = theme,
            onSelect = { onSetTheme(it); subScreen = "" },
            onBack   = { subScreen = "" },
        )
        "LANG" -> PickerSubScreen(
            title    = stringResource(R.string.settings_lang_label),
            options  = langOptions,
            selected = lang,
            onSelect = { onSetLang(it); subScreen = "" },
            onBack   = { subScreen = "" },
        )
        else -> Scaffold(
            topBar = {
                TopAppBar(
                    title          = { Text(stringResource(R.string.settings_general_title), style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            LazyColumn(
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding      = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    SettingsGroup {
                        NavValueRow(stringResource(R.string.settings_home_view_label), homeViewLabel) { subScreen = "HOME_VIEW" }
                        SettingsDivider()
                        NavValueRow(stringResource(R.string.settings_theme_label), themeLabel)         { subScreen = "THEME"     }
                        SettingsDivider()
                        NavValueRow(stringResource(R.string.settings_lang_label), langLabel)           { subScreen = "LANG"      }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSubScreen(
    title   : String,
    options : List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onBack  : () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding      = PaddingValues(vertical = 8.dp),
        ) {
            item {
                SettingsGroup {
                    options.forEachIndexed { i, (key, label) ->
                        if (i > 0) SettingsDivider()
                        CheckRow(label = label, selected = selected == key) { onSelect(key) }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 4. Calendar edit screen
// ═══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarEditScreen(
    data            : SettingsNav.CalEdit,
    holidayVnMode   : String  = "ALL",
    onSetHolidayMode: (String) -> Unit = {},
    onBack          : () -> Unit,
    onDelete        : () -> Unit,
    onSave          : (categoryId: String, hex: String, notifMinutes: Int?) -> Unit,
) {
    var selectedHex     by remember { mutableStateOf(data.colorHex) }
    var notifMinutes    by remember { mutableStateOf(data.defaultNotifMinutes) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showNotifMenu   by remember { mutableStateOf(false) }
    // Holiday sub-navigation: "" | "NATIONAL_LIST" | "VN_MODE"
    var holidaySubScreen by remember { mutableStateOf("") }

    val reminderPresets = listOf(
        null to stringResource(R.string.reminder_no_alert),
        0    to stringResource(R.string.reminder_at_event),
        5    to stringResource(R.string.reminder_5min),
        10   to stringResource(R.string.reminder_10min),
        15   to stringResource(R.string.reminder_15min),
        30   to stringResource(R.string.reminder_30min),
        60   to stringResource(R.string.reminder_1h),
        120  to stringResource(R.string.reminder_2h),
        1440 to stringResource(R.string.reminder_1day),
    )

    BackHandler(enabled = holidaySubScreen.isNotEmpty()) { holidaySubScreen = "" }

    // Determine whether to show the notification section (HOLIDAY has no EntryCategory)
    val entryCategory = remember(data.id) {
        runCatching { EntryCategory.valueOf(data.id) }.getOrNull()
    }

    // ── Holiday sub-screens ───────────────────────────────────────────
    if (holidaySubScreen == "NATIONAL_LIST") {
        // Country list (only Vietnam for now)
        Scaffold(
            topBar = {
                TopAppBar(
                    title          = { Text(stringResource(R.string.settings_national_holidays), style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = { IconButton(onClick = { holidaySubScreen = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                    colors         = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { SectionLabel(stringResource(R.string.settings_not_showing)) }
                item {
                    SettingsGroup {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .clickable { holidaySubScreen = "VN_MODE" }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("🇻🇳", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text     = stringResource(R.string.settings_country_vietnam),
                                style    = MaterialTheme.typography.bodyLarge,
                                color    = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Default.ChevronRight, null,
                                tint     = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
        return
    }

    if (holidaySubScreen == "VN_MODE") {
        val vnOptions = listOf(
            "ALL"      to Pair(stringResource(R.string.settings_vn_all_holidays),    stringResource(R.string.settings_vn_all_desc)),
            "NATIONAL" to Pair(stringResource(R.string.settings_vn_national_only),  null),
            "NONE"     to Pair(stringResource(R.string.settings_vn_none),            null),
        )
        Scaffold(
            topBar = {
                TopAppBar(
                    title          = { Text(stringResource(R.string.settings_country_vietnam), style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = { IconButton(onClick = { holidaySubScreen = "NATIONAL_LIST" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                    colors         = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    SettingsGroup {
                        vnOptions.forEachIndexed { i, (key, labels) ->
                            if (i > 0) SettingsDivider()
                            Row(
                                modifier          = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSetHolidayMode(key); holidaySubScreen = "" }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(labels.first, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                    if (labels.second != null) {
                                        Text(labels.second!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (holidayVnMode == key) {
                                    Icon(Icons.Default.Check, null,
                                        tint     = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    if (showColorPicker) {
        ModalBottomSheet(
            onDismissRequest = { showColorPicker = false },
            containerColor   = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    stringResource(R.string.settings_pick_color),
                    style    = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                CAL_COLORS.forEach { (hex, nameResId) ->
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedHex = hex
                                showColorPicker = false
                                onSave(data.id, hex, notifMinutes)
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(hexToColor(hex)),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(nameResId),
                            style    = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (selectedHex.equals(hex, ignoreCase = true)) {
                            Icon(Icons.Default.Check, null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(stringResource(R.string.settings_edit_calendar), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding      = PaddingValues(vertical = 8.dp),
        ) {
            // Calendar name
            item {
                Text(
                    data.name,
                    style      = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.padding(vertical = 4.dp),
                )
            }

            // Color row
            item {
                SettingsGroup {
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clickable { showColorPicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(hexToColor(selectedHex)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(colorName(selectedHex),
                            style    = MaterialTheme.typography.bodyLarge,
                            color    = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Default.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }

            // Holiday options (only for HOLIDAY calendar)
            if (data.id == "HOLIDAY") {
                item {
                    SettingsGroup {
                        SettingsRow(
                            icon    = Icons.Outlined.Flag,
                            iconBg  = Color(0xFF26A69A),
                            title   = stringResource(R.string.settings_add_holidays),
                            onClick = { holidaySubScreen = "NATIONAL_LIST" },
                        )
                    }
                }
            }

            // Default notification (hidden for HOLIDAY which has no EntryCategory)
            if (entryCategory != null) {
                item { SectionLabel(stringResource(R.string.settings_default_notif_label)) }
                item {
                    SettingsGroup {
                        Box {
                            Row(
                                modifier          = Modifier
                                    .fillMaxWidth()
                                    .clickable { showNotifMenu = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Notifications,
                                    contentDescription = null,
                                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    formatReminderLabel(notifMinutes),
                                    style    = MaterialTheme.typography.bodyLarge,
                                    color    = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.Default.ArrowDropDown, null,
                                    tint = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                            DropdownMenu(
                                expanded         = showNotifMenu,
                                onDismissRequest = { showNotifMenu = false },
                            ) {
                                reminderPresets.forEach { (minutes, label) ->
                                    DropdownMenuItem(
                                        text    = { Text(label) },
                                        onClick = {
                                            notifMinutes = minutes
                                            showNotifMenu = false
                                            onSave(data.id, selectedHex, minutes)
                                        },
                                        trailingIcon = if (notifMinutes == minutes) ({
                                            Icon(
                                                Icons.Default.Check, null,
                                                tint     = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }) else null,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Delete (custom calendars only — id starts with "CUSTOM_")
            if (data.id.startsWith("CUSTOM_")) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SettingsGroup {
                        TextButton(
                            onClick  = onDelete,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Text(
                                stringResource(R.string.settings_delete_calendar),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 5. Add calendar sheet
// ═══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCalendarSheet(
    onDismiss: () -> Unit,
    onSave   : (name: String, colorHex: String) -> Unit,
) {
    var calName  by remember { mutableStateOf("") }
    var calColor by remember { mutableStateOf("1E88E5") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.settings_add_new_calendar),
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp),
            )

            OutlinedTextField(
                value         = calName,
                onValueChange = { calName = it },
                label         = { Text(stringResource(R.string.settings_calendar_name)) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp),
            )

            // Color chips
            Text(stringResource(R.string.field_color),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CAL_COLORS.forEach { (hex, _) ->
                    val isSelected = calColor == hex
                    Box(
                        modifier         = Modifier
                            .size(if (isSelected) 36.dp else 30.dp)
                            .clip(CircleShape)
                            .background(hexToColor(hex))
                            .border(
                                width = if (isSelected) 2.5.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                        else Color.Transparent,
                                shape = CircleShape,
                            )
                            .clickable { calColor = hex },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, null,
                                tint     = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Button(
                onClick  = { if (calName.isNotBlank()) onSave(calName.trim(), calColor) },
                enabled  = calName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.action_save), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// 6. About screen
// ═══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Box(
                modifier         = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.CalendarMonth, null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Scalendar",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.settings_version),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.settings_about_description),
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Reusable components
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape          = RoundedCornerShape(12.dp),
        color          = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier       = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(start = 16.dp),
        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        thickness = 0.5.dp,
    )
}

@Composable
private fun SettingsRow(
    icon   : ImageVector,
    iconBg : Color,
    title  : String,
    onClick: () -> Unit,
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier         = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title,
            style    = MaterialTheme.typography.bodyLarge,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Default.ChevronRight, null,
            tint     = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CalendarRow(name: String, colorHex: String, onClick: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(hexToColor(colorHex)),
        )
        Spacer(Modifier.width(12.dp))
        Text(name,
            style    = MaterialTheme.typography.bodyLarge,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Default.ChevronRight, null,
            tint     = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Row hiển thị label + giá trị hiện tại + chevron — dùng trong GeneralScreen chính. */
@Composable
private fun NavValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyLarge,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text  = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Default.ChevronRight, null,
            tint     = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Row chọn một option trong PickerSubScreen — hiển thị checkmark khi được chọn. */
@Composable
private fun CheckRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyLarge,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Default.Check, null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
