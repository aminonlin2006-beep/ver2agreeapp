package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AppDao
import com.example.data.entity.AbsenceEntity
import com.example.data.entity.AttendanceEntity
import com.example.data.entity.CrewEntity
import com.example.data.entity.CrewMemberEntity
import com.example.data.entity.EmploymentEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.ExpenseTypeEntity
import com.example.data.entity.FarmEntity
import com.example.data.entity.HistoryEntity
import com.example.data.entity.LedgerEntryEntity
import com.example.data.entity.MyCountEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.PersonEntity
import com.example.data.entity.PieceWorkEntity
import com.example.data.entity.PlotEntity
import com.example.data.entity.RoleEntity
import com.example.data.entity.SalaryPostingEntity
import com.example.data.entity.SalaryRateEntity
import com.example.data.entity.SettingEntity
import com.example.data.entity.SnapshotEntity
import com.example.data.entity.TagEntity
import com.example.data.entity.TaskEntity
import com.example.data.entity.WageRateEntity
import com.example.data.entity.WaitingListEntity

@Database(
    entities = [
        FarmEntity::class,
        PlotEntity::class,
        PersonEntity::class,
        RoleEntity::class,
        TagEntity::class,
        SettingEntity::class,
        SnapshotEntity::class,
        HistoryEntity::class,
        TaskEntity::class,
        CrewEntity::class,
        CrewMemberEntity::class,
        WageRateEntity::class,
        AttendanceEntity::class,
        MyCountEntity::class,
        PieceWorkEntity::class,
        LedgerEntryEntity::class,
        PaymentEntity::class,
        WaitingListEntity::class,
        ExpenseTypeEntity::class,
        ExpenseEntity::class,
        EmploymentEntity::class,
        SalaryRateEntity::class,
        AbsenceEntity::class,
        SalaryPostingEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tasks` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `farmId` TEXT,
                        `plotId` TEXT,
                        `startDate` TEXT NOT NULL,
                        `endDate` TEXT NOT NULL,
                        `priority` TEXT NOT NULL,
                        `assigneePersonIds` TEXT NOT NULL,
                        `tagId` TEXT,
                        `note` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `closedAt` TEXT,
                        `rescheduleCount` INTEGER NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        `createdBy` TEXT NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `deletedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `crews` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `foremanId` TEXT,
                        `note` TEXT NOT NULL,
                        `active` INTEGER NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        `createdBy` TEXT NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `deletedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `crew_members` (
                        `id` TEXT NOT NULL,
                        `crewId` TEXT NOT NULL,
                        `personId` TEXT NOT NULL,
                        `active` INTEGER NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        `createdBy` TEXT NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `deletedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `wage_rates` (
                        `id` TEXT NOT NULL,
                        `crewId` TEXT NOT NULL,
                        `personId` TEXT,
                        `dailyWage` INTEGER NOT NULL,
                        `effectiveFrom` TEXT NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        `createdBy` TEXT NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `deletedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `attendance` (
                        `id` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `farmId` TEXT,
                        `plotId` TEXT,
                        `crewId` TEXT NOT NULL,
                        `tagId` TEXT NOT NULL,
                        `lines` TEXT NOT NULL,
                        `headcount` INTEGER,
                        `countTicks` INTEGER,
                        `foreman` TEXT,
                        `coefPresent` INTEGER NOT NULL,
                        `coefAbsent` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        `createdBy` TEXT NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `deletedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `my_counts` (
                        `id` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `crewId` TEXT NOT NULL,
                        `headcount` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        `createdBy` TEXT NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `deletedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `piece_work` (
                        `id` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `farmId` TEXT,
                        `plotId` TEXT,
                        `tagId` TEXT NOT NULL,
                        `jobType` TEXT NOT NULL,
                        `size` TEXT NOT NULL,
                        `rate` INTEGER NOT NULL,
                        `lines` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        `createdBy` TEXT NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `deletedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ledger_entries` (
                        `id` TEXT NOT NULL,
                        `accountType` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `voided` INTEGER NOT NULL,
                        `voidReason` TEXT,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        `createdBy` TEXT NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `deletedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `payments` (
                        `id` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `handedToPersonId` TEXT,
                        `targetType` TEXT NOT NULL,
                        `targetId` TEXT NOT NULL,
                        `method` TEXT,
                        `note` TEXT NOT NULL,
                        `shares` TEXT NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        `createdBy` TEXT NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `deletedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `waiting_list` (
                        `id` TEXT NOT NULL,
                        `accountType` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `amount` INTEGER,
                        `date` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        `createdBy` TEXT NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `deletedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                try {
                    db.execSQL("ALTER TABLE `tasks` ADD COLUMN `assigneeCrewId` TEXT DEFAULT NULL")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `attendance` ADD COLUMN `extraFields` TEXT NOT NULL DEFAULT '[]'")
                } catch (_: Exception) {}

                try {
                    db.execSQL("ALTER TABLE `piece_work` ADD COLUMN `extraFields` TEXT NOT NULL DEFAULT '[]'")
                } catch (_: Exception) {}

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `expense_types` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `builtInKey` TEXT,
                        `hidden` INTEGER NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        `createdBy` TEXT NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `deletedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `expenses` (
                        `id` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `typeId` TEXT NOT NULL,
                        `farmId` TEXT,
                        `plotId` TEXT,
                        `workerIds` TEXT NOT NULL,
                        `tagId` TEXT,
                        `counterpartyId` TEXT,
                        `payMode` TEXT NOT NULL,
                        `paidAmount` INTEGER,
                        `note` TEXT NOT NULL,
                        `extraFields` TEXT NOT NULL,
                        `systemKind` TEXT,
                        `salaryRef` TEXT,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        `createdBy` TEXT NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `deletedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `employments` (
                        `id` TEXT NOT NULL,
                        `personId` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `workStartDate` TEXT,
                        `salaryFromMonth` TEXT NOT NULL,
                        `endDate` TEXT,
                        `defaultFarmId` TEXT,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        `createdBy` TEXT NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `deletedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `salary_rates` (
                        `id` TEXT NOT NULL,
                        `personId` TEXT NOT NULL,
                        `monthlySalary` INTEGER NOT NULL,
                        `effectiveFrom` TEXT NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        `createdBy` TEXT NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `deletedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `absences` (
                        `id` TEXT NOT NULL,
                        `personId` TEXT NOT NULL,
                        `fromDate` TEXT NOT NULL,
                        `toDate` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        `createdBy` TEXT NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `deletedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `salary_postings` (
                        `id` TEXT NOT NULL,
                        `personId` TEXT NOT NULL,
                        `month` TEXT NOT NULL,
                        `computedAmount` INTEGER NOT NULL,
                        `expenseId` TEXT NOT NULL,
                        `postedAt` TEXT NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        `createdBy` TEXT NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `deletedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "farm_manager_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
