package com.scalendar.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.scalendar.data.database.entity.EntryEntity
import com.scalendar.data.model.EntryCategory
import com.scalendar.ui.screen.auth.AuthScreen
import com.scalendar.ui.screen.auth.AuthViewModel
import com.scalendar.ui.screen.dayview.DayViewScreen
import com.scalendar.ui.screen.dayview.DayViewViewModel
import com.scalendar.ui.screen.dayview.EntryDetailSheet
import com.scalendar.ui.screen.journal.JournalScreen
import com.scalendar.ui.screen.monthview.MonthViewScreen
import com.scalendar.data.database.entity.NoteEntity
import com.scalendar.ui.screen.notes.NoteSearchOverlay
import com.scalendar.ui.screen.notes.NotesScreen
import com.scalendar.ui.screen.onboarding.OnboardingScreen
import com.scalendar.ui.screen.search.NoteSearchViewModel
import com.scalendar.ui.screen.search.SearchViewModel
import com.scalendar.ui.screen.settings.SettingsScreen
import com.scalendar.ui.screen.settings.SettingsViewModel
import com.scalendar.ui.screen.splash.SplashScreen
import com.scalendar.ui.screen.weekview.WeekViewScreen
import com.scalendar.ui.shared.SharedCalendarViewModel
import com.scalendar.ui.theme.LocalColorOverrides
import com.scalendar.ui.theme.displayBgColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.scalendar.R
import com.scalendar.data.model.localizedName
import java.time.format.DateTimeFormatter

// ── App-level screen states ───────────────────────────────────────────
private enum class AppScreen { SPLASH, ONBOARDING, AUTH, MAIN }

// ── Per-category indicator colours (drawer checkboxes) ───────────────
private fun defaultIndicatorColor(cat: EntryCategory): Color = when (cat) {
    EntryCategory.TASK     -> Color(0xFFF4511E)  // deep orange-red
    EntryCategory.CLASS    -> Color(0xFF9E9E9E)  // light gray
    EntryCategory.SPORT    -> Color(0xFF66BB6A)  // light green
    EntryCategory.EXAM     -> Color(0xFFFFA000)  // amber / dark yellow
    EntryCategory.BIRTHDAY -> Color(0xFFEC4899)  // pink
    EntryCategory.EVENT    -> Color(0xFF1E88E5)  // blue
}

private fun indicatorColor(cat: EntryCategory, overrides: Map<String, String>): Color {
    val hex = overrides[cat.name]
    return if (hex != null) {
        try { Color(android.graphics.Color.parseColor("#$hex")) }
        catch (_: Exception) { defaultIndicatorColor(cat) }
    } else {
        defaultIndicatorColor(cat)
    }
}

/** Resolves the HOLIDAY indicator color, respecting user override (key = "HOLIDAY"). */
private fun holidayColor(overrides: Map<String, String>): Color {
    val hex = overrides["HOLIDAY"]
    return if (hex != null) {
        try { Color(android.graphics.Color.parseColor("#$hex")) }
        catch (_: Exception) { Color(0xFF26A69A) }
    } else {
        Color(0xFF26A69A)   // default teal from SettingsScreen
    }
}

// ── Bottom nav item descriptor ────────────────────────────────────────
private data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

