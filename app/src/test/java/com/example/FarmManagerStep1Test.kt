package com.example

import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FarmManagerStep1Test {

    @Test
    fun testPersianDigitsConversion() {
        assertEquals("۰۱۲۳۴۵۶۷۸۹", NumberFormatHelper.toPersianDigits("0123456789"))
        assertEquals("۱۲۳", NumberFormatHelper.toPersianDigits(123))
    }

    @Test
    fun testNumericParsing() {
        // Persian digits with Persian decimal comma
        assertEquals(2.5, NumberFormatHelper.parsePersianDouble("۲٫۵")!!, 0.001)
        // English digits with dot
        assertEquals(2.5, NumberFormatHelper.parsePersianDouble("2.5")!!, 0.001)
        // Thousands separator
        assertEquals(15000000L, NumberFormatHelper.parsePersianLong("۱۵٬۰۰۰٬۰۰۰")!!)
        assertEquals(15000000L, NumberFormatHelper.parsePersianLong("15,000,000")!!)
    }

    @Test
    fun testCurrencyShortFormatting_AgentsMdExamples() {
        // زیر ۱٬۰۰۰ ← «۸۰۰ تومان»
        assertEquals("۸۰۰ تومان", NumberFormatHelper.formatTomanShort(800L))

        // زیر ۱٬۰۰۰٬۰۰۰ ← «۴۸۴ هزار»
        assertEquals("۴۸۴ هزار", NumberFormatHelper.formatTomanShort(484000L))

        // از یک میلیون تا زیر هزار میلیون ← «۱۴٫۰۳ میلیون»، «۱۵ میلیون»
        assertEquals("۱۴٫۰۳ میلیون", NumberFormatHelper.formatTomanShort(14030000L))
        assertEquals("۱۵ میلیون", NumberFormatHelper.formatTomanShort(15000000L))

        // از هزار میلیون به بالا ← «۱٫۲ میلیارد»
        assertEquals("۱٫۲ میلیارد", NumberFormatHelper.formatTomanShort(1200000000L))

        // مبلغ منفی با علامت منفی: «−۴۸۴ هزار»
        assertEquals("−۴۸۴ هزار", NumberFormatHelper.formatTomanShort(-484000L))
    }

    @Test
    fun testRoundingToThousand_AgentsMdExample() {
        // ۴۸۳٬۸۷۱ ← ۴۸۴٬۰۰۰
        assertEquals(484000L, NumberFormatHelper.roundToThousand(483871L))
        assertEquals(483000L, NumberFormatHelper.roundToThousand(483499L))
        assertEquals(484000L, NumberFormatHelper.roundToThousand(483500L))
    }

    @Test
    fun testAreaFormatting() {
        // ۲٫۵ هکتار (۲۵٬۰۰۰ متر مربع)
        val area = NumberFormatHelper.formatArea(25000.0)
        assertTrue(area.contains("۲٫۵ هکتار"))
        assertTrue(area.contains("۲۵٬۰۰۰ متر مربع"))

        // کمتر از یک هکتار
        val smallArea = NumberFormatHelper.formatArea(5000.0)
        assertTrue(smallArea.contains("۵٬۰۰۰ متر مربع"))
        assertTrue(smallArea.contains("۰٫۵ هکتار"))
    }

    @Test
    fun testJalaliDateHelper() {
        // 2026-10-17 is 1405-07-26 (شنبه ۲۶ مهر ۱۴۰۵)
        val date = LocalDate.of(2026, 10, 17)
        val jalali = JalaliDateHelper.fromGregorian(date)
        assertEquals(1405, jalali.year)
        assertEquals(7, jalali.month)
        assertEquals(25, jalali.day)
        assertEquals("مهر", jalali.monthName)
        assertEquals("شنبه", jalali.dayOfWeekName)
        assertEquals("1405-07", jalali.monthKey)
        assertEquals("1405-07-25", jalali.isoFormat)
    }

    @Test
    fun testJalaliRoundTrip() {
        val original = LocalDate.of(2025, 3, 21) // 1404-01-01
        val jalali = JalaliDateHelper.fromGregorian(original)
        val restored = JalaliDateHelper.toGregorian(jalali.year, jalali.month, jalali.day)
        assertEquals(original, restored)
    }
}
