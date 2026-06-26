package com.scalendar.navigation

sealed class NavRoutes(val route: String) {
    object Splash     : NavRoutes("splash")
    object Onboarding : NavRoutes("onboarding")
    object DayView    : NavRoutes("day_view")
    object WeekView   : NavRoutes("week_view")
    object MonthView  : NavRoutes("month_view")
    object Journal    : NavRoutes("journal")
    object Notes      : NavRoutes("notes")
    object Settings   : NavRoutes("settings")

    companion object {
        val calendarRoutes = setOf(DayView.route, WeekView.route, MonthView.route)
    }
}

enum class CalendarView { DAY, WEEK, MONTH }
