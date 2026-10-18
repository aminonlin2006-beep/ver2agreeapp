package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(tableName = "farms")
data class FarmEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val areaM2: Double? = null,
    val ownership: String? = null, // "owned", "rented", "mixed"
    val waterSource: String? = null, // "well", "canal", "both"
    val note: String = "",
    val active: Boolean = true,
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "plots")
data class PlotEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val farmId: String,
    val name: String,
    val workType: String = "zeraat", // "zeraat" | "nahalestan"
    val areaM2: Double? = null,
    val note: String = "",
    val active: Boolean = true,
    val isDefault: Boolean = false, // Hidden default plot
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String = "",
    val roleIds: String = "[]", // JSON array of role IDs
    val note: String = "",
    val active: Boolean = true,
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "roles")
data class RoleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val builtInKey: String? = null, // "worker" | "foreman" | "buyer" | "seller"
    val hidden: Boolean = false,
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val parentId: String? = null, // null for stage, non-null for activity
    val name: String,
    val color: String = "#2E7D32",
    val hidden: Boolean = false,
    val builtInKey: String? = null, // "kasht" | "dasht" | "bardasht" | "sayer"
    val sortOrder: Int = 0,
    val usageCount: Int = 0,
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "snapshots")
data class SnapshotEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String, // Jalali date e.g. "1405-07-26"
    val createdAt: String = Instant.now().toString(),
    val summary: String, // e.g. "۱۲ مزرعه، ۳۰ شخص"
    val data: String // full json string
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entityType: String,
    val entityId: String,
    val at: String = Instant.now().toString(),
    val changes: String // JSON array of field changes
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val farmId: String? = null,
    val plotId: String? = null,
    val startDate: String, // ISO date "YYYY-MM-DD"
    val endDate: String,   // ISO date "YYYY-MM-DD"
    val priority: String = "normal", // "urgent" | "important" | "normal"
    val assigneePersonIds: String = "[]", // JSON array of person IDs
    val assigneeCrewId: String? = null, // Optional crew assignee (either personIds or crewId; Decision 173)
    val tagId: String? = null,
    val cropId: String? = null,
    val note: String = "",
    val status: String = "open", // "open" | "done" | "cancelled"
    val closedAt: String? = null,
    val rescheduleCount: Int = 0,
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "crews")
data class CrewEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: String = "members", // "members" | "count"
    val foremanId: String? = null,
    val note: String = "",
    val active: Boolean = true,
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "crew_members")
data class CrewMemberEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val crewId: String,
    val personId: String,
    val active: Boolean = true,
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "wage_rates")
data class WageRateEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val crewId: String,
    val personId: String? = null, // null = crew base wage
    val dailyWage: Long, // in Tomans
    val effectiveFrom: String, // ISO date "YYYY-MM-DD"
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "attendance")
data class AttendanceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String, // ISO date "YYYY-MM-DD"
    val farmId: String? = null,
    val plotId: String? = null,
    val crewId: String,
    val tagId: String, // mandatory
    val cropId: String? = null,
    val lines: String = "[]", // JSON array: [{"personId": "...", "ticks": 1}]
    val headcount: Int? = null, // for kind = count
    val countTicks: Int? = null, // for kind = count
    val foreman: String? = null, // JSON object: {"personId": "...", "ticks": 1, "present": true}
    val coefPresent: Int = 2,
    val coefAbsent: Int = 1,
    val note: String = "",
    val extraFields: String = "[]",
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "my_counts")
data class MyCountEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String, // ISO date "YYYY-MM-DD"
    val crewId: String,
    val headcount: Int,
    val note: String = "",
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "piece_work")
data class PieceWorkEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String, // ISO date "YYYY-MM-DD"
    val farmId: String? = null,
    val plotId: String? = null,
    val tagId: String,
    val cropId: String? = null,
    val jobType: String,
    val size: String,
    val rate: Long,
    val lines: String = "[]", // JSON array: [{"personId": "...", "count": 100}]
    val note: String = "",
    val extraFields: String = "[]",
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "ledger_entries")
data class LedgerEntryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val accountType: String, // "person" | "crew"
    val accountId: String,
    val date: String, // ISO date "YYYY-MM-DD"
    val kind: String, // "payTo" | "receiveFrom" | "credit" | "debit" | "openingCredit" | "openingDebit"
    val amount: Long,
    val note: String = "",
    val voided: Boolean = false,
    val voidReason: String? = null,
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String, // ISO date "YYYY-MM-DD"
    val amount: Long,
    val handedToPersonId: String? = null,
    val targetType: String, // "person" | "crew"
    val targetId: String,
    val method: String? = null, // "cash" | "card" | null
    val note: String = "",
    val shares: String = "[]", // JSON array: [{"accountType": "person", "accountId": "...", "amount": 1000000}]
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "waiting_list")
data class WaitingListEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val accountType: String, // "person" | "crew"
    val accountId: String,
    val amount: Long? = null,
    val date: String, // ISO date "YYYY-MM-DD"
    val note: String = "",
    val status: String = "open", // "open" | "done"
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

