package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.entity.FarmEntity
import com.example.data.entity.PersonEntity
import com.example.data.entity.PlotEntity
import com.example.data.entity.RoleEntity
import com.example.data.entity.SnapshotEntity
import com.example.data.entity.TagEntity
import com.example.data.gateway.DataGateway
import com.example.lib.JalaliDateHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

import com.example.data.entity.AttendanceEntity
import com.example.data.entity.CrewEntity
import com.example.data.entity.CrewMemberEntity
import com.example.data.entity.LedgerEntryEntity
import com.example.data.entity.MyCountEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.PieceWorkEntity
import com.example.data.entity.EmploymentEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.ExpenseTypeEntity
import com.example.data.entity.CropEntity
import com.example.data.entity.ProductEntity
import com.example.data.entity.TaskEntity
import com.example.data.entity.SalaryPostingEntity
import com.example.data.entity.SalaryRateEntity
import com.example.data.entity.WageRateEntity
import com.example.data.entity.WaitingListEntity
import com.example.data.entity.AbsenceEntity
import com.example.data.entity.IrrigationScheduleEntity
import kotlinx.coroutines.flow.update

data class MainUiState(
    val farms: List<FarmEntity> = emptyList(),
    val plots: List<PlotEntity> = emptyList(),
    val persons: List<PersonEntity> = emptyList(),
    val roles: List<RoleEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val crews: List<CrewEntity> = emptyList(),
    val crewMembers: List<CrewMemberEntity> = emptyList(),
    val wageRates: List<WageRateEntity> = emptyList(),
    val attendances: List<AttendanceEntity> = emptyList(),
    val myCounts: List<MyCountEntity> = emptyList(),
    val pieceWorks: List<PieceWorkEntity> = emptyList(),
    val ledgerEntries: List<LedgerEntryEntity> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
    val waitingList: List<WaitingListEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val expenseTypes: List<ExpenseTypeEntity> = emptyList(),
    val employments: List<EmploymentEntity> = emptyList(),
    val salaryRates: List<SalaryRateEntity> = emptyList(),
    val absences: List<AbsenceEntity> = emptyList(),
    val salaryPostings: List<SalaryPostingEntity> = emptyList(),
    val crops: List<CropEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val irrigationSchedules: List<IrrigationScheduleEntity> = emptyList(),
    val snapshots: List<SnapshotEntity> = emptyList(),
    val unsentChanges: Int = 0,
    val isZeraatActive: Boolean = true,
    val isNahalestanActive: Boolean = true,
    val isTasksModuleActive: Boolean = true,
    val isWorkforceModuleActive: Boolean = true,
    val isReportsModuleActive: Boolean = true,
    val isIrrigationModuleActive: Boolean = true,
    val foremanCoefPresent: Int = 2,
    val foremanCoefAbsent: Int = 1,
    val tomorrowTasksHour: String = "18:00",
    val isDarkTheme: Boolean = false,
    val fontScale: Float = 1.0f,
    val isGuideDismissed: Boolean = false,
    val lastBackupReminderDate: String = "",
    val lastExportAt: String = "",
    val tileOrder: List<String> = listOf("farms", "irrigation", "tasks", "workforce", "ledger", "expenses", "crops", "reports", "persons", "tags"),
    val hiddenTileKeys: Set<String> = emptySet(),
    val isInitialized: Boolean = false
)

data class Workforce2Data(
    val employments: List<EmploymentEntity>,
    val salaryRates: List<SalaryRateEntity>,
    val absences: List<AbsenceEntity>,
    val postings: List<SalaryPostingEntity>
)


class MainViewModel(application: Application) : AndroidViewModel(application) {

    private data class Combined4(val c: CoreData, val t: List<TaskEntity>, val w: WorkforceData, val w2: Workforce2Data)
    private data class Combined5(val c: CoreData, val t: List<TaskEntity>, val w: WorkforceData, val w2: Workforce2Data, val l: LedgerData)
    private data class Combined6(val c: CoreData, val t: List<TaskEntity>, val w: WorkforceData, val w2: Workforce2Data, val l: LedgerData, val e: ExpenseData)
    private data class Combined7(val c: CoreData, val t: List<TaskEntity>, val w: WorkforceData, val w2: Workforce2Data, val l: LedgerData, val e: ExpenseData, val cr: CropData)

    private data class CombinedData(
        val core: CoreData,
        val tasks: List<TaskEntity>,
        val workforce: WorkforceData,
        val workforce2: Workforce2Data,
        val ledger: LedgerData,
        val expense: ExpenseData,
        val crop: CropData,
        val irrigation: List<IrrigationScheduleEntity>
    )

