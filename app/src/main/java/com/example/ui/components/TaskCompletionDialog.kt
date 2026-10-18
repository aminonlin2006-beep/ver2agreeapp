package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.entity.*
import com.example.lib.JalaliDateHelper
import com.example.ui.MainUiState
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCompletionDialog(
    task: TaskEntity,
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onCompleteOnly: () -> Unit,
    onCompleteWithAttendance: (
        date: String,
        farmId: String?,
        plotId: String?,
        crewId: String,
        tagId: String,
        lines: String,
        note: String
    ) -> Unit
) {
    val personsMap = remember(uiState.persons) { uiState.persons.associateBy { it.id } }
    val taskAssigneeIds = remember(task.assigneePersonIds) {
        try {
            val arr = JSONArray(task.assigneePersonIds)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (_: Exception) { emptySet() }
    }

    var registerAttendance by remember {
        mutableStateOf(uiState.isWorkforceModuleActive && (taskAssigneeIds.isNotEmpty() || uiState.crews.isNotEmpty()))
    }

    var selectedDateIso by remember { mutableStateOf(task.endDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Crew selection: find crew of first assigned worker if available
    val defaultCrewId = remember(taskAssigneeIds, uiState.crewMembers, uiState.crews) {
        val foundMember = uiState.crewMembers.firstOrNull { it.personId in taskAssigneeIds }
        foundMember?.crewId ?: uiState.crews.firstOrNull()?.id ?: ""
    }
    var selectedCrewId by remember { mutableStateOf(defaultCrewId) }

    // Workers selection
    var selectedWorkerIds by remember {
        mutableStateOf(if (taskAssigneeIds.isNotEmpty()) taskAssigneeIds else emptySet())
    }

    // Tag
    val effectiveTagId = task.tagId ?: uiState.tags.firstOrNull()?.id ?: ""
    var note by remember { mutableStateOf("کارکرد انجام کار: ${task.title}") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "تکمیل کار و ثبت کارکرد",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Task Overview Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val farmName = task.farmId?.let { fid -> uiState.farms.firstOrNull { it.id == fid }?.name }
                            val plotName = task.plotId?.let { pid -> uiState.plots.firstOrNull { it.id == pid }?.name }
                            if (farmName != null) {
                                val loc = if (plotName != null) "$farmName — $plotName" else farmName
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "محل: $loc",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                // Attendance Switch
                if (uiState.isWorkforceModuleActive) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (registerAttendance) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ثبت حضور و کارکرد در دفتر کارگران",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "ساعات یا روز کارکرد مستقیم ثبت شود تا نیازی به ورود مجدد نباشد",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Switch(
                                    checked = registerAttendance,
                                    onCheckedChange = { registerAttendance = it },
                                    modifier = Modifier.testTag("toggle_register_attendance_switch")
                                )
                            }
                        }
                    }
                }

                // Attendance Details (if enabled)
                if (registerAttendance && uiState.isWorkforceModuleActive) {
                    // Date
                    item {
                        val jalali = try { JalaliDateHelper.fromIsoDate(selectedDateIso) } catch (_: Exception) { JalaliDateHelper.today() }
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDatePicker = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(12.dp).fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("تاریخ کارکرد:", style = MaterialTheme.typography.labelSmall)
                                        Text(jalali.toDisplayFull(), fontWeight = FontWeight.Bold)
                                    }
                                }
                                TextButton(onClick = { showDatePicker = true }) {
                                    Text("تغییر")
                                }
                            }
                        }
                    }

                    // Crew dropdown or selection
                    if (uiState.crews.isNotEmpty()) {
                        item {
                            Column {
                                Text("گروه کاری / سرکارگر:", style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    uiState.crews.forEach { crew ->
                                        FilterChip(
                                            selected = selectedCrewId == crew.id,
                                            onClick = { selectedCrewId = crew.id },
                                            label = { Text(crew.name) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Assigned workers checklist
                    val availableWorkers = if (taskAssigneeIds.isNotEmpty()) {
                        taskAssigneeIds.mapNotNull { personsMap[it] }
                    } else {
                        uiState.persons.filter { it.active }
                    }

                    if (availableWorkers.isNotEmpty()) {
                        item {
                            Text(
                                text = "کارگران حاضر در این کار:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(availableWorkers) { person ->
                            val isSelected = person.id in selectedWorkerIds
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedWorkerIds = if (isSelected) {
                                            selectedWorkerIds - person.id
                                        } else {
                                            selectedWorkerIds + person.id
                                        }
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedWorkerIds = if (checked) {
                                            selectedWorkerIds + person.id
                                        } else {
                                            selectedWorkerIds - person.id
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = person.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "۱ روز کارکرد",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("یادداشت کارکرد") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (registerAttendance && selectedWorkerIds.isNotEmpty()) {
                        val linesArr = JSONArray()
                        selectedWorkerIds.forEach { pid ->
                            val obj = JSONObject()
                            obj.put("personId", pid)
                            obj.put("ticks", 1) // 1 day / shift
                            linesArr.put(obj)
                        }

                        onCompleteWithAttendance(
                            selectedDateIso,
                            task.farmId,
                            task.plotId,
                            selectedCrewId,
                            effectiveTagId,
                            linesArr.toString(),
                            note
                        )
                    } else {
                        onCompleteOnly()
                    }
                },
                modifier = Modifier.testTag("confirm_task_completion_btn")
            ) {
                Text(if (registerAttendance && selectedWorkerIds.isNotEmpty()) "ثبت در کارکرد و تکمیل" else "ثبت انجام کار")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )

    if (showDatePicker) {
        JalaliDatePickerDialog(
            initialIso = selectedDateIso,
            onDismiss = { showDatePicker = false },
            onDateSelected = { newDate ->
                selectedDateIso = newDate
                showDatePicker = false
            }
        )
    }
}
