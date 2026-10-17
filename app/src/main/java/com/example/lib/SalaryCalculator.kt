package com.example.lib

import com.example.data.entity.AbsenceEntity
import com.example.data.entity.EmploymentEntity
import com.example.data.entity.SalaryRateEntity
import java.time.LocalDate

data class CalculatedSalaryResult(
    val month: String, // "1405-07"
    val daysInMonth: Int,
    val workedDaysCount: Int,
    val absenceDaysCount: Int,
    val grossSalary: Long,
    val deductions: Long,
    val netSalary: Long,
    val hasUnknownAbsences: Boolean,
    val details: List<DaySalaryDetail>
)

data class DaySalaryDetail(
    val dayNumber: Int,
    val dateIso: String,
    val isWorked: Boolean,
    val isAbsence: Boolean,
    val absenceType: String?, // "paid" | "unpaid" | "unknown"
    val dailyRate: Double
)

object SalaryCalculator {

    /**
     * Calculates the salary for a person in a given Jalali month (e.g. "1405-07").
     */
    fun calculateMonthlySalary(
        employment: EmploymentEntity,
        salaryRates: List<SalaryRateEntity>,
        absences: List<AbsenceEntity>,
        monthStr: String // "1405-07"
    ): CalculatedSalaryResult {
        val parts = monthStr.split("-")
        val year = parts[0].toIntOrNull() ?: 1405
        val month = parts[1].toIntOrNull() ?: 7
        val daysInMonth = JalaliDateHelper.daysInMonth(year, month)

        val activeRates = salaryRates
            .filter { it.personId == employment.personId && !it.deleted }
            .sortedBy { it.effectiveFrom }

        val activeAbsences = absences
            .filter { it.personId == employment.personId && !it.deleted }

        var grossDouble = 0.0
        var deductionDouble = 0.0
        var workedDaysCount = 0
        var absenceDaysCount = 0
        var hasUnknown = false
        val details = mutableListOf<DaySalaryDetail>()

        for (d in 1..daysInMonth) {
            val dateGregorian = JalaliDateHelper.toGregorian(year, month, d)
            val dateIso = dateGregorian.toString()

            val isAfterStart = employment.workStartDate == null || dateIso >= employment.workStartDate
            val isBeforeEnd = employment.endDate == null || dateIso <= employment.endDate
            val isWorked = isAfterStart && isBeforeEnd

            // Find effective monthly salary for this day
            val validRate = activeRates.filter { it.effectiveFrom <= dateIso }.maxByOrNull { it.effectiveFrom }
            val monthlySalary = validRate?.monthlySalary ?: 0L
            val dailyRate = if (daysInMonth > 0) monthlySalary.toDouble() / daysInMonth.toDouble() else 0.0

            // Check absences
            val matchingAbsence = if (isWorked) {
                activeAbsences.find { it.fromDate <= dateIso && dateIso <= it.toDate }
            } else null

            var isAbsence = false
            var absenceType: String? = null

            if (isWorked) {
                workedDaysCount++
                grossDouble += dailyRate

                if (matchingAbsence != null) {
                    isAbsence = true
                    absenceType = matchingAbsence.type
                    if (matchingAbsence.type == "unknown") {
                        hasUnknown = true
                    }
                    if (employment.kind == "fixed") {
                        if (matchingAbsence.type == "unpaid" || matchingAbsence.type == "unknown") {
                            absenceDaysCount++
                            deductionDouble += dailyRate
                        }
                    }
                }
            }

            details.add(
                DaySalaryDetail(
                    dayNumber = d,
                    dateIso = dateIso,
                    isWorked = isWorked,
                    isAbsence = isAbsence,
                    absenceType = absenceType,
                    dailyRate = dailyRate
                )
            )
        }

        val rawNet = (grossDouble - deductionDouble).coerceAtLeast(0.0)
        val netRounded = NumberFormatHelper.roundToThousand(Math.round(rawNet))
        val grossRounded = NumberFormatHelper.roundToThousand(Math.round(grossDouble))
        val deductionsRounded = NumberFormatHelper.roundToThousand(Math.round(deductionDouble))

        return CalculatedSalaryResult(
            month = monthStr,
            daysInMonth = daysInMonth,
            workedDaysCount = workedDaysCount,
            absenceDaysCount = absenceDaysCount,
            grossSalary = grossRounded,
            deductions = deductionsRounded,
            netSalary = netRounded,
            hasUnknownAbsences = hasUnknown,
            details = details
        )
    }

    /**
     * Calculates single day deduction for display (e.g. 15M in 31-day month = 484,000)
     */
    fun calculateSingleDayRate(monthlySalary: Long, daysInMonth: Int): Long {
        if (daysInMonth <= 0) return 0L
        val raw = monthlySalary.toDouble() / daysInMonth.toDouble()
        return NumberFormatHelper.roundToThousand(Math.round(raw))
    }
}
