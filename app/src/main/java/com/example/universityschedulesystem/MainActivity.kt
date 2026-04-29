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
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.universityschedulesystem.data.*
import com.example.universityschedulesystem.ui.*
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
                        @Suppress("UNCHECKED_CAST")
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
    CLASSROOMS("Classrooms", Icons.Default.MeetingRoom),
    DATA("Lecturers", Icons.AutoMirrored.Filled.List),
    SETTINGS("Settings", Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversityScheduleSystemApp(viewModel: CourseViewModel, dao: AppDao) {
    var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }
    val userSettings = viewModel.userSettings
    val loggedInLecturer = viewModel.loggedInLecturer

    if (!userSettings.isRegistered) {
        LoginScreen(viewModel, dao, onNavigateToSettings = { currentDestination = AppDestinations.SETTINGS })
    } else if (loggedInLecturer?.must_change_password == true) {
        PasswordChangeScreen(
            onPasswordChanged = { current, new -> viewModel.changePasswordWithVerification(current, new) },
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
                            onClick = { 
                                if (userSettings.position == Position.ADMIN && userSettings.department == null && destination != AppDestinations.SETTINGS) {
                                    // Block access
                                } else {
                                    currentDestination = destination 
                                }
                            }
                        )
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("University Scheduler", fontWeight = FontWeight.Bold) },
                        actions = {
                            IconButton(onClick = { viewModel.logout(); currentDestination = AppDestinations.HOME }) {
                                Icon(Icons.AutoMirrored.Filled.Logout, "Logout")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    if (userSettings.position == Position.ADMIN && userSettings.department == null && currentDestination != AppDestinations.SETTINGS) {
                        LaunchedEffect(Unit) { currentDestination = AppDestinations.SETTINGS }
                    }
                    
                    when (currentDestination) {
                        AppDestinations.HOME -> HomeScreen(viewModel)
                        AppDestinations.CALENDAR -> CalendarScreen(viewModel)
                        AppDestinations.CLASSROOMS -> ClassroomScreen(viewModel)
                        AppDestinations.DATA -> DataScreen(viewModel, onGoToCalendar = { currentDestination = AppDestinations.CALENDAR })
                        AppDestinations.SETTINGS -> SettingsScreen(userSettings, onSave = { 
                            viewModel.userSettings = it
                            viewModel.selectedLecturerForCalendar = null
                            currentDestination = AppDestinations.HOME 
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: CourseViewModel, dao: AppDao, onNavigateToSettings: () -> Unit) {
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    val gradient = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface))

    Box(Modifier.fillMaxSize().background(gradient), Alignment.Center) {
        Card(Modifier.fillMaxWidth().padding(32.dp), RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.School, null, Modifier.size(64.dp), MaterialTheme.colorScheme.primary)
                Text("Login", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
                Button(onClick = { scope.launch { if (!viewModel.login(username, password, dao)) Toast.makeText(context, "Invalid Credentials", Toast.LENGTH_SHORT).show() } }, modifier = Modifier.fillMaxWidth()) { Text("Login") }
                TextButton(onClick = { 
                    viewModel.userSettings = UserSettings(position = Position.ADMIN, isRegistered = true, name = "")
                    onNavigateToSettings()
                }) { Text("Continue as Administrator") }
            }
        }
    }
}

@Composable
fun PasswordChangeScreen(onPasswordChanged: (String, String) -> Unit, onLogout: () -> Unit) {
    var cur by remember { mutableStateOf("") }; var new by remember { mutableStateOf("") }; var conf by remember { mutableStateOf("") }
    val gradient = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface))
    Box(Modifier.fillMaxSize().background(gradient), Alignment.Center) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.LockReset, null, Modifier.size(80.dp), MaterialTheme.colorScheme.primary)
            Text("Initial Password Change", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(cur, { cur = it }, label = { Text("Current Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(new, { new = it }, label = { Text("New Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(conf, { conf = it }, label = { Text("Confirm New Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            Button(onClick = { if (new == conf && new.length >= 4) onPasswordChanged(cur, new) }, modifier = Modifier.fillMaxWidth()) { Text("Save & Continue") }
            TextButton(onClick = onLogout) { Text("Logout") }
        }
    }
}

@Composable
fun HomeScreen(viewModel: CourseViewModel) {
    val user = viewModel.userSettings; val lecturer = viewModel.loggedInLecturer
    val scheduleEntries by viewModel.scheduleEntriesState.collectAsStateWithLifecycle()
    val assignedCount = if (user.position == Position.LECTURER && lecturer != null) scheduleEntries.count { it.lecturerUsername == lecturer.username } else 0
    val gradient = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface))

    Box(Modifier.fillMaxSize().background(gradient), Alignment.Center) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(32.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (user.position == Position.ADMIN) Icons.Default.AdminPanelSettings else Icons.Default.School, null, Modifier.size(80.dp), MaterialTheme.colorScheme.primary)
                    Text("Welcome,", style = MaterialTheme.typography.bodyLarge)
                    
                    val welcomeName = when {
                        user.position == Position.LECTURER && lecturer != null -> "${lecturer.title} ${lecturer.name}"
                        user.position == Position.ADMIN && user.name.isNotBlank() -> "Admin ${user.name} ${user.surname}"
                        else -> "Administrator"
                    }
                    
                    Text(welcomeName, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                    Text("Department: ${user.department?.displayName ?: "NOT SELECTED"}", style = MaterialTheme.typography.bodyMedium, color = if (user.department == null) Color.Red else Color.Unspecified)
                    if (user.position == Position.LECTURER) {
                        Spacer(Modifier.height(24.dp))
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Weekly Summary", style = MaterialTheme.typography.labelLarge)
                                Text("$assignedCount Courses Assigned", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClassroomScreen(viewModel: CourseViewModel) {
    val context = LocalContext.current
    val classrooms by viewModel.classroomsState.collectAsStateWithLifecycle()
    var showAddRoom by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    
    val excelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { viewModel.importDataFromExcel(context, it) } }
    val templateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { it?.let { viewModel.writeExcelTemplate(context, it) } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Classrooms", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showAddRoom = true }) { Icon(Icons.Default.Add, "Add") }
            IconButton(onClick = { templateLauncher.launch("Classroom_Template.xlsx") }) { Icon(Icons.Default.Download, "Template") }
            IconButton(onClick = { excelLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }) { Icon(Icons.Default.CloudUpload, "Import") }
            IconButton(onClick = { showClearConfirm = true }) { Icon(Icons.Default.DeleteSweep, "Clear", tint = Color.Red) }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(classrooms.filter { it.department == viewModel.userSettings.department }) { room ->
                ListItem(
                    headlineContent = { Text(room.roomCode, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Capacity: ${room.capacity}") },
                    trailingContent = { IconButton(onClick = { viewModel.deleteClassroom(room) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) } }
                )
            }
        }
    }
    if (showAddRoom) {
        var code by remember { mutableStateOf("") }; var cap by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showAddRoom = false }, title = { Text("Add Room") }, text = { Column { OutlinedTextField(code, { code = it }, label = { Text("Room Code") }); OutlinedTextField(cap, { cap = it }, label = { Text("Capacity") }) } }, confirmButton = { Button(onClick = { viewModel.addClassroom(Classroom(roomCode = code, capacity = cap.toIntOrNull() ?: 0, department = viewModel.userSettings.department ?: Department.COMPUTER_ENGINEERING)); showAddRoom = false }) { Text("Add") } })
    }
    if (showClearConfirm) {
        AlertDialog(onDismissRequest = { showClearConfirm = false }, title = { Text("Clear Classrooms") }, text = { Text("Proceed?") }, confirmButton = { Button(onClick = { viewModel.clearDatabase(); showClearConfirm = false }) { Text("Clear All") } })
    }
}

@Composable
fun DataScreen(viewModel: CourseViewModel, onGoToCalendar: () -> Unit) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val courses by viewModel.coursesState.collectAsStateWithLifecycle()
    var showLogs by remember { mutableStateOf(false) }
    var selectedLecturerForDetails by remember { mutableStateOf<Lecturer?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val excelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { viewModel.importDataFromExcel(context, it) } }
    val templateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { it?.let { viewModel.writeExcelTemplate(context, it) } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val title = if (showLogs) "Logs" else "Lecturers"
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (viewModel.userSettings.position == Position.ADMIN) {
                IconButton(onClick = { showClearConfirm = true }) { Icon(Icons.Default.DeleteSweep, null, tint = Color.Red) }
                IconButton(onClick = { showLogs = !showLogs }) { Icon(if (showLogs) Icons.AutoMirrored.Filled.List else Icons.Default.History, null) }
                IconButton(onClick = { templateLauncher.launch("University_Template.xlsx") }) { Icon(Icons.Default.Download, null) }
                IconButton(onClick = { excelLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }) { Icon(Icons.Default.CloudUpload, null) }
            }
        }
        Box(Modifier.weight(1f)) {
            if (showLogs) {
                LazyColumn { items(auditLogs) { log -> ListItem(headlineContent = { Text("${log.user}: ${log.action}") }, supportingContent = { Text(log.details) }) } }
            } else {
                when (val state = uiState) {
                    is UiState.Success -> {
                        LazyColumn { items(state.data.filter { it.department == viewModel.userSettings.department }) { l -> ListItem(headlineContent = { Text(l.name, Modifier.clickable { selectedLecturerForDetails = l }) }, supportingContent = { Text(l.title) }, trailingContent = { IconButton(onClick = { viewModel.selectedLecturerForCalendar = l; onGoToCalendar() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) } }) } }
                    }
                    is UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else -> Unit
                }
            }
        }
    }
    if (showClearConfirm) {
        AlertDialog(onDismissRequest = { showClearConfirm = false }, title = { Text("Clear All Data") }, text = { Text("Proceed?") }, confirmButton = { Button(onClick = { viewModel.clearDatabase(); showClearConfirm = false }) { Text("Clear") } })
    }
    if (selectedLecturerForDetails != null) {
        val l = selectedLecturerForDetails!!
        val lecturerCourses = courses.filter { it.lecturerName.contains(l.name) }
        AlertDialog(onDismissRequest = { selectedLecturerForDetails = null }, title = { Text(l.name) }, text = {
            Column {
                Text("Username: ${l.username}"); Text("Password Hash: ${l.password.take(15)}...")
                Spacer(Modifier.height(8.dp))
                Text("Assigned Courses:", fontWeight = FontWeight.Bold)
                lecturerCourses.forEach { Text("- ${it.code}: ${it.name}") }
                if (lecturerCourses.isEmpty()) Text("No courses assigned.", color = Color.Gray)
            }
        }, confirmButton = { TextButton(onClick = { selectedLecturerForDetails = null }) { Text("Close") } })
    }
}

@Composable
fun CalendarScreen(viewModel: CourseViewModel) {
    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday"); val hours = (8..17).map { "$it:00" }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val courses by viewModel.coursesState.collectAsStateWithLifecycle()
    val classrooms by viewModel.classroomsState.collectAsStateWithLifecycle()
    val entries by viewModel.scheduleEntriesState.collectAsStateWithLifecycle()
    val current = viewModel.loggedInLecturer; val selected = if (viewModel.userSettings.position == Position.ADMIN) viewModel.selectedLecturerForCalendar else current
    var showAssign by remember { mutableStateOf<Pair<String, String>?>(null) }; val context = LocalContext.current
    val verticalScrollState = rememberScrollState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (viewModel.userSettings.position == Position.ADMIN) {
            var exp by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                OutlinedButton(onClick = { exp = true }, Modifier.fillMaxWidth()) { Text(selected?.name ?: "Select Lecturer View") }
                DropdownMenu(exp, { exp = false }) {
                    val state = uiState
                    if (state is UiState.Success) {
                        state.data.filter { it.department == viewModel.userSettings.department }.forEach { l -> DropdownMenuItem(text = { Text(l.name) }, onClick = { viewModel.selectedLecturerForCalendar = l; exp = false }) }
                    }
                }
            }
        }
        if (selected != null) {
            Box(modifier = Modifier.weight(1f).verticalScroll(verticalScrollState)) {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Column { Spacer(Modifier.height(40.dp).width(60.dp)); hours.forEach { h -> Box(Modifier.height(110.dp).width(60.dp), Alignment.Center) { Text(h, style = MaterialTheme.typography.labelSmall) } } }
                    days.forEach { d ->
                        Column {
                            Box(Modifier.height(40.dp).width(150.dp), Alignment.Center) { Text(d, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                            hours.forEach { h ->
                                val avail = selected.availability.find { it.day == d && it.timeSlot == h }?.isAvailable ?: true
                                val entry = entries.find { it.lecturerUsername == selected.username && it.day == d && it.timeSlot == h }
                                val course = entry?.let { e -> courses.find { it.code == e.courseCode } }
                                val roomCode = entry?.roomCode ?: ""
                                Box(modifier = Modifier.height(110.dp).width(150.dp).padding(2.dp).clip(RoundedCornerShape(8.dp)).background(if (course != null) MaterialTheme.colorScheme.primaryContainer else if (!avail) Color(0xFFFFCDD2) else Color(0xFFC8E6C9)).border(1.dp, Color.Gray.copy(0.1f)).clickable {
                                    if (viewModel.userSettings.position == Position.LECTURER && current?.username == selected.username) {
                                        val list = selected.availability.toMutableList(); val idx = list.indexOfFirst { it.day == d && it.timeSlot == h }
                                        if (idx != -1) list[idx] = list[idx].copy(isAvailable = !avail) else list.add(AvailabilitySlot(d, h, false))
                                        viewModel.updateLecturerAvailability(selected, list)
                                    } else if (viewModel.userSettings.position == Position.ADMIN) {
                                        if (!avail) Toast.makeText(context, "Warning: Lecturer marked this slot as unavailable!", Toast.LENGTH_LONG).show()
                                        showAssign = d to h
                                    }
                                }, contentAlignment = Alignment.Center) {
                                    if (course != null) Column(modifier = Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(course.code, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Text(course.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 12.sp)
                                        if (roomCode.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(4.dp), modifier = Modifier.padding(top = 4.dp)) { Text(roomCode, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                        if (viewModel.userSettings.position == Position.ADMIN) IconButton(onClick = { viewModel.removeScheduleEntry(entry!!) }, Modifier.size(16.dp)) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAssign != null) {
        var selC by remember { mutableStateOf<Course?>(null) }; var selR by remember { mutableStateOf<Classroom?>(null) }
        var cExp by remember { mutableStateOf(false) }; var rExp by remember { mutableStateOf(false) }
        val (d, h) = showAssign!!
        AlertDialog(onDismissRequest = { showAssign = null }, title = { Text("Assign Course at $d $h") }, text = {
            Column {
                Box { OutlinedButton(onClick = { cExp = true }, Modifier.fillMaxWidth()) { Text(selC?.name ?: "Select Course") }; DropdownMenu(cExp, { cExp = false }) {
                    val lecturerCourses = courses.filter { it.department == viewModel.userSettings.department && it.lecturerName.contains(selected?.name ?: "") }
                    lecturerCourses.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { selC = c; cExp = false }) } 
                } }
                Spacer(Modifier.height(8.dp))
                Box { OutlinedButton(onClick = { rExp = true }, Modifier.fillMaxWidth()) { Text(selR?.roomCode ?: "Select Classroom") }; DropdownMenu(rExp, { rExp = false }) { classrooms.filter { it.department == viewModel.userSettings.department }.forEach { r -> DropdownMenuItem(text = { Text(r.roomCode) }, onClick = { selR = r; rExp = false }) } } }
            }
        }, confirmButton = { Button(onClick = {
            val err = viewModel.checkScheduleConflict(selected!!, selR!!, d, h)
            if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show() else { viewModel.assignSchedule(selC!!, selected, selR!!, d, h); showAssign = null }
        }, enabled = selC != null && selR != null) { Text("Assign") } })
    }
}

@Composable
fun SettingsScreen(user: UserSettings, onSave: (UserSettings) -> Unit) {
    var name by remember { mutableStateOf(user.name) }; var surname by remember { mutableStateOf(user.surname) }; var dept by remember { mutableStateOf(user.department ?: Department.COMPUTER_ENGINEERING) }
    val gradient = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface))
    Box(Modifier.fillMaxSize().background(gradient)) {
        Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("User Profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(name, { name = it }, label = { Text("First Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(surname, { surname = it }, label = { Text("Last Name") }, modifier = Modifier.fillMaxWidth())
            Text("Department Selection", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Department.entries.forEach { d -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { dept = d }) { RadioButton(dept == d, onClick = { dept = d }); Text(d.displayName) } }
            Button(onClick = { onSave(user.copy(name = name, surname = surname, department = dept, isRegistered = true)) }, modifier = Modifier.fillMaxWidth()) { Text("Save & Continue") }
        }
    }
}

@Composable
fun EmptyDataView(onImport: () -> Unit, onGetTemplate: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.CloudUpload, null, Modifier.size(64.dp), tint = Color.Gray)
        Text("No data available.", color = Color.Gray)
        Button(onGetTemplate, Modifier.padding(top = 16.dp)) { Text("Get Excel Template") }
        TextButton(onImport) { Text("Import here") }
    }
}
