package com.example.ui.screens

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
import androidx.compose.ui.unit.dp
import com.example.data.entity.TagEntity
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.TagColorPalette
import com.example.ui.theme.ColorDangerRed
import com.example.ui.theme.TAG_PALETTE_HEX

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCreateStageDialog by remember { mutableStateOf(false) }
    var stageForNewActivity by remember { mutableStateOf<TagEntity?>(null) }
    var tagToEdit by remember { mutableStateOf<TagEntity?>(null) }
    var activityToMerge by remember { mutableStateOf<TagEntity?>(null) }
    var tagToDelete by remember { mutableStateOf<TagEntity?>(null) }
    var alertMessage by remember { mutableStateOf<String?>(null) }

    // Group tags into stages and their activities
    val stages = remember(uiState.tags) {
        uiState.tags.filter { it.parentId == null }.sortedBy { it.sortOrder }
    }
    val activitiesByStage = remember(uiState.tags) {
        uiState.tags.filter { it.parentId != null }.groupBy { it.parentId }
    }

    // Keep collapsed state of stages
    val collapsedStageIds = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("برچسب‌ها", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("tags_back_btn")
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    Button(
                        onClick = { showCreateStageDialog = true },
                        modifier = Modifier.testTag("create_stage_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مرحله‌ی تازه")
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
            // Search field
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("جستجوی برچسب...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "پاک کردن")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tags_search_input")
                )
            }

            // Stages list
            items(stages, key = { it.id }) { stage ->
                val activities = activitiesByStage[stage.id] ?: emptyList()
                val isCollapsed = collapsedStageIds.contains(stage.id)

                val filteredActivities = if (searchQuery.isBlank()) {
                    activities
                } else {
                    activities.filter { it.name.contains(searchQuery, ignoreCase = true) }
                }

                val stageMatches = searchQuery.isBlank() || stage.name.contains(searchQuery, ignoreCase = true) || filteredActivities.isNotEmpty()

                if (stageMatches) {
                    StageCard(
                        stage = stage,
                        activities = filteredActivities,
                        isCollapsed = isCollapsed,
                        onToggleCollapse = {
                            if (isCollapsed) collapsedStageIds.remove(stage.id)
                            else collapsedStageIds.add(stage.id)
                        },
                        onAddActivity = { stageForNewActivity = stage },
                        onEditStage = { tagToEdit = stage },
                        onToggleHidden = { viewModel.toggleTagHidden(stage) },
                        onDeleteStage = { tagToDelete = stage },
                        onEditActivity = { tagToEdit = it },
                        onToggleActivityHidden = { viewModel.toggleTagHidden(it) },
                        onMergeActivity = { activityToMerge = it },
                        onDeleteActivity = { tagToDelete = it }
                    )
                }
            }
        }
    }

    // --- CREATE STAGE DIALOG ---
    if (showCreateStageDialog) {
        val nextColor = findNextColor(uiState.tags)
        TagEditDialog(
            title = "ساخت مرحله‌ی تازه",
            nameLabel = "نام مرحله (مثلاً کاشت، آماده‌سازی زمین...)",
            initialColor = nextColor,
            onDismiss = { showCreateStageDialog = false },
            onSave = { name, color ->
                viewModel.createStage(name, color) {
                    showCreateStageDialog = false
                }
            }
        )
    }

    // --- CREATE ACTIVITY DIALOG ---
    if (stageForNewActivity != null) {
        val stage = stageForNewActivity!!
        val nextColor = findNextColor(uiState.tags)
        TagEditDialog(
            title = "فعالیت تازه زیر «${stage.name}»",
            nameLabel = "نام فعالیت (مثلاً وجین، سم‌پاشی...)",
            initialColor = nextColor,
            onDismiss = { stageForNewActivity = null },
            onSave = { name, color ->
                viewModel.createActivity(stage.id, name, color) {
                    stageForNewActivity = null
                }
            }
        )
    }

    // --- EDIT TAG DIALOG ---
    if (tagToEdit != null) {
        val tag = tagToEdit!!
        TagEditDialog(
            title = if (tag.parentId == null) "ویرایش مرحله" else "ویرایش فعالیت",
            nameLabel = "نام برچسب",
            initialName = tag.name,
            initialColor = tag.color,
            onDismiss = { tagToEdit = null },
            onSave = { name, color ->
                viewModel.updateTag(tag, name, color) {
                    tagToEdit = null
                }
            }
        )
    }

    // --- DELETE TAG CONFIRMATION ---
    if (tagToDelete != null) {
        val tag = tagToDelete!!
        val isStage = tag.parentId == null
        val title = if (isStage) "حذف مرحله" else "حذف فعالیت"
        ConfirmDialog(
            title = title,
            message = "آیا از حذف «${tag.name}» مطمئن هستید؟",
            confirmText = "حذف",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteTag(tag) { deleted ->
                    if (!deleted) {
                        alertMessage = if (isStage) {
                            "مرحله‌های آماده یا مرحله‌هایی که فعالیت دارند یا در ثبت‌ها استفاده شده‌اند را نمی‌توان حذف کرد؛ به جای آن می‌توانید آن‌ها را پنهان کنید."
                        } else {
                            "این فعالیت در ثبت‌ها استفاده شده و نمی‌توان آن را حذف کرد؛ به جای آن می‌توانید آن را پنهان یا با فعالیت دیگری یکی کنید."
                        }
                    }
                    tagToDelete = null
                }
            },
            onDismiss = { tagToDelete = null }
        )
    }

    // --- MERGE ACTIVITY DIALOG ---
    if (activityToMerge != null) {
        val sourceActivity = activityToMerge!!
        MergeActivityDialog(
            sourceActivity = sourceActivity,
            allStages = stages,
            allActivities = uiState.tags.filter { it.parentId != null },
            onDismiss = { activityToMerge = null },
            onConfirmMerge = { destTagId ->
                viewModel.mergeActivities(sourceActivity.id, destTagId) { success ->
                    if (success) {
                        activityToMerge = null
                    } else {
                        alertMessage = "خطا در یکی کردن فعالیت‌ها"
                    }
                }
            }
        )
    }

    // Alert
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

