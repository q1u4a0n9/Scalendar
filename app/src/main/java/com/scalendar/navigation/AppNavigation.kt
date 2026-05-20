package com.scalendar.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.scalendar.data.model.EntryCategory
import com.scalendar.ui.screen.dayview.DayViewScreen
import com.scalendar.ui.screen.journal.JournalScreen
import com.scalendar.ui.screen.monthview.MonthViewScreen
import com.scalendar.ui.screen.notes.NotesScreen
import com.scalendar.ui.screen.settings.SettingsScreen
import com.scalendar.ui.screen.weekview.WeekViewScreen
import com.scalendar.ui.shared.SharedCalendarViewModel
import kotlinx.coroutines.launch

// ── Per-category indicator colours (match HTML design: green/blue/orange/pink/red/purple) ──
private fun EntryCategory.indicatorColor(): Color = when (this) {
    EntryCategory.TASK     -> Color(0xFF4A654E)  // primary green
    EntryCategory.CLASS    -> Color(0xFFF97316)  // orange
    EntryCategory.SPORT    -> Color(0xFF3B82F6)  // blue
    EntryCategory.EXAM     -> Color(0xFFB91C1C)  // dark red
    EntryCategory.BIRTHDAY -> Color(0xFFEC4899)  // pink
    EntryCategory.EVENT    -> Color(0xFFA855F7)  // purple
}

// ── Bottom nav item descriptor ────────────────────────────────────────
private data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

private val bottomNavItems = listOf(
    BottomNavItem("Ghi chú", Icons.Filled.EditNote,       NavRoutes.Notes.route),
    BottomNavItem("Lịch",    Icons.Filled.CalendarMonth,  NavRoutes.DayView.route),
    BottomNavItem("Nhật ký", Icons.AutoMirrored.Filled.MenuBook, NavRoutes.Journal.route),
    BottomNavItem("Cài đặt", Icons.Filled.Settings,       NavRoutes.Settings.route),
)

// ── Root composable ───────────────────────────────────────────────────
@Composable
fun AppNavigation() {
    val navController   = rememberNavController()
    val sharedVm: SharedCalendarViewModel = hiltViewModel()
    val calendarView    by sharedVm.calendarView.collectAsState()
    val categoryFilters by sharedVm.categoryFilters.collectAsState()

    val drawerState     = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope  = rememberCoroutineScope()

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // Which "tab" is selected?  Calendar family all map to the Calendar tab.
    val selectedTabRoute = when (currentRoute) {
        in NavRoutes.calendarRoutes -> NavRoutes.DayView.route
        NavRoutes.Journal.route     -> NavRoutes.Journal.route
        NavRoutes.Notes.route       -> NavRoutes.Notes.route
        NavRoutes.Settings.route    -> NavRoutes.Settings.route
        else                        -> NavRoutes.Notes.route
    }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        gesturesEnabled = currentRoute in NavRoutes.calendarRoutes,   // drawer only on calendar screens
        drawerContent = {
            CalendarDrawerContent(
                currentView       = calendarView,
                categoryFilters   = categoryFilters,
                onToggleCategory  = sharedVm::toggleCategoryFilter,
                onSelectDay     = {
                    sharedVm.setCalendarView(CalendarView.DAY)
                    navController.navigate(NavRoutes.DayView.route) { launchSingleTop = true }
                    coroutineScope.launch { drawerState.close() }
                },
                onSelectWeek    = {
                    sharedVm.setCalendarView(CalendarView.WEEK)
                    navController.navigate(NavRoutes.WeekView.route) { launchSingleTop = true }
                    coroutineScope.launch { drawerState.close() }
                },
                onSelectMonth   = {
                    sharedVm.setCalendarView(CalendarView.MONTH)
                    navController.navigate(NavRoutes.MonthView.route) { launchSingleTop = true }
                    coroutineScope.launch { drawerState.close() }
                },
            )
        }
    ) {
        Scaffold(
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
                modifier         = Modifier.padding(innerPadding),
            ) {
                composable(NavRoutes.DayView.route) {
                    DayViewScreen(
                        sharedVm      = sharedVm,
                        onOpenDrawer  = { coroutineScope.launch { drawerState.open() } },
                    )
                }
                composable(NavRoutes.WeekView.route) {
                    WeekViewScreen(
                        sharedVm     = sharedVm,
                        onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                        onDayClick   = { date ->
                            sharedVm.selectDate(date)
                            sharedVm.setCalendarView(CalendarView.DAY)
                            navController.navigate(NavRoutes.DayView.route) { launchSingleTop = true }
                        }
                    )
                }
                composable(NavRoutes.MonthView.route) {
                    MonthViewScreen(
                        sharedVm     = sharedVm,
                        onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                        onDayClick   = { date ->
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
                            navController.navigate(NavRoutes.DayView.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable(NavRoutes.Notes.route) {
                    NotesScreen()
                }
                composable(NavRoutes.Settings.route) {
                    SettingsScreen()
                }
            }
        }
    }
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
            text     = "Lịch",
            style    = MaterialTheme.typography.headlineMedium,
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ── View switcher ─────────────────────────────────────────────
        // Design: short labels "Ngày / Tuần / Tháng", selected bg = secondaryContainer (amber)
        val drawerItemColors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor   = MaterialTheme.colorScheme.secondaryContainer,
            selectedTextColor        = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedIconColor        = MaterialTheme.colorScheme.onSecondaryContainer,
            unselectedTextColor      = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedIconColor      = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        NavigationDrawerItem(
            label    = { Text("Ngày", style = MaterialTheme.typography.bodyLarge) },
            icon     = { Icon(Icons.Outlined.CalendarViewDay, contentDescription = null) },
            selected = currentView == CalendarView.DAY,
            onClick  = onSelectDay,
            modifier = Modifier.padding(horizontal = 12.dp),
            colors   = drawerItemColors,
        )

        NavigationDrawerItem(
            label    = { Text("Tuần", style = MaterialTheme.typography.bodyLarge) },
            icon     = { Icon(Icons.Outlined.CalendarViewWeek, contentDescription = null) },
            selected = currentView == CalendarView.WEEK,
            onClick  = onSelectWeek,
            modifier = Modifier.padding(horizontal = 12.dp),
            colors   = drawerItemColors,
        )

        NavigationDrawerItem(
            label    = { Text("Tháng", style = MaterialTheme.typography.bodyLarge) },
            icon     = { Icon(Icons.Outlined.GridView, contentDescription = null) },
            selected = currentView == CalendarView.MONTH,
            onClick  = onSelectMonth,
            modifier = Modifier.padding(horizontal = 12.dp),
            colors   = drawerItemColors,
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // ── Categories section ────────────────────────────────────────
        Text(
            text     = "DANH MỤC",
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        EntryCategory.entries.forEach { cat ->
            CategoryFilterItem(
                category   = cat,
                checked    = cat in categoryFilters,
                onToggle   = { onToggleCategory(cat) },
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── Category filter row in drawer ─────────────────────────────────────
// Design: [checkbox LEFT, coloured per category] [label]  — no separate dot
@Composable
private fun CategoryFilterItem(
    category: EntryCategory,
    checked : Boolean,
    onToggle: () -> Unit,
) {
    val catColor = category.indicatorColor()

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
                checkedColor          = catColor,
                uncheckedColor        = catColor.copy(alpha = 0.45f),
                checkmarkColor        = Color.White,
            ),
        )

        Text(
            text  = category.displayName(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
