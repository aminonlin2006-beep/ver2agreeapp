package com.example.lib

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Pure offline Jalali (Persian Solar Hijri) date calculation and formatting.
 * Canonical astronomical algorithms (JDF standard).
 */
object JalaliDateHelper {

    private val MONTH_NAMES = listOf(
        "فروردین", "اردیبهشت", "خرداد",
        "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر",
        "دی", "بهمن", "اسفند"
    )

    private val WEEKDAY_NAMES = listOf(
        "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه"
    )

    data class JalaliDate(
        val year: Int,
        val month: Int, // 1 to 12
        val day: Int,   // 1 to 31
        val dayOfWeekIndex: Int // 0: شنبه ... 6: جمعه
    ) {
        val monthName: String get() = MONTH_NAMES.getOrElse(month - 1) { "" }
        val dayOfWeekName: String get() = WEEKDAY_NAMES.getOrElse(dayOfWeekIndex) { "" }

        /** Internal standard format: "1405-07-26" */
        val isoFormat: String
            get() = "%04d-%02d-%02d".format(year, month, day)

        /** Month key: "1405-07" */
        val monthKey: String
            get() = "%04d-%02d".format(year, month)

        /** Formatted full text: "شنبه ۲۵ مهر ۱۴۰۵" */
        fun toDisplayFull(): String {
            val dayFa = NumberFormatHelper.toPersianDigits(day)
            val yearFa = NumberFormatHelper.toPersianDigits(year)
            return "$dayOfWeekName $dayFa $monthName $yearFa"
        }

        /** Formatted short date: "۲۵ مهر ۱۴۰۵" */
        fun toDisplayShort(): String {
            val dayFa = NumberFormatHelper.toPersianDigits(day)
            val yearFa = NumberFormatHelper.toPersianDigits(year)
            return "$dayFa $monthName $yearFa"
        }
    }

    /**
     * Converts a Gregorian LocalDate to Jalali date.
     */
    fun fromGregorian(gDate: LocalDate): JalaliDate {
        val gy = gDate.year
        val gm = gDate.monthValue
        val gd = gDate.dayOfMonth

        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) (gy + 1) else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]

        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461

        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }

        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }

        // Day of week: Saturday = 0, Sunday = 1, ... Friday = 6
        val dow = when (gDate.dayOfWeek.value) {
            6 -> 0 // Saturday
            7 -> 1 // Sunday
            1 -> 2 // Monday
            2 -> 3 // Tuesday
            3 -> 4 // Wednesday
            4 -> 5 // Thursday
            5 -> 6 // Friday
            else -> 0
        }

        return JalaliDate(jy, jm, jd, dow)
    }

    /**
     * Converts a Jalali date (year, month, day) back to Gregorian LocalDate.
     */
    fun toGregorian(jyIn: Int, jm: Int, jd: Int): LocalDate {
        val jy = jyIn + 1595
        val jDaysInMonth = if (jm < 7) (jm - 1) * 31 else (((jm - 7) * 30) + 186)
        var days = -355668 + (365 * jy) + ((jy / 33) * 8) + (((jy % 33) + 3) / 4) + jd + jDaysInMonth

        var gy = 400 * (days / 146097)
        days %= 146097

        if (days > 36524) {
            days--
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) {
                days++
            }
        }

        gy += 4 * (days / 1461)
        days %= 1461

        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }

        var gd = days + 1
        val isLeap = (gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)
        val salA = intArrayOf(0, 31, if (isLeap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > salA[gm]) {
            gd -= salA[gm]
            gm++
        }

        return LocalDate.of(gy, gm, gd)
    }

    /** Returns today's Jalali date */
    fun today(): JalaliDate {
        return fromGregorian(LocalDate.now())
    }

    /** Convert ISO Gregorian string "YYYY-MM-DD" to JalaliDate */
    fun fromIsoDate(iso: String): JalaliDate {
        return try {
            val ld = LocalDate.parse(iso.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
            fromGregorian(ld)
        } catch (_: Exception) {
            today()
        }
    }

    fun isoToJalali(iso: String): JalaliDate = fromIsoDate(iso)

    fun toIsoDate(gDate: LocalDate): String {
        return gDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    fun daysBetween(startIso: String, endIso: String): Long {
        return try {
            val d1 = LocalDate.parse(startIso.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
            val d2 = LocalDate.parse(endIso.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
            java.time.temporal.ChronoUnit.DAYS.between(d1, d2)
        } catch (_: Exception) {
            0L
        }
    }

    fun addDays(iso: String, days: Long): String {
        return try {
            val d = LocalDate.parse(iso.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
            d.plusDays(days).format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            iso
        }
    }

    fun isLeapJalaliYear(jy: Int): Boolean {
        val rem = ((jy + 38) * 31) % 128
        return rem < 31
    }

    fun daysInMonth(jy: Int, jm: Int): Int {
        return when {
            jm in 1..6 -> 31
            jm in 7..11 -> 30
            jm == 12 -> if (isLeapJalaliYear(jy)) 30 else 29
            else -> 30
        }
    }
}
