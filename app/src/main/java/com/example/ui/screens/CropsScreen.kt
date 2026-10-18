package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.entity.CropEntity
import com.example.lib.JalaliDateHelper
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.ConfirmDialog
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropsScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    var showAddCropDialog by remember { mutableStateOf(false) }
    var cropToEdit by remember { mutableStateOf<CropEntity?>(null) }
    var cropToDelete by remember { mutableStateOf<CropEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("کشت‌ها و محصولات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddCropDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("کشت تازه") }
            )
        }
    ) { innerPadding ->
        val activeCrops = remember(uiState.crops) { uiState.crops.filter { !it.deleted } }

        if (activeCrops.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "هنوز کشتی ثبت نشده است.\nبرای افزودن، دکمه‌ی «کشت تازه» را بزنید.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                items(activeCrops, key = { it.id }) { crop ->
                    val plot = uiState.plots.find { it.id == crop.plotId }
                    val plotName = if (plot?.isDefault == true) "کل مزرعه" else (plot?.name ?: "نامشخص")
                    val farmName = uiState.farms.find { it.id == plot?.farmId }?.name ?: ""

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { cropToEdit = crop }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = crop.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (farmName.isNotBlank() || plotName.isNotBlank()) {
                                    Text(
                                        text = "$farmName - $plotName",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                val dateFa = try {
                                    JalaliDateHelper.fromIsoDate(crop.startDate).toDisplayShort()
                                } catch (_: Exception) { crop.startDate }
                                Text(
                                    text = "شروع کشت: $dateFa",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                if (crop.note.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = crop.note,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { cropToEdit = crop }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "ویرایش",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { cropToDelete = crop }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "حذف",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- ADD CROP DIALOG ---
    if (showAddCropDialog) {
        val todayIso = remember { JalaliDateHelper.toIsoDate(LocalDate.now()) }
        var name by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var selectedPlotId by remember { mutableStateOf(uiState.plots.firstOrNull { it.active }?.id ?: "") }
        var plotDropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddCropDialog = false },
            title = { Text("ثبت کشت تازه", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("نام محصول / رقم (مثلاً گندم میهن)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val activePlots = uiState.plots.filter { it.active }
                    if (activePlots.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = plotDropdownExpanded,
                            onExpandedChange = { plotDropdownExpanded = !plotDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val curPlot = activePlots.find { it.id == selectedPlotId }
                            val curPlotName = if (curPlot?.isDefault == true) "کل مزرعه" else (curPlot?.name ?: "انتخاب قطعه")
                            val farm = uiState.farms.find { it.id == curPlot?.farmId }
                            val displayLabel = if (farm != null) "${farm.name} - $curPlotName" else curPlotName

                            OutlinedTextField(
                                value = displayLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("قطعه زمین") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = plotDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = plotDropdownExpanded,
                                onDismissRequest = { plotDropdownExpanded = false }
                            ) {
                                activePlots.forEach { p ->
                                    val fName = uiState.farms.find { it.id == p.farmId }?.name ?: ""
                                    val pName = if (p.isDefault) "کل مزرعه" else p.name
                                    DropdownMenuItem(
                                        text = { Text("$fName - $pName") },
                                        onClick = {
                                            selectedPlotId = p.id
                                            plotDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("یادداشت (اختیاری)") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createCrop(name, selectedPlotId, todayIso, note) {
                            showAddCropDialog = false
                        }
                    },
                    enabled = name.isNotBlank() && selectedPlotId.isNotBlank()
                ) {
                    Text("ذخیره")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddCropDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    // --- EDIT CROP DIALOG ---
    if (cropToEdit != null) {
        val targetCrop = cropToEdit!!
        var editName by remember(targetCrop) { mutableStateOf(targetCrop.name) }
        var editNote by remember(targetCrop) { mutableStateOf(targetCrop.note) }
        var editPlotId by remember(targetCrop) { mutableStateOf(targetCrop.plotId) }
        var editPlotDropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { cropToEdit = null },
            title = { Text("ویرایش کشت", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("نام محصول / رقم") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val activePlots = uiState.plots.filter { it.active }
                    if (activePlots.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = editPlotDropdownExpanded,
                            onExpandedChange = { editPlotDropdownExpanded = !editPlotDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val curPlot = activePlots.find { it.id == editPlotId }
                            val curPlotName = if (curPlot?.isDefault == true) "کل مزرعه" else (curPlot?.name ?: "انتخاب قطعه")
                            val farm = uiState.farms.find { it.id == curPlot?.farmId }
                            val displayLabel = if (farm != null) "${farm.name} - $curPlotName" else curPlotName

                            OutlinedTextField(
                                value = displayLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("قطعه زمین") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = editPlotDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = editPlotDropdownExpanded,
                                onDismissRequest = { editPlotDropdownExpanded = false }
                            ) {
                                activePlots.forEach { p ->
                                    val fName = uiState.farms.find { it.id == p.farmId }?.name ?: ""
                                    val pName = if (p.isDefault) "کل مزرعه" else p.name
                                    DropdownMenuItem(
                                        text = { Text("$fName - $pName") },
                                        onClick = {
                                            editPlotId = p.id
                                            editPlotDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editNote,
                        onValueChange = { editNote = it },
                        label = { Text("یادداشت (اختیاری)") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = targetCrop.copy(
                            name = editName.trim(),
                            plotId = editPlotId,
                            note = editNote.trim(),
                            updatedAt = java.time.Instant.now().toString()
                        )
                        viewModel.updateCrop(updated) {
                            cropToEdit = null
                        }
                    },
                    enabled = editName.isNotBlank() && editPlotId.isNotBlank()
                ) {
                    Text("به‌روزرسانی")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { cropToEdit = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    // --- DELETE CROP CONFIRMATION ---
    if (cropToDelete != null) {
        ConfirmDialog(
            title = "حذف کشت",
            message = "آیا از حذف کشت «${cropToDelete!!.name}» اطمینان دارید؟",
            confirmText = "حذف",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteCrop(cropToDelete!!) {
                    cropToDelete = null
                }
            },
            onDismiss = { cropToDelete = null }
        )
    }
}
