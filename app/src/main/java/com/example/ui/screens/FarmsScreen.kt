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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmsScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToFarmDetail: (farmId: String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showInactiveSection by remember { mutableStateOf(false) }

    val activeFarms = uiState.farms.filter { it.active }
    val inactiveFarms = uiState.farms.filter { !it.active }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مزرعه‌ها", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("farms_back_btn")
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "بازگشت")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("مزرعه تازه") },
                modifier = Modifier.testTag("create_farm_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (activeFarms.isEmpty() && inactiveFarms.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp)
                    ) {
                        Text(
                            text = "هنوز مزرعه‌ای ثبت نشده است.\nبرای شروع دکمه‌ی «مزرعه تازه» را بزنید.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Active Farms List
            items(activeFarms, key = { it.id }) { farm ->
                val farmPlots = uiState.plots.filter { it.farmId == farm.id }
                FarmCard(
                    farm = farm,
                    plots = farmPlots,
                    onClick = { onNavigateToFarmDetail(farm.id) }
                )
            }

            // Collapsible Inactive Farms
            if (inactiveFarms.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
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
                                    .clickable { showInactiveSection = !showInactiveSection }
                                    .padding(16.dp)
                            ) {
                                val countFa = NumberFormatHelper.toPersianDigits(inactiveFarms.size)
                                Text(
                                    text = "غیرفعال‌ها ($countFa)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (showInactiveSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }

                            AnimatedVisibility(visible = showInactiveSection) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    inactiveFarms.forEach { farm ->
                                        val farmPlots = uiState.plots.filter { it.farmId == farm.id }
                                        FarmCard(
                                            farm = farm,
                                            plots = farmPlots,
                                            onClick = { onNavigateToFarmDetail(farm.id) }
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

    if (showCreateDialog) {
        FarmFormDialog(
            isZeraatActive = uiState.isZeraatActive,
            isNahalestanActive = uiState.isNahalestanActive,
            onDismiss = { showCreateDialog = false },
            onSave = { name, workType, areaM2, ownership, waterSource, note ->
                viewModel.createFarm(name, workType, areaM2, ownership, waterSource, note) {
                    showCreateDialog = false
                }
            }
        )
    }
}

@Composable
fun FarmCard(
    farm: FarmEntity,
    plots: List<PlotEntity>,
    onClick: () -> Unit
) {
    val workTypeDisplay = computeFarmWorkTypeDisplay(plots)
    val areaDisplay = NumberFormatHelper.formatArea(farm.areaM2)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (farm.active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (farm.active) 2.dp else 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("farm_card_${farm.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = farm.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                AssistChip(
                    onClick = {},
                    label = { Text(workTypeDisplay, style = MaterialTheme.typography.labelSmall) }
                )
            }

            if (areaDisplay.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SquareFoot,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = areaDisplay,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (farm.ownership != null || farm.waterSource != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    farm.ownership?.let {
                        val label = when (it) {
                            "owned" -> "ملکی"
                            "rented" -> "اجاره‌ای"
                            "mixed" -> "ترکیبی"
                            else -> it
                        }
                        SuggestionChip(onClick = {}, label = { Text(label) })
                    }
                    farm.waterSource?.let {
                        val label = when (it) {
                            "well" -> "چاه"
                            "canal" -> "نوبت کانال"
                            "both" -> "هر دو"
                            else -> it
                        }
                        SuggestionChip(onClick = {}, label = { Text(label) })
                    }
                }
            }
        }
    }
}

/**
 * Computes work type text to display on farm card (Decision 160):
 * - If no real plots: type of default plot.
 * - If real plots exist: types of active plots. If both: "زراعت و نهالستان".
 */
fun computeFarmWorkTypeDisplay(plots: List<PlotEntity>): String {
    val realPlots = plots.filter { !it.isDefault && it.active }
    return if (realPlots.isEmpty()) {
        val defaultPlot = plots.firstOrNull { it.isDefault }
        when (defaultPlot?.workType) {
            "nahalestan" -> "نهالستان"
            else -> "زراعت"
        }
    } else {
        val hasZeraat = realPlots.any { it.workType == "zeraat" }
        val hasNahalestan = realPlots.any { it.workType == "nahalestan" }
        when {
            hasZeraat && hasNahalestan -> "زراعت و نهالستان"
            hasNahalestan -> "نهالستان"
            else -> "زراعت"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmFormDialog(
    initialFarm: FarmEntity? = null,
    isZeraatActive: Boolean,
    isNahalestanActive: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, workType: String, areaM2: Double?, ownership: String?, waterSource: String?, note: String) -> Unit
) {
    var name by remember { mutableStateOf(initialFarm?.name ?: "") }
    var selectedWorkType by remember {
        mutableStateOf(
            if (isZeraatActive) "zeraat" else "nahalestan"
        )
    }

    var areaInput by remember {
        mutableStateOf(
            if (initialFarm?.areaM2 != null) {
                // If >= 10,000 show as hectares by default
                if (initialFarm.areaM2 >= 10000.0) {
                    (initialFarm.areaM2 / 10000.0).toString()
                } else {
                    initialFarm.areaM2.toLong().toString()
                }
            } else ""
        )
    }
    var areaUnit by remember {
        mutableStateOf(
            if (initialFarm?.areaM2 != null && initialFarm.areaM2 >= 10000.0) "hectare" else "m2"
        )
    }

    var ownership by remember { mutableStateOf(initialFarm?.ownership) }
    var waterSource by remember { mutableStateOf(initialFarm?.waterSource) }
    var note by remember { mutableStateOf(initialFarm?.note ?: "") }

    val bothTypesActive = isZeraatActive && isNahalestanActive
    val effectiveWorkType = if (bothTypesActive) selectedWorkType else if (isZeraatActive) "zeraat" else "nahalestan"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialFarm == null) "ساختن مزرعه تازه" else "ویرایش مزرعه",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Name
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("نام مزرعه *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("farm_name_input")
                    )
                }

                // Work Type (only shown if both are active)
                if (bothTypesActive) {
                    item {
                        Column {
                            Text("نوع کار مزرعه:", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = selectedWorkType == "zeraat",
                                    onClick = { selectedWorkType = "zeraat" },
                                    label = { Text("زراعت") },
                                    modifier = Modifier.testTag("farm_type_zeraat_chip")
                                )
                                FilterChip(
                                    selected = selectedWorkType == "nahalestan",
                                    onClick = { selectedWorkType = "nahalestan" },
                                    label = { Text("نهالستان") },
                                    modifier = Modifier.testTag("farm_type_nahalestan_chip")
                                )
                            }
                        }
                    }
                }

                // Area Input & Unit
                item {
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
                            modifier = Modifier
                                .weight(1f)
                                .testTag("farm_area_input")
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
                }

                // Ownership Chips
                item {
                    Column {
                        Text("مالکیت:", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("owned" to "ملکی", "rented" to "اجاره‌ای", "mixed" to "ترکیبی").forEach { (key, label) ->
                                FilterChip(
                                    selected = ownership == key,
                                    onClick = { ownership = if (ownership == key) null else key },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }

                // Water Source Chips
                item {
                    Column {
                        Text("منبع آب:", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("well" to "چاه", "canal" to "نوبت کانال", "both" to "هر دو").forEach { (key, label) ->
                                FilterChip(
                                    selected = waterSource == key,
                                    onClick = { waterSource = if (waterSource == key) null else key },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }

                // Notes
                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("توضیحات") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedArea = NumberFormatHelper.parsePersianDouble(areaInput)
                    val areaM2 = if (parsedArea != null) {
                        if (areaUnit == "hectare") parsedArea * 10000.0 else parsedArea
                    } else null

                    onSave(name, effectiveWorkType, areaM2, ownership, waterSource, note)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_farm_btn")
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
