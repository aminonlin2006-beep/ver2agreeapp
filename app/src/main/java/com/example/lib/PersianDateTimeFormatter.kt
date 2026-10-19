package com.example.lib

/**
 * Formats dates and times into natural, human-friendly Persian text.
 * Example: "دوشنبه ۱۸ مهر ساعت ۹ عصر"
 */
object PersianDateTimeFormatter {

    /**
     * Converts 24-hour time "HH:mm" (e.g. "21:00", "09:30") to natural Persian:
     * e.g. "ساعت ۹ عصر", "ساعت ۹ و نیم عصر", "ساعت ۱۰ صبح", "ساعت ۴ بعدازظهر", "ساعت ۱۲ ظهر"
     */
    fun formatPersianTimeOfDay(time: String): String {
        if (time.isBlank()) return "در طول روز"
        val parts = time.trim().split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return "ساعت $time"
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val (h12, period) = when (hour) {
            0 -> Pair(12, "نیمه‌شب")
            in 1..4 -> Pair(hour, "بامداد")
            in 5..11 -> Pair(hour, "صبح")
            12 -> Pair(12, "ظهر")
            in 13..16 -> Pair(hour - 12, "بعدازظهر")
            in 17..20 -> Pair(hour - 12, "عصر")
            in 21..23 -> Pair(hour - 12, "عصر")
            else -> Pair(hour, "")
        }

        val hFa = NumberFormatHelper.toPersianDigits(h12)
        val mFa = NumberFormatHelper.toPersianDigits("%02d".format(minute))

        return if (minute == 0) {
            "ساعت $hFa $period".trim()
        } else if (minute == 30) {
            "ساعت $hFa و نیم $period".trim()
        } else {
            "ساعت $hFa:$mFa $period".trim()
        }
    }

    /**
     * Returns full natural Persian date-time string:
     * e.g. "دوشنبه ۱۸ مهر ساعت ۹ عصر" or "دوشنبه ۱۸ مهر در طول روز"
     */
    fun formatPersianDateTime(dateIso: String, time: String = ""): String {
        val jDate = try {
            JalaliDateHelper.fromIsoDate(dateIso)
        } catch (_: Exception) {
            JalaliDateHelper.today()
        }

        val dayName = jDate.dayOfWeekName
        val dayNum = NumberFormatHelper.toPersianDigits(jDate.day)
        val monthName = jDate.monthName

        val datePart = "$dayName $dayNum $monthName"

        return if (time.isBlank()) {
            "$datePart (در طول روز)"
        } else {
            val timePart = formatPersianTimeOfDay(time)
            "$datePart $timePart"
        }
    }

    /**
     * Returns full natural Persian date-time including year:
     * e.g. "دوشنبه ۱۸ مهر ۱۴۰۵ ساعت ۹ عصر"
     */
    fun formatPersianDateTimeWithYear(dateIso: String, time: String = ""): String {
        val jDate = try {
            JalaliDateHelper.fromIsoDate(dateIso)
        } catch (_: Exception) {
            JalaliDateHelper.today()
        }

        val dayName = jDate.dayOfWeekName
        val dayNum = NumberFormatHelper.toPersianDigits(jDate.day)
        val monthName = jDate.monthName
        val yearNum = NumberFormatHelper.toPersianDigits(jDate.year)

        val datePart = "$dayName $dayNum $monthName $yearNum"
        return if (time.isBlank()) {
            "$datePart (در طول روز)"
        } else {
            val timePart = formatPersianTimeOfDay(time)
            "$datePart $timePart"
        }
    }
}
