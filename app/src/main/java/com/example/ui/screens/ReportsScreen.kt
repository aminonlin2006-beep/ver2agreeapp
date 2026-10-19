package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.entity.AttendanceEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.PieceWorkEntity
import com.example.data.entity.TaskEntity
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.JalaliDatePickerDialog
import com.example.ui.components.VisualReportsChartComponent
import com.example.ui.theme.ColorDangerRed
import com.example.ui.theme.ColorSuccessGreen
import com.example.ui.theme.ColorWarningOrange
import org.json.JSONArray
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val todayIso = remember { JalaliDateHelper.toIsoDate(LocalDate.now()) }

    // Filter states
    var selectedPreset by remember { mutableStateOf("month") } // "today", "week", "month", "year", "custom"
    var startDateIso by remember {
        mutableStateOf(JalaliDateHelper.addDays(todayIso, -30))
    }
    var endDateIso by remember { mutableStateOf(todayIso) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    var selectedFarmId by remember { mutableStateOf<String?>(null) }
    var selectedPersonId by remember { mutableStateOf<String?>(null) }

    var selectedTab by remember { mutableStateOf(0) } // 0: Workers Summary, 1: Expenses & Tasks

    // Preset handlers
    LaunchedEffect(selectedPreset) {
        when (selectedPreset) {
            "today" -> {
                startDateIso = todayIso
                endDateIso = todayIso
            }
            "week" -> {
                startDateIso = JalaliDateHelper.addDays(todayIso, -7)
                endDateIso = todayIso
            }
            "month" -> {
                startDateIso = JalaliDateHelper.addDays(todayIso, -30)
                endDateIso = todayIso
            }
            "year" -> {
                startDateIso = JalaliDateHelper.addDays(todayIso, -365)
                endDateIso = todayIso
            }
        }
    }

    // Maps
    val personsMap = remember(uiState.persons) { uiState.persons.associateBy { it.id } }
    val farmsMap = remember(uiState.farms) { uiState.farms.associateBy { it.id } }
    val plotsMap = remember(uiState.plots) { uiState.plots.associateBy { it.id } }
    val tagsMap = remember(uiState.tags) { uiState.tags.associateBy { it.id } }

    // Filtered data in date range & farm filter
    val filteredExpenses = remember(uiState.expenses, startDateIso, endDateIso, selectedFarmId) {
        uiState.expenses.filter { e ->
            e.date in startDateIso..endDateIso &&
                    (selectedFarmId == null || e.farmId == selectedFarmId)
        }
    }

    val filteredAttendances = remember(uiState.attendances, startDateIso, endDateIso, selectedFarmId) {
        uiState.attendances.filter { a ->
            a.date in startDateIso..endDateIso &&
                    (selectedFarmId == null || a.farmId == selectedFarmId)
        }
    }

    val filteredPieceWorks = remember(uiState.pieceWorks, startDateIso, endDateIso, selectedFarmId) {
        uiState.pieceWorks.filter { pw ->
            pw.date in startDateIso..endDateIso &&
                    (selectedFarmId == null || pw.farmId == selectedFarmId)
        }
    }

    val filteredPayments = remember(uiState.payments, startDateIso, endDateIso, selectedPersonId) {
        uiState.payments.filter { p ->
            p.date in startDateIso..endDateIso &&
                    (selectedPersonId == null || (p.targetType == "person" && p.targetId == selectedPersonId))
        }
    }

    val filteredTasks = remember(uiState.tasks, startDateIso, endDateIso, selectedFarmId) {
        uiState.tasks.filter { t ->
            t.startDate <= endDateIso && t.endDate >= startDateIso &&
                    (selectedFarmId == null || t.farmId == selectedFarmId)
        }
    }

    // Calculations
    val totalExpenseAmount = remember(filteredExpenses) { filteredExpenses.sumOf { it.amount } }
    val totalPaymentsAmount = remember(filteredPayments) { filteredPayments.sumOf { it.amount } }

    // Calculate worker worker-days and wages from attendance, salary, & piecework
    data class WorkerReport(
        val personId: String,
        val personName: String,
        val daysWorked: Float,
        val attendanceWage: Long,
        val salaryWage: Long,
        val pieceWorkUnits: Float,
        val pieceWorkWage: Long,
        val totalEarned: Long,
        val totalPaid: Long,
        val balance: Long,
        val farmsWorked: Set<String>
    )

    val workerReports = remember(
        filteredAttendances,
        filteredPieceWorks,
        filteredPayments,
        filteredExpenses,
        selectedPersonId,
        personsMap,
        farmsMap,
        uiState.wageRates,
        uiState.salaryRates,
        uiState.employments
    ) {
        val workerMap = mutableMapOf<String, MutableMap<String, Any>>()

        // 1. Attendance processing
        filteredAttendances.forEach { att ->
            val farmName = att.farmId?.let { farmsMap[it]?.name } ?: "عمومی"
            try {
                val lines = JSONArray(att.lines)
                for (i in 0 until lines.length()) {
                    val line = lines.getJSONObject(i)
                    val pid = line.optString("personId")
                    val ticks = line.optInt("ticks", 1)
                    if (pid.isNotBlank() && (selectedPersonId == null || pid == selectedPersonId)) {
                        val current = workerMap.getOrPut(pid) {
                            mutableMapOf(
                                "days" to 0f,
                                "attWage" to 0L,
                                "salaryWage" to 0L,
                                "pwUnits" to 0f,
                                "pwWage" to 0L,
                                "farms" to mutableSetOf<String>()
                            )
                        }
                        @Suppress("UNCHECKED_CAST")
                        val farms = current["farms"] as MutableSet<String>
                        farms.add(farmName)
                        current["days"] = (current["days"] as Float) + ticks.toFloat()

                        val dailyWage = com.example.lib.WorkforceCalculator.getEffectiveDailyWage(
                            wageRates = uiState.wageRates,
                            crewId = att.crewId,
                            personId = pid,
                            date = att.date,
                            salaryRates = uiState.salaryRates
                        )
                        current["attWage"] = (current["attWage"] as Long) + (ticks.toLong() * dailyWage)
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Piecework processing
        filteredPieceWorks.forEach { pw ->
            val farmName = pw.farmId?.let { farmsMap[it]?.name } ?: "عمومی"
            try {
                val arr = JSONArray(pw.lines)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val pid = obj.optString("personId")
                    val countVal = obj.optInt("count", 0)
                    val lineTotal = countVal * pw.rate
                    if (pid.isNotBlank() && (selectedPersonId == null || pid == selectedPersonId)) {
                        val current = workerMap.getOrPut(pid) {
                            mutableMapOf(
                                "days" to 0f,
                                "attWage" to 0L,
                                "salaryWage" to 0L,
                                "pwUnits" to 0f,
                                "pwWage" to 0L,
                                "farms" to mutableSetOf<String>()
                            )
                        }
                        @Suppress("UNCHECKED_CAST")
                        val farms = current["farms"] as MutableSet<String>
                        farms.add(farmName)
                        current["pwUnits"] = (current["pwUnits"] as Float) + countVal
                        current["pwWage"] = (current["pwWage"] as Long) + lineTotal
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Monthly Salary expenses / postings in date range
        filteredExpenses.filter { it.systemKind == "salary" }.forEach { exp ->
            try {
                val arr = JSONArray(exp.workerIds ?: "[]")
                for (i in 0 until arr.length()) {
                    val pid = arr.optString(i)
                    if (pid.isNotBlank() && (selectedPersonId == null || pid == selectedPersonId)) {
                        val current = workerMap.getOrPut(pid) {
                            mutableMapOf(
                                "days" to 0f,
                                "attWage" to 0L,
                                "salaryWage" to 0L,
                                "pwUnits" to 0f,
                                "pwWage" to 0L,
                                "farms" to mutableSetOf<String>()
                            )
                        }
                        current["salaryWage"] = (current["salaryWage"] as Long) + exp.amount
                    }
                }
            } catch (_: Exception) {}
        }

        // Include any person who had payments in this range even if no attendance
        filteredPayments.filter { it.targetType == "person" }.forEach { p ->
            val pid = p.targetId
            if (pid.isNotBlank() && (selectedPersonId == null || pid == selectedPersonId)) {
                workerMap.getOrPut(pid) {
                    mutableMapOf(
                        "days" to 0f,
                        "attWage" to 0L,
                        "salaryWage" to 0L,
                        "pwUnits" to 0f,
                        "pwWage" to 0L,
                        "farms" to mutableSetOf<String>()
                    )
                }
            }
        }

        // Convert to WorkerReport list
        workerMap.map { (pid, data) ->
            val personName = personsMap[pid]?.name ?: "کارگر ناشناس"
            val days = data["days"] as Float
            val attWage = data["attWage"] as Long
            val salWage = data["salaryWage"] as Long
            val pwUnits = data["pwUnits"] as Float
            val pwWage = data["pwWage"] as Long
            @Suppress("UNCHECKED_CAST")
            val farms = data["farms"] as Set<String>

            val isFixedWorker = uiState.employments.any { it.personId == pid && it.kind == "fixed" && !it.deleted }
            val effectiveAttWage = if (isFixedWorker && salWage > 0L) 0L else attWage
            val personPayments = filteredPayments.filter { it.targetType == "person" && it.targetId == pid }.sumOf { it.amount }
            val totalEarned = effectiveAttWage + salWage + pwWage
            val balance = totalEarned - personPayments

            WorkerReport(
                personId = pid,
                personName = personName,
                daysWorked = days,
                attendanceWage = effectiveAttWage,
                salaryWage = salWage,
                pieceWorkUnits = pwUnits,
                pieceWorkWage = pwWage,
                totalEarned = totalEarned,
                totalPaid = personPayments,
                balance = balance,
                farmsWorked = farms
            )
        }.sortedByDescending { it.daysWorked }
    }

    val totalWorkerDays = remember(workerReports) { workerReports.sumOf { it.daysWorked.toDouble() }.toFloat() }
    val totalPieceWorkValue = remember(workerReports) { workerReports.sumOf { it.pieceWorkWage } }
    val completedTasksCount = remember(filteredTasks) { filteredTasks.count { it.status == "done" } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("گزارش جامع کشاورزی", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("reports_back_btn")
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val reportText = buildString {
                                appendLine("📊 گزارش عملکرد مزرعه")
                                appendLine("📅 بازه: ${JalaliDateHelper.fromIsoDate(startDateIso).toDisplayFull()} تا ${JalaliDateHelper.fromIsoDate(endDateIso).toDisplayFull()}")
                                appendLine("------------------------")
                                appendLine("💰 مجموع هزینه‌ها: ${NumberFormatHelper.formatTomanFull(totalExpenseAmount)}")
                                appendLine("💵 مجموع پرداختی‌ها: ${NumberFormatHelper.formatTomanFull(totalPaymentsAmount)}")
                                appendLine("👥 روزکاری کارگران: ${NumberFormatHelper.toPersianDigits(totalWorkerDays)} روز")
                                appendLine("🔨 ارزش پیمانکاری: ${NumberFormatHelper.formatTomanFull(totalPieceWorkValue)}")
                                appendLine("✅ کارهای انجام‌شده: ${NumberFormatHelper.toPersianDigits(completedTasksCount)} مورد")
                                if (workerReports.isNotEmpty()) {
                                    appendLine("------------------------")
                                    appendLine("👥 خلاصه عملکرد کارگران:")
                                    workerReports.take(10).forEach { wr ->
                                        appendLine("• ${wr.personName}: ${NumberFormatHelper.toPersianDigits(wr.daysWorked)} روز | پرداختی: ${NumberFormatHelper.formatTomanShort(wr.totalPaid)}")
                                    }
                                }
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, reportText)
                            }
                            context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری گزارش"))
                        },
                        modifier = Modifier.testTag("share_report_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "اشتراک‌گذاری گزارش")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- DATE PRESET SELECTOR ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("بازه زمانی گزارش:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedPreset == "today",
                                onClick = { selectedPreset = "today" },
                                label = { Text("امروز") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedPreset == "week",
                                onClick = { selectedPreset = "week" },
                                label = { Text("هفته اخیر") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedPreset == "month",
                                onClick = { selectedPreset = "month" },
                                label = { Text("ماه اخیر") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedPreset == "year",
                                onClick = { selectedPreset = "year" },
                                label = { Text("امسال") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedPreset == "custom",
                                onClick = { selectedPreset = "custom" },
                                label = { Text("دوره دلخواه") }
                            )
                        }
                    }

                    if (selectedPreset == "custom") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedCard(
                                onClick = { showStartDatePicker = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("از تاریخ:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text(JalaliDateHelper.fromIsoDate(startDateIso).toDisplayFull(), fontWeight = FontWeight.Bold)
                                }
                            }
                            OutlinedCard(
                                onClick = { showEndDatePicker = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("تا تاریخ:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text(JalaliDateHelper.fromIsoDate(endDateIso).toDisplayFull(), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // --- FARM & WORKER FILTERS ---
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    // Farm selector
                    var farmExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedCard(onClick = { farmExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp).fillMaxWidth()
                            ) {
                                Text(
                                    text = selectedFarmId?.let { farmsMap[it]?.name } ?: "همه مزرعه‌ها",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(expanded = farmExpanded, onDismissRequest = { farmExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("همه مزرعه‌ها") },
                                onClick = { selectedFarmId = null; farmExpanded = false }
                            )
                            uiState.farms.forEach { farm ->
                                DropdownMenuItem(
                                    text = { Text(farm.name) },
                                    onClick = { selectedFarmId = farm.id; farmExpanded = false }
                                )
                            }
                        }
                    }

                    // Person selector
                    var personExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedCard(onClick = { personExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp).fillMaxWidth()
                            ) {
                                Text(
                                    text = selectedPersonId?.let { personsMap[it]?.name } ?: "همه کارگران",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(expanded = personExpanded, onDismissRequest = { personExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("همه کارگران") },
                                onClick = { selectedPersonId = null; personExpanded = false }
                            )
                            uiState.persons.forEach { person ->
                                DropdownMenuItem(
                                    text = { Text(person.name) },
                                    onClick = { selectedPersonId = person.id; personExpanded = false }
                                )
                            }
                        }
                    }
                }
            }

            // --- STATS SUMMARY CARDS ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("مجموع هزینه‌ها", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    NumberFormatHelper.formatTomanFull(totalExpenseAmount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Icon(Icons.Default.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("مجموع پرداختی‌ها", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    NumberFormatHelper.formatTomanFull(totalPaymentsAmount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("روزکاری کارگران", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "${NumberFormatHelper.toPersianDigits(totalWorkerDays)} روز",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Icon(Icons.Default.TaskAlt, contentDescription = null, tint = ColorSuccessGreen)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("کارهای انجام‌شده", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "${NumberFormatHelper.toPersianDigits(completedTasksCount)} مورد",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // --- TABS ---
            item {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("نمودار خطی بصری") },
                        icon = { Icon(Icons.Default.ShowChart, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("عملکرد کارگران") },
                        icon = { Icon(Icons.Default.Badge, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("هزینه‌ها و کارها") },
                        icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) }
                    )
                }
            }

            // --- TAB CONTENT 0: VISUAL CHART ---
            if (selectedTab == 0) {
                item {
                    VisualReportsChartComponent(
                        expenses = filteredExpenses,
                        payments = filteredPayments,
                        startDateIso = startDateIso,
                        endDateIso = endDateIso,
                        onPeriodSelected = { startIso, endIso ->
                            startDateIso = startIso
                            endDateIso = endIso
                            selectedPreset = "custom"
                        }
                    )
                }
            }

            // --- TAB CONTENT 1: WORKERS ---
            if (selectedTab == 1) {
                if (workerReports.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                                Text("در این بازه زمانی هیچ فعالیت یا کارکردی ثبت نشده است.")
                            }
                        }
                    }
                } else {
                    items(workerReports) { wr ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(wr.personName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    val farmsText = wr.farmsWorked.joinToString("، ")
                                    if (farmsText.isNotBlank()) {
                                        Text(farmsText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }

                                Divider()

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Text("روزکاری (حضور):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        Text("${NumberFormatHelper.toPersianDigits(wr.daysWorked)} روز", fontWeight = FontWeight.SemiBold)
                                    }
                                    Column {
                                        Text("کارکرد و حقوق:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        Text(NumberFormatHelper.formatTomanShort(wr.totalEarned), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Column {
                                        Text("پرداخت‌شده:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        Text(NumberFormatHelper.formatTomanShort(wr.totalPaid), fontWeight = FontWeight.Bold, color = ColorSuccessGreen)
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                ) {
                                    if (wr.pieceWorkUnits > 0) {
                                        Text(
                                            "پیمانکاری: ${NumberFormatHelper.toPersianDigits(wr.pieceWorkUnits)} واحد (${NumberFormatHelper.formatTomanShort(wr.pieceWorkWage)})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.width(1.dp))
                                    }

                                    val (balanceText, balanceColor) = when {
                                        wr.balance > 0 -> "طلبکار: ${NumberFormatHelper.formatTomanShort(wr.balance)}" to ColorWarningOrange
                                        wr.balance < 0 -> "بدهکار: ${NumberFormatHelper.formatTomanShort(-wr.balance)}" to ColorDangerRed
                                        else -> "تسویه کامل" to ColorSuccessGreen
                                    }
                                    Surface(
                                        color = balanceColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = balanceText,
                                            color = balanceColor,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- TAB CONTENT 2: EXPENSES & TASKS ---
            if (selectedTab == 2) {
                item {
                    Text("ریز هزینه‌های ثبت‌شده:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                if (filteredExpenses.isEmpty()) {
                    item {
                        Text("هزینه‌ای در این بازه ثبت نشده است.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    items(filteredExpenses.take(10)) { exp ->
                        val farmName = exp.farmId?.let { farmsMap[it]?.name }
                        val plotName = exp.plotId?.let { plotsMap[it]?.name }
                        val loc = listOfNotNull(farmName, plotName).joinToString(" — ")
                        val expTitle = exp.note.ifBlank { tagsMap[exp.tagId]?.name ?: "هزینه" }

                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp).fillMaxWidth()
                            ) {
                                Column {
                                    Text(expTitle, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${JalaliDateHelper.fromIsoDate(exp.date).toDisplayFull()} ${if (loc.isNotBlank()) "• $loc" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Text(NumberFormatHelper.formatTomanFull(exp.amount), fontWeight = FontWeight.Bold, color = ColorDangerRed)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("کارهای دوره:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                if (filteredTasks.isEmpty()) {
                    item {
                        Text("کاری در این بازه ثبت نشده است.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    items(filteredTasks.take(10)) { task ->
                        val statusText = if (task.status == "done") "انجام شده" else "در حال انجام"
                        val statusColor = if (task.status == "done") ColorSuccessGreen else ColorWarningOrange

                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp).fillMaxWidth()
                            ) {
                                Column {
                                    Text(task.title, fontWeight = FontWeight.Bold)
                                    Text(
                                        "از ${JalaliDateHelper.fromIsoDate(task.startDate).toDisplayFull()} تا ${JalaliDateHelper.fromIsoDate(task.endDate).toDisplayFull()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Surface(
                                    color = statusColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        statusText,
                                        color = statusColor,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Jalali Date Pickers for custom preset
    if (showStartDatePicker) {
        JalaliDatePickerDialog(
            initialIso = startDateIso,
            onDismiss = { showStartDatePicker = false },
            onDateSelected = { iso ->
                startDateIso = iso
                showStartDatePicker = false
            }
        )
    }

    if (showEndDatePicker) {
        JalaliDatePickerDialog(
            initialIso = endDateIso,
            onDismiss = { showEndDatePicker = false },
            onDateSelected = { iso ->
                endDateIso = iso
                showEndDatePicker = false
            }
        )
    }
}