private fun findNextColor(tags: List<TagEntity>): String {
    val counts = tags.groupingBy { it.color.uppercase() }.eachCount()
    return TAG_PALETTE_HEX.minByOrNull { counts[it.uppercase()] ?: 0 } ?: TAG_PALETTE_HEX[0]
}

@Composable
fun StageCard(
    stage: TagEntity,
    activities: List<TagEntity>,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onAddActivity: () -> Unit,
    onEditStage: () -> Unit,
    onToggleHidden: () -> Unit,
    onDeleteStage: () -> Unit,
    onEditActivity: (TagEntity) -> Unit,
    onToggleActivityHidden: (TagEntity) -> Unit,
    onMergeActivity: (TagEntity) -> Unit,
    onDeleteActivity: (TagEntity) -> Unit
) {
    val stageColor = try {
        Color(android.graphics.Color.parseColor(stage.color))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (stage.hidden) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (stage.hidden) 0.dp else 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("stage_card_${stage.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Stage Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleCollapse() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(stageColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stage.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (stage.hidden) {
                        Spacer(modifier = Modifier.width(6.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text("پنهان", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row {
                    IconButton(onClick = onAddActivity) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "فعالیت تازه", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onEditStage) {
                        Icon(Icons.Default.Edit, contentDescription = "ویرایش", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onToggleHidden) {
                        Icon(
                            imageVector = if (stage.hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (stage.hidden) "پنهان" else "آشکار",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (stage.builtInKey == null) {
                        IconButton(onClick = onDeleteStage) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = ColorDangerRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Activities under Stage
            AnimatedVisibility(visible = !isCollapsed) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 16.dp)
                ) {
                    if (activities.isEmpty()) {
                        Text(
                            text = "هنوز فعالیتی زیر این مرحله ثبت نشده است.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        activities.forEach { activity ->
                            ActivityRow(
                                activity = activity,
                                onEdit = { onEditActivity(activity) },
                                onToggleHidden = { onToggleActivityHidden(activity) },
                                onMerge = { onMergeActivity(activity) },
                                onDelete = { onDeleteActivity(activity) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityRow(
    activity: TagEntity,
    onEdit: () -> Unit,
    onToggleHidden: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit
) {
    val activityColor = try {
        Color(android.graphics.Color.parseColor(activity.color))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.secondary
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (activity.hidden) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth().testTag("activity_card_${activity.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(activityColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = activity.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (activity.hidden) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(پنهان)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Row {
                IconButton(onClick = onMerge) {
                    Icon(Icons.Default.CallMerge, contentDescription = "یکی کن با...", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "ویرایش", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onToggleHidden) {
                    Icon(
                        imageVector = if (activity.hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (activity.hidden) "پنهان" else "آشکار",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = ColorDangerRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun TagEditDialog(
    title: String,
    nameLabel: String,
    initialName: String = "",
    initialColor: String,
    onDismiss: () -> Unit,
    onSave: (name: String, color: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(nameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("tag_name_input")
                )

                TagColorPalette(
                    selectedHex = color,
                    onSelectColor = { color = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, color) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_tag_btn")
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

@Composable
fun MergeActivityDialog(
    sourceActivity: TagEntity,
    allStages: List<TagEntity>,
    allActivities: List<TagEntity>,
    onDismiss: () -> Unit,
    onConfirmMerge: (destTagId: String) -> Unit
) {
    val stagesMap = remember(allStages) { allStages.associateBy { it.id } }
    val candidateActivities = remember(allActivities, sourceActivity) {
        allActivities.filter { it.id != sourceActivity.id }
    }

    var selectedDestActivity by remember { mutableStateOf<TagEntity?>(null) }
    var showWarningDialog by remember { mutableStateOf(false) }

    val sourceStage = stagesMap[sourceActivity.parentId]

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("یکی کردن فعالیت «${sourceActivity.name}» با...")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "یک فعالیت مقصد انتخاب کنید. تمام ثبت‌های فعالیت قبلی به فعالیت مقصد منتقل شده و فعالیت قبلی حذف خواهد شد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(candidateActivities) { act ->
                        val destStage = stagesMap[act.parentId]
                        val isSelected = selectedDestActivity?.id == act.id

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedDestActivity = act }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(act.name, fontWeight = FontWeight.Bold)
                                    Text("مرحله: ${destStage?.name ?: "-"}", style = MaterialTheme.typography.labelSmall)
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedDestActivity != null) {
                        val destStage = stagesMap[selectedDestActivity!!.parentId]
                        if (sourceStage?.id != destStage?.id) {
                            showWarningDialog = true
                        } else {
                            onConfirmMerge(selectedDestActivity!!.id)
                        }
                    }
                },
                enabled = selectedDestActivity != null,
                modifier = Modifier.testTag("confirm_merge_selection_btn")
            ) {
                Text("ادامه")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )

    // Cross-stage warning (Decision 124)
    if (showWarningDialog && selectedDestActivity != null) {
        val destStage = stagesMap[selectedDestActivity!!.parentId]
        ConfirmDialog(
            title = "هشدار تغییر مرحله",
            message = "ثبت‌های «${sourceActivity.name}» از مرحله‌ی «${sourceStage?.name}» به مرحله‌ی «${destStage?.name}» می‌روند و گزارش‌ها عوض می‌شود. آیا مطمئنید؟",
            confirmText = "تأیید یکی کردن",
            isDestructive = true,
            onConfirm = {
                onConfirmMerge(selectedDestActivity!!.id)
            },
            onDismiss = { showWarningDialog = false }
        )
    }
}
