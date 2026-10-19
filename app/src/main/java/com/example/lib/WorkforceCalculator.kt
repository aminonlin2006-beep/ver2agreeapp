package com.example.lib

import com.example.data.entity.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

data class CalculatedAttendanceLine(
    val personId: String,
    val personName: String,
    val ticks: Int,
    val dailyWage: Long,
    val totalWage: Long,
    val wageUnits: Int
)

data class CalculatedForeman(
    val personId: String,
    val personName: String,
    val ticks: Int,
    val present: Boolean,
    val coef: Int,
    val dailyWage: Long, // crew base wage
    val totalWage: Long,
    val wageUnits: Int
)

data class CalculatedAttendance(
    val totalPersons: Int,
    val totalWageUnits: Int,
    val totalAmount: Long,
    val memberLines: List<CalculatedAttendanceLine>,
    val foreman: CalculatedForeman?,
    val headcount: Int? = null,
    val countTicks: Int? = null,
    val crewDailyWage: Long? = null
)

data class PaymentShare(
    val accountType: String, // "person" | "crew"
    val accountId: String,
    val accountName: String,
    val amount: Long
)

data class DisplayLedgerRow(
    val id: String,
    val date: String,
    val description: String,
    val debit: Long, // بدهکار (پرداخت به او، بدهی)
    val credit: Long, // بستانکار (کارکرد، طلب)
    var runningBalance: Long = 0L,
    val isAuto: Boolean,
    val sourceKind: String, // "attendance" | "piecework" | "payment" | "manual"
    val sourceId: String,
    val isVoided: Boolean = false,
    val voidReason: String? = null,
    val isEdited: Boolean = false,
    val createdAt: String = ""
)

object WorkforceCalculator {

    /**
     * Finds the effective daily wage for a crew / person on day D (Decision 21).
     * If personId != null and a specific rate exists with effectiveFrom <= D, takes max effectiveFrom.
     * Otherwise takes crew base rate (personId == null) with max effectiveFrom <= D.
     */
    fun getEffectiveDailyWage(
        wageRates: List<WageRateEntity>,
        crewId: String,
        personId: String?,
        date: String,
        salaryRates: List<SalaryRateEntity> = emptyList()
    ): Long {
        if (personId != null) {
            val personalRate = wageRates
                .filter { it.crewId == crewId && it.personId == personId && it.effectiveFrom <= date && !it.deleted }
                .maxByOrNull { it.effectiveFrom }
            if (personalRate != null) {
                return personalRate.dailyWage
            }
            val salaryRate = salaryRates
                .filter { it.personId == personId && it.effectiveFrom <= date && !it.deleted }
                .maxByOrNull { it.effectiveFrom }
            if (salaryRate != null && salaryRate.monthlySalary > 0L) {
                return NumberFormatHelper.roundToThousand(salaryRate.monthlySalary / 30L)
            }
        }
        val crewRate = wageRates
            .filter { it.crewId == crewId && it.personId == null && it.effectiveFrom <= date && !it.deleted }
            .maxByOrNull { it.effectiveFrom }

        return crewRate?.dailyWage ?: 0L
    }

