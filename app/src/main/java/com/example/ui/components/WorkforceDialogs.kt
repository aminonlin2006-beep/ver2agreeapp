package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.*
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import com.example.lib.PaymentShare
import com.example.lib.WorkforceCalculator
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.theme.ColorDangerRed
import com.example.ui.theme.ColorSuccessGreen
import com.example.ui.theme.ColorWarningOrange
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

// -------------------------------------------------------------
// 1. CREW DIALOG (Create & Edit)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewDialog(
    initialCrew: CrewEntity? = null,
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onSave: (name: String, kind: String, foremanId: String?, dailyWage: Long, effectiveFrom: String, note: String) -> Unit
) {
    var name by remember { mutableStateOf(initialCrew?.name ?: "") }
    var kind by remember { mutableStateOf(initialCrew?.kind ?: "members") } // "members" | "count"
    var selectedForemanId by remember { mutableStateOf(initialCrew?.foremanId) }
    var note by remember { mutableStateOf(initialCrew?.note ?: "") }

    val todayIso = remember { JalaliDateHelper.toIsoDate(LocalDate.now()) }
    var effectiveFrom by remember { mutableStateOf(todayIso) }

    // Existing wage for crew
    val initialWage = remember(initialCrew, uiState.wageRates) {
        if (initialCrew != null) {
            WorkforceCalculator.getEffectiveDailyWage(uiState.wageRates, initialCrew.id, null, todayIso)
        } else 800_000L
    }
    var dailyWageInput by remember { mutableStateOf(if (initialWage > 0) initialWage.toString() else "800000") }

    val activePersons = remember(uiState.persons) { uiState.persons.filter { it.active } }
    var isForemanExpanded by remember { mutableStateOf(false) }

    val wageLong = NumberFormatHelper.parsePersianLong(dailyWageInput) ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialCrew == null) "دسته‌ی تازه" else "ویرایش دسته", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام دسته (اجباری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("crew_name_input")
                )

                // Kind selector: "با اعضا" vs "شمارشی"
                Text("نوع دسته:", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == "members",
                        onClick = { kind = "members" },
                        label = { Text("با اعضا (با نام)") },
                        modifier = Modifier.weight(1f).testTag("crew_kind_members_chip")
                    )
                    FilterChip(
                        selected = kind == "count",
                        onClick = { kind = "count" },
                        label = { Text("شمارشی (بدون نام)") },
                        modifier = Modifier.weight(1f).testTag("crew_kind_count_chip")
                    )
                }

                // Foreman Picker
                ExposedDropdownMenuBox(
                    expanded = isForemanExpanded,
                    onExpandedChange = { isForemanExpanded = !isForemanExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val foremanName = activePersons.find { it.id == selectedForemanId }?.name ?: "بدون سرکارگر"
                    OutlinedTextField(
                        value = foremanName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("سرکارگر (اختیاری)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isForemanExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor().testTag("crew_foreman_picker")
                    )
                    ExposedDropdownMenu(
                        expanded = isForemanExpanded,
                        onDismissRequest = { isForemanExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("بدون سرکارگر") },
                            onClick = {
                                selectedForemanId = null
                                isForemanExpanded = false
                            }
                        )
                        activePersons.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                onClick = {
                                    selectedForemanId = p.id
                                    isForemanExpanded = false
                                }
                            )
                        }
                    }
                }

                // Daily Wage
                OutlinedTextField(
                    value = dailyWageInput,
                    onValueChange = { dailyWageInput = it },
                    label = { Text("مزد روزانه پایه (تومان)") },
                    supportingText = {
                        if (wageLong > 0) {
                            Text(NumberFormatHelper.formatTomanFull(wageLong), color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("crew_wage_input")
                )

                // Effective From Date
                OutlinedTextField(
                    value = JalaliDateHelper.fromIsoDate(effectiveFrom).toDisplayFull(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("از تاریخ") },
                    modifier = Modifier.fillMaxWidth().testTag("crew_effective_from_input")
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیح (اختیاری)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("crew_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name.trim(), kind, selectedForemanId, wageLong, effectiveFrom, note.trim())
                },
                enabled = name.isNotBlank() && wageLong > 0,
                modifier = Modifier.testTag("save_crew_btn")
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
}

// -------------------------------------------------------------
// 2. ADD CREW MEMBER DIALOG (Select from persons or Quick Add)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCrewMemberDialog(
    crew: CrewEntity,
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onAddExisting: (personId: String) -> Unit,
    onAddNewPerson: (name: String, phone: String) -> Unit
) {
    var showQuickAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    // Eligible persons: active, not crew foreman (Decision 284), not already in this crew
    val currentMemberIds = remember(crew.id, uiState.crewMembers) {
        uiState.crewMembers.filter { it.crewId == crew.id && it.active && !it.deleted }.map { it.personId }.toSet()
    }
    val eligiblePersons = remember(uiState.persons, currentMemberIds, crew.foremanId) {
        uiState.persons.filter { p ->
            p.active && !currentMemberIds.contains(p.id) && p.id != crew.foremanId
        }
    }

    var selectedPersonId by remember { mutableStateOf(eligiblePersons.firstOrNull()?.id) }
    var isExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن عضو به «${crew.name}»", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (!showQuickAdd) {
                    if (eligiblePersons.isEmpty()) {
                        Text(
                            "همه‌ی اشخاص فعال در حال حاضر عضو این دسته هستند یا شخصی ثبت نشده است.",
                            color = MaterialTheme.colorScheme.outline,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = isExpanded,
                            onExpandedChange = { isExpanded = !isExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val curName = eligiblePersons.find { it.id == selectedPersonId }?.name ?: "انتخاب شخص"
                            OutlinedTextField(
                                value = curName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("انتخاب از اشخاص") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor().testTag("select_person_to_crew_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = isExpanded,
                                onDismissRequest = { isExpanded = false }
                            ) {
                                eligiblePersons.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.name) },
                                        onClick = {
                                            selectedPersonId = p.id
                                            isExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { showQuickAdd = true },
                        modifier = Modifier.align(Alignment.End).testTag("quick_add_new_person_crew_btn")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ شخص تازه")
                    }
                } else {
                    Text("مشخصات شخص تازه (نقش کارگر خودکار داده می‌شود):", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("نام و نام خانوادگی (اجباری)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_person_name_crew_input")
                    )
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("شماره همراه (اختیاری)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_person_phone_crew_input")
                    )

                    TextButton(
                        onClick = { showQuickAdd = false },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text("← بازگشت به لیست اشخاص")
                    }
                }
            }
        },
        confirmButton = {
            if (!showQuickAdd) {
                Button(
                    onClick = {
                        selectedPersonId?.let { onAddExisting(it) }
                    },
                    enabled = selectedPersonId != null,
                    modifier = Modifier.testTag("confirm_add_member_existing_btn")
                ) {
                    Text("افزودن")
                }
            } else {
                Button(
                    onClick = {
                        onAddNewPerson(newName.trim(), newPhone.trim())
                    },
                    enabled = newName.isNotBlank(),
                    modifier = Modifier.testTag("confirm_add_member_new_btn")
                ) {
                    Text("ثبت و افزودن")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

// -------------------------------------------------------------
// 3. WAGE CHANGE DIALOG (Crew or Specific Member)
// -------------------------------------------------------------
@Composable
fun WageChangeDialog(
    title: String,
    crewId: String,
    personId: String?,
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onSave: (dailyWage: Long, effectiveFrom: String) -> Unit
) {
    val todayIso = remember { JalaliDateHelper.toIsoDate(LocalDate.now()) }
    var effectiveFrom by remember { mutableStateOf(todayIso) }
    var showDatePicker by remember { mutableStateOf(false) }

    val currentWage = remember(crewId, personId, uiState.wageRates) {
        WorkforceCalculator.getEffectiveDailyWage(uiState.wageRates, crewId, personId, todayIso)
    }
    var wageInput by remember { mutableStateOf(if (currentWage > 0) currentWage.toString() else "") }
    val wageLong = NumberFormatHelper.parsePersianLong(wageInput) ?: 0L

    // Wage rate history
    val historyRates = remember(crewId, personId, uiState.wageRates) {
        uiState.wageRates.filter {
            it.crewId == crewId && it.personId == personId && !it.deleted
        }.sortedByDescending { it.effectiveFrom }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = wageInput,
                    onValueChange = { wageInput = it },
                    label = { Text("مبلغ تازه روزانه (تومان)") },
                    supportingText = {
                        if (wageLong > 0) {
                            Text(NumberFormatHelper.formatTomanFull(wageLong), color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_wage_amount_input")
                )

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
                                text = "اعمال از تاریخ:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = JalaliDateHelper.fromIsoDate(effectiveFrom).toDisplayFull(),
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

                if (historyRates.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("سابقه‌ی نرخ‌های قبلی:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp)) {
                        items(historyRates) { r ->
                            val dateFa = JalaliDateHelper.fromIsoDate(r.effectiveFrom).toDisplayShort()
                            val wageFa = NumberFormatHelper.formatTomanFull(r.dailyWage)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(wageFa, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text("از $dateFa", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(wageLong, effectiveFrom) },
                enabled = wageLong > 0,
                modifier = Modifier.testTag("confirm_wage_change_btn")
            ) {
                Text("ثبت مزد تازه")
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
            initialIso = effectiveFrom,
            onDismiss = { showDatePicker = false },
            onDateSelected = { newDate ->
                effectiveFrom = newDate
                showDatePicker = false
            }
        )
    }
}

// -------------------------------------------------------------
// 4. ATTENDANCE DIALOG (Section 2 - Registration & Live Preview)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceDialog(
    initialAttendance: AttendanceEntity? = null,
    prefilledDate: String? = null,
    prefilledFarmId: String? = null,
    prefilledPlotId: String? = null,
    prefilledCrewId: String? = null,
    prefilledTagId: String? = null,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSave: (date: String, farmId: String?, plotId: String?, crewId: String, tagId: String, lines: String, headcount: Int?, countTicks: Int?, foreman: String?, note: String) -> Unit
) {
    val todayIso = remember { JalaliDateHelper.toIsoDate(LocalDate.now()) }
    var date by remember { mutableStateOf(initialAttendance?.date ?: prefilledDate ?: todayIso) }
    var showDatePicker by remember { mutableStateOf(false) }
    var viewAllWorkersMode by remember { mutableStateOf(false) }

    val activeCrews = remember(uiState.crews) { uiState.crews.filter { it.active } }
    var selectedCrewId by remember {
        mutableStateOf(initialAttendance?.crewId ?: prefilledCrewId ?: activeCrews.firstOrNull()?.id ?: "")
    }

    val selectedCrew = remember(selectedCrewId, uiState.crews) {
        uiState.crews.find { it.id == selectedCrewId }
    }

    // Default farm & plot: prefilled, or last attendance of this crew, or first farm
    val lastAttOfCrew = remember(selectedCrewId, uiState.attendances) {
        uiState.attendances.filter { it.crewId == selectedCrewId && !it.deleted }.maxByOrNull { it.date }
    }

    var selectedFarmId by remember {
        mutableStateOf(initialAttendance?.farmId ?: prefilledFarmId ?: lastAttOfCrew?.farmId ?: uiState.farms.firstOrNull()?.id)
    }

    var selectablePlots by remember { mutableStateOf<List<PlotEntity>>(emptyList()) }
    var selectedPlotId by remember {
        mutableStateOf(initialAttendance?.plotId ?: prefilledPlotId ?: lastAttOfCrew?.plotId)
    }

    LaunchedEffect(selectedFarmId) {
        if (selectedFarmId != null) {
            val plots = viewModel.gateway.selectablePlots(selectedFarmId!!)
            selectablePlots = plots
            if (selectedPlotId == null || plots.none { it.id == selectedPlotId }) {
                selectedPlotId = plots.firstOrNull()?.id
            }
        } else {
            selectablePlots = emptyList()
            selectedPlotId = null
        }
    }

    // Tag (mandatory, default last tag of this crew)
    val tagsList = remember(uiState.tags) { uiState.tags.filter { !it.hidden } }
    var selectedTagId by remember {
        mutableStateOf(initialAttendance?.tagId ?: prefilledTagId ?: lastAttOfCrew?.tagId ?: tagsList.firstOrNull()?.id ?: "")
    }

    var note by remember { mutableStateOf(initialAttendance?.note ?: "") }

    // Count crew fields
    var headcountInput by remember {
        mutableStateOf(initialAttendance?.headcount?.toString() ?: "10")
    }
    var countTicks by remember { mutableStateOf(initialAttendance?.countTicks ?: 1) }

    // Members crew: Map of personId -> (checked, ticks)
    val crewMembers = remember(selectedCrewId, uiState.crewMembers) {
        uiState.crewMembers.filter { it.crewId == selectedCrewId && it.active && !it.deleted }
    }

    // Map of member state
    val memberSelections = remember(crewMembers, initialAttendance) {
        val map = mutableStateMapOf<String, Pair<Boolean, Int>>()
        if (initialAttendance != null && initialAttendance.crewId == selectedCrewId) {
            try {
                val arr = JSONArray(initialAttendance.lines)
                val checkedIds = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val pId = o.getString("personId")
                    val t = o.optInt("ticks", 1)
                    map[pId] = Pair(true, t)
                    checkedIds.add(pId)
                }
                crewMembers.forEach { m ->
                    if (!checkedIds.contains(m.personId)) {
                        map[m.personId] = Pair(false, 1)
                    }
                }
            } catch (_: Exception) {}
        } else {
            crewMembers.forEach { m ->
                map[m.personId] = Pair(false, 1)
            }
        }
        map
    }

    // Foreman state
    val hasForeman = selectedCrew?.foremanId != null
    val foremanPerson = remember(selectedCrew?.foremanId, uiState.persons) {
        uiState.persons.find { it.id == selectedCrew?.foremanId }
    }
    var foremanPresent by remember { mutableStateOf(true) }
    var foremanTicks by remember { mutableStateOf(1) }

    // Auto-update foreman ticks when member ticks change (Decision 191)
    val maxMemberTicks = memberSelections.filter { it.value.first }.maxOfOrNull { it.value.second } ?: 1
    LaunchedEffect(maxMemberTicks, countTicks, selectedCrew?.kind) {
        if (initialAttendance == null) {
            if (selectedCrew?.kind == "members") {
                foremanTicks = maxMemberTicks
            } else {
                foremanTicks = countTicks
            }
        }
    }

    // Quick add person in attendance
    var showQuickAddPerson by remember { mutableStateOf(false) }
    var newPersonName by remember { mutableStateOf("") }
    var newPersonPhone by remember { mutableStateOf("") }

    val personsMap = remember(uiState.persons) { uiState.persons.associateBy { it.id } }

    // Live Wage Preview calculation
    val livePreview = remember(
        selectedCrew, date, memberSelections.toMap(), headcountInput, countTicks,
        foremanPresent, foremanTicks, uiState.wageRates, uiState.foremanCoefPresent, uiState.foremanCoefAbsent
    ) {
        if (selectedCrew == null) null else {
            val linesJson = if (selectedCrew.kind == "members") {
                val arr = JSONArray()
                memberSelections.forEach { (pId, sel) ->
                    if (sel.first) {
                        arr.put(JSONObject().apply {
                            put("personId", pId)
                            put("ticks", sel.second)
                        })
                    }
                }
                arr.toString()
            } else "[]"

            val fJson = if (hasForeman && foremanPerson != null) {
                JSONObject().apply {
                    put("personId", foremanPerson.id)
                    put("ticks", foremanTicks)
                    put("present", foremanPresent)
                }.toString()
            } else null

            val tempAtt = AttendanceEntity(
                date = date,
                crewId = selectedCrew.id,
                tagId = selectedTagId,
                lines = linesJson,
                headcount = headcountInput.toIntOrNull() ?: 0,
                countTicks = countTicks,
                foreman = fJson,
                coefPresent = uiState.foremanCoefPresent,
                coefAbsent = uiState.foremanCoefAbsent
            )
            WorkforceCalculator.calculateAttendanceWage(tempAtt, selectedCrew, uiState.wageRates, personsMap)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialAttendance == null) "ثبت حضور و کارکرد" else "ویرایش ثبت حضور",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "بستن")
                    }
                }

                Divider()

                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    // 1. Date (with JalaliDatePicker)
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDatePicker = true }
                                .testTag("attendance_date_input")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("تاریخ حضور و غیاب:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = JalaliDateHelper.fromIsoDate(date).toDisplayFull(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(Icons.Default.CalendarMonth, contentDescription = "انتخاب تاریخ", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // 2. Crew Selector
                    item {
                        Text("دسته:", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            activeCrews.forEach { c ->
                                val displayName = remember(c, personsMap) {
                                    if (c.foremanId != null) {
                                        val fName = personsMap[c.foremanId]?.name
                                        if (!fName.isNullOrBlank() && !c.name.startsWith("دسته‌ی")) "دسته‌ی $fName" else c.name
                                    } else c.name
                                }
                                FilterChip(
                                    selected = c.id == selectedCrewId,
                                    onClick = { selectedCrewId = c.id },
                                    label = { Text(displayName) },
                                    modifier = Modifier.testTag("attendance_crew_chip_${c.id}")
                                )
                            }
                        }
                    }

                    // 3. Farm & Plot Selector
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Farm
                            var farmExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = farmExpanded,
                                onExpandedChange = { farmExpanded = !farmExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                val currentFarmName = uiState.farms.find { it.id == selectedFarmId }?.name ?: "مزرعه"
                                OutlinedTextField(
                                    value = currentFarmName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("مزرعه") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = farmExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor().testTag("attendance_farm_picker")
                                )
                                ExposedDropdownMenu(expanded = farmExpanded, onDismissRequest = { farmExpanded = false }) {
                                    uiState.farms.filter { it.active }.forEach { f ->
                                        DropdownMenuItem(
                                            text = { Text(f.name) },
                                            onClick = {
                                                selectedFarmId = f.id
                                                farmExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Plot
                            var plotExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = plotExpanded,
                                onExpandedChange = { plotExpanded = !plotExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                val currentPlotName = selectablePlots.find { it.id == selectedPlotId }?.let {
                                    if (it.isDefault) "کل مزرعه" else it.name
                                } ?: "قطعه"
                                OutlinedTextField(
                                    value = currentPlotName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("قطعه") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = plotExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor().testTag("attendance_plot_picker")
                                )
                                ExposedDropdownMenu(expanded = plotExpanded, onDismissRequest = { plotExpanded = false }) {
                                    selectablePlots.forEach { p ->
                                        val pName = if (p.isDefault) "کل مزرعه" else p.name
                                        DropdownMenuItem(
                                            text = { Text(pName) },
                                            onClick = {
                                                selectedPlotId = p.id
                                                plotExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Tag Selector (Mandatory)
                    item {
                        var tagExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = tagExpanded,
                            onExpandedChange = { tagExpanded = !tagExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val curTagName = tagsList.find { it.id == selectedTagId }?.name ?: "انتخاب برچسب فعالیت"
                            OutlinedTextField(
                                value = curTagName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("برچسب فعالیت (اجباری)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tagExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor().testTag("attendance_tag_picker")
                            )
                            ExposedDropdownMenu(expanded = tagExpanded, onDismissRequest = { tagExpanded = false }) {
                                tagsList.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t.name) },
                                        onClick = {
                                            selectedTagId = t.id
                                            tagExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 5. Members List or Headcount
                    if (selectedCrew?.kind == "members") {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("ثبت حضور افراد:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FilterChip(
                                            selected = !viewAllWorkersMode,
                                            onClick = { viewAllWorkersMode = false },
                                            label = { Text("اعضای دسته (${NumberFormatHelper.toPersianDigits(crewMembers.size)})") }
                                        )
                                        FilterChip(
                                            selected = viewAllWorkersMode,
                                            onClick = { viewAllWorkersMode = true },
                                            label = { Text("کل کارگران (${NumberFormatHelper.toPersianDigits(uiState.persons.count { it.active })})") }
                                        )
                                    }
                                }
                            }
                        }

                        val displayedList = if (viewAllWorkersMode) {
                            uiState.persons.filter { it.active }.map { p ->
                                val m = crewMembers.find { it.personId == p.id }
                                Triple(p.id, p.name, m?.id)
                            }
                        } else {
                            crewMembers.map { m ->
                                val p = personsMap[m.personId]
                                Triple(m.personId, p?.name ?: "کارگر", m.id)
                            }
                        }

                        if (displayedList.isEmpty()) {
                            item {
                                Text("هیچ عضوی یافت نشد. می‌توانید از تب «کل کارگران» یا دکمه افزودن شخص استفاده کنید.", color = ColorDangerRed)
                            }
                        } else {
                            items(displayedList) { (personId, personName, memberId) ->
                                val sel = memberSelections[personId] ?: Pair(false, 1)
                                val isChecked = sel.first
                                val ticks = sel.second

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    memberSelections[personId] = Pair(checked, ticks)
                                                },
                                                modifier = Modifier.testTag("attendance_member_check_$personId")
                                            )
                                            Column {
                                                Text(personName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                if (isChecked) {
                                                    Text("حاضر", style = MaterialTheme.typography.labelSmall, color = ColorSuccessGreen, fontWeight = FontWeight.Bold)
                                                } else {
                                                    Text("غایب", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                }
                                                if (memberId != null) {
                                                    TextButton(
                                                        onClick = { viewModel.deactivateCrewMember(memberId) },
                                                        contentPadding = PaddingValues(0.dp)
                                                    ) {
                                                        Text("دیگر نمی‌آید", style = MaterialTheme.typography.labelSmall, color = ColorDangerRed)
                                                    }
                                                }
                                            }
                                        }

                                        // Tick Counter
                                        if (isChecked) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        if (ticks > 1) memberSelections[personId] = Pair(true, ticks - 1)
                                                    },
                                                    modifier = Modifier.size(32.dp).testTag("att_member_minus_$personId")
                                                ) {
                                                    Text("−", fontSize = MaterialTheme.typography.titleMedium.fontSize, fontWeight = FontWeight.Bold)
                                                }
                                                Text(
                                                    text = NumberFormatHelper.toPersianDigits(ticks),
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.padding(horizontal = 6.dp)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        memberSelections[personId] = Pair(true, ticks + 1)
                                                    },
                                                    modifier = Modifier.size(32.dp).testTag("att_member_plus_$personId")
                                                ) {
                                                    Text("+", fontSize = MaterialTheme.typography.titleMedium.fontSize, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            if (!showQuickAddPerson) {
                                TextButton(
                                    onClick = { showQuickAddPerson = true },
                                    modifier = Modifier.testTag("quick_add_person_in_attendance_btn")
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("+ شخص تازه (به دسته هم اضافه می‌شود)")
                                }
                            } else {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("شخص تازه:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                        OutlinedTextField(
                                            value = newPersonName,
                                            onValueChange = { newPersonName = it },
                                            label = { Text("نام و نام خانوادگی") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = newPersonPhone,
                                            onValueChange = { newPersonPhone = it },
                                            label = { Text("شماره تماس") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                            TextButton(onClick = { showQuickAddPerson = false }) { Text("انصراف") }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    selectedCrew?.let { c ->
                                                        viewModel.createPersonAndAddToCrew(c.id, newPersonName, newPersonPhone) { newP ->
                                                            memberSelections[newP.id] = Pair(true, 1)
                                                            showQuickAddPerson = false
                                                            newPersonName = ""
                                                            newPersonPhone = ""
                                                        }
                                                    }
                                                },
                                                enabled = newPersonName.isNotBlank()
                                            ) {
                                                Text("ثبت و حضور")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (selectedCrew?.kind == "count") {
                        // Count crew inputs
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = headcountInput,
                                    onValueChange = { headcountInput = it },
                                    label = { Text("تعداد نفرات (اجباری)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("att_count_headcount_input")
                                )

                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("تیک:", style = MaterialTheme.typography.labelSmall)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { if (countTicks > 1) countTicks-- },
                                            modifier = Modifier.size(36.dp).testTag("att_count_minus_tick")
                                        ) { Text("−", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.titleLarge.fontSize) }
                                        Text(NumberFormatHelper.toPersianDigits(countTicks), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                        IconButton(
                                            onClick = { countTicks++ },
                                            modifier = Modifier.size(36.dp).testTag("att_count_plus_tick")
                                        ) { Text("+", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.titleLarge.fontSize) }
                                    }
                                }
                            }
                        }
                    }

                    // 6. Foreman Row
                    if (hasForeman && foremanPerson != null) {
                        item {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth().testTag("att_foreman_row")
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "سرکارگر: ${foremanPerson.name}",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (foremanPresent) "حاضر" else "غایب", style = MaterialTheme.typography.labelMedium)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Switch(
                                                checked = foremanPresent,
                                                onCheckedChange = { foremanPresent = it },
                                                modifier = Modifier.testTag("att_foreman_present_switch")
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val coef = if (foremanPresent) uiState.foremanCoefPresent else uiState.foremanCoefAbsent
                                        Text(
                                            text = "ضریب سرکارگر: ${NumberFormatHelper.toPersianDigits(coef)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("تیک:", style = MaterialTheme.typography.labelSmall)
                                            IconButton(
                                                onClick = { if (foremanTicks > 1) foremanTicks-- },
                                                modifier = Modifier.size(32.dp).testTag("att_foreman_minus_tick")
                                            ) { Text("−", fontWeight = FontWeight.Bold) }
                                            Text(NumberFormatHelper.toPersianDigits(foremanTicks), fontWeight = FontWeight.Bold)
                                            IconButton(
                                                onClick = { foremanTicks++ },
                                                modifier = Modifier.size(32.dp).testTag("att_foreman_plus_tick")
                                            ) { Text("+", fontWeight = FontWeight.Bold) }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 7. Note
                    item {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("توضیح (اختیاری)") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth().testTag("att_note_input")
                        )
                    }
                }

                // 8. Bottom Live Preview and Action Buttons
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (livePreview != null) {
                            val personsFa = NumberFormatHelper.toPersianDigits(livePreview.totalPersons)
                            val unitsFa = NumberFormatHelper.toPersianDigits(livePreview.totalWageUnits)
                            val amountFa = NumberFormatHelper.formatTomanShort(livePreview.totalAmount)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$personsFa نفر، $unitsFa مزد، جمع $amountFa",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(onClick = onDismiss) { Text("انصراف") }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    if (selectedCrew != null) {
                                        val linesJson = if (selectedCrew.kind == "members") {
                                            val arr = JSONArray()
                                            memberSelections.forEach { (pId, sel) ->
                                                if (sel.first) {
                                                    arr.put(JSONObject().apply {
                                                        put("personId", pId)
                                                        put("ticks", sel.second)
                                                    })
                                                }
                                            }
                                            arr.toString()
                                        } else "[]"

                                        val fJson = if (hasForeman && foremanPerson != null) {
                                            JSONObject().apply {
                                                put("personId", foremanPerson.id)
                                                put("ticks", foremanTicks)
                                                put("present", foremanPresent)
                                            }.toString()
                                        } else null

                                        val hc = if (selectedCrew.kind == "count") headcountInput.toIntOrNull() else null
                                        val ct = if (selectedCrew.kind == "count") countTicks else null

                                        onSave(date, selectedFarmId, selectedPlotId, selectedCrew.id, selectedTagId, linesJson, hc, ct, fJson, note.trim())
                                    }
                                },
                                enabled = selectedCrew != null && selectedTagId.isNotBlank() && (
                                    (selectedCrew.kind == "members" && memberSelections.any { it.value.first }) ||
                                    (selectedCrew.kind == "count" && (headcountInput.toIntOrNull() ?: 0) > 0)
                                ),
                                modifier = Modifier.testTag("submit_attendance_btn")
                            ) {
                                Text("ثبت حضور")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        JalaliDatePickerDialog(
            initialIso = date,
            onDismiss = { showDatePicker = false },
            onDateSelected = { newDate ->
                date = newDate
                showDatePicker = false
            }
        )
    }
}

// -------------------------------------------------------------
// 5. MY COUNT DIALOG (Section 2)
// -------------------------------------------------------------
@Composable
fun MyCountDialog(
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onSave: (date: String, crewId: String, headcount: Int, note: String) -> Unit
) {
    val todayIso = remember { JalaliDateHelper.toIsoDate(LocalDate.now()) }
    var date by remember { mutableStateOf(todayIso) }

    val activeCrews = remember(uiState.crews) { uiState.crews.filter { it.active } }
    var selectedCrewId by remember { mutableStateOf(activeCrews.firstOrNull()?.id ?: "") }
    var headcountInput by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val headcountInt = NumberFormatHelper.parsePersianLong(headcountInput)?.toInt() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت شمارش من", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = JalaliDateHelper.fromIsoDate(date).toDisplayFull(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("تاریخ") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("دسته:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    activeCrews.forEach { c ->
                        FilterChip(
                            selected = c.id == selectedCrewId,
                            onClick = { selectedCrewId = c.id },
                            label = { Text(c.name) }
                        )
                    }
                }

                OutlinedTextField(
                    value = headcountInput,
                    onValueChange = { headcountInput = it },
                    label = { Text("تعداد نفرات شمارش شده (اجباری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("my_count_headcount_input")
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیح (اختیاری)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(date, selectedCrewId, headcountInt, note.trim()) },
                enabled = selectedCrewId.isNotBlank() && headcountInt > 0,
                modifier = Modifier.testTag("submit_my_count_btn")
            ) {
                Text("ثبت")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

// -------------------------------------------------------------
// 6. COMPARISON DIALOG (Attendance vs MyCount)
// -------------------------------------------------------------
@Composable
fun AttendanceComparisonDialog(
    uiState: MainUiState,
    onDismiss: () -> Unit
) {
    // Group attendance and myCounts by (date, crewId)
    val attendances = remember(uiState.attendances) { uiState.attendances.filter { !it.deleted } }
    val myCounts = remember(uiState.myCounts) { uiState.myCounts.filter { !it.deleted } }
    val crewsMap = remember(uiState.crews) { uiState.crews.associateBy { it.id } }

    val allKeys = remember(attendances, myCounts) {
        val keys = mutableSetOf<Pair<String, String>>()
        attendances.forEach { keys.add(Pair(it.date, it.crewId)) }
        myCounts.forEach { keys.add(Pair(it.date, it.crewId)) }
        keys.sortedByDescending { it.first }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مقایسه‌ی حضور و شمارش من", fontWeight = FontWeight.Bold) },
        text = {
            if (allKeys.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("هیچ ثبت حضور یا شمارشی موجود نیست.", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allKeys) { (dateStr, crewId) ->
                        val crewName = crewsMap[crewId]?.name ?: "دسته"
                        val att = attendances.find { it.date == dateStr && it.crewId == crewId }
                        val mc = myCounts.find { it.date == dateStr && it.crewId == crewId }

                        // Att count (foreman not included per spec)
                        val attListCount = if (att == null) 0 else {
                            if (att.headcount != null && att.headcount > 0) {
                                att.headcount
                            } else {
                                try { JSONArray(att.lines).length() } catch (_: Exception) { 0 }
                            }
                        }
                        val myCount = mc?.headcount ?: 0
                        val diff = kotlin.math.abs(attListCount - myCount)
                        val hasDiff = diff != 0

                        val dateFa = JalaliDateHelper.fromIsoDate(dateStr).toDisplayFull()

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (hasDiff) ColorDangerRed.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(crewName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(dateFa, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val listText = "لیست: ${NumberFormatHelper.toPersianDigits(attListCount)} نفر"
                                    val myText = "شمارش من: ${NumberFormatHelper.toPersianDigits(myCount)} نفر"
                                    val diffText = "اختلاف: ${NumberFormatHelper.toPersianDigits(diff)}"

                                    Text("$listText • $myText", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        diffText,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasDiff) ColorDangerRed else ColorSuccessGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        }
    )
}

// -------------------------------------------------------------
// 7. PIECE WORK DIALOG (Section 3 - Nursery Only)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PieceWorkDialog(
    initialPieceWork: PieceWorkEntity? = null,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSave: (date: String, farmId: String?, plotId: String?, tagId: String, jobType: String, size: String, rate: Long, lines: String, note: String) -> Unit
) {
    val todayIso = remember { JalaliDateHelper.toIsoDate(LocalDate.now()) }
    var date by remember { mutableStateOf(initialPieceWork?.date ?: todayIso) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Nursery plots only (Decision 89)
    val nurseryPlots = remember(uiState.plots) {
        uiState.plots.filter { it.active && it.workType == "nahalestan" }
    }
    val nurseryPlotIds = remember(nurseryPlots) { nurseryPlots.map { it.id }.toSet() }
    val farmsWithNursery = remember(uiState.farms, nurseryPlots) {
        val fIds = nurseryPlots.map { it.farmId }.toSet()
        uiState.farms.filter { it.active && fIds.contains(it.id) }
    }

    var selectedFarmId by remember {
        mutableStateOf(initialPieceWork?.farmId ?: farmsWithNursery.firstOrNull()?.id)
    }
    val plotsForSelectedFarm = remember(selectedFarmId, nurseryPlots) {
        nurseryPlots.filter { it.farmId == selectedFarmId }
    }
    var selectedPlotId by remember {
        mutableStateOf(initialPieceWork?.plotId ?: plotsForSelectedFarm.firstOrNull()?.id)
    }

    LaunchedEffect(selectedFarmId) {
        if (selectedPlotId == null || plotsForSelectedFarm.none { it.id == selectedPlotId }) {
            selectedPlotId = plotsForSelectedFarm.firstOrNull()?.id
        }
    }

    val tagsList = remember(uiState.tags) { uiState.tags.filter { !it.hidden } }
    var selectedTagId by remember {
        mutableStateOf(initialPieceWork?.tagId ?: tagsList.firstOrNull()?.id ?: "")
    }

    var jobType by remember { mutableStateOf(initialPieceWork?.jobType ?: "") }
    var sizeInput by remember { mutableStateOf(initialPieceWork?.size ?: "") }
    var rateInput by remember { mutableStateOf(initialPieceWork?.rate?.toString() ?: "") }
    var note by remember { mutableStateOf(initialPieceWork?.note ?: "") }

    // Auto-suggest rate from previous piecework with same jobType and size (Decision 24)
    LaunchedEffect(jobType, sizeInput) {
        if (initialPieceWork == null && jobType.isNotBlank() && sizeInput.isNotBlank()) {
            val matching = uiState.pieceWorks
                .filter { it.jobType.trim() == jobType.trim() && it.size.trim() == sizeInput.trim() && !it.deleted }
                .maxByOrNull { it.date }
            if (matching != null && rateInput.isBlank()) {
                rateInput = matching.rate.toString()
            }
        }
    }

    val rateLong = NumberFormatHelper.parsePersianLong(rateInput) ?: 0L

    // Lines: list of (personId, count)
    val activePersons = remember(uiState.persons) { uiState.persons.filter { it.active } }
    val linesState = remember(initialPieceWork) {
        val list = mutableStateListOf<Pair<String, String>>()
        if (initialPieceWork != null) {
            try {
                val arr = JSONArray(initialPieceWork.lines)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(Pair(o.getString("personId"), o.optInt("count", 0).toString()))
                }
            } catch (_: Exception) {}
        }
        if (list.isEmpty() && activePersons.isNotEmpty()) {
            list.add(Pair(activePersons.first().id, ""))
        }
        list
    }

    val totalAmount = remember(linesState.toList(), rateLong) {
        var total = 0L
        for ((_, cntStr) in linesState) {
            val count = NumberFormatHelper.parsePersianLong(cntStr) ?: 0L
            total += count * rateLong
        }
        NumberFormatHelper.roundToThousand(total)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ثبت کار دانه‌ای (نهالستان)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "بستن") }
                }

                Divider()

                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDatePicker = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("تاریخ کار دانه‌ای:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = JalaliDateHelper.fromIsoDate(date).toDisplayFull(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(Icons.Default.CalendarMonth, contentDescription = "انتخاب تاریخ", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // Farm & Plot
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Farm
                            var farmExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = farmExpanded,
                                onExpandedChange = { farmExpanded = !farmExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                val currentFarmName = farmsWithNursery.find { it.id == selectedFarmId }?.name ?: "مزرعه نهالستان"
                                OutlinedTextField(
                                    value = currentFarmName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("مزرعه نهالستان") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = farmExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor()
                                )
                                ExposedDropdownMenu(expanded = farmExpanded, onDismissRequest = { farmExpanded = false }) {
                                    farmsWithNursery.forEach { f ->
                                        DropdownMenuItem(
                                            text = { Text(f.name) },
                                            onClick = {
                                                selectedFarmId = f.id
                                                farmExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Plot
                            var plotExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = plotExpanded,
                                onExpandedChange = { plotExpanded = !plotExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                val currentPlotName = plotsForSelectedFarm.find { it.id == selectedPlotId }?.let {
                                    if (it.isDefault) "کل قطعه" else it.name
                                } ?: "قطعه نهالستان"
                                OutlinedTextField(
                                    value = currentPlotName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("قطعه نهالستان") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = plotExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor()
                                )
                                ExposedDropdownMenu(expanded = plotExpanded, onDismissRequest = { plotExpanded = false }) {
                                    plotsForSelectedFarm.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text(if (p.isDefault) "کل قطعه" else p.name) },
                                            onClick = {
                                                selectedPlotId = p.id
                                                plotExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Tag
                    item {
                        var tagExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = tagExpanded,
                            onExpandedChange = { tagExpanded = !tagExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val tagName = tagsList.find { it.id == selectedTagId }?.name ?: "انتخاب برچسب"
                            OutlinedTextField(
                                value = tagName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("برچسب فعالیت (اجباری)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tagExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = tagExpanded, onDismissRequest = { tagExpanded = false }) {
                                tagsList.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t.name) },
                                        onClick = {
                                            selectedTagId = t.id
                                            tagExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Job Type & Size & Rate
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = jobType,
                                onValueChange = { jobType = it },
                                label = { Text("نوع کار (مثلاً گلدان‌کاری)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("pw_job_type_input")
                            )
                            OutlinedTextField(
                                value = sizeInput,
                                onValueChange = { sizeInput = it },
                                label = { Text("سایز (مثلاً سایز ۳)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("pw_size_input")
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = rateInput,
                            onValueChange = { rateInput = it },
                            label = { Text("نرخ هر دانه (تومان)") },
                            supportingText = {
                                if (rateLong > 0) {
                                    Text(NumberFormatHelper.formatTomanFull(rateLong), color = MaterialTheme.colorScheme.primary)
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("pw_rate_input")
                        )
                    }

                    // Persons & Counts
                    item {
                        Text("افراد و تعداد دانه‌ها:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }

                    items(linesState.size) { index ->
                        val (pId, countStr) = linesState[index]
                        val personName = activePersons.find { it.id == pId }?.name ?: "شخص"

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            var pExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = pExpanded,
                                onExpandedChange = { pExpanded = !pExpanded },
                                modifier = Modifier.weight(1.2f)
                            ) {
                                OutlinedTextField(
                                    value = personName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("شخص") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor()
                                )
                                ExposedDropdownMenu(expanded = pExpanded, onDismissRequest = { pExpanded = false }) {
                                    activePersons.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text(p.name) },
                                            onClick = {
                                                linesState[index] = Pair(p.id, countStr)
                                                pExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = countStr,
                                onValueChange = { linesState[index] = Pair(pId, it) },
                                label = { Text("تعداد دانه") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("pw_count_input_$index")
                            )

                            if (linesState.size > 1) {
                                IconButton(onClick = { linesState.removeAt(index) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = ColorDangerRed)
                                }
                            }
                        }
                    }

                    item {
                        TextButton(
                            onClick = {
                                val unusedPerson = activePersons.find { p -> linesState.none { it.first == p.id } } ?: activePersons.firstOrNull()
                                if (unusedPerson != null) {
                                    linesState.add(Pair(unusedPerson.id, ""))
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ افزودن شخص دیگر")
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("توضیح (اختیاری)") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Footer
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "جمع کل مبلغ: ${NumberFormatHelper.formatTomanFull(totalAmount)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(onClick = onDismiss) { Text("انصراف") }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    val arr = JSONArray()
                                    linesState.forEach { (pId, cntStr) ->
                                        val cnt = NumberFormatHelper.parsePersianLong(cntStr) ?: 0L
                                        if (cnt > 0) {
                                            arr.put(JSONObject().apply {
                                                put("personId", pId)
                                                put("count", cnt)
                                            })
                                        }
                                    }
                                    onSave(date, selectedFarmId, selectedPlotId, selectedTagId, jobType.trim(), sizeInput.trim(), rateLong, arr.toString(), note.trim())
                                },
                                enabled = jobType.isNotBlank() && rateLong > 0 && selectedTagId.isNotBlank() && linesState.any { (NumberFormatHelper.parsePersianLong(it.second) ?: 0L) > 0 },
                                modifier = Modifier.testTag("submit_piece_work_btn")
                            ) {
                                Text("ثبت کار دانه‌ای")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        JalaliDatePickerDialog(
            initialIso = date,
            onDismiss = { showDatePicker = false },
            onDateSelected = { newDate ->
                date = newDate
                showDatePicker = false
            }
        )
    }
}

// -------------------------------------------------------------
// 8. PAYMENT DIALOG (Section 4 - Single or Crew Shares)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentDialog(
    initialPayment: PaymentEntity? = null,
    prefilledTargetType: String? = null,
    prefilledTargetId: String? = null,
    prefilledAmount: Long? = null,
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onSave: (date: String, amount: Long, handedToPersonId: String?, targetType: String, targetId: String, method: String?, note: String, shares: String) -> Unit
) {
    val todayIso = remember { JalaliDateHelper.toIsoDate(LocalDate.now()) }
    var date by remember { mutableStateOf(initialPayment?.date ?: todayIso) }
    var showDatePicker by remember { mutableStateOf(false) }

    var amountInput by remember {
        mutableStateOf(initialPayment?.amount?.toString() ?: prefilledAmount?.toString() ?: "")
    }
    val amountLong = NumberFormatHelper.parsePersianLong(amountInput) ?: 0L

    var targetType by remember { mutableStateOf(initialPayment?.targetType ?: prefilledTargetType ?: "person") } // "person" | "crew"

    val activePersons = remember(uiState.persons) { uiState.persons.filter { it.active } }
    val activeCrews = remember(uiState.crews) { uiState.crews.filter { it.active } }

    var targetId by remember {
        mutableStateOf(
            initialPayment?.targetId ?: prefilledTargetId ?: if (targetType == "person") activePersons.firstOrNull()?.id ?: "" else activeCrews.firstOrNull()?.id ?: ""
        )
    }

    val selectedCrew = remember(targetId, targetType, uiState.crews) {
        if (targetType == "crew") uiState.crews.find { it.id == targetId } else null
    }

    var handedToPersonId by remember {
        mutableStateOf(
            initialPayment?.handedToPersonId ?: if (targetType == "person") targetId else selectedCrew?.foremanId
        )
    }

    var method by remember { mutableStateOf(initialPayment?.method ?: "cash") } // "cash" | "card" | null
    var note by remember { mutableStateOf(initialPayment?.note ?: "") }

    // Map of positive balances for all persons
    val personsMap = remember(uiState.persons) { uiState.persons.associateBy { it.id } }
    val crewsMap = remember(uiState.crews) { uiState.crews.associateBy { it.id } }
    val tagsMap = remember(uiState.tags) { uiState.tags.associateBy { it.id } }

    val personBalances = remember(
        uiState.attendances, uiState.pieceWorks, uiState.payments, uiState.ledgerEntries, uiState.wageRates
    ) {
        activePersons.associate { p ->
            p.id to WorkforceCalculator.calculateAccountBalance(
                "person", p.id, uiState.attendances, uiState.pieceWorks, uiState.payments,
                uiState.ledgerEntries, uiState.wageRates, crewsMap, tagsMap, personsMap
            )
        }
    }

    // Shares state for crew payment
    val sharesState = remember(targetType, selectedCrew, amountLong) {
        val list = mutableStateListOf<Pair<PaymentShare, String>>()
        if (initialPayment != null) {
            try {
                val arr = JSONArray(initialPayment.shares)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val aType = o.optString("accountType", "person")
                    val aId = o.getString("accountId")
                    val aAmt = o.getLong("amount")
                    val name = if (aType == "person") personsMap[aId]?.name ?: "شخص" else crewsMap[aId]?.name ?: "دسته"
                    list.add(Pair(PaymentShare(aType, aId, name, aAmt), aAmt.toString()))
                }
            } catch (_: Exception) {}
        } else if (targetType == "crew" && selectedCrew != null && selectedCrew.kind == "members") {
            val members = uiState.crewMembers.filter { it.crewId == selectedCrew.id && it.active && !it.deleted }
                .mapNotNull { personsMap[it.personId] }
            val foreman = selectedCrew.foremanId?.let { personsMap[it] }

            val calculated = WorkforceCalculator.calculateCrewPaymentShares(
                amountLong, members, foreman, personBalances
            )
            calculated.forEach { s ->
                list.add(Pair(s, s.amount.toString()))
            }
        }
        list
    }

    val currentSharesSum = remember(sharesState.toList()) {
        sharesState.sumOf { NumberFormatHelper.parsePersianLong(it.second) ?: 0L }
    }
    val sharesDifference = amountLong - currentSharesSum
    val isSharesValid = if (targetType == "crew" && selectedCrew?.kind == "members") {
        amountLong > 0 && sharesDifference == 0L
    } else {
        amountLong > 0
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (initialPayment == null) "ثبت پرداخت" else "ویرایش پرداخت", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "بستن") }
                }

                Divider()

                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = amountInput,
                            onValueChange = { amountInput = it },
                            label = { Text("مبلغ پرداخت (تومان)") },
                            supportingText = {
                                if (amountLong > 0) {
                                    Text(NumberFormatHelper.formatTomanFull(amountLong), color = MaterialTheme.colorScheme.primary)
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("payment_amount_input")
                        )
                    }

                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDatePicker = true }
                                .testTag("payment_date_picker")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("تاریخ پرداخت:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = JalaliDateHelper.fromIsoDate(date).toDisplayFull(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(Icons.Default.CalendarMonth, contentDescription = "انتخاب تاریخ", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // Target Type: Person or Crew
                    item {
                        Text("به حساب:", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilterChip(
                                selected = targetType == "person",
                                onClick = {
                                    targetType = "person"
                                    targetId = activePersons.firstOrNull()?.id ?: ""
                                    handedToPersonId = targetId
                                },
                                label = { Text("شخص") },
                                modifier = Modifier.weight(1f).testTag("payment_target_person_chip")
                            )
                            FilterChip(
                                selected = targetType == "crew",
                                onClick = {
                                    targetType = "crew"
                                    targetId = activeCrews.firstOrNull()?.id ?: ""
                                    handedToPersonId = selectedCrew?.foremanId
                                },
                                label = { Text("دسته") },
                                modifier = Modifier.weight(1f).testTag("payment_target_crew_chip")
                            )
                        }
                    }

                    // Target Selector
                    item {
                        var targetExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = targetExpanded,
                            onExpandedChange = { targetExpanded = !targetExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val targetLabel = if (targetType == "person") {
                                activePersons.find { it.id == targetId }?.name ?: "انتخاب شخص"
                            } else {
                                activeCrews.find { it.id == targetId }?.name ?: "انتخاب دسته"
                            }
                            OutlinedTextField(
                                value = targetLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(if (targetType == "person") "نام شخص" else "نام دسته") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor().testTag("payment_target_picker")
                            )
                            ExposedDropdownMenu(expanded = targetExpanded, onDismissRequest = { targetExpanded = false }) {
                                if (targetType == "person") {
                                    activePersons.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text(p.name) },
                                            onClick = {
                                                targetId = p.id
                                                handedToPersonId = p.id
                                                targetExpanded = false
                                            }
                                        )
                                    }
                                } else {
                                    activeCrews.forEach { c ->
                                        DropdownMenuItem(
                                            text = { Text("${c.name} (${if (c.kind == "members") "با اعضا" else "شمارشی"})") },
                                            onClick = {
                                                targetId = c.id
                                                handedToPersonId = c.foremanId
                                                targetExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Handed to person
                    item {
                        var handedExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = handedExpanded,
                            onExpandedChange = { handedExpanded = !handedExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val handedName = activePersons.find { it.id == handedToPersonId }?.name ?: "انتخاب تحویل‌گیرنده"
                            OutlinedTextField(
                                value = handedName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("به دست چه کسی رسید؟") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = handedExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor().testTag("payment_handed_to_picker")
                            )
                            ExposedDropdownMenu(expanded = handedExpanded, onDismissRequest = { handedExpanded = false }) {
                                activePersons.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.name) },
                                        onClick = {
                                            handedToPersonId = p.id
                                            handedExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Method
                    item {
                        Text("روش پرداخت:", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilterChip(
                                selected = method == "cash",
                                onClick = { method = "cash" },
                                label = { Text("نقد") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = method == "card",
                                onClick = { method = "card" },
                                label = { Text("کارت‌به‌کارت") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Shares Breakdown for Crew with Members
                    if (targetType == "crew" && selectedCrew?.kind == "members") {
                        item {
                            Divider()
                            Text("تقسیم سهم اعضای دسته:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "سهم‌ها بر اساس طلب فعلی پیشنهاد شده‌اند و دستی قابل تغییر هستند.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        items(sharesState.size) { index ->
                            val (share, amtStr) = sharesState[index]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(share.accountName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = amtStr,
                                    onValueChange = { newStr ->
                                        sharesState[index] = Pair(share, newStr)
                                    },
                                    singleLine = true,
                                    modifier = Modifier.width(160.dp).testTag("payment_share_input_$index")
                                )
                            }
                        }

                        item {
                            if (sharesDifference != 0L) {
                                val diffFa = NumberFormatHelper.formatTomanFull(kotlin.math.abs(sharesDifference))
                                val label = if (sharesDifference > 0) "کسری: $diffFa باقی مانده" else "اضافی: $diffFa بیش از مبلغ کل"
                                Text(
                                    text = "جمع سهم‌ها باید برابر مبلغ باشد ($label)",
                                    color = ColorDangerRed,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("payment_shares_diff_warning")
                                )
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("توضیح (اختیاری)") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Footer
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = onDismiss) { Text("انصراف") }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                val finalShares = if (targetType == "crew" && selectedCrew?.kind == "members") {
                                    val arr = JSONArray()
                                    sharesState.forEach { (s, amtStr) ->
                                        val amt = NumberFormatHelper.parsePersianLong(amtStr) ?: 0L
                                        arr.put(JSONObject().apply {
                                            put("accountType", s.accountType)
                                            put("accountId", s.accountId)
                                            put("amount", amt)
                                        })
                                    }
                                    arr.toString()
                                } else {
                                    val arr = JSONArray().apply {
                                        put(JSONObject().apply {
                                            put("accountType", targetType)
                                            put("accountId", targetId)
                                            put("amount", amountLong)
                                        })
                                    }
                                    arr.toString()
                                }

                                onSave(date, amountLong, handedToPersonId, targetType, targetId, method, note.trim(), finalShares)
                            },
                            enabled = isSharesValid,
                            modifier = Modifier.testTag("submit_payment_btn")
                        ) {
                            Text("ثبت پرداخت")
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        JalaliDatePickerDialog(
            initialIso = date,
            onDismiss = { showDatePicker = false },
            onDateSelected = { newDate ->
                date = newDate
                showDatePicker = false
            }
        )
    }
}

// -------------------------------------------------------------
// 9. MANUAL LEDGER ENTRY DIALOG (Section 4)
// -------------------------------------------------------------
@Composable
fun LedgerEntryDialog(
    accountType: String,
    accountId: String,
    accountName: String,
    initialKind: String = "credit",
    onDismiss: () -> Unit,
    onSave: (date: String, kind: String, amount: Long, note: String) -> Unit
) {
    val todayIso = remember { JalaliDateHelper.toIsoDate(LocalDate.now()) }
    var date by remember { mutableStateOf(todayIso) }
    var showDatePicker by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf(initialKind) }
    var amountInput by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val amountLong = NumberFormatHelper.parsePersianLong(amountInput) ?: 0L

    val title = when (kind) {
        "payTo" -> "پرداخت به او"
        "receiveFrom" -> "دریافت از او"
        "credit" -> "طلب او"
        "debit" -> "بدهی او"
        "openingCredit" -> "مانده‌ی قبلی: طلب او"
        "openingDebit" -> "مانده‌ی قبلی: بدهی او"
        else -> "ثبت ردیف در حساب"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$title «$accountName»", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("مبلغ (تومان)") },
                    supportingText = {
                        if (amountLong > 0) {
                            Text(NumberFormatHelper.formatTomanFull(amountLong), color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("manual_ledger_amount_input")
                )

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("تاریخ ردیف دفتر:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = JalaliDateHelper.fromIsoDate(date).toDisplayFull(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(Icons.Default.CalendarMonth, contentDescription = "انتخاب تاریخ", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیح (اختیاری)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("manual_ledger_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(date, kind, amountLong, note.trim()) },
                enabled = amountLong > 0,
                modifier = Modifier.testTag("submit_manual_ledger_btn")
            ) {
                Text("ثبت در دفتر")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("انصراف") }
        }
    )

    if (showDatePicker) {
        JalaliDatePickerDialog(
            initialIso = date,
            onDismiss = { showDatePicker = false },
            onDateSelected = { newDate ->
                date = newDate
                showDatePicker = false
            }
        )
    }
}

// -------------------------------------------------------------
// 10. VOID ENTRY DIALOG (Section 4)
// -------------------------------------------------------------
@Composable
fun VoidEntryDialog(
    onDismiss: () -> Unit,
    onConfirm: (reason: String) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("باطل کردن ردیف دستی", fontWeight = FontWeight.Bold, color = ColorDangerRed) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text("ردیف به شکل خط‌خورده در دفتر باقی می‌ماند و در مانده حساب نمی‌شود.")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("دلیل باطل کردن (اجباری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("void_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.trim()) },
                enabled = reason.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ColorDangerRed),
                modifier = Modifier.testTag("confirm_void_entry_btn")
            ) {
                Text("باطل کن")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

// -------------------------------------------------------------
// 11. WAITING LIST DIALOG (Section 4)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaitingListDialog(
    prefilledAccountType: String? = null,
    prefilledAccountId: String? = null,
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onSave: (accountType: String, accountId: String, amount: Long?, date: String, note: String) -> Unit
) {
    val todayIso = remember { JalaliDateHelper.toIsoDate(LocalDate.now()) }
    var date by remember { mutableStateOf(todayIso) }
    var showDatePicker by remember { mutableStateOf(false) }

    var accountType by remember { mutableStateOf(prefilledAccountType ?: "person") }
    val activePersons = remember(uiState.persons) { uiState.persons.filter { it.active } }
    val countCrews = remember(uiState.crews) { uiState.crews.filter { it.active && it.kind == "count" } }

    var accountId by remember {
        mutableStateOf(prefilledAccountId ?: activePersons.firstOrNull()?.id ?: "")
    }

    val personsMap = remember(uiState.persons) { uiState.persons.associateBy { it.id } }
    val crewsMap = remember(uiState.crews) { uiState.crews.associateBy { it.id } }
    val tagsMap = remember(uiState.tags) { uiState.tags.associateBy { it.id } }

    val currentClaim = remember(accountType, accountId, uiState.attendances, uiState.payments, uiState.pieceWorks, uiState.ledgerEntries) {
        val balance = WorkforceCalculator.calculateAccountBalance(
            accountType, accountId, uiState.attendances, uiState.pieceWorks, uiState.payments,
            uiState.ledgerEntries, uiState.wageRates, crewsMap, tagsMap, personsMap
        )
        if (balance > 0) balance else null
    }

    var amountInput by remember { mutableStateOf(currentClaim?.toString() ?: "") }
    var note by remember { mutableStateOf("") }
    val amountLong = NumberFormatHelper.parsePersianLong(amountInput)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن به لیست انتظار («فلانی پول می‌خواهد»)", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                var isExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = isExpanded,
                    onExpandedChange = { isExpanded = !isExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val accName = if (accountType == "person") personsMap[accountId]?.name ?: "انتخاب شخص" else crewsMap[accountId]?.name ?: "انتخاب دسته"
                    OutlinedTextField(
                        value = accName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("شخص یا دسته") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor().testTag("waiting_list_person_picker")
                    )
                    ExposedDropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
                        activePersons.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                onClick = {
                                    accountType = "person"
                                    accountId = p.id
                                    isExpanded = false
                                }
                            )
                        }
                        countCrews.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.name} (دسته شمارشی)") },
                                onClick = {
                                    accountType = "crew"
                                    accountId = c.id
                                    isExpanded = false
                                }
                            )
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("تاریخ درخواست:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = JalaliDateHelper.fromIsoDate(date).toDisplayFull(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(Icons.Default.CalendarMonth, contentDescription = "انتخاب تاریخ", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("مبلغ درخواستی (اختیاری - پیش‌فرض طلب فعلی)") },
                    supportingText = {
                        if (amountLong != null && amountLong > 0) {
                            Text(NumberFormatHelper.formatTomanFull(amountLong), color = MaterialTheme.colorScheme.primary)
                        } else if (currentClaim != null) {
                            Text("طلب فعلی: ${NumberFormatHelper.formatTomanShort(currentClaim)}", color = ColorSuccessGreen)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("waiting_list_amount_input")
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیح (اختیاری)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(accountType, accountId, amountLong, date, note.trim()) },
                enabled = accountId.isNotBlank(),
                modifier = Modifier.testTag("submit_waiting_list_btn")
            ) {
                Text("ثبت در لیست انتظار")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("انصراف") }
        }
    )

    if (showDatePicker) {
        JalaliDatePickerDialog(
            initialIso = date,
            onDismiss = { showDatePicker = false },
            onDateSelected = { newDate ->
                date = newDate
                showDatePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbsenceDialog(
    initialAbsence: com.example.data.entity.AbsenceEntity? = null,
    personId: String,
    onDismiss: () -> Unit,
    onSave: (com.example.data.entity.AbsenceEntity) -> Unit
) {
    val todayIso = remember { JalaliDateHelper.toIsoDate(LocalDate.now()) }
    var fromDate by remember { mutableStateOf(initialAbsence?.fromDate ?: todayIso) }
    var toDate by remember { mutableStateOf(initialAbsence?.toDate ?: todayIso) }
    var type by remember { mutableStateOf(initialAbsence?.type ?: "unexcused") }
    var note by remember { mutableStateOf(initialAbsence?.note ?: "") }
    var pickingForField by remember { mutableStateOf<String?>(null) } // "from" or "to"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت غیبت کارگر", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                // From Date
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().clickable { pickingForField = "from" }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("از تاریخ:", style = MaterialTheme.typography.labelSmall)
                            Text(JalaliDateHelper.fromIsoDate(fromDate).toDisplayFull(), fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // To Date
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().clickable { pickingForField = "to" }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("تا تاریخ:", style = MaterialTheme.typography.labelSmall)
                            Text(JalaliDateHelper.fromIsoDate(toDate).toDisplayFull(), fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Type
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = type == "unexcused",
                        onClick = { type = "unexcused" },
                        label = { Text("غیرموجه") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = type == "leave",
                        onClick = { type = "leave" },
                        label = { Text("مرخصی") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیح (اختیاری)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    com.example.data.entity.AbsenceEntity(
                        id = initialAbsence?.id ?: java.util.UUID.randomUUID().toString(),
                        personId = personId,
                        fromDate = fromDate,
                        toDate = toDate,
                        type = type,
                        note = note.trim()
                    )
                )
            }) { Text("ذخیره غیبت") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("انصراف") } }
    )

    if (pickingForField != null) {
        val curIso = if (pickingForField == "from") fromDate else toDate
        JalaliDatePickerDialog(
            initialIso = curIso,
            onDismiss = { pickingForField = null },
            onDateSelected = { newDate ->
                if (pickingForField == "from") {
                    fromDate = newDate
                    if (toDate < newDate) toDate = newDate
                } else {
                    toDate = newDate
                    if (fromDate > newDate) fromDate = newDate
                }
                pickingForField = null
            }
        )
    }
}
