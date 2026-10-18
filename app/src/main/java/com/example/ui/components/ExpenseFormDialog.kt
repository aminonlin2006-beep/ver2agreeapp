package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.entity.ExpenseEntity
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFormDialog(
    initialExpense: ExpenseEntity? = null,
    uiState: com.example.ui.MainUiState,
    onDismiss: () -> Unit,
    onSave: (ExpenseEntity) -> Unit
) {
    var amountStr by remember {
        mutableStateOf(initialExpense?.amount?.toString() ?: "")
    }
    var selectedTypeId by remember {
        mutableStateOf(initialExpense?.typeId ?: uiState.expenseTypes.firstOrNull { !it.hidden }?.id ?: "")
    }
    var selectedDateIso by remember {
        mutableStateOf(initialExpense?.date ?: LocalDate.now().toString())
    }
    var showDatePicker by remember { mutableStateOf(false) }

    var selectedFarmId by remember { mutableStateOf(initialExpense?.farmId) }
    var selectedPlotId by remember { mutableStateOf(initialExpense?.plotId) }
    var counterpartyId by remember { mutableStateOf(initialExpense?.counterpartyId) }
    var payMode by remember { mutableStateOf(initialExpense?.payMode ?: "cash") } // cash, credit, partial
    var paidAmountStr by remember { mutableStateOf(initialExpense?.paidAmount?.toString() ?: "") }
    var note by remember { mutableStateOf(initialExpense?.note ?: "") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Dropdown expanded states
    var farmDropdownExpanded by remember { mutableStateOf(false) }
    var plotDropdownExpanded by remember { mutableStateOf(false) }
    var personDropdownExpanded by remember { mutableStateOf(false) }

    val activeFarms = remember(uiState.farms) { uiState.farms.filter { it.active } }
    val selectablePlots = remember(selectedFarmId, uiState.plots) {
        if (selectedFarmId != null) {
            uiState.plots.filter { it.farmId == selectedFarmId && !it.isDefault && it.active }
        } else emptyList()
    }

    val jalaliDate = remember(selectedDateIso) {
        JalaliDateHelper.fromIsoDate(selectedDateIso)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialExpense == null) "ثبت هزینه تازه" else "ویرایش هزینه",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Amount (تومان)
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        amountStr = digits
                        errorMessage = null
                    },
                    label = { Text("مبلغ هزینه (تومان)*") },
                    placeholder = { Text("مثلاً ۵۰۰۰۰۰") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    supportingText = {
                        val amt = amountStr.toLongOrNull() ?: 0L
                        if (amt > 0) {
                            Text(
                                text = "${NumberFormatHelper.formatTomanFull(amt)} (${NumberFormatHelper.formatTomanShort(amt)})",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_amount_input")
                )

                // Date Picker Button
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
                                text = "تاریخ هزینه",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = jalaliDate.toDisplayFull(),
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

                // Expense Type
                Text(
                    text = "نوع هزینه*",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                val visibleTypes = uiState.expenseTypes.filter { !it.hidden }
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    visibleTypes.forEach { type ->
                        val isSelected = selectedTypeId == type.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTypeId = type.id
                                    errorMessage = null
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedTypeId = type.id }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = type.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Farm Selector (Optional)
                Text(
                    text = "مزرعه و قطعه (اختیاری)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                ExposedDropdownMenuBox(
                    expanded = farmDropdownExpanded,
                    onExpandedChange = { farmDropdownExpanded = !farmDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val farmName = uiState.farms.find { it.id == selectedFarmId }?.name ?: "عمومی (کل مزارع)"
                    OutlinedTextField(
                        value = farmName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("مزرعه") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = farmDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = farmDropdownExpanded,
                        onDismissRequest = { farmDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("عمومی (کل مزارع)") },
                            onClick = {
                                selectedFarmId = null
                                selectedPlotId = null
                                farmDropdownExpanded = false
                            }
                        )
                        activeFarms.forEach { farm ->
                            DropdownMenuItem(
                                text = { Text(farm.name) },
                                onClick = {
                                    selectedFarmId = farm.id
                                    selectedPlotId = null
                                    farmDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Plot Selector (if farm selected and has plots)
                if (selectedFarmId != null && selectablePlots.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = plotDropdownExpanded,
                        onExpandedChange = { plotDropdownExpanded = !plotDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val plotName = selectablePlots.find { it.id == selectedPlotId }?.name ?: "کل این مزرعه"
                        OutlinedTextField(
                            value = plotName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("قطعه") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = plotDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = plotDropdownExpanded,
                            onDismissRequest = { plotDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("کل این مزرعه") },
                                onClick = {
                                    selectedPlotId = null
                                    plotDropdownExpanded = false
                                }
                            )
                            selectablePlots.forEach { plot ->
                                DropdownMenuItem(
                                    text = { Text(plot.name) },
                                    onClick = {
                                        selectedPlotId = plot.id
                                        plotDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Counterparty / Person (طرف‌حساب)
                Text(
                    text = "طرف‌حساب / شخص مرتبط (اختیاری)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                ExposedDropdownMenuBox(
                    expanded = personDropdownExpanded,
                    onExpandedChange = { personDropdownExpanded = !personDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val personName = uiState.persons.find { it.id == counterpartyId }?.name ?: "بدون طرف‌حساب خاص"
                    OutlinedTextField(
                        value = personName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("شخص طرف‌حساب") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = personDropdownExpanded,
                        onDismissRequest = { personDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("بدون طرف‌حساب خاص") },
                            onClick = {
                                counterpartyId = null
                                personDropdownExpanded = false
                            }
                        )
                        uiState.persons.forEach { person ->
                            DropdownMenuItem(
                                text = { Text(person.name) },
                                onClick = {
                                    counterpartyId = person.id
                                    personDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Payment Mode (نحوه پرداخت)
                Text(
                    text = "وضعیت پرداخت",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = payMode == "cash",
                        onClick = { payMode = "cash" },
                        label = { Text("نقدی / پرداخت‌شده") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = payMode == "credit",
                        onClick = { payMode = "credit" },
                        label = { Text("نسیه / بدهی") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیحات / جزئیات خرید") },
                    placeholder = { Text("مثلاً خرید کود از فروشگاه مرکزی") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountStr.toLongOrNull() ?: 0L
                    if (amount <= 0L) {
                        errorMessage = "لطفاً مبلغ معتبری وارد کنید."
                        return@Button
                    }
                    if (selectedTypeId.isBlank()) {
                        errorMessage = "لطفاً نوع هزینه را انتخاب کنید."
                        return@Button
                    }

                    val expense = ExpenseEntity(
                        id = initialExpense?.id ?: java.util.UUID.randomUUID().toString(),
                        date = selectedDateIso,
                        amount = amount,
                        typeId = selectedTypeId,
                        farmId = selectedFarmId,
                        plotId = selectedPlotId,
                        workerIds = initialExpense?.workerIds ?: "[]",
                        tagId = initialExpense?.tagId,
                        cropId = initialExpense?.cropId,
                        counterpartyId = counterpartyId,
                        payMode = payMode,
                        paidAmount = if (payMode == "cash") amount else paidAmountStr.toLongOrNull(),
                        note = note.trim()
                    )
                    onSave(expense)
                },
                modifier = Modifier.testTag("save_expense_btn")
            ) {
                Text("ذخیره هزینه")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
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
