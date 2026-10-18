package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.entity.CropEntity
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropFormDialog(
    initialCrop: CropEntity? = null,
    plotId: String,
    onDismiss: () -> Unit,
    onSave: (CropEntity) -> Unit
) {
    var name by remember { mutableStateOf(initialCrop?.name ?: "") }
    var startDate by remember { mutableStateOf(initialCrop?.startDate ?: LocalDate.now().toString()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کشت تازه") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام کشت") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = startDate, onValueChange = { startDate = it }, label = { Text("تاریخ شروع") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val crop = CropEntity(
                    id = initialCrop?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name,
                    plotId = plotId,
                    startDate = startDate
                )
                onSave(crop)
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
