package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.entity.EmploymentEntity
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmploymentDialog(
    personId: String,
    initialEmployment: EmploymentEntity? = null,
    initialSalary: Long = 0L,
    onDismiss: () -> Unit,
    onSave: (EmploymentEntity, Long) -> Unit
) {
    var kind by remember { mutableStateOf(initialEmployment?.kind ?: "fixed") }
    var salary by remember { mutableStateOf(initialSalary.toString()) }
    var salaryMonth by remember { mutableStateOf(initialEmployment?.salaryFromMonth ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تنظیمات کارگر ثابت") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    RadioButton(selected = kind == "fixed", onClick = { kind = "fixed" })
                    Text("ثابت")
                    RadioButton(selected = kind == "salkar", onClick = { kind = "salkar" })
                    Text("سال‌کار")
                }
                OutlinedTextField(value = salary, onValueChange = { salary = it }, label = { Text("حقوق ماهانه") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = salaryMonth, onValueChange = { salaryMonth = it }, label = { Text("حقوق از ماه (YYYY-MM)") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = EmploymentEntity(
                    personId = personId,
                    kind = kind,
                    salaryFromMonth = salaryMonth
                )
                onSave(e, salary.toLongOrNull() ?: 0L)
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
