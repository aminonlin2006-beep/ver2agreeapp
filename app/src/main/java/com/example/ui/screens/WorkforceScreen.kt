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
import androidx.compose.ui.unit.dp
import com.example.data.entity.AttendanceEntity
import com.example.data.entity.CrewEntity
import com.example.data.entity.PieceWorkEntity
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import com.example.lib.WorkforceCalculator
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.*
import com.example.ui.theme.ColorDangerRed
import com.example.ui.theme.ColorNeutralGray
import com.example.ui.theme.ColorSuccessGreen
import com.example.ui.theme.ColorWarningOrange
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkforceScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCrewDetail: (String) -> Unit
) {
    BackHandler { onNavigateBack() }
    val context = LocalContext.current

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("دسته‌ها", "حضور و غیاب", "کار پیمانی", "شمارش من")

    val personsMap = remember(uiState.persons) { uiState.persons.associateBy { it.id } }
    val crewsMap = remember(uiState.crews) { uiState.crews.associateBy { it.id } }
    val farmsMap = remember(uiState.farms) { uiState.farms.associateBy { it.id } }
    val plotsMap = remember(uiState.plots) { uiState.plots.associateBy { it.id } }
    val tagsMap = remember(uiState.tags) { uiState.tags.associateBy { it.id } }
    val todayIso = remember { LocalDate.now().toString() }

    // Dialog states
    var showCreateCrewDialog by remember { mutableStateOf(false) }
    var crewToEdit by remember { mutableStateOf<CrewEntity?>(null) }
    var crewToDelete by remember { mutableStateOf<CrewEntity?>(null) }

    var showAttendanceDialog by remember { mutableStateOf(false) }
    var attendanceToEdit by remember { mutableStateOf<AttendanceEntity?>(null) }

    var showPieceWorkDialog by remember { mutableStateOf(false) }
    var pieceWorkToEdit by remember { mutableStateOf<PieceWorkEntity?>(null) }

    var showMyCountDialog by remember { mutableStateOf(false) }
    var showComparisonDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("نیروی کار و روزمزد", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("workforce_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        },
        floatingActionButton = {
            when (selectedTabIndex) {
                0 -> {
                    ExtendedFloatingActionButton(
                        onClick = { showCreateCrewDialog = true },
                        icon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
                        text = { Text("دسته‌ی جدید") },
                        modifier = Modifier.testTag("workforce_add_crew_fab")
                    )
                }
                1 -> {
                    ExtendedFloatingActionButton(
                        onClick = {
                            attendanceToEdit = null
                            showAttendanceDialog = true
                        },
                        icon = { Icon(Icons.Default.FactCheck, contentDescription = null) },
                        text = { Text("ثبت حضور و غیاب") },
                        modifier = Modifier.testTag("workforce_add_attendance_fab")
                    )
                }
                2 -> {
                    ExtendedFloatingActionButton(
                        onClick = {
                            pieceWorkToEdit = null
                            showPieceWorkDialog = true
                        },
                        icon = { Icon(Icons.Default.Handshake, contentDescription = null) },
                        text = { Text("ثبت کار پیمانی") },
                        modifier = Modifier.testTag("workforce_add_piecework_fab")
                    )
                }
                3 -> {
                    ExtendedFloatingActionButton(
                        onClick = { showMyCountDialog = true },
                        icon = { Icon(Icons.Default.Pin, contentDescription = null) },
                        text = { Text("شمارش جدید") },
                        modifier = Modifier.testTag("workforce_add_mycount_fab")
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
                modifier = Modifier.fillMaxWidth().testTag("workforce_tabs")
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("workforce_tab_$index")
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> {
                    // --- CREWS TAB ---
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
                    ) {
                        if (uiState.crews.isEmpty()) {
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
                                        Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                                        Text("هنوز هیچ دسته‌ای ثبت نشده است.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                                        Button(onClick = { showCreateCrewDialog = true }) {
                                            Text("ایجاد اولین دسته")
                                        }
                                    }
                                }
                            }
                        } else {
                            items(uiState.crews) { crew ->
                                val membersCount = uiState.crewMembers.count { it.crewId == crew.id && !it.deleted }
                                val baseWage = WorkforceCalculator.getEffectiveDailyWage(uiState.wageRates, crew.id, null, todayIso)
                                val foreman = crew.foremanId?.let { personsMap[it] }

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToCrewDetail(crew.id) }
                                        .testTag("crew_card_${crew.id}")
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                                Text(crew.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                            }

                                            SuggestionChip(
                                                onClick = { onNavigateToCrewDetail(crew.id) },
                                                label = {
                                                    Text(
                                                        if (crew.kind == "count") "عددی" else "${NumberFormatHelper.toPersianDigits(membersCount)} عضو",
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column {
                                                Text(
                                                    "سرکارگر: ${foreman?.name ?: "ندارد"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                                Text(
                                                    "روزمزد پایه: ${NumberFormatHelper.formatTomanFull(baseWage)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            Row {
                                                IconButton(
                                                    onClick = { crewToEdit = crew },
                                                    modifier = Modifier.testTag("edit_crew_btn_${crew.id}")
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "ویرایش", tint = MaterialTheme.colorScheme.outline)
                                                }
                                                IconButton(
                                                    onClick = { crewToDelete = crew },
                                                    modifier = Modifier.testTag("delete_crew_btn_${crew.id}")
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = ColorDangerRed)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // --- ATTENDANCE TAB ---
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
                    ) {
                        if (uiState.attendances.isEmpty()) {
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
                                        Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                                        Text("هنوز حضور و غیابی ثبت نشده است.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                                        Button(onClick = {
                                            attendanceToEdit = null
                                            showAttendanceDialog = true
                                        }) {
                                            Text("ثبت اولین حضور و غیاب")
                                        }
                                    }
                                }
                            }
                        } else {
                            val sortedAtt = uiState.attendances.sortedByDescending { it.date }
                            items(sortedAtt) { att ->
                                val crew = crewsMap[att.crewId]
                                val farm = att.farmId?.let { farmsMap[it] }
                                val plot = att.plotId?.let { plotsMap[it] }
                                val location = if (plot != null && !plot.isDefault) "${farm?.name ?: ""} — ${plot.name}" else farm?.name ?: "مزرعه نامشخص"
                                val jDate = JalaliDateHelper.isoToJalali(att.date)

                                val calc = if (crew != null) {
                                    WorkforceCalculator.calculateAttendanceWage(att, crew, uiState.wageRates, personsMap)
                                } else null

                                // Check if user has a myCount on this date for this crew
                                val matchingMyCount = uiState.myCounts.find { it.date == att.date && it.crewId == att.crewId && !it.deleted }

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            attendanceToEdit = att
                                            showAttendanceDialog = true
                                        }
                                        .testTag("attendance_card_${att.id}")
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column {
                                                Text(jDate.toDisplayFull(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                                Text("دسته: ${crew?.name ?: "نامشخص"} • محل: $location", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            }

                                            if (calc != null) {
                                                Text(
                                                    NumberFormatHelper.formatTomanShort(calc.totalAmount),
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        if (calc != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    "تعداد: ${NumberFormatHelper.toPersianDigits(calc.totalPersons)} نفر (${NumberFormatHelper.toPersianDigits(calc.totalWageUnits)} واحد)",
                                                    style = MaterialTheme.typography.bodySmall
                                                )

                                                if (matchingMyCount != null) {
                                                    val myHeadcount = matchingMyCount.headcount
                                                    val isMatch = myHeadcount == calc.totalPersons
                                                    val matchText = if (isMatch) "شمارش من منطبق است" else "مغایرت با شمارش من (${NumberFormatHelper.toPersianDigits(myHeadcount)})"
                                                    val matchColor = if (isMatch) ColorSuccessGreen else ColorWarningOrange

                                                    SuggestionChip(
                                                        onClick = { showComparisonDialog = true },
                                                        label = { Text(matchText, color = matchColor, style = MaterialTheme.typography.labelSmall) }
                                                    )
                                                }
                                            }
                                        }

                                        if (att.note.isNotBlank()) {
                                            Text("یادداشت: ${att.note}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // --- PIECEWORK TAB ---
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
                    ) {
                        if (uiState.pieceWorks.isEmpty()) {
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
                                        Icon(Icons.Default.Handshake, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                                        Text("هنوز کار پیمانی ثبت نشده است.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                                        Button(onClick = {
                                            pieceWorkToEdit = null
                                            showPieceWorkDialog = true
                                        }) {
                                            Text("ثبت اولین کار پیمانی")
                                        }
                                    }
                                }
                            }
                        } else {
                            val sortedPw = uiState.pieceWorks.sortedByDescending { it.date }
                            items(sortedPw) { pw ->
                                val farm = pw.farmId?.let { farmsMap[it] }
                                val plot = pw.plotId?.let { plotsMap[it] }
                                val location = if (plot != null && !plot.isDefault) "${farm?.name ?: ""} — ${plot.name}" else farm?.name ?: "محل مشخص نشده"
                                val jDate = JalaliDateHelper.isoToJalali(pw.date)
                                val tag = pw.tagId?.let { tagsMap[it] }

                                val linesArr = remember(pw.lines) {
                                    try { org.json.JSONArray(pw.lines) } catch (_: Exception) { org.json.JSONArray() }
                                }
                                var totalCount = 0
                                for (i in 0 until linesArr.length()) {
                                    totalCount += linesArr.getJSONObject(i).optInt("count", 0)
                                }
                                val totalEarned = totalCount.toLong() * pw.rate

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            pieceWorkToEdit = pw
                                            showPieceWorkDialog = true
                                        }
                                        .testTag("piecework_card_${pw.id}")
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column {
                                                Text("${pw.jobType} (${pw.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    "${NumberFormatHelper.toPersianDigits(linesArr.length())} کارگر • هر واحد ${NumberFormatHelper.formatTomanFull(pw.rate)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }

                                            Text(
                                                NumberFormatHelper.formatTomanShort(totalEarned),
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "مجموع: ${NumberFormatHelper.toPersianDigits(totalCount)} عدد",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(jDate.toDisplayShort(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("محل: $location", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            if (tag != null) {
                                                SuggestionChip(
                                                    onClick = {},
                                                    label = { Text(tag.name, style = MaterialTheme.typography.labelSmall) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // --- MY COUNTS TAB ---
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
                                    Text("شمارش سرانگشتی سر مزرعه", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "برای کنترل ادعای سرکارگر، تعداد کارگرهای حاضر را سریع ثبت کنید تا بعداً مغایرت‌ها مشخص شود.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Button(
                                        onClick = { showComparisonDialog = true },
                                        modifier = Modifier.fillMaxWidth().testTag("open_comparison_dialog_btn")
                                    ) {
                                        Icon(Icons.Default.CompareArrows, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("مقایسه با لیست سرکارگر")
                                    }
                                }
                            }
                        }

                        if (uiState.myCounts.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("هنوز هیچ شمارش مستقلی ثبت نشده است.", color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        } else {
                            val sortedCounts = uiState.myCounts.sortedByDescending { it.date }
                            items(sortedCounts) { mc ->
                                val crew = crewsMap[mc.crewId]
                                val jDate = JalaliDateHelper.isoToJalali(mc.date)

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth().testTag("mycount_card_${mc.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth().padding(14.dp)
                                    ) {
                                        Column {
                                            Text(jDate.toDisplayFull(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text("دسته: ${crew?.name ?: "نامشخص"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            if (mc.note.isNotBlank()) {
                                                Text(mc.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "${NumberFormatHelper.toPersianDigits(mc.headcount)} نفر",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            IconButton(onClick = { viewModel.deleteMyCount(mc.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = ColorDangerRed)
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

    // --- CREATE CREW DIALOG ---
    if (showCreateCrewDialog) {
        CrewDialog(
            uiState = uiState,
            onDismiss = { showCreateCrewDialog = false },
            onSave = { name, kind, foremanId, dailyWage, effectiveFrom, note ->
                viewModel.createCrew(name, kind, foremanId, dailyWage, effectiveFrom, note) {
                    showCreateCrewDialog = false
                    Toast.makeText(context, "دسته با موفقیت ایجاد شد", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // --- EDIT CREW DIALOG ---
    if (crewToEdit != null) {
        CrewDialog(
            initialCrew = crewToEdit,
            uiState = uiState,
            onDismiss = { crewToEdit = null },
            onSave = { name, kind, foremanId, _, _, note ->
                viewModel.updateCrew(
                    crewToEdit!!.copy(name = name, kind = kind, foremanId = foremanId, note = note)
                ) {
                    crewToEdit = null
                    Toast.makeText(context, "دسته ویرایش شد", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // --- DELETE CREW CONFIRMATION ---
    if (crewToDelete != null) {
        val target = crewToDelete!!
        AlertDialog(
            onDismissRequest = { crewToDelete = null },
            title = { Text("حذف دسته «${target.name}»") },
            text = { Text("آیا از حذف این دسته اطمینان داری؟ اگر سابقه‌ای برای این دسته ثبت شده باشد حذف نخواهد شد.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCrew(target.id) { deleted ->
                            if (deleted) {
                                Toast.makeText(context, "دسته حذف شد", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "این دسته سابقه دارد و قابل حذف نیست", Toast.LENGTH_LONG).show()
                            }
                            crewToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerRed)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { crewToDelete = null }) { Text("انصراف") }
            }
        )
    }

    // --- ATTENDANCE DIALOG ---
    if (showAttendanceDialog) {
        AttendanceDialog(
            initialAttendance = attendanceToEdit,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = {
                showAttendanceDialog = false
                attendanceToEdit = null
            },
            onSave = { date, farmId, plotId, crewId, tagId, lines, headcount, countTicks, foreman, note ->
                if (attendanceToEdit != null) {
                    viewModel.updateAttendance(
                        attendanceToEdit!!.copy(
                            date = date,
                            farmId = farmId,
                            plotId = plotId,
                            crewId = crewId,
                            tagId = tagId,
                            lines = lines,
                            headcount = headcount,
                            countTicks = countTicks,
                            foreman = foreman,
                            note = note
                        )
                    ) {
                        showAttendanceDialog = false
                        attendanceToEdit = null
                        Toast.makeText(context, "حضور و غیاب ویرایش شد", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    viewModel.createAttendance(
                        date = date,
                        farmId = farmId,
                        plotId = plotId,
                        crewId = crewId,
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
            }
        )
    }

    // --- PIECEWORK DIALOG ---
    if (showPieceWorkDialog) {
        PieceWorkDialog(
            initialPieceWork = pieceWorkToEdit,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = {
                showPieceWorkDialog = false
                pieceWorkToEdit = null
            },
            onSave = { date, farmId, plotId, tagId, jobType, size, rate, lines, note ->
                if (pieceWorkToEdit != null) {
                    viewModel.updatePieceWork(
                        pieceWorkToEdit!!.copy(
                            date = date,
                            farmId = farmId,
                            plotId = plotId,
                            tagId = tagId,
                            jobType = jobType,
                            size = size,
                            rate = rate,
                            lines = lines,
                            note = note
                        )
                    ) {
                        showPieceWorkDialog = false
                        pieceWorkToEdit = null
                        Toast.makeText(context, "کار پیمانی ویرایش شد", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    viewModel.createPieceWork(
                        date = date,
                        farmId = farmId,
                        plotId = plotId,
                        tagId = tagId,
                        jobType = jobType,
                        size = size,
                        rate = rate,
                        lines = lines,
                        note = note
                    ) {
                        showPieceWorkDialog = false
                        Toast.makeText(context, "کار پیمانی ثبت شد", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // --- MY COUNT DIALOG ---
    if (showMyCountDialog) {
        MyCountDialog(
            uiState = uiState,
            onDismiss = { showMyCountDialog = false },
            onSave = { date, crewId, headcount, note ->
                viewModel.createMyCount(date, crewId, headcount, note) {
                    showMyCountDialog = false
                    Toast.makeText(context, "شمارش ثبت شد", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // --- ATTENDANCE COMPARISON DIALOG ---
    if (showComparisonDialog) {
        AttendanceComparisonDialog(
            uiState = uiState,
            onDismiss = { showComparisonDialog = false }
        )
    }
}
