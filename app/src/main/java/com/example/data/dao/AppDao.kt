package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
import com.example.data.entity.ProductEntity
import com.example.data.entity.CropEntity
import com.example.data.entity.SalaryRateEntity
import com.example.data.entity.SettingEntity
import com.example.data.entity.SnapshotEntity
import com.example.data.entity.TagEntity
import com.example.data.entity.TaskEntity
import com.example.data.entity.WageRateEntity
import com.example.data.entity.WaitingListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // --- FARMS ---
    @Query("SELECT * FROM farms WHERE deleted = 0 ORDER BY createdAt DESC")
    fun getAllFarms(): Flow<List<FarmEntity>>

    @Query("SELECT * FROM farms WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getFarmById(id: String): FarmEntity?

    @Query("SELECT * FROM farms WHERE deleted = 0")
    suspend fun getAllFarmsList(): List<FarmEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFarm(farm: FarmEntity)

    @Update
    suspend fun updateFarm(farm: FarmEntity)

    // --- PLOTS ---
    @Query("SELECT * FROM plots WHERE deleted = 0 ORDER BY isDefault ASC, createdAt ASC")
    fun getAllPlots(): Flow<List<PlotEntity>>

    @Query("SELECT * FROM plots WHERE farmId = :farmId AND deleted = 0 ORDER BY isDefault ASC, createdAt ASC")
    fun getPlotsForFarm(farmId: String): Flow<List<PlotEntity>>

    @Query("SELECT * FROM plots WHERE farmId = :farmId AND deleted = 0")
    suspend fun getPlotsForFarmList(farmId: String): List<PlotEntity>

    @Query("SELECT * FROM plots WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getPlotById(id: String): PlotEntity?

    @Query("SELECT * FROM plots WHERE deleted = 0")
    suspend fun getAllPlotsList(): List<PlotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlot(plot: PlotEntity)

    @Update
    suspend fun updatePlot(plot: PlotEntity)

    // --- PERSONS ---
    @Query("SELECT * FROM persons WHERE deleted = 0 ORDER BY name ASC")
    fun getAllPersons(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons WHERE deleted = 0")
    suspend fun getAllPersonsList(): List<PersonEntity>

    @Query("SELECT * FROM persons WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getPersonById(id: String): PersonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity)

    @Update
    suspend fun updatePerson(person: PersonEntity)

    // --- ROLES ---
    @Query("SELECT * FROM roles WHERE deleted = 0 ORDER BY createdAt ASC")
    fun getAllRoles(): Flow<List<RoleEntity>>

    @Query("SELECT * FROM roles WHERE deleted = 0")
    suspend fun getAllRolesList(): List<RoleEntity>

    @Query("SELECT * FROM roles WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getRoleById(id: String): RoleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRole(role: RoleEntity)

    @Update
    suspend fun updateRole(role: RoleEntity)

    // --- TAGS ---
    @Query("SELECT * FROM tags WHERE deleted = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE deleted = 0")
    suspend fun getAllTagsList(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getTagById(id: String): TagEntity?

    @Query("SELECT * FROM tags WHERE parentId = :parentId AND deleted = 0 ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getActivitiesForStage(parentId: String): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity)

    @Update
    suspend fun updateTag(tag: TagEntity)

    // --- SETTINGS ---
    @Query("SELECT * FROM settings")
    fun getAllSettings(): Flow<List<SettingEntity>>

    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: SettingEntity)

    // --- SNAPSHOTS ---
    @Query("SELECT * FROM snapshots ORDER BY createdAt DESC LIMIT 7")
    fun getRecentSnapshots(): Flow<List<SnapshotEntity>>

    @Query("SELECT * FROM snapshots ORDER BY createdAt DESC")
    suspend fun getAllSnapshotsList(): List<SnapshotEntity>

    @Query("SELECT * FROM snapshots WHERE date = :date LIMIT 1")
    suspend fun getSnapshotByDate(date: String): SnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: SnapshotEntity)

    @Query("DELETE FROM snapshots WHERE id NOT IN (SELECT id FROM snapshots ORDER BY createdAt DESC LIMIT 7)")
    suspend fun pruneSnapshots()

    // --- HISTORY ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    // --- BULK OPERATIONS FOR RESTORE / CLEAR ---
    @Query("DELETE FROM farms")
    suspend fun clearFarms()

    @Query("DELETE FROM plots")
    suspend fun clearPlots()

    @Query("DELETE FROM persons")
    suspend fun clearPersons()

    @Query("DELETE FROM roles")
    suspend fun clearRoles()

    @Query("DELETE FROM tags")
    suspend fun clearTags()

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFarms(farms: List<FarmEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlots(plots: List<PlotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersons(persons: List<PersonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoles(roles: List<RoleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<TagEntity>)

    // --- TASKS ---
    @Query("SELECT * FROM tasks WHERE deleted = 0 ORDER BY createdAt ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deleted = 0")
    suspend fun getAllTasksList(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getTaskById(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    // --- CREWS ---
    @Query("SELECT * FROM crews WHERE deleted = 0 ORDER BY active DESC, createdAt ASC")
    fun getAllCrews(): Flow<List<CrewEntity>>

    @Query("SELECT * FROM crews WHERE deleted = 0")
    suspend fun getAllCrewsList(): List<CrewEntity>

    @Query("SELECT * FROM crews WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getCrewById(id: String): CrewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrew(crew: CrewEntity)

    @Update
    suspend fun updateCrew(crew: CrewEntity)

    @Query("DELETE FROM crews")
    suspend fun clearCrews()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrews(crews: List<CrewEntity>)

    // --- CREW MEMBERS ---
    @Query("SELECT * FROM crew_members WHERE deleted = 0")
    fun getAllCrewMembers(): Flow<List<CrewMemberEntity>>

    @Query("SELECT * FROM crew_members WHERE crewId = :crewId AND deleted = 0")
    fun getCrewMembersForCrew(crewId: String): Flow<List<CrewMemberEntity>>

    @Query("SELECT * FROM crew_members WHERE deleted = 0")
    suspend fun getAllCrewMembersList(): List<CrewMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrewMember(crewMember: CrewMemberEntity)

    @Update
    suspend fun updateCrewMember(crewMember: CrewMemberEntity)

    @Query("DELETE FROM crew_members")
    suspend fun clearCrewMembers()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrewMembers(crewMembers: List<CrewMemberEntity>)

    // --- WAGE RATES ---
    @Query("SELECT * FROM wage_rates WHERE deleted = 0 ORDER BY effectiveFrom ASC")
    fun getAllWageRates(): Flow<List<WageRateEntity>>

    @Query("SELECT * FROM wage_rates WHERE deleted = 0")
    suspend fun getAllWageRatesList(): List<WageRateEntity>

    @Query("SELECT * FROM wage_rates WHERE crewId = :crewId AND deleted = 0 ORDER BY effectiveFrom ASC")
    fun getWageRatesForCrew(crewId: String): Flow<List<WageRateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWageRate(wageRate: WageRateEntity)

    @Update
    suspend fun updateWageRate(wageRate: WageRateEntity)

    @Query("DELETE FROM wage_rates")
    suspend fun clearWageRates()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWageRates(wageRates: List<WageRateEntity>)

    // --- ATTENDANCE ---
    @Query("SELECT * FROM attendance WHERE deleted = 0 ORDER BY date DESC, createdAt DESC")
    fun getAllAttendance(): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE deleted = 0")
    suspend fun getAllAttendanceList(): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getAttendanceById(id: String): AttendanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(attendance: AttendanceEntity)

    @Update
    suspend fun updateAttendance(attendance: AttendanceEntity)

    @Query("DELETE FROM attendance")
    suspend fun clearAttendance()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendances(attendances: List<AttendanceEntity>)

    // --- MY COUNTS ---
    @Query("SELECT * FROM my_counts WHERE deleted = 0 ORDER BY date DESC")
    fun getAllMyCounts(): Flow<List<MyCountEntity>>

    @Query("SELECT * FROM my_counts WHERE deleted = 0")
    suspend fun getAllMyCountsList(): List<MyCountEntity>

    @Query("SELECT * FROM my_counts WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getMyCountById(id: String): MyCountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMyCount(myCount: MyCountEntity)

    @Update
    suspend fun updateMyCount(myCount: MyCountEntity)

    @Query("DELETE FROM my_counts")
    suspend fun clearMyCounts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMyCounts(myCounts: List<MyCountEntity>)

    // --- PIECE WORK ---
    @Query("SELECT * FROM piece_work WHERE deleted = 0 ORDER BY date DESC, createdAt DESC")
    fun getAllPieceWork(): Flow<List<PieceWorkEntity>>

    @Query("SELECT * FROM piece_work WHERE deleted = 0")
    suspend fun getAllPieceWorkList(): List<PieceWorkEntity>

    @Query("SELECT * FROM piece_work WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getPieceWorkById(id: String): PieceWorkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPieceWork(pieceWork: PieceWorkEntity)

    @Update
    suspend fun updatePieceWork(pieceWork: PieceWorkEntity)

    @Query("DELETE FROM piece_work")
    suspend fun clearPieceWork()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPieceWorks(pieceWorks: List<PieceWorkEntity>)

    // --- LEDGER ENTRIES ---
    @Query("SELECT * FROM ledger_entries WHERE deleted = 0 ORDER BY date DESC, createdAt DESC")
    fun getAllLedgerEntries(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries WHERE deleted = 0")
    suspend fun getAllLedgerEntriesList(): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entries WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getLedgerEntryById(id: String): LedgerEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedgerEntry(entry: LedgerEntryEntity)

    @Update
    suspend fun updateLedgerEntry(entry: LedgerEntryEntity)

    @Query("DELETE FROM ledger_entries")
    suspend fun clearLedgerEntries()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedgerEntries(entries: List<LedgerEntryEntity>)

    // --- PAYMENTS ---
    @Query("SELECT * FROM payments WHERE deleted = 0 ORDER BY date DESC, createdAt DESC")
    fun getAllPayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE deleted = 0")
    suspend fun getAllPaymentsList(): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getPaymentById(id: String): PaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity)

    @Update
    suspend fun updatePayment(payment: PaymentEntity)

    @Query("DELETE FROM payments")
    suspend fun clearPayments()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<PaymentEntity>)

    // --- WAITING LIST ---
    @Query("SELECT * FROM waiting_list WHERE deleted = 0 ORDER BY date DESC, createdAt DESC")
    fun getAllWaitingList(): Flow<List<WaitingListEntity>>

    @Query("SELECT * FROM waiting_list WHERE deleted = 0")
    suspend fun getAllWaitingListList(): List<WaitingListEntity>

    @Query("SELECT * FROM waiting_list WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getWaitingListById(id: String): WaitingListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaitingList(item: WaitingListEntity)

    @Update
    suspend fun updateWaitingList(item: WaitingListEntity)

    @Query("DELETE FROM waiting_list")
    suspend fun clearWaitingList()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaitingLists(items: List<WaitingListEntity>)

    // --- EXPENSE TYPES ---
    @Query("SELECT * FROM expense_types WHERE deleted = 0 ORDER BY createdAt ASC")
    fun getAllExpenseTypes(): Flow<List<ExpenseTypeEntity>>

    @Query("SELECT * FROM expense_types WHERE deleted = 0")
    suspend fun getAllExpenseTypesList(): List<ExpenseTypeEntity>

    @Query("SELECT * FROM expense_types WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getExpenseTypeById(id: String): ExpenseTypeEntity?

    @Query("SELECT * FROM expense_types WHERE builtInKey = :builtInKey AND deleted = 0 LIMIT 1")
    suspend fun getExpenseTypeByBuiltInKey(builtInKey: String): ExpenseTypeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseType(expenseType: ExpenseTypeEntity)

    @Update
    suspend fun updateExpenseType(expenseType: ExpenseTypeEntity)

    @Query("DELETE FROM expense_types")
    suspend fun clearExpenseTypes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseTypes(expenseTypes: List<ExpenseTypeEntity>)

    // --- EXPENSES ---
    @Query("SELECT * FROM expenses WHERE deleted = 0 ORDER BY date DESC, createdAt DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE deleted = 0")
    suspend fun getAllExpensesList(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getExpenseById(id: String): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses")
    suspend fun clearExpenses()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    // --- EMPLOYMENTS ---
    @Query("SELECT * FROM employments WHERE deleted = 0 ORDER BY createdAt ASC")
    fun getAllEmployments(): Flow<List<EmploymentEntity>>

    @Query("SELECT * FROM employments WHERE deleted = 0")
    suspend fun getAllEmploymentsList(): List<EmploymentEntity>

    @Query("SELECT * FROM employments WHERE personId = :personId AND deleted = 0 LIMIT 1")
    suspend fun getEmploymentByPersonId(personId: String): EmploymentEntity?

    @Query("SELECT * FROM employments WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getEmploymentById(id: String): EmploymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployment(employment: EmploymentEntity)



    @Update
    suspend fun updateEmployment(employment: EmploymentEntity)

    @Query("DELETE FROM employments")
    suspend fun clearEmployments()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployments(employments: List<EmploymentEntity>)

    // --- SALARY RATES ---
    @Query("SELECT * FROM salary_rates WHERE deleted = 0 ORDER BY effectiveFrom ASC")
    fun getAllSalaryRates(): Flow<List<SalaryRateEntity>>

    @Query("SELECT * FROM salary_rates WHERE deleted = 0")
    suspend fun getAllSalaryRatesList(): List<SalaryRateEntity>

    @Query("SELECT * FROM salary_rates WHERE personId = :personId AND deleted = 0 ORDER BY effectiveFrom ASC")
    fun getSalaryRatesForPerson(personId: String): Flow<List<SalaryRateEntity>>

    @Query("SELECT * FROM salary_rates WHERE personId = :personId AND deleted = 0 ORDER BY effectiveFrom ASC")
    suspend fun getSalaryRatesForPersonList(personId: String): List<SalaryRateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalaryRate(salaryRate: SalaryRateEntity)

    @Update
    suspend fun updateSalaryRate(salaryRate: SalaryRateEntity)

    @Query("DELETE FROM salary_rates")
    suspend fun clearSalaryRates()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalaryRates(salaryRates: List<SalaryRateEntity>)

    // --- ABSENCES ---
    @Query("SELECT * FROM absences WHERE deleted = 0 ORDER BY fromDate DESC, createdAt DESC")
    fun getAllAbsences(): Flow<List<AbsenceEntity>>

    @Query("SELECT * FROM absences WHERE deleted = 0")
    suspend fun getAllAbsencesList(): List<AbsenceEntity>

    @Query("SELECT * FROM absences WHERE personId = :personId AND deleted = 0 ORDER BY fromDate DESC")
    fun getAbsencesForPerson(personId: String): Flow<List<AbsenceEntity>>

    @Query("SELECT * FROM absences WHERE personId = :personId AND deleted = 0 ORDER BY fromDate DESC")
    suspend fun getAbsencesForPersonList(personId: String): List<AbsenceEntity>

    @Query("SELECT * FROM absences WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getAbsenceById(id: String): AbsenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAbsence(absence: AbsenceEntity)

    @Update
    suspend fun updateAbsence(absence: AbsenceEntity)

    @Query("DELETE FROM absences")
    suspend fun clearAbsences()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAbsences(absences: List<AbsenceEntity>)

    // --- SALARY POSTINGS ---
    @Query("SELECT * FROM salary_postings WHERE deleted = 0 ORDER BY month DESC")
    fun getAllSalaryPostings(): Flow<List<SalaryPostingEntity>>

    @Query("SELECT * FROM salary_postings WHERE deleted = 0")
    suspend fun getAllSalaryPostingsList(): List<SalaryPostingEntity>

    @Query("SELECT * FROM salary_postings WHERE personId = :personId AND month = :month AND deleted = 0 LIMIT 1")
    suspend fun getSalaryPosting(personId: String, month: String): SalaryPostingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalaryPosting(posting: SalaryPostingEntity)

    @Update
    suspend fun updateSalaryPosting(posting: SalaryPostingEntity)

    @Query("DELETE FROM salary_postings")
    suspend fun clearSalaryPostings()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalaryPostings(postings: List<SalaryPostingEntity>)

    @Query("SELECT * FROM products WHERE deleted = 0")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE deleted = 0")
    suspend fun getAllProductsList(): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("SELECT * FROM crops WHERE deleted = 0")
    fun getAllCrops(): Flow<List<CropEntity>>

    @Query("SELECT * FROM crops WHERE deleted = 0")
    suspend fun getAllCropsList(): List<CropEntity>

    @Query("SELECT * FROM crops WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getCropById(id: String): CropEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrop(crop: CropEntity)

    @Update
    suspend fun updateCrop(crop: CropEntity)
}
