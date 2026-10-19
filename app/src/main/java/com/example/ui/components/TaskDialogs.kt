package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import com.example.data.entity.FarmEntity
import com.example.data.entity.PersonEntity
import com.example.data.entity.PlotEntity
import com.example.data.entity.TagEntity
import com.example.data.entity.TaskEntity
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import com.example.ui.theme.ColorDangerRed
import com.example.ui.theme.ColorNeutralGray
import com.example.ui.theme.ColorWarningOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFormDialog(
    initialTask: TaskEntity? = null,
    defaultDateIso: String,
    farms: List<FarmEntity>,
    getPlotsForFarm: suspend (String) -> List<PlotEntity>,
    persons: List<PersonEntity>,
    roles: List<com.example.data.entity.RoleEntity>,
    tags: List<TagEntity>,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        farmId: String?,
        plotId: String?,
        plotIds: List<String>,
        startDate: String,
        endDate: String,
        priority: String,
        assigneePersonIds: List<String>,
        tagId: String?,
        note: String
    ) -> Unit
) {
    var title by remember { mutableStateOf(initialTask?.title ?: "") }
    var startDate by remember { mutableStateOf(initialTask?.startDate ?: defaultDateIso) }
    var isMultiDay by remember {
        mutableStateOf(initialTask != null && initialTask.startDate != initialTask.endDate)
    }
    var endDate by remember { mutableStateOf(initialTask?.endDate ?: defaultDateIso) }
    var priority by remember { mutableStateOf(initialTask?.priority ?: "normal") }

    var selectedFarmId by remember { mutableStateOf(initialTask?.farmId) }
    var selectablePlotsList by remember { mutableStateOf<List<PlotEntity>>(emptyList()) }
    
    val initialPlotIds = remember(initialTask) {
        if (initialTask != null) {
            val list = mutableListOf<String>()
            try {
                val arr = org.json.JSONArray(initialTask.plotIds)
                for (i in 0 until arr.length()) list.add(arr.getString(i))
            } catch (_: Exception) {}
            if (list.isEmpty() && initialTask.plotId != null) {
                list.add(initialTask.plotId)
            }
            list.toSet()
        } else emptySet()
    }
    var selectedPlotIds by remember { mutableStateOf(initialPlotIds) }

    // Parse assignees
    val initialAssignees = remember(initialTask) {
        if (initialTask != null) {
            try {
                val arr = org.json.JSONArray(initialTask.assigneePersonIds)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) list.add(arr.getString(i))
                list
            } catch (_: Exception) { emptyList() }
        } else emptyList()
    }
    var selectedAssignees by remember { mutableStateOf(initialAssignees.toSet()) }
    var selectedTagId by remember { mutableStateOf(initialTask?.tagId) }
    var note by remember { mutableStateOf(initialTask?.note ?: "") }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Load selectable plots when farm changes
    LaunchedEffect(selectedFarmId) {
        if (selectedFarmId != null) {
            val plots = getPlotsForFarm(selectedFarmId!!)
            val realPlots = plots.filter { !it.isDefault && it.active }
            selectablePlotsList = realPlots
            selectedPlotIds = selectedPlotIds.filter { pid -> realPlots.any { it.id == pid } }.toSet()
        } else {
            selectablePlotsList = emptyList()
            selectedPlotIds = emptySet()
        }
    }

    // Sort persons: workers/foremen first (Decision 48)
    val sortedPersons = remember(persons, roles) {
        val workerRoleIds = roles.filter { it.builtInKey == "worker" || it.builtInKey == "foreman" }.map { it.id }.toSet()
        persons.filter { it.active }.sortedWith(
            compareByDescending<PersonEntity> { p ->
                val pRoles = try {
                    val arr = org.json.JSONArray(p.roleIds)
                    (0 until arr.length()).map { arr.getString(it) }
                } catch (_: Exception) { emptyList() }
                pRoles.any { workerRoleIds.contains(it) }
            }.thenBy { it.name }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialTask == null) "ثبت کار تازه" else "ویرایش کار",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Title
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("عنوان کار *") },
                        placeholder = { Text("مثلاً وجین قطعه الف، خرید سم...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("task_title_input")
                    )
                }

                // Date & Multi-day
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تاریخ:", style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = isMultiDay,
                                    onCheckedChange = { checked ->
                                        isMultiDay = checked
                                        if (!checked) endDate = startDate
                                    }
                                )
                                Text("چندروزه", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        // Start date selector
                        val startJalali = JalaliDateHelper.fromIsoDate(startDate).toDisplayFull()
                        OutlinedCard(
                            onClick = { showStartDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                            ) {
                                Column {
                                    Text(if (isMultiDay) "تاریخ شروع:" else "تاریخ کار:", style = MaterialTheme.typography.labelSmall)
                                    Text(startJalali, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                Icon(Icons.Default.DateRange, contentDescription = null)
                            }
                        }

                        // End date selector (if multi-day)
                        if (isMultiDay) {
                            val endJalali = JalaliDateHelper.fromIsoDate(endDate).toDisplayFull()
                            OutlinedCard(
                                onClick = { showEndDatePicker = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                                ) {
                                    Column {
                                        Text("تاریخ پایان:", style = MaterialTheme.typography.labelSmall)
                                        Text(endJalali, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    }
                                    Icon(Icons.Default.DateRange, contentDescription = null)
                                }
                            }
                        }
                    }
                }

                // Priority: urgent (قرمز), important (نارنجی), normal (خاکستری)
                item {
                    Column {
                        Text("اولویت:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = priority == "urgent",
                                onClick = { priority = "urgent" },
                                label = { Text("فوری") },
                                leadingIcon = {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(ColorDangerRed))
                                },
                                modifier = Modifier.testTag("priority_urgent_chip")
                            )
                            FilterChip(
                                selected = priority == "important",
                                onClick = { priority = "important" },
                                label = { Text("مهم") },
                                leadingIcon = {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(ColorWarningOrange))
                                },
                                modifier = Modifier.testTag("priority_important_chip")
                            )
                            FilterChip(
                                selected = priority == "normal",
                                onClick = { priority = "normal" },
                                label = { Text("عادی") },
                                leadingIcon = {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(ColorNeutralGray))
                                },
                                modifier = Modifier.testTag("priority_normal_chip")
                            )
                        }
                    }
                }

                // Farm & Plot selection
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("مزرعه و قطعه (اختیاری):", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedFarmId == null,
                                    onClick = { selectedFarmId = null },
                                    label = { Text("بدون مزرعه") }
                                )
                            }
                            items(farms.filter { it.active }) { farm ->
                                FilterChip(
                                    selected = selectedFarmId == farm.id,
                                    onClick = { selectedFarmId = if (selectedFarmId == farm.id) null else farm.id },
                                    label = { Text(farm.name) }
                                )
                            }
                        }

                        // Multi-Plot picker (only if farm has real active plots)
                        if (selectedFarmId != null && selectablePlotsList.isNotEmpty()) {
                            Text("انتخاب قطعه‌ها (امکان انتخاب چند قطعه):", style = MaterialTheme.typography.labelSmall)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(selectablePlotsList) { plot ->
                                    val isSelected = selectedPlotIds.contains(plot.id)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedPlotIds = if (isSelected) {
                                                selectedPlotIds - plot.id
                                            } else {
                                                selectedPlotIds + plot.id
                                            }
                                        },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        label = { Text(plot.name) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Assignees selection
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("انجام‌دهندگان (اختیاری):", style = MaterialTheme.typography.labelMedium)
                        if (sortedPersons.isEmpty()) {
                            Text("هیچ شخص فعالی تعریف نشده است.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(sortedPersons) { person ->
                                    val isSelected = selectedAssignees.contains(person.id)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedAssignees = if (isSelected) {
                                                selectedAssignees - person.id
                                            } else {
                                                selectedAssignees + person.id
                                            }
                                        },
                                        label = { Text(person.name) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Tag selection
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("برچسب (اختیاری):", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedTagId == null,
                                    onClick = { selectedTagId = null },
                                    label = { Text("بدون برچسب") }
                                )
                            }
                            items(tags.filter { !it.hidden }) { tag ->
                                val tagColor = try {
                                    Color(android.graphics.Color.parseColor(tag.color))
                                } catch (_: Exception) { MaterialTheme.colorScheme.primary }

                                FilterChip(
                                    selected = selectedTagId == tag.id,
                                    onClick = { selectedTagId = if (selectedTagId == tag.id) null else tag.id },
                                    label = { Text(tag.name) },
                                    leadingIcon = {
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(tagColor))
                                    }
                                )
                            }
                        }
                    }
                }

                // Note
                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("توضیحات کار") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("task_note_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalEndDate = if (isMultiDay && endDate >= startDate) endDate else startDate
                    onSave(
                        title.trim(),
                        selectedFarmId,
                        selectedPlotIds.firstOrNull(),
                        selectedPlotIds.toList(),
                        startDate,
                        finalEndDate,
                        priority,
                        selectedAssignees.toList(),
                        selectedTagId,
                        note.trim()
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_task_btn")
            ) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )

    // Start Date Picker Dialog
    if (showStartDatePicker) {
        JalaliDatePickerDialog(
            initialIso = startDate,
            onDismiss = { showStartDatePicker = false },
            onDateSelected = { newDate ->
                startDate = newDate
                if (endDate < newDate) endDate = newDate
                showStartDatePicker = false
            }
        )
    }

    // End Date Picker Dialog
    if (showEndDatePicker) {
        JalaliDatePickerDialog(
            initialIso = endDate,
            onDismiss = { showEndDatePicker = false },
            onDateSelected = { newDate ->
                if (newDate >= startDate) {
                    endDate = newDate
                } else {
                    endDate = startDate
                }
                showEndDatePicker = false
            }
        )
    }
}

