package com.scalendar.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

fun LocalDate.startOfWeek(): LocalDate =
    with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun LocalDate.endOfWeek(): LocalDate =
    with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
