package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import com.example.data.entity.IrrigationScheduleEntity
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import com.example.lib.PersianDateTimeFormatter
import com.example.lib.SmsHelper
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.IrrigationFormDialog
import com.example.ui.theme.ColorDangerRed
import com.example.ui.theme.ColorSuccessGreen
import com.example.ui.theme.ColorWarningOrange
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrrigationScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val todayIso = remember { JalaliDateHelper.toIsoDate(LocalDate.now()) }

    var selectedStatusFilter by remember { mutableStateOf("all") } // "all", "scheduled", "today", "done"
    var selectedFarmFilterId by remember { mutableStateOf<String?>(null) }

    var scheduleToEdit by remember { mutableStateOf<IrrigationScheduleEntity?>(null) }
    var scheduleToDelete by remember { mutableStateOf<IrrigationScheduleEntity?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val farmsMap = remember(uiState.farms) { uiState.farms.associateBy { it.id } }
    val plotsMap = remember(uiState.plots) { uiState.plots.associateBy { it.id } }
    val personsMap = remember(uiState.persons) { uiState.persons.associateBy { it.id } }

    val nonDeletedSchedules = remember(uiState.irrigationSchedules) {
        uiState.irrigationSchedules.filter { !it.deleted }
    }

    val filteredSchedules = remember(nonDeletedSchedules, selectedStatusFilter, selectedFarmFilterId, todayIso) {
        nonDeletedSchedules.filter { s ->
            val matchFarm = selectedFarmFilterId == null || s.farmId == selectedFarmFilterId
            val matchStatus = when (selectedStatusFilter) {
                "scheduled" -> s.status == "scheduled"
                "today" -> s.date == todayIso && s.status != "cancelled"
                "done" -> s.status == "done"
                else -> true
            }
            matchFarm && matchStatus
        }.sortedWith(
            compareBy<IrrigationScheduleEntity> {
                when (it.status) {
                    "scheduled" -> 0
                    "done" -> 1
                    else -> 2
                }
            }.thenBy { it.date }.thenBy { it.time }
        )
    }

    val scheduledCount = remember(nonDeletedSchedules) { nonDeletedSchedules.count { it.status == "scheduled" } }
    val todayCount = remember(nonDeletedSchedules, todayIso) { nonDeletedSchedules.count { it.date == todayIso && it.status == "scheduled" } }
    val doneCount = remember(nonDeletedSchedules) { nonDeletedSchedules.count { it.status == "done" } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("زمان‌بندی و یادآور آبیاری")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("irrigation_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showCreateDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("new_irrigation_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نوبت جدید")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("ثبت آبیاری جدید") },
                modifier = Modifier.testTag("irrigation_fab")
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("irrigation_list")
        ) {
            // Summary KPI Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("در انتظار آبیاری", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = "${NumberFormatHelper.toPersianDigits(scheduledCount)} نوبت",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("آبیاری‌های امروز", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = "${NumberFormatHelper.toPersianDigits(todayCount)} نوبت",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (todayCount > 0) ColorWarningOrange else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("انجام‌شده", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = "${NumberFormatHelper.toPersianDigits(doneCount)} نوبت",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ColorSuccessGreen
                            )
                        }
                    }
                }
            }

            // Status Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedStatusFilter == "all",
                            onClick = { selectedStatusFilter = "all" },
                            label = { Text("همه (${NumberFormatHelper.toPersianDigits(nonDeletedSchedules.size)})") },
                            modifier = Modifier.testTag("irrigation_filter_all")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedStatusFilter == "scheduled",
                            onClick = { selectedStatusFilter = "scheduled" },
                            label = { Text("در انتظار (${NumberFormatHelper.toPersianDigits(scheduledCount)})") },
                            modifier = Modifier.testTag("irrigation_filter_scheduled")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedStatusFilter == "today",
                            onClick = { selectedStatusFilter = "today" },
                            label = { Text("امروز (${NumberFormatHelper.toPersianDigits(todayCount)})") },
                            modifier = Modifier.testTag("irrigation_filter_today")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedStatusFilter == "done",
                            onClick = { selectedStatusFilter = "done" },
                            label = { Text("انجام‌شده (${NumberFormatHelper.toPersianDigits(doneCount)})") },
                            modifier = Modifier.testTag("irrigation_filter_done")
                        )
                    }
                }
            }

            // Farm Filter if more than 1 farm exists
            if (uiState.farms.size > 1) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            SuggestionChip(
                                onClick = { selectedFarmFilterId = null },
                                label = { Text("تمام مزارع") },
                                colors = if (selectedFarmFilterId == null) SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ) else SuggestionChipDefaults.suggestionChipColors()
                            )
                        }
                        items(uiState.farms) { farm ->
                            SuggestionChip(
                                onClick = { selectedFarmFilterId = farm.id },
                                label = { Text(farm.name) },
                                colors = if (selectedFarmFilterId == farm.id) SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ) else SuggestionChipDefaults.suggestionChipColors()
                            )
                        }
                    }
                }
            }

            // List of Irrigation Cards
            if (filteredSchedules.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Icon(
                                Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "هیچ برنامه آبیاری در این بخش یافت نشد.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Button(onClick = { showCreateDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ثبت برنامه آبیاری برای یک قطعه")
                            }
                        }
                    }
                }
            } else {
                items(filteredSchedules, key = { it.id }) { schedule ->
                    val farm = farmsMap[schedule.farmId]
                    val plot = plotsMap[schedule.plotId]
                    val irrigator = schedule.irrigatorPersonId?.let { personsMap[it] }

                    IrrigationScheduleCard(
                        schedule = schedule,
                        farm = farm,
                        plot = plot,
                        irrigator = irrigator,
                        todayIso = todayIso,
                        onToggleDone = {
                            val newStatus = if (schedule.status == "done") "scheduled" else "done"
                            viewModel.toggleIrrigationStatus(schedule.id, newStatus)
                        },
                        onSendSms = {
                            val irrigatorName = irrigator?.name
                            val irrigatorPhone = irrigator?.phone ?: ""
                            val farmName = farm?.name ?: "مزرعه"
                            val plotName = if (plot != null && !plot.isDefault) plot.name else "تمام مزرعه"

                            val smsText = SmsHelper.buildIrrigationSmsText(
                                irrigatorName = irrigatorName,
                                farmName = farmName,
                                plotName = plotName,
                                dateIso = schedule.date,
                                time = schedule.time,
                                durationHours = schedule.durationHours,
                                waterSource = schedule.waterSource,
                                note = schedule.note
                            )

                            SmsHelper.sendSms(context, irrigatorPhone, smsText)
                            viewModel.markIrrigationSmsSent(schedule.id)
                        },
                        onEdit = { scheduleToEdit = schedule },
                        onDelete = { scheduleToDelete = schedule }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showCreateDialog) {
        IrrigationFormDialog(
            farms = uiState.farms,
            plots = uiState.plots,
            persons = uiState.persons,
            onDismiss = { showCreateDialog = false },
            onSave = { farmId, plotId, irrigatorPersonId, dateIso, time, durationHours, waterSource, reminderEnabled, reminderMinutesBefore, note ->
                viewModel.createIrrigationSchedule(
                    farmId = farmId,
                    plotId = plotId,
                    irrigatorPersonId = irrigatorPersonId,
                    date = dateIso,
                    time = time,
                    durationHours = durationHours,
                    waterSource = waterSource,
                    reminderEnabled = reminderEnabled,
                    reminderMinutesBefore = reminderMinutesBefore,
                    note = note
                ) {
                    showCreateDialog = false
                    Toast.makeText(context, "برنامه آبیاری با موفقیت ذخیره شد", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (scheduleToEdit != null) {
        IrrigationFormDialog(
            initialSchedule = scheduleToEdit,
            farms = uiState.farms,
            plots = uiState.plots,
            persons = uiState.persons,
            onDismiss = { scheduleToEdit = null },
            onSave = { farmId, plotId, irrigatorPersonId, dateIso, time, durationHours, waterSource, reminderEnabled, reminderMinutesBefore, note ->
                val updated = scheduleToEdit!!.copy(
                    farmId = farmId,
                    plotId = plotId,
                    irrigatorPersonId = irrigatorPersonId,
                    date = dateIso,
                    time = time,
                    durationHours = durationHours,
                    waterSource = waterSource,
                    reminderEnabled = reminderEnabled,
                    reminderMinutesBefore = reminderMinutesBefore,
                    note = note
                )
                viewModel.updateIrrigationSchedule(updated) {
                    scheduleToEdit = null
                    Toast.makeText(context, "تغییرات آبیاری ذخیره شد", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (scheduleToDelete != null) {
        ConfirmDialog(
            title = "حذف نوبت آبیاری",
            message = "آیا از حذف این برنامه آبیاری اطمینان دارید؟ هشدار یادآور نوتیفیکیشن نیز لغو خواهد شد.",
            confirmText = "حذف",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteIrrigationSchedule(scheduleToDelete!!.id) {
                    scheduleToDelete = null
                    Toast.makeText(context, "برنامه آبیاری حذف شد", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { scheduleToDelete = null }
        )
    }
}

@Composable
private fun IrrigationScheduleCard(
    schedule: IrrigationScheduleEntity,
    farm: com.example.data.entity.FarmEntity?,
    plot: com.example.data.entity.PlotEntity?,
    irrigator: com.example.data.entity.PersonEntity?,
    todayIso: String,
    onToggleDone: () -> Unit,
    onSendSms: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isDone = schedule.status == "done"
    val isToday = schedule.date == todayIso
    val isOverdue = schedule.date < todayIso && schedule.status == "scheduled"

    val naturalDateTime = remember(schedule.date, schedule.time) {
        PersianDateTimeFormatter.formatPersianDateTime(schedule.date, schedule.time)
    }

    val cardColor = when {
        isDone -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isToday) 2.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("irrigation_card_${schedule.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Location & Status Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    val farmName = farm?.name ?: "مزرعه"
                    val plotName = if (plot != null && !plot.isDefault) " — قطعه‌ی ${plot.name}" else ""
                    Text(
                        text = "$farmName$plotName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                when {
                    isDone -> {
                        Surface(
                            color = ColorSuccessGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "تکمیل شد",
                                color = ColorSuccessGreen,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    isToday -> {
                        Surface(
                            color = ColorWarningOrange.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "نوبت امروز",
                                color = ColorWarningOrange,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    isOverdue -> {
                        Surface(
                            color = ColorDangerRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "موعد گذشته",
                                color = ColorDangerRed,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    else -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "برنامه‌ریزی‌شده",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Natural Date & Time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = naturalDateTime,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Details Row: Duration & Water Source & Reminder
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (schedule.durationHours != null && schedule.durationHours > 0) {
                    val durFa = NumberFormatHelper.toPersianDigits(
                        if (schedule.durationHours % 1.0 == 0.0) schedule.durationHours.toInt().toString() else schedule.durationHours.toString()
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text("مدت: $durFa ساعت", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }

                if (!schedule.waterSource.isNullOrBlank()) {
                    val srcFa = when (schedule.waterSource) {
                        "well" -> "چاه"
                        "canal" -> "کانال / جوی"
                        "pool" -> "استخر ذخیره"
                        "network" -> "شبکه / لوله"
                        else -> schedule.waterSource
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text("منبع: $srcFa", style = MaterialTheme.typography.labelSmall) }
                    )
                }

                if (schedule.reminderEnabled) {
                    val remText = if (schedule.reminderMinutesBefore == 0) "سر وقت" else "${NumberFormatHelper.toPersianDigits(schedule.reminderMinutesBefore)} دقیقه قبل"
                    AssistChip(
                        onClick = {},
                        label = { Text("هشدار: $remText", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary) }
                    )
                }
            }

            // Irrigator Person (مسئول آبیاری)
            if (irrigator != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                            Text(
                                text = "مسئول آبیاری: ${irrigator.name}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (irrigator.phone.isNotBlank()) {
                            Text(
                                text = NumberFormatHelper.toPersianDigits(irrigator.phone),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Note if present
            if (schedule.note.isNotBlank()) {
                Text(
                    text = "نکات: ${schedule.note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // SMS Sent Indicator if sent
            if (schedule.smsSent) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ColorSuccessGreen, modifier = Modifier.size(14.dp))
                    Text(
                        text = "پیامک اطلاع‌رسانی ارسال شده است",
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorSuccessGreen
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Action Buttons Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Primary Action: Send SMS button
                FilledTonalButton(
                    onClick = onSendSms,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("send_sms_btn_${schedule.id}")
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ارسال پیامک به آبیار", style = MaterialTheme.typography.labelMedium)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Done toggle
                    IconButton(onClick = onToggleDone, modifier = Modifier.testTag("toggle_done_irrigation_${schedule.id}")) {
                        Icon(
                            imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (isDone) "تکمیل شده" else "علامت‌گذاری به عنوان انجام شده",
                            tint = if (isDone) ColorSuccessGreen else MaterialTheme.colorScheme.outline
                        )
                    }

                    // Edit
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "ویرایش", modifier = Modifier.size(18.dp))
                    }

                    // Delete
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", modifier = Modifier.size(18.dp), tint = ColorDangerRed)
                    }
                }
            }
        }
    }
}
