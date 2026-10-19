package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.ui.components.EmploymentDialog
import com.example.ui.components.TeamManagementDialog
import com.example.ui.components.JalaliDatePickerDialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.entity.CrewEntity
import com.example.data.entity.CrewMemberEntity
import com.example.data.entity.EmploymentEntity
import com.example.data.entity.PersonEntity
import com.example.data.entity.RoleEntity
import com.example.lib.JalaliDateHelper
import com.example.lib.NumberFormatHelper
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.ConfirmDialog
import com.example.ui.theme.ColorDangerRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonsScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedRoleFilter by remember { mutableStateOf<String?>(null) }

    var showCreatePersonDialog by remember { mutableStateOf(false) }
    var personToEdit by remember { mutableStateOf<PersonEntity?>(null) }
    var personToDelete by remember { mutableStateOf<PersonEntity?>(null) }
    var personToEmploy by remember { mutableStateOf<PersonEntity?>(null) }
    var personToManageTeam by remember { mutableStateOf<PersonEntity?>(null) }
    var showRolesManagementDialog by remember { mutableStateOf(false) }
    var showInactiveSection by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }

    if (personToEmploy != null) {
        val person = personToEmploy!!
        val employment = uiState.employments.find { it.personId == person.id && !it.deleted }
        val salaryRate = uiState.salaryRates.find { it.personId == person.id && !it.deleted }?.monthlySalary ?: 0L
        EmploymentDialog(
            personId = person.id,
            personName = person.name,
            initialEmployment = employment,
            initialSalary = salaryRate,
            onDismiss = { personToEmploy = null },
            onSave = { e, salary, applyUpToToday ->
                viewModel.saveEmployment(e, salary, applyUpToToday)
                personToEmploy = null
            }
        )
    }

    if (personToManageTeam != null) {
        val foremanPerson = personToManageTeam!!
        TeamManagementDialog(
            foreman = foremanPerson,
            crews = uiState.crews,
            crewMembers = uiState.crewMembers,
            allWorkers = uiState.persons.filter { it.id != foremanPerson.id && it.active },
            onDismiss = { personToManageTeam = null },
            onSave = { workerIds ->
                viewModel.saveTeamForForeman(foremanPerson, workerIds)
                personToManageTeam = null
            }
        )
    }

    val filteredPersons = remember(uiState.persons, searchQuery, selectedRoleFilter) {
        uiState.persons.filter { person ->
            val matchesSearch = searchQuery.isBlank() || person.name.contains(searchQuery, ignoreCase = true)
            val personRoles = viewModel.gateway.parseRoleIds(person.roleIds)
            val matchesRole = selectedRoleFilter == null || personRoles.contains(selectedRoleFilter)
            matchesSearch && matchesRole
        }
    }

    val activePersons = filteredPersons.filter { it.active }
    val inactivePersons = filteredPersons.filter { !it.active }
    val rolesMap = remember(uiState.roles) { uiState.roles.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("اشخاص", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("persons_back_btn")
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = { showRolesManagementDialog = true },
                        modifier = Modifier.testTag("manage_roles_btn")
                    ) {
                        Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نقش‌ها")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreatePersonDialog = true },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("شخص تازه") },
                modifier = Modifier.testTag("add_person_fab")
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
            // --- SEARCH BAR ---
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("جستجو بر اساس نام...") },
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
                        .testTag("person_search_input")
                )
            }

            // --- ROLE FILTER CHIPS ---
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedRoleFilter == null,
                            onClick = { selectedRoleFilter = null },
                            label = { Text("همه") }
                        )
                    }
                    items(uiState.roles.filter { !it.hidden }) { role ->
                        FilterChip(
                            selected = selectedRoleFilter == role.id,
                            onClick = {
                                selectedRoleFilter = if (selectedRoleFilter == role.id) null else role.id
                            },
                            label = { Text(role.name) }
                        )
                    }
                }
            }

            // --- EMPTY STATE ---
            if (activePersons.isEmpty() && inactivePersons.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp)
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedRoleFilter != null) {
                                "شخصی با این مشخصات پیدا نشد."
                            } else {
                                "هنوز شخصی ثبت نشده است.\nبرای اضافه کردن، دکمه‌ی «شخص تازه» را بزنید."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // --- ACTIVE PERSONS ---
            items(activePersons, key = { it.id }) { person ->
                PersonCard(
                    person = person,
                    rolesMap = rolesMap,
                    onCallPhone = { phone ->
                        try {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    onEdit = { personToEdit = person },
                    onToggleActive = { viewModel.togglePersonActive(person) },
                    onDelete = { personToDelete = person },
                    onSetEmployment = { personToEmploy = person },
                    onManageTeam = { personToManageTeam = person }
                )
            }

            // --- INACTIVE PERSONS ---
            if (inactivePersons.isNotEmpty()) {
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
                                val countFa = NumberFormatHelper.toPersianDigits(inactivePersons.size)
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
                                    inactivePersons.forEach { person ->
                                        PersonCard(
                                            person = person,
                                            rolesMap = rolesMap,
                                            onCallPhone = { phone ->
                                                try {
                                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {}
                                            },
                                            onEdit = { personToEdit = person },
                                            onToggleActive = { viewModel.togglePersonActive(person) },
                                            onDelete = { personToDelete = person },
                                            onSetEmployment = { personToEmploy = person },
                                            onManageTeam = { personToManageTeam = person }
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

    // --- CREATE PERSON DIALOG ---
    if (showCreatePersonDialog) {
        PersonFormDialog(
            availableRoles = uiState.roles.filter { !it.hidden },
            allPersons = uiState.persons,
            crews = uiState.crews,
            crewMembers = uiState.crewMembers,
            onDismiss = { showCreatePersonDialog = false },
            onSave = { name, phone, roleIds, note, foremanId, workerIds, monthlySalary, salaryMonth, applyRetro ->
                viewModel.savePersonWithRelations(
                    personId = null,
                    name = name,
                    phone = phone,
                    roleIds = roleIds,
                    note = note,
                    foremanId = foremanId,
                    workerIds = workerIds,
                    monthlySalary = monthlySalary,
                    salaryFromMonth = salaryMonth,
                    applyRetroactiveSalary = applyRetro,
                    onSuccess = { showCreatePersonDialog = false }
                )
            }
        )
    }

    // --- EDIT PERSON DIALOG ---
    if (personToEdit != null) {
        val p = personToEdit!!
        val emp = uiState.employments.find { it.personId == p.id && !it.deleted }
        val sal = uiState.salaryRates.find { it.personId == p.id && !it.deleted }?.monthlySalary ?: 0L
        PersonFormDialog(
            initialPerson = p,
            initialEmployment = emp,
            initialSalary = sal,
            availableRoles = uiState.roles, // Show even hidden roles if this person already has it
            allPersons = uiState.persons,
            crews = uiState.crews,
            crewMembers = uiState.crewMembers,
            onDismiss = { personToEdit = null },
            onSave = { name, phone, roleIds, note, foremanId, workerIds, monthlySalary, salaryMonth, applyRetro ->
                viewModel.savePersonWithRelations(
                    personId = p.id,
                    name = name,
                    phone = phone,
                    roleIds = roleIds,
                    note = note,
                    foremanId = foremanId,
                    workerIds = workerIds,
                    monthlySalary = monthlySalary,
                    salaryFromMonth = salaryMonth,
                    applyRetroactiveSalary = applyRetro,
                    onSuccess = { personToEdit = null }
                )
            }
        )
    }

    // --- DELETE PERSON DIALOG ---
    if (personToDelete != null) {
        ConfirmDialog(
            title = "حذف شخص",
            message = "آیا از حذف «${personToDelete!!.name}» مطمئن هستید؟",
            confirmText = "حذف",
            isDestructive = true,
            onConfirm = {
                viewModel.deletePerson(personToDelete!!) { deleted ->
                    if (!deleted) {
                        alertMessage = "این شخص دارای ثبت در سیستم است و فقط غیرفعال شد."
                    }
                    personToDelete = null
                }
            },
            onDismiss = { personToDelete = null }
        )
    }

    // --- MANAGE ROLES DIALOG ---
    if (showRolesManagementDialog) {
        ManageRolesDialog(
            roles = uiState.roles,
            onDismiss = { showRolesManagementDialog = false },
            onCreateRole = { roleName ->
                viewModel.createRole(roleName) {}
            },
            onRenameRole = { role, newName ->
                viewModel.updateRoleName(role, newName) {}
            },
            onToggleHidden = { role ->
                viewModel.toggleRoleHidden(role)
            },
            onDeleteRole = { role ->
                viewModel.deleteRole(role) {}
            }
        )
    }

    // --- ALERT ---
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

@Composable
fun PersonCard(
    person: PersonEntity,
    rolesMap: Map<String, RoleEntity>,
    onCallPhone: (String) -> Unit,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit,
    onSetEmployment: () -> Unit,
    onManageTeam: (() -> Unit)? = null
) {
    val roleIds = try {
        val arr = org.json.JSONArray(person.roleIds)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list.add(arr.getString(i))
        list
    } catch (_: Exception) {
        emptyList()
    }
    
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (person.active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (person.active) 2.dp else 0.dp),
        modifier = Modifier.fillMaxWidth().testTag("person_card_${person.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row {
                    if (roleIds.any { rolesMap[it]?.builtInKey == "foreman" || rolesMap[it]?.name == "سرکارگر" }) {
                        IconButton(onClick = { onManageTeam?.invoke() }) {
                            Icon(Icons.Default.GroupAdd, contentDescription = "مدیریت تیم", modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(onClick = onSetEmployment) {
                        Icon(Icons.Default.Work, contentDescription = "تنظیم کار", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "ویرایش", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onToggleActive) {
                        Icon(
                            imageVector = if (person.active) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (person.active) "غیرفعال" else "فعال",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = ColorDangerRed, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Roles as chips

            if (roleIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    roleIds.forEach { rid ->
                        val role = rolesMap[rid]
                        if (role != null) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text(role.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            // Phone
            if (person.phone.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onCallPhone(person.phone) }
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "تماس",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = NumberFormatHelper.toPersianDigits(person.phone),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Note
            if (person.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = person.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonFormDialog(
    initialPerson: PersonEntity? = null,
    initialEmployment: EmploymentEntity? = null,
    initialSalary: Long = 0L,
    availableRoles: List<RoleEntity>,
    allPersons: List<PersonEntity>,
    crews: List<CrewEntity>,
    crewMembers: List<CrewMemberEntity>,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, roleIds: List<String>, note: String, foremanId: String?, workerIds: List<String>, monthlySalary: Long?, salaryFromMonth: String?, applyRetroactive: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialPerson?.name ?: "") }
    var phone by remember { mutableStateOf(initialPerson?.phone ?: "") }
    var note by remember { mutableStateOf(initialPerson?.note ?: "") }

    val initialSelectedRoleIds = remember(initialPerson) {
        if (initialPerson != null) {
            try {
                val arr = org.json.JSONArray(initialPerson.roleIds)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) list.add(arr.getString(i))
                list
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()
    }

    var selectedRoleIds by remember { mutableStateOf(initialSelectedRoleIds.toSet()) }

    // Logic for Foreman/Worker Selection
    val foremanRole = availableRoles.find { it.builtInKey == "foreman" || it.name == "سرکارگر" }
    val workerRole = availableRoles.find { it.builtInKey == "worker" || it.name == "کارگر" }
    val isForeman = foremanRole != null && selectedRoleIds.contains(foremanRole.id)
    val isWorker = workerRole != null && selectedRoleIds.contains(workerRole.id)

    // Salary state
    var monthlySalaryInput by remember { mutableStateOf(if (initialSalary > 0) initialSalary.toString() else "") }
    val currentJalali = remember { JalaliDateHelper.today() }
    val initialIsoDate = remember(initialEmployment) {
        if (!initialEmployment?.salaryFromMonth.isNullOrBlank()) {
            val parts = initialEmployment!!.salaryFromMonth.split("-")
            val yr = parts.getOrNull(0)?.toIntOrNull() ?: currentJalali.year
            val mo = parts.getOrNull(1)?.toIntOrNull() ?: currentJalali.month
            val g = JalaliDateHelper.toGregorian(yr, mo, 1)
            JalaliDateHelper.toIsoDate(g)
        } else {
            JalaliDateHelper.toIsoDate(java.time.LocalDate.now())
        }
    }
    var selectedSalaryIsoDate by remember { mutableStateOf(initialIsoDate) }
    var showSalaryDatePicker by remember { mutableStateOf(false) }
    var applyRetroactiveSalary by remember { mutableStateOf(true) }
    val selectedSalaryJalali = remember(selectedSalaryIsoDate) { JalaliDateHelper.fromIsoDate(selectedSalaryIsoDate) }
    val salaryMonthKey = selectedSalaryJalali.monthKey
    val isPastMonth = salaryMonthKey < currentJalali.monthKey
    val salaryLong = NumberFormatHelper.parsePersianLong(monthlySalaryInput) ?: 0L

    val initialForemanId = remember(initialPerson, crews, crewMembers) {
        if (initialPerson != null) {
            val myMember = crewMembers.find { it.personId == initialPerson.id && !it.deleted }
            crews.find { it.id == myMember?.crewId && !it.deleted }?.foremanId
        } else null
    }
    var selectedForemanId by remember { mutableStateOf<String?>(initialForemanId) }

    val initialWorkerIds = remember(initialPerson, crews, crewMembers) {
        if (initialPerson != null) {
            val myCrew = crews.find { it.foremanId == initialPerson.id && !it.deleted }
            if (myCrew != null) {
                crewMembers.filter { it.crewId == myCrew.id && !it.deleted }.map { it.personId }.toSet()
            } else emptySet()
        } else emptySet()
    }
    var selectedWorkerIds by remember { mutableStateOf(initialWorkerIds) }
    var isForemanExpanded by remember { mutableStateOf(false) }

    val eligibleForemen = remember(allPersons, availableRoles, initialPerson) {
        allPersons.filter { p ->
            val pRoles = try {
                val arr = org.json.JSONArray(p.roleIds)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) list.add(arr.getString(i))
                list
            } catch (_: Exception) { emptyList<String>() }
            pRoles.any { rid ->
                availableRoles.find { it.id == rid }?.let { it.builtInKey == "foreman" || it.name == "سرکارگر" } == true
            } && p.active && p.id != initialPerson?.id
        }
    }

    val candidateWorkers = remember(allPersons, initialPerson) {
        allPersons.filter { it.id != initialPerson?.id && it.active }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialPerson == null) "افزودن شخص تازه" else "ویرایش شخص",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("نام و نام خانوادگی *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("person_name_input")
                    )
                }

                item {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("شماره تماس") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("person_phone_input")
                    )
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("نقش‌ها:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            availableRoles.forEach { role ->
                                val isChecked = selectedRoleIds.contains(role.id)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedRoleIds = if (isChecked) selectedRoleIds - role.id else selectedRoleIds + role.id
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Checkbox(checked = isChecked, onCheckedChange = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(role.name, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                item {
                    androidx.compose.animation.AnimatedVisibility(visible = isWorker) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("انتخاب سرکارگر", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                if (eligibleForemen.isEmpty()) {
                                    Text(
                                        text = "سرکارگری در سامانه ثبت نشده است.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                } else {
                                    ExposedDropdownMenuBox(
                                        expanded = isForemanExpanded,
                                        onExpandedChange = { isForemanExpanded = !isForemanExpanded },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val foreman = allPersons.find { it.id == selectedForemanId }
                                        OutlinedTextField(
                                            value = foreman?.name ?: "انتخاب سرکارگر (اختیاری)",
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("سرکارگر") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isForemanExpanded) },
                                            modifier = Modifier.fillMaxWidth().menuAnchor()
                                        )
                                        ExposedDropdownMenu(
                                            expanded = isForemanExpanded,
                                            onDismissRequest = { isForemanExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("بدون سرکارگر") },
                                                onClick = {
                                                    selectedForemanId = null
                                                    isForemanExpanded = false
                                                }
                                            )
                                            eligibleForemen.forEach { f ->
                                                DropdownMenuItem(
                                                    text = { Text(f.name) },
                                                    onClick = {
                                                        selectedForemanId = f.id
                                                        isForemanExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    androidx.compose.animation.AnimatedVisibility(visible = isForeman) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("کارگران زیرمجموعه", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                if (candidateWorkers.isEmpty()) {
                                    Text(
                                        text = "کارگر دیگری در سامانه ثبت نشده است.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                } else {
                                    candidateWorkers.forEach { worker ->
                                        val isChecked = selectedWorkerIds.contains(worker.id)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedWorkerIds = if (isChecked) selectedWorkerIds - worker.id else selectedWorkerIds + worker.id
                                                }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    selectedWorkerIds = if (checked) selectedWorkerIds + worker.id else selectedWorkerIds - worker.id
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(worker.name, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Monthly Salary Section
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("حقوق ثابت ماهانه (اختیاری)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = monthlySalaryInput,
                                onValueChange = { monthlySalaryInput = it },
                                label = { Text("حقوق ماهانه (تومان)") },
                                singleLine = true,
                                supportingText = if (salaryLong > 0) {
                                    { Text(NumberFormatHelper.formatTomanFull(salaryLong), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                                } else null,
                                modifier = Modifier.fillMaxWidth().testTag("person_monthly_salary_input")
                            )

                            if (salaryLong > 0) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth().clickable { showSalaryDatePicker = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("تاریخ شروع حقوق:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            Text("${selectedSalaryJalali.monthName} ${NumberFormatHelper.toPersianDigits(selectedSalaryJalali.year)}", fontWeight = FontWeight.Bold)
                                        }
                                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                if (isPastMonth) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().clickable { applyRetroactiveSalary = !applyRetroactiveSalary }.padding(vertical = 4.dp)
                                    ) {
                                        Checkbox(checked = applyRetroactiveSalary, onCheckedChange = { applyRetroactiveSalary = it })
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "اعمال حقوق و کارکرد برای ماه‌های گذشته تا امروز",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("توضیحات...") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        phone,
                        selectedRoleIds.toList(),
                        note,
                        selectedForemanId,
                        selectedWorkerIds.toList(),
                        if (salaryLong > 0) salaryLong else null,
                        salaryMonthKey,
                        applyRetroactiveSalary
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("save_person_btn")
            ) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )

    if (showSalaryDatePicker) {
        JalaliDatePickerDialog(
            initialIso = selectedSalaryIsoDate,
            onDismiss = { showSalaryDatePicker = false },
            onDateSelected = { iso ->
                selectedSalaryIsoDate = iso
                showSalaryDatePicker = false
            }
        )
    }
}

@Composable
fun ManageRolesDialog(
    roles: List<RoleEntity>,
    onDismiss: () -> Unit,
    onCreateRole: (name: String) -> Unit,
    onRenameRole: (role: RoleEntity, newName: String) -> Unit,
    onToggleHidden: (role: RoleEntity) -> Unit,
    onDeleteRole: (role: RoleEntity) -> Unit
) {
    var showCreateRoleDialog by remember { mutableStateOf(false) }
    var roleToRename by remember { mutableStateOf<RoleEntity?>(null) }
    var roleToDelete by remember { mutableStateOf<RoleEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("مدیریت نقش‌ها", fontWeight = FontWeight.Bold)
                TextButton(onClick = { showCreateRoleDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("نقش تازه")
                }
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(roles) { role ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = role.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (role.builtInKey != null) {
                                    Text(
                                        text = if (role.hidden) "نقش آماده (پنهان)" else "نقش آماده",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Row {
                                IconButton(onClick = { roleToRename = role }) {
                                    Icon(Icons.Default.Edit, contentDescription = "تغییر نام", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onToggleHidden(role) }) {
                                    Icon(
                                        imageVector = if (role.hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (role.hidden) "پنهان" else "آشکار",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                if (role.builtInKey == null) {
                                    IconButton(onClick = { roleToDelete = role }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = ColorDangerRed, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("بستن")
            }
        }
    )

    // Create Role Subdialog
    if (showCreateRoleDialog) {
        var newRoleName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateRoleDialog = false },
            title = { Text("ساخت نقش تازه") },
            text = {
                OutlinedTextField(
                    value = newRoleName,
                    onValueChange = { newRoleName = it },
                    label = { Text("نام نقش (مثلاً راننده، حسابدار...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateRole(newRoleName)
                        showCreateRoleDialog = false
                    },
                    enabled = newRoleName.isNotBlank()
                ) {
                    Text("افزودن")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCreateRoleDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Rename Role Subdialog
    if (roleToRename != null) {
        var editedRoleName by remember { mutableStateOf(roleToRename!!.name) }
        AlertDialog(
            onDismissRequest = { roleToRename = null },
            title = { Text("تغییر نام نقش") },
            text = {
                OutlinedTextField(
                    value = editedRoleName,
                    onValueChange = { editedRoleName = it },
                    label = { Text("نام جدید") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameRole(roleToRename!!, editedRoleName)
                        roleToRename = null
                    },
                    enabled = editedRoleName.isNotBlank()
                ) {
                    Text("ذخیره")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { roleToRename = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Delete Custom Role Confirmation
    if (roleToDelete != null) {
        ConfirmDialog(
            title = "حذف نقش «${roleToDelete!!.name}»",
            message = "با حذف این نقش، این نقش از روی تمام اشخاصی که آن را دارند برداشته می‌شود. آیا مطمئنید؟",
            confirmText = "حذف نقش",
            isDestructive = true,
            onConfirm = {
                onDeleteRole(roleToDelete!!)
                roleToDelete = null
            },
            onDismiss = { roleToDelete = null }
        )
    }
}