// -------------------------------------------------------------
// STEP 4 ENTITIES (Expenses, Fixed Worker & Salkar)
// -------------------------------------------------------------

@Entity(tableName = "expense_types")
data class ExpenseTypeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val builtInKey: String? = null, // e.g. "salary"
    val hidden: Boolean = false,
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String, // ISO date "YYYY-MM-DD"
    val amount: Long, // can be negative only for systemKind == "salaryCorrection"
    val typeId: String,
    val farmId: String? = null, // null = "عمومی"
    val plotId: String? = null,
    val workerIds: String = "[]", // JSON array of personIds, or "\"all\""
    val tagId: String? = null,
    val cropId: String? = null,
    val counterpartyId: String? = null, // personId
    val payMode: String = "cash", // "cash" | "credit" | "partial"
    val paidAmount: Long? = null, // for partial
    val note: String = "",
    val extraFields: String = "[]", // JSON array: [{"name": "...", "value": "..."}]
    val systemKind: String? = null, // null | "salary" | "salaryCorrection"
    val salaryRef: String? = null, // e.g. "1405-07"
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "employments")
data class EmploymentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val personId: String,
    val kind: String = "fixed", // "fixed" | "salkar"
    val workStartDate: String? = null, // ISO date "YYYY-MM-DD" (only for info)
    val salaryFromMonth: String, // Jalali month "1405-07" (mandatory, no default)
    val endDate: String? = null, // ISO date "YYYY-MM-DD" (null until end of work)
    val defaultFarmId: String? = null,
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "salary_rates")
data class SalaryRateEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val personId: String,
    val monthlySalary: Long, // in Tomans
    val effectiveFrom: String, // ISO date "YYYY-MM-DD"
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "absences")
data class AbsenceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val personId: String,
    val fromDate: String, // ISO date "YYYY-MM-DD"
    val toDate: String, // ISO date "YYYY-MM-DD"
    val type: String = "unknown", // "paid" | "unpaid" | "unknown"
    val note: String = "",
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "salary_postings")
data class SalaryPostingEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val personId: String,
    val month: String, // Jalali month "1405-07"
    val computedAmount: Long,
    val expenseId: String,
    val postedAt: String = Instant.now().toString(),
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)

@Entity(tableName = "crops")
data class CropEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val plotId: String,
    val startDate: String, // ISO date "YYYY-MM-DD"
    val endDate: String? = null,   // ISO date "YYYY-MM-DD"
    val productIds: String = "[]", // JSON array
    val quantity: Double? = null,
    val size: String? = null,
    val note: String = "",
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val createdBy: String = "owner",
    val deleted: Boolean = false,
    val deletedAt: String? = null
)
