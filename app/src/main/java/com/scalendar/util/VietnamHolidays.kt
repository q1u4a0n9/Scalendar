package com.scalendar.util

import java.time.LocalDate

/**
 * Danh sách ngày lễ chính thức Việt Nam (Bộ luật Lao động 2019, Điều 112).
 *
 * Ngày cố định (Dương lịch) được tính trực tiếp.
 * Ngày Âm lịch (Tết Nguyên Đán, Giỗ Tổ Hùng Vương) dùng bảng tra cứu hardcoded.
 * Phạm vi: 2023 – 2035. Ngoài khoảng này chỉ hiện ngày cố định.
 */
object VietnamHolidays {

    // ── 1/1 Âm lịch (ngày đầu Tết Nguyên Đán) ─────────────────────────
    private val lunarNewYear = mapOf(
        2023 to LocalDate.of(2023, 1, 22),
        2024 to LocalDate.of(2024, 2, 10),
        2025 to LocalDate.of(2025, 1, 29),
        2026 to LocalDate.of(2026, 2, 17),
        2027 to LocalDate.of(2027, 2,  6),
        2028 to LocalDate.of(2028, 1, 26),
        2029 to LocalDate.of(2029, 2, 13),
        2030 to LocalDate.of(2030, 2,  3),
        2031 to LocalDate.of(2031, 1, 23),
        2032 to LocalDate.of(2032, 2, 11),
        2033 to LocalDate.of(2033, 1, 31),
        2034 to LocalDate.of(2034, 2, 19),
        2035 to LocalDate.of(2035, 2,  8),
    )

    // ── 10/3 Âm lịch (Giỗ Tổ Hùng Vương) ──────────────────────────────
    private val hungKingsDay = mapOf(
        2023 to LocalDate.of(2023, 4, 29),
        2024 to LocalDate.of(2024, 4, 18),
        2025 to LocalDate.of(2025, 4,  7),
        2026 to LocalDate.of(2026, 4, 26),
        2027 to LocalDate.of(2027, 4, 15),
        2028 to LocalDate.of(2028, 4,  3),
        2029 to LocalDate.of(2029, 4, 22),
        2030 to LocalDate.of(2030, 4, 12),
        2031 to LocalDate.of(2031, 4, 30),
        2032 to LocalDate.of(2032, 4, 19),
        2033 to LocalDate.of(2033, 4,  8),
        2034 to LocalDate.of(2034, 4, 27),
        2035 to LocalDate.of(2035, 4, 16),
    )

    // ── Bảng tổng hợp (lazy — chỉ build khi dùng lần đầu) ─────────────
    private val table: Map<LocalDate, String> by lazy {
        buildMap {
            for (year in 2020..2040) {
                put(LocalDate.of(year, 1,  1), "Tết Dương lịch")
                put(LocalDate.of(year, 4, 30), "Ngày Giải phóng miền Nam")
                put(LocalDate.of(year, 5,  1), "Quốc tế Lao động")
                put(LocalDate.of(year, 9,  2), "Quốc khánh")
            }

            // Tết Nguyên Đán: Giao thừa + Mùng 1–4
            lunarNewYear.values.forEach { tet ->
                put(tet.minusDays(1), "Giao thừa Tết Nguyên Đán")
                put(tet,              "Tết Nguyên Đán – Mùng 1")
                put(tet.plusDays(1),  "Tết Nguyên Đán – Mùng 2")
                put(tet.plusDays(2),  "Tết Nguyên Đán – Mùng 3")
                put(tet.plusDays(3),  "Tết Nguyên Đán – Mùng 4")
            }

            // Giỗ Tổ Hùng Vương
            hungKingsDay.values.forEach { day ->
                put(day, "Giỗ Tổ Hùng Vương")
            }
        }
    }

    // ── Chỉ ngày lễ Dương lịch (quốc gia) ─────────────────────────────
    private val nationalOnly: Map<LocalDate, String> by lazy {
        buildMap {
            for (year in 2020..2040) {
                put(LocalDate.of(year, 1,  1), "Tết Dương lịch")
                put(LocalDate.of(year, 4, 30), "Ngày Giải phóng miền Nam")
                put(LocalDate.of(year, 5,  1), "Quốc tế Lao động")
                put(LocalDate.of(year, 9,  2), "Quốc khánh")
            }
        }
    }

    /**
     * Tên ngày lễ theo [mode]:
     * - "ALL"      → tất cả (Tết, Giỗ Tổ, cố định)
     * - "NATIONAL" → chỉ ngày Dương lịch (1/1, 30/4, 1/5, 2/9)
     * - "NONE"     → không hiển thị
     */
    fun getName(date: LocalDate, mode: String = "ALL"): String? = when (mode) {
        "NONE"     -> null
        "NATIONAL" -> nationalOnly[date]
        else       -> table[date]
    }

    /** Kiểm tra có phải ngày lễ không (theo mode). */
    fun isHoliday(date: LocalDate, mode: String = "ALL"): Boolean =
        getName(date, mode) != null
}
