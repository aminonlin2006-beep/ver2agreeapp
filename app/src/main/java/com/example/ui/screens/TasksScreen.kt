package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.entity.FarmEntity
import com.example.data.entity.PersonEntity
import com.example.data.entity.PlotEntity
import com.example.data.entity.TagEntity
import com.example.data.entity.TaskEntity
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import com.example.lib.SmsHelper
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.JalaliDatePickerDialog
import com.example.ui.components.SendScheduleDialog
import com.example.ui.components.TaskCompletionDialog
import com.example.ui.components.TaskFormDialog
import com.example.ui.theme.ColorDangerRed
import com.example.ui.theme.ColorNeutralGray
import com.example.ui.theme.ColorSuccessGreen
import com.example.ui.theme.ColorWarningOrange
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val todayIso = remember { JalaliDateHelper.toIsoDate(LocalDate.now()) }
    var selectedDateIso by remember { mutableStateOf(todayIso) }
    var isCalendarViewOpen by remember { mutableStateOf(false) }

    var showCreateTaskDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<TaskEntity?>(null) }
    var taskToReschedule by remember { mutableStateOf<TaskEntity?>(null) }
    var taskToDelete by remember { mutableStateOf<TaskEntity?>(null) }
    var taskToCompleteWithAttendance by remember { mutableStateOf<TaskEntity?>(null) }
    var showSendScheduleDialog by remember { mutableStateOf(false) }
    var showClosedTasksSection by remember { mutableStateOf(false) }

    val farmsMap = remember(uiState.farms) { uiState.farms.associateBy { it.id } }
    val plotsMap = remember(uiState.plots) { uiState.plots.associateBy { it.id } }
    val personsMap = remember(uiState.persons) { uiState.persons.associateBy { it.id } }
    val tagsMap = remember(uiState.tags) { uiState.tags.associateBy { it.id } }

    val selectedJalali = remember(selectedDateIso) { JalaliDateHelper.fromIsoDate(selectedDateIso) }
    val isTodaySelected = selectedDateIso == todayIso

    // Filter and sort open tasks for the selected day
    val dailyTasks = remember(uiState.tasks, selectedDateIso, todayIso) {
        val tasksForDay = uiState.tasks.filter { t ->
            if (t.status != "open") return@filter false
            val isWithinRange = t.startDate <= selectedDateIso && selectedDateIso <= t.endDate
            val isOverdueAndToday = (selectedDateIso == todayIso && t.endDate < todayIso)
            isWithinRange || isOverdueAndToday
        }

        // Order: Overdue first, then urgent, important, normal; by createdAt ASC
        tasksForDay.sortedWith(
            compareBy<TaskEntity> { t ->
                val isOverdue = selectedDateIso == todayIso && t.endDate < todayIso
                if (isOverdue) 0 else 1
            }.thenBy { t ->
                when (t.priority) {
                    "urgent" -> 0
                    "important" -> 1
                    else -> 2
                }
            }.thenBy { t -> t.createdAt }
        )
    }

    // Closed tasks for the selected date (or all recent closed)
    val closedTasks = remember(uiState.tasks) {
        uiState.tasks.filter { it.status == "done" || it.status == "cancelled" }
            .sortedByDescending { it.closedAt ?: it.updatedAt }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("کارهای آینده", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("tasks_back_btn")
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isCalendarViewOpen = !isCalendarViewOpen },
                        modifier = Modifier.testTag("tasks_calendar_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (isCalendarViewOpen) Icons.Default.ViewAgenda else Icons.Default.CalendarMonth,
                            contentDescription = "تقویم"
                        )
                    }
                    IconButton(
                        onClick = { showSendScheduleDialog = true },
                        modifier = Modifier.testTag("tasks_send_schedule_btn")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "فرستادن برنامه")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateTaskDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("کار تازه") },
                modifier = Modifier.testTag("add_task_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- DATE NAVIGATION BAR ---
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        // Previous Day (In RTL, Forward is left/next day, Back is right/prev day)
                        IconButton(
                            onClick = { selectedDateIso = JalaliDateHelper.addDays(selectedDateIso, -1) },
                            modifier = Modifier.testTag("prev_day_btn")
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "روز قبل")
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { isCalendarViewOpen = true }
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = selectedJalali.toDisplayFull(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isTodaySelected) {
                                Text(
                                    text = "امروز",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!isTodaySelected) {
                                TextButton(
                                    onClick = { selectedDateIso = todayIso },
                                    modifier = Modifier.testTag("go_to_today_btn")
                                ) {
                                    Text("امروز")
                                }
                            }
                            IconButton(
                                onClick = { selectedDateIso = JalaliDateHelper.addDays(selectedDateIso, 1) },
                                modifier = Modifier.testTag("next_day_btn")
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "روز بعد")
                            }
                        }
                    }
                }
            }

            // --- CALENDAR VIEW (If open) ---
            if (isCalendarViewOpen) {
                item {
                    CalendarMonthCard(
                        selectedDateIso = selectedDateIso,
                        todayIso = todayIso,
                        allTasks = uiState.tasks.filter { it.status == "open" },
                        onSelectDate = {
                            selectedDateIso = it
                            isCalendarViewOpen = false
                        }
                    )
                }
            }

            // --- DAILY TASKS LIST ---
            if (dailyTasks.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp)
                    ) {
                        Text(
                            text = if (isTodaySelected) "امروز کاری ثبت نشده است." else "در این روز کاری ثبت نشده است.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                items(dailyTasks, key = { it.id }) { task ->
                    val isOverdue = isTodaySelected && task.endDate < todayIso
                    TaskItemCard(
                        task = task,
                        currentDayIso = selectedDateIso,
                        isOverdue = isOverdue,
                        farmsMap = farmsMap,
                        plotsMap = plotsMap,
                        personsMap = personsMap,
                        tagsMap = tagsMap,
                        onMarkDone = { taskToCompleteWithAttendance = task },
                        onMarkCancelled = { viewModel.toggleTaskStatus(task.id, "cancelled") },
                        onReschedule = { taskToReschedule = task },
                        onEdit = { taskToEdit = task },
                        onDelete = { taskToDelete = task },
                        onSendSms = {
                            val assigneeIds = try {
                                val arr = org.json.JSONArray(task.assigneePersonIds)
                                (0 until arr.length()).map { arr.getString(it) }
                            } catch (_: Exception) { emptyList() }

                            val firstAssignee = assigneeIds.firstNotNullOfOrNull { personsMap[it] }
                            val workerName = firstAssignee?.name
                            val workerPhone = firstAssignee?.phone ?: ""
                            val farmName = task.farmId?.let { farmsMap[it]?.name }
                            val plotName = task.plotId?.let { plotsMap[it]?.name }

                            val smsText = SmsHelper.buildTaskSmsText(
                                workerName = workerName,
                                taskTitle = task.title,
                                farmName = farmName,
                                plotName = plotName,
                                dateIso = task.startDate,
                                time = task.reminderTime ?: "",
                                note = task.note
                            )
                            SmsHelper.sendSms(context, workerPhone, smsText)
                        }
                    )
                }
            }

            // --- CLOSED TASKS (بسته‌شده‌ها) ---
            if (closedTasks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showClosedTasksSection = !showClosedTasksSection }
                                    .padding(16.dp)
                            ) {
                                val countFa = NumberFormatHelper.toPersianDigits(closedTasks.size)
                                Text(
                                    text = "بسته‌شده‌ها ($countFa)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (showClosedTasksSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }

                            AnimatedVisibility(visible = showClosedTasksSection) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    closedTasks.forEach { task ->
                                        ClosedTaskItemCard(
                                            task = task,
                                            farmsMap = farmsMap,
                                            plotsMap = plotsMap,
                                            onReopen = { viewModel.toggleTaskStatus(task.id, "open") },
                                            onDelete = { taskToDelete = task }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- CREATE TASK DIALOG ---
    if (showCreateTaskDialog) {
        TaskFormDialog(
            defaultDateIso = selectedDateIso,
            farms = uiState.farms,
            getPlotsForFarm = { farmId -> viewModel.gateway.selectablePlots(farmId) },
            persons = uiState.persons,
            roles = uiState.roles,
            tags = uiState.tags,
            onDismiss = { showCreateTaskDialog = false },
            onSave = { title, farmId, plotId, plotIds, startDate, endDate, priority, assignees, tagId, note ->
                viewModel.createTask(
                    title = title,
                    farmId = farmId,
                    plotId = plotId,
                    plotIds = plotIds,
                    startDate = startDate,
                    endDate = endDate,
                    priority = priority,
                    assigneePersonIds = assignees,
                    tagId = tagId,
                    note = note
                ) {
                    showCreateTaskDialog = false
                }
            }
        )
    }

    // --- EDIT TASK DIALOG ---
    if (taskToEdit != null) {
        TaskFormDialog(
            initialTask = taskToEdit,
            defaultDateIso = taskToEdit!!.startDate,
            farms = uiState.farms,
            getPlotsForFarm = { farmId -> viewModel.gateway.selectablePlots(farmId) },
            persons = uiState.persons,
            roles = uiState.roles,
            tags = uiState.tags,
            onDismiss = { taskToEdit = null },
            onSave = { title, farmId, plotId, plotIds, startDate, endDate, priority, assignees, tagId, note ->
                val updated = taskToEdit!!.copy(
                    title = title,
                    farmId = farmId,
                    plotId = plotId,
                    plotIds = org.json.JSONArray(plotIds).toString(),
                    startDate = startDate,
                    endDate = endDate,
                    priority = priority,
                    assigneePersonIds = org.json.JSONArray(assignees).toString(),
                    tagId = tagId,
                    note = note
                )
                viewModel.updateTask(updated) {
                    taskToEdit = null
                }
            }
        )
    }

    // --- RESCHEDULE DIALOG ---
    if (taskToReschedule != null) {
        JalaliDatePickerDialog(
            initialIso = taskToReschedule!!.startDate,
            onDismiss = { taskToReschedule = null },
            onDateSelected = { newStartDate ->
                viewModel.rescheduleTask(taskToReschedule!!.id, newStartDate) {
                    taskToReschedule = null
                }
            }
        )
    }

    // --- DELETE TASK CONFIRMATION ---
    if (taskToDelete != null) {
        ConfirmDialog(
            title = "حذف کار",
            message = "آیا از حذف کار «${taskToDelete!!.title}» مطمئن هستید؟",
            confirmText = "حذف",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteTask(taskToDelete!!.id) {
                    taskToDelete = null
                }
            },
            onDismiss = { taskToDelete = null }
        )
    }

    // --- SEND SCHEDULE DIALOG ---
    if (showSendScheduleDialog) {
        SendScheduleDialog(
            dateIso = selectedDateIso,
            tasksOnDate = dailyTasks,
            allFarms = uiState.farms,
            allPlots = uiState.plots,
            allPersons = uiState.persons,
            onDismiss = { showSendScheduleDialog = false }
        )
    }

    // --- TASK COMPLETION & WORKFORCE ATTENDANCE DIALOG ---
    if (taskToCompleteWithAttendance != null) {
        TaskCompletionDialog(
            task = taskToCompleteWithAttendance!!,
            uiState = uiState,
            onDismiss = { taskToCompleteWithAttendance = null },
            onCompleteOnly = {
                viewModel.toggleTaskStatus(taskToCompleteWithAttendance!!.id, "done")
                taskToCompleteWithAttendance = null
            },
            onCompleteWithAttendance = { date, farmId, plotId, crewId, tagId, lines, note ->
                viewModel.createAttendance(
                    date = date,
                    farmId = farmId,
                    plotId = plotId,
                    crewId = crewId,
                    tagId = tagId,
                    lines = lines,
                    headcount = null,
                    countTicks = null,
                    foreman = null,
                    note = note
                ) {
                    viewModel.toggleTaskStatus(taskToCompleteWithAttendance!!.id, "done")
                    taskToCompleteWithAttendance = null
                }
            }
        )
    }
}

@Composable
fun TaskItemCard(
    task: TaskEntity,
    currentDayIso: String,
    isOverdue: Boolean,
    farmsMap: Map<String, FarmEntity>,
    plotsMap: Map<String, PlotEntity>,
    personsMap: Map<String, PersonEntity>,
    tagsMap: Map<String, TagEntity>,
    onMarkDone: () -> Unit,
    onMarkCancelled: () -> Unit,
    onReschedule: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSendSms: () -> Unit
) {
    val priorityColor = when (task.priority) {
        "urgent" -> ColorDangerRed
        "important" -> ColorWarningOrange
        else -> ColorNeutralGray
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("task_item_${task.id}")
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Priority Bar on the right/edge
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(priorityColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
            ) {
                // Header with Badges & Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Priority label chip
                    val priorityLabel = when (task.priority) {
                        "urgent" -> "فوری"
                        "important" -> "مهم"
                        else -> "عادی"
                    }
                    SuggestionChip(
                        onClick = {},
                        label = { Text(priorityLabel, color = priorityColor, fontWeight = FontWeight.Bold) }
                    )
                }

                // Badges row: Overdue, Multi-day, Rescheduled
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    if (isOverdue) {
                        val daysOverdue = JalaliDateHelper.daysBetween(task.endDate, currentDayIso)
                        val daysFa = NumberFormatHelper.toPersianDigits(if (daysOverdue > 0) daysOverdue else 1)
                        AssistChip(
                            onClick = {},
                            label = { Text("$daysFa روز عقب", color = ColorDangerRed, fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ColorDangerRed, modifier = Modifier.size(16.dp))
                            }
                        )
                    }

                    if (task.startDate != task.endDate) {
                        val totalDays = JalaliDateHelper.daysBetween(task.startDate, task.endDate) + 1
                        val curDay = JalaliDateHelper.daysBetween(task.startDate, currentDayIso) + 1
                        if (curDay in 1..totalDays) {
                            val curFa = NumberFormatHelper.toPersianDigits(curDay)
                            val totFa = NumberFormatHelper.toPersianDigits(totalDays)
                            AssistChip(
                                onClick = {},
                                label = { Text("روز $curFa از $totFa", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    if (task.rescheduleCount > 0) {
                        val countFa = NumberFormatHelper.toPersianDigits(task.rescheduleCount)
                        AssistChip(
                            onClick = {},
                            label = { Text("$countFa بار عقب افتاده", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Farm & Plot
                val farm = task.farmId?.let { farmsMap[it] }
                val plot = task.plotId?.let { plotsMap[it] }
                if (farm != null || (plot != null && !plot.isDefault)) {
                    val locationText = if (plot != null && !plot.isDefault) {
                        "${farm?.name ?: ""} — قطعه‌ی ${plot.name}"
                    } else {
                        farm?.name ?: ""
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(locationText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }

                // Assignees
                val assigneeIds = try {
                    val arr = org.json.JSONArray(task.assigneePersonIds)
                    (0 until arr.length()).map { arr.getString(it) }
                } catch (_: Exception) { emptyList() }

                if (assigneeIds.isNotEmpty()) {
                    val names = assigneeIds.mapNotNull { personsMap[it]?.name }.joinToString("، ")
                    if (names.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(names, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                // Tag
                val tag = task.tagId?.let { tagsMap[it] }
                if (tag != null) {
                    val tagColor = try {
                        Color(android.graphics.Color.parseColor(tag.color))
                    } catch (_: Exception) { MaterialTheme.colorScheme.primary }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(tagColor))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tag.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                // Note
                if (task.note.isNotBlank()) {
                    Text(
                        text = task.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Actions row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onMarkDone,
                            colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessGreen),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("task_done_btn_${task.id}")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("انجام شد")
                        }

                        OutlinedButton(
                            onClick = onReschedule,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("بینداز به تاریخ دیگر")
                        }
                    }

                    Row {
                        IconButton(onClick = onSendSms, modifier = Modifier.testTag("task_sms_btn_${task.id}")) {
                            Icon(Icons.Default.Send, contentDescription = "ارسال پیامک یادآوری کار به کارگر", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "ویرایش", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onMarkCancelled) {
                            Icon(Icons.Default.Close, contentDescription = "لغو شد", modifier = Modifier.size(18.dp), tint = ColorWarningOrange)
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", modifier = Modifier.size(18.dp), tint = ColorDangerRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClosedTaskItemCard(
    task: TaskEntity,
    farmsMap: Map<String, FarmEntity>,
    plotsMap: Map<String, PlotEntity>,
    onReopen: () -> Unit,
    onDelete: () -> Unit
) {
    val isDone = task.status == "done"

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (isDone) ColorSuccessGreen else ColorWarningOrange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (isDone) TextDecoration.None else TextDecoration.LineThrough
                    )
                    Text(
                        text = if (isDone) "انجام شده" else "لغو شده",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Row {
                TextButton(onClick = onReopen) {
                    Text("برگردان")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = ColorDangerRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun CalendarMonthCard(
    selectedDateIso: String,
    todayIso: String,
    allTasks: List<TaskEntity>,
    onSelectDate: (String) -> Unit
) {
    val selectedJalali = remember(selectedDateIso) { JalaliDateHelper.fromIsoDate(selectedDateIso) }
    var calYear by remember { mutableStateOf(selectedJalali.year) }
    var calMonth by remember { mutableStateOf(selectedJalali.month) }

    val daysInMonth = JalaliDateHelper.daysInMonth(calYear, calMonth)
    val monthName = JalaliDateHelper.today().copy(month = calMonth).monthName

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Month Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        if (calMonth > 1) calMonth--
                        else {
                            calMonth = 12
                            calYear--
                        }
                    }
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "ماه قبل")
                }

                Text(
                    text = "$monthName ${NumberFormatHelper.toPersianDigits(calYear)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = {
                        if (calMonth < 12) calMonth++
                        else {
                            calMonth = 1
                            calYear++
                        }
                    }
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "ماه بعد")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Weekday Headers: شنبه ... جمعه
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach { dayName ->
                    Text(
                        text = dayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Days Grid
            val days = (1..daysInMonth).toList()
            days.chunked(7).forEach { week ->
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    week.forEach { d ->
                        val gDate = JalaliDateHelper.toGregorian(calYear, calMonth, d)
                        val dateIso = JalaliDateHelper.toIsoDate(gDate)
                        val isSelected = dateIso == selectedDateIso
                        val isToday = dateIso == todayIso

                        // Tasks on this day for dots (red for urgent, orange for important, gray for normal)
                        val tasksOnDay = allTasks.filter { it.startDate <= dateIso && dateIso <= it.endDate }
                        val hasUrgent = tasksOnDay.any { it.priority == "urgent" }
                        val hasImportant = tasksOnDay.any { it.priority == "important" }
                        val hasNormal = tasksOnDay.any { it.priority == "normal" }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .border(
                                    width = if (isToday) 2.dp else 0.dp,
                                    color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onSelectDate(dateIso) }
                                .padding(2.dp)
                        ) {
                            Text(
                                text = NumberFormatHelper.toPersianDigits(d),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                            )

                            // Dots
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(top = 1.dp)
                            ) {
                                if (hasUrgent) {
                                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(ColorDangerRed))
                                }
                                if (hasImportant) {
                                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(ColorWarningOrange))
                                }
                                if (hasNormal) {
                                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(ColorNeutralGray))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