@Composable
fun JalaliDatePickerDialog(
    initialIso: String,
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit
) {
    val initialJalali = remember(initialIso) { JalaliDateHelper.fromIsoDate(initialIso) }
    var selectedYear by remember { mutableStateOf(initialJalali.year) }
    var selectedMonth by remember { mutableStateOf(initialJalali.month) }
    var selectedDay by remember { mutableStateOf(initialJalali.day) }

    val daysInCurrentMonth = JalaliDateHelper.daysInMonth(selectedYear, selectedMonth)
    if (selectedDay > daysInCurrentMonth) {
        selectedDay = daysInCurrentMonth
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تاریخ جلالی", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Year & Month selector
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Month selector
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (selectedMonth > 1) selectedMonth--
                                else {
                                    selectedMonth = 12
                                    selectedYear--
                                }
                            }
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "ماه قبل")
                        }
                        val monthName = JalaliDateHelper.today().copy(month = selectedMonth).monthName
                        Text(monthName, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = {
                                if (selectedMonth < 12) selectedMonth++
                                else {
                                    selectedMonth = 1
                                    selectedYear++
                                }
                            }
                        ) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "ماه بعد")
                        }
                    }

                    // Year
                    Text(
                        NumberFormatHelper.toPersianDigits(selectedYear),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Days Grid (1 to daysInCurrentMonth)
                val daysList = (1..daysInCurrentMonth).toList()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    daysList.chunked(7).forEach { week ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            week.forEach { d ->
                                val isSelected = d == selectedDay
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { selectedDay = d }
                                ) {
                                    Text(
                                        text = NumberFormatHelper.toPersianDigits(d),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val gDate = JalaliDateHelper.toGregorian(selectedYear, selectedMonth, selectedDay)
                    onDateSelected(JalaliDateHelper.toIsoDate(gDate))
                }
            ) {
                Text("تأیید")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

@Composable
fun SendScheduleDialog(
    dateIso: String,
    tasksOnDate: List<TaskEntity>,
    allFarms: List<FarmEntity>,
    allPlots: List<PlotEntity>,
    allPersons: List<PersonEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val farmsMap = remember(allFarms) { allFarms.associateBy { it.id } }
    val plotsMap = remember(allPlots) { allPlots.associateBy { it.id } }
    val personsMap = remember(allPersons) { allPersons.associateBy { it.id } }

    val todayIso = remember { JalaliDateHelper.toIsoDate(java.time.LocalDate.now()) }
    val dateJalali = remember(dateIso) { JalaliDateHelper.fromIsoDate(dateIso) }

    // Find persons with tasks on this date
    val assignedPersonIds = remember(tasksOnDate) {
        val set = mutableSetOf<String>()
        tasksOnDate.forEach { t ->
            try {
                val arr = org.json.JSONArray(t.assigneePersonIds)
                for (i in 0 until arr.length()) set.add(arr.getString(i))
            } catch (_: Exception) {}
        }
        set
    }

    val candidatePersons = remember(assignedPersonIds, allPersons) {
        allPersons.filter { assignedPersonIds.contains(it.id) }
    }

    // Selected person: null means "All tasks of the day (بدون اسم)"
    var selectedPersonId by remember { mutableStateOf<String?>(candidatePersons.firstOrNull()?.id) }
    var generatedText by remember { mutableStateOf("") }

    // Rebuild text when selection changes
    LaunchedEffect(selectedPersonId, tasksOnDate) {
        val selectedPerson = candidatePersons.firstOrNull { it.id == selectedPersonId }
        val header = if (selectedPerson != null) {
            "برنامه‌ی ${dateJalali.dayOfWeekName} ${NumberFormatHelper.toPersianDigits(dateJalali.day)} ${dateJalali.monthName} – ${selectedPerson.name}:"
        } else {
            "برنامه‌ی ${dateJalali.dayOfWeekName} ${NumberFormatHelper.toPersianDigits(dateJalali.day)} ${dateJalali.monthName}:"
        }

        val relevantTasks = if (selectedPersonId != null) {
            tasksOnDate.filter { t ->
                try {
                    val arr = org.json.JSONArray(t.assigneePersonIds)
                    (0 until arr.length()).any { arr.getString(it) == selectedPersonId }
                } catch (_: Exception) { false }
            }
        } else {
            tasksOnDate
        }

        val sb = StringBuilder()
        sb.append(header).append("\n")

        relevantTasks.forEachIndexed { index, task ->
            val numFa = NumberFormatHelper.toPersianDigits(index + 1)
            sb.append("$numFa) ").append(task.title)

            // Location
            val plot = task.plotId?.let { plotsMap[it] }
            val farm = task.farmId?.let { farmsMap[it] }
            if (plot != null && !plot.isDefault) {
                sb.append(" (").append("قطعه‌ی ").append(plot.name).append(")")
            } else if (farm != null) {
                sb.append(" (").append(farm.name).append(")")
            }

            // Priority (only for urgent and important)
            when (task.priority) {
                "urgent" -> sb.append(" (فوری)")
                "important" -> sb.append(" (مهم)")
            }

            // Multi-day
            if (task.startDate != task.endDate) {
                val totalDays = JalaliDateHelper.daysBetween(task.startDate, task.endDate) + 1
                val currentDayNum = JalaliDateHelper.daysBetween(task.startDate, dateIso) + 1
                if (currentDayNum in 1..totalDays) {
                    val curFa = NumberFormatHelper.toPersianDigits(currentDayNum)
                    val totFa = NumberFormatHelper.toPersianDigits(totalDays)
                    sb.append(" (روز $curFa از $totFa)")
                }
            }

            // Overdue if today
            if (dateIso == todayIso && task.endDate < todayIso && task.status == "open") {
                sb.append(" (عقب‌افتاده)")
            }

            sb.append("\n")
        }

        generatedText = sb.toString().trimEnd()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فرستادن برنامه کاری", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("انتخاب گیرنده برنامه:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedPersonId == null,
                            onClick = { selectedPersonId = null },
                            label = { Text("همه‌ی کارهای روز (بدون اسم)") }
                        )
                    }
                    items(candidatePersons) { p ->
                        FilterChip(
                            selected = selectedPersonId == p.id,
                            onClick = { selectedPersonId = p.id },
                            label = { Text(p.name) }
                        )
                    }
                }

                Text("متن پیام (قابل ویرایش قبل از ارسال):", style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(
                    value = generatedText,
                    onValueChange = { generatedText = it },
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth().testTag("send_schedule_text_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, generatedText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "ارسال برنامه کاری"))
                        onDismiss()
                    } catch (_: Exception) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("برنامه کاری", generatedText))
                        Toast.makeText(context, "کپی شد", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("send_schedule_submit_btn")
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("فرستادن")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}
