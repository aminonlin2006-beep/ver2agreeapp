package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.entity.FarmEntity
import com.example.data.entity.PlotEntity
import com.example.lib.NurseryHelper
import com.example.lib.NumberFormatHelper
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.IrrigationFormDialog
import com.example.ui.theme.ColorDangerRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmDetailScreen(
    farmId: String,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val farm = uiState.farms.firstOrNull { it.id == farmId }
    val allFarmPlots = uiState.plots.filter { it.farmId == farmId }
    val realPlots = allFarmPlots.filter { !it.isDefault }
    val activeRealPlots = realPlots.filter { it.active }
    val inactiveRealPlots = realPlots.filter { !it.active }

    // Expenses calculation for this farm and plots
    val farmExpenses = remember(uiState.expenses, farmId) {
        uiState.expenses.filter { it.farmId == farmId && !it.deleted }
    }
    val totalFarmCost = remember(farmExpenses) {
        farmExpenses.sumOf { it.amount }
    }
    val plotExpensesMap = remember(farmExpenses) {
        farmExpenses.filter { it.plotId != null }.groupBy { it.plotId!! }
            .mapValues { (_, exps) -> exps.sumOf { it.amount } }
    }

    var showEditFarmDialog by remember { mutableStateOf(false) }
    var showDeleteFarmDialog by remember { mutableStateOf(false) }
    var showCreatePlotDialog by remember { mutableStateOf(false) }
    var plotToEdit by remember { mutableStateOf<PlotEntity?>(null) }
    var plotToDelete by remember { mutableStateOf<PlotEntity?>(null) }
    var plotForIrrigation by remember { mutableStateOf<PlotEntity?>(null) }
    var showInactivePlots by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }

    if (farm == null) {
        LaunchedEffect(Unit) { onNavigateBack() }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(farm.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("farm_detail_back_btn")
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showEditFarmDialog = true },
                        modifier = Modifier.testTag("farm_detail_edit_btn")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "ویرایش مزرعه")
                    }
                    IconButton(
                        onClick = { showDeleteFarmDialog = true },
                        modifier = Modifier.testTag("farm_detail_delete_btn")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف مزرعه", tint = ColorDangerRed)
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
            // --- FARM INFO CARD ---
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("farm_info_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = farm.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            val statusText = if (farm.active) "فعال" else "غیرفعال"
                            val statusColor = if (farm.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            SuggestionChip(
                                onClick = {},
                                label = { Text(statusText, color = statusColor) }
                            )
                        }

                        val areaDisplay = NumberFormatHelper.formatArea(farm.areaM2)
                        if (areaDisplay.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "مساحت: $areaDisplay",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        val workTypeDisplay = computeFarmWorkTypeDisplay(allFarmPlots)
                        Text(
                            text = "نوع کار: $workTypeDisplay",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )

                        if (farm.ownership != null) {
                            val label = when (farm.ownership) {
                                "owned" -> "ملکی"
                                "rented" -> "اجاره‌ای"
                                "mixed" -> "ترکیبی"
                                else -> farm.ownership
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "مالکیت: $label", style = MaterialTheme.typography.bodyMedium)
                        }

                        if (farm.waterSource != null) {
                            val label = when (farm.waterSource) {
                                "well" -> "چاه"
                                "canal" -> "نوبت کانال"
                                "both" -> "هر دو"
                                else -> farm.waterSource
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "منبع آب: $label", style = MaterialTheme.typography.bodyMedium)
                        }

                        if (farm.note.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "توضیح: ${farm.note}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // --- FINANCIAL / EXPENSES SUMMARY CARD ---
            if (totalFarmCost > 0) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("farm_expenses_summary_card")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                        RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "مجموع هزینه‌های ثبت شده برای این مزرعه",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "${NumberFormatHelper.formatTomanFull(totalFarmCost)} (${NumberFormatHelper.formatTomanShort(totalFarmCost)})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // --- PLOTS HEADER ---
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val countFa = NumberFormatHelper.toPersianDigits(realPlots.size)
                    Text(
                        text = "قطعه‌ها ($countFa)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { showCreatePlotDialog = true },
                        modifier = Modifier.testTag("add_plot_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("افزودن قطعه")
                    }
                }
            }

            // --- ACTIVE REAL PLOTS LIST ---
            if (activeRealPlots.isEmpty() && inactiveRealPlots.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Text(
                                text = "این مزرعه هنوز قطعه‌ی جداگانه ندارد.\nدر صورت تمایل، می‌توانید قطعه‌های مزرعه را با دکمه‌ی «افزودن قطعه» اضافه کنید.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(activeRealPlots, key = { it.id }) { plot ->
                    val plotCost = plotExpensesMap[plot.id] ?: 0L
                    PlotItemCard(
                        plot = plot,
                        plotCost = plotCost,
                        onEdit = { plotToEdit = plot },
                        onToggleActive = { viewModel.togglePlotActive(plot) },
                        onDelete = { plotToDelete = plot },
                        onScheduleIrrigation = { plotForIrrigation = plot }
                    )
                }

                // Collapsible Inactive Plots
                if (inactiveRealPlots.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showInactivePlots = !showInactivePlots }
                                        .padding(16.dp)
                                ) {
                                    val countFa = NumberFormatHelper.toPersianDigits(inactiveRealPlots.size)
                                    Text(
                                        text = "قطعه‌های غیرفعال ($countFa)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = if (showInactivePlots) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                }

                                AnimatedVisibility(visible = showInactivePlots) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        inactiveRealPlots.forEach { plot ->
                                            val plotCost = plotExpensesMap[plot.id] ?: 0L
                                            PlotItemCard(
                                                plot = plot,
                                                plotCost = plotCost,
                                                onEdit = { plotToEdit = plot },
                                                onToggleActive = { viewModel.togglePlotActive(plot) },
                                                onDelete = { plotToDelete = plot },
                                                onScheduleIrrigation = { plotForIrrigation = plot }
                                            )
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

    // --- DIALOGS ---
    // Edit Farm Dialog
    if (showEditFarmDialog) {
        val hasRealPlots = realPlots.isNotEmpty()
        FarmFormDialog(
            initialFarm = farm,
            isZeraatActive = uiState.isZeraatActive,
            isNahalestanActive = uiState.isNahalestanActive,
            onDismiss = { showEditFarmDialog = false },
            onSave = { name, workType, areaM2, ownership, waterSource, note ->
                viewModel.updateFarm(
                    farm.copy(
                        name = name,
                        areaM2 = areaM2,
                        ownership = ownership,
                        waterSource = waterSource,
                        note = note
                    ),
                    newWorkType = if (!hasRealPlots) workType else null
                ) {
                    showEditFarmDialog = false
                }
            }
        )
    }

    // Delete Farm Dialog
    if (showDeleteFarmDialog) {
        ConfirmDialog(
            title = "حذف مزرعه",
            message = "آیا از حذف مزرعه‌ی «${farm.name}» مطمئن هستید؟ با حذف مزرعه، تمام قطعه‌های آن نیز حذف خواهند شد.",
            confirmText = "حذف",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteFarm(farm.id) {
                    showDeleteFarmDialog = false
                    onNavigateBack()
                }
            },
            onDismiss = { showDeleteFarmDialog = false }
        )
    }

    // Create Plot Dialog
    if (showCreatePlotDialog) {
        PlotFormDialog(
            initialPlot = null,
            farmDefaultWorkType = getFarmDefaultWorkType(allFarmPlots),
            isZeraatActive = uiState.isZeraatActive,
            isNahalestanActive = uiState.isNahalestanActive,
            onDismiss = { showCreatePlotDialog = false },
            onSave = { name, workType, areaM2, note ->
                viewModel.createPlot(farm.id, name, workType, areaM2, note) {
                    showCreatePlotDialog = false
                }
            }
        )
    }

    // Edit Plot Dialog
    if (plotToEdit != null) {
        PlotFormDialog(
            initialPlot = plotToEdit,
            farmDefaultWorkType = plotToEdit!!.workType,
            isZeraatActive = uiState.isZeraatActive,
            isNahalestanActive = uiState.isNahalestanActive,
            onDismiss = { plotToEdit = null },
            onSave = { name, workType, areaM2, note ->
                viewModel.updatePlot(
                    plotToEdit!!.copy(
                        name = name,
                        workType = workType,
                        areaM2 = areaM2,
                        note = note
                    )
                ) {
                    plotToEdit = null
                }
            }
        )
    }

    // Delete Plot Dialog
    if (plotToDelete != null) {
        ConfirmDialog(
            title = "حذف قطعه",
            message = "آیا از حذف قطعه‌ی «${plotToDelete!!.name}» مطمئن هستید؟",
            confirmText = "حذف",
            isDestructive = true,
            onConfirm = {
                viewModel.deletePlot(plotToDelete!!) {
                    plotToDelete = null
                }
            },
            onDismiss = { plotToDelete = null }
        )
    }

    // Alert Message Dialog
    if (alertMessage != null) {
        AlertDialog(
            onDismissRequest = { alertMessage = null },
            title = { Text("توجه") },
            text = { Text(alertMessage!!) },
            confirmButton = {
                Button(onClick = { alertMessage = null }) {
                    Text("باشه")
                }
            }
        )
    }

    if (plotForIrrigation != null) {
        IrrigationFormDialog(
            farms = uiState.farms,
            plots = uiState.plots,
            persons = uiState.persons,
            preSelectedFarmId = farmId,
            preSelectedPlotId = plotForIrrigation!!.id,
            onDismiss = { plotForIrrigation = null },
            onSave = { fId, pId, irrId, dateIso, time, dur, src, rem, remMin, note ->
                viewModel.createIrrigationSchedule(
                    farmId = fId,
                    plotId = pId,
                    irrigatorPersonId = irrId,
                    date = dateIso,
                    time = time,
                    durationHours = dur,
                    waterSource = src,
                    reminderEnabled = rem,
                    reminderMinutesBefore = remMin,
                    note = note
                ) {
                    plotForIrrigation = null
                }
            }
        )
    }
}

private fun getFarmDefaultWorkType(plots: List<PlotEntity>): String {
    val defaultPlot = plots.firstOrNull { it.isDefault }
    return defaultPlot?.workType ?: "zeraat"
}

@Composable
fun PlotItemCard(
    plot: PlotEntity,
    plotCost: Long = 0L,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit,
    onScheduleIrrigation: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (plot.active) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth().testTag("plot_card_${plot.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plot.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val typeLabel = if (plot.workType == "nahalestan") "نهالستان" else "زراعت"
                val areaDisplay = NumberFormatHelper.formatArea(plot.areaM2)
                val subtitle = if (areaDisplay.isNotEmpty()) "$typeLabel — $areaDisplay" else typeLabel
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                // Cost Chip if expenses exist
                if (plotCost > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text("هزینه‌ها: ${NumberFormatHelper.formatTomanShort(plotCost)}", style = MaterialTheme.typography.labelSmall) },
                        icon = { Icon(Icons.Default.MonetizationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary) }
                    )
                }

                // Nursery badges if plot is nahalestan
                if (plot.workType == "nahalestan") {
                    val nurseryData = remember(plot.note) { NurseryHelper.parse(plot.note) }
                    val hasNurseryData = nurseryData.saplingCount != null || nurseryData.scionVariety.isNotBlank() || nurseryData.graftYear.isNotBlank()
                    if (hasNurseryData) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (nurseryData.saplingCount != null) {
                                val saplingFa = NumberFormatHelper.toPersianDigits(nurseryData.saplingCount)
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("$saplingFa اصله نهال", style = MaterialTheme.typography.labelSmall) },
                                    icon = { Icon(Icons.Default.Park, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary) }
                                )
                            }
                            if (nurseryData.scionVariety.isNotBlank()) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("رقم: ${nurseryData.scionVariety}", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            if (nurseryData.graftYear.isNotBlank()) {
                                val yearFa = NumberFormatHelper.toPersianDigits(nurseryData.graftYear)
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("سال: $yearFa", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    if (nurseryData.generalNote.isNotBlank()) {
                        Text(
                            text = nurseryData.generalNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                } else if (plot.note.isNotBlank()) {
                    Text(
                        text = plot.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Row {
                IconButton(onClick = onScheduleIrrigation) {
                    Icon(Icons.Default.WaterDrop, contentDescription = "برنامه‌ریزی آبیاری", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "ویرایش", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onToggleActive) {
                    Icon(
                        imageVector = if (plot.active) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (plot.active) "غیرفعال" else "فعال",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = ColorDangerRed, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlotFormDialog(
    initialPlot: PlotEntity? = null,
    farmDefaultWorkType: String,
    isZeraatActive: Boolean,
    isNahalestanActive: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, workType: String, areaM2: Double?, note: String) -> Unit
) {
    var name by remember { mutableStateOf(initialPlot?.name ?: "") }
    var workType by remember { mutableStateOf(initialPlot?.workType ?: farmDefaultWorkType) }
    var areaInput by remember {
        mutableStateOf(
            if (initialPlot?.areaM2 != null) {
                if (initialPlot.areaM2 >= 10000.0) {
                    (initialPlot.areaM2 / 10000.0).toString()
                } else {
                    initialPlot.areaM2.toLong().toString()
                }
            } else ""
        )
    }
    var areaUnit by remember {
        mutableStateOf(
            if (initialPlot?.areaM2 != null && initialPlot.areaM2 >= 10000.0) "hectare" else "m2"
        )
    }

    // Parse initial nursery details
    val initialNurseryData = remember(initialPlot?.note) {
        NurseryHelper.parse(initialPlot?.note ?: "")
    }
    var saplingCountInput by remember {
        mutableStateOf(initialNurseryData.saplingCount?.toString() ?: "")
    }
    var scionVarietyInput by remember {
        mutableStateOf(initialNurseryData.scionVariety)
    }
    var graftYearInput by remember {
        mutableStateOf(initialNurseryData.graftYear)
    }
    var generalNoteInput by remember {
        mutableStateOf(initialNurseryData.generalNote)
    }

    val bothTypesActive = isZeraatActive && isNahalestanActive
    val effectiveWorkType = if (bothTypesActive) workType else if (isZeraatActive) "zeraat" else "nahalestan"
    val isNurseryMode = effectiveWorkType == "nahalestan"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialPlot == null) "افزودن قطعه تازه" else "ویرایش قطعه",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام قطعه *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("plot_name_input")
                )

                if (bothTypesActive) {
                    Column {
                        Text("نوع کار قطعه:", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = workType == "zeraat",
                                onClick = { workType = "zeraat" },
                                label = { Text("زراعت") }
                            )
                            FilterChip(
                                selected = workType == "nahalestan",
                                onClick = { workType = "nahalestan" },
                                label = { Text("نهالستان") }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = areaInput,
                        onValueChange = { areaInput = it },
                        label = { Text("مساحت") },
                        placeholder = { Text("مثلاً ۲٫۵ یا ۲۵۰۰۰") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("plot_area_input")
                    )
                    Row {
                        FilterChip(
                            selected = areaUnit == "hectare",
                            onClick = { areaUnit = "hectare" },
                            label = { Text("هکتار") }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        FilterChip(
                            selected = areaUnit == "m2",
                            onClick = { areaUnit = "m2" },
                            label = { Text("متر مربع") }
                        )
                    }
                }

                // Dedicated Nursery Fields
                if (isNurseryMode) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "اطلاعات اختصاصی نهالستان",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            OutlinedTextField(
                                value = saplingCountInput,
                                onValueChange = { saplingCountInput = it },
                                label = { Text("تعداد اصله نهال") },
                                placeholder = { Text("مثلاً ۱۲۰۰") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("plot_saplings_count_input")
                            )

                            OutlinedTextField(
                                value = scionVarietyInput,
                                onValueChange = { scionVarietyInput = it },
                                label = { Text("نوع پایه / رقم پیوندک") },
                                placeholder = { Text("مثلاً بادام شاهرود دیرگل") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("plot_scion_variety_input")
                            )

                            OutlinedTextField(
                                value = graftYearInput,
                                onValueChange = { graftYearInput = it },
                                label = { Text("سال پیوند یا کاشت") },
                                placeholder = { Text("مثلاً ۱۴۰۲") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("plot_graft_year_input")
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = generalNoteInput,
                    onValueChange = { generalNoteInput = it },
                    label = { Text("توضیحات کلی") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedArea = NumberFormatHelper.parsePersianDouble(areaInput)
                    val areaM2 = if (parsedArea != null) {
                        if (areaUnit == "hectare") parsedArea * 10000.0 else parsedArea
                    } else null

                    val parsedSaplings = NumberFormatHelper.parsePersianLong(saplingCountInput)?.toInt()
                    val finalNote = if (isNurseryMode) {
                        NurseryHelper.format(
                            saplingCount = parsedSaplings,
                            scionVariety = scionVarietyInput,
                            graftYear = graftYearInput,
                            generalNote = generalNoteInput
                        )
                    } else {
                        generalNoteInput
                    }

                    onSave(name, effectiveWorkType, areaM2, finalNote)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_plot_btn")
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