// ── Root composable ───────────────────────────────────────────────────
@Composable
fun AppNavigation() {
    val authVm: AuthViewModel         = hiltViewModel()
    val settingsVm: SettingsViewModel = hiltViewModel()

    val isLoggedIn          by authVm.isLoggedIn.collectAsState()
    val onboardingCompleted by settingsVm.onboardingCompleted.collectAsState()

    // ── App-level screen state machine ────────────────────────────────
    var appScreen by remember { mutableStateOf(AppScreen.SPLASH) }

    // React to login/logout while inside AUTH or MAIN
    LaunchedEffect(isLoggedIn, onboardingCompleted) {
        when (appScreen) {
            AppScreen.AUTH -> if (isLoggedIn) appScreen = AppScreen.MAIN
            AppScreen.MAIN -> if (!isLoggedIn) appScreen = AppScreen.AUTH
            else -> Unit
        }
    }

    when (appScreen) {
        AppScreen.SPLASH -> {
            SplashScreen {
                appScreen = when {
                    !onboardingCompleted -> AppScreen.ONBOARDING
                    !isLoggedIn          -> AppScreen.AUTH
                    else                 -> AppScreen.MAIN
                }
            }
        }
        AppScreen.ONBOARDING -> {
            OnboardingScreen { name ->
                if (name.isNotBlank()) settingsVm.setPendingName(name)
                settingsVm.setOnboardingCompleted()
                appScreen = AppScreen.AUTH
            }
        }
        AppScreen.AUTH -> {
            AuthScreen(viewModel = authVm)
        }
        AppScreen.MAIN -> {
            MainApp(authVm = authVm, settingsVm = settingsVm)
        }
    }
}

