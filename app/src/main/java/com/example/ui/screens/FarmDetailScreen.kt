package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import com.example.lib.NumberFormatHelper
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.ConfirmDialog
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

    var showEditFarmDialog by remember { mutableStateOf(false) }
    var showDeleteFarmDialog by remember { mutableStateOf(false) }
    var showCreatePlotDialog by remember { mutableStateOf(false) }
    var plotToEdit by remember { mutableStateOf<PlotEntity?>(null) }
    var plotToDelete by remember { mutableStateOf<PlotEntity?>(null) }
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
                        modifier = Modifier.testTag("edit_farm_btn")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "ویرایش")
                    }
                    IconButton(
                        onClick = { showDeleteFarmDialog = true },
                        modifier = Modifier.testTag("delete_farm_btn")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = ColorDangerRed)
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
            // --- FARM SUMMARY CARD ---
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "اطلاعات مزرعه",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { viewModel.toggleFarmActive(farm) }) {
                                Text(if (farm.active) "غیرفعال کردن" else "فعال کردن")
                            }
                        }

                        val areaDisplay = NumberFormatHelper.formatArea(farm.areaM2)
                        if (areaDisplay.isNotEmpty()) {
                            Text(
                                text = "مساحت: $areaDisplay",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        val workTypeDisplay = computeFarmWorkTypeDisplay(allFarmPlots)
                        Text(
                            text = "نوع کار: $workTypeDisplay",
                            style = MaterialTheme.typography.bodyMedium
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
                    PlotItemCard(
                        plot = plot,
                        onEdit = { plotToEdit = plot },
                        onToggleActive = { viewModel.togglePlotActive(plot) },
                        onDelete = { plotToDelete = plot }
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
                                            PlotItemCard(
                                                plot = plot,
                                                onEdit = { plotToEdit = plot },
                                                onToggleActive = { viewModel.togglePlotActive(plot) },
                                                onDelete = { plotToDelete = plot }
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
        val plotsCount = realPlots.size
        val message = if (plotsCount > 0) {
            val countFa = NumberFormatHelper.toPersianDigits(plotsCount)
            "این مزرعه $countFa قطعه دارد که همه حذف می‌شوند. آیا مطمئنید؟"
        } else {
            "آیا از حذف این مزرعه مطمئن هستید؟"
        }

        ConfirmDialog(
            title = "حذف مزرعه",
            message = message,
            confirmText = "حذف",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteFarm(farm.id) { deleted ->
                    if (deleted) {
                        onNavigateBack()
                    } else {
                        alertMessage = "این مزرعه دارای ثبت است و نمی‌توان آن را حذف کرد؛ به جای آن غیرفعال شد."
                    }
                }
            },
            onDismiss = { showDeleteFarmDialog = false }
        )
    }

    // Create Plot Dialog
    if (showCreatePlotDialog) {
        PlotFormDialog(
            farmDefaultWorkType = farmWorkTypeDefault(allFarmPlots),
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
                viewModel.deletePlot(plotToDelete!!) { deleted ->
                    if (!deleted) {
                        alertMessage = "این قطعه دارای ثبت است و فقط غیرفعال شد."
                    }
                    plotToDelete = null
                }
            },
            onDismiss = { plotToDelete = null }
        )
    }

    // Info Alert
    if (alertMessage != null) {
        AlertDialog(
            onDismissRequest = { alertMessage = null },
            title = { Text("توجه") },
            text = { Text(alertMessage!!) },
            confirmButton = {
                Button(onClick = { alertMessage = null }) {
                    Text("متوجه شدم")
                }
            }
        )
    }
}

private fun farmWorkTypeDefault(plots: List<PlotEntity>): String {
    val defaultPlot = plots.firstOrNull { it.isDefault }
    return defaultPlot?.workType ?: "zeraat"
}

@Composable
fun PlotItemCard(
    plot: PlotEntity,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
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
            }

            Row {
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
    var note by remember { mutableStateOf(initialPlot?.note ?: "") }

    val bothTypesActive = isZeraatActive && isNahalestanActive
    val effectiveWorkType = if (bothTypesActive) workType else if (isZeraatActive) "zeraat" else "nahalestan"

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

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیحات") },
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

                    onSave(name, effectiveWorkType, areaM2, note)
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
