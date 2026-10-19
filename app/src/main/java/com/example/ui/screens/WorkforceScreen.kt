package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.data.entity.AttendanceEntity
import com.example.data.entity.CrewEntity
import com.example.data.entity.PieceWorkEntity
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import com.example.lib.WorkforceCalculator
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.*
import com.example.ui.theme.ColorDangerRed
import com.example.ui.theme.ColorNeutralGray
import com.example.ui.theme.ColorSuccessGreen
import com.example.ui.theme.ColorWarningOrange
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkforceScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCrewDetail: (String) -> Unit,
    onNavigateToReports: () -> Unit = {}
) {
    BackHandler { onNavigateBack() }
    val context = LocalContext.current

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("حضور و غیاب", "کار پیمانی", "شمارش من", "دسته‌ها")

    val personsMap = remember(uiState.persons) { uiState.persons.associateBy { it.id } }
    val crewsMap = remember(uiState.crews) { uiState.crews.associateBy { it.id } }
    val farmsMap = remember(uiState.farms) { uiState.farms.associateBy { it.id } }
    val plotsMap = remember(uiState.plots) { uiState.plots.associateBy { it.id } }
    val tagsMap = remember(uiState.tags) { uiState.tags.associateBy { it.id } }
    val todayIso = remember { LocalDate.now().toString() }

    // Dialog states
    var showCreateCrewDialog by remember { mutableStateOf(false) }
    var crewToEdit by remember { mutableStateOf<CrewEntity?>(null) }
    var crewToDelete by remember { mutableStateOf<CrewEntity?>(null) }

    var showAttendanceDialog by remember { mutableStateOf(false) }
    var attendanceToEdit by remember { mutableStateOf<AttendanceEntity?>(null) }
    var attendanceDateIso by remember { mutableStateOf(todayIso) }
    var showAttendanceDatePicker by remember { mutableStateOf(false) }
    var workerSearchQuery by remember { mutableStateOf("") }
    var workerStatusFilter by remember { mutableIntStateOf(0) }
    var selectedCrewFilterId by remember { mutableStateOf<String?>(null) }

    var showPieceWorkDialog by remember { mutableStateOf(false) }
    var pieceWorkToEdit by remember { mutableStateOf<PieceWorkEntity?>(null) }

    var showMyCountDialog by remember { mutableStateOf(false) }
    var showComparisonDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("حضور و غیاب و نیروی کار", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("workforce_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToReports, modifier = Modifier.testTag("workforce_reports_btn")) {
                        Icon(Icons.Default.Assessment, contentDescription = "گزارش‌ها", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        floatingActionButton = {
            when (selectedTabIndex) {
                0 -> {
                    ExtendedFloatingActionButton(
                        onClick = {
                            attendanceToEdit = null
                            showAttendanceDialog = true
                        },
                        icon = { Icon(Icons.Default.FactCheck, contentDescription = null) },
                        text = { Text("ثبت حضور تفصیلی") },
                        modifier = Modifier.testTag("workforce_add_attendance_fab")
                    )
                }
                1 -> {
                    ExtendedFloatingActionButton(
                        onClick = {
                            pieceWorkToEdit = null
                            showPieceWorkDialog = true
                        },
                        icon = { Icon(Icons.Default.Handshake, contentDescription = null) },
                        text = { Text("ثبت کار پیمانی") },
                        modifier = Modifier.testTag("workforce_add_piecework_fab")
                    )
                }
                2 -> {
                    ExtendedFloatingActionButton(
                        onClick = { showMyCountDialog = true },
                        icon = { Icon(Icons.Default.Pin, contentDescription = null) },
                        text = { Text("شمارش جدید") },
                        modifier = Modifier.testTag("workforce_add_mycount_fab")
                    )
                }
                3 -> {
                    ExtendedFloatingActionButton(
                        onClick = { showCreateCrewDialog = true },
                        icon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
                        text = { Text("دسته تازه") },
                        modifier = Modifier.testTag("workforce_add_crew_fab")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Row
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth().testTag("workforce_tabs")
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("workforce_tab_$index")
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> {
                    // --- TAB 0: ATTENDANCE TAB (With Integrated Crew/Foreman Selection & Bulk Presence) ---
                    val presentPersonIdsForDate = remember(attendanceDateIso, uiState.attendances) {
                        val set = mutableSetOf<String>()
                        val dateAtts = uiState.attendances.filter { it.date == attendanceDateIso && !it.deleted }
                        for (att in dateAtts) {
                            try {
                                val arr = org.json.JSONArray(att.lines)
                                for (i in 0 until arr.length()) {
                                    val pId = arr.getJSONObject(i).optString("personId", "")
                                    val ticks = arr.getJSONObject(i).optInt("ticks", 1)
                                    if (pId.isNotBlank() && ticks > 0) {
                                        set.add(pId)
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                        set
                    }

                    val allActiveWorkers = remember(uiState.persons) {
                        uiState.persons.filter { it.active && !it.deleted }
                    }

                    val crewFilteredWorkers = remember(allActiveWorkers, selectedCrewFilterId, uiState.crewMembers, uiState.crews) {
                        if (selectedCrewFilterId == null) {
                            allActiveWorkers
                        } else {
                            val crew = uiState.crews.find { it.id == selectedCrewFilterId }
                            val memberPersonIds = uiState.crewMembers
                                .filter { it.crewId == selectedCrewFilterId && it.active && !it.deleted }
                                .map { it.personId }
                                .toMutableSet()
                            crew?.foremanId?.let { if (it.isNotBlank()) memberPersonIds.add(it) }
                            allActiveWorkers.filter { memberPersonIds.contains(it.id) }
                        }
                    }

                    val filteredWorkers = remember(crewFilteredWorkers, workerSearchQuery, workerStatusFilter, presentPersonIdsForDate) {
                        crewFilteredWorkers.filter { p ->
                            val matchesSearch = workerSearchQuery.isBlank() || p.name.contains(workerSearchQuery, ignoreCase = true)
                            val isPresent = presentPersonIdsForDate.contains(p.id)
                            val matchesStatus = when (workerStatusFilter) {
                                1 -> isPresent
                                2 -> !isPresent
                                else -> true
                            }
                            matchesSearch && matchesStatus
                        }
                    }

                    val presentCount = crewFilteredWorkers.count { presentPersonIdsForDate.contains(it.id) }
                    val absentCount = crewFilteredWorkers.size - presentCount

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
                    ) {
                        // 1. Date Header & Summary Card
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column {
                                            Text(
                                                text = "تاریخ حضور و غیاب",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Text(
                                                text = JalaliDateHelper.fromIsoDate(attendanceDateIso).toDisplayFull(),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { showAttendanceDatePicker = true },
                                            modifier = Modifier.testTag("change_attendance_date_btn")
                                        ) {
                                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("تغییر تاریخ")
                                        }
                                    }

                                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "آمار این روز:",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text("حاضر: ${NumberFormatHelper.toPersianDigits(presentCount)} نفر", color = ColorSuccessGreen, fontWeight = FontWeight.Bold) }
                                            )
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text("غایب: ${NumberFormatHelper.toPersianDigits(absentCount)} نفر", color = MaterialTheme.colorScheme.outline) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Crew / Foreman Selection Card (بخش انتخاب دسته و سرکارگر)
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            Text(
                                                text = "انتخاب دسته / سرکارگر:",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        TextButton(onClick = { selectedTabIndex = 3 }) {
                                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("مدیریت دسته‌ها", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }

                                    // FilterChips for Crews
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        item {
                                            FilterChip(
                                                selected = selectedCrewFilterId == null,
                                                onClick = { selectedCrewFilterId = null },
                                                label = { Text("همه کارگران (${NumberFormatHelper.toPersianDigits(allActiveWorkers.size)})") },
                                                modifier = Modifier.testTag("filter_all_crews_chip")
                                            )
                                        }
                                        items(uiState.crews.filter { !it.deleted }) { crew ->
                                            val foreman = crew.foremanId?.let { personsMap[it] }
                                            val labelText = if (foreman != null) "${crew.name} (${foreman.name})" else crew.name
                                            FilterChip(
                                                selected = selectedCrewFilterId == crew.id,
                                                onClick = {
                                                    selectedCrewFilterId = if (selectedCrewFilterId == crew.id) null else crew.id
                                                },
                                                label = { Text(labelText) },
                                                modifier = Modifier.testTag("filter_crew_${crew.id}_chip")
                                            )
                                        }
                                    }

                                    // Bulk action for selected crew
                                    if (selectedCrewFilterId != null) {
                                        val selectedCrew = uiState.crews.find { it.id == selectedCrewFilterId }
                                        if (selectedCrew != null) {
                                            val foreman = selectedCrew.foremanId?.let { personsMap[it] }
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(
                                                        text = "دسته انتخاب‌شده: ${selectedCrew.name}${if (foreman != null) " • سرکارگر: ${foreman.name}" else ""}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                        Button(
                                                            onClick = {
                                                                viewModel.toggleCrewAttendance(attendanceDateIso, selectedCrew.id, true) {
                                                                    Toast.makeText(context, "حضور تمام اعضای دسته ثبت شد", Toast.LENGTH_SHORT).show()
                                                                }
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessGreen),
                                                            modifier = Modifier.weight(1f).testTag("mark_crew_all_present_btn")
                                                        ) {
                                                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("حضور همه اعضا", style = MaterialTheme.typography.labelMedium)
                                                        }

                                                        OutlinedButton(
                                                            onClick = {
                                                                viewModel.toggleCrewAttendance(attendanceDateIso, selectedCrew.id, false) {
                                                                    Toast.makeText(context, "غیبت تمام اعضای دسته ثبت شد", Toast.LENGTH_SHORT).show()
                                                                }
                                                            },
                                                            modifier = Modifier.weight(1f).testTag("mark_crew_all_absent_btn")
                                                        ) {
                                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp), tint = ColorDangerRed)
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("غیبت همه اعضا", style = MaterialTheme.typography.labelMedium, color = ColorDangerRed)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Search & Status Filter Bar
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = workerSearchQuery,
                                    onValueChange = { workerSearchQuery = it },
                                    placeholder = { Text("جستجوی نام کارگر...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = if (workerSearchQuery.isNotEmpty()) {
                                        { IconButton(onClick = { workerSearchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = "پاک کردن") } }
                                    } else null,
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("worker_search_input")
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    FilterChip(
                                        selected = workerStatusFilter == 0,
                                        onClick = { workerStatusFilter = 0 },
                                        label = { Text("همه (${NumberFormatHelper.toPersianDigits(crewFilteredWorkers.size)})") },
                                        modifier = Modifier.testTag("filter_all_workers_chip")
                                    )
                                    FilterChip(
                                        selected = workerStatusFilter == 1,
                                        onClick = { workerStatusFilter = 1 },
                                        label = { Text("حاضرین (${NumberFormatHelper.toPersianDigits(presentCount)})") },
                                        modifier = Modifier.testTag("filter_present_workers_chip")
                                    )
                                    FilterChip(
                                        selected = workerStatusFilter == 2,
                                        onClick = { workerStatusFilter = 2 },
                                        label = { Text("غایبین (${NumberFormatHelper.toPersianDigits(absentCount)})") },
                                        modifier = Modifier.testTag("filter_absent_workers_chip")
                                    )
                                }
                            }
                        }

                        // 4. Section Title
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Text(
                                    text = if (selectedCrewFilterId != null) "کارگران دسته‌ی انتخاب‌شده" else "لیست کارگران (ثبت تیک حضور/غیاب)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${NumberFormatHelper.toPersianDigits(filteredWorkers.size)} کارگر",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        // 5. Worker List Items with Toggle Checkbox/Switch
                        if (filteredWorkers.isEmpty()) {
                            item {
                                Text(
                                    text = "هیچ کارگری با این مشخصات یا فیلتر یافت نشد.",
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            }
                        } else {
                            items(filteredWorkers, key = { it.id }) { person ->
                                val isPresent = presentPersonIdsForDate.contains(person.id)
                                val personCrews = uiState.crewMembers.filter { it.personId == person.id && it.active && !it.deleted }
                                val crewNames = personCrews.mapNotNull { cm -> crewsMap[cm.crewId]?.name }.joinToString(" • ")

                                val personSalRate = uiState.salaryRates.find { it.personId == person.id && !it.deleted }?.monthlySalary ?: 0L
                                val salarySubtitle = if (personSalRate > 0L) {
                                    "حقوق ماهانه: ${NumberFormatHelper.formatTomanShort(personSalRate)}"
                                } else {
                                    val dailyWage = WorkforceCalculator.getEffectiveDailyWage(
                                        wageRates = uiState.wageRates,
                                        crewId = personCrews.firstOrNull()?.crewId ?: "",
                                        personId = person.id,
                                        date = attendanceDateIso,
                                        salaryRates = uiState.salaryRates
                                    )
                                    if (dailyWage > 0L) "روزمزد: ${NumberFormatHelper.formatTomanShort(dailyWage)}" else null
                                }

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isPresent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (isPresent) 2.dp else 1.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.toggleWorkerAttendance(attendanceDateIso, person.id, !isPresent)
                                        }
                                        .testTag("worker_attendance_item_${person.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Checkbox(
                                                checked = isPresent,
                                                onCheckedChange = { checked ->
                                                    viewModel.toggleWorkerAttendance(attendanceDateIso, person.id, checked)
                                                },
                                                modifier = Modifier.testTag("worker_check_${person.id}")
                                            )
                                            Column {
                                                Text(person.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                                val details = listOfNotNull(
                                                    crewNames.ifBlank { null },
                                                    salarySubtitle
                                                ).joinToString(" | ")
                                                if (details.isNotBlank()) {
                                                    Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                }
                                            }
                                        }

                                        FilterChip(
                                            selected = isPresent,
                                            onClick = {
                                                viewModel.toggleWorkerAttendance(attendanceDateIso, person.id, !isPresent)
                                            },
                                            label = {
                                                Text(
                                                    text = if (isPresent) "حاضر ✓" else "غایب ✕",
                                                    fontWeight = FontWeight.Bold
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = ColorSuccessGreen.copy(alpha = 0.2f),
                                                selectedLabelColor = ColorSuccessGreen,
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                labelColor = MaterialTheme.colorScheme.outline
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // 6. Section for Detailed Attendance Records & History
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "ثبت‌های تفصیلی و تاریخچه حضور",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = {
                                    attendanceToEdit = null
                                    showAttendanceDialog = true
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ثبت تفصیلی")
                                }
                            }
                        }

                        if (uiState.attendances.isEmpty()) {
                            item {
                                Text("هنوز ثبت گروهی ثبت نشده است.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            val sortedAtt = uiState.attendances.sortedByDescending { it.date }
                            items(sortedAtt) { att ->
                                val crew = crewsMap[att.crewId]
                                val farm = att.farmId?.let { farmsMap[it] }
                                val plot = att.plotId?.let { plotsMap[it] }
                                val location = if (plot != null && !plot.isDefault) "${farm?.name ?: ""} — ${plot.name}" else farm?.name ?: "مزرعه نامشخص"
                                val jDate = JalaliDateHelper.isoToJalali(att.date)

                                val calc = if (crew != null) {
                                    WorkforceCalculator.calculateAttendanceWage(att, crew, uiState.wageRates, personsMap, uiState.salaryRates)
                                } else null

                                val matchingMyCount = uiState.myCounts.find { it.date == att.date && it.crewId == att.crewId && !it.deleted }

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            attendanceToEdit = att
                                            showAttendanceDialog = true
                                        }
                                        .testTag("attendance_card_${att.id}")
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column {
                                                Text(jDate.toDisplayFull(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                                Text("دسته: ${crew?.name ?: "نامشخص"} • محل: $location", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            }

                                            if (calc != null) {
                                                Text(
                                                    NumberFormatHelper.formatTomanShort(calc.totalAmount),
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        if (calc != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    "تعداد: ${NumberFormatHelper.toPersianDigits(calc.totalPersons)} نفر (${NumberFormatHelper.toPersianDigits(calc.totalWageUnits)} واحد)",
                                                    style = MaterialTheme.typography.bodySmall
                                                )

                                                if (matchingMyCount != null) {
                                                    val myHeadcount = matchingMyCount.headcount
                                                    val isMatch = myHeadcount == calc.totalPersons
                                                    val matchText = if (isMatch) "شمارش من منطبق است" else "مغایرت با شمارش من (${NumberFormatHelper.toPersianDigits(myHeadcount)})"
                                                    val matchColor = if (isMatch) ColorSuccessGreen else ColorWarningOrange

                                                    SuggestionChip(
                                                        onClick = { showComparisonDialog = true },
                                                        label = { Text(matchText, color = matchColor, style = MaterialTheme.typography.labelSmall) }
                                                    )
                                                }
                                            }
                                        }

                                        if (att.note.isNotBlank()) {
                                            Text("یادداشت: ${att.note}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // --- TAB 1: PIECEWORK TAB ---
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
                    ) {
                        if (uiState.pieceWorks.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Default.Handshake, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                                        Text("هنوز کار پیمانی ثبت نشده است.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                                        Button(onClick = {
                                            pieceWorkToEdit = null
                                            showPieceWorkDialog = true
                                        }) {
                                            Text("ثبت اولین کار پیمانی")
                                        }
                                    }
                                }
                            }
                        } else {
                            val sortedPw = uiState.pieceWorks.sortedByDescending { it.date }
                            items(sortedPw) { pw ->
                                val farm = pw.farmId?.let { farmsMap[it] }
                                val plot = pw.plotId?.let { plotsMap[it] }
                                val location = if (plot != null && !plot.isDefault) "${farm?.name ?: ""} — ${plot.name}" else farm?.name ?: "محل مشخص نشده"
                                val jDate = JalaliDateHelper.isoToJalali(pw.date)
                                val tag = pw.tagId?.let { tagsMap[it] }

                                val linesArr = remember(pw.lines) {
                                    try { org.json.JSONArray(pw.lines) } catch (_: Exception) { org.json.JSONArray() }
                                }
                                var totalCount = 0
                                for (i in 0 until linesArr.length()) {
                                    totalCount += linesArr.getJSONObject(i).optInt("count", 0)
                                }
                                val totalEarned = totalCount.toLong() * pw.rate

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            pieceWorkToEdit = pw
                                            showPieceWorkDialog = true
                                        }
                                        .testTag("piecework_card_${pw.id}")
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column {
                                                Text("${pw.jobType} (${pw.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    "${NumberFormatHelper.toPersianDigits(linesArr.length())} کارگر • هر واحد ${NumberFormatHelper.formatTomanFull(pw.rate)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }

                                            Text(
                                                NumberFormatHelper.formatTomanShort(totalEarned),
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "مجموع: ${NumberFormatHelper.toPersianDigits(totalCount)} عدد",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(jDate.toDisplayShort(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("محل: $location", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            if (tag != null) {
                                                SuggestionChip(
                                                    onClick = {},
                                                    label = { Text(tag.name, style = MaterialTheme.typography.labelSmall) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // --- TAB 2: MY COUNTS TAB ---
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
                    ) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("شمارش سرانگشتی سر مزرعه", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "برای کنترل ادعای سرکارگر، تعداد کارگرهای حاضر را سریع ثبت کنید تا بعداً مغایرت‌ها مشخص شود.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Button(
                                        onClick = { showComparisonDialog = true },
                                        modifier = Modifier.fillMaxWidth().testTag("open_comparison_dialog_btn")
                                    ) {
                                        Icon(Icons.Default.CompareArrows, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("مقایسه با لیست سرکارگر")
                                    }
                                }
                            }
                        }

                        if (uiState.myCounts.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("هنوز هیچ شمارش مستقلی ثبت نشده است.", color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        } else {
                            val sortedCounts = uiState.myCounts.sortedByDescending { it.date }
                            items(sortedCounts) { mc ->
                                val crew = crewsMap[mc.crewId]
                                val jDate = JalaliDateHelper.isoToJalali(mc.date)

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth().testTag("mycount_card_${mc.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth().padding(14.dp)
                                    ) {
                                        Column {
                                            Text(jDate.toDisplayFull(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text("دسته: ${crew?.name ?: "نامشخص"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            if (mc.note.isNotBlank()) {
                                                Text(mc.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "${NumberFormatHelper.toPersianDigits(mc.headcount)} نفر",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            IconButton(onClick = { viewModel.deleteMyCount(mc.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = ColorDangerRed)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // --- TAB 3: CREWS TAB (MANAGE CREWS) ---
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
                    ) {
                        if (uiState.crews.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                                        Text("هنوز هیچ دسته‌ای ثبت نشده است.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                                        Button(onClick = { showCreateCrewDialog = true }) {
                                            Text("ایجاد اولین دسته")
                                        }
                                    }
                                }
                            }
                        } else {
                            items(uiState.crews) { crew ->
                                val membersCount = uiState.crewMembers.count { it.crewId == crew.id && !it.deleted }
                                val baseWage = WorkforceCalculator.getEffectiveDailyWage(uiState.wageRates, crew.id, null, todayIso, uiState.salaryRates)
                                val foreman = crew.foremanId?.let { personsMap[it] }

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToCrewDetail(crew.id) }
                                        .testTag("crew_card_${crew.id}")
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(if (crew.active) ColorSuccessGreen else ColorNeutralGray)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(crew.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                            }

                                            SuggestionChip(
                                                onClick = { onNavigateToCrewDetail(crew.id) },
                                                label = {
                                                    Text(
                                                        if (crew.kind == "count") "عددی" else "${NumberFormatHelper.toPersianDigits(membersCount)} عضو",
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column {
                                                Text(
                                                    "سرکارگر: ${foreman?.name ?: "ندارد"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                                Text(
                                                    "روزمزد پایه: ${NumberFormatHelper.formatTomanFull(baseWage)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            Row {
                                                IconButton(
                                                    onClick = { crewToEdit = crew },
                                                    modifier = Modifier.testTag("edit_crew_btn_${crew.id}")
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "ویرایش", tint = MaterialTheme.colorScheme.outline)
                                                }
                                                IconButton(
                                                    onClick = { crewToDelete = crew },
                                                    modifier = Modifier.testTag("delete_crew_btn_${crew.id}")
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = ColorDangerRed)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- CREATE CREW DIALOG ---
    if (showCreateCrewDialog) {
        CrewDialog(
            uiState = uiState,
            onDismiss = { showCreateCrewDialog = false },
            onSave = { name, kind, foremanId, dailyWage, effectiveFrom, note ->
                viewModel.createCrew(name, kind, foremanId, dailyWage, effectiveFrom, note) {
                    showCreateCrewDialog = false
                    Toast.makeText(context, "دسته با موفقیت ایجاد شد", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // --- EDIT CREW DIALOG ---
    if (crewToEdit != null) {
        CrewDialog(
            initialCrew = crewToEdit,
            uiState = uiState,
            onDismiss = { crewToEdit = null },
            onSave = { name, kind, foremanId, _, _, note ->
                viewModel.updateCrew(
                    crewToEdit!!.copy(name = name, kind = kind, foremanId = foremanId, note = note)
                ) {
                    crewToEdit = null
                    Toast.makeText(context, "دسته ویرایش شد", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // --- DELETE CREW CONFIRMATION ---
    if (crewToDelete != null) {
        val target = crewToDelete!!
        AlertDialog(
            onDismissRequest = { crewToDelete = null },
            title = { Text("حذف دسته «${target.name}»") },
            text = { Text("آیا از حذف این دسته اطمینان داری؟ اگر سابقه‌ای برای این دسته ثبت شده باشد حذف نخواهد شد.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCrew(target.id) { deleted ->
                            if (deleted) {
                                Toast.makeText(context, "دسته حذف شد", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "این دسته سابقه دارد و قابل حذف نیست", Toast.LENGTH_LONG).show()
                            }
                            crewToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerRed)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { crewToDelete = null }) { Text("انصراف") }
            }
        )
    }

    // --- ATTENDANCE DIALOG ---
    if (showAttendanceDialog) {
        AttendanceDialog(
            initialAttendance = attendanceToEdit,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = {
                showAttendanceDialog = false
                attendanceToEdit = null
            },
            onSave = { date, farmId, plotId, crewId, tagId, lines, headcount, countTicks, foreman, note ->
                if (attendanceToEdit != null) {
                    viewModel.updateAttendance(
                        attendanceToEdit!!.copy(
                            date = date,
                            farmId = farmId,
                            plotId = plotId,
                            crewId = crewId,
                            tagId = tagId,
                            lines = lines,
                            headcount = headcount,
                            countTicks = countTicks,
                            foreman = foreman,
                            note = note
                        )
                    ) {
                        showAttendanceDialog = false
                        attendanceToEdit = null
                        Toast.makeText(context, "حضور و غیاب ویرایش شد", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    viewModel.createAttendance(
                        date = date,
                        farmId = farmId,
                        plotId = plotId,
                        crewId = crewId,
                        tagId = tagId,
                        lines = lines,
                        headcount = headcount,
                        countTicks = countTicks,
                        foreman = foreman,
                        note = note
                    ) {
                        showAttendanceDialog = false
                        Toast.makeText(context, "حضور و غیاب ثبت شد", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // --- PIECEWORK DIALOG ---
    if (showPieceWorkDialog) {
        PieceWorkDialog(
            initialPieceWork = pieceWorkToEdit,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = {
                showPieceWorkDialog = false
                pieceWorkToEdit = null
            },
            onSave = { date, farmId, plotId, tagId, jobType, size, rate, lines, note ->
                if (pieceWorkToEdit != null) {
                    viewModel.updatePieceWork(
                        pieceWorkToEdit!!.copy(
                            date = date,
                            farmId = farmId,
                            plotId = plotId,
                            tagId = tagId,
                            jobType = jobType,
                            size = size,
                            rate = rate,
                            lines = lines,
                            note = note
                        )
                    ) {
                        showPieceWorkDialog = false
                        pieceWorkToEdit = null
                        Toast.makeText(context, "کار پیمانی ویرایش شد", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    viewModel.createPieceWork(
                        date = date,
                        farmId = farmId,
                        plotId = plotId,
                        tagId = tagId,
                        jobType = jobType,
                        size = size,
                        rate = rate,
                        lines = lines,
                        note = note
                    ) {
                        showPieceWorkDialog = false
                        Toast.makeText(context, "کار پیمانی ثبت شد", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // --- MY COUNT DIALOG ---
    if (showMyCountDialog) {
        MyCountDialog(
            uiState = uiState,
            onDismiss = { showMyCountDialog = false },
            onSave = { date, crewId, headcount, note ->
                viewModel.createMyCount(date, crewId, headcount, note) {
                    showMyCountDialog = false
                    Toast.makeText(context, "شمارش ثبت شد", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // --- ATTENDANCE COMPARISON DIALOG ---
    if (showComparisonDialog) {
        AttendanceComparisonDialog(
            uiState = uiState,
            onDismiss = { showComparisonDialog = false }
        )
    }

    // --- ATTENDANCE DATE PICKER DIALOG ---
    if (showAttendanceDatePicker) {
        JalaliDatePickerDialog(
            initialIso = attendanceDateIso,
            onDismiss = { showAttendanceDatePicker = false },
            onDateSelected = { iso ->
                attendanceDateIso = iso
                showAttendanceDatePicker = false
            }
        )
    }
}