// ── Main app (drawer + bottom nav + navhost) ──────────────────────────
@Composable
private fun MainApp(
    authVm    : AuthViewModel,
    settingsVm: SettingsViewModel,
) {
    val navController   = rememberNavController()
    val sharedVm: SharedCalendarViewModel  = hiltViewModel()
    val searchVm: SearchViewModel           = hiltViewModel()
    val noteSearchVm: NoteSearchViewModel   = hiltViewModel()
    val actionsVm: DayViewViewModel         = hiltViewModel()
    val calendarView      by sharedVm.calendarView.collectAsState()
    val categoryFilters   by sharedVm.categoryFilters.collectAsState()
    val settingsUiState   by settingsVm.uiState.collectAsState()
    val resolvedHolidayColor = holidayColor(settingsUiState.colorOverrides)

    val locale = LocalConfiguration.current.locales[0]
    val navNotes    = stringResource(R.string.nav_notes)
    val navCalendar = stringResource(R.string.nav_calendar)
    val navJournal  = stringResource(R.string.nav_journal)
    val navSettings = stringResource(R.string.nav_settings)
    val bottomNavItems = remember(locale) {
        listOf(
            BottomNavItem(navNotes,    Icons.Filled.EditNote,              NavRoutes.Notes.route),
            BottomNavItem(navCalendar, Icons.Filled.CalendarMonth,         NavRoutes.DayView.route),
            BottomNavItem(navJournal,  Icons.AutoMirrored.Filled.MenuBook, NavRoutes.Journal.route),
            BottomNavItem(navSettings, Icons.Filled.Settings,              NavRoutes.Settings.route),
        )
    }

    // ── Calendar search overlay state ─────────────────────────────────
    val searchQuery    by searchVm.query.collectAsState()
    val searchResults  by searchVm.results.collectAsState()
    var isSearchActive by remember { mutableStateOf(false) }
    var detailEntry    by remember { mutableStateOf<EntryEntity?>(null) }

    // ── Notes search overlay state ────────────────────────────────────
    val noteSearchQuery   by noteSearchVm.query.collectAsState()
    val noteSearchResults by noteSearchVm.results.collectAsState()
    var isNoteSearchActive by remember { mutableStateOf(false) }
    var noteToOpen         by remember { mutableStateOf<NoteEntity?>(null) }

    val drawerState    = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // ── Initial screen based on Home View setting ─────────────────────
    // Fires once (survives config changes via rememberSaveable).
    // Waits for the real DataStore value via .first(), then navigates.
    var homeNavDone by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (homeNavDone) return@LaunchedEffect
        homeNavDone = true
        when (settingsVm.homeViewFlow.first()) {
            "WEEK"  -> {
                sharedVm.setCalendarView(CalendarView.WEEK)
                navController.navigate(NavRoutes.WeekView.route) { launchSingleTop = true }
            }
            "MONTH" -> {
                sharedVm.setCalendarView(CalendarView.MONTH)
                navController.navigate(NavRoutes.MonthView.route) { launchSingleTop = true }
            }
            // "TODAY" → DayView is already the startDestination, do nothing
        }
    }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // Which "tab" is selected?  Calendar family all map to the Calendar tab.
    val selectedTabRoute = when (currentRoute) {
        in NavRoutes.calendarRoutes -> NavRoutes.DayView.route
        NavRoutes.Journal.route     -> NavRoutes.Journal.route
        NavRoutes.Notes.route       -> NavRoutes.Notes.route
        NavRoutes.Settings.route    -> NavRoutes.Settings.route
        else                        -> NavRoutes.Notes.route
    }

    CompositionLocalProvider(LocalColorOverrides provides settingsUiState.colorOverrides) {
    Box(modifier = Modifier.fillMaxSize()) {
    ModalNavigationDrawer(
        drawerState     = drawerState,
        gesturesEnabled = currentRoute in NavRoutes.calendarRoutes,
        drawerContent   = {
            CalendarDrawerContent(
                currentView      = calendarView,
                categoryFilters  = categoryFilters,
                colorOverrides   = settingsUiState.colorOverrides,
                onToggleCategory = sharedVm::toggleCategoryFilter,
                onSelectDay      = {
                    sharedVm.setCalendarView(CalendarView.DAY)
                    navController.navigate(NavRoutes.DayView.route) { launchSingleTop = true }
                    coroutineScope.launch { drawerState.close() }
                },
                onSelectWeek     = {
                    sharedVm.setCalendarView(CalendarView.WEEK)
                    navController.navigate(NavRoutes.WeekView.route) { launchSingleTop = true }
                    coroutineScope.launch { drawerState.close() }
                },
                onSelectMonth    = {
                    sharedVm.setCalendarView(CalendarView.MONTH)
                    navController.navigate(NavRoutes.MonthView.route) { launchSingleTop = true }
                    coroutineScope.launch { drawerState.close() }
                },
            )
        }
    ) {
        Scaffold(
            // Each inner screen handles its own top insets via TopAppBar.windowInsets.
            // Without this, the outer Scaffold adds a status-bar-height top padding AND
            // the inner TopAppBar adds another one → content is pushed down twice.
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                ) {
                    bottomNavItems.forEach { item ->
                        val isSelected = selectedTabRoute == item.route
                        NavigationBarItem(
                            selected  = isSelected,
                            icon      = { Icon(item.icon, contentDescription = item.label) },
                            label     = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                            colors    = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                            onClick   = {
                                val target = resolveCalendarRoute(item.route, calendarView)
                                navController.navigate(target) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController    = navController,
                startDestination = NavRoutes.DayView.route,
                modifier         = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                composable(NavRoutes.DayView.route) {
                    DayViewScreen(
                        sharedVm      = sharedVm,
                        onOpenDrawer  = { coroutineScope.launch { drawerState.open() } },
                        onOpenSearch  = { isSearchActive = true },
                        holidayVnMode = settingsUiState.holidayVnMode,
                        holidayColor  = resolvedHolidayColor,
                    )
                }
                composable(NavRoutes.WeekView.route) {
                    WeekViewScreen(
                        sharedVm      = sharedVm,
                        onOpenDrawer  = { coroutineScope.launch { drawerState.open() } },
                        onOpenSearch  = { isSearchActive = true },
                        holidayVnMode = settingsUiState.holidayVnMode,
                        holidayColor  = resolvedHolidayColor,
                        onDayClick    = { date ->
                            sharedVm.selectDate(date)
                            sharedVm.setCalendarView(CalendarView.DAY)
                            navController.navigate(NavRoutes.DayView.route) { launchSingleTop = true }
                        }
                    )
                }
                composable(NavRoutes.MonthView.route) {
                    MonthViewScreen(
                        sharedVm      = sharedVm,
                        onOpenDrawer  = { coroutineScope.launch { drawerState.open() } },
                        onOpenSearch  = { isSearchActive = true },
                        holidayVnMode = settingsUiState.holidayVnMode,
                        holidayColor  = resolvedHolidayColor,
                        onDayClick    = { date ->
                            sharedVm.selectDate(date)
                            sharedVm.setCalendarView(CalendarView.DAY)
                            navController.navigate(NavRoutes.DayView.route) { launchSingleTop = true }
                        }
                    )
                }
                composable(NavRoutes.Journal.route) {
                    JournalScreen(
                        onDayClick = { date ->
                            sharedVm.selectDate(date)
                            sharedVm.setCalendarView(CalendarView.DAY)
                            navController.navigate(NavRoutes.DayView.route) { launchSingleTop = true }
                        },
                        holidayVnMode = settingsUiState.holidayVnMode,
                        holidayColor  = resolvedHolidayColor,
                    )
                }
                composable(NavRoutes.Notes.route) {
                    NotesScreen(
                        onOpenSearch = { isNoteSearchActive = true },
                        noteToOpen   = noteToOpen,
                        onNoteOpened = { noteToOpen = null },
                    )
                }
                composable(NavRoutes.Settings.route) {
                    SettingsScreen(
                        viewModel = settingsVm,
                        authVm    = authVm,
                        onSignOut = { authVm.signOut() },
                    )
                }
            }
        }
    } // end ModalNavigationDrawer

    // ── Calendar search overlay ───────────────────────────────────────
    if (isSearchActive) {
        SearchOverlay(
            query         = searchQuery,
            results       = searchResults,
            onQueryChange = searchVm::setQuery,
            onDismiss     = { isSearchActive = false; searchVm.clearQuery() },
            onEntryClick  = { detailEntry = it },
        )
    }

    // ── Notes search overlay (root level → correct IME insets) ────────
    if (isNoteSearchActive) {
        NoteSearchOverlay(
            query         = noteSearchQuery,
            results       = noteSearchResults,
            onQueryChange = noteSearchVm::setQuery,
            onDismiss     = { isNoteSearchActive = false; noteSearchVm.clearQuery() },
            onNoteClick   = { note ->
                isNoteSearchActive = false
                noteSearchVm.clearQuery()
                noteToOpen = note
                // Navigate to Notes tab so the editor can open there
                navController.navigate(NavRoutes.Notes.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState    = true
                }
            },
        )
    }

    // ── Entry detail sheet from search result ─────────────────────────
    if (detailEntry != null) {
        EntryDetailSheet(
            entry          = detailEntry!!,
            onEdit         = {
                val entry = detailEntry!!
                sharedVm.selectDate(entry.date)
                navController.navigate(NavRoutes.DayView.route) { launchSingleTop = true }
                detailEntry = null
                isSearchActive = false
                searchVm.clearQuery()
            },
            onDelete       = { actionsVm.deleteEntry(detailEntry!!); detailEntry = null },
            onDeleteSeries = { actionsVm.deleteEntrySeries(detailEntry!!); detailEntry = null },
            onDismiss      = { detailEntry = null },
        )
    }

    } // end Box
    } // end CompositionLocalProvider
}

// ── Helper: resolve which calendar route to land on ───────────────────
private fun resolveCalendarRoute(tabRoute: String, view: CalendarView): String =
    if (tabRoute == NavRoutes.DayView.route) {
        when (view) {
            CalendarView.WEEK  -> NavRoutes.WeekView.route
            CalendarView.MONTH -> NavRoutes.MonthView.route
            else               -> NavRoutes.DayView.route
        }
    } else tabRoute

// ── Drawer content ────────────────────────────────────────────────────
@Composable
private fun CalendarDrawerContent(
    currentView      : CalendarView,
    categoryFilters  : Set<EntryCategory>,
    colorOverrides   : Map<String, String>,
    onToggleCategory : (EntryCategory) -> Unit,
    onSelectDay      : () -> Unit,
    onSelectWeek     : () -> Unit,
    onSelectMonth    : () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text     = stringResource(R.string.drawer_title),
            style    = MaterialTheme.typography.headlineMedium,
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        val drawerItemColors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedTextColor      = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedIconColor      = MaterialTheme.colorScheme.onSecondaryContainer,
            unselectedTextColor    = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedIconColor    = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        NavigationDrawerItem(
            label    = { Text(stringResource(R.string.drawer_day), style = MaterialTheme.typography.bodyLarge) },
            icon     = { Icon(Icons.Outlined.CalendarViewDay, contentDescription = null) },
            selected = currentView == CalendarView.DAY,
            onClick  = onSelectDay,
            modifier = Modifier.padding(horizontal = 12.dp),
            colors   = drawerItemColors,
        )
        NavigationDrawerItem(
            label    = { Text(stringResource(R.string.drawer_week), style = MaterialTheme.typography.bodyLarge) },
            icon     = { Icon(Icons.Outlined.CalendarViewWeek, contentDescription = null) },
            selected = currentView == CalendarView.WEEK,
            onClick  = onSelectWeek,
            modifier = Modifier.padding(horizontal = 12.dp),
            colors   = drawerItemColors,
        )
        NavigationDrawerItem(
            label    = { Text(stringResource(R.string.drawer_month), style = MaterialTheme.typography.bodyLarge) },
            icon     = { Icon(Icons.Outlined.GridView, contentDescription = null) },
            selected = currentView == CalendarView.MONTH,
            onClick  = onSelectMonth,
            modifier = Modifier.padding(horizontal = 12.dp),
            colors   = drawerItemColors,
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text(
            text     = stringResource(R.string.drawer_categories),
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        EntryCategory.entries.forEach { cat ->
            CategoryFilterItem(
                category       = cat,
                checked        = cat in categoryFilters,
                colorOverrides = colorOverrides,
                onToggle       = { onToggleCategory(cat) },
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── Category filter row in drawer ─────────────────────────────────────
@Composable
private fun CategoryFilterItem(
    category      : EntryCategory,
    checked       : Boolean,
    colorOverrides: Map<String, String>,
    onToggle      : () -> Unit,
) {
    val catColor = indicatorColor(category, colorOverrides)
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked         = checked,
            onCheckedChange = { onToggle() },
            colors          = CheckboxDefaults.colors(
                checkedColor   = catColor,
                uncheckedColor = catColor.copy(alpha = 0.45f),
                checkmarkColor = Color.White,
            ),
        )
        Text(
            text  = category.localizedName(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Search overlay ────────────────────────────────────────────────────
@Composable
private fun SearchOverlay(
    query        : String,
    results      : List<EntryEntity>,
    onQueryChange: (String) -> Unit,
    onDismiss    : () -> Unit,
    onEntryClick : (EntryEntity) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background,
    ) {
        Column {
            // ── Search bar ────────────────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                OutlinedTextField(
                    value         = query,
                    onValueChange = onQueryChange,
                    modifier      = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder   = { Text(stringResource(R.string.search_hint)) },
                    singleLine    = true,
                    trailingIcon  = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                )
            }
            HorizontalDivider()

            // ── Results area ──────────────────────────────────────────
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
                            text  = stringResource(R.string.search_no_results),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding      = PaddingValues(16.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        items(results, key = { it.id }) { entry ->
                            SearchEntryCard(
                                entry   = entry,
                                onClick = { onEntryClick(entry) },
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

// ── Search result card ────────────────────────────────────────────────
@Composable
private fun SearchEntryCard(entry: EntryEntity, onClick: () -> Unit) {
    val accentColor = entry.displayBgColor()
    val locale      = LocalConfiguration.current.locales[0]
    val dateFmt     = remember(locale) { DateTimeFormatter.ofPattern("d MMM yyyy", locale) }

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Accent stripe
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                .background(accentColor),
        )
        Spacer(Modifier.width(12.dp))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = entry.title,
                style    = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color    = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(3.dp))
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                // Category badge
                Text(
                    text     = entry.category.localizedName(),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor.copy(alpha = 0.25f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                // Date
                Text(
                    text  = entry.date.format(dateFmt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.location.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text     = entry.location,
                        style    = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