    val gateway = DataGateway(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            gateway.initializeIfNeeded()
            observeDatabase()
        }
    }

    private data class CoreData(
        val farms: List<FarmEntity>,
        val plots: List<PlotEntity>,
        val persons: List<PersonEntity>,
        val roles: List<RoleEntity>,
        val tags: List<TagEntity>
    )

    private data class WorkforceData(
        val crews: List<CrewEntity>,
        val crewMembers: List<CrewMemberEntity>,
        val wageRates: List<WageRateEntity>,
        val attendances: List<AttendanceEntity>,
        val myCounts: List<MyCountEntity>
    )

    private data class LedgerData(
        val pieceWorks: List<PieceWorkEntity>,
        val ledgerEntries: List<LedgerEntryEntity>,
        val payments: List<PaymentEntity>,
        val waitingList: List<WaitingListEntity>
    )

    private data class ExpenseData(
        val expenses: List<ExpenseEntity>,
        val expenseTypes: List<ExpenseTypeEntity>
    )

    private data class CropData(
        val crops: List<CropEntity>,
        val products: List<ProductEntity>
    )

    private fun observeDatabase() {
        viewModelScope.launch {
            val coreDataFlow = combine(
                gateway.getAllFarmsFlow(),
                gateway.getAllPlotsFlow(),
                gateway.getAllPersonsFlow(),
                gateway.getAllRolesFlow(),
                gateway.getAllTagsFlow()
            ) { farms, plots, persons, roles, tags ->
                CoreData(farms, plots, persons, roles, tags)
            }

            val workforceFlow = combine(
                gateway.getAllCrewsFlow(),
                gateway.getAllCrewMembersFlow(),
                gateway.getAllWageRatesFlow(),
                gateway.getAllAttendanceFlow(),
                gateway.getAllMyCountsFlow()
            ) { crews, members, wages, attendances, counts ->
                WorkforceData(crews, members, wages, attendances, counts)
            }

            val ledgerFlow = combine(
                gateway.getAllPieceWorkFlow(),
                gateway.getAllLedgerEntriesFlow(),
                gateway.getAllPaymentsFlow(),
                gateway.getAllWaitingListFlow()
            ) { pieceWorks, entries, payments, waitingList ->
                LedgerData(pieceWorks, entries, payments, waitingList)
            }

            val expenseFlow = combine(
                gateway.getAllExpensesFlow(),
                gateway.getAllExpenseTypesFlow()
            ) { expenses, expenseTypes ->
                ExpenseData(expenses, expenseTypes)
            }

            val workforceFlow2 = combine(
                gateway.getAllEmploymentsFlow(),
                gateway.getAllSalaryRatesFlow(),
                gateway.getAllAbsencesFlow(),
                gateway.getAllSalaryPostingsFlow()
            ) { employments, salaryRates, absences, postings ->
                Workforce2Data(employments, salaryRates, absences, postings)
            }



            val cropFlow = combine(
                gateway.getAllCropsFlow(),
                gateway.getAllProductsFlow()
            ) { crops, products ->
                CropData(crops, products)
            }

            val combinedFlow1 = coreDataFlow
                .combine(gateway.getAllTasksFlow()) { core, tasks -> Pair(core, tasks) }
                .combine(workforceFlow) { (core, tasks), workforce -> Triple(core, tasks, workforce) }
                .combine(workforceFlow2) { (core, tasks, workforce), workforce2 -> Combined4(core, tasks, workforce, workforce2) }
                .combine(ledgerFlow) { (core, tasks, workforce, workforce2), ledger -> Combined5(core, tasks, workforce, workforce2, ledger) }
                .combine(expenseFlow) { (core, tasks, workforce, workforce2, ledger), expense -> Combined6(core, tasks, workforce, workforce2, ledger, expense) }
                .combine(cropFlow) { (core, tasks, workforce, workforce2, ledger, expense), crop ->
                    Combined7(core, tasks, workforce, workforce2, ledger, expense, crop)
                }
                .combine(gateway.getAllIrrigationSchedulesFlow()) { c7, irrigation ->
                    CombinedData(c7.c, c7.t, c7.w, c7.w2, c7.l, c7.e, c7.cr, irrigation)
                }


            combine(
                combinedFlow1,
                gateway.getAllSettingsFlow().combine(gateway.getRecentSnapshotsFlow()) { s, sn -> Pair(s, sn) }
            ) { combined, pair ->
                val settingsList = pair.first
                val snapshots = pair.second
                val core = combined.core
                val tasks = combined.tasks
                val workforce = combined.workforce
                val workforce2 = combined.workforce2
                val ledger = combined.ledger
                val expense = combined.expense
                val crop = combined.crop
                val irrigation = combined.irrigation
                val settingsMap = settingsList.associate { it.key to it.value }

                val unsent = settingsMap["unsentChanges"]?.toIntOrNull() ?: 0
                val workTypesJson = settingsMap["workTypes"] ?: """{"zeraat":true,"nahalestan":true}"""
                val (zeraat, nahalestan) = parseWorkTypes(workTypesJson)

                val modulesJson = settingsMap["modules"] ?: """{"tasks":true,"workforce":true,"reports":true,"irrigation":true}"""
                val (tasksModuleActive, workforceModuleActive, reportsModuleActive, irrigationModuleActive) = try {
                    val mObj = JSONObject(modulesJson)
                    listOf(
                        mObj.optBoolean("tasks", true),
                        mObj.optBoolean("workforce", true),
                        mObj.optBoolean("reports", true),
                        mObj.optBoolean("irrigation", true)
                    )
                } catch (_: Exception) { listOf(true, true, true, true) }

                val (cp, ca) = try {
                    val fc = JSONObject(settingsMap["foremanCoefficients"] ?: """{"present":2,"absent":1}""")
                    Pair(fc.optInt("present", 2), fc.optInt("absent", 1))
                } catch (_: Exception) { Pair(2, 1) }

                val tomorrowHour = settingsMap["tomorrowTasksHour"] ?: "18:00"

                val darkTheme = settingsMap["isDarkTheme"] == "true"
                val fontScaleVal = if (settingsMap["fontScale"] == "large") 1.2f else 1.0f
                val guideDismissed = settingsMap["guideDismissed"] == "true"
                val lastReminder = settingsMap["lastBackupReminderDate"] ?: ""
                val lastExport = settingsMap["lastExportAt"] ?: ""

                val hiddenTiles = (settingsMap["hiddenTiles"] ?: "")
                    .split(",")
                    .filter { it.isNotBlank() }
                    .toSet()

                val order = (settingsMap["tileOrder"] ?: "farms,irrigation,tasks,workforce,ledger,expenses,crops,reports,persons,tags")
                    .split(",")
                    .filter { it.isNotBlank() }

                val defaultOrder = listOf("farms", "irrigation", "tasks", "workforce", "ledger", "expenses", "crops", "reports", "persons", "tags")
                val finalOrder = if (order.isNotEmpty()) {
                    (order + defaultOrder).distinct()
                } else defaultOrder

                MainUiState(
                    farms = core.farms,
                    plots = core.plots,
                    persons = core.persons,
                    roles = core.roles,
                    tags = core.tags,
                    tasks = tasks,
                    crews = workforce.crews,
                    crewMembers = workforce.crewMembers,
                    wageRates = workforce.wageRates,
                    attendances = workforce.attendances,
                    myCounts = workforce.myCounts,
                    pieceWorks = ledger.pieceWorks,
                    ledgerEntries = ledger.ledgerEntries,
                    payments = ledger.payments,
                    waitingList = ledger.waitingList,
                    expenses = expense.expenses,
                    expenseTypes = expense.expenseTypes,
                    employments = workforce2.employments,
                    salaryRates = workforce2.salaryRates,
                    absences = workforce2.absences,
                    salaryPostings = workforce2.postings,
                    crops = crop.crops,
                    products = crop.products,
                    irrigationSchedules = irrigation,
                    snapshots = snapshots,
                    unsentChanges = unsent,
                    isZeraatActive = zeraat,
                    isNahalestanActive = nahalestan,
                    isTasksModuleActive = tasksModuleActive,
                    isWorkforceModuleActive = workforceModuleActive,
                    isReportsModuleActive = reportsModuleActive,
                    isIrrigationModuleActive = irrigationModuleActive,
                    foremanCoefPresent = cp,
                    foremanCoefAbsent = ca,
                    tomorrowTasksHour = tomorrowHour,
                    isDarkTheme = darkTheme,
                    fontScale = fontScaleVal,
                    isGuideDismissed = guideDismissed,
                    lastBackupReminderDate = lastReminder,
                    lastExportAt = lastExport,
                    tileOrder = finalOrder,
                    hiddenTileKeys = hiddenTiles,
                    isInitialized = true
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    private fun parseWorkTypes(jsonStr: String): Pair<Boolean, Boolean> {
        return try {
            val obj = JSONObject(jsonStr)
            val z = obj.optBoolean("zeraat", true)
            val n = obj.optBoolean("nahalestan", true)
            Pair(z, n)
        } catch (_: Exception) {
            Pair(true, true)
        }
    }

    // --- FARMS & PLOTS ---
    fun createFarm(
        name: String,
        workType: String,
        areaM2: Double?,
        ownership: String?,
        waterSource: String?,
        note: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            gateway.createFarm(name, workType, areaM2, ownership, waterSource, note)
            onSuccess()
        }
    }

    fun updateFarm(
        farm: FarmEntity,
        newWorkType: String? = null,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            gateway.updateFarm(farm, newWorkType)
            onSuccess()
        }
    }

    fun toggleFarmActive(farm: FarmEntity) {
        viewModelScope.launch {
            gateway.toggleFarmActive(farm)
        }
    }

    fun deleteFarm(farmId: String, onResult: (deleted: Boolean) -> Unit) {
        viewModelScope.launch {
            val deleted = gateway.deleteFarm(farmId)
            onResult(deleted)
        }
    }

    fun createPlot(
        farmId: String,
        name: String,
        workType: String,
        areaM2: Double?,
        note: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            gateway.createPlot(farmId, name, workType, areaM2, note)
            onSuccess()
        }
    }

    fun updatePlot(plot: PlotEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.updatePlot(plot)
            onSuccess()
        }
    }

    fun togglePlotActive(plot: PlotEntity) {
        viewModelScope.launch {
            gateway.togglePlotActive(plot)
        }
    }

    fun deletePlot(plot: PlotEntity, onResult: (deleted: Boolean) -> Unit) {
        viewModelScope.launch {
            val deleted = gateway.deletePlot(plot)
            onResult(deleted)
        }
    }

    // --- PERSONS & ROLES ---
    fun createPerson(
        name: String,
        phone: String,
        roleIds: List<String>,
        note: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            gateway.createPerson(name, phone, roleIds, note)
            onSuccess()
        }
    }

    fun updatePerson(person: PersonEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.updatePerson(person)
            onSuccess()
        }
    }

    fun togglePersonActive(person: PersonEntity) {
        viewModelScope.launch {
            gateway.togglePersonActive(person)
        }
    }

    fun deletePerson(person: PersonEntity, onResult: (deleted: Boolean) -> Unit) {
        viewModelScope.launch {
            val deleted = gateway.deletePerson(person)
            onResult(deleted)
        }
    }

    fun createRole(name: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.createRole(name)
            onSuccess()
        }
    }

    fun updateRoleName(role: RoleEntity, newName: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.updateRoleName(role, newName)
            onSuccess()
        }
    }

    fun toggleRoleHidden(role: RoleEntity) {
        viewModelScope.launch {
            gateway.toggleRoleHidden(role)
        }
    }

    fun deleteRole(role: RoleEntity, onResult: (deleted: Boolean) -> Unit) {
        viewModelScope.launch {
            val deleted = gateway.deleteCustomRole(role)
            onResult(deleted)
        }
    }

    // --- TAGS (STAGES & ACTIVITIES) ---
    fun createStage(name: String, color: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.createStage(name, color)
            onSuccess()
        }
    }

    fun createActivity(stageId: String, name: String, color: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.createActivity(stageId, name, color)
            onSuccess()
        }
    }

    fun updateTag(tag: TagEntity, newName: String, newColor: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.updateTag(tag, newName, newColor)
            onSuccess()
        }
    }

    fun toggleTagHidden(tag: TagEntity) {
        viewModelScope.launch {
            gateway.toggleTagHidden(tag)
        }
    }

    fun deleteTag(tag: TagEntity, onResult: (deleted: Boolean) -> Unit) {
        viewModelScope.launch {
            val deleted = gateway.deleteTag(tag)
            onResult(deleted)
        }
    }

    fun mergeActivities(sourceTagId: String, destTagId: String, onResult: (success: Boolean) -> Unit) {
        viewModelScope.launch {
            val result = gateway.mergeActivities(sourceTagId, destTagId)
            onResult(result)
        }
    }

    // --- SETTINGS ---
    fun toggleWorkType(type: String, onValidationFailed: (farmNames: List<String>) -> Unit) {
        viewModelScope.launch {
            val currentZ = _uiState.value.isZeraatActive
            val currentN = _uiState.value.isNahalestanActive

            // Rule: at least one must always be active
            if (type == "zeraat" && !currentN && currentZ) return@launch
            if (type == "nahalestan" && !currentZ && currentN) return@launch

            val targetState = if (type == "zeraat") !currentZ else !currentN

            // If turning OFF, check if any active plot of this type exists
            if (!targetState) {
                val activePlotsOfType = _uiState.value.plots.filter { it.workType == type && it.active }
                if (activePlotsOfType.isNotEmpty()) {
                    val farmIds = activePlotsOfType.map { it.farmId }.distinct()
                    val activeFarms = _uiState.value.farms.filter { farmIds.contains(it.id) && it.active }
                    if (activeFarms.isNotEmpty()) {
                        onValidationFailed(activeFarms.map { it.name })
                        return@launch
                    }
                }
            }

            val newZ = if (type == "zeraat") targetState else currentZ
            val newN = if (type == "nahalestan") targetState else currentN
            val json = JSONObject().apply {
                put("zeraat", newZ)
                put("nahalestan", newN)
            }.toString()

            gateway.setSetting("workTypes", json)
        }
    }

    fun setFontScale(isLarge: Boolean) {
        viewModelScope.launch {
            gateway.setSetting("fontScale", if (isLarge) "large" else "normal")
        }
    }

    fun setDarkTheme(isDark: Boolean) {
        viewModelScope.launch {
            gateway.setSetting("isDarkTheme", if (isDark) "true" else "false")
        }
    }

    fun dismissGuide() {
        viewModelScope.launch {
            gateway.setSetting("guideDismissed", "true")
        }
    }

    fun dismissDailyBackupReminder() {
        viewModelScope.launch {
            val todayStr = JalaliDateHelper.today().isoFormat
            gateway.setSetting("lastBackupReminderDate", todayStr)
        }
    }

    fun hideTile(key: String) {
        viewModelScope.launch {
            val current = _uiState.value.hiddenTileKeys.toMutableSet()
            current.add(key)
            gateway.setSetting("hiddenTiles", current.joinToString(","))
        }
    }

    fun unhideTile(key: String) {
        viewModelScope.launch {
            val current = _uiState.value.hiddenTileKeys.toMutableSet()
            current.remove(key)
            gateway.setSetting("hiddenTiles", current.joinToString(","))
        }
    }

    fun moveTile(key: String, direction: Int) { // -1 up, 1 down
        viewModelScope.launch {
            val current = _uiState.value.tileOrder.toMutableList()
            val index = current.indexOf(key)
            if (index != -1) {
                val target = index + direction
                if (target in 0 until current.size) {
                    val item = current.removeAt(index)
                    current.add(target, item)
                    gateway.setSetting("tileOrder", current.joinToString(","))
                }
            }
        }
    }

    // --- TASKS ---
    fun createTask(
        title: String,
        farmId: String?,
        plotId: String?,
        plotIds: List<String> = emptyList(),
        startDate: String,
        endDate: String,
        priority: String,
        assigneePersonIds: List<String>,
        assigneeCrewId: String? = null,
        tagId: String?,
        note: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            gateway.createTask(
                title = title,
                farmId = farmId,
                plotId = plotId,
                plotIds = plotIds,
                startDate = startDate,
                endDate = endDate,
                priority = priority,
                assigneePersonIds = assigneePersonIds,
                assigneeCrewId = assigneeCrewId,
                tagId = tagId,
                note = note
            )
            onSuccess()
        }
    }

    fun completeTaskWithSmartAttendance(
        task: TaskEntity,
        date: String,
        farmId: String?,
        plotId: String?,
        crewId: String,
        tagId: String,
        assignedWorkerIds: List<String>,
        note: String,
        onComplete: (addedNewAttendance: Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val added = gateway.syncTaskCompletionAttendance(
                task = task,
                date = date,
                farmId = farmId,
                plotId = plotId,
                crewId = crewId,
                tagId = tagId,
                assignedWorkerIds = assignedWorkerIds,
                note = note
            )
            gateway.toggleTaskStatus(task.id, "done")
            onComplete(added)
        }
    }

    fun updateTask(task: TaskEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.updateTask(task)
            onSuccess()
        }
    }

    fun toggleTaskStatus(taskId: String, newStatus: String, onDoneCrewTask: ((TaskEntity) -> Unit)? = null) {
        viewModelScope.launch {
            val updated = gateway.toggleTaskStatus(taskId, newStatus)
            if (updated != null && newStatus == "done" && updated.assigneeCrewId != null) {
                onDoneCrewTask?.invoke(updated)
            }
        }
    }

    fun rescheduleTask(taskId: String, newStartDate: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.rescheduleTask(taskId, newStartDate)
            onSuccess()
        }
    }

    fun deleteTask(taskId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.deleteTask(taskId)
            onSuccess()
        }
    }

    // --- CREWS & WAGES (Section 1) ---
    fun createCrew(
        name: String,
        kind: String,
        foremanId: String?,
        dailyWage: Long,
        effectiveFrom: String,
        note: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            gateway.createCrew(name, kind, foremanId, dailyWage, effectiveFrom, note)
            onSuccess()
        }
    }

    fun updateCrew(crew: CrewEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.updateCrew(crew)
            onSuccess()
        }
    }

    fun toggleCrewActive(crew: CrewEntity) {
        viewModelScope.launch {
            gateway.toggleCrewActive(crew)
        }
    }

    fun deleteCrew(crewId: String, onResult: (deleted: Boolean) -> Unit) {
        viewModelScope.launch {
            val deleted = gateway.deleteCrew(crewId)
            onResult(deleted)
        }
    }

    fun addCrewMember(crewId: String, personId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.addCrewMember(crewId, personId)
            onSuccess()
        }
    }

    fun createPersonAndAddToCrew(crewId: String, name: String, phone: String, onSuccess: (PersonEntity) -> Unit) {
        viewModelScope.launch {
            val person = gateway.createPerson(name, phone, emptyList(), "")
            gateway.ensureWorkerRole(person.id)
            gateway.addCrewMember(crewId, person.id)
            onSuccess(person)
        }
    }

    fun deactivateCrewMember(crewMemberId: String) {
        viewModelScope.launch {
            gateway.deactivateCrewMember(crewMemberId)
        }
    }

    fun setCrewDailyWage(crewId: String, personId: String?, dailyWage: Long, effectiveFrom: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.setDailyWage(crewId, personId, dailyWage, effectiveFrom)
            onSuccess()
        }
    }

    // --- ATTENDANCE (Section 2) ---
    fun createAttendance(
        date: String,
        farmId: String?,
        plotId: String?,
        crewId: String,
        tagId: String,
        lines: String,
        headcount: Int?,
        countTicks: Int?,
        foreman: String?,
        note: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            gateway.createAttendance(date, farmId, plotId, crewId, tagId, lines, headcount, countTicks, foreman, note)
            onSuccess()
        }
    }

    fun toggleWorkerAttendance(date: String, personId: String, isPresent: Boolean, ticks: Int = 1, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            gateway.toggleWorkerAttendanceForDate(date, personId, isPresent, ticks)
            onSuccess()
        }
    }

    fun toggleCrewAttendance(date: String, crewId: String, isPresent: Boolean, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            gateway.toggleCrewAttendanceForDate(date, crewId, isPresent)
            onSuccess()
        }
    }

    fun updateAttendance(att: AttendanceEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.updateAttendance(att)
            onSuccess()
        }
    }

    fun deleteAttendance(attendanceId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.deleteAttendance(attendanceId)
            onSuccess()
        }
    }

    // --- MY COUNTS (Section 2) ---
    fun createMyCount(date: String, crewId: String, headcount: Int, note: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.createMyCount(date, crewId, headcount, note)
            onSuccess()
        }
    }

    fun deleteMyCount(id: String) {
        viewModelScope.launch {
            gateway.deleteMyCount(id)
        }
    }

    // --- PIECE WORK (Section 3) ---
    fun createPieceWork(
        date: String,
        farmId: String?,
        plotId: String?,
        tagId: String,
        jobType: String,
        size: String,
        rate: Long,
        lines: String,
        note: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            gateway.createPieceWork(date, farmId, plotId, tagId, jobType, size, rate, lines, note)
            onSuccess()
        }
    }

    fun updatePieceWork(pw: PieceWorkEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.updatePieceWork(pw)
            onSuccess()
        }
    }

    fun deletePieceWork(pieceWorkId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.deletePieceWork(pieceWorkId)
            onSuccess()
        }
    }

    // --- LEDGER ENTRIES (Section 4) ---
    fun createLedgerEntry(
        accountType: String,
        accountId: String,
        date: String,
        kind: String,
        amount: Long,
        note: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            gateway.createLedgerEntry(accountType, accountId, date, kind, amount, note)
            onSuccess()
        }
    }

    fun updateLedgerEntry(entry: LedgerEntryEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.updateLedgerEntry(entry)
            onSuccess()
        }
    }

    fun voidLedgerEntry(entryId: String, reason: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.voidLedgerEntry(entryId, reason)
            onSuccess()
        }
    }

    fun deleteLedgerEntry(entryId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.deleteLedgerEntry(entryId)
            onSuccess()
        }
    }

    // --- PAYMENTS (Section 4) ---
    fun createPayment(
        date: String,
        amount: Long,
        handedToPersonId: String?,
        targetType: String,
        targetId: String,
        method: String?,
        note: String,
        shares: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            gateway.createPayment(date, amount, handedToPersonId, targetType, targetId, method, note, shares)
            onSuccess()
        }
    }

    fun updatePayment(payment: PaymentEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.updatePayment(payment)
            onSuccess()
        }
    }

    fun deletePayment(paymentId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.deletePayment(paymentId)
            onSuccess()
        }
    }

    // --- WAITING LIST (Section 4) ---
    fun createWaitingListItem(
        accountType: String,
        accountId: String,
        amount: Long?,
        date: String,
        note: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            gateway.createWaitingListItem(accountType, accountId, amount, date, note)
            onSuccess()
        }
    }

    fun markWaitingListItemDone(id: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            gateway.updateWaitingListItemStatus(id, "done")
            onSuccess()
        }
    }

    fun deleteWaitingListItem(id: String) {
        viewModelScope.launch {
            gateway.deleteWaitingListItem(id)
        }
    }

    // --- MODULES & WORKFORCE SETTINGS ---
    fun toggleModule(moduleKey: String) {
        viewModelScope.launch {
            val currentMod = _uiState.value
            val isCurrentActive = when (moduleKey) {
                "tasks" -> currentMod.isTasksModuleActive
                "workforce" -> currentMod.isWorkforceModuleActive
                "reports" -> currentMod.isReportsModuleActive
                else -> true
            }
            val newActive = !isCurrentActive
            val modStr = gateway.getSetting("modules") ?: """{"tasks":true,"workforce":true,"reports":true}"""
            val obj = try { JSONObject(modStr) } catch (_: Exception) { JSONObject() }
            obj.put(moduleKey, newActive)
            gateway.setSetting("modules", obj.toString())
        }
    }

    fun toggleTasksModule() {
        toggleModule("tasks")
    }

    fun toggleWorkforceModule() {
        toggleModule("workforce")
    }

    fun toggleReportsModule() {
        toggleModule("reports")
    }

    fun setForemanCoefficients(present: Int, absent: Int) {
        viewModelScope.launch {
            gateway.setForemanCoefficients(present, absent)
        }
    }

    fun setTomorrowTasksHour(hour: String) {
        viewModelScope.launch {
            gateway.setSetting("tomorrowTasksHour", hour)
        }
    }

    // --- BACKUP & RESTORE ---
    suspend fun getExportJsonString(): String {
        return gateway.generateExportJsonString()
    }

    fun acknowledgeBackupSent() {
        viewModelScope.launch {
            gateway.resetUnsentChanges()
        }
    }

    fun restoreBackup(jsonString: String, onResult: (success: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            val validation = gateway.validateBackupContent(jsonString)
            if (!validation.isValid) {
                onResult(false, validation.errorMessage ?: "فایل نامعتبر است")
                return@launch
            }
            val success = gateway.restoreFromJson(jsonString, isSnapshotRestore = false)
            if (success) {
                onResult(true, "پشتیبان با موفقیت برگردانده شد")
            } else {
                onResult(false, "خطا در برگرداندن فایل پشتیبان")
            }
        }
    }

    fun restoreSnapshot(snapshot: SnapshotEntity, onResult: (success: Boolean) -> Unit) {
        viewModelScope.launch {
            val success = gateway.restoreFromJson(snapshot.data, isSnapshotRestore = true)
            onResult(success)
        }
    }

    fun clearAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            gateway.clearAllData()
            onComplete()
        }
    }

    suspend fun getTeamMembers(crewId: String): List<String> {
        return gateway.getAllCrewMembersList()
            .filter { it.crewId == crewId && !it.deleted }
            .map { it.personId }
    }

    fun saveTeam(crewId: String, workerIds: List<String>) {
        viewModelScope.launch {
            // Add or deactivate crew members based on selection
            val existing = gateway.getAllCrewMembersList().filter { it.crewId == crewId && !it.deleted }
            for (id in workerIds) {
                if (existing.none { it.personId == id }) {
                    gateway.addCrewMember(crewId, id)
                }
            }
            for (member in existing) {
                if (workerIds.none { it == member.personId }) {
                    gateway.deactivateCrewMember(member.id)
                }
            }
        }
    }

    // --- EXPENSES ---
    fun createExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            gateway.insertExpense(expense)
        }
    }


    fun updateExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            gateway.updateExpense(expense)
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            gateway.deleteExpense(expenseId)
        }
    }

    fun saveEmployment(employment: EmploymentEntity, salary: Long, applyUpToToday: Boolean = false) {
        viewModelScope.launch {
            gateway.saveEmploymentWithRetroactive(employment, salary, applyUpToToday)
        }
    }

    fun savePersonWithRelations(
        personId: String?,
        name: String,
        phone: String,
        roleIds: List<String>,
        note: String,
        foremanId: String?,
        workerIds: List<String>,
        monthlySalary: Long? = null,
        salaryFromMonth: String? = null,
        applyRetroactiveSalary: Boolean = false,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val person = if (personId == null) {
                gateway.createPerson(name, phone, roleIds, note)
            } else {
                val existing = gateway.getAllPersonsList().find { it.id == personId }
                val updated = existing?.copy(
                    name = name.trim(),
                    phone = phone.trim(),
                    roleIds = org.json.JSONArray(roleIds).toString(),
                    note = note.trim(),
                    updatedAt = java.time.Instant.now().toString()
                ) ?: PersonEntity(
                    id = personId,
                    name = name.trim(),
                    phone = phone.trim(),
                    roleIds = org.json.JSONArray(roleIds).toString(),
                    note = note.trim()
                )
                gateway.updatePerson(updated)
                updated
            }

            val roles = gateway.getAllRolesList()
            val foremanRole = roles.find { it.builtInKey == "foreman" || it.name == "سرکارگر" }
            val workerRole = roles.find { it.builtInKey == "worker" || it.name == "کارگر" }
            val isForeman = foremanRole != null && roleIds.contains(foremanRole.id)
            val isWorker = workerRole != null && roleIds.contains(workerRole.id)

            val todayIso = JalaliDateHelper.toIsoDate(java.time.LocalDate.now())

            // 1. If foreman: ensure or create their crew, then sync workerIds
            if (isForeman) {
                val allCrews = gateway.getAllCrewsList()
                val crew = allCrews.find { it.foremanId == person.id && !it.deleted }
                    ?: gateway.createCrew("دسته‌ی ${person.name}", "members", person.id, 800_000L, todayIso, "")

                val currentMembers = gateway.getAllCrewMembersList().filter { it.crewId == crew.id && !it.deleted }
                for (wId in workerIds) {
                    if (currentMembers.none { it.personId == wId }) {
                        gateway.addCrewMember(crew.id, wId)
                        gateway.ensureWorkerRole(wId)
                    }
                }
                for (m in currentMembers) {
                    if (!workerIds.contains(m.personId)) {
                        gateway.deactivateCrewMember(m.id)
                    }
                }
            }

            // 2. If worker and foremanId chosen: assign this worker to foreman's crew
            if (isWorker && !foremanId.isNullOrBlank()) {
                val allCrews = gateway.getAllCrewsList()
                val fCrew = allCrews.find { it.foremanId == foremanId && !it.deleted }
                    ?: run {
                        val foremanPerson = gateway.getAllPersonsList().find { it.id == foremanId }
                        gateway.createCrew("دسته‌ی ${foremanPerson?.name ?: ""}", "members", foremanId, 800_000L, todayIso, "")
                    }
                val currentMembers = gateway.getAllCrewMembersList().filter { it.crewId == fCrew.id && !it.deleted }
                if (currentMembers.none { it.personId == person.id }) {
                    gateway.addCrewMember(fCrew.id, person.id)
                    gateway.ensureWorkerRole(person.id)
                }
            }

            // 3. If monthly salary is set: save employment and apply retroactive if requested
            if (monthlySalary != null && monthlySalary > 0L) {
                val effectiveMonth = salaryFromMonth ?: JalaliDateHelper.today().monthKey
                val startIso = if (!salaryFromMonth.isNullOrBlank()) {
                    val p = salaryFromMonth.split("-")
                    val yr = p[0].toIntOrNull() ?: JalaliDateHelper.today().year
                    val mo = p.getOrNull(1)?.toIntOrNull() ?: JalaliDateHelper.today().month
                    JalaliDateHelper.toIsoDate(JalaliDateHelper.toGregorian(yr, mo, 1))
                } else todayIso

                val emp = EmploymentEntity(
                    personId = person.id,
                    kind = "fixed",
                    salaryFromMonth = effectiveMonth,
                    workStartDate = startIso
                )
                gateway.saveEmploymentWithRetroactive(emp, monthlySalary, applyRetroactiveSalary)
            }

            onSuccess()
        }
    }

    fun saveTeamForForeman(foreman: PersonEntity, workerIds: List<String>, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val todayIso = JalaliDateHelper.toIsoDate(java.time.LocalDate.now())
            val allCrews = gateway.getAllCrewsList()
            val crew = allCrews.find { it.foremanId == foreman.id && !it.deleted }
                ?: gateway.createCrew("دسته‌ی ${foreman.name}", "members", foreman.id, 800_000L, todayIso, "")

            val currentMembers = gateway.getAllCrewMembersList().filter { it.crewId == crew.id && !it.deleted }
            for (wId in workerIds) {
                if (currentMembers.none { it.personId == wId }) {
                    gateway.addCrewMember(crew.id, wId)
                    gateway.ensureWorkerRole(wId)
                }
            }
            for (m in currentMembers) {
                if (!workerIds.contains(m.personId)) {
                    gateway.deactivateCrewMember(m.id)
                }
            }
            onSuccess()
        }
    }

    // --- CROPS ---
    fun createCrop(name: String, plotId: String, startDate: String, note: String = "", onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val crop = CropEntity(
                name = name.trim(),
                plotId = plotId,
                startDate = startDate,
                note = note.trim()
            )
            gateway.insertCrop(crop)
            onSuccess()
        }
    }

    fun updateCrop(crop: CropEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            gateway.updateCrop(crop)
            onSuccess()
        }
    }

    fun deleteCrop(crop: CropEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            gateway.updateCrop(crop.copy(deleted = true, deletedAt = java.time.Instant.now().toString()))
            onSuccess()
        }
    }

    // --- IRRIGATION SCHEDULES ---
    fun createIrrigationSchedule(
        farmId: String,
        plotId: String,
        irrigatorPersonId: String?,
        date: String,
        time: String,
        durationHours: Double?,
        waterSource: String?,
        reminderEnabled: Boolean,
        reminderMinutesBefore: Int,
        note: String,
        onSuccess: (IrrigationScheduleEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            val entity = gateway.createIrrigationSchedule(
                farmId = farmId,
                plotId = plotId,
                irrigatorPersonId = irrigatorPersonId,
                date = date,
                time = time,
                durationHours = durationHours,
                waterSource = waterSource,
                reminderEnabled = reminderEnabled,
                reminderMinutesBefore = reminderMinutesBefore,
                note = note
            )
            onSuccess(entity)
        }
    }

    fun updateIrrigationSchedule(
        schedule: IrrigationScheduleEntity,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            gateway.updateIrrigationSchedule(schedule)
            onSuccess()
        }
    }

    fun toggleIrrigationStatus(
        scheduleId: String,
        newStatus: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            gateway.toggleIrrigationStatus(scheduleId, newStatus)
            onSuccess()
        }
    }

    fun markIrrigationSmsSent(
        scheduleId: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            gateway.markIrrigationSmsSent(scheduleId)
            onSuccess()
        }
    }

    fun deleteIrrigationSchedule(
        scheduleId: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            gateway.deleteIrrigationSchedule(scheduleId)
            onSuccess()
        }
    }
}
