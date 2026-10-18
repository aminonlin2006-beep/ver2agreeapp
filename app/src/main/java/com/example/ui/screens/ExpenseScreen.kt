package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.entity.ExpenseEntity
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.ExpenseFormDialog
import com.example.ui.theme.ColorDangerRed
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<ExpenseEntity?>(null) }
    var expenseToDelete by remember { mutableStateOf<ExpenseEntity?>(null) }
    var selectedTab by remember { mutableStateOf(0) } // 0: ریز هزینه‌ها, 1: تفکیک مزرعه و قطعه

    // Jalali Month Filters
    val currentJalali = remember { JalaliDateHelper.fromGregorian(LocalDate.now()) }
    val thisMonthKey = remember(currentJalali) { currentJalali.monthKey }
    var selectedMonthFilter by remember { mutableStateOf("this_month") } // "all", "this_month", or specific "YYYY-MM"

    // Active (non-deleted) expenses
    val activeExpenses = remember(uiState.expenses) {
        uiState.expenses.filter { !it.deleted }.sortedByDescending { it.date }
    }

    // Filtered expenses based on selected month
    val filteredExpenses = remember(activeExpenses, selectedMonthFilter, thisMonthKey) {
        when (selectedMonthFilter) {
            "all" -> activeExpenses
            "this_month" -> activeExpenses.filter {
                try {
                    JalaliDateHelper.fromIsoDate(it.date).monthKey == thisMonthKey
                } catch (_: Exception) { true }
            }
            else -> activeExpenses.filter {
                try {
                    JalaliDateHelper.fromIsoDate(it.date).monthKey == selectedMonthFilter
                } catch (_: Exception) { true }
            }
        }
    }

    val totalAmount = remember(filteredExpenses) {
        filteredExpenses.sumOf { it.amount }
    }

    val expenseTypesMap = remember(uiState.expenseTypes) {
        uiState.expenseTypes.associateBy { it.id }
    }
    val farmsMap = remember(uiState.farms) {
        uiState.farms.associateBy { it.id }
    }
    val plotsMap = remember(uiState.plots) {
        uiState.plots.associateBy { it.id }
    }
    val personsMap = remember(uiState.persons) {
        uiState.persons.associateBy { it.id }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("هزینه‌ها", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("expenses_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("هزینه تازه") },
                modifier = Modifier.testTag("add_expense_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // --- TABS (ریز هزینه‌ها vs تفکیک مزرعه و قطعه) ---
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth().testTag("expense_tab_row")
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("ریز هزینه‌ها", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("expense_list_tab")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("تفکیک مزرعه و قطعه", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.PieChart, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("expense_breakdown_tab")
                )
            }

            if (selectedTab == 0) {
                // ==================== TAB 0: ریز هزینه‌ها ====================
                // --- SUMMARY CARD ---
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (selectedMonthFilter == "this_month") "مجموع هزینه‌های این ماه" else "مجموع هزینه‌ها",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = NumberFormatHelper.formatTomanFull(totalAmount),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = "${NumberFormatHelper.toPersianDigits(filteredExpenses.size)} ثبت",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // --- MONTH FILTER CHIPS ---
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedMonthFilter == "this_month",
                            onClick = { selectedMonthFilter = "this_month" },
                            label = { Text("این ماه (${currentJalali.monthName})") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedMonthFilter == "all",
                            onClick = { selectedMonthFilter = "all" },
                            label = { Text("همه‌ی ماه‌ها") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // --- EXPENSES LIST ---
                if (filteredExpenses.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Text(
                                text = "هزینه‌ای برای این دوره ثبت نشده است.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "برای افزودن، دکمه‌ی «هزینه تازه» را لمس کنید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredExpenses, key = { it.id }) { expense ->
                            val typeName = expenseTypesMap[expense.typeId]?.name ?: "هزینه متفرقه"
                            val farmName = expense.farmId?.let { farmsMap[it]?.name }
                            val plotName = expense.plotId?.let { plotsMap[it]?.name }
                            val personName = expense.counterpartyId?.let { personsMap[it]?.name }

                            val jalali = remember(expense.date) {
                                try {
                                    JalaliDateHelper.fromIsoDate(expense.date)
                                } catch (_: Exception) { null }
                            }

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expenseToEdit = expense }
                                    .testTag("expense_item_${expense.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = typeName,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }

                                            if (expense.payMode == "credit") {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.errorContainer
                                                ) {
                                                    Text(
                                                        text = "نسیه / بدهی",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = NumberFormatHelper.formatTomanFull(expense.amount),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Details subtitle (Date + Farm/Plot or Counterparty)
                                        val dateStr = jalali?.toDisplayShort() ?: expense.date
                                        val locationOrPerson = when {
                                            farmName != null && plotName != null -> "$farmName - $plotName"
                                            farmName != null -> farmName
                                            personName != null -> "طرف‌حساب: $personName"
                                            else -> null
                                        }

                                        val subtitle = if (locationOrPerson != null) "$dateStr • $locationOrPerson" else dateStr
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )

                                        if (expense.note.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = expense.note,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { expenseToEdit = expense },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "ویرایش",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { expenseToDelete = expense },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "حذف",
                                                tint = ColorDangerRed,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ==================== TAB 1: تفکیک مزرعه و قطعه ====================
                FarmPlotExpenseBreakdownView(
                    expenses = activeExpenses,
                    farmsMap = farmsMap,
                    plotsMap = plotsMap,
                    expenseTypesMap = expenseTypesMap
                )
            }
        }
    }

    // --- CREATE EXPENSE DIALOG ---
    if (showAddDialog) {
        ExpenseFormDialog(
            initialExpense = null,
            uiState = uiState,
            onDismiss = { showAddDialog = false },
            onSave = { newExpense ->
                viewModel.createExpense(newExpense)
                showAddDialog = false
            }
        )
    }

    // --- EDIT EXPENSE DIALOG ---
    if (expenseToEdit != null) {
        ExpenseFormDialog(
            initialExpense = expenseToEdit,
            uiState = uiState,
            onDismiss = { expenseToEdit = null },
            onSave = { updatedExpense ->
                viewModel.updateExpense(updatedExpense)
                expenseToEdit = null
            }
        )
    }

    // --- DELETE CONFIRMATION DIALOG ---
    if (expenseToDelete != null) {
        ConfirmDialog(
            title = "حذف هزینه",
            message = "آیا از حذف این هزینه به مبلغ «${NumberFormatHelper.formatTomanFull(expenseToDelete!!.amount)}» مطمئن هستید؟",
            confirmText = "حذف شود",
            cancelText = "انصراف",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteExpense(expenseToDelete!!.id)
                expenseToDelete = null
            },
            onDismiss = { expenseToDelete = null }
        )
    }
}

@Composable
fun FarmPlotExpenseBreakdownView(
    expenses: List<ExpenseEntity>,
    farmsMap: Map<String, com.example.data.entity.FarmEntity>,
    plotsMap: Map<String, com.example.data.entity.PlotEntity>,
    expenseTypesMap: Map<String, com.example.data.entity.ExpenseTypeEntity>
) {
    val totalAll = remember(expenses) { expenses.sumOf { it.amount } }

    // Group expenses by farmId
    val farmGroups = remember(expenses) {
        expenses.groupBy { it.farmId }
    }

    if (expenses.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "هنوز هیچ هزینه‌ای ثبت نشده است.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // --- OVERVIEW KPI CARD ---
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "گزارش تجمیعی کل هزینه‌های کشاورزی",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = NumberFormatHelper.formatTomanFull(totalAll),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val farmsCountFa = NumberFormatHelper.toPersianDigits(farmGroups.keys.filterNotNull().size)
                    Text(
                        text = "تفکیک شده در $farmsCountFa مزرعه و قطعه‌های تحت کشت",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // --- FARMS BREAKDOWN ---
        // Sort farms by total amount descending
        val sortedFarms = farmGroups.entries.sortedByDescending { it.value.sumOf { e -> e.amount } }

        items(sortedFarms, key = { it.key ?: "unassigned" }) { (farmId, farmExpenses) ->
            val farmName = farmId?.let { farmsMap[it]?.name } ?: "هزینه‌های عمومی و دفتری (بدون مزرعه مشخص)"
            val farmTotal = farmExpenses.sumOf { it.amount }
            val farmPercentage = if (totalAll > 0) (farmTotal.toFloat() / totalAll.toFloat()) else 0f

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Farm Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (farmId != null) Icons.Default.Agriculture else Icons.Default.Business,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = farmName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val percentFa = NumberFormatHelper.toPersianDigits((farmPercentage * 100).toInt())
                                Text(
                                    text = "$percentFa٪ از کل هزینه‌ها",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = NumberFormatHelper.formatTomanShort(farmTotal),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = NumberFormatHelper.formatTomanFull(farmTotal),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { farmPercentage },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Group by plot
                    val plotGroups = farmExpenses.groupBy { it.plotId }
                    Text(
                        text = "تفکیک قطعه‌ها:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    plotGroups.forEach { (plotId, pExpenses) ->
                        val plotName = plotId?.let { plotsMap[it]?.name } ?: "عمومی مزرعه"
                        val plotTotal = pExpenses.sumOf { it.amount }
                        val plotShare = if (farmTotal > 0) (plotTotal.toFloat() / farmTotal.toFloat()) else 0f

                        // Top expense types for this plot
                        val topTypes = pExpenses.groupBy { it.typeId }
                            .mapValues { (_, exps) -> exps.sumOf { it.amount } }
                            .entries.sortedByDescending { it.value }
                            .take(2)
                            .mapNotNull { expenseTypesMap[it.key]?.name }

                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Square,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = plotName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = NumberFormatHelper.formatTomanShort(plotTotal),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (topTypes.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                                ) {
                                    topTypes.forEach { type ->
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = type,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
}
