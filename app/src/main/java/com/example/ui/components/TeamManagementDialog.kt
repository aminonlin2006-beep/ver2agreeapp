package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.entity.CrewEntity
import com.example.data.entity.CrewMemberEntity
import com.example.data.entity.PersonEntity

@Composable
fun TeamManagementDialog(
    foreman: PersonEntity,
    crews: List<CrewEntity>,
    crewMembers: List<CrewMemberEntity>,
    allWorkers: List<PersonEntity>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit // workerIds
) {
    val foremanCrew = crews.find { it.foremanId == foreman.id && !it.deleted }
    val initialMemberIds = remember(foremanCrew, crewMembers) {
        if (foremanCrew != null) {
            crewMembers.filter { it.crewId == foremanCrew.id && !it.deleted }.map { it.personId }
        } else emptyList()
    }
    var selectedWorkerIds by remember { mutableStateOf(initialMemberIds) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مدیریت تیم سرکارگر: ${foreman.name}") },
        text = {
            if (allWorkers.isEmpty()) {
                Text(
                    text = "کارگر دیگری در سامانه ثبت نشده است.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)
                ) {
                    items(allWorkers, key = { it.id }) { worker ->
                        val isChecked = selectedWorkerIds.contains(worker.id)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedWorkerIds = if (isChecked) {
                                        selectedWorkerIds - worker.id
                                    } else {
                                        selectedWorkerIds + worker.id
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedWorkerIds = if (checked) {
                                            selectedWorkerIds + worker.id
                                        } else {
                                            selectedWorkerIds - worker.id
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(worker.name, style = MaterialTheme.typography.bodyMedium)
                                    if (worker.phone.isNotBlank()) {
                                        Text(
                                            text = com.example.lib.NumberFormatHelper.toPersianDigits(worker.phone),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                onSave(selectedWorkerIds)
                onDismiss() 
            }) { Text("ذخیره") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
