package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.entity.TaskEntity
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.SummaryDashboardComponent
import com.example.ui.theme.ColorDangerRed
import com.example.ui.theme.ColorNeutralGray
import com.example.ui.theme.ColorSuccessGreen
import com.example.ui.theme.ColorWarningOrange
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateToFarms: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToIrrigation: () -> Unit = {},
    onNavigateToPersons: () -> Unit,
    onNavigateToTags: () -> Unit,
    onNavigateToWorkforce: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToCrops: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToLedger: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val today = remember { JalaliDateHelper.today() }
    val todayIso = remember { JalaliDateHelper.toIsoDate(LocalDate.now()) }
    val tomorrowIso = remember { JalaliDateHelper.addDays(todayIso, 1) }

    var selectedTileForOptions by remember { mutableStateOf<String?>(null) }
    var showUpdateCard by remember { mutableStateOf(false) }
    var tomorrowCardDismissedTonight by remember { mutableStateOf(false) }
    var backupReminderDismissedSession by remember { mutableStateOf(false) }

    val showBackupReminder = (uiState.unsentChanges >= 5 || (uiState.unsentChanges > 0 && uiState.lastBackupReminderDate != todayIso)) && !backupReminderDismissedSession
    val showGuide = uiState.farms.isEmpty() && !uiState.isGuideDismissed

    // Check time for "Tomorrow's Tasks" card (Decision 53, 59, 171)
    val showTomorrowTasksCard = remember(uiState.tasks, uiState.tomorrowTasksHour, tomorrowCardDismissedTonight, uiState.isTasksModuleActive) {
        if (!uiState.isTasksModuleActive || tomorrowCardDismissedTonight) return@remember false
        val now = LocalTime.now()
        val targetHour = try {
            val parts = uiState.tomorrowTasksHour.split(":")
            LocalTime.of(parts[0].toInt(), parts.getOrElse(1) { "0" }.toInt())
        } catch (_: Exception) { LocalTime.of(18, 0) }

        if (now >= targetHour) {
            uiState.tasks.any { it.status == "open" && it.startDate <= tomorrowIso && tomorrowIso <= it.endDate }
        } else false
    }

    val tomorrowTasksCount = remember(uiState.tasks, tomorrowIso) {
        uiState.tasks.count { it.status == "open" && it.startDate <= tomorrowIso && tomorrowIso <= it.endDate }
    }

    // Today's & Overdue Tasks for "کارهای امروز" section (Decision 143, 282)
    val todayAndOverdueTasks = remember(uiState.tasks, todayIso, uiState.isTasksModuleActive) {
        if (!uiState.isTasksModuleActive) return@remember emptyList()
        val tasks = uiState.tasks.filter { t ->
            if (t.status != "open") return@filter false
            val isToday = t.startDate <= todayIso && todayIso <= t.endDate
            val isOverdue = t.endDate < todayIso
            isToday || isOverdue
        }
        tasks.sortedWith(
            compareBy<TaskEntity> { t -> if (t.endDate < todayIso) 0 else 1 }
                .thenBy { t -> when (t.priority) { "urgent" -> 0; "important" -> 1; else -> 2 } }
                .thenBy { it.createdAt }
        )
    }

    val overdueCount = remember(uiState.tasks, todayIso) {
        uiState.tasks.count { it.status == "open" && it.endDate < todayIso }
    }
    val todayOpenCount = remember(uiState.tasks, todayIso) {
        uiState.tasks.count { it.status == "open" && it.startDate <= todayIso && todayIso <= it.endDate }
    }

    val farmsMap = remember(uiState.farms) { uiState.farms.associateBy { it.id } }
    val plotsMap = remember(uiState.plots) { uiState.plots.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = today.toDisplayFull(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        // Backup status subtitle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onNavigateToBackup() }
                                .padding(vertical = 2.dp)
                                .testTag("backup_status_badge")
                        ) {
                            if (uiState.unsentChanges == 0) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ColorSuccessGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "همه چیز پشتیبان دارد",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ColorSuccessGreen
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = ColorWarningOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                val changesFa = NumberFormatHelper.toPersianDigits(uiState.unsentChanges)
                                Text(
                                    text = "$changesFa تغییر بدون پشتیبان",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ColorWarningOrange,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToBackup,
                        modifier = Modifier.testTag("top_bar_backup_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "پشتیبان‌گیری"
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("top_bar_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "تنظیمات"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- NEWS CARDS ---
            fun itemFull(content: @Composable LazyGridItemScope.() -> Unit) {
                item(span = { GridItemSpan(maxLineSpan) }, content = content)
            }

            // 1. Update card
            if (showUpdateCard) {
                itemFull {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "نسخه‌ی تازه آماده است",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "جهت به‌روزرسانی لمس کنید",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { showUpdateCard = false }) {
                                Icon(Icons.Default.Close, contentDescription = "بستن")
                            }
                        }
                    }
                }
            }

            // 2. Tomorrow's Tasks News Card (کارت کارهای فردا)
            if (showTomorrowTasksCard) {
                itemFull {
                    val countFa = NumberFormatHelper.toPersianDigits(tomorrowTasksCount)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToTasks() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "کارهای فردا ($countFa)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "برنامه‌ریزی و آماده‌سازی برای کارهای فردا",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { tomorrowCardDismissedTonight = true }) {
                                Icon(Icons.Default.Close, contentDescription = "بستن")
                            }
                        }
                    }
                }
            }

            // 3. Smart Backup Reminder Card
            if (showBackupReminder) {
                itemFull {
                    val changesFa = NumberFormatHelper.toPersianDigits(uiState.unsentChanges)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = ColorWarningOrange.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("smart_backup_reminder_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(ColorWarningOrange.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = ColorWarningOrange
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "پشتیبان‌گیری توصیه می‌شود",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorWarningOrange
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = ColorWarningOrange.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "$changesFa تغییر تازه",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorWarningOrange,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "داده‌ها فقط روی این گوشی ذخیره هستند؛ جهت ایمنی داده‌ها خروجی پشتیبان بگیرید.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                IconButton(onClick = {
                                    backupReminderDismissedSession = true
                                    viewModel.dismissDailyBackupReminder()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "بستن")
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = onNavigateToBackup,
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorWarningOrange),
                                    modifier = Modifier.weight(1f).testTag("backup_now_btn")
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("پشتیبان‌گیری سریع")
                                }
                                OutlinedButton(
                                    onClick = {
                                        backupReminderDismissedSession = true
                                        viewModel.dismissDailyBackupReminder()
                                    }
                                ) {
                                    Text("بعداً")
                                }
                            }
                        }
                    }
                }
            }

            // --- GUIDE CARD ---
            if (showGuide) {
                itemFull {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "خوش آمدی!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "اول یک مزرعه بساز ← بعد اشخاص را اضافه کن",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onNavigateToFarms,
                                modifier = Modifier.testTag("guide_create_farm_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ساختن مزرعه")
                            }
                        }
                    }
                }
            }

            // --- SUMMARY DASHBOARD COMPONENT (نمای کلی و نمودار وضعیت مزرعه، کارگران و کارها) ---
            itemFull {
                SummaryDashboardComponent(
                    uiState = uiState,
                    onNavigateToFarms = onNavigateToFarms,
                    onNavigateToWorkforce = onNavigateToWorkforce,
                    onNavigateToTasks = onNavigateToTasks
                )
            }

            // --- TODAY'S TASKS SECTION (کارهای امروز - Decision 143, 282) ---
            if (uiState.isTasksModuleActive) {
                itemFull {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "کارهای امروز",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (todayAndOverdueTasks.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val countFa = NumberFormatHelper.toPersianDigits(todayAndOverdueTasks.size)
                                    Text(
                                        text = "($countFa)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            if (todayAndOverdueTasks.size > 5) {
                                val totalFa = NumberFormatHelper.toPersianDigits(todayAndOverdueTasks.size)
                                TextButton(onClick = onNavigateToTasks) {
                                    Text("همه ($totalFa)")
                                }
                            }
                        }

                        if (todayAndOverdueTasks.isEmpty()) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                ) {
                                    Text(
                                        text = "امروز کاری ثبت نشده است.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        } else {
                            todayAndOverdueTasks.take(5).forEach { task ->
                                val isOverdue = task.endDate < todayIso
                                val farm = task.farmId?.let { farmsMap[it] }
                                val plot = task.plotId?.let { plotsMap[it] }
                                val loc = if (plot != null && !plot.isDefault) "${farm?.name ?: ""} — ${plot.name}" else farm?.name

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToTasks() }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            val priorityColor = when (task.priority) {
                                                "urgent" -> ColorDangerRed
                                                "important" -> ColorWarningOrange
                                                else -> ColorNeutralGray
                                            }
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(priorityColor))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(task.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                if (loc != null || isOverdue) {
                                                    val sub = if (isOverdue) "عقب‌افتاده ${loc?.let { "• $it" } ?: ""}" else loc ?: ""
                                                    Text(
                                                        text = sub,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isOverdue) ColorDangerRed else MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = { viewModel.toggleTaskStatus(task.id, "done") },
                                            modifier = Modifier.testTag("home_quick_done_btn_${task.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircleOutline,
                                                contentDescription = "انجام شد",
                                                tint = ColorSuccessGreen
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- TILES ---
            val activeTiles = uiState.tileOrder.filter { !uiState.hiddenTileKeys.contains(it) }

            items(activeTiles.size) { index ->
                val tileKey = activeTiles[index]
                when (tileKey) {
                    "farms" -> {
                        FarmHomeTile(
                            count = uiState.farms.size,
                            onClick = onNavigateToFarms,
                            onLongClick = { selectedTileForOptions = "farms" }
                        )
                    }
                    "irrigation" -> {
                        if (uiState.isIrrigationModuleActive) {
                            val upCount = uiState.irrigationSchedules.count { !it.deleted && it.status == "scheduled" }
                            val tdCount = uiState.irrigationSchedules.count { !it.deleted && it.date == todayIso && it.status == "scheduled" }
                            IrrigationHomeTile(
                                upcomingCount = upCount,
                                todayCount = tdCount,
                                onClick = onNavigateToIrrigation,
                                onLongClick = { selectedTileForOptions = "irrigation" }
                            )
                        }
                    }
                    "tasks" -> {
                        if (uiState.isTasksModuleActive) {
                            TaskHomeTile(
                                todayCount = todayOpenCount,
                                overdueCount = overdueCount,
                                onClick = onNavigateToTasks,
                                onLongClick = { selectedTileForOptions = "tasks" }
                            )
                        }
                    }
                    "workforce" -> {
                        if (uiState.isWorkforceModuleActive) {
                            WorkforceHomeTile(
                                crewsCount = uiState.crews.size,
                                attendancesCount = uiState.attendances.count { it.date == todayIso },
                                onClick = onNavigateToWorkforce,
                                onLongClick = { selectedTileForOptions = "workforce" }
                            )
                        }
                    }
                    "ledger" -> {
                        if (uiState.isWorkforceModuleActive) {
                            LedgerHomeTile(
                                paymentsCount = uiState.payments.size,
                                onClick = onNavigateToLedger,
                                onLongClick = { selectedTileForOptions = "ledger" }
                            )
                        }
                    }
                    "expenses" -> {
                        ExpenseHomeTile(
                            onClick = onNavigateToExpenses,
                            onLongClick = { selectedTileForOptions = "expenses" }
                        )
                    }
                    "crops" -> {
                        CropHomeTile(
                            onClick = onNavigateToCrops,
                            onLongClick = { selectedTileForOptions = "crops" }
                        )
                    }
                    "reports" -> {
                        if (uiState.isReportsModuleActive) {
                            ReportsHomeTile(
                                onClick = onNavigateToReports,
                                onLongClick = { selectedTileForOptions = "reports" }
                            )
                        }
                    }
                    "persons" -> {
                        PersonHomeTile(
                            count = uiState.persons.size,
                            onClick = onNavigateToPersons,
                            onLongClick = { selectedTileForOptions = "persons" }
                        )
                    }
                    "tags" -> {
                        TagHomeTile(
                            count = uiState.tags.size,
                            onClick = onNavigateToTags,
                            onLongClick = { selectedTileForOptions = "tags" }
                        )
                    }
                }
            }

            // --- HIDDEN TILES SECTION ---
            if (uiState.hiddenTileKeys.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Text(
                            text = "کاشی‌های پنهان",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.hiddenTileKeys.forEach { key ->
                                val title = when (key) {
                                    "farms" -> "مزرعه‌ها"
                                    "irrigation" -> "زمان‌بندی آبیاری"
                                    "tasks" -> "کارهای آینده"
                                    "workforce" -> "نیروی کار"
                                    "ledger" -> "دفتر حساب"
                                    "expenses" -> "هزینه‌ها"
                                    "crops" -> "کشت‌ها"
                                    "reports" -> "گزارش‌ها"
                                    "persons" -> "اشخاص"
                                    "tags" -> "برچسب‌ها"
                                    else -> key
                                }
                                AssistChip(
                                    onClick = { viewModel.unhideTile(key) },
                                    label = { Text("نمایش $title") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Tile Options Sheet
    if (selectedTileForOptions != null) {
        val tile = selectedTileForOptions!!
        val tileTitle = when (tile) {
            "farms" -> "مزرعه‌ها"
            "irrigation" -> "زمان‌بندی آبیاری"
            "tasks" -> "کارهای آینده"
            "workforce" -> "نیروی کار"
            "ledger" -> "دفتر حساب"
            "expenses" -> "هزینه‌ها"
            "crops" -> "کشت‌ها"
            "reports" -> "گزارش‌ها"
            "persons" -> "اشخاص"
            "tags" -> "برچسب‌ها"
            else -> ""
        }

        AlertDialog(
            onDismissRequest = { selectedTileForOptions = null },
            title = { Text("تنظیم کاشی «$tileTitle»") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.moveTile(tile, -1)
                            selectedTileForOptions = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("انتقال به بالا")
                        }
                    }
                    TextButton(
                        onClick = {
                            viewModel.moveTile(tile, 1)
                            selectedTileForOptions = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("انتقال به پایین")
                        }
                    }
                    TextButton(
                        onClick = {
                            viewModel.hideTile(tile)
                            selectedTileForOptions = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("پنهان کردن کاشی")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTileForOptions = null }) {
                    Text("بستن")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FarmHomeTile(
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    BigHomeCard(
        title = "مزرعه‌ها",
        subtitle = "${NumberFormatHelper.toPersianDigits(count)} مزرعه ثبت شده",
        icon = Icons.Default.Agriculture,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        onClick = onClick,
        onLongClick = onLongClick,
        testTag = "home_tile_farms"
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IrrigationHomeTile(
    upcomingCount: Int,
    todayCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val subtitle = if (todayCount > 0) {
        val todayFa = NumberFormatHelper.toPersianDigits(todayCount)
        val upFa = NumberFormatHelper.toPersianDigits(upcomingCount)
        "$todayFa نوبت امروز • $upFa در انتظار"
    } else {
        "${NumberFormatHelper.toPersianDigits(upcomingCount)} نوبت آبیاری قطعات"
    }

    BigHomeCard(
        title = "زمان‌بندی آبیاری",
        subtitle = subtitle,
        icon = Icons.Default.WaterDrop,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        onClick = onClick,
        onLongClick = onLongClick,
        testTag = "home_tile_irrigation"
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskHomeTile(
    todayCount: Int,
    overdueCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val subtitle = if (overdueCount > 0) {
        val todayFa = NumberFormatHelper.toPersianDigits(todayCount)
        val overdueFa = NumberFormatHelper.toPersianDigits(overdueCount)
        "$todayFa کار امروز، $overdueFa عقب‌افتاده"
    } else {
        "${NumberFormatHelper.toPersianDigits(todayCount)} کار برای امروز"
    }

    BigHomeCard(
        title = "کارهای آینده",
        subtitle = subtitle,
        icon = Icons.Default.Assignment,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        onClick = onClick,
        onLongClick = onLongClick,
        testTag = "home_tile_tasks"
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkforceHomeTile(
    crewsCount: Int,
    attendancesCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val subtitle = if (attendancesCount > 0) {
        "${NumberFormatHelper.toPersianDigits(crewsCount)} دسته • ${NumberFormatHelper.toPersianDigits(attendancesCount)} حضور ثبت‌شده امروز"
    } else {
        "${NumberFormatHelper.toPersianDigits(crewsCount)} دسته‌ی کارگری و روزمزد"
    }

    BigHomeCard(
        title = "نیروی کار و روزمزد",
        subtitle = subtitle,
        icon = Icons.Default.Groups,
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        onClick = onClick,
        onLongClick = onLongClick,
        testTag = "home_tile_workforce"
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LedgerHomeTile(
    paymentsCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    BigHomeCard(
        title = "دفتر حساب و تسویه‌ها",
        subtitle = "گردش حساب اشخاص، دسته‌ها و پرداخت‌ها",
        icon = Icons.Default.AccountBalanceWallet,
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        onClick = onClick,
        onLongClick = onLongClick,
        testTag = "home_tile_ledger"
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonHomeTile(
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    BigHomeCard(
        title = "اشخاص",
        subtitle = "${NumberFormatHelper.toPersianDigits(count)} شخص ثبت شده",
        icon = Icons.Default.People,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        onClick = onClick,
        onLongClick = onLongClick,
        testTag = "home_tile_persons"
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagHomeTile(
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    BigHomeCard(
        title = "برچسب‌ها",
        subtitle = "${NumberFormatHelper.toPersianDigits(count)} مرحله و فعالیت",
        icon = Icons.Default.LocalOffer,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick,
        onLongClick = onLongClick,
        testTag = "home_tile_tags"
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BigHomeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    testTag: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .testTag(testTag)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ExpenseHomeTile(
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    BigHomeCard(
        title = "هزینه‌ها",
        subtitle = "مدیریت هزینه‌های مزرعه",
        icon = Icons.Default.AttachMoney,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick,
        onLongClick = onLongClick,
        testTag = "expense_tile"
    )
}

@Composable
private fun CropHomeTile(
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    BigHomeCard(
        title = "کشت‌ها",
        subtitle = "مدیریت کشت‌ها و محصولات",
        icon = Icons.Default.Eco,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        onClick = onClick,
        onLongClick = onLongClick,
        testTag = "crop_tile"
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReportsHomeTile(
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    BigHomeCard(
        title = "گزارش‌ها و نمودارها",
        subtitle = "نمودار خطی هزینه‌ها و حقوق",
        icon = Icons.Default.ShowChart,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        onClick = onClick,
        onLongClick = onLongClick,
        testTag = "reports_tile"
    )
}
