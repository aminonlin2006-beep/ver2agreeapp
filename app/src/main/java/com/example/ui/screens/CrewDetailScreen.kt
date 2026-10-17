package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import com.example.data.entity.CrewEntity
import com.example.data.entity.WageRateEntity
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import com.example.lib.ShareReportHelper
import com.example.lib.WorkforceCalculator
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.AddCrewMemberDialog
import com.example.ui.components.AttendanceDialog
import com.example.ui.components.CrewDialog
import com.example.ui.components.WageChangeDialog
import com.example.ui.theme.ColorDangerRed
import com.example.ui.theme.ColorNeutralGray
import com.example.ui.theme.ColorSuccessGreen
import com.example.ui.theme.ColorWarningOrange
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewDetailScreen(
    crewId: String,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    val context = LocalContext.current

    val crew = remember(uiState.crews, crewId) {
        uiState.crews.find { it.id == crewId }
    }

    if (crew == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("دسته یافت نشد.")
        }
        return
    }

    val personsMap = remember(uiState.persons) { uiState.persons.associateBy { it.id } }
    val farmsMap = remember(uiState.farms) { uiState.farms.associateBy { it.id } }
    val plotsMap = remember(uiState.plots) { uiState.plots.associateBy { it.id } }

    val members = remember(uiState.crewMembers, crewId) {
        uiState.crewMembers.filter { it.crewId == crewId && !it.deleted }
    }

    val wageRates = remember(uiState.wageRates, crewId) {
        uiState.wageRates.filter { it.crewId == crewId && !it.deleted }
            .sortedByDescending { it.effectiveFrom }
    }

    val attendances = remember(uiState.attendances, crewId) {
        uiState.attendances.filter { it.crewId == crewId && !it.deleted }
            .sortedByDescending { it.date }
    }

    val todayIso = remember { LocalDate.now().toString() }
    val currentBaseWage = remember(wageRates, todayIso) {
        WorkforceCalculator.getEffectiveDailyWage(wageRates, crewId, null, todayIso)
    }

    val foreman = remember(crew.foremanId, personsMap) {
        crew.foremanId?.let { personsMap[it] }
    }

    // Dialog states
    var showEditCrewDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showWageChangeDialog by remember { mutableStateOf(false) }
    var wageChangePersonId by remember { mutableStateOf<String?>(null) }
    var wageChangeTitle by remember { mutableStateOf("") }
    var showAttendanceDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(crew.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (crew.kind == "count") "دسته‌ی عددی (شمارشی)" else "دسته‌ی اسمی (${NumberFormatHelper.toPersianDigits(members.size)} عضو)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("crew_detail_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(onClick = { showReportDialog = true }, modifier = Modifier.testTag("crew_detail_report_btn")) {
                        Icon(Icons.Default.Share, contentDescription = "گزارش کارکرد")
                    }
                    IconButton(onClick = { showEditCrewDialog = true }, modifier = Modifier.testTag("crew_detail_edit_btn")) {
                        Icon(Icons.Default.Edit, contentDescription = "ویرایش دسته")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAttendanceDialog = true },
                icon = { Icon(Icons.Default.FactCheck, contentDescription = null) },
                text = { Text("ثبت حضور و غیاب") },
                modifier = Modifier.testTag("crew_detail_add_attendance_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp)
        ) {
            // --- HEADER SUMMARY CARD ---
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("crew_summary_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (crew.active) ColorSuccessGreen else ColorNeutralGray)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (crew.active) "دسته فعال" else "دسته غیرفعال",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (crew.active) ColorSuccessGreen else ColorNeutralGray
                                )
                            }

                            SuggestionChip(
                                onClick = { viewModel.toggleCrewActive(crew) },
                                label = { Text(if (crew.active) "غیرفعال کردن" else "فعال کردن") }
                            )
                        }

                        Divider()

                        // Foreman info
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("سرکارگر:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    if (foreman != null) "${foreman.name} ${if (foreman.phone.isNotBlank()) "(${NumberFormatHelper.toPersianDigits(foreman.phone)})" else ""}" else "تعیین نشده",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Base wage
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("نرخ روزمزد پایه:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text(
                                        NumberFormatHelper.formatTomanFull(currentBaseWage),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    wageChangePersonId = null
                                    wageChangeTitle = "تغییر نرخ روزمزد پایه‌ی دسته"
                                    showWageChangeDialog = true
                                },
                                modifier = Modifier.testTag("change_base_wage_btn")
                            ) {
                                Text("تغییر نرخ")
                            }
                        }

                        if (crew.note.isNotBlank()) {
                            Text(
                                text = "یادداشت: ${crew.note}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // --- MEMBERS SECTION (for "members" kind) ---
            if (crew.kind == "members") {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(
                            text = "اعضای دسته (${NumberFormatHelper.toPersianDigits(members.size)})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        FilledTonalButton(
                            onClick = { showAddMemberDialog = true },
                            modifier = Modifier.testTag("add_crew_member_btn")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("افزودن عضو")
                        }
                    }
                }

                if (members.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("هنوز عضوی به این دسته اضافه نشده است.", color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                } else {
                    items(members) { m ->
                        val person = personsMap[m.personId]
                        val personalWage = remember(wageRates, m.personId, todayIso) {
                            WorkforceCalculator.getEffectiveDailyWage(wageRates, crewId, m.personId, todayIso)
                        }
                        val hasCustomWage = personalWage != currentBaseWage

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth().testTag("crew_member_card_${m.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (m.active) ColorSuccessGreen else ColorNeutralGray)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            person?.name ?: "شخص نامشخص",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (!person?.phone.isNullOrBlank()) {
                                            Text(
                                                NumberFormatHelper.toPersianDigits(person!!.phone),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        Text(
                                            if (hasCustomWage) "نرخ اختصاصی: ${NumberFormatHelper.formatTomanFull(personalWage)}" else "نرخ پایه دسته",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (hasCustomWage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            wageChangePersonId = m.personId
                                            wageChangeTitle = "نرخ دستمزد ${person?.name ?: ""}"
                                            showWageChangeDialog = true
                                        }
                                    ) {
                                        Icon(Icons.Default.PriceChange, contentDescription = "تغییر نرخ", tint = MaterialTheme.colorScheme.primary)
                                    }

                                    if (m.active) {
                                        IconButton(
                                            onClick = { viewModel.deactivateCrewMember(m.id) }
                                        ) {
                                            Icon(Icons.Default.PersonRemove, contentDescription = "غیرفعال‌سازی عضو", tint = ColorWarningOrange)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- WAGE HISTORY SECTION ---
            item {
                Text(
                    text = "تاریخچه‌ی نرخ‌های دستمزد",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            if (wageRates.isEmpty()) {
                item {
                    Text("هیچ نرخی ثبت نشده است.", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                items(wageRates) { wr ->
                    val jDate = JalaliDateHelper.isoToJalali(wr.effectiveFrom)
                    val pName = wr.personId?.let { personsMap[it]?.name }
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    if (pName != null) "مخصوص $pName" else "نرخ کل دسته",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "از تاریخ: ${jDate.toDisplayShort()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Text(
                                NumberFormatHelper.formatTomanFull(wr.dailyWage),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // --- ATTENDANCE RECORDS SECTION ---
            item {
                Text(
                    text = "ثبت‌های حضور و غیاب اخیر",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            if (attendances.isEmpty()) {
                item {
                    Text("هنوز حضور و غیابی برای این دسته ثبت نشده است.", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                items(attendances.take(10)) { att ->
                    val jDate = JalaliDateHelper.isoToJalali(att.date)
                    val farm = att.farmId?.let { farmsMap[it] }
                    val plot = att.plotId?.let { plotsMap[it] }
                    val location = if (plot != null && !plot.isDefault) "${farm?.name ?: ""} — ${plot.name}" else farm?.name ?: "نامشخص"

                    val calc = remember(att, crew, wageRates, personsMap) {
                        WorkforceCalculator.calculateAttendanceWage(att, crew, wageRates, personsMap)
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().testTag("crew_attendance_item_${att.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(jDate.toDisplayFull(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "محل: $location • ${NumberFormatHelper.toPersianDigits(calc.totalPersons)} نفر (${NumberFormatHelper.toPersianDigits(calc.totalWageUnits)} واحد)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                if (att.note.isNotBlank()) {
                                    Text(att.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Text(
                                NumberFormatHelper.formatTomanShort(calc.totalAmount),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    // --- EDIT CREW DIALOG ---
    if (showEditCrewDialog) {
        CrewDialog(
            initialCrew = crew,
            uiState = uiState,
            onDismiss = { showEditCrewDialog = false },
            onSave = { name, kind, foremanId, _, _, note ->
                viewModel.updateCrew(
                    crew.copy(name = name, kind = kind, foremanId = foremanId, note = note)
                ) {
                    showEditCrewDialog = false
                }
            }
        )
    }

    // --- ADD CREW MEMBER DIALOG ---
    if (showAddMemberDialog) {
        AddCrewMemberDialog(
            crew = crew,
            uiState = uiState,
            onDismiss = { showAddMemberDialog = false },
            onAddExisting = { personId ->
                viewModel.addCrewMember(crew.id, personId) {
                    showAddMemberDialog = false
                }
            },
            onAddNewPerson = { name, phone ->
                viewModel.createPersonAndAddToCrew(crew.id, name, phone) {
                    showAddMemberDialog = false
                }
            }
        )
    }

    // --- WAGE CHANGE DIALOG ---
    if (showWageChangeDialog) {
        WageChangeDialog(
            title = wageChangeTitle,
            crewId = crewId,
            personId = wageChangePersonId,
            uiState = uiState,
            onDismiss = { showWageChangeDialog = false },
            onSave = { newWage, effectiveFrom ->
                viewModel.setCrewDailyWage(crewId, wageChangePersonId, newWage, effectiveFrom) {
                    showWageChangeDialog = false
                    Toast.makeText(context, "نرخ جدید با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // --- ATTENDANCE DIALOG ---
    if (showAttendanceDialog) {
        AttendanceDialog(
            uiState = uiState,
            viewModel = viewModel,
            prefilledCrewId = crewId,
            onDismiss = { showAttendanceDialog = false },
            onSave = { date, farmId, plotId, cId, tagId, lines, headcount, countTicks, foreman, note ->
                viewModel.createAttendance(
                    date = date,
                    farmId = farmId,
                    plotId = plotId,
                    crewId = cId,
                    tagId = tagId,
                    lines = lines,
                    headcount = headcount,
                    countTicks = countTicks,
                    foreman = foreman,
                    note = note
                ) {
                    showAttendanceDialog = false
                    Toast.makeText(context, "حضور و غیاب ثبت شد", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // --- REPORT EXPORT DIALOG ---
    if (showReportDialog) {
        CrewReportExportDialog(
            crew = crew,
            members = members,
            wageRates = wageRates,
            attendances = attendances,
            personsMap = personsMap,
            onDismiss = { showReportDialog = false }
        )
    }
}

@Composable
fun CrewReportExportDialog(
    crew: CrewEntity,
    members: List<com.example.data.entity.CrewMemberEntity>,
    wageRates: List<WageRateEntity>,
    attendances: List<com.example.data.entity.AttendanceEntity>,
    personsMap: Map<String, com.example.data.entity.PersonEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var fromDateIso by remember {
        mutableStateOf(LocalDate.now().minusDays(30).toString())
    }
    var toDateIso by remember {
        mutableStateOf(LocalDate.now().toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("گزارش کارکرد دسته‌ی ${crew.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("بازه زمانی گزارش:", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("از تاریخ:", style = MaterialTheme.typography.labelSmall)
                        Text(
                            JalaliDateHelper.isoToJalali(fromDateIso).toDisplayFull(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("تا تاریخ:", style = MaterialTheme.typography.labelSmall)
                        Text(
                            JalaliDateHelper.isoToJalali(toDateIso).toDisplayFull(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val filteredAtt = remember(attendances, fromDateIso, toDateIso) {
                    attendances.filter { it.date in fromDateIso..toDateIso }
                }

                val totalSum = remember(filteredAtt, crew, wageRates, personsMap) {
                    filteredAtt.sumOf { att ->
                        WorkforceCalculator.calculateAttendanceWage(att, crew, wageRates, personsMap).totalAmount
                    }
                }

                Text(
                    "تعداد روزهای کارکرد: ${NumberFormatHelper.toPersianDigits(filteredAtt.size)} روز",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "مجموع کارکرد: ${NumberFormatHelper.formatTomanFull(totalSum)}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val filteredAtt = attendances.filter { it.date in fromDateIso..toDateIso }
                        val lines = mutableListOf<String>()
                        lines.add("گزارش کارکرد دسته: ${crew.name}")
                        lines.add("از: ${JalaliDateHelper.isoToJalali(fromDateIso).toDisplayFull()} تا: ${JalaliDateHelper.isoToJalali(toDateIso).toDisplayFull()}")
                        lines.add("تعداد روزها: ${NumberFormatHelper.toPersianDigits(filteredAtt.size)}")
                        var sum = 0L
                        filteredAtt.forEach { att ->
                            val c = WorkforceCalculator.calculateAttendanceWage(att, crew, wageRates, personsMap)
                            sum += c.totalAmount
                            lines.add("${JalaliDateHelper.isoToJalali(att.date).toDisplayShort()}: ${NumberFormatHelper.toPersianDigits(c.totalPersons)} نفر – ${NumberFormatHelper.formatTomanFull(c.totalAmount)}")
                        }
                        lines.add("مجموع کل: ${NumberFormatHelper.formatTomanFull(sum)}")
                        ShareReportHelper.shareTextReportAsPdf(context, "گزارش ${crew.name}", lines, "crew_${crew.id}")
                        onDismiss()
                    }
                ) {
                    Text("PDF")
                }

                Button(
                    onClick = {
                        val filteredAtt = attendances.filter { it.date in fromDateIso..toDateIso }
                        val lines = mutableListOf<String>()
                        lines.add("گزارش کارکرد دسته: ${crew.name}")
                        lines.add("از: ${JalaliDateHelper.isoToJalali(fromDateIso).toDisplayFull()} تا: ${JalaliDateHelper.isoToJalali(toDateIso).toDisplayFull()}")
                        lines.add("تعداد روزها: ${NumberFormatHelper.toPersianDigits(filteredAtt.size)}")
                        var sum = 0L
                        filteredAtt.forEach { att ->
                            val c = WorkforceCalculator.calculateAttendanceWage(att, crew, wageRates, personsMap)
                            sum += c.totalAmount
                            lines.add("${JalaliDateHelper.isoToJalali(att.date).toDisplayShort()}: ${NumberFormatHelper.toPersianDigits(c.totalPersons)} نفر – ${NumberFormatHelper.formatTomanFull(c.totalAmount)}")
                        }
                        lines.add("مجموع کل: ${NumberFormatHelper.formatTomanFull(sum)}")
                        ShareReportHelper.shareTextReportAsImage(context, "گزارش ${crew.name}", lines, "crew_${crew.id}")
                        onDismiss()
                    }
                ) {
                    Text("تصویر")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("بستن") }
        }
    )
}
