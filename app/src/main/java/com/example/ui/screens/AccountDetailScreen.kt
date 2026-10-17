package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import com.example.lib.ShareReportHelper
import com.example.lib.WorkforceCalculator
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.LedgerEntryDialog
import com.example.ui.components.PaymentDialog
import com.example.ui.components.VoidEntryDialog
import com.example.ui.theme.ColorDangerRed
import com.example.ui.theme.ColorNeutralGray
import com.example.ui.theme.ColorSuccessGreen
import com.example.ui.theme.ColorWarningOrange
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    accountType: String, // "person" | "crew"
    accountId: String,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    val context = LocalContext.current

    val person = remember(uiState.persons, accountId) {
        if (accountType == "person") uiState.persons.find { it.id == accountId } else null
    }
    val crew = remember(uiState.crews, accountId) {
        if (accountType == "crew") uiState.crews.find { it.id == accountId } else null
    }

    val accountTitle = if (accountType == "person") person?.name ?: "شخص" else crew?.name ?: "دسته"

    val personsMap = remember(uiState.persons) { uiState.persons.associateBy { it.id } }
    val crewsMap = remember(uiState.crews) { uiState.crews.associateBy { it.id } }
    val tagsMap = remember(uiState.tags) { uiState.tags.associateBy { it.id } }

    val ledgerRows = remember(
        accountType,
        accountId,
        uiState.attendances,
        uiState.pieceWorks,
        uiState.payments,
        uiState.ledgerEntries,
        uiState.wageRates
    ) {
        WorkforceCalculator.buildAccountLedger(
            accountType = accountType,
            accountId = accountId,
            attendances = uiState.attendances,
            pieceWorks = uiState.pieceWorks,
            payments = uiState.payments,
            ledgerEntries = uiState.ledgerEntries,
            wageRates = uiState.wageRates,
            crewsMap = crewsMap,
            tagsMap = tagsMap,
            personsMap = personsMap
        )
    }

    val totalBalance = ledgerRows.lastOrNull()?.runningBalance ?: 0L

    // Dialog states
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showManualEntryDialog by remember { mutableStateOf(false) }
    var entryToVoidId by remember { mutableStateOf<String?>(null) }
    var showReportShareSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(accountTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (accountType == "person") "صورت‌حساب شخص" else "صورت‌حساب دسته",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("account_detail_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(onClick = { showReportShareSheet = true }, modifier = Modifier.testTag("account_detail_share_btn")) {
                        Icon(Icons.Default.Share, contentDescription = "اشتراک‌گذاری صورت‌حساب")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showManualEntryDialog = true },
                        modifier = Modifier.weight(1f).testTag("account_detail_manual_entry_btn")
                    ) {
                        Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ردیف دستی")
                    }

                    Button(
                        onClick = { showPaymentDialog = true },
                        modifier = Modifier.weight(1f).testTag("account_detail_payment_btn")
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ثبت پرداخت")
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
        ) {
            // --- SUMMARY CARD ---
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("account_balance_summary_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("وضعیت فعلی حساب", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)

                            val statusBadgeText = when {
                                totalBalance > 0 -> "بستانکار (طلبکار)"
                                totalBalance < 0 -> "بدهکار"
                                else -> "تسویه کامل"
                            }
                            val statusBadgeColor = when {
                                totalBalance > 0 -> ColorSuccessGreen
                                totalBalance < 0 -> ColorDangerRed
                                else -> ColorNeutralGray
                            }

                            AssistChip(
                                onClick = {},
                                label = { Text(statusBadgeText, color = statusBadgeColor, fontWeight = FontWeight.Bold) }
                            )
                        }

                        Text(
                            text = NumberFormatHelper.formatTomanFull(kotlin.math.abs(totalBalance)),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                totalBalance > 0 -> ColorSuccessGreen
                                totalBalance < 0 -> ColorDangerRed
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )

                        if (person?.phone?.isNotBlank() == true) {
                            Text(
                                "شماره تماس: ${NumberFormatHelper.toPersianDigits(person.phone)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // --- TRANSACTIONS HEADER ---
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(
                        "گردش حساب (${NumberFormatHelper.toPersianDigits(ledgerRows.size)} ردیف)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // --- TRANSACTIONS ROWS ---
            if (ledgerRows.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("هیچ تراکنشی برای این حساب ثبت نشده است.", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            } else {
                items(ledgerRows) { row ->
                    val jDate = JalaliDateHelper.isoToJalali(row.date)
                    val isVoided = row.isVoided

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isVoided) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("ledger_row_${row.id}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = row.description,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textDecoration = if (isVoided) TextDecoration.LineThrough else TextDecoration.None,
                                        color = if (isVoided) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = jDate.toDisplayShort(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AssistChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                if (row.isAuto) "خودکار" else "دستی",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    )

                                    if (!row.isAuto && !isVoided) {
                                        IconButton(onClick = { entryToVoidId = row.sourceId }) {
                                            Icon(Icons.Default.Block, contentDescription = "ابطال ردیف", tint = ColorDangerRed)
                                        }
                                    }
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Debit / Credit breakdown
                                Column {
                                    if (row.credit > 0) {
                                        Text(
                                            "طلب: +${NumberFormatHelper.formatTomanFull(row.credit)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorSuccessGreen,
                                            textDecoration = if (isVoided) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                    }
                                    if (row.debit > 0) {
                                        Text(
                                            "پرداخت: -${NumberFormatHelper.formatTomanFull(row.debit)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorDangerRed,
                                            textDecoration = if (isVoided) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                    }
                                }

                                // Running balance
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("مانده بعد:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    val rColor = when {
                                        row.runningBalance > 0 -> ColorSuccessGreen
                                        row.runningBalance < 0 -> ColorDangerRed
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                    Text(
                                        text = NumberFormatHelper.formatTomanShort(kotlin.math.abs(row.runningBalance)),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = rColor
                                    )
                                }
                            }

                            if (isVoided) {
                                val reason = row.voidReason ?: "باطل شده"
                                Text(
                                    text = "این ردیف ابطال شده است: $reason",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ColorDangerRed,
                                    fontWeight = FontWeight.Bold
                                )
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
            initialPayment = null,
            prefilledTargetType = accountType,
            prefilledTargetId = accountId,
            prefilledAmount = null,
            uiState = uiState,
            onDismiss = { showPaymentDialog = false },
            onSave = { date, amount, handedToPersonId, targetType, targetId, method, note, shares ->
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
                    Toast.makeText(context, "پرداخت ثبت شد", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // --- MANUAL ENTRY DIALOG ---
    if (showManualEntryDialog) {
        LedgerEntryDialog(
            accountType = accountType,
            accountId = accountId,
            accountName = accountTitle,
            onDismiss = { showManualEntryDialog = false },
            onSave = { date, kind, amount, note ->
                viewModel.createLedgerEntry(accountType, accountId, date, kind, amount, note) {
                    showManualEntryDialog = false
                    Toast.makeText(context, "ردیف دستی با موفقیت اضافه شد", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // --- VOID ENTRY DIALOG ---
    if (entryToVoidId != null) {
        VoidEntryDialog(
            onDismiss = { entryToVoidId = null },
            onConfirm = { reason ->
                val id = entryToVoidId!!
                viewModel.voidLedgerEntry(id, reason) {
                    entryToVoidId = null
                    Toast.makeText(context, "ردیف با موفقیت ابطال شد", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // --- SHARE STATEMENT SHEET ---
    if (showReportShareSheet) {
        AlertDialog(
            onDismissRequest = { showReportShareSheet = false },
            title = { Text("اشتراک‌گذاری صورت‌حساب $accountTitle", fontWeight = FontWeight.Bold) },
            text = { Text("نوع خروجی صورت‌حساب را انتخاب کنید:") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val lines = mutableListOf<String>()
                            lines.add("صورت‌حساب: $accountTitle")
                            lines.add("تاریخ صدور: ${JalaliDateHelper.isoToJalali(LocalDate.now().toString()).toDisplayFull()}")
                            lines.add("مانده نهایی: ${NumberFormatHelper.formatTomanFull(totalBalance)}")
                            lines.add("--------------------------------")
                            ledgerRows.forEach { r ->
                                val j = JalaliDateHelper.isoToJalali(r.date)
                                val status = if (r.isVoided) " [باطل]" else ""
                                val amountStr = if (r.credit > 0) "+${NumberFormatHelper.formatTomanFull(r.credit)}" else "-${NumberFormatHelper.formatTomanFull(r.debit)}"
                                lines.add("${j.toDisplayShort()}: ${r.description}$status | $amountStr | مانده: ${NumberFormatHelper.formatTomanShort(r.runningBalance)}")
                            }
                            ShareReportHelper.shareTextReportAsPdf(context, "صورت‌حساب $accountTitle", lines, "ledger_$accountId")
                            showReportShareSheet = false
                        }
                    ) {
                        Text("PDF")
                    }

                    Button(
                        onClick = {
                            val lines = mutableListOf<String>()
                            lines.add("صورت‌حساب: $accountTitle")
                            lines.add("تاریخ صدور: ${JalaliDateHelper.isoToJalali(LocalDate.now().toString()).toDisplayFull()}")
                            lines.add("مانده نهایی: ${NumberFormatHelper.formatTomanFull(totalBalance)}")
                            lines.add("--------------------------------")
                            ledgerRows.forEach { r ->
                                val j = JalaliDateHelper.isoToJalali(r.date)
                                val status = if (r.isVoided) " [باطل]" else ""
                                val amountStr = if (r.credit > 0) "+${NumberFormatHelper.formatTomanFull(r.credit)}" else "-${NumberFormatHelper.formatTomanFull(r.debit)}"
                                lines.add("${j.toDisplayShort()}: ${r.description}$status | $amountStr | مانده: ${NumberFormatHelper.formatTomanShort(r.runningBalance)}")
                            }
                            ShareReportHelper.shareTextReportAsImage(context, "صورت‌حساب $accountTitle", lines, "ledger_$accountId")
                            showReportShareSheet = false
                        }
                    ) {
                        Text("تصویر")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showReportShareSheet = false }) { Text("انصراف") }
            }
        )
    }
}
