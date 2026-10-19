package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.lib.NumberFormatHelper
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.theme.ColorDangerRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    var validationErrorDialogMessage by remember { mutableStateOf<String?>(null) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var clearDataConfirmationInput by remember { mutableStateOf("") }
    var showClearDataSuccessDialog by remember { mutableStateOf(false) }
    var showForemanCoefDialog by remember { mutableStateOf(false) }
    var coefPresentStr by remember(uiState.foremanCoefPresent) { mutableStateOf(uiState.foremanCoefPresent.toString()) }
    var coefAbsentStr by remember(uiState.foremanCoefAbsent) { mutableStateOf(uiState.foremanCoefAbsent.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_btn")
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
            // --- SECTION: WORK TYPES ---
            item {
                Text(
                    text = "نوع کار مزرعه",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Zeraat Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("زراعت", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("فعالیت‌های زراعی و محصولات کشاورزی", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Switch(
                                checked = uiState.isZeraatActive,
                                onCheckedChange = {
                                    viewModel.toggleWorkType("zeraat") { farmNames ->
                                        validationErrorDialogMessage = "اول این مزرعه‌ها را غیرفعال کن: ${farmNames.joinToString("، ")}"
                                    }
                                },
                                modifier = Modifier.testTag("toggle_zeraat_switch")
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        // Nahalestan Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("نهالستان", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("تولید و پرورش نهال و درختان", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Switch(
                                checked = uiState.isNahalestanActive,
                                onCheckedChange = {
                                    viewModel.toggleWorkType("nahalestan") { farmNames ->
                                        validationErrorDialogMessage = "اول این مزرعه‌ها را غیرفعال کن: ${farmNames.joinToString("، ")}"
                                    }
                                },
                                modifier = Modifier.testTag("toggle_nahalestan_switch")
                            )
                        }
                    }
                }
            }

            // --- SECTION: MODULES ---
            item {
                Text(
                    text = "ماژول‌های برنامه",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Tasks Module
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ماژول کارهای آینده", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("ثبت و زمان‌بندی وظایف و کارهای روزانه", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Switch(
                                checked = uiState.isTasksModuleActive,
                                onCheckedChange = { viewModel.toggleTasksModule() },
                                modifier = Modifier.testTag("toggle_tasks_module_switch")
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        // Reports Module
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ماژول گزارش‌های آمار و مالی", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("تحلیل کارکرد کارگران، هزینه‌ها و خروجی گزارش‌ها", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Switch(
                                checked = uiState.isReportsModuleActive,
                                onCheckedChange = { viewModel.toggleReportsModule() },
                                modifier = Modifier.testTag("toggle_reports_module_switch")
                            )
                        }

                        // Workforce & Ledger Module
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ماژول نیروی کار و دفتر حساب", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("دسته‌های کارگری، حضور و غیاب، کار پیمانی و تسویه‌ها", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Switch(
                                checked = uiState.isWorkforceModuleActive,
                                onCheckedChange = { viewModel.toggleWorkforceModule() },
                                modifier = Modifier.testTag("toggle_workforce_module_switch")
                            )
                        }

                        if (uiState.isWorkforceModuleActive) {
                            Divider(modifier = Modifier.padding(vertical = 12.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("ضریب‌های سرکارگر", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    val cpFa = NumberFormatHelper.toPersianDigits(uiState.foremanCoefPresent)
                                    val caFa = NumberFormatHelper.toPersianDigits(uiState.foremanCoefAbsent)
                                    Text("حضور: $cpFa برابر • عدم‌حضور: $caFa برابر", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                OutlinedButton(
                                    onClick = { showForemanCoefDialog = true },
                                    modifier = Modifier.testTag("edit_foreman_coefs_btn")
                                ) {
                                    Text("تنظیم")
                                }
                            }
                        }
                    }
                }
            }

            // --- SECTION: DISPLAY SETTINGS (Font size & Theme) ---
            item {
                Text(
                    text = "نمایش و ظاهر",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Font Scale
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("اندازه‌ی نوشته", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    if (uiState.fontScale > 1.1f) "بزرگ (مناسب دید بهتر)" else "معمولی (پیش‌فرض)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Row {
                                FilterChip(
                                    selected = uiState.fontScale <= 1.1f,
                                    onClick = { viewModel.setFontScale(false) },
                                    label = { Text("معمولی") },
                                    modifier = Modifier.testTag("font_scale_normal_chip")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                FilterChip(
                                    selected = uiState.fontScale > 1.1f,
                                    onClick = { viewModel.setFontScale(true) },
                                    label = { Text("بزرگ") },
                                    modifier = Modifier.testTag("font_scale_large_chip")
                                )
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        // Theme Mode
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("حالت", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    if (uiState.isDarkTheme) "حالت تیره" else "حالت روشن",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Row {
                                FilterChip(
                                    selected = !uiState.isDarkTheme,
                                    onClick = { viewModel.setDarkTheme(false) },
                                    label = { Text("روشن") },
                                    leadingIcon = { Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    modifier = Modifier.testTag("theme_light_chip")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                FilterChip(
                                    selected = uiState.isDarkTheme,
                                    onClick = { viewModel.setDarkTheme(true) },
                                    label = { Text("تیره") },
                                    leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    modifier = Modifier.testTag("theme_dark_chip")
                                )
                            }
                        }
                    }
                }
            }

            // --- SECTION: CLEAR ALL DATA ---
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ColorDangerRed.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "پاک کردن همه‌ی داده‌ها",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ColorDangerRed
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "همه‌ی مزرعه‌ها، اشخاص، برچسب‌ها و ثبت‌ها پاک می‌شوند. قبل از پاک‌کردن، یک نسخه‌ی خودکار ذخیره می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                clearDataConfirmationInput = ""
                                showClearDataDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorDangerRed),
                            modifier = Modifier.testTag("clear_all_data_btn")
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("پاک کردن همه‌ی داده‌ها")
                        }
                    }
                }
            }
        }
    }

    // --- VALIDATION ERROR DIALOG ---
    if (validationErrorDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { validationErrorDialogMessage = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("غیرفعال‌سازی ممکن نیست") },
            text = { Text(validationErrorDialogMessage!!) },
            confirmButton = {
                Button(onClick = { validationErrorDialogMessage = null }) {
                    Text("متوجه شدم")
                }
            }
        )
    }

    // --- CLEAR ALL DATA CONFIRMATION DIALOG ---
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = ColorDangerRed) },
            title = { Text("تأیید پاک کردن همه‌ی داده‌ها", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "همه‌ی مزرعه‌ها، اشخاص، برچسب‌ها و ثبت‌ها پاک می‌شوند. اگر پشتیبان نفرستاده‌ای، اول بفرست.",
                        color = ColorDangerRed
                    )
                    Text("برای تأیید کلمه‌ی پاک را بنویس:")
                    OutlinedTextField(
                        value = clearDataConfirmationInput,
                        onValueChange = { clearDataConfirmationInput = it },
                        placeholder = { Text("پاک") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("clear_data_confirm_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData {
                            showClearDataDialog = false
                            showClearDataSuccessDialog = true
                        }
                    },
                    enabled = clearDataConfirmationInput.trim() == "پاک",
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerRed),
                    modifier = Modifier.testTag("confirm_clear_data_final_btn")
                ) {
                    Text("پاک کردن همه")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearDataDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    if (showClearDataSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataSuccessDialog = false },
            title = { Text("انجام شد") },
            text = { Text("همه‌ی داده‌ها با موفقیت پاک شدند و سیستم به حالت اولیه بازگشت.") },
            confirmButton = {
                Button(onClick = { showClearDataSuccessDialog = false }) {
                    Text("تأیید")
                }
            }
        )
    }

    if (showForemanCoefDialog) {
        AlertDialog(
            onDismissRequest = { showForemanCoefDialog = false },
            title = { Text("تنظیم ضریب‌های سرکارگر", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "ضریب سرکارگر نسبت به نرخ روزمزد کارگر محاسبه می‌شود:",
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = coefPresentStr,
                        onValueChange = { coefPresentStr = it },
                        label = { Text("ضریب در صورت حضور سرکارگر (پیش‌فرض ۲)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("coef_present_input")
                    )

                    OutlinedTextField(
                        value = coefAbsentStr,
                        onValueChange = { coefAbsentStr = it },
                        label = { Text("ضریب در صورت غیبت سرکارگر (پیش‌فرض ۱)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("coef_absent_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cp = NumberFormatHelper.parsePersianLong(coefPresentStr)?.toInt() ?: 2
                        val ca = NumberFormatHelper.parsePersianLong(coefAbsentStr)?.toInt() ?: 1
                        viewModel.setForemanCoefficients(cp, ca)
                        showForemanCoefDialog = false
                    },
                    modifier = Modifier.testTag("save_foreman_coefs_btn")
                ) {
                    Text("ذخیره")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showForemanCoefDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}
