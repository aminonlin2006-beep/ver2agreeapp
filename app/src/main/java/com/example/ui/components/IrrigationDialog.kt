package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.entity.FarmEntity
import com.example.data.entity.IrrigationScheduleEntity
import com.example.data.entity.PersonEntity
import com.example.data.entity.PlotEntity
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrrigationFormDialog(
    initialSchedule: IrrigationScheduleEntity? = null,
    preSelectedFarmId: String? = null,
    preSelectedPlotId: String? = null,
    farms: List<FarmEntity>,
    plots: List<PlotEntity>,
    persons: List<PersonEntity>,
    onDismiss: () -> Unit,
    onSave: (
        farmId: String,
        plotId: String,
        irrigatorPersonId: String?,
        dateIso: String,
        time: String,
        durationHours: Double?,
        waterSource: String?,
        reminderEnabled: Boolean,
        reminderMinutesBefore: Int,
        note: String
    ) -> Unit
) {
    val todayIso = remember { JalaliDateHelper.toIsoDate(LocalDate.now()) }
    val initialDate = initialSchedule?.date ?: todayIso

    var selectedFarmId by remember {
        mutableStateOf(initialSchedule?.farmId ?: preSelectedFarmId ?: farms.firstOrNull()?.id ?: "")
    }

    val availablePlots = remember(selectedFarmId, plots) {
        plots.filter { it.farmId == selectedFarmId && !it.deleted }
    }

    var selectedPlotId by remember {
        val defPlot = availablePlots.firstOrNull { it.id == (initialSchedule?.plotId ?: preSelectedPlotId) }
            ?: availablePlots.firstOrNull { !it.isDefault }
            ?: availablePlots.firstOrNull()
        mutableStateOf(defPlot?.id ?: "")
    }

    var selectedIrrigatorId by remember {
        mutableStateOf<String?>(initialSchedule?.irrigatorPersonId)
    }

    // Jalali date picker state
    val jDate = remember(initialDate) {
        try { JalaliDateHelper.fromIsoDate(initialDate) } catch (_: Exception) { JalaliDateHelper.today() }
    }
    var jalaliYear by remember { mutableStateOf(jDate.year) }
    var jalaliMonth by remember { mutableStateOf(jDate.month) }
    var jalaliDay by remember { mutableStateOf(jDate.day) }

    // Time picker state (e.g. "09:00" or "21:00")
    val initialTimeParts = (initialSchedule?.time ?: "08:00").split(":")
    var hourText by remember { mutableStateOf(initialTimeParts.getOrNull(0) ?: "08") }
    var minuteText by remember { mutableStateOf(initialTimeParts.getOrNull(1) ?: "00") }

    var durationText by remember {
        mutableStateOf(initialSchedule?.durationHours?.let {
            if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
        } ?: "2")
    }

    var selectedWaterSource by remember {
        mutableStateOf(initialSchedule?.waterSource ?: "well")
    }

    var reminderEnabled by remember {
        mutableStateOf(initialSchedule?.reminderEnabled ?: true)
    }

    var reminderMinutesBefore by remember {
        mutableStateOf(initialSchedule?.reminderMinutesBefore ?: 30)
    }

    var note by remember { mutableStateOf(initialSchedule?.note ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showFarmMenu by remember { mutableStateOf(false) }
    var showPlotMenu by remember { mutableStateOf(false) }
    var showIrrigatorMenu by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = if (initialSchedule == null) "برنامه‌ریزی آبیاری قطعه" else "ویرایش نوبت آبیاری",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider()

                // Error message banner
                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // 1. Farm Selector
                ExposedDropdownMenuBox(
                    expanded = showFarmMenu,
                    onExpandedChange = { showFarmMenu = !showFarmMenu }
                ) {
                    val farmName = farms.find { it.id == selectedFarmId }?.name ?: "انتخاب مزرعه"
                    OutlinedTextField(
                        value = farmName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("مزرعه") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showFarmMenu) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = showFarmMenu,
                        onDismissRequest = { showFarmMenu = false }
                    ) {
                        farms.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f.name) },
                                onClick = {
                                    selectedFarmId = f.id
                                    val fPlots = plots.filter { it.farmId == f.id && !it.deleted }
                                    selectedPlotId = fPlots.firstOrNull { !it.isDefault }?.id ?: fPlots.firstOrNull()?.id ?: ""
                                    showFarmMenu = false
                                }
                            )
                        }
                    }
                }

                // 2. Plot Selector
                ExposedDropdownMenuBox(
                    expanded = showPlotMenu,
                    onExpandedChange = { showPlotMenu = !showPlotMenu }
                ) {
                    val plotName = availablePlots.find { it.id == selectedPlotId }?.let {
                        if (it.isDefault) "تمام مزرعه / پیش‌فرض" else it.name
                    } ?: "انتخاب قطعه"
                    OutlinedTextField(
                        value = plotName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("قطعه مزرعه") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPlotMenu) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = showPlotMenu,
                        onDismissRequest = { showPlotMenu = false }
                    ) {
                        if (availablePlots.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("قطعه‌ای ثبت نشده است") },
                                onClick = { showPlotMenu = false }
                            )
                        } else {
                            availablePlots.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(if (p.isDefault) "تمام مزرعه / پیش‌فرض" else p.name) },
                                    onClick = {
                                        selectedPlotId = p.id
                                        showPlotMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. Irrigator Person Selector (نفر آبیارکننده)
                ExposedDropdownMenuBox(
                    expanded = showIrrigatorMenu,
                    onExpandedChange = { showIrrigatorMenu = !showIrrigatorMenu }
                ) {
                    val irrigatorName = persons.find { it.id == selectedIrrigatorId }?.let {
                        val ph = if (it.phone.isNotBlank()) " (${it.phone})" else ""
                        "${it.name}$ph"
                    } ?: "انتخاب شخص مسئول آبیاری (اختیاری)"
                    OutlinedTextField(
                        value = irrigatorName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("مسئول آبیاری (جهت ارسال پیامک و یادآوری)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showIrrigatorMenu) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = showIrrigatorMenu,
                        onDismissRequest = { showIrrigatorMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("بدون مشخص کردن شخص") },
                            onClick = {
                                selectedIrrigatorId = null
                                showIrrigatorMenu = false
                            }
                        )
                        persons.forEach { p ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(p.name, fontWeight = FontWeight.Bold)
                                        if (p.phone.isNotBlank()) {
                                            Text(p.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                },
                                onClick = {
                                    selectedIrrigatorId = p.id
                                    showIrrigatorMenu = false
                                }
                            )
                        }
                    }
                }

                // 4. Jalali Date
                Text("تاریخ آبیاری (شمسی)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Day
                    OutlinedTextField(
                        value = jalaliDay.toString(),
                        onValueChange = { jalaliDay = it.toIntOrNull()?.coerceIn(1, 31) ?: 1 },
                        label = { Text("روز") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    // Month
                    OutlinedTextField(
                        value = jalaliMonth.toString(),
                        onValueChange = { jalaliMonth = it.toIntOrNull()?.coerceIn(1, 12) ?: 1 },
                        label = { Text("ماه") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    // Year
                    OutlinedTextField(
                        value = jalaliYear.toString(),
                        onValueChange = { jalaliYear = it.toIntOrNull()?.coerceIn(1390, 1420) ?: 1404 },
                        label = { Text("سال") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.2f)
                    )
                }

                // 5. Time (ساعت و دقیقه)
                Text("ساعت شروع آبیاری", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = {
                            if (it.length <= 2) hourText = it
                        },
                        label = { Text("ساعت (۰ تا ۲۳)") },
                        placeholder = { Text("مثلاً ۹ یا ۲۱") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Text(":", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = {
                            if (it.length <= 2) minuteText = it
                        },
                        label = { Text("دقیقه (۰۰ تا ۵۹)") },
                        placeholder = { Text("۰۰") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                // 6. Duration & Water Source
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it },
                        label = { Text("مدت زمان (ساعت)") },
                        placeholder = { Text("مثلاً ۲") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )

                    // Water source picker
                    var showWaterMenu by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = showWaterMenu,
                        onExpandedChange = { showWaterMenu = !showWaterMenu },
                        modifier = Modifier.weight(1f)
                    ) {
                        val srcLabel = when (selectedWaterSource) {
                            "well" -> "چاه"
                            "canal" -> "کانال / جوی"
                            "pool" -> "استخر ذخیره"
                            "network" -> "شبکه / لوله"
                            else -> selectedWaterSource
                        }
                        OutlinedTextField(
                            value = srcLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("منبع آب") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showWaterMenu) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = showWaterMenu,
                            onDismissRequest = { showWaterMenu = false }
                        ) {
                            listOf(
                                "well" to "چاه",
                                "canal" to "کانال / جوی",
                                "pool" to "استخر ذخیره",
                                "network" to "شبکه / لوله"
                            ).forEach { (k, v) ->
                                DropdownMenuItem(
                                    text = { Text(v) },
                                    onClick = {
                                        selectedWaterSource = k
                                        showWaterMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 7. Notification Reminder Toggle
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (reminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                                Text("یادآوری در نوتیفیکیشن‌ها", fontWeight = FontWeight.Bold)
                            }
                            Switch(
                                checked = reminderEnabled,
                                onCheckedChange = { reminderEnabled = it }
                            )
                        }

                        if (reminderEnabled) {
                            Text("زمان ارسال هشدار نوتیفیکیشن:", style = MaterialTheme.typography.labelSmall)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf(
                                    0 to "سر وقت",
                                    15 to "۱۵ دقیقه قبل",
                                    30 to "۳۰ دقیقه قبل",
                                    60 to "۱ ساعت قبل"
                                ).forEach { (min, label) ->
                                    FilterChip(
                                        selected = reminderMinutesBefore == min,
                                        onClick = { reminderMinutesBefore = min },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 8. Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیحات و نکات نوبت آبیاری (اختیاری)") },
                    placeholder = { Text("مثلاً فلکه اصلی باز شود یا کود مایع تزریق گردد...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                // Actions: Cancel & Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("انصراف")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (selectedFarmId.isBlank()) {
                                errorMessage = "لطفاً یک مزرعه را انتخاب کنید."
                                return@Button
                            }
                            if (selectedPlotId.isBlank()) {
                                errorMessage = "لطفاً قطعه موردنظر را انتخاب کنید."
                                return@Button
                            }

                            val cleanHour = hourText.trim().toIntOrNull()?.coerceIn(0, 23) ?: 8
                            val cleanMin = minuteText.trim().toIntOrNull()?.coerceIn(0, 59) ?: 0
                            val timeStr = "%02d:%02d".format(cleanHour, cleanMin)

                            val durationVal = durationText.trim().toDoubleOrNull()

                            val gDate = try {
                                JalaliDateHelper.toGregorian(jalaliYear, jalaliMonth, jalaliDay)
                            } catch (_: Exception) {
                                LocalDate.now()
                            }
                            val dateIso = JalaliDateHelper.toIsoDate(gDate)

                            onSave(
                                selectedFarmId,
                                selectedPlotId,
                                selectedIrrigatorId,
                                dateIso,
                                timeStr,
                                durationVal,
                                selectedWaterSource,
                                reminderEnabled,
                                reminderMinutesBefore,
                                note
                            )
                        },
                        modifier = Modifier.testTag("save_irrigation_btn")
                    ) {
                        Text(if (initialSchedule == null) "ثبت زمان‌بندی" else "ذخیره تغییرات")
                    }
                }
            }
        }
    }
}
