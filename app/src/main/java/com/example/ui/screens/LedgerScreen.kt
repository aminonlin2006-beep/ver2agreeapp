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
import com.example.data.entity.PaymentEntity
import com.example.data.entity.WaitingListEntity
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import com.example.lib.WorkforceCalculator
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.PaymentDialog
import com.example.ui.components.WaitingListDialog
import com.example.ui.theme.ColorDangerRed
import com.example.ui.theme.ColorNeutralGray
import com.example.ui.theme.ColorSuccessGreen
import com.example.ui.theme.ColorWarningOrange

data class AccountListItem(
    val accountType: String, // "person" | "crew"
    val id: String,
    val name: String,
    val subtitle: String?,
    val balance: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAccountDetail: (accountType: String, accountId: String) -> Unit
) {
    BackHandler { onNavigateBack() }
    val context = LocalContext.current

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("حساب‌ها", "پرداخت‌ها", "صف انتظار")

    var searchQuery by remember { mutableStateOf("") }
    var balanceFilter by remember { mutableStateOf("all") } // "all" | "creditor" | "debtor" | "settled"

    val personsMap = remember(uiState.persons) { uiState.persons.associateBy { it.id } }
    val crewsMap = remember(uiState.crews) { uiState.crews.associateBy { it.id } }
    val tagsMap = remember(uiState.tags) { uiState.tags.associateBy { it.id } }

    // Precalculate all account balances
    val allAccounts = remember(
        uiState.persons,
        uiState.crews,
        uiState.attendances,
        uiState.pieceWorks,
        uiState.payments,
        uiState.ledgerEntries,
        uiState.wageRates
    ) {
        val list = mutableListOf<AccountListItem>()

        // Persons
        uiState.persons.forEach { p ->
            val bal = WorkforceCalculator.calculateAccountBalance(
                "person", p.id, uiState.attendances, uiState.pieceWorks,
                uiState.payments, uiState.ledgerEntries, uiState.wageRates,
                crewsMap, tagsMap, personsMap
            )
            list.add(
                AccountListItem(
                    accountType = "person",
                    id = p.id,
                    name = p.name,
                    subtitle = if (p.phone.isNotBlank()) NumberFormatHelper.toPersianDigits(p.phone) else null,
                    balance = bal
                )
            )
        }

        // Crews
        uiState.crews.forEach { c ->
            val bal = WorkforceCalculator.calculateAccountBalance(
                "crew", c.id, uiState.attendances, uiState.pieceWorks,
                uiState.payments, uiState.ledgerEntries, uiState.wageRates,
                crewsMap, tagsMap, personsMap
            )
            val membersCount = uiState.crewMembers.count { it.crewId == c.id && !it.deleted }
            list.add(
                AccountListItem(
                    accountType = "crew",
                    id = c.id,
                    name = c.name,
                    subtitle = if (c.kind == "count") "دسته‌ی عددی" else "${NumberFormatHelper.toPersianDigits(membersCount)} عضو",
                    balance = bal
                )
            )
        }

        list.sortedByDescending { kotlin.math.abs(it.balance) }
    }

    val filteredAccounts = remember(allAccounts, searchQuery, balanceFilter) {
        allAccounts.filter { acc ->
            val matchQuery = searchQuery.isBlank() || acc.name.contains(searchQuery, ignoreCase = true)
            val matchFilter = when (balanceFilter) {
                "creditor" -> acc.balance > 0
                "debtor" -> acc.balance < 0
                "settled" -> acc.balance == 0L
                else -> true
            }
            matchQuery && matchFilter
        }
    }

    val totalClaims = remember(allAccounts) {
        allAccounts.filter { it.balance > 0 }.sumOf { it.balance }
    }
    val totalDebts = remember(allAccounts) {
        allAccounts.filter { it.balance < 0 }.sumOf { kotlin.math.abs(it.balance) }
    }

    // Dialog states
    var showPaymentDialog by remember { mutableStateOf(false) }
    var paymentPrefilledType by remember { mutableStateOf<String?>(null) }
    var paymentPrefilledId by remember { mutableStateOf<String?>(null) }
    var paymentPrefilledAmount by remember { mutableStateOf<Long?>(null) }

    var paymentToEdit by remember { mutableStateOf<PaymentEntity?>(null) }
    var paymentToDelete by remember { mutableStateOf<PaymentEntity?>(null) }

    var showWaitingListDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دفتر حساب و تسویه‌ها", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("ledger_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        },
        floatingActionButton = {
            when (selectedTabIndex) {
                0, 1 -> {
                    ExtendedFloatingActionButton(
                        onClick = {
                            paymentPrefilledType = null
                            paymentPrefilledId = null
                            paymentPrefilledAmount = null
                            paymentToEdit = null
                            showPaymentDialog = true
                        },
                        icon = { Icon(Icons.Default.Payment, contentDescription = null) },
                        text = { Text("پرداخت جدید") },
                        modifier = Modifier.testTag("ledger_add_payment_fab")
                    )
                }
                2 -> {
                    ExtendedFloatingActionButton(
                        onClick = { showWaitingListDialog = true },
                        icon = { Icon(Icons.Default.PostAdd, contentDescription = null) },
                        text = { Text("افزودن به صف") },
                        modifier = Modifier.testTag("ledger_add_waiting_fab")
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
                modifier = Modifier.fillMaxWidth().testTag("ledger_tabs")
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("ledger_tab_$index")
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> {
                    // --- ACCOUNTS TAB ---
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
                    ) {
                        // Summary Banner
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().testTag("ledger_balance_overview_card")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("مجموع طلب‌ها از شما", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        Text(
                                            NumberFormatHelper.formatTomanFull(totalClaims),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorSuccessGreen
                                        )
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("مجموع بدهی‌ها به شما", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        Text(
                                            NumberFormatHelper.formatTomanFull(totalDebts),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorDangerRed
                                        )
                                    }
                                }
                            }
                        }

                        // Search and Filter
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("جستجوی طرف حساب...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("ledger_account_search_input")
                            )
                        }

                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = balanceFilter == "all",
                                        onClick = { balanceFilter = "all" },
                                        label = { Text("همه (${NumberFormatHelper.toPersianDigits(allAccounts.size)})") },
                                        modifier = Modifier.testTag("filter_all_accounts_chip")
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = balanceFilter == "creditor",
                                        onClick = { balanceFilter = "creditor" },
                                        label = { Text("طلبکاران") },
                                        modifier = Modifier.testTag("filter_creditors_chip")
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = balanceFilter == "debtor",
                                        onClick = { balanceFilter = "debtor" },
                                        label = { Text("بدهکاران") },
                                        modifier = Modifier.testTag("filter_debtors_chip")
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = balanceFilter == "settled",
                                        onClick = { balanceFilter = "settled" },
                                        label = { Text("تسویه‌شده") },
                                        modifier = Modifier.testTag("filter_settled_chip")
                                    )
                                }
                            }
                        }

                        // Account Cards
                        if (filteredAccounts.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("حسابی با این شرایط یافت نشد.", color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        } else {
                            items(filteredAccounts) { acc ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToAccountDetail(acc.accountType, acc.id) }
                                        .testTag("account_card_${acc.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(acc.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                AssistChip(
                                                    onClick = {},
                                                    label = {
                                                        Text(
                                                            if (acc.accountType == "person") "شخص" else "دسته",
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                    }
                                                )
                                            }
                                            if (acc.subtitle != null) {
                                                Text(acc.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            val bColor = when {
                                                acc.balance > 0 -> ColorSuccessGreen
                                                acc.balance < 0 -> ColorDangerRed
                                                else -> ColorNeutralGray
                                            }
                                            val bLabel = when {
                                                acc.balance > 0 -> "طلبکار"
                                                acc.balance < 0 -> "بدهکار"
                                                else -> "تسویه"
                                            }

                                            Text(
                                                NumberFormatHelper.formatTomanFull(kotlin.math.abs(acc.balance)),
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = bColor
                                            )
                                            Text(bLabel, style = MaterialTheme.typography.labelSmall, color = bColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // --- PAYMENTS TAB ---
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
                    ) {
                        if (uiState.payments.isEmpty()) {
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
                                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                                        Text("هنوز پرداختی ثبت نشده است.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                                        Button(onClick = {
                                            paymentPrefilledType = null
                                            paymentPrefilledId = null
                                            paymentPrefilledAmount = null
                                            paymentToEdit = null
                                            showPaymentDialog = true
                                        }) {
                                            Text("ثبت اولین پرداخت")
                                        }
                                    }
                                }
                            }
                        } else {
                            val sortedPayments = uiState.payments.sortedByDescending { it.date }
                            items(sortedPayments) { p ->
                                val jDate = JalaliDateHelper.isoToJalali(p.date)
                                val targetName = if (p.targetType == "person") {
                                    personsMap[p.targetId]?.name ?: "شخص"
                                } else {
                                    crewsMap[p.targetId]?.name ?: "دسته"
                                }
                                val methodStr = when (p.method) {
                                    "cash" -> "نقد"
                                    "card" -> "کارت‌به‌کارت"
                                    else -> "سایر"
                                }

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            paymentToEdit = p
                                            showPaymentDialog = true
                                        }
                                        .testTag("payment_card_${p.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("پرداخت به $targetName", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${jDate.toDisplayFull()} • روش: $methodStr",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            if (p.note.isNotBlank()) {
                                                Text(p.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                NumberFormatHelper.formatTomanFull(p.amount),
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = ColorDangerRed
                                            )
                                            IconButton(onClick = { paymentToDelete = p }) {
                                                Icon(Icons.Default.Delete, contentDescription = "حذف پرداخت", tint = ColorDangerRed)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // --- WAITING LIST TAB ---
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
                                    Text("صف انتظار پرداخت‌ها", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "مبالغ و دستمزدهایی که موعد پرداخت آن‌ها نزدیک است یا باید پرداخت شوند را اینجا یادداشت کنید تا فراموش نشود.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        if (uiState.waitingList.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("صف انتظار خالی است.", color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        } else {
                            val activeList = uiState.waitingList.filter { it.status == "open" && !it.deleted }
                            if (activeList.isEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Text("همه‌ی موارد صف انتظار تسویه شده‌اند.", color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            } else {
                                items(activeList) { w ->
                                    val targetName = if (w.accountType == "person") {
                                        personsMap[w.accountId]?.name ?: "شخص"
                                    } else {
                                        crewsMap[w.accountId]?.name ?: "دسته"
                                    }

                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("waiting_item_${w.id}")
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(targetName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                                    val jDue = JalaliDateHelper.isoToJalali(w.date)
                                                    Text("تاریخ / موعد: ${jDue.toDisplayShort()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                    if (w.note.isNotBlank()) {
                                                        Text(w.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                                    }
                                                }

                                                Text(
                                                    NumberFormatHelper.formatTomanFull(w.amount ?: 0L),
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            Divider()

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.End,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                OutlinedButton(
                                                    onClick = { viewModel.deleteWaitingListItem(w.id) },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDangerRed)
                                                ) {
                                                    Text("حذف")
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                Button(
                                                    onClick = {
                                                        paymentPrefilledType = w.accountType
                                                        paymentPrefilledId = w.accountId
                                                        paymentPrefilledAmount = w.amount
                                                        paymentToEdit = null
                                                        showPaymentDialog = true
                                                        viewModel.markWaitingListItemDone(w.id) {}
                                                    },
                                                    modifier = Modifier.testTag("pay_waiting_item_btn_${w.id}")
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("تسویه شد")
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

    // --- PAYMENT DIALOG ---
    if (showPaymentDialog) {
        PaymentDialog(
            initialPayment = paymentToEdit,
            prefilledTargetType = paymentPrefilledType,
            prefilledTargetId = paymentPrefilledId,
            prefilledAmount = paymentPrefilledAmount,
            uiState = uiState,
            onDismiss = {
                showPaymentDialog = false
                paymentToEdit = null
            },
            onSave = { date, amount, handedToPersonId, targetType, targetId, method, note, shares ->
                if (paymentToEdit != null) {
                    viewModel.updatePayment(
                        paymentToEdit!!.copy(
                            date = date,
                            amount = amount,
                            handedToPersonId = handedToPersonId,
                            targetType = targetType,
                            targetId = targetId,
                            method = method,
                            note = note,
                            shares = shares
                        )
                    ) {
                        showPaymentDialog = false
                        paymentToEdit = null
                        Toast.makeText(context, "پرداخت ویرایش شد", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    viewModel.createPayment(
                        date = date,
                        amount = amount,
                        handedToPersonId = handedToPersonId,
                        targetType = targetType,
                        targetId = targetId,
                        method = method,
                        note = note,
                        shares = shares
                    ) {
                        showPaymentDialog = false
                        Toast.makeText(context, "پرداخت با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // --- DELETE PAYMENT CONFIRMATION ---
    if (paymentToDelete != null) {
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            title = { Text("حذف پرداخت") },
            text = { Text("آیا از حذف این پرداخت اطمینان داری؟ با حذف این پرداخت، مانده حساب طرف مقابل دوباره افزایش می‌یابد.") },
            confirmButton = {
                Button(
                    onClick = {
                        val id = paymentToDelete!!.id
                        viewModel.deletePayment(id) {
                            paymentToDelete = null
                            Toast.makeText(context, "پرداخت حذف شد", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerRed)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { paymentToDelete = null }) { Text("انصراف") }
            }
        )
    }

    // --- WAITING LIST DIALOG ---
    if (showWaitingListDialog) {
        WaitingListDialog(
            uiState = uiState,
            onDismiss = { showWaitingListDialog = false },
            onSave = { accountType, accountId, amount, date, note ->
                viewModel.createWaitingListItem(accountType, accountId, amount, date, note) {
                    showWaitingListDialog = false
                    Toast.makeText(context, "به صف انتظار اضافه شد", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
