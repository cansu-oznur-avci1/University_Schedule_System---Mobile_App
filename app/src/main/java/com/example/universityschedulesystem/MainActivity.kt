package com.example.universityschedulesystem

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.universityschedulesystem.data.*
import com.example.universityschedulesystem.ui.CourseViewModel
import com.example.universityschedulesystem.ui.theme.UniversityScheduleSystemTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UniversityScheduleSystemTheme {
                val db = AppDatabase.getDatabase(LocalContext.current)
                val repository = CourseRepository(db.appDao())
                val viewModel: CourseViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return CourseViewModel(repository) as T
                    }
                })
                UniversityScheduleSystemApp(viewModel, db.appDao())
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
fun UniversityScheduleSystemApp(viewModel: CourseViewModel, dao: AppDao) {
    var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }
    val userSettings = viewModel.userSettings
    val loggedInLecturer = viewModel.loggedInLecturer

    if (!userSettings.isRegistered) {
        LoginScreen(viewModel, dao)
    } else if (loggedInLecturer?.must_change_password == true) {
        PasswordChangeScreen(
            onPasswordChanged = { current, new ->
                viewModel.changePasswordWithVerification(current, new)
            },
            onLogout = { viewModel.logout() }
        )
    } else {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                AppDestinations.entries.forEach { destination ->
                    val isVisible = if (userSettings.position == Position.LECTURER) {
                        destination == AppDestinations.HOME || destination == AppDestinations.CALENDAR
                    } else true

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
                            IconButton(onClick = {
                                viewModel.logout()
                                currentDestination = AppDestinations.HOME
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    when (currentDestination) {
                        AppDestinations.HOME -> HomeScreen(viewModel)
                        AppDestinations.CALENDAR -> CalendarScreen(viewModel)
                        AppDestinations.DATA -> DataScreen(viewModel, onGoToCalendar = { currentDestination = AppDestinations.CALENDAR })
                        AppDestinations.SETTINGS -> SettingsScreen(
                            userSettings = userSettings,
                            onSave = {
                                viewModel.userSettings = it
                                currentDestination = AppDestinations.HOME
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: CourseViewModel, dao: AppDao) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val gradient = Brush.verticalGradient(colors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface))

    Box(modifier = Modifier.fillMaxSize().background(gradient), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.School, null, Modifier.size(64.dp), MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("University Scheduler", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Please login to continue", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                
                Spacer(Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Person, null) }
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Lock, null) }
                )
                
                Spacer(Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        scope.launch {
                            if (!viewModel.login(username, password, dao)) {
                                Toast.makeText(context, "Invalid Credentials", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Login")
                }
                
                Spacer(Modifier.height(16.dp))
                
                TextButton(onClick = { 
                    viewModel.userSettings = UserSettings(position = Position.ADMIN, isRegistered = true, name = "Admin")
                }) {
                    Text("Continue as Administrator")
                }
            }
        }
    }
}

@Composable
fun PasswordChangeScreen(onPasswordChanged: (String, String) -> Unit, onLogout: () -> Unit) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.LockReset, null, Modifier.size(80.dp), MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Password Change Required", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("For security, you must change your password before proceeding.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        
        Spacer(Modifier.height(32.dp))
        
        OutlinedTextField(
            value = currentPassword,
            onValueChange = { currentPassword = it },
            label = { Text("Current Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("New Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm New Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation()
        )
        
        Spacer(Modifier.height(32.dp))
        
        Button(
            onClick = {
                if (newPassword != confirmPassword) {
                    Toast.makeText(context, "New passwords do not match", Toast.LENGTH_SHORT).show()
                } else if (newPassword.length < 4) {
                    Toast.makeText(context, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show()
                } else {
                    onPasswordChanged(currentPassword, newPassword)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save & Continue")
        }
        
        Spacer(Modifier.height(8.dp))
        
        TextButton(onClick = onLogout) {
            Text("Cancel and Logout")
        }
    }
}

@Composable
fun HomeScreen(viewModel: CourseViewModel) {
    val userSettings = viewModel.userSettings
    val gradient = Brush.verticalGradient(colors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface))

    Column(modifier = Modifier.fillMaxSize().background(gradient).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(32.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (userSettings.position == Position.ADMIN) Icons.Default.AdminPanelSettings else Icons.Default.School, null, Modifier.size(80.dp), MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Welcome,", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Text(
                    text = if (userSettings.name.isNotBlank()) "${userSettings.name} ${userSettings.surname}" else "User",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text("Role: ${userSettings.position?.displayName}", style = MaterialTheme.typography.bodyMedium)
                if (userSettings.department != null) {
                    Text("Department: ${userSettings.department.displayName}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun DataScreen(viewModel: CourseViewModel, onGoToCalendar: () -> Unit) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    
    var showLogs by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var selectedLecturerDetails by remember { mutableStateOf<Lecturer?>(null) }

    val excelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importDataFromExcel(context, it) }
    }

    val templateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        uri?.let { viewModel.writeExcelTemplate(context, it) }
    }

    LaunchedEffect(importStatus) { 
        if (importStatus is UiState.Success) { 
            Toast.makeText(context, (importStatus as UiState.Success).data, Toast.LENGTH_SHORT).show()
            viewModel.resetImportStatus() 
        } 
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (showLogs) "Audit Logs" else "Data Management", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (viewModel.userSettings.position == Position.ADMIN) {
                IconButton(onClick = { showClearConfirm = true }) { Icon(Icons.Default.DeleteSweep, "Clear", tint = MaterialTheme.colorScheme.error) }
                IconButton(onClick = { showLogs = !showLogs }) { Icon(if (showLogs) Icons.AutoMirrored.Filled.List else Icons.Default.History, "Logs") }
                IconButton(onClick = { templateLauncher.launch("University_Template.xlsx") }) { Icon(Icons.Default.Download, "Template") }
                IconButton(onClick = { excelLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }) { Icon(Icons.Default.Add, "Import") }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (showLogs) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(auditLogs) { log ->
                        ListItem(
                            headlineContent = { Text("${log.user}: ${log.action}") },
                            supportingContent = { Text(log.details) },
                            overlineContent = { Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(log.timestamp))) }
                        )
                    }
                }
            } else {
                when (uiState) {
                    is UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is UiState.Success -> {
                        val lecturers = (uiState as UiState.Success).data
                        if (lecturers.isEmpty()) {
                            EmptyDataView(
                                onImport = { excelLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) },
                                onGetTemplate = { templateLauncher.launch("University_Template.xlsx") }
                            )
                        } else {
                            LazyColumn {
                                items(lecturers) { lecturer ->
                                    ListItem(
                                        headlineContent = { Text(lecturer.name, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { selectedLecturerDetails = lecturer }) },
                                        supportingContent = { Text(lecturer.title) },
                                        trailingContent = { IconButton(onClick = { viewModel.selectedLecturerForCalendar = lecturer; onGoToCalendar() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) } }
                                    )
                                }
                            }
                        }
                    }
                    is UiState.Error -> Text("Error: ${(uiState as UiState.Error).message}")
                    else -> Unit
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(onDismissRequest = { showClearConfirm = false }, title = { Text("Clear Database") }, text = { Text("Delete all records?") }, confirmButton = { Button(onClick = { viewModel.clearDatabase(); showClearConfirm = false }) { Text("Clear") } }, dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } })
    }

    if (selectedLecturerDetails != null) {
        val lecturer = selectedLecturerDetails!!
        val courses by viewModel.coursesState.collectAsStateWithLifecycle()
        val lecturerCourses = courses.filter { it.lecturerName.contains(lecturer.name) }
        AlertDialog(onDismissRequest = { selectedLecturerDetails = null }, title = { Text(lecturer.name) }, text = {
            Column {
                Text("Username: ${lecturer.username}")
                Text("Password: ${lecturer.password}")
                Spacer(Modifier.height(8.dp))
                Text("Courses:", fontWeight = FontWeight.Bold)
                lecturerCourses.forEach { Text("- ${it.code}: ${it.name}") }
            }
        }, confirmButton = { TextButton(onClick = { selectedLecturerDetails = null }) { Text("Close") } })
    }
}

@Composable
fun CalendarScreen(viewModel: CourseViewModel) {
    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
    val hours = (8..17).map { "$it:00" }
    val lecturersState by viewModel.uiState.collectAsStateWithLifecycle()
    val courses by viewModel.coursesState.collectAsStateWithLifecycle()
    
    val currentLoggedInLecturer = viewModel.loggedInLecturer
    val selectedLecturer = if (viewModel.userSettings.position == Position.ADMIN) {
        viewModel.selectedLecturerForCalendar
    } else {
        currentLoggedInLecturer
    }
    
    var selectedSlots by remember { mutableStateOf(setOf<Pair<String, String>>()) }
    var showAssignDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (viewModel.userSettings.position == Position.ADMIN) {
            var expanded by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth()) { Text(selectedLecturer?.name ?: "Select Lecturer") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (lecturersState is UiState.Success) {
                        (lecturersState as UiState.Success).data.forEach { l -> 
                            DropdownMenuItem(text = { Text(l.name) }, onClick = { viewModel.selectedLecturerForCalendar = l; expanded = false }) 
                        }
                    }
                }
            }
        }

        if (selectedLecturer != null) {
            if (viewModel.userSettings.position == Position.ADMIN && selectedSlots.isNotEmpty()) {
                Button(onClick = { showAssignDialog = true }, Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("Assign to ${selectedSlots.size} Slots") }
            }

            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    Spacer(Modifier.height(40.dp).width(60.dp))
                    hours.forEach { hour -> Box(Modifier.height(60.dp).width(60.dp), Alignment.Center) { Text(hour, style = MaterialTheme.typography.labelSmall) } }
                }
                days.forEach { day ->
                    Column {
                        Box(Modifier.height(40.dp).width(120.dp), Alignment.Center) { Text(day, fontWeight = FontWeight.Bold) }
                        hours.forEach { hour ->
                            val availability = selectedLecturer.availability.find { it.day == day && it.timeSlot == hour }
                            val isAvailable = availability?.isAvailable ?: true
                            
                            val assignedCourse = courses.find { it.lecturerName.contains(selectedLecturer.name) && it.scheduledSlots.any { s -> s.day == day && s.timeSlot == hour } }
                            val isSelected = selectedSlots.contains(day to hour)

                            Box(
                                modifier = Modifier.height(60.dp).width(120.dp).padding(2.dp).clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when {
                                            assignedCourse != null -> MaterialTheme.colorScheme.primaryContainer
                                            isSelected -> MaterialTheme.colorScheme.secondaryContainer
                                            !isAvailable -> Color(0xFFFFCDD2) // Red for unavailable
                                            else -> Color(0xFFC8E6C9) // Green for available
                                        }
                                    )
                                    .border(1.dp, Color.Gray.copy(alpha = 0.2f))
                                    .clickable {
                                        if (viewModel.userSettings.position == Position.LECTURER && currentLoggedInLecturer?.id == selectedLecturer.id) {
                                            val currentAvailability = selectedLecturer.availability.toMutableList()
                                            val existingIndex = currentAvailability.indexOfFirst { it.day == day && it.timeSlot == hour }
                                            
                                            if (existingIndex != -1) {
                                                currentAvailability[existingIndex] = currentAvailability[existingIndex].copy(isAvailable = !isAvailable)
                                            } else {
                                                currentAvailability.add(AvailabilitySlot(day, hour, false))
                                            }
                                            viewModel.updateLecturerAvailability(selectedLecturer, currentAvailability)
                                        } else if (viewModel.userSettings.position == Position.ADMIN && assignedCourse == null) {
                                            selectedSlots = if (isSelected) selectedSlots - (day to hour) else selectedSlots + (day to hour)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (assignedCourse != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(assignedCourse.code, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        if (viewModel.userSettings.position == Position.ADMIN) { 
                                            IconButton(onClick = { viewModel.removeCourseAssignment(assignedCourse.id) }, Modifier.size(20.dp)) { 
                                                Icon(Icons.Default.Delete, null, tint = Color.Red) 
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

    if (showAssignDialog) {
        val lecturerCourses = courses.filter { it.lecturerName.contains(selectedLecturer?.name ?: "") && it.scheduledSlots.isEmpty() }
        AlertDialog(onDismissRequest = { showAssignDialog = false }, title = { Text("Assign Course") }, text = {
            LazyColumn {
                items(lecturerCourses) { course ->
                    ListItem(headlineContent = { Text("${course.code}: ${course.name}") }, modifier = Modifier.clickable {
                        viewModel.assignCourse(course.id, selectedSlots.map { ScheduledSlot(it.first, it.second) })
                        selectedSlots = emptySet(); showAssignDialog = false
                    })
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { showAssignDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SettingsScreen(userSettings: UserSettings, onSave: (UserSettings) -> Unit) {
    var name by remember { mutableStateOf(userSettings.name) }
    var surname by remember { mutableStateOf(userSettings.surname) }
    var department by remember { mutableStateOf(userSettings.department ?: Department.COMPUTER_ENGINEERING) }
    Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Profile Setup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = surname, onValueChange = { surname = it }, label = { Text("Surname") }, modifier = Modifier.fillMaxWidth())
        Text("Department", style = MaterialTheme.typography.labelLarge)
        Department.entries.forEach { dept ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { department = dept }) {
                RadioButton(selected = department == dept, onClick = { department = dept })
                Text(dept.displayName)
            }
        }
        Button(onClick = { onSave(userSettings.copy(name = name, surname = surname, department = department, isRegistered = true)) }, modifier = Modifier.fillMaxWidth()) { Text("Save & Continue") }
    }
}

@Composable
fun EmptyDataView(onImport: () -> Unit, onGetTemplate: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CloudUpload, null, Modifier.size(64.dp), tint = Color.Gray)
        Spacer(Modifier.height(16.dp))
        Text("No data available.", color = Color.Gray)
        Button(onClick = onGetTemplate, Modifier.padding(top = 16.dp)) {
            Icon(Icons.Default.Download, null)
            Spacer(Modifier.width(8.dp))
            Text("Get Excel Template")
        }
        TextButton(onClick = onImport, Modifier.padding(top = 8.dp)) { Text("Already have a file? Import here") }
    }
}
