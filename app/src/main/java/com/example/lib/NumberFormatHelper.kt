package com.example.lib

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object NumberFormatHelper {

    private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    private val ARABIC_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    /**
     * Converts any number or string containing 0-9 to Persian digits ۰-۹.
     */
    fun toPersianDigits(value: Any?): String {
        if (value == null) return ""
        val s = value.toString()
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                in '0'..'9' -> sb.append(PERSIAN_DIGITS[ch - '0'])
                '.' -> sb.append('٫') // Persian decimal separator
                ',' -> sb.append('٬') // Persian thousands separator
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Normalizes a string by converting Persian and Arabic digits to English digits,
     * converting Persian decimal '٫' to '.', and removing thousand separators '٬' and ','.
     */
    fun normalizeToEnglish(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input.trim()) {
            when {
                ch in '0'..'9' -> sb.append(ch)
                ch in PERSIAN_DIGITS -> sb.append((ch - '۰'))
                ch in ARABIC_DIGITS -> sb.append((ch - '٠'))
                ch == '٫' || ch == '.' -> sb.append('.')
                ch == '٬' || ch == ',' || ch == ' ' || ch == '_' -> {
                    // ignore separators
                }
                ch == '-' || ch == '−' -> sb.append('-')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Parses user numeric input (supporting Persian, Arabic, English digits and separators).
     */
    fun parsePersianDouble(input: String): Double? {
        val normalized = normalizeToEnglish(input)
        if (normalized.isEmpty()) return null
        return normalized.toDoubleOrNull()
    }

    fun parsePersianLong(input: String): Long? {
        val normalized = normalizeToEnglish(input)
        if (normalized.isEmpty()) return null
        return normalized.toLongOrNull() ?: normalized.toDoubleOrNull()?.toLong()
    }

    /**
     * Formats an integer number with Persian thousand separators (e.g. ۱۵٬۰۰۰٬۰۰۰).
     */
    fun formatWithThousands(value: Long): String {
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = '٬'
        }
        val df = DecimalFormat("#,###", symbols)
        val formattedEn = df.format(value)
        return toPersianDigits(formattedEn)
    }

    /**
     * Full Toman formatting for forms: e.g. "۱۵٬۰۰۰٬۰۰۰ تومان"
     */
    fun formatTomanFull(amount: Long): String {
        val isNegative = amount < 0
        val absAmount = abs(amount)
        val formatted = formatWithThousands(absAmount)
        val prefix = if (isNegative) "−" else ""
        return "$prefix$formatted تومان"
    }

    /**
     * Short Toman formatting for lists and reports (AGENTS.md Decision 268):
     * - Under 1,000: e.g. "۸۰۰ تومان"
     * - 1,000 to 999,999: e.g. "۴۸۴ هزار"
     * - 1,000,000 to 999,999,999: up to 2 decimal digits without trailing zeros, e.g. "۱۴٫۰۳ میلیون", "۱۵ میلیون"
     * - 1,000,000,000+: e.g. "۱٫۲ میلیارد"
     * - Negative amounts with "−": e.g. "−۴۸۴ هزار"
     */
    fun formatTomanShort(amount: Long): String {
        val isNegative = amount < 0
        val prefix = if (isNegative) "−" else ""
        val absAmount = abs(amount)

        val result = when {
            absAmount < 1_000L -> "${toPersianDigits(absAmount)} تومان"
            absAmount < 1_000_000L -> {
                val thousands = absAmount / 1_000.0
                "${formatDecimalShort(thousands)} هزار"
            }
            absAmount < 1_000_000_000L -> {
                val millions = absAmount / 1_000_000.0
                "${formatDecimalShort(millions)} میلیون"
            }
            else -> {
                val billions = absAmount / 1_000_000_000.0
                "${formatDecimalShort(billions)} میلیارد"
            }
        }
        return "$prefix$result"
    }

    private fun formatDecimalShort(value: Double): String {
        // Round to 2 decimal places
        val rounded = (value * 100.0).roundToLong() / 100.0
        val df = DecimalFormat("#.##", DecimalFormatSymbols(Locale.US))
        val s = df.format(rounded)
        return toPersianDigits(s)
    }

    /**
     * Rounds monetary amount to nearest 1,000 Tomans (Decision 267).
     * Half-up rounding: e.g. 483,871 -> 484,000.
     */
    fun roundToThousand(amount: Long): Long {
        val sign = if (amount < 0) -1 else 1
        val absVal = abs(amount)
        val remainder = absVal % 1000
        val base = (absVal / 1000) * 1000
        val rounded = if (remainder >= 500) base + 1000 else base
        return rounded * sign
    }

    /**
     * Formats area in square meters and hectares.
     * 1 hectare = 10,000 m².
     * Returns: "۲٫۵ هکتار (۲۵٬۰۰۰ متر مربع)"
     */
    fun formatArea(areaM2: Double?): String {
        if (areaM2 == null || areaM2 <= 0) return ""
        val hectares = areaM2 / 10000.0
        val df = DecimalFormat("#.##", DecimalFormatSymbols(Locale.US))
        val hecStr = toPersianDigits(df.format(hectares))
        val m2Str = formatWithThousands(areaM2.roundToLong())

        return if (areaM2 >= 10000.0) {
            "$hecStr هکتار ($m2Str متر مربع)"
        } else {
            "$m2Str متر مربع ($hecStr هکتار)"
        }
    }
}
