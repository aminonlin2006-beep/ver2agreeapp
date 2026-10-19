package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.entity.EmploymentEntity
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmploymentDialog(
    personId: String,
    personName: String = "",
    initialEmployment: EmploymentEntity? = null,
    initialSalary: Long = 0L,
    onDismiss: () -> Unit,
    onSave: (EmploymentEntity, Long, Boolean) -> Unit
) {
    var kind by remember { mutableStateOf(initialEmployment?.kind ?: "fixed") }
    var salaryInput by remember { mutableStateOf(if (initialSalary > 0) initialSalary.toString() else "") }
    
    val currentJalali = remember { JalaliDateHelper.today() }
    val initialIsoDate = remember(initialEmployment) {
        if (!initialEmployment?.salaryFromMonth.isNullOrBlank()) {
            val parts = initialEmployment!!.salaryFromMonth.split("-")
            val yr = parts.getOrNull(0)?.toIntOrNull() ?: currentJalali.year
            val mo = parts.getOrNull(1)?.toIntOrNull() ?: currentJalali.month
            val g = JalaliDateHelper.toGregorian(yr, mo, 1)
            JalaliDateHelper.toIsoDate(g)
        } else {
            JalaliDateHelper.toIsoDate(java.time.LocalDate.now())
        }
    }
    
    var selectedIsoDate by remember { mutableStateOf(initialIsoDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showRetroactiveConfirm by remember { mutableStateOf(false) }

    val selectedJalali = remember(selectedIsoDate) { JalaliDateHelper.fromIsoDate(selectedIsoDate) }
    val salaryMonthKey = selectedJalali.monthKey // e.g. "1404-01"
    val isPastMonth = salaryMonthKey < currentJalali.monthKey
    val salaryLong = NumberFormatHelper.parsePersianLong(salaryInput) ?: 0L

    if (showRetroactiveConfirm) {
        AlertDialog(
            onDismissRequest = { showRetroactiveConfirm = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = {
                Text(
                    text = "اعمال حقوق و حضور برای ماه‌های گذشته",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "تاریخ شروع حقوق (${selectedJalali.monthName} ${NumberFormatHelper.toPersianDigits(selectedJalali.year)}) قبل از ماه جاری است.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "آیا حقوق و حضور برای ماه‌های گذشته تا امروز اعمال و حساب شود؟\n(در صورت انتخاب بله، کارکرد و بستانکاری ثبت شده و حضور کارگر نیز تا امروز ثبت می‌گردد و بعداً می‌توانید برای روزهای مشخصی غیبت ثبت نمایید.)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val e = EmploymentEntity(
                            id = initialEmployment?.id ?: java.util.UUID.randomUUID().toString(),
                            personId = personId,
                            kind = kind,
                            salaryFromMonth = salaryMonthKey,
                            workStartDate = selectedIsoDate
                        )
                        showRetroactiveConfirm = false
                        onSave(e, salaryLong, true)
                    },
                    modifier = Modifier.testTag("confirm_apply_retroactive_btn")
                ) {
                    Text("بله، اعمال و ثبت شود")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        val e = EmploymentEntity(
                            id = initialEmployment?.id ?: java.util.UUID.randomUUID().toString(),
                            personId = personId,
                            kind = kind,
                            salaryFromMonth = salaryMonthKey,
                            workStartDate = selectedIsoDate
                        )
                        showRetroactiveConfirm = false
                        onSave(e, salaryLong, false)
                    }
                ) {
                    Text("خیر، فقط از ماه جاری")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (personName.isNotBlank()) "تنظیم حقوق کارگر ($personName)" else "تنظیم حقوق ثابت کارگر",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                // Kind selection
                Text("نوع قرارداد کاری:", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == "fixed",
                        onClick = { kind = "fixed" },
                        label = { Text("ثابت ماهانه") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = kind == "salkar",
                        onClick = { kind = "salkar" },
                        label = { Text("سال‌کار") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Salary input
                OutlinedTextField(
                    value = salaryInput,
                    onValueChange = { salaryInput = it },
                    label = { Text("حقوق ماهانه (تومان) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    supportingText = {
                        if (salaryLong > 0) {
                            Text(
                                text = "${NumberFormatHelper.formatTomanFull(salaryLong)} (${NumberFormatHelper.formatTomanShort(salaryLong)})",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("employment_salary_input")
                )

                // Starting Date Picker Button
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "تاریخ شروع حقوق:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${selectedJalali.monthName} ${NumberFormatHelper.toPersianDigits(selectedJalali.year)} (${selectedJalali.toDisplayShort()})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "انتخاب تاریخ",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (isPastMonth) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "تاریخ شروع متعلق به ماه‌های گذشته است. موقع ذخیره می‌توانید تعیین کنید تا امروز محاسبه و حضورش اعمال شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isPastMonth) {
                        showRetroactiveConfirm = true
                    } else {
                        val e = EmploymentEntity(
                            id = initialEmployment?.id ?: java.util.UUID.randomUUID().toString(),
                            personId = personId,
                            kind = kind,
                            salaryFromMonth = salaryMonthKey,
                            workStartDate = selectedIsoDate
                        )
                        onSave(e, salaryLong, false)
                    }
                },
                enabled = salaryLong > 0,
                modifier = Modifier.testTag("save_employment_btn")
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

    if (showDatePicker) {
        JalaliDatePickerDialog(
            initialIso = selectedIsoDate,
            onDismiss = { showDatePicker = false },
            onDateSelected = { newDate ->
                selectedIsoDate = newDate
                showDatePicker = false
            }
        )
    }
}