    /**
     * Calculates full attendance wages and person breakdown (Decisions 15-17, 191, 192, 305).
     */
    fun calculateAttendanceWage(
        attendance: AttendanceEntity,
        crew: CrewEntity,
        wageRates: List<WageRateEntity>,
        personsMap: Map<String, PersonEntity>,
        salaryRates: List<SalaryRateEntity> = emptyList()
    ): CalculatedAttendance {
        val crewBaseWage = getEffectiveDailyWage(wageRates, crew.id, null, attendance.date, salaryRates)

        if (crew.kind == "count") {
            val headcount = attendance.headcount ?: 0
            val countTicks = (attendance.countTicks ?: 1).coerceAtLeast(1)
            val countTotal = headcount.toLong() * countTicks * crewBaseWage
            val countUnits = headcount * countTicks

            var foremanResult: CalculatedForeman? = null
            var foremanTotal = 0L
            var foremanUnits = 0

            if (!attendance.foreman.isNullOrBlank()) {
                try {
                    val fObj = JSONObject(attendance.foreman)
                    val fPersonId = fObj.optString("personId", "")
                    if (fPersonId.isNotEmpty()) {
                        val fTicks = fObj.optInt("ticks", countTicks).coerceAtLeast(1)
                        val fPresent = fObj.optBoolean("present", true)
                        val fCoef = if (fPresent) attendance.coefPresent else attendance.coefAbsent
                        foremanTotal = fTicks.toLong() * fCoef * crewBaseWage
                        foremanUnits = fTicks * fCoef
                        foremanResult = CalculatedForeman(
                            personId = fPersonId,
                            personName = personsMap[fPersonId]?.name ?: "سرکارگر",
                            ticks = fTicks,
                            present = fPresent,
                            coef = fCoef,
                            dailyWage = crewBaseWage,
                            totalWage = foremanTotal,
                            wageUnits = foremanUnits
                        )
                    }
                } catch (_: Exception) {}
            }

            val totalAmount = NumberFormatHelper.roundToThousand(countTotal + foremanTotal)
            val totalPersons = headcount + (if (foremanResult != null && foremanResult.present) 1 else 0)
            val totalWageUnits = countUnits + foremanUnits

            return CalculatedAttendance(
                totalPersons = totalPersons,
                totalWageUnits = totalWageUnits,
                totalAmount = totalAmount,
                memberLines = emptyList(),
                foreman = foremanResult,
                headcount = headcount,
                countTicks = countTicks,
                crewDailyWage = crewBaseWage
            )
        } else {
            // Kind == "members"
            val linesResult = mutableListOf<CalculatedAttendanceLine>()
            var memberTotal = 0L
            var memberUnits = 0

            try {
                val arr = JSONArray(attendance.lines)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val personId = obj.getString("personId")
                    val ticks = obj.optInt("ticks", 1).coerceAtLeast(1)
                    val wage = getEffectiveDailyWage(wageRates, crew.id, personId, attendance.date, salaryRates)
                    val lineTotal = ticks.toLong() * wage
                    memberTotal += lineTotal
                    memberUnits += ticks
                    linesResult.add(
                        CalculatedAttendanceLine(
                            personId = personId,
                            personName = personsMap[personId]?.name ?: "کارگر",
                            ticks = ticks,
                            dailyWage = wage,
                            totalWage = lineTotal,
                            wageUnits = ticks
                        )
                    )
                }
            } catch (_: Exception) {}

            var foremanResult: CalculatedForeman? = null
            var foremanTotal = 0L
            var foremanUnits = 0

            if (!attendance.foreman.isNullOrBlank()) {
                try {
                    val fObj = JSONObject(attendance.foreman)
                    val fPersonId = fObj.optString("personId", "")
                    if (fPersonId.isNotEmpty()) {
                        val maxMemberTicks = linesResult.maxOfOrNull { it.ticks } ?: 1
                        val fTicks = fObj.optInt("ticks", maxMemberTicks).coerceAtLeast(1)
                        val fPresent = fObj.optBoolean("present", true)
                        val fCoef = if (fPresent) attendance.coefPresent else attendance.coefAbsent
                        foremanTotal = fTicks.toLong() * fCoef * crewBaseWage
                        foremanUnits = fTicks * fCoef
                        foremanResult = CalculatedForeman(
                            personId = fPersonId,
                            personName = personsMap[fPersonId]?.name ?: "سرکارگر",
                            ticks = fTicks,
                            present = fPresent,
                            coef = fCoef,
                            dailyWage = crewBaseWage,
                            totalWage = foremanTotal,
                            wageUnits = foremanUnits
                        )
                    }
                } catch (_: Exception) {}
            }

            val totalAmount = NumberFormatHelper.roundToThousand(memberTotal + foremanTotal)
            val totalPersons = linesResult.size + (if (foremanResult != null && foremanResult.present) 1 else 0)
            val totalWageUnits = memberUnits + foremanUnits

            return CalculatedAttendance(
                totalPersons = totalPersons,
                totalWageUnits = totalWageUnits,
                totalAmount = totalAmount,
                memberLines = linesResult,
                foreman = foremanResult,
                crewDailyWage = crewBaseWage
            )
        }
    }

    /**
     * Calculates payment distribution shares for crew members (Decision 286).
     * 1. Positive claims calculated for active members and foreman.
     * 2. If amount <= total claims: proportional to claims.
     * 3. If amount > total claims: each gets full claim, remainder divided equally.
     * 4. If no claims: divided equally.
     * 5. Rounding to thousands and remainder difference on last share.
     */
    fun calculateCrewPaymentShares(
        totalAmount: Long,
        members: List<PersonEntity>,
        foreman: PersonEntity?,
        memberBalances: Map<String, Long>
    ): List<PaymentShare> {
        val allPersons = (members + listOfNotNull(foreman)).distinctBy { it.id }
        if (allPersons.isEmpty() || totalAmount <= 0) return emptyList()

        val positiveClaims = allPersons.map { p ->
            p to (memberBalances[p.id] ?: 0L).coerceAtLeast(0L)
        }
        val sumClaims = positiveClaims.sumOf { it.second }

        val shares = mutableListOf<Pair<PersonEntity, Long>>()

        if (sumClaims > 0 && totalAmount <= sumClaims) {
            // Proportional distribution
            for ((person, claim) in positiveClaims) {
                val rawShare = (claim.toDouble() / sumClaims * totalAmount).toLong()
                shares.add(person to NumberFormatHelper.roundToThousand(rawShare))
            }
        } else if (sumClaims > 0 && totalAmount > sumClaims) {
            // Everyone gets their full claim, remainder split equally
            val remainder = totalAmount - sumClaims
            val n = allPersons.size
            val equalPart = NumberFormatHelper.roundToThousand(remainder / n)
            for ((person, claim) in positiveClaims) {
                shares.add(person to (claim + equalPart))
            }
        } else {
            // Nobody has claims: equal division
            val n = allPersons.size
            val equalPart = NumberFormatHelper.roundToThousand(totalAmount / n)
            for (person in allPersons) {
                shares.add(person to equalPart)
            }
        }

        // Fix discrepancy on last item to ensure sum(shares) == totalAmount
        val currentSum = shares.sumOf { it.second }
        val diff = totalAmount - currentSum
        if (shares.isNotEmpty()) {
            val last = shares.last()
            shares[shares.size - 1] = last.first to (last.second + diff)
        }

        return shares.map { PaymentShare("person", it.first.id, it.first.name, it.second) }
    }

    /**
     * Builds all display ledger rows for an account (Person or Count Crew).
     * Includes automatic rows from Attendance, PieceWork, Payment shares, and manual Ledger Entries.
     * Computes running balance across non-voided rows.
     */
    fun buildAccountLedger(
        accountType: String, // "person" | "crew"
        accountId: String,
        attendances: List<AttendanceEntity>,
        pieceWorks: List<PieceWorkEntity>,
        payments: List<PaymentEntity>,
        ledgerEntries: List<LedgerEntryEntity>,
        wageRates: List<WageRateEntity>,
        crewsMap: Map<String, CrewEntity>,
        tagsMap: Map<String, TagEntity>,
        personsMap: Map<String, PersonEntity>,
        expenses: List<ExpenseEntity> = emptyList(),
        expenseTypesMap: Map<String, ExpenseTypeEntity> = emptyMap(),
        salaryRates: List<SalaryRateEntity> = emptyList()
    ): List<DisplayLedgerRow> {
        val rows = mutableListOf<DisplayLedgerRow>()

        if (accountType == "person") {
            val manualDatesForPerson = attendances.filter { att ->
                !att.deleted && !att.note.contains("خودکار") && !att.note.contains("اتوماتیک") && !att.createdBy.startsWith("auto") &&
                try {
                    val arr = org.json.JSONArray(att.lines)
                    (0 until arr.length()).any { arr.getJSONObject(it).optString("personId") == accountId }
                } catch (_: Exception) { false }
            }.map { it.date }.toSet()

            // 1. Attendances for this person
            for (att in attendances) {
                val isAuto = att.note.contains("خودکار") || att.note.contains("اتوماتیک") || att.createdBy.startsWith("auto")
                if (isAuto && manualDatesForPerson.contains(att.date)) {
                    continue // Manual attendance replaces automatic attendance on the same date
                }

                val crew = crewsMap[att.crewId] ?: continue
                val tag = tagsMap[att.tagId]
                val tagName = tag?.name ?: "فعالیت"
                val calc = calculateAttendanceWage(att, crew, wageRates, personsMap, salaryRates)

                // Member participation
                val memberLine = calc.memberLines.find { it.personId == accountId }
                if (memberLine != null) {
                    val wageUnitsDesc = if (memberLine.wageUnits > 1) " – ${NumberFormatHelper.toPersianDigits(memberLine.wageUnits)} مزد" else ""
                    val desc = "حضور – ${crew.name} – $tagName$wageUnitsDesc"
                    rows.add(
                        DisplayLedgerRow(
                            id = "att_mem_${att.id}_$accountId",
                            date = att.date,
                            description = desc,
                            debit = 0L,
                            credit = memberLine.totalWage,
                            isAuto = true,
                            sourceKind = "attendance",
                            sourceId = att.id,
                            isVoided = att.deleted,
                            voidReason = if (att.deleted) "باطل" else null,
                            isEdited = att.updatedAt != att.createdAt,
                            createdAt = att.createdAt
                        )
                    )
                }

                // Foreman participation (in crew with members)
                if (crew.kind == "members" && calc.foreman != null && calc.foreman.personId == accountId) {
                    val wageUnitsDesc = " – ${NumberFormatHelper.toPersianDigits(calc.foreman.wageUnits)} مزد"
                    val desc = "حضور (سرکارگر) – ${crew.name} – $tagName$wageUnitsDesc"
                    rows.add(
                        DisplayLedgerRow(
                            id = "att_for_${att.id}_$accountId",
                            date = att.date,
                            description = desc,
                            debit = 0L,
                            credit = calc.foreman.totalWage,
                            isAuto = true,
                            sourceKind = "attendance",
                            sourceId = att.id,
                            isVoided = att.deleted,
                            voidReason = if (att.deleted) "باطل" else null,
                            isEdited = att.updatedAt != att.createdAt,
                            createdAt = att.createdAt
                        )
                    )
                }
            }

            // 2. PieceWork
            for (pw in pieceWorks) {
                try {
                    val arr = JSONArray(pw.lines)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val pId = obj.getString("personId")
                        if (pId == accountId) {
                            val count = obj.optInt("count", 0)
                            val earned = count.toLong() * pw.rate
                            val desc = "کار دانه‌ای – ${pw.jobType} ${pw.size} – ${NumberFormatHelper.toPersianDigits(count)} عدد"
                            rows.add(
                                DisplayLedgerRow(
                                    id = "pw_${pw.id}_$accountId",
                                    date = pw.date,
                                    description = desc,
                                    debit = 0L,
                                    credit = earned,
                                    isAuto = true,
                                    sourceKind = "piecework",
                                    sourceId = pw.id,
                                    isVoided = pw.deleted,
                                    voidReason = if (pw.deleted) "باطل" else null,
                                    isEdited = pw.updatedAt != pw.createdAt,
                                    createdAt = pw.createdAt
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            // 3. Payment shares
            for (p in payments) {
                try {
                    val arr = JSONArray(p.shares)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val sType = obj.optString("accountType", "person")
                        val sId = obj.getString("accountId")
                        if (sType == "person" && sId == accountId) {
                            val amount = obj.getLong("amount")
                            val methodDesc = when (p.method) {
                                "cash" -> " (نقد)"
                                "card" -> " (کارت)"
                                else -> ""
                            }
                            val noteDesc = if (p.note.isNotBlank()) " – ${p.note}" else ""
                            val desc = "پرداخت$methodDesc$noteDesc"
                            rows.add(
                                DisplayLedgerRow(
                                    id = "pay_${p.id}_$accountId",
                                    date = p.date,
                                    description = desc,
                                    debit = amount,
                                    credit = 0L,
                                    isAuto = true,
                                    sourceKind = "payment",
                                    sourceId = p.id,
                                    isVoided = p.deleted,
                                    voidReason = if (p.deleted) "باطل" else null,
                                    isEdited = p.updatedAt != p.createdAt,
                                    createdAt = p.createdAt
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            // 4. Manual Ledger entries
            for (entry in ledgerEntries) {
                if (entry.accountType == "person" && entry.accountId == accountId && !entry.deleted) {
                    val (debit, credit, prefix) = when (entry.kind) {
                        "payTo" -> Triple(entry.amount, 0L, "پرداخت")
                        "receiveFrom" -> Triple(0L, entry.amount, "دریافت")
                        "credit" -> Triple(0L, entry.amount, "طلب او")
                        "debit" -> Triple(entry.amount, 0L, "بدهی او")
                        "openingCredit" -> Triple(0L, entry.amount, "مانده‌ی قبلی: طلب او")
                        "openingDebit" -> Triple(entry.amount, 0L, "مانده‌ی قبلی: بدهی او")
                        else -> Triple(0L, 0L, "ردیف دستی")
                    }
                    val desc = if (entry.note.isNotBlank()) "$prefix – ${entry.note}" else prefix
                    rows.add(
                        DisplayLedgerRow(
                            id = entry.id,
                            date = entry.date,
                            description = desc,
                            debit = debit,
                            credit = credit,
                            isAuto = false,
                            sourceKind = "manual",
                            sourceId = entry.id,
                            isVoided = entry.voided,
                            voidReason = entry.voidReason ?: if (entry.voided) "باطل" else null,
                            isEdited = entry.updatedAt != entry.createdAt,
                            createdAt = entry.createdAt
                        )
                    )
                }
            }

            // 5. Expenses for this person (credit / partial / salary)
            for (exp in expenses) {
                if (exp.counterpartyId == accountId) {
                    val expType = expenseTypesMap[exp.typeId]
                    val typeName = expType?.name ?: "هزینه"
                    var debit = 0L
                    var credit = 0L
                    var desc = ""

                    if (exp.systemKind == "salary") {
                        credit = exp.amount
                        desc = if (exp.note.isNotBlank()) exp.note else "حقوق کارگر"
                    } else if (exp.systemKind == "salaryCorrection") {
                        if (exp.amount >= 0) {
                            credit = exp.amount
                            desc = if (exp.note.isNotBlank()) exp.note else "اصلاح حقوق"
                        } else {
                            debit = -exp.amount
                            desc = if (exp.note.isNotBlank()) exp.note else "اصلاح حقوق (کسر)"
                        }
                    } else if (exp.payMode == "credit") {
                        credit = exp.amount
                        desc = "هزینه – $typeName – نسیه"
                    } else if (exp.payMode == "partial") {
                        val remaining = exp.amount - (exp.paidAmount ?: 0L)
                        if (remaining > 0) {
                            credit = remaining
                            desc = "هزینه – $typeName – بخشی نسیه"
                        }
                    }

                    if (credit > 0 || debit > 0) {
                        rows.add(
                            DisplayLedgerRow(
                                id = "exp_${exp.id}_$accountId",
                                date = exp.date,
                                description = desc,
                                debit = debit,
                                credit = credit,
                                isAuto = true,
                                sourceKind = "expense",
                                sourceId = exp.id,
                                isVoided = exp.deleted,
                                voidReason = if (exp.deleted) "باطل" else null,
                                isEdited = exp.updatedAt != exp.createdAt,
                                createdAt = exp.createdAt
                            )
                        )
                    }
                }
            }
        } else {
            // accountType == "crew" (Count Crew)
            val crew = crewsMap[accountId]
            if (crew != null) {
                for (att in attendances) {
                    if (att.crewId == accountId) {
                        val tag = tagsMap[att.tagId]
                        val tagName = tag?.name ?: "فعالیت"
                        val calc = calculateAttendanceWage(att, crew, wageRates, personsMap)

                        // Main headcount credit
                        val headcount = att.headcount ?: 0
                        val countTicks = att.countTicks ?: 1
                        val crewWage = calc.crewDailyWage ?: 0L
                        val countTotal = headcount.toLong() * countTicks * crewWage
                        val countTicksDesc = if (countTicks > 1) " – ${NumberFormatHelper.toPersianDigits(countTicks)} تیک" else ""
                        val desc = "حضور – ${crew.name} – $tagName – ${NumberFormatHelper.toPersianDigits(headcount)} نفر$countTicksDesc"

                        rows.add(
                            DisplayLedgerRow(
                                id = "att_crew_${att.id}",
                                date = att.date,
                                description = desc,
                                debit = 0L,
                                credit = countTotal,
                                isAuto = true,
                                sourceKind = "attendance",
                                sourceId = att.id,
                                isVoided = att.deleted,
                                voidReason = if (att.deleted) "باطل" else null,
                                isEdited = att.updatedAt != att.createdAt,
                                createdAt = att.createdAt
                            )
                        )

                        // Foreman for count crew also credited to crew account (Section 4 spec)
                        if (calc.foreman != null) {
                            val fDesc = "حضور (سرکارگر) – ${crew.name} – $tagName"
                            rows.add(
                                DisplayLedgerRow(
                                    id = "att_crew_for_${att.id}",
                                    date = att.date,
                                    description = fDesc,
                                    debit = 0L,
                                    credit = calc.foreman.totalWage,
                                    isAuto = true,
                                    sourceKind = "attendance",
                                    sourceId = att.id,
                                    isVoided = att.deleted,
                                    voidReason = if (att.deleted) "باطل" else null,
                                    isEdited = att.updatedAt != att.createdAt,
                                    createdAt = att.createdAt
                                )
                            )
                        }
                    }
                }

                // Payment shares for crew account
                for (p in payments) {
                    try {
                        val arr = JSONArray(p.shares)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val sType = obj.optString("accountType", "crew")
                            val sId = obj.getString("accountId")
                            if (sType == "crew" && sId == accountId) {
                                val amount = obj.getLong("amount")
                                val methodDesc = when (p.method) {
                                    "cash" -> " (نقد)"
                                    "card" -> " (کارت)"
                                    else -> ""
                                }
                                val noteDesc = if (p.note.isNotBlank()) " – ${p.note}" else ""
                                val desc = "پرداخت$methodDesc$noteDesc"
                                rows.add(
                                    DisplayLedgerRow(
                                        id = "pay_crew_${p.id}",
                                        date = p.date,
                                        description = desc,
                                        debit = amount,
                                        credit = 0L,
                                        isAuto = true,
                                        sourceKind = "payment",
                                        sourceId = p.id,
                                        isVoided = p.deleted,
                                        voidReason = if (p.deleted) "باطل" else null,
                                        isEdited = p.updatedAt != p.createdAt,
                                        createdAt = p.createdAt
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Manual ledger entries for crew
                for (entry in ledgerEntries) {
                    if (entry.accountType == "crew" && entry.accountId == accountId && !entry.deleted) {
                        val (debit, credit, prefix) = when (entry.kind) {
                            "payTo" -> Triple(entry.amount, 0L, "پرداخت")
                            "receiveFrom" -> Triple(0L, entry.amount, "دریافت")
                            "credit" -> Triple(0L, entry.amount, "طلب دسته")
                            "debit" -> Triple(entry.amount, 0L, "بدهی دسته")
                            "openingCredit" -> Triple(0L, entry.amount, "مانده‌ی قبلی: طلب دسته")
                            "openingDebit" -> Triple(entry.amount, 0L, "مانده‌ی قبلی: بدهی دسته")
                            else -> Triple(0L, 0L, "ردیف دستی")
                        }
                        val desc = if (entry.note.isNotBlank()) "$prefix – ${entry.note}" else prefix
                        rows.add(
                            DisplayLedgerRow(
                                id = entry.id,
                                date = entry.date,
                                description = desc,
                                debit = debit,
                                credit = credit,
                                isAuto = false,
                                sourceKind = "manual",
                                sourceId = entry.id,
                                isVoided = entry.voided,
                                voidReason = entry.voidReason ?: if (entry.voided) "باطل" else null,
                                isEdited = entry.updatedAt != entry.createdAt,
                                createdAt = entry.createdAt
                            )
                        )
                    }
                }
            }
        }

        // Sort rows chronologically by date ASC, then createdAt ASC
        val sorted = rows.sortedWith(compareBy({ it.date }, { it.createdAt }))

        var running = 0L
        for (r in sorted) {
            if (!r.isVoided) {
                running += (r.credit - r.debit)
            }
            r.runningBalance = running
        }

        return sorted
    }

    /**
     * Calculates the overall balance of an account (Credit - Debit of all non-voided rows).
     * Positive = Claim (طلب او), Negative = Debt (بدهی او), Zero = Settled (تسویه).
     */
    fun calculateAccountBalance(
        accountType: String,
        accountId: String,
        attendances: List<AttendanceEntity>,
        pieceWorks: List<PieceWorkEntity>,
        payments: List<PaymentEntity>,
        ledgerEntries: List<LedgerEntryEntity>,
        wageRates: List<WageRateEntity>,
        crewsMap: Map<String, CrewEntity>,
        tagsMap: Map<String, TagEntity>,
        personsMap: Map<String, PersonEntity>,
        expenses: List<ExpenseEntity> = emptyList(),
        expenseTypesMap: Map<String, ExpenseTypeEntity> = emptyMap(),
        salaryRates: List<SalaryRateEntity> = emptyList()
    ): Long {
        val ledger = buildAccountLedger(
            accountType, accountId, attendances, pieceWorks, payments, ledgerEntries,
            wageRates, crewsMap, tagsMap, personsMap, expenses, expenseTypesMap, salaryRates
        )
        return ledger.lastOrNull()?.runningBalance ?: 0L
    }
}
