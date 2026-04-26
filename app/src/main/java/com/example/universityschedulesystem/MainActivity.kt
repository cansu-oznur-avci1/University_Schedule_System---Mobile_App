package com.example.universityschedulesystem

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.universityschedulesystem.data.*
import com.example.universityschedulesystem.ui.theme.UniversityScheduleSystemTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UniversityScheduleSystemTheme {
                UniversityScheduleSystemApp()
            }
        }
    }
}

enum class AppDestinations(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    CALENDAR("Calendar", Icons.Default.DateRange),
    DATA("Data", Icons.AutoMirrored.Filled.List),
    SETTINGS("Settings", Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversityScheduleSystemApp(viewModel: MainViewModel = viewModel()) {
    var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }
    val scope = rememberCoroutineScope()

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                val isVisible = if (viewModel.userSettings.position == Position.LECTURER) {
                    destination == AppDestinations.HOME || destination == AppDestinations.CALENDAR
                } else {
                    true
                }

                if (isVisible) {
                    item(
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        selected = destination == currentDestination,
                        onClick = { currentDestination = destination }
                    )
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("University Scheduler", fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        if (viewModel.userSettings.isRegistered) {
                            IconButton(onClick = { 
                                viewModel.logout()
                                currentDestination = AppDestinations.HOME
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                when (currentDestination) {
                    AppDestinations.HOME -> HomeScreen(viewModel, onNavigateToSettings = { currentDestination = AppDestinations.SETTINGS })
                    AppDestinations.CALENDAR -> CalendarScreen(viewModel)
                    AppDestinations.DATA -> DataScreen(viewModel, onGoToCalendar = { currentDestination = AppDestinations.CALENDAR })
                    AppDestinations.SETTINGS -> SettingsScreen(
                        userSettings = viewModel.userSettings,
                        onSave = { 
                            viewModel.saveSettings(it)
                            currentDestination = AppDestinations.HOME
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: MainViewModel, onNavigateToSettings: () -> Unit) {
    val userSettings = viewModel.userSettings
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val gradient = Brush.verticalGradient(
        colors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (userSettings.isRegistered) {
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        if (userSettings.position == Position.ADMIN) Icons.Default.AdminPanelSettings else Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Welcome back,",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (userSettings.position == Position.ADMIN) "Administrator" else "${userSettings.name} ${userSettings.surname}",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        ),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    InfoRow(label = "Department", value = userSettings.department?.displayName ?: "N/A")
                    InfoRow(label = "Role", value = userSettings.position?.displayName ?: "N/A")
                    if (userSettings.position == Position.LECTURER) {
                        userSettings.educationLevel?.let {
                            InfoRow(label = "Level", value = it.displayName)
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.School, 
                        contentDescription = null, 
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("University Scheduler", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Secure Login", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    
                    var username by remember { mutableStateOf("") }
                    var password by remember { mutableStateOf("") }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                val success = viewModel.login(username, password)
                                if (!success) Toast.makeText(context, "Invalid Credentials", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Login", fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text("Or", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = { 
                            viewModel.userSettings = UserSettings(position = Position.ADMIN)
                            onNavigateToSettings()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Continue as Administrator")
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(viewModel: MainViewModel, onGoToCalendar: () -> Unit) {
    val context = LocalContext.current
    var showLogs by remember { mutableStateOf(false) }
    var selectedLecturerDetails by remember { mutableStateOf<Lecturer?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    
    val excelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { viewModel.importDataFromExcel(context, it) }
        }
    )

    val templateSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        onResult = { uri ->
            uri?.let { viewModel.writeExcelTemplate(context, it) }
        }
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Data Management", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (viewModel.userSettings.position == Position.ADMIN) {
                IconButton(onClick = { showClearConfirm = true }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Database", tint = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = { showLogs = !showLogs }) {
                Icon(if (showLogs) Icons.AutoMirrored.Filled.ViewList else Icons.Default.History, contentDescription = "Toggle Logs")
            }
        }

        if (showLogs) {
            Text("Audit Logs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(viewModel.auditLogs) { log ->
                    ListItem(
                        headlineContent = { Text("${log.user}: ${log.action}") },
                        supportingContent = { Text(log.details) },
                        overlineContent = { Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(log.timestamp))) }
                    )
                }
            }
        } else if (!viewModel.isDataImported) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // New Visual Section for Excel Template
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.TableChart, 
                            contentDescription = null, 
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Import Database", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Start by downloading our standard Excel template to format your lecturer and course data correctly.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { templateSaverLauncher.launch("University_Template.xlsx") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Get Excel Template")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                FloatingActionButton(
                    onClick = { 
                        excelPickerLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel")) 
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = "Import Excel")
                }
                Text("Upload Excel File", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lecturers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { 
                        excelPickerLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel")) 
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Re-import")
                    }
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(viewModel.lecturers) { lecturer ->
                        ListItem(
                            headlineContent = { 
                                Text(
                                    "${lecturer.title} ${lecturer.name}", 
                                    modifier = Modifier.clickable { selectedLecturerDetails = lecturer },
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                ) 
                            },
                            supportingContent = { Text("User: ${lecturer.username}") },
                            trailingContent = {
                                IconButton(onClick = { 
                                    viewModel.selectedLecturerForCalendar = lecturer
                                    onGoToCalendar()
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go to Calendar")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Database") },
            text = { Text("Are you sure you want to delete all lecturers, courses, and logs? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearDatabase()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (selectedLecturerDetails != null) {
        val lecturer = selectedLecturerDetails!!
        val lecturerCourses = viewModel.courses.filter { it.lecturerName.contains(lecturer.name) }
        
        AlertDialog(
            onDismissRequest = { selectedLecturerDetails = null },
            title = { Text("${lecturer.title} ${lecturer.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Department: ${lecturer.department.displayName}")
                    Text("Username: ${lecturer.username}")
                    Text("Password: ${lecturer.password}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Assigned Courses:", fontWeight = FontWeight.Bold)
                    if (lecturerCourses.isEmpty()) {
                        Text("None", style = MaterialTheme.typography.bodySmall)
                    } else {
                        lecturerCourses.forEach { course ->
                            Text("- ${course.code}: ${course.name}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedLecturerDetails = null }) { Text("Close") }
            }
        )
    }
}

@Composable
fun CalendarScreen(viewModel: MainViewModel) {
    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
    val hours = (8..17).map { "$it:00" }

    var selectedLecturer by remember { mutableStateOf(viewModel.selectedLecturerForCalendar ?: viewModel.loggedInLecturer) }
    var selectedSlotsForAssignment by remember { mutableStateOf(setOf<Pair<String, String>>()) }
    var courseToAssign by remember { mutableStateOf<Course?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Weekly Schedule", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (viewModel.userSettings.position == Position.ADMIN) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text(selectedLecturer?.name ?: "Select Lecturer")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        viewModel.lecturers.forEach { lecturer ->
                            DropdownMenuItem(
                                text = { Text(lecturer.name) },
                                onClick = {
                                    selectedLecturer = lecturer
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            } else {
                Text(selectedLecturer?.name ?: "", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedLecturer == null) {
            Text("Please select a lecturer to view their schedule.")
        } else {
            if (viewModel.userSettings.position == Position.ADMIN && selectedSlotsForAssignment.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text("Selected: ${selectedSlotsForAssignment.size} blocks", modifier = Modifier.weight(1f))
                    Button(onClick = { courseToAssign = viewModel.courses.filter { it.lecturerName.contains(selectedLecturer?.name ?: "") && it.scheduledSlots.isEmpty() }.firstOrNull() }) {
                        Text("Assign Course")
                    }
                    TextButton(onClick = { selectedSlotsForAssignment = emptySet() }) {
                        Text("Cancel")
                    }
                }
            }

            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    Spacer(modifier = Modifier.height(40.dp).width(60.dp))
                    hours.forEach { hour ->
                        Box(modifier = Modifier.height(60.dp).width(60.dp), contentAlignment = Alignment.Center) {
                            Text(hour, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                days.forEach { day ->
                    Column {
                        Box(modifier = Modifier.height(40.dp).width(120.dp), contentAlignment = Alignment.Center) {
                            Text(day, fontWeight = FontWeight.Bold)
                        }
                        hours.forEach { hour ->
                            val isAvailable = selectedLecturer?.availability?.find { it.day == day && it.timeSlot == hour }?.isAvailable ?: true
                            val assignedCourse = viewModel.courses.find { course ->
                                course.lecturerName.contains(selectedLecturer?.name ?: "") && 
                                course.scheduledSlots.any { it.day == day && it.timeSlot == hour }
                            }
                            val isSelected = selectedSlotsForAssignment.contains(day to hour)

                            Box(
                                modifier = Modifier
                                    .height(60.dp)
                                    .width(120.dp)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when {
                                            assignedCourse != null -> MaterialTheme.colorScheme.primaryContainer
                                            isSelected -> MaterialTheme.colorScheme.secondaryContainer
                                            isAvailable -> Color(0xFFC8E6C9)
                                            else -> Color(0xFFFFCDD2)
                                        }
                                    )
                                    .border(
                                        if (isSelected) 2.dp else 1.dp, 
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), 
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        if (viewModel.userSettings.position == Position.LECTURER && viewModel.loggedInLecturer?.id == selectedLecturer?.id) {
                                            val currentAvailability = selectedLecturer?.availability?.toMutableList() ?: mutableListOf()
                                            val index = currentAvailability.indexOfFirst { it.day == day && it.timeSlot == hour }
                                            if (index != -1) {
                                                currentAvailability[index] = currentAvailability[index].copy(isAvailable = !isAvailable)
                                            } else {
                                                currentAvailability.add(AvailabilitySlot(day, hour, !isAvailable))
                                            }
                                            viewModel.updateLecturerAvailability(selectedLecturer!!, currentAvailability)
                                            selectedLecturer = selectedLecturer?.copy(availability = currentAvailability)
                                        } else if (viewModel.userSettings.position == Position.ADMIN && isAvailable && assignedCourse == null) {
                                            selectedSlotsForAssignment = if (isSelected) {
                                                selectedSlotsForAssignment - (day to hour)
                                            } else {
                                                selectedSlotsForAssignment + (day to hour)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (assignedCourse != null) {
                                        Text(assignedCourse.code, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                                        if (viewModel.userSettings.position == Position.ADMIN) {
                                            Icon(
                                                Icons.Default.Delete, 
                                                contentDescription = "Remove", 
                                                modifier = Modifier.size(16.dp).clickable { viewModel.removeCourseAssignment(assignedCourse.id) },
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    } else {
                                        Text(if (isAvailable) "Available" else "Busy", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (courseToAssign != null) {
        val lecturerCourses = viewModel.courses.filter { it.lecturerName.contains(selectedLecturer?.name ?: "") && it.scheduledSlots.isEmpty() }
        
        AlertDialog(
            onDismissRequest = { courseToAssign = null },
            title = { Text("Assign Course to ${selectedSlotsForAssignment.size} blocks") },
            text = {
                if (lecturerCourses.isEmpty()) {
                    Text("No unassigned courses for this lecturer.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(lecturerCourses) { course ->
                            ListItem(
                                headlineContent = { Text(course.code) },
                                supportingContent = { Text(course.name) },
                                modifier = Modifier.clickable {
                                    viewModel.assignCourse(course.id, selectedSlotsForAssignment.map { ScheduledSlot(it.first, it.second) })
                                    selectedSlotsForAssignment = emptySet()
                                    courseToAssign = null
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { courseToAssign = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsScreen(userSettings: UserSettings, onSave: (UserSettings) -> Unit) {
    var name by remember { mutableStateOf(userSettings.name) }
    var surname by remember { mutableStateOf(userSettings.surname) }
    var department by remember { mutableStateOf(userSettings.department) }
    var position by remember { mutableStateOf(userSettings.position) }
    var educationLevel by remember { mutableStateOf(userSettings.educationLevel) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            if (position == Position.ADMIN) "Administrator Setup" else "Profile Settings", 
            style = MaterialTheme.typography.headlineMedium, 
            fontWeight = FontWeight.Bold
        )

        if (position == Position.LECTURER) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = surname, onValueChange = { surname = it }, label = { Text("Surname") }, modifier = Modifier.fillMaxWidth())
        }

        Text("Department", style = MaterialTheme.typography.labelLarge)
        Department.entries.forEach { dept ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { department = dept }) {
                RadioButton(selected = department == dept, onClick = { department = dept })
                Text(dept.displayName)
            }
        }

        if (position == Position.LECTURER) {
            Text("Education Level", style = MaterialTheme.typography.labelLarge)
            EducationLevel.entries.forEach { level ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { educationLevel = level }) {
                    RadioButton(selected = educationLevel == level, onClick = { educationLevel = level })
                    Text(level.displayName)
                }
            }
        }

        Button(
            onClick = { onSave(UserSettings(name, surname, department, position, educationLevel, true, userSettings.lecturerId)) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text(if (position == Position.ADMIN) "Complete Admin Setup" else "Save Profile")
        }
    }
}
