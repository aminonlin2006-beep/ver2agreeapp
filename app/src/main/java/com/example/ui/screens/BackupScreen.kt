package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.entity.SnapshotEntity
import com.example.data.gateway.DataGateway
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.ConfirmDialog
import com.example.ui.theme.ColorDangerRed
import com.example.ui.theme.ColorSuccessGreen
import com.example.ui.theme.ColorWarningOrange
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showShareConfirmationDialog by remember { mutableStateOf(false) }
    var showRestoreFileDialog by remember { mutableStateOf(false) }
    var restoreJsonInput by remember { mutableStateOf("") }
    var restoreValidationResult by remember { mutableStateOf<DataGateway.BackupValidationResult?>(null) }
    var showRestoreWarningDialog by remember { mutableStateOf(false) }
    var snapshotToRestore by remember { mutableStateOf<SnapshotEntity?>(null) }
    var alertMessage by remember { mutableStateOf<String?>(null) }

    // File picker launcher
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val content = stream.bufferedReader().use { it.readText() }
                    restoreJsonInput = content
                    restoreValidationResult = viewModel.gateway.validateBackupContent(content)
                    showRestoreFileDialog = true
                }
            } catch (e: Exception) {
                alertMessage = "خطا در خواندن فایل انتخاب شده: ${e.localizedMessage}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پشتیبان‌گیری و بازیابی", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("backup_back_btn")
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "بازگشت")
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
            // --- STATUS CARD ---
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.unsentChanges == 0) ColorSuccessGreen.copy(alpha = 0.12f) else ColorWarningOrange.copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (uiState.unsentChanges == 0) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (uiState.unsentChanges == 0) ColorSuccessGreen else ColorWarningOrange,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.unsentChanges == 0) {
                                    "همه چیز پشتیبان دارد"
                                } else {
                                    val countFa = NumberFormatHelper.toPersianDigits(uiState.unsentChanges)
                                    "$countFa تغییر بدون پشتیبان"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.unsentChanges == 0) ColorSuccessGreen else ColorWarningOrange
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        if (uiState.lastExportAt.isNotBlank()) {
                            val lastExportJalali = JalaliDateHelper.fromIsoDate(uiState.lastExportAt).toDisplayFull()
                            Text(
                                text = "آخرین فرستادن: $lastExportJalali",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else {
                            Text(
                                text = "هنوز پشتیبانی به بیرون فرستاده نشده است.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ذخیره‌ی ماندگار روی گوشی: فعال",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // --- PERMANENT NOTE ---
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تنها پشتیبان مطمئن، فایلی است که بیرون می‌فرستی (تلگرام، ایمیل...).",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // --- PRIMARY ACTION BUTTONS ---
            item {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val json = viewModel.getExportJsonString()
                                val today = JalaliDateHelper.today()
                                val fileName = "پشتیبان-مزرعه-${today.year}-${today.month}-${today.day}.json"

                                val cacheDir = File(context.cacheDir, "backups").apply { mkdirs() }
                                val file = File(cacheDir, fileName)
                                file.writeText(json)

                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )

                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, fileName)
                                    putExtra(Intent.EXTRA_TEXT, "فایل پشتیبان مدیریت مزرعه — ${today.toDisplayFull()}")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }

                                context.startActivity(Intent.createChooser(shareIntent, "ارسال فایل پشتیبان"))
                                showShareConfirmationDialog = true
                            } catch (e: Exception) {
                                alertMessage = "خطا در ساخت فایل پشتیبان: ${e.localizedMessage}"
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("send_backup_btn")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("فرستادن پشتیبان", style = MaterialTheme.typography.titleSmall)
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        try {
                            openDocumentLauncher.launch(arrayOf("application/json", "*/*"))
                        } catch (_: Exception) {
                            showRestoreFileDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("restore_backup_btn")
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("برگرداندن از فایل", style = MaterialTheme.typography.titleSmall)
                }
            }

            // --- AUTOMATIC SNAPSHOTS LIST ---
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "نسخه‌های خودکار روزانه (۷ نسخه‌ی آخر)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.snapshots.isEmpty()) {
                item {
                    Text(
                        text = "هنوز نسخه‌ی خودکاری ثبت نشده است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                items(uiState.snapshots, key = { it.id }) { snapshot ->
                    SnapshotItemCard(
                        snapshot = snapshot,
                        onRestore = { snapshotToRestore = snapshot }
                    )
                }
            }
        }
    }

    // --- "فرستادی؟" CONFIRMATION DIALOG ---
    if (showShareConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showShareConfirmationDialog = false },
            title = { Text("ارسال پشتیبان") },
            text = { Text("آیا فایل پشتیبان را با موفقیت ارسال یا ذخیره کردید؟") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.acknowledgeBackupSent()
                        showShareConfirmationDialog = false
                    },
                    modifier = Modifier.testTag("backup_sent_yes_btn")
                ) {
                    Text("بله")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showShareConfirmationDialog = false }) {
                    Text("خیر")
                }
            }
        )
    }

    // --- RESTORE FROM FILE DIALOG ---
    if (showRestoreFileDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreFileDialog = false },
            title = { Text("برگرداندن فایل پشتیبان") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (restoreValidationResult == null) {
                        Text("محتوای متنی فایل JSON پشتیبان را در کادر زیر وارد کنید یا فایل را انتخاب کنید:")
                        OutlinedTextField(
                            value = restoreJsonInput,
                            onValueChange = {
                                restoreJsonInput = it
                                if (it.isNotBlank()) {
                                    restoreValidationResult = viewModel.gateway.validateBackupContent(it)
                                }
                            },
                            label = { Text("محتوای JSON پشتیبان") },
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (!restoreValidationResult!!.isValid) {
                        Text(
                            text = restoreValidationResult!!.errorMessage ?: "فایل نامعتبر است",
                            color = ColorDangerRed,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = {
                                restoreValidationResult = null
                                restoreJsonInput = ""
                            }
                        ) {
                            Text("انتخاب مجدد فایل")
                        }
                    } else {
                        val res = restoreValidationResult!!
                        Text(
                            text = "خلاصه محتوای فایل:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text("تاریخ پشتیبان: ${res.exportedAtJalali}")
                        Text("تعداد مزرعه‌ها: ${NumberFormatHelper.toPersianDigits(res.farmsCount)}")
                        Text("تعداد اشخاص: ${NumberFormatHelper.toPersianDigits(res.personsCount)}")
                        Text("تعداد برچسب‌ها: ${NumberFormatHelper.toPersianDigits(res.tagsCount)}")

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "هشدار: همه‌ی داده‌ی فعلی با این فایل عوض می‌شود.",
                            color = ColorDangerRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                if (restoreValidationResult?.isValid == true) {
                    Button(
                        onClick = {
                            viewModel.restoreBackup(restoreJsonInput) { success, msg ->
                                showRestoreFileDialog = false
                                alertMessage = msg
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorDangerRed),
                        modifier = Modifier.testTag("confirm_restore_file_btn")
                    ) {
                        Text("برگردان")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRestoreFileDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    // --- RESTORE SNAPSHOT CONFIRMATION ---
    if (snapshotToRestore != null) {
        val s = snapshotToRestore!!
        val dateDisplay = JalaliDateHelper.fromIsoDate(s.date).toDisplayFull()
        ConfirmDialog(
            title = "برگرداندن نسخه‌ی خودکار",
            message = "آیا می‌خواهید نسخه‌ی «$dateDisplay — ${s.summary}» را برگردانید؟\nتوجه: داده‌های فعلی با این نسخه جایگزین خواهند شد.",
            confirmText = "برگردان",
            isDestructive = true,
            onConfirm = {
                viewModel.restoreSnapshot(s) { success ->
                    if (success) {
                        alertMessage = "نسخه‌ی خودکار با موفقیت برگردانده شد."
                    } else {
                        alertMessage = "خطا در برگرداندن نسخه‌ی خودکار."
                    }
                    snapshotToRestore = null
                }
            },
            onDismiss = { snapshotToRestore = null }
        )
    }

    // Alert
    if (alertMessage != null) {
        AlertDialog(
            onDismissRequest = { alertMessage = null },
            title = { Text("نتیجه") },
            text = { Text(alertMessage!!) },
            confirmButton = {
                Button(onClick = { alertMessage = null }) {
                    Text("تأیید")
                }
            }
        )
    }
}

@Composable
fun SnapshotItemCard(
    snapshot: SnapshotEntity,
    onRestore: () -> Unit
) {
    val dateDisplay = JalaliDateHelper.fromIsoDate(snapshot.date).toDisplayFull()

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth().testTag("snapshot_item_${snapshot.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateDisplay,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = snapshot.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier.testTag("restore_snapshot_btn_${snapshot.id}")
            ) {
                Text("برگردان")
            }
        }
    }
}
