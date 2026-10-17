package com.example.data.gateway

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.entity.AttendanceEntity
import com.example.data.entity.CrewEntity
import com.example.data.entity.CrewMemberEntity
import com.example.data.entity.FarmEntity
import com.example.data.entity.HistoryEntity
import com.example.data.entity.LedgerEntryEntity
import com.example.data.entity.MyCountEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.PersonEntity
import com.example.data.entity.PieceWorkEntity
import com.example.data.entity.PlotEntity
import com.example.data.entity.RoleEntity
import com.example.data.entity.SettingEntity
import com.example.data.entity.SnapshotEntity
import com.example.data.entity.TagEntity
import com.example.data.entity.TaskEntity
import com.example.data.entity.WageRateEntity
import com.example.data.entity.WaitingListEntity
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import com.example.lib.WorkforceCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

class DataGateway(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val dao = db.appDao()

    // Registry for entity record usage counters (AGENTS.md Section 4)
    private val usageCounters = mutableListOf<suspend (entityType: String, id: String) -> Int>()

    // Registry for tag reference transfer hooks on merge
    private val tagReferenceUpdaters = mutableListOf<suspend (sourceTagId: String, destTagId: String) -> Unit>()

    init {
        // Task usage counter (Decision 180)
        registerUsageCounter { entityType, id ->
            val tasks = dao.getAllTasksList().filter { !it.deleted }
            when (entityType) {
                "farm" -> tasks.count { it.farmId == id }
                "plot" -> tasks.count { it.plotId == id }
                "person" -> tasks.count {
                    val personIds = parseRoleIds(it.assigneePersonIds)
                    personIds.contains(id)
                }
                "crew" -> tasks.count { it.assigneeCrewId == id }
                "tag" -> tasks.count { it.tagId == id }
                else -> 0
            }
        }

        // Tag reference updater for tasks
        registerTagReferenceUpdater { sourceTagId, destTagId ->
            val tasks = dao.getAllTasksList().filter { it.tagId == sourceTagId }
            val now = Instant.now().toString()
            for (task in tasks) {
                dao.updateTask(task.copy(tagId = destTagId, updatedAt = now))
            }
        }

        // Attendance & Workforce usage counters (Decisions 117, 198, 277)
        registerUsageCounter { entityType, id ->
            val attendances = dao.getAllAttendanceList().filter { !it.deleted }
            val pieceWorks = dao.getAllPieceWorkList().filter { !it.deleted }
            val payments = dao.getAllPaymentsList().filter { !it.deleted }
            val ledgerEntries = dao.getAllLedgerEntriesList().filter { !it.deleted }

            when (entityType) {
                "farm" -> {
                    attendances.count { it.farmId == id } + pieceWorks.count { it.farmId == id }
                }
                "plot" -> {
                    attendances.count { it.plotId == id } + pieceWorks.count { it.plotId == id }
                }
                "crew" -> {
                    attendances.count { it.crewId == id } +
                    payments.count { it.targetType == "crew" && it.targetId == id } +
                    ledgerEntries.count { it.accountType == "crew" && it.accountId == id }
                }
                "person" -> {
                    var count = 0
                    for (att in attendances) {
                        try {
                            val arr = JSONArray(att.lines)
                            for (i in 0 until arr.length()) {
                                if (arr.getJSONObject(i).getString("personId") == id) count++
                            }
                        } catch (_: Exception) {}
                        try {
                            if (!att.foreman.isNullOrBlank()) {
                                if (JSONObject(att.foreman).optString("personId") == id) count++
                            }
                        } catch (_: Exception) {}
                    }
                    for (pw in pieceWorks) {
                        try {
                            val arr = JSONArray(pw.lines)
                            for (i in 0 until arr.length()) {
                                if (arr.getJSONObject(i).getString("personId") == id) count++
                            }
                        } catch (_: Exception) {}
                    }
                    for (p in payments) {
                        if (p.handedToPersonId == id) count++
                        try {
                            val arr = JSONArray(p.shares)
                            for (i in 0 until arr.length()) {
                                val s = arr.getJSONObject(i)
                                if (s.optString("accountType") == "person" && s.getString("accountId") == id) count++
                            }
                        } catch (_: Exception) {}
                    }
                    count += ledgerEntries.count { it.accountType == "person" && it.accountId == id }
                    count
                }
                "tag" -> {
                    attendances.count { it.tagId == id } + pieceWorks.count { it.tagId == id }
                }
                else -> 0
            }
        }

        // Tag reference updater for attendance and piecework
        registerTagReferenceUpdater { sourceTagId, destTagId ->
            val now = Instant.now().toString()
            val attendances = dao.getAllAttendanceList().filter { it.tagId == sourceTagId }
            for (att in attendances) {
                dao.updateAttendance(att.copy(tagId = destTagId, updatedAt = now))
            }
            val pieceWorks = dao.getAllPieceWorkList().filter { it.tagId == sourceTagId }
            for (pw in pieceWorks) {
                dao.updatePieceWork(pw.copy(tagId = destTagId, updatedAt = now))
            }
        }
    }

    fun registerUsageCounter(counter: suspend (entityType: String, id: String) -> Int) {
        usageCounters.add(counter)
    }

    fun registerTagReferenceUpdater(updater: suspend (sourceTagId: String, destTagId: String) -> Unit) {
        tagReferenceUpdaters.add(updater)
    }

    /**
     * Central countUsage function: counts references in actual records.
     * Note: Plots do NOT count as records (Decision 158: "قطعه «ثبت» حساب نمی‌شود").
     */
    suspend fun countUsage(entityType: String, id: String): Int = withContext(Dispatchers.IO) {
        var total = 0
        for (counter in usageCounters) {
            total += counter(entityType, id)
        }
        total
    }

    /**
     * Increments unsent backup changes counter by 1 (Decision 155).
     */
    private suspend fun incrementUnsentChanges() {
        val currentStr = dao.getSetting("unsentChanges") ?: "0"
        val current = currentStr.toIntOrNull() ?: 0
        dao.setSetting(SettingEntity("unsentChanges", (current + 1).toString()))
    }

    suspend fun resetUnsentChanges() = withContext(Dispatchers.IO) {
        dao.setSetting(SettingEntity("unsentChanges", "0"))
        dao.setSetting(SettingEntity("lastExportAt", Instant.now().toString()))
    }

    suspend fun getUnsentChanges(): Int = withContext(Dispatchers.IO) {
        (dao.getSetting("unsentChanges") ?: "0").toIntOrNull() ?: 0
    }

    // --- INITIAL SEEDING ---
    suspend fun initializeIfNeeded() = withContext(Dispatchers.IO) {
        val initialized = dao.getSetting("app_initialized")
        if (initialized != "true") {
            seedBuiltIns()
            dao.setSetting(SettingEntity("workTypes", """{"zeraat":true,"nahalestan":true}"""))
            dao.setSetting(SettingEntity("workMode", "local"))
            dao.setSetting(SettingEntity("guideDismissed", "false"))
            dao.setSetting(SettingEntity("unsentChanges", "0"))
            dao.setSetting(SettingEntity("modules", """{"tasks":true,"workforce":true}"""))
            dao.setSetting(SettingEntity("tomorrowTasksHour", "18:00"))
            dao.setSetting(SettingEntity("foremanCoefficients", """{"present":2,"absent":1}"""))
            dao.setSetting(SettingEntity("app_initialized", "true"))
        } else {
            val modStr = dao.getSetting("modules")
            if (modStr == null) {
                dao.setSetting(SettingEntity("modules", """{"tasks":true,"workforce":true}"""))
            } else {
                try {
                    val mObj = JSONObject(modStr)
                    if (!mObj.has("workforce")) {
                        mObj.put("workforce", true)
                        dao.setSetting(SettingEntity("modules", mObj.toString()))
                    }
                } catch (_: Exception) {}
            }
            if (dao.getSetting("foremanCoefficients") == null) {
                dao.setSetting(SettingEntity("foremanCoefficients", """{"present":2,"absent":1}"""))
            }
            if (dao.getSetting("tomorrowTasksHour") == null) {
                dao.setSetting(SettingEntity("tomorrowTasksHour", "18:00"))
            }
        }
        checkAndCreateDailySnapshot()
    }

    private suspend fun seedBuiltIns() {
        // Built-in Roles: کارگر، سرکارگر، خریدار، فروشنده
        val existingRoles = dao.getAllRolesList()
        if (existingRoles.isEmpty()) {
            dao.insertRole(RoleEntity(name = "کارگر", builtInKey = "worker"))
            dao.insertRole(RoleEntity(name = "سرکارگر", builtInKey = "foreman"))
            dao.insertRole(RoleEntity(name = "خریدار", builtInKey = "buyer"))
            dao.insertRole(RoleEntity(name = "فروشنده", builtInKey = "seller"))
        }

        // Built-in Stages: کاشت، داشت، برداشت، سایر
        val existingTags = dao.getAllTagsList()
        if (existingTags.isEmpty()) {
            dao.insertTag(TagEntity(name = "کاشت", parentId = null, color = "#2E7D32", builtInKey = "kasht", sortOrder = 1))
            dao.insertTag(TagEntity(name = "داشت", parentId = null, color = "#1976D2", builtInKey = "dasht", sortOrder = 2))
            dao.insertTag(TagEntity(name = "برداشت", parentId = null, color = "#F57C00", builtInKey = "bardasht", sortOrder = 3))
            dao.insertTag(TagEntity(name = "سایر", parentId = null, color = "#757575", builtInKey = "sayer", sortOrder = 4))
        }
    }

    // --- FARMS & PLOTS ---
    fun getAllFarmsFlow(): Flow<List<FarmEntity>> = dao.getAllFarms().flowOn(Dispatchers.IO)
    fun getAllPlotsFlow(): Flow<List<PlotEntity>> = dao.getAllPlots().flowOn(Dispatchers.IO)

    suspend fun getFarmById(id: String): FarmEntity? = withContext(Dispatchers.IO) {
        dao.getFarmById(id)
    }

    fun getPlotsForFarmFlow(farmId: String): Flow<List<PlotEntity>> =
        dao.getPlotsForFarm(farmId).flowOn(Dispatchers.IO)

    suspend fun getPlotsForFarm(farmId: String): List<PlotEntity> = withContext(Dispatchers.IO) {
        dao.getPlotsForFarmList(farmId)
    }

    /**
     * Creates a new Farm along with its hidden default Plot (isDefault = true).
     */
    suspend fun createFarm(
        name: String,
        workType: String,
        areaM2: Double?,
        ownership: String?,
        waterSource: String?,
        note: String
    ): FarmEntity = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val farm = FarmEntity(
            name = name.trim(),
            areaM2 = areaM2,
            ownership = ownership,
            waterSource = waterSource,
            note = note.trim(),
            active = true,
            createdAt = now,
            updatedAt = now
        )
        dao.insertFarm(farm)

        // Create default hidden plot
        val defaultPlot = PlotEntity(
            farmId = farm.id,
            name = "بدون قطعه",
            workType = workType,
            areaM2 = areaM2,
            note = "",
            active = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now
        )
        dao.insertPlot(defaultPlot)

        incrementUnsentChanges()
        farm
    }

    suspend fun updateFarm(
        farm: FarmEntity,
        newWorkType: String? = null
    ) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val updated = farm.copy(updatedAt = now)
        dao.updateFarm(updated)

        // If work type was updated and farm has no real plots, update default plot
        if (newWorkType != null) {
            val realPlots = dao.getPlotsForFarmList(farm.id).filter { !it.isDefault }
            if (realPlots.isEmpty()) {
                val defaultPlot = dao.getPlotsForFarmList(farm.id).firstOrNull { it.isDefault }
                if (defaultPlot != null && defaultPlot.workType != newWorkType) {
                    dao.updatePlot(defaultPlot.copy(workType = newWorkType, updatedAt = now))
                }
            }
        }

        incrementUnsentChanges()
    }

    suspend fun toggleFarmActive(farm: FarmEntity) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        dao.updateFarm(farm.copy(active = !farm.active, updatedAt = now))
        incrementUnsentChanges()
    }

    suspend fun deleteFarm(farmId: String): Boolean = withContext(Dispatchers.IO) {
        val farm = dao.getFarmById(farmId) ?: return@withContext false
        val plots = dao.getPlotsForFarmList(farmId)

        // Check usage of farm and all its plots
        var totalUsage = countUsage("farm", farmId)
        for (p in plots) {
            totalUsage += countUsage("plot", p.id)
        }

        if (totalUsage == 0) {
            val now = Instant.now().toString()
            // Soft delete farm and all plots
            dao.updateFarm(farm.copy(deleted = true, deletedAt = now, updatedAt = now))
            for (p in plots) {
                dao.updatePlot(p.copy(deleted = true, deletedAt = now, updatedAt = now))
            }
            incrementUnsentChanges()
            true
        } else {
            // Cannot delete -> only deactivate
            toggleFarmActive(farm)
            false
        }
    }

    suspend fun createPlot(
        farmId: String,
        name: String,
        workType: String,
        areaM2: Double?,
        note: String
    ): PlotEntity = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val plot = PlotEntity(
            farmId = farmId,
            name = name.trim(),
            workType = workType,
            areaM2 = areaM2,
            note = note.trim(),
            active = true,
            isDefault = false,
            createdAt = now,
            updatedAt = now
        )
        dao.insertPlot(plot)
        incrementUnsentChanges()
        plot
    }

    suspend fun canChangePlotWorkType(plotId: String, newWorkType: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val existing = dao.getPlotById(plotId) ?: return@withContext Pair(true, null)
        if (existing.workType == "nahalestan" && newWorkType != "nahalestan") {
            val count = dao.getAllPieceWorkList().count { it.plotId == plotId && !it.deleted }
            if (count > 0) {
                return@withContext Pair(false, "این قطعه کار دانه‌ای دارد")
            }
        }
        Pair(true, null)
    }

    suspend fun updatePlot(plot: PlotEntity) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        dao.updatePlot(plot.copy(updatedAt = now))
        incrementUnsentChanges()
    }

    suspend fun togglePlotActive(plot: PlotEntity) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        dao.updatePlot(plot.copy(active = !plot.active, updatedAt = now))
        incrementUnsentChanges()
    }

    suspend fun deletePlot(plot: PlotEntity): Boolean = withContext(Dispatchers.IO) {
        val usage = countUsage("plot", plot.id)
        if (usage == 0) {
            val now = Instant.now().toString()
            dao.updatePlot(plot.copy(deleted = true, deletedAt = now, updatedAt = now))
            incrementUnsentChanges()
            true
        } else {
            togglePlotActive(plot)
            false
        }
    }

    /**
     * Determines which plots can be selected for a farm in logs/records (Decision 278):
     * - If the farm has active real plots -> returns only active real plots.
     * - If no active real plots -> returns the default hidden plot.
     */
    suspend fun selectablePlots(farmId: String): List<PlotEntity> = withContext(Dispatchers.IO) {
        val plots = dao.getPlotsForFarmList(farmId).filter { it.active }
        val realPlots = plots.filter { !it.isDefault }
        if (realPlots.isNotEmpty()) {
            realPlots
        } else {
            plots.filter { it.isDefault }
        }
    }

    // --- PERSONS & ROLES ---
    fun getAllPersonsFlow(): Flow<List<PersonEntity>> = dao.getAllPersons().flowOn(Dispatchers.IO)
    fun getAllRolesFlow(): Flow<List<RoleEntity>> = dao.getAllRoles().flowOn(Dispatchers.IO)

    suspend fun getAllRolesList(): List<RoleEntity> = withContext(Dispatchers.IO) {
        dao.getAllRolesList()
    }

    suspend fun createPerson(
        name: String,
        phone: String,
        roleIds: List<String>,
        note: String
    ): PersonEntity = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val roleJson = JSONArray(roleIds).toString()
        val person = PersonEntity(
            name = name.trim(),
            phone = phone.trim(),
            roleIds = roleJson,
            note = note.trim(),
            active = true,
            createdAt = now,
            updatedAt = now
        )
        dao.insertPerson(person)
        incrementUnsentChanges()
        person
    }

    suspend fun updatePerson(person: PersonEntity) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        dao.updatePerson(person.copy(updatedAt = now))
        incrementUnsentChanges()
    }

    suspend fun togglePersonActive(person: PersonEntity) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        dao.updatePerson(person.copy(active = !person.active, updatedAt = now))
        incrementUnsentChanges()
    }

    suspend fun deletePerson(person: PersonEntity): Boolean = withContext(Dispatchers.IO) {
        val usage = countUsage("person", person.id)
        if (usage == 0) {
            val now = Instant.now().toString()
            dao.updatePerson(person.copy(deleted = true, deletedAt = now, updatedAt = now))
            incrementUnsentChanges()
            true
        } else {
            togglePersonActive(person)
            false
        }
    }

    suspend fun createRole(name: String): RoleEntity = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val role = RoleEntity(
            name = name.trim(),
            builtInKey = null,
            hidden = false,
            createdAt = now,
            updatedAt = now
        )
        dao.insertRole(role)
        incrementUnsentChanges()
        role
    }

    suspend fun updateRoleName(role: RoleEntity, newName: String) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        dao.updateRole(role.copy(name = newName.trim(), updatedAt = now))
        incrementUnsentChanges()
    }

    suspend fun toggleRoleHidden(role: RoleEntity) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        dao.updateRole(role.copy(hidden = !role.hidden, updatedAt = now))
        incrementUnsentChanges()
    }

    suspend fun deleteCustomRole(role: RoleEntity): Boolean = withContext(Dispatchers.IO) {
        if (role.builtInKey != null) return@withContext false // built-in roles cannot be deleted
        val now = Instant.now().toString()
        // Remove this role from all persons
        val allPersons = dao.getAllPersonsList()
        for (person in allPersons) {
            val roleList = parseRoleIds(person.roleIds)
            if (roleList.contains(role.id)) {
                val updatedRoles = roleList.filter { it != role.id }
                dao.updatePerson(person.copy(roleIds = JSONArray(updatedRoles).toString(), updatedAt = now))
            }
        }
        dao.updateRole(role.copy(deleted = true, deletedAt = now, updatedAt = now))
        incrementUnsentChanges()
        true
    }

    fun parseRoleIds(roleIdsJson: String): List<String> {
        return try {
            val arr = JSONArray(roleIdsJson)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    // --- TAGS (STAGES & ACTIVITIES) ---
    fun getAllTagsFlow(): Flow<List<TagEntity>> = dao.getAllTags().flowOn(Dispatchers.IO)

    suspend fun createStage(name: String, color: String): TagEntity = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val tag = TagEntity(
            name = name.trim(),
            parentId = null,
            color = color,
            hidden = false,
            createdAt = now,
            updatedAt = now
        )
        dao.insertTag(tag)
        incrementUnsentChanges()
        tag
    }

    suspend fun createActivity(stageId: String, name: String, color: String): TagEntity = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val tag = TagEntity(
            name = name.trim(),
            parentId = stageId,
            color = color,
            hidden = false,
            createdAt = now,
            updatedAt = now
        )
        dao.insertTag(tag)
        incrementUnsentChanges()
        tag
    }

    suspend fun updateTag(tag: TagEntity, newName: String, newColor: String) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        dao.updateTag(tag.copy(name = newName.trim(), color = newColor, updatedAt = now))
        incrementUnsentChanges()
    }

    suspend fun toggleTagHidden(tag: TagEntity) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val newHidden = !tag.hidden
        dao.updateTag(tag.copy(hidden = newHidden, updatedAt = now))

        // If hiding a stage, hide its activities as well in selector representation
        if (tag.parentId == null) {
            val activities = dao.getActivitiesForStage(tag.id)
            for (act in activities) {
                dao.updateTag(act.copy(hidden = newHidden, updatedAt = now))
            }
        }
        incrementUnsentChanges()
    }

    suspend fun deleteTag(tag: TagEntity): Boolean = withContext(Dispatchers.IO) {
        if (tag.builtInKey != null) return@withContext false // Built-in stages cannot be deleted
        val usage = countUsage("tag", tag.id)
        if (usage > 0) return@withContext false

        // If stage, ensure it has no active activities
        if (tag.parentId == null) {
            val activities = dao.getActivitiesForStage(tag.id)
            if (activities.isNotEmpty()) return@withContext false
        }

        val now = Instant.now().toString()
        dao.updateTag(tag.copy(deleted = true, deletedAt = now, updatedAt = now))
        incrementUnsentChanges()
        true
    }

    /**
     * Merge source activity into destination activity (Decision 124, 164, 279):
     * - Only activities can be merged (not stages).
     * - Transfers references to destination activity.
     * - Accumulates usageCount.
     * - Deletes source activity.
     */
    suspend fun mergeActivities(sourceTagId: String, destTagId: String): Boolean = withContext(Dispatchers.IO) {
        val source = dao.getTagById(sourceTagId) ?: return@withContext false
        val dest = dao.getTagById(destTagId) ?: return@withContext false

        if (source.parentId == null || dest.parentId == null) return@withContext false

        // Transfer all references via registered hooks
        for (updater in tagReferenceUpdaters) {
            updater(sourceTagId, destTagId)
        }

        val now = Instant.now().toString()
        // Accumulate usage count
        val updatedDest = dest.copy(
            usageCount = dest.usageCount + source.usageCount,
            updatedAt = now
        )
        dao.updateTag(updatedDest)

        // Delete source tag
        dao.updateTag(source.copy(deleted = true, deletedAt = now, updatedAt = now))
        incrementUnsentChanges()
        true
    }

    // --- TASKS ---
    fun getAllTasksFlow(): Flow<List<TaskEntity>> = dao.getAllTasks().flowOn(Dispatchers.IO)

    suspend fun getAllTasksList(): List<TaskEntity> = withContext(Dispatchers.IO) {
        dao.getAllTasksList()
    }

    suspend fun createTask(
        title: String,
        farmId: String?,
        plotId: String?,
        startDate: String,
        endDate: String,
        priority: String,
        assigneePersonIds: List<String>,
        assigneeCrewId: String? = null,
        tagId: String?,
        note: String
    ): TaskEntity = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val task = TaskEntity(
            title = title.trim(),
            farmId = farmId,
            plotId = plotId,
            startDate = startDate,
            endDate = endDate,
            priority = priority,
            assigneePersonIds = JSONArray(assigneePersonIds).toString(),
            assigneeCrewId = assigneeCrewId,
            tagId = tagId,
            note = note.trim(),
            status = "open",
            closedAt = null,
            rescheduleCount = 0,
            createdAt = now,
            updatedAt = now
        )
        dao.insertTask(task)

        // Increment tag usageCount if assigned (Decision 180)
        if (tagId != null) {
            val tag = dao.getTagById(tagId)
            if (tag != null) {
                dao.updateTag(tag.copy(usageCount = tag.usageCount + 1, updatedAt = now))
            }
        }

        incrementUnsentChanges()
        task
    }

    suspend fun updateTask(task: TaskEntity) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        dao.updateTask(task.copy(updatedAt = now))
        incrementUnsentChanges()
    }

    suspend fun toggleTaskStatus(taskId: String, newStatus: String): TaskEntity? = withContext(Dispatchers.IO) {
        val task = dao.getTaskById(taskId) ?: return@withContext null
        val now = Instant.now().toString()
        val closedAt = if (newStatus == "open") null else now
        val updated = task.copy(status = newStatus, closedAt = closedAt, updatedAt = now)
        dao.updateTask(updated)
        incrementUnsentChanges()
        updated
    }

    suspend fun rescheduleTask(taskId: String, newStartDate: String) = withContext(Dispatchers.IO) {
        val task = dao.getTaskById(taskId) ?: return@withContext
        val durationDays = JalaliDateHelper.daysBetween(task.startDate, task.endDate)
        val newEndDate = JalaliDateHelper.addDays(newStartDate, durationDays)
        val now = Instant.now().toString()
        dao.updateTask(
            task.copy(
                startDate = newStartDate,
                endDate = newEndDate,
                rescheduleCount = task.rescheduleCount + 1,
                updatedAt = now
            )
        )
        incrementUnsentChanges()
    }

    suspend fun deleteTask(taskId: String) = withContext(Dispatchers.IO) {
        val task = dao.getTaskById(taskId) ?: return@withContext
        val now = Instant.now().toString()
        dao.updateTask(task.copy(deleted = true, deletedAt = now, updatedAt = now))
        incrementUnsentChanges()
    }

    // --- WORKFORCE: CREWS & WAGES (Section 1) ---
    fun getAllCrewsFlow(): Flow<List<CrewEntity>> = dao.getAllCrews().flowOn(Dispatchers.IO)
    fun getAllCrewMembersFlow(): Flow<List<CrewMemberEntity>> = dao.getAllCrewMembers().flowOn(Dispatchers.IO)
    fun getAllWageRatesFlow(): Flow<List<WageRateEntity>> = dao.getAllWageRates().flowOn(Dispatchers.IO)

    suspend fun getAllCrewsList(): List<CrewEntity> = withContext(Dispatchers.IO) { dao.getAllCrewsList() }
    suspend fun getAllCrewMembersList(): List<CrewMemberEntity> = withContext(Dispatchers.IO) { dao.getAllCrewMembersList() }
    suspend fun getAllWageRatesList(): List<WageRateEntity> = withContext(Dispatchers.IO) { dao.getAllWageRatesList() }

    suspend fun createCrew(
        name: String,
        kind: String, // "members" | "count"
        foremanId: String?,
        dailyWage: Long,
        effectiveFrom: String,
        note: String
    ): CrewEntity = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        if (foremanId != null) {
            ensureForemanRole(foremanId)
        }
        val crew = CrewEntity(
            name = name.trim(),
            kind = kind,
            foremanId = foremanId,
            note = note.trim(),
            active = true,
            createdAt = now,
            updatedAt = now
        )
        dao.insertCrew(crew)
        val wageRate = WageRateEntity(
            crewId = crew.id,
            personId = null,
            dailyWage = dailyWage,
            effectiveFrom = effectiveFrom,
            createdAt = now,
            updatedAt = now
        )
        dao.insertWageRate(wageRate)
        incrementUnsentChanges()
        crew
    }

    suspend fun updateCrew(crew: CrewEntity) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        if (crew.foremanId != null) {
            ensureForemanRole(crew.foremanId)
        }
        dao.updateCrew(crew.copy(updatedAt = now))
        incrementUnsentChanges()
    }

    suspend fun toggleCrewActive(crew: CrewEntity) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        dao.updateCrew(crew.copy(active = !crew.active, updatedAt = now))
        incrementUnsentChanges()
    }

    suspend fun deleteCrew(crewId: String): Boolean = withContext(Dispatchers.IO) {
        val crew = dao.getCrewById(crewId) ?: return@withContext false
        val usage = countUsage("crew", crewId)
        if (usage == 0) {
            val now = Instant.now().toString()
            dao.updateCrew(crew.copy(deleted = true, deletedAt = now, updatedAt = now))
            for (m in dao.getAllCrewMembersList().filter { it.crewId == crewId }) {
                dao.updateCrewMember(m.copy(deleted = true, deletedAt = now, updatedAt = now))
            }
            for (wr in dao.getAllWageRatesList().filter { it.crewId == crewId }) {
                dao.updateWageRate(wr.copy(deleted = true, deletedAt = now, updatedAt = now))
            }
            incrementUnsentChanges()
            true
        } else {
            toggleCrewActive(crew)
            false
        }
    }

    suspend fun addCrewMember(crewId: String, personId: String): CrewMemberEntity = withContext(Dispatchers.IO) {
        val existing = dao.getAllCrewMembersList().find { it.crewId == crewId && it.personId == personId }
        val now = Instant.now().toString()
        if (existing != null) {
            val updated = existing.copy(active = true, deleted = false, deletedAt = null, updatedAt = now)
            dao.updateCrewMember(updated)
            incrementUnsentChanges()
            updated
        } else {
            val member = CrewMemberEntity(
                crewId = crewId,
                personId = personId,
                active = true,
                createdAt = now,
                updatedAt = now
            )
            dao.insertCrewMember(member)
            incrementUnsentChanges()
            member
        }
    }

    suspend fun deactivateCrewMember(crewMemberId: String) = withContext(Dispatchers.IO) {
        val members = dao.getAllCrewMembersList()
        val member = members.find { it.id == crewMemberId } ?: return@withContext
        val now = Instant.now().toString()
        dao.updateCrewMember(member.copy(active = false, updatedAt = now))
        incrementUnsentChanges()
    }

    suspend fun setDailyWage(crewId: String, personId: String?, dailyWage: Long, effectiveFrom: String): WageRateEntity = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val rate = WageRateEntity(
            crewId = crewId,
            personId = personId,
            dailyWage = dailyWage,
            effectiveFrom = effectiveFrom,
            createdAt = now,
            updatedAt = now
        )
        dao.insertWageRate(rate)
        incrementUnsentChanges()
        rate
    }

    private suspend fun ensureForemanRole(personId: String) {
        val person = dao.getPersonById(personId) ?: return
        val roles = dao.getAllRolesList()
        val foremanRole = roles.find { it.builtInKey == "foreman" || it.name == "سرکارگر" } ?: return
        val currentRoles = parseRoleIds(person.roleIds).toMutableList()
        if (!currentRoles.contains(foremanRole.id)) {
            currentRoles.add(foremanRole.id)
            val now = Instant.now().toString()
            dao.updatePerson(person.copy(roleIds = JSONArray(currentRoles).toString(), updatedAt = now))
        }
    }

    suspend fun ensureWorkerRole(personId: String) = withContext(Dispatchers.IO) {
        val person = dao.getPersonById(personId) ?: return@withContext
        val roles = dao.getAllRolesList()
        val workerRole = roles.find { it.builtInKey == "worker" || it.name == "کارگر" } ?: return@withContext
        val currentRoles = parseRoleIds(person.roleIds).toMutableList()
        if (!currentRoles.contains(workerRole.id)) {
            currentRoles.add(workerRole.id)
            val now = Instant.now().toString()
            dao.updatePerson(person.copy(roleIds = JSONArray(currentRoles).toString(), updatedAt = now))
        }
    }

    // --- ATTENDANCE (Section 2) ---
    fun getAllAttendanceFlow(): Flow<List<AttendanceEntity>> = dao.getAllAttendance().flowOn(Dispatchers.IO)
    suspend fun getAllAttendanceList(): List<AttendanceEntity> = withContext(Dispatchers.IO) { dao.getAllAttendanceList() }

    suspend fun getForemanCoefficients(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val str = dao.getSetting("foremanCoefficients") ?: """{"present":2,"absent":1}"""
        try {
            val obj = JSONObject(str)
            val cp = obj.optInt("present", 2)
            val ca = obj.optInt("absent", 1)
            Pair(cp, ca)
        } catch (_: Exception) {
            Pair(2, 1)
        }
    }

    suspend fun setForemanCoefficients(present: Int, absent: Int) = withContext(Dispatchers.IO) {
        val obj = JSONObject().apply {
            put("present", present)
            put("absent", absent)
        }
        dao.setSetting(SettingEntity("foremanCoefficients", obj.toString()))
    }

    suspend fun createAttendance(
        date: String,
        farmId: String?,
        plotId: String?,
        crewId: String,
        tagId: String,
        lines: String,
        headcount: Int?,
        countTicks: Int?,
        foreman: String?,
        note: String
    ): AttendanceEntity = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val (cp, ca) = getForemanCoefficients()
        val att = AttendanceEntity(
            date = date,
            farmId = farmId,
            plotId = plotId,
            crewId = crewId,
            tagId = tagId,
            lines = lines,
            headcount = headcount,
            countTicks = countTicks,
            foreman = foreman,
            coefPresent = cp,
            coefAbsent = ca,
            note = note.trim(),
            createdAt = now,
            updatedAt = now
        )
        dao.insertAttendance(att)

        val tag = dao.getTagById(tagId)
        if (tag != null) {
            dao.updateTag(tag.copy(usageCount = tag.usageCount + 1, updatedAt = now))
        }

        incrementUnsentChanges()
        att
    }

    suspend fun updateAttendance(
        att: AttendanceEntity,
        changeDescription: String = "ویرایش ثبت حضور"
    ) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val old = dao.getAttendanceById(att.id)
        if (old != null) {
            val hist = HistoryEntity(
                entityType = "attendance",
                entityId = att.id,
                at = now,
                changes = JSONArray().apply {
                    put(JSONObject().apply {
                        put("field", "all")
                        put("description", changeDescription)
                    })
                }.toString()
            )
            dao.insertHistory(hist)
        }
        dao.updateAttendance(att.copy(updatedAt = now))
        incrementUnsentChanges()
    }

    suspend fun deleteAttendance(attendanceId: String) = withContext(Dispatchers.IO) {
        val att = dao.getAttendanceById(attendanceId) ?: return@withContext
        val now = Instant.now().toString()
        dao.updateAttendance(att.copy(deleted = true, deletedAt = now, updatedAt = now))
        incrementUnsentChanges()
    }

    // --- MY COUNTS (Section 2) ---
    fun getAllMyCountsFlow(): Flow<List<MyCountEntity>> = dao.getAllMyCounts().flowOn(Dispatchers.IO)
    suspend fun getAllMyCountsList(): List<MyCountEntity> = withContext(Dispatchers.IO) { dao.getAllMyCountsList() }

    suspend fun createMyCount(
        date: String,
        crewId: String,
        headcount: Int,
        note: String
    ): MyCountEntity = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val mc = MyCountEntity(
            date = date,
            crewId = crewId,
            headcount = headcount,
            note = note.trim(),
            createdAt = now,
            updatedAt = now
        )
        dao.insertMyCount(mc)
        incrementUnsentChanges()
        mc
    }

    suspend fun deleteMyCount(id: String) = withContext(Dispatchers.IO) {
        val mc = dao.getMyCountById(id) ?: return@withContext
        val now = Instant.now().toString()
        dao.updateMyCount(mc.copy(deleted = true, deletedAt = now, updatedAt = now))
        incrementUnsentChanges()
    }

    // --- PIECE WORK (Section 3) ---
    fun getAllPieceWorkFlow(): Flow<List<PieceWorkEntity>> = dao.getAllPieceWork().flowOn(Dispatchers.IO)
    suspend fun getAllPieceWorkList(): List<PieceWorkEntity> = withContext(Dispatchers.IO) { dao.getAllPieceWorkList() }

    suspend fun createPieceWork(
        date: String,
        farmId: String?,
        plotId: String?,
        tagId: String,
        jobType: String,
        size: String,
        rate: Long,
        lines: String,
        note: String
    ): PieceWorkEntity = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val pw = PieceWorkEntity(
            date = date,
            farmId = farmId,
            plotId = plotId,
            tagId = tagId,
            jobType = jobType.trim(),
            size = size.trim(),
            rate = rate,
            lines = lines,
            note = note.trim(),
            createdAt = now,
            updatedAt = now
        )
        dao.insertPieceWork(pw)

        val tag = dao.getTagById(tagId)
        if (tag != null) {
            dao.updateTag(tag.copy(usageCount = tag.usageCount + 1, updatedAt = now))
        }

        incrementUnsentChanges()
        pw
    }

    suspend fun updatePieceWork(
        pw: PieceWorkEntity,
        changeDescription: String = "ویرایش کار دانه‌ای"
    ) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val old = dao.getPieceWorkById(pw.id)
        if (old != null) {
            val hist = HistoryEntity(
                entityType = "piece_work",
                entityId = pw.id,
                at = now,
                changes = JSONArray().apply {
                    put(JSONObject().apply {
                        put("field", "all")
                        put("description", changeDescription)
                    })
                }.toString()
            )
            dao.insertHistory(hist)
        }
        dao.updatePieceWork(pw.copy(updatedAt = now))
        incrementUnsentChanges()
    }

    suspend fun deletePieceWork(pieceWorkId: String) = withContext(Dispatchers.IO) {
        val pw = dao.getPieceWorkById(pieceWorkId) ?: return@withContext
        val now = Instant.now().toString()
        dao.updatePieceWork(pw.copy(deleted = true, deletedAt = now, updatedAt = now))
        incrementUnsentChanges()
    }

    // --- LEDGER ENTRIES (Section 4) ---
    fun getAllLedgerEntriesFlow(): Flow<List<LedgerEntryEntity>> = dao.getAllLedgerEntries().flowOn(Dispatchers.IO)
    suspend fun getAllLedgerEntriesList(): List<LedgerEntryEntity> = withContext(Dispatchers.IO) { dao.getAllLedgerEntriesList() }

    suspend fun createLedgerEntry(
        accountType: String,
        accountId: String,
        date: String,
        kind: String,
        amount: Long,
        note: String
    ): LedgerEntryEntity = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val entry = LedgerEntryEntity(
            accountType = accountType,
            accountId = accountId,
            date = date,
            kind = kind,
            amount = amount,
            note = note.trim(),
            voided = false,
            voidReason = null,
            createdAt = now,
            updatedAt = now
        )
        dao.insertLedgerEntry(entry)
        incrementUnsentChanges()
        entry
    }

    suspend fun updateLedgerEntry(
        entry: LedgerEntryEntity,
        changeDescription: String = "ویرایش ردیف دستی دفتر"
    ) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val old = dao.getLedgerEntryById(entry.id)
        if (old != null) {
            val hist = HistoryEntity(
                entityType = "ledger_entry",
                entityId = entry.id,
                at = now,
                changes = JSONArray().apply {
                    put(JSONObject().apply {
                        put("field", "all")
                        put("description", changeDescription)
                    })
                }.toString()
            )
            dao.insertHistory(hist)
        }
        dao.updateLedgerEntry(entry.copy(updatedAt = now))
        incrementUnsentChanges()
    }

    suspend fun voidLedgerEntry(entryId: String, reason: String) = withContext(Dispatchers.IO) {
        val entry = dao.getLedgerEntryById(entryId) ?: return@withContext
        val now = Instant.now().toString()
        dao.updateLedgerEntry(entry.copy(voided = true, voidReason = reason.trim(), updatedAt = now))
        incrementUnsentChanges()
    }

    suspend fun deleteLedgerEntry(entryId: String) = withContext(Dispatchers.IO) {
        val entry = dao.getLedgerEntryById(entryId) ?: return@withContext
        val now = Instant.now().toString()
        dao.updateLedgerEntry(entry.copy(deleted = true, deletedAt = now, updatedAt = now))
        incrementUnsentChanges()
    }

    // --- PAYMENTS (Section 4) ---
    fun getAllPaymentsFlow(): Flow<List<PaymentEntity>> = dao.getAllPayments().flowOn(Dispatchers.IO)
    suspend fun getAllPaymentsList(): List<PaymentEntity> = withContext(Dispatchers.IO) { dao.getAllPaymentsList() }

    suspend fun createPayment(
        date: String,
        amount: Long,
        handedToPersonId: String?,
        targetType: String,
        targetId: String,
        method: String?,
        note: String,
        shares: String
    ): PaymentEntity = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val payment = PaymentEntity(
            date = date,
            amount = amount,
            handedToPersonId = handedToPersonId,
            targetType = targetType,
            targetId = targetId,
            method = method,
            note = note.trim(),
            shares = shares,
            createdAt = now,
            updatedAt = now
        )
        dao.insertPayment(payment)
        incrementUnsentChanges()
        payment
    }

    suspend fun updatePayment(
        payment: PaymentEntity,
        changeDescription: String = "ویرایش پرداخت"
    ) = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val old = dao.getPaymentById(payment.id)
        if (old != null) {
            val hist = HistoryEntity(
                entityType = "payment",
                entityId = payment.id,
                at = now,
                changes = JSONArray().apply {
                    put(JSONObject().apply {
                        put("field", "all")
                        put("description", changeDescription)
                    })
                }.toString()
            )
            dao.insertHistory(hist)
        }
        dao.updatePayment(payment.copy(updatedAt = now))
        incrementUnsentChanges()
    }

    suspend fun deletePayment(paymentId: String) = withContext(Dispatchers.IO) {
        val payment = dao.getPaymentById(paymentId) ?: return@withContext
        val now = Instant.now().toString()
        dao.updatePayment(payment.copy(deleted = true, deletedAt = now, updatedAt = now))
        incrementUnsentChanges()
    }

    // --- WAITING LIST (Section 4) ---
    fun getAllWaitingListFlow(): Flow<List<WaitingListEntity>> = dao.getAllWaitingList().flowOn(Dispatchers.IO)
    suspend fun getAllWaitingListList(): List<WaitingListEntity> = withContext(Dispatchers.IO) { dao.getAllWaitingListList() }

    suspend fun createWaitingListItem(
        accountType: String,
        accountId: String,
        amount: Long?,
        date: String,
        note: String
    ): WaitingListEntity = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val item = WaitingListEntity(
            accountType = accountType,
            accountId = accountId,
            amount = amount,
            date = date,
            note = note.trim(),
            status = "open",
            createdAt = now,
            updatedAt = now
        )
        dao.insertWaitingList(item)
        incrementUnsentChanges()
        item
    }

    suspend fun updateWaitingListItemStatus(id: String, status: String) = withContext(Dispatchers.IO) {
        val item = dao.getWaitingListById(id) ?: return@withContext
        val now = Instant.now().toString()
        dao.updateWaitingList(item.copy(status = status, updatedAt = now))
        incrementUnsentChanges()
    }

    suspend fun deleteWaitingListItem(id: String) = withContext(Dispatchers.IO) {
        val item = dao.getWaitingListById(id) ?: return@withContext
        val now = Instant.now().toString()
        dao.updateWaitingList(item.copy(deleted = true, deletedAt = now, updatedAt = now))
        incrementUnsentChanges()
    }

    // --- SETTINGS ---
    fun getAllSettingsFlow(): Flow<List<SettingEntity>> = dao.getAllSettings().flowOn(Dispatchers.IO)

    suspend fun getSetting(key: String): String? = withContext(Dispatchers.IO) {
        dao.getSetting(key)
    }

    suspend fun setSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        dao.setSetting(SettingEntity(key, value))
    }

    // --- SNAPSHOTS (AUTOMATIC DAILY BACKUPS) ---
    fun getRecentSnapshotsFlow(): Flow<List<SnapshotEntity>> = dao.getRecentSnapshots().flowOn(Dispatchers.IO)

    suspend fun checkAndCreateDailySnapshot() = withContext(Dispatchers.IO) {
        val todayStr = JalaliDateHelper.today().isoFormat
        val existing = dao.getSnapshotByDate(todayStr)
        if (existing == null) {
            createSnapshot(todayStr)
        }
    }

    suspend fun createSnapshot(dateStr: String? = null): SnapshotEntity = withContext(Dispatchers.IO) {
        val date = dateStr ?: JalaliDateHelper.today().isoFormat
        val backupJson = generateExportJsonString()

        val farmsCount = dao.getAllFarmsList().size
        val personsCount = dao.getAllPersonsList().size
        val summary = "$farmsCount مزرعه، $personsCount شخص"

        val snapshot = SnapshotEntity(
            date = date,
            createdAt = Instant.now().toString(),
            summary = summary,
            data = backupJson
        )
        dao.insertSnapshot(snapshot)
        dao.pruneSnapshots()
        snapshot
    }

    // --- BACKUP EXPORT & IMPORT ---
    suspend fun generateExportJsonString(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "farm-manager")
        root.put("schemaVersion", 1)
        root.put("appVersion", "1.0")
        root.put("exportedAt", Instant.now().toString())

        val tables = JSONObject()

        // Farms
        val farmsArr = JSONArray()
        for (f in dao.getAllFarmsList()) {
            val o = JSONObject()
            o.put("id", f.id)
            o.put("name", f.name)
            o.put("areaM2", f.areaM2 ?: JSONObject.NULL)
            o.put("ownership", f.ownership ?: JSONObject.NULL)
            o.put("waterSource", f.waterSource ?: JSONObject.NULL)
            o.put("note", f.note)
            o.put("active", f.active)
            o.put("createdAt", f.createdAt)
            o.put("updatedAt", f.updatedAt)
            o.put("createdBy", f.createdBy)
            o.put("deleted", f.deleted)
            o.put("deletedAt", f.deletedAt ?: JSONObject.NULL)
            farmsArr.put(o)
        }
        tables.put("farms", farmsArr)

        // Plots
        val plotsArr = JSONArray()
        for (p in dao.getAllPlotsList()) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("farmId", p.farmId)
            o.put("name", p.name)
            o.put("workType", p.workType)
            o.put("areaM2", p.areaM2 ?: JSONObject.NULL)
            o.put("note", p.note)
            o.put("active", p.active)
            o.put("isDefault", p.isDefault)
            o.put("createdAt", p.createdAt)
            o.put("updatedAt", p.updatedAt)
            o.put("createdBy", p.createdBy)
            o.put("deleted", p.deleted)
            o.put("deletedAt", p.deletedAt ?: JSONObject.NULL)
            plotsArr.put(o)
        }
        tables.put("plots", plotsArr)

        // Persons
        val personsArr = JSONArray()
        for (p in dao.getAllPersonsList()) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("name", p.name)
            o.put("phone", p.phone)
            o.put("roleIds", p.roleIds)
            o.put("note", p.note)
            o.put("active", p.active)
            o.put("createdAt", p.createdAt)
            o.put("updatedAt", p.updatedAt)
            o.put("createdBy", p.createdBy)
            o.put("deleted", p.deleted)
            o.put("deletedAt", p.deletedAt ?: JSONObject.NULL)
            personsArr.put(o)
        }
        tables.put("persons", personsArr)

        // Roles
        val rolesArr = JSONArray()
        for (r in dao.getAllRolesList()) {
            val o = JSONObject()
            o.put("id", r.id)
            o.put("name", r.name)
            o.put("builtInKey", r.builtInKey ?: JSONObject.NULL)
            o.put("hidden", r.hidden)
            o.put("createdAt", r.createdAt)
            o.put("updatedAt", r.updatedAt)
            o.put("createdBy", r.createdBy)
            o.put("deleted", r.deleted)
            o.put("deletedAt", r.deletedAt ?: JSONObject.NULL)
            rolesArr.put(o)
        }
        tables.put("roles", rolesArr)

        // Tags
        val tagsArr = JSONArray()
        for (t in dao.getAllTagsList()) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("parentId", t.parentId ?: JSONObject.NULL)
            o.put("name", t.name)
            o.put("color", t.color)
            o.put("hidden", t.hidden)
            o.put("builtInKey", t.builtInKey ?: JSONObject.NULL)
            o.put("sortOrder", t.sortOrder)
            o.put("usageCount", t.usageCount)
            o.put("createdAt", t.createdAt)
            o.put("updatedAt", t.updatedAt)
            o.put("createdBy", t.createdBy)
            o.put("deleted", t.deleted)
            o.put("deletedAt", t.deletedAt ?: JSONObject.NULL)
            tagsArr.put(o)
        }
        tables.put("tags", tagsArr)

        // Tasks (Step 2 & 3)
        val tasksArr = JSONArray()
        for (tk in dao.getAllTasksList()) {
            val o = JSONObject()
            o.put("id", tk.id)
            o.put("title", tk.title)
            o.put("farmId", tk.farmId ?: JSONObject.NULL)
            o.put("plotId", tk.plotId ?: JSONObject.NULL)
            o.put("startDate", tk.startDate)
            o.put("endDate", tk.endDate)
            o.put("priority", tk.priority)
            o.put("assigneePersonIds", tk.assigneePersonIds)
            o.put("assigneeCrewId", tk.assigneeCrewId ?: JSONObject.NULL)
            o.put("tagId", tk.tagId ?: JSONObject.NULL)
            o.put("note", tk.note)
            o.put("status", tk.status)
            o.put("closedAt", tk.closedAt ?: JSONObject.NULL)
            o.put("rescheduleCount", tk.rescheduleCount)
            o.put("createdAt", tk.createdAt)
            o.put("updatedAt", tk.updatedAt)
            o.put("createdBy", tk.createdBy)
            o.put("deleted", tk.deleted)
            o.put("deletedAt", tk.deletedAt ?: JSONObject.NULL)
            tasksArr.put(o)
        }
        tables.put("tasks", tasksArr)

        // Crews (Step 3)
        val crewsArr = JSONArray()
        for (c in dao.getAllCrewsList()) {
            val o = JSONObject()
            o.put("id", c.id)
            o.put("name", c.name)
            o.put("kind", c.kind)
            o.put("foremanId", c.foremanId ?: JSONObject.NULL)
            o.put("note", c.note)
            o.put("active", c.active)
            o.put("createdAt", c.createdAt)
            o.put("updatedAt", c.updatedAt)
            o.put("createdBy", c.createdBy)
            o.put("deleted", c.deleted)
            o.put("deletedAt", c.deletedAt ?: JSONObject.NULL)
            crewsArr.put(o)
        }
        tables.put("crews", crewsArr)

        // Crew Members
        val crewMembersArr = JSONArray()
        for (cm in dao.getAllCrewMembersList()) {
            val o = JSONObject()
            o.put("id", cm.id)
            o.put("crewId", cm.crewId)
            o.put("personId", cm.personId)
            o.put("active", cm.active)
            o.put("createdAt", cm.createdAt)
            o.put("updatedAt", cm.updatedAt)
            o.put("createdBy", cm.createdBy)
            o.put("deleted", cm.deleted)
            o.put("deletedAt", cm.deletedAt ?: JSONObject.NULL)
            crewMembersArr.put(o)
        }
        tables.put("crew_members", crewMembersArr)

        // Wage Rates
        val wageRatesArr = JSONArray()
        for (wr in dao.getAllWageRatesList()) {
            val o = JSONObject()
            o.put("id", wr.id)
            o.put("crewId", wr.crewId)
            o.put("personId", wr.personId ?: JSONObject.NULL)
            o.put("dailyWage", wr.dailyWage)
            o.put("effectiveFrom", wr.effectiveFrom)
            o.put("createdAt", wr.createdAt)
            o.put("updatedAt", wr.updatedAt)
            o.put("createdBy", wr.createdBy)
            o.put("deleted", wr.deleted)
            o.put("deletedAt", wr.deletedAt ?: JSONObject.NULL)
            wageRatesArr.put(o)
        }
        tables.put("wage_rates", wageRatesArr)

        // Attendance
        val attendanceArr = JSONArray()
        for (a in dao.getAllAttendanceList()) {
            val o = JSONObject()
            o.put("id", a.id)
            o.put("date", a.date)
            o.put("farmId", a.farmId ?: JSONObject.NULL)
            o.put("plotId", a.plotId ?: JSONObject.NULL)
            o.put("crewId", a.crewId)
            o.put("tagId", a.tagId)
            o.put("lines", a.lines)
            o.put("headcount", a.headcount ?: JSONObject.NULL)
            o.put("countTicks", a.countTicks ?: JSONObject.NULL)
            o.put("foreman", a.foreman ?: JSONObject.NULL)
            o.put("coefPresent", a.coefPresent)
            o.put("coefAbsent", a.coefAbsent)
            o.put("note", a.note)
            o.put("createdAt", a.createdAt)
            o.put("updatedAt", a.updatedAt)
            o.put("createdBy", a.createdBy)
            o.put("deleted", a.deleted)
            o.put("deletedAt", a.deletedAt ?: JSONObject.NULL)
            attendanceArr.put(o)
        }
        tables.put("attendance", attendanceArr)

        // My Counts
        val myCountsArr = JSONArray()
        for (mc in dao.getAllMyCountsList()) {
            val o = JSONObject()
            o.put("id", mc.id)
            o.put("date", mc.date)
            o.put("crewId", mc.crewId)
            o.put("headcount", mc.headcount)
            o.put("note", mc.note)
            o.put("createdAt", mc.createdAt)
            o.put("updatedAt", mc.updatedAt)
            o.put("createdBy", mc.createdBy)
            o.put("deleted", mc.deleted)
            o.put("deletedAt", mc.deletedAt ?: JSONObject.NULL)
            myCountsArr.put(o)
        }
        tables.put("my_counts", myCountsArr)

        // Piece Work
        val pieceWorkArr = JSONArray()
        for (pw in dao.getAllPieceWorkList()) {
            val o = JSONObject()
            o.put("id", pw.id)
            o.put("date", pw.date)
            o.put("farmId", pw.farmId ?: JSONObject.NULL)
            o.put("plotId", pw.plotId ?: JSONObject.NULL)
            o.put("tagId", pw.tagId)
            o.put("jobType", pw.jobType)
            o.put("size", pw.size)
            o.put("rate", pw.rate)
            o.put("lines", pw.lines)
            o.put("note", pw.note)
            o.put("createdAt", pw.createdAt)
            o.put("updatedAt", pw.updatedAt)
            o.put("createdBy", pw.createdBy)
            o.put("deleted", pw.deleted)
            o.put("deletedAt", pw.deletedAt ?: JSONObject.NULL)
            pieceWorkArr.put(o)
        }
        tables.put("piece_work", pieceWorkArr)

        // Ledger Entries
        val ledgerEntriesArr = JSONArray()
        for (le in dao.getAllLedgerEntriesList()) {
            val o = JSONObject()
            o.put("id", le.id)
            o.put("accountType", le.accountType)
            o.put("accountId", le.accountId)
            o.put("date", le.date)
            o.put("kind", le.kind)
            o.put("amount", le.amount)
            o.put("note", le.note)
            o.put("voided", le.voided)
            o.put("voidReason", le.voidReason ?: JSONObject.NULL)
            o.put("createdAt", le.createdAt)
            o.put("updatedAt", le.updatedAt)
            o.put("createdBy", le.createdBy)
            o.put("deleted", le.deleted)
            o.put("deletedAt", le.deletedAt ?: JSONObject.NULL)
            ledgerEntriesArr.put(o)
        }
        tables.put("ledger_entries", ledgerEntriesArr)

        // Payments
        val paymentsArr = JSONArray()
        for (p in dao.getAllPaymentsList()) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("date", p.date)
            o.put("amount", p.amount)
            o.put("handedToPersonId", p.handedToPersonId ?: JSONObject.NULL)
            o.put("targetType", p.targetType)
            o.put("targetId", p.targetId)
            o.put("method", p.method ?: JSONObject.NULL)
            o.put("note", p.note)
            o.put("shares", p.shares)
            o.put("createdAt", p.createdAt)
            o.put("updatedAt", p.updatedAt)
            o.put("createdBy", p.createdBy)
            o.put("deleted", p.deleted)
            o.put("deletedAt", p.deletedAt ?: JSONObject.NULL)
            paymentsArr.put(o)
        }
        tables.put("payments", paymentsArr)

        // Waiting List
        val waitingListArr = JSONArray()
        for (w in dao.getAllWaitingListList()) {
            val o = JSONObject()
            o.put("id", w.id)
            o.put("accountType", w.accountType)
            o.put("accountId", w.accountId)
            o.put("amount", w.amount ?: JSONObject.NULL)
            o.put("date", w.date)
            o.put("note", w.note)
            o.put("status", w.status)
            o.put("createdAt", w.createdAt)
            o.put("updatedAt", w.updatedAt)
            o.put("createdBy", w.createdBy)
            o.put("deleted", w.deleted)
            o.put("deletedAt", w.deletedAt ?: JSONObject.NULL)
            waitingListArr.put(o)
        }
        tables.put("waiting_list", waitingListArr)

        root.put("schemaVersion", 3)
        root.put("tables", tables)
        root.toString(2)
    }

    data class BackupValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val exportedAtJalali: String = "",
        val farmsCount: Int = 0,
        val personsCount: Int = 0,
        val tagsCount: Int = 0,
        val tasksCount: Int = 0
    )

    fun validateBackupContent(jsonString: String): BackupValidationResult {
        return try {
            val root = JSONObject(jsonString)
            val app = root.optString("app")
            if (app != "farm-manager") {
                return BackupValidationResult(false, "این فایل پشتیبان این اپ نیست")
            }
            val schemaVersion = root.optInt("schemaVersion", 1)
            if (schemaVersion > 3) {
                return BackupValidationResult(false, "این فایل با نسخه‌ی جدیدتر اپ ساخته شده؛ اول اپ را به‌روز کن")
            }

            val exportedAtIso = root.optString("exportedAt", "")
            val jalaliDate = if (exportedAtIso.isNotEmpty()) {
                JalaliDateHelper.fromIsoDate(exportedAtIso).toDisplayFull()
            } else ""

            val tables = root.optJSONObject("tables") ?: JSONObject()
            val farmsCount = tables.optJSONArray("farms")?.length() ?: 0
            val personsCount = tables.optJSONArray("persons")?.length() ?: 0
            val tagsCount = tables.optJSONArray("tags")?.length() ?: 0
            val tasksCount = tables.optJSONArray("tasks")?.length() ?: 0

            BackupValidationResult(
                isValid = true,
                exportedAtJalali = jalaliDate,
                farmsCount = farmsCount,
                personsCount = personsCount,
                tagsCount = tagsCount,
                tasksCount = tasksCount
            )
        } catch (_: Exception) {
            BackupValidationResult(false, "این فایل پشتیبان این اپ نیست")
        }
    }

    /**
     * Restores data from JSON string.
     * Takes a snapshot first. Replaces tables in single atomic transaction.
     */
    suspend fun restoreFromJson(jsonString: String, isSnapshotRestore: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val validation = validateBackupContent(jsonString)
        if (!validation.isValid) return@withContext false

        // Take snapshot of current data before replacement
        createSnapshot()

        val root = JSONObject(jsonString)
        val tables = root.getJSONObject("tables")

        // Parse entities
        val farms = mutableListOf<FarmEntity>()
        val farmsArr = tables.optJSONArray("farms") ?: JSONArray()
        for (i in 0 until farmsArr.length()) {
            val o = farmsArr.getJSONObject(i)
            farms.add(
                FarmEntity(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    areaM2 = if (o.isNull("areaM2")) null else o.optDouble("areaM2"),
                    ownership = if (o.isNull("ownership")) null else o.optString("ownership"),
                    waterSource = if (o.isNull("waterSource")) null else o.optString("waterSource"),
                    note = o.optString("note", ""),
                    active = o.optBoolean("active", true),
                    createdAt = o.optString("createdAt", Instant.now().toString()),
                    updatedAt = o.optString("updatedAt", Instant.now().toString()),
                    createdBy = o.optString("createdBy", "owner"),
                    deleted = o.optBoolean("deleted", false),
                    deletedAt = if (o.isNull("deletedAt")) null else o.optString("deletedAt")
                )
            )
        }

        val plots = mutableListOf<PlotEntity>()
        val plotsArr = tables.optJSONArray("plots") ?: JSONArray()
        for (i in 0 until plotsArr.length()) {
            val o = plotsArr.getJSONObject(i)
            plots.add(
                PlotEntity(
                    id = o.getString("id"),
                    farmId = o.getString("farmId"),
                    name = o.getString("name"),
                    workType = o.optString("workType", "zeraat"),
                    areaM2 = if (o.isNull("areaM2")) null else o.optDouble("areaM2"),
                    note = o.optString("note", ""),
                    active = o.optBoolean("active", true),
                    isDefault = o.optBoolean("isDefault", false),
                    createdAt = o.optString("createdAt", Instant.now().toString()),
                    updatedAt = o.optString("updatedAt", Instant.now().toString()),
                    createdBy = o.optString("createdBy", "owner"),
                    deleted = o.optBoolean("deleted", false),
                    deletedAt = if (o.isNull("deletedAt")) null else o.optString("deletedAt")
                )
            )
        }

        val persons = mutableListOf<PersonEntity>()
        val personsArr = tables.optJSONArray("persons") ?: JSONArray()
        for (i in 0 until personsArr.length()) {
            val o = personsArr.getJSONObject(i)
            persons.add(
                PersonEntity(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    phone = o.optString("phone", ""),
                    roleIds = o.optString("roleIds", "[]"),
                    note = o.optString("note", ""),
                    active = o.optBoolean("active", true),
                    createdAt = o.optString("createdAt", Instant.now().toString()),
                    updatedAt = o.optString("updatedAt", Instant.now().toString()),
                    createdBy = o.optString("createdBy", "owner"),
                    deleted = o.optBoolean("deleted", false),
                    deletedAt = if (o.isNull("deletedAt")) null else o.optString("deletedAt")
                )
            )
        }

        val roles = mutableListOf<RoleEntity>()
        val rolesArr = tables.optJSONArray("roles") ?: JSONArray()
        for (i in 0 until rolesArr.length()) {
            val o = rolesArr.getJSONObject(i)
            roles.add(
                RoleEntity(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    builtInKey = if (o.isNull("builtInKey")) null else o.optString("builtInKey"),
                    hidden = o.optBoolean("hidden", false),
                    createdAt = o.optString("createdAt", Instant.now().toString()),
                    updatedAt = o.optString("updatedAt", Instant.now().toString()),
                    createdBy = o.optString("createdBy", "owner"),
                    deleted = o.optBoolean("deleted", false),
                    deletedAt = if (o.isNull("deletedAt")) null else o.optString("deletedAt")
                )
            )
        }

        val tags = mutableListOf<TagEntity>()
        val tagsArr = tables.optJSONArray("tags") ?: JSONArray()
        for (i in 0 until tagsArr.length()) {
            val o = tagsArr.getJSONObject(i)
            tags.add(
                TagEntity(
                    id = o.getString("id"),
                    parentId = if (o.isNull("parentId")) null else o.optString("parentId"),
                    name = o.getString("name"),
                    color = o.optString("color", "#2E7D32"),
                    hidden = o.optBoolean("hidden", false),
                    builtInKey = if (o.isNull("builtInKey")) null else o.optString("builtInKey"),
                    sortOrder = o.optInt("sortOrder", 0),
                    usageCount = o.optInt("usageCount", 0),
                    createdAt = o.optString("createdAt", Instant.now().toString()),
                    updatedAt = o.optString("updatedAt", Instant.now().toString()),
                    createdBy = o.optString("createdBy", "owner"),
                    deleted = o.optBoolean("deleted", false),
                    deletedAt = if (o.isNull("deletedAt")) null else o.optString("deletedAt")
                )
            )
        }

        val tasks = mutableListOf<TaskEntity>()
        val tasksArr = tables.optJSONArray("tasks")
        if (tasksArr != null) {
            for (i in 0 until tasksArr.length()) {
                val o = tasksArr.getJSONObject(i)
                tasks.add(
                    TaskEntity(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        farmId = if (o.isNull("farmId")) null else o.optString("farmId"),
                        plotId = if (o.isNull("plotId")) null else o.optString("plotId"),
                        startDate = o.getString("startDate"),
                        endDate = o.getString("endDate"),
                        priority = o.optString("priority", "normal"),
                        assigneePersonIds = o.optString("assigneePersonIds", "[]"),
                        assigneeCrewId = if (o.isNull("assigneeCrewId")) null else o.optString("assigneeCrewId"),
                        tagId = if (o.isNull("tagId")) null else o.optString("tagId"),
                        note = o.optString("note", ""),
                        status = o.optString("status", "open"),
                        closedAt = if (o.isNull("closedAt")) null else o.optString("closedAt"),
                        rescheduleCount = o.optInt("rescheduleCount", 0),
                        createdAt = o.optString("createdAt", Instant.now().toString()),
                        updatedAt = o.optString("updatedAt", Instant.now().toString()),
                        createdBy = o.optString("createdBy", "owner"),
                        deleted = o.optBoolean("deleted", false),
                        deletedAt = if (o.isNull("deletedAt")) null else o.optString("deletedAt")
                    )
                )
            }
        }

        val crews = mutableListOf<CrewEntity>()
        val crewsArr = tables.optJSONArray("crews")
        if (crewsArr != null) {
            for (i in 0 until crewsArr.length()) {
                val o = crewsArr.getJSONObject(i)
                crews.add(
                    CrewEntity(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        kind = o.optString("kind", "members"),
                        foremanId = if (o.isNull("foremanId")) null else o.optString("foremanId"),
                        note = o.optString("note", ""),
                        active = o.optBoolean("active", true),
                        createdAt = o.optString("createdAt", Instant.now().toString()),
                        updatedAt = o.optString("updatedAt", Instant.now().toString()),
                        createdBy = o.optString("createdBy", "owner"),
                        deleted = o.optBoolean("deleted", false),
                        deletedAt = if (o.isNull("deletedAt")) null else o.optString("deletedAt")
                    )
                )
            }
        }

        val crewMembers = mutableListOf<CrewMemberEntity>()
        val crewMembersArr = tables.optJSONArray("crew_members")
        if (crewMembersArr != null) {
            for (i in 0 until crewMembersArr.length()) {
                val o = crewMembersArr.getJSONObject(i)
                crewMembers.add(
                    CrewMemberEntity(
                        id = o.getString("id"),
                        crewId = o.getString("crewId"),
                        personId = o.getString("personId"),
                        active = o.optBoolean("active", true),
                        createdAt = o.optString("createdAt", Instant.now().toString()),
                        updatedAt = o.optString("updatedAt", Instant.now().toString()),
                        createdBy = o.optString("createdBy", "owner"),
                        deleted = o.optBoolean("deleted", false),
                        deletedAt = if (o.isNull("deletedAt")) null else o.optString("deletedAt")
                    )
                )
            }
        }

        val wageRates = mutableListOf<WageRateEntity>()
        val wageRatesArr = tables.optJSONArray("wage_rates")
        if (wageRatesArr != null) {
            for (i in 0 until wageRatesArr.length()) {
                val o = wageRatesArr.getJSONObject(i)
                wageRates.add(
                    WageRateEntity(
                        id = o.getString("id"),
                        crewId = o.getString("crewId"),
                        personId = if (o.isNull("personId")) null else o.optString("personId"),
                        dailyWage = o.optLong("dailyWage", 0L),
                        effectiveFrom = o.getString("effectiveFrom"),
                        createdAt = o.optString("createdAt", Instant.now().toString()),
                        updatedAt = o.optString("updatedAt", Instant.now().toString()),
                        createdBy = o.optString("createdBy", "owner"),
                        deleted = o.optBoolean("deleted", false),
                        deletedAt = if (o.isNull("deletedAt")) null else o.optString("deletedAt")
                    )
                )
            }
        }

        val attendances = mutableListOf<AttendanceEntity>()
        val attendanceArr = tables.optJSONArray("attendance")
        if (attendanceArr != null) {
            for (i in 0 until attendanceArr.length()) {
                val o = attendanceArr.getJSONObject(i)
                attendances.add(
                    AttendanceEntity(
                        id = o.getString("id"),
                        date = o.getString("date"),
                        farmId = if (o.isNull("farmId")) null else o.optString("farmId"),
                        plotId = if (o.isNull("plotId")) null else o.optString("plotId"),
                        crewId = o.getString("crewId"),
                        tagId = o.getString("tagId"),
                        lines = o.optString("lines", "[]"),
                        headcount = if (o.isNull("headcount")) null else o.optInt("headcount"),
                        countTicks = if (o.isNull("countTicks")) null else o.optInt("countTicks"),
                        foreman = if (o.isNull("foreman")) null else o.optString("foreman"),
                        coefPresent = o.optInt("coefPresent", 2),
                        coefAbsent = o.optInt("coefAbsent", 1),
                        note = o.optString("note", ""),
                        createdAt = o.optString("createdAt", Instant.now().toString()),
                        updatedAt = o.optString("updatedAt", Instant.now().toString()),
                        createdBy = o.optString("createdBy", "owner"),
                        deleted = o.optBoolean("deleted", false),
                        deletedAt = if (o.isNull("deletedAt")) null else o.optString("deletedAt")
                    )
                )
            }
        }

        val myCounts = mutableListOf<MyCountEntity>()
        val myCountsArr = tables.optJSONArray("my_counts")
        if (myCountsArr != null) {
            for (i in 0 until myCountsArr.length()) {
                val o = myCountsArr.getJSONObject(i)
                myCounts.add(
                    MyCountEntity(
                        id = o.getString("id"),
                        date = o.getString("date"),
                        crewId = o.getString("crewId"),
                        headcount = o.getInt("headcount"),
                        note = o.optString("note", ""),
                        createdAt = o.optString("createdAt", Instant.now().toString()),
                        updatedAt = o.optString("updatedAt", Instant.now().toString()),
                        createdBy = o.optString("createdBy", "owner"),
                        deleted = o.optBoolean("deleted", false),
                        deletedAt = if (o.isNull("deletedAt")) null else o.optString("deletedAt")
                    )
                )
            }
        }

        val pieceWorks = mutableListOf<PieceWorkEntity>()
        val pieceWorksArr = tables.optJSONArray("piece_work")
        if (pieceWorksArr != null) {
            for (i in 0 until pieceWorksArr.length()) {
                val o = pieceWorksArr.getJSONObject(i)
                pieceWorks.add(
                    PieceWorkEntity(
                        id = o.getString("id"),
                        date = o.getString("date"),
                        farmId = if (o.isNull("farmId")) null else o.optString("farmId"),
                        plotId = if (o.isNull("plotId")) null else o.optString("plotId"),
                        tagId = o.getString("tagId"),
                        jobType = o.getString("jobType"),
                        size = o.getString("size"),
                        rate = o.getLong("rate"),
                        lines = o.optString("lines", "[]"),
                        note = o.optString("note", ""),
                        createdAt = o.optString("createdAt", Instant.now().toString()),
                        updatedAt = o.optString("updatedAt", Instant.now().toString()),
                        createdBy = o.optString("createdBy", "owner"),
                        deleted = o.optBoolean("deleted", false),
                        deletedAt = if (o.isNull("deletedAt")) null else o.optString("deletedAt")
                    )
                )
            }
        }

        val ledgerEntries = mutableListOf<LedgerEntryEntity>()
        val ledgerEntriesArr = tables.optJSONArray("ledger_entries")
        if (ledgerEntriesArr != null) {
            for (i in 0 until ledgerEntriesArr.length()) {
                val o = ledgerEntriesArr.getJSONObject(i)
                ledgerEntries.add(
                    LedgerEntryEntity(
                        id = o.getString("id"),
                        accountType = o.getString("accountType"),
                        accountId = o.getString("accountId"),
                        date = o.getString("date"),
                        kind = o.getString("kind"),
                        amount = o.getLong("amount"),
                        note = o.optString("note", ""),
                        voided = o.optBoolean("voided", false),
                        voidReason = if (o.isNull("voidReason")) null else o.optString("voidReason"),
                        createdAt = o.optString("createdAt", Instant.now().toString()),
                        updatedAt = o.optString("updatedAt", Instant.now().toString()),
                        createdBy = o.optString("createdBy", "owner"),
                        deleted = o.optBoolean("deleted", false),
                        deletedAt = if (o.isNull("deletedAt")) null else o.optString("deletedAt")
                    )
                )
            }
        }

        val payments = mutableListOf<PaymentEntity>()
        val paymentsArr = tables.optJSONArray("payments")
        if (paymentsArr != null) {
            for (i in 0 until paymentsArr.length()) {
                val o = paymentsArr.getJSONObject(i)
                payments.add(
                    PaymentEntity(
                        id = o.getString("id"),
                        date = o.getString("date"),
                        amount = o.getLong("amount"),
                        handedToPersonId = if (o.isNull("handedToPersonId")) null else o.optString("handedToPersonId"),
                        targetType = o.getString("targetType"),
                        targetId = o.getString("targetId"),
                        method = if (o.isNull("method")) null else o.optString("method"),
                        note = o.optString("note", ""),
                        shares = o.optString("shares", "[]"),
                        createdAt = o.optString("createdAt", Instant.now().toString()),
                        updatedAt = o.optString("updatedAt", Instant.now().toString()),
                        createdBy = o.optString("createdBy", "owner"),
                        deleted = o.optBoolean("deleted", false),
                        deletedAt = if (o.isNull("deletedAt")) null else o.optString("deletedAt")
                    )
                )
            }
        }

        val waitingList = mutableListOf<WaitingListEntity>()
        val waitingListArr = tables.optJSONArray("waiting_list")
        if (waitingListArr != null) {
            for (i in 0 until waitingListArr.length()) {
                val o = waitingListArr.getJSONObject(i)
                waitingList.add(
                    WaitingListEntity(
                        id = o.getString("id"),
                        accountType = o.getString("accountType"),
                        accountId = o.getString("accountId"),
                        amount = if (o.isNull("amount")) null else o.optLong("amount"),
                        date = o.getString("date"),
                        note = o.optString("note", ""),
                        status = o.optString("status", "open"),
                        createdAt = o.optString("createdAt", Instant.now().toString()),
                        updatedAt = o.optString("updatedAt", Instant.now().toString()),
                        createdBy = o.optString("createdBy", "owner"),
                        deleted = o.optBoolean("deleted", false),
                        deletedAt = if (o.isNull("deletedAt")) null else o.optString("deletedAt")
                    )
                )
            }
        }

        // Atomic swap
        dao.clearFarms()
        dao.clearPlots()
        dao.clearPersons()
        dao.clearRoles()
        dao.clearTags()
        dao.clearTasks()
        dao.clearCrews()
        dao.clearCrewMembers()
        dao.clearWageRates()
        dao.clearAttendance()
        dao.clearMyCounts()
        dao.clearPieceWork()
        dao.clearLedgerEntries()
        dao.clearPayments()
        dao.clearWaitingList()

        dao.insertFarms(farms)
        dao.insertPlots(plots)
        dao.insertPersons(persons)
        dao.insertRoles(roles)
        dao.insertTags(tags)
        if (tasks.isNotEmpty()) dao.insertTasks(tasks)
        if (crews.isNotEmpty()) dao.insertCrews(crews)
        if (crewMembers.isNotEmpty()) dao.insertCrewMembers(crewMembers)
        if (wageRates.isNotEmpty()) dao.insertWageRates(wageRates)
        if (attendances.isNotEmpty()) dao.insertAttendances(attendances)
        if (myCounts.isNotEmpty()) dao.insertMyCounts(myCounts)
        if (pieceWorks.isNotEmpty()) dao.insertPieceWorks(pieceWorks)
        if (ledgerEntries.isNotEmpty()) dao.insertLedgerEntries(ledgerEntries)
        if (payments.isNotEmpty()) dao.insertPayments(payments)
        if (waitingList.isNotEmpty()) dao.insertWaitingLists(waitingList)

        if (isSnapshotRestore) {
            dao.setSetting(SettingEntity("unsentChanges", "1"))
        } else {
            dao.setSetting(SettingEntity("unsentChanges", "0"))
        }
        true
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        createSnapshot()

        dao.clearFarms()
        dao.clearPlots()
        dao.clearPersons()
        dao.clearRoles()
        dao.clearTags()
        dao.clearTasks()
        dao.clearCrews()
        dao.clearCrewMembers()
        dao.clearWageRates()
        dao.clearAttendance()
        dao.clearMyCounts()
        dao.clearPieceWork()
        dao.clearLedgerEntries()
        dao.clearPayments()
        dao.clearWaitingList()
        dao.clearHistory()

        seedBuiltIns()

        dao.setSetting(SettingEntity("guideDismissed", "false"))
        dao.setSetting(SettingEntity("unsentChanges", "0"))
        dao.setSetting(SettingEntity("workTypes", """{"zeraat":true,"nahalestan":true}"""))
        dao.setSetting(SettingEntity("modules", """{"tasks":true,"workforce":true}"""))
        dao.setSetting(SettingEntity("foremanCoefficients", """{"present":2,"absent":1}"""))
        dao.setSetting(SettingEntity("tomorrowTasksHour", "18:00"))
    }
}
