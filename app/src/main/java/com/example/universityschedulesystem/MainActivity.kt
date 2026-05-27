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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.Dp
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
            val db = AppDatabase.getDatabase(LocalContext.current)
            val repository = CourseRepository(db.appDao())
            val viewModel: CourseViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return CourseViewModel(repository) as T
                }
            })

            UniversityScheduleSystemTheme(darkTheme = viewModel.isDarkMode) {
                UniversityScheduleSystemApp(viewModel, db.appDao())
            }
        }
    }
}

@Composable
fun UserAvatar(name: String, size: Dp = 48.dp) {
    val titlesToExclude = listOf(
        "Prof.", "Prof", "Dr.", "Dr", "Doç.", "Doc.", "Doç", "Doc", 
        "Assist.", "Assoc.", "Assist", "Assoc", "Öğr.", "Ogr.", "Gör.", "Gor.", 
        "Arş.", "Ars.", "Üyesi", "Uyesi", "Admin", "Administrator", "Lecturer"
    )
    
    val nameParts = name.trim().split(" ")
        .filter { it.isNotBlank() }
        .filter { part -> 
            titlesToExclude.none { title -> part.equals(title, ignoreCase = true) } 
        }

    val initials = nameParts
        .take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifEmpty { 
            name.trim().firstOrNull()?.toString()?.uppercase() ?: "?" 
        }

    val colors = listOf(
        Color(0xFF9C27B0), Color(0xFF673AB7), Color(0xFF3F51B5),
        Color(0xFF2196F3), Color(0xFF03A9F4), Color(0xFF00BCD4),
        Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A)
    )
    val backgroundColor = colors[name.hashCode().let { if (it < 0) -it else it } % colors.size]

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4).sp
        )
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
                        AppDestinations.SETTINGS -> SettingsScreen(
                            user = userSettings,
                            isDarkMode = viewModel.isDarkMode,
                            onDarkModeToggle = { viewModel.toggleDarkMode(it) },
                            onCheckAdminPassword = { dept, pass -> viewModel.checkAdminPassword(dept, pass) },
                            onUpdateAdminPassword = { dept, pass -> viewModel.updateAdminPassword(dept, pass) },
                            onSave = { 
                                viewModel.userSettings = it
                                viewModel.selectedLecturerForCalendar = null
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
fun LoginScreen(viewModel: CourseViewModel, dao: AppDao, onNavigateToSettings: () -> Unit) {
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var showAdminDialog by remember { mutableStateOf(false) }
    val gradient = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface))

    Box(Modifier.fillMaxSize().background(gradient), Alignment.Center) {
        Card(Modifier.fillMaxWidth().padding(32.dp), RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.School, null, Modifier.size(64.dp), MaterialTheme.colorScheme.primary)
                Text("Login", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                ClarifySubtitle("Enter credentials to continue")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
                Button(onClick = { scope.launch { if (!viewModel.login(username, password, dao)) Toast.makeText(context, "Invalid Credentials", Toast.LENGTH_SHORT).show() } }, modifier = Modifier.fillMaxWidth()) { Text("Login") }
                TextButton(onClick = { showAdminDialog = true }) { Text("Continue as Administrator") }
            }
        }
    }

    if (showAdminDialog) {
        var adminPass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdminDialog = false },
            title = { Text("Master Admin Authentication") },
            text = {
                Column {
                    Text("Please enter the Master Admin Password to continue.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = adminPass,
                        onValueChange = { adminPass = it },
                        label = { Text("Master Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (viewModel.adminLogin(adminPass)) {
                        showAdminDialog = false
                        onNavigateToSettings()
                    } else {
                        Toast.makeText(context, "Incorrect Master Password", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Authenticate") }
            },
            dismissButton = {
                TextButton(onClick = { showAdminDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ClarifySubtitle(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
    val courses by viewModel.coursesState.collectAsStateWithLifecycle()
    val classrooms by viewModel.classroomsState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lecturers = (uiState as? UiState.Success)?.data ?: emptyList()

    val assignedCount = if (user.position == Position.LECTURER && lecturer != null) scheduleEntries.count { it.lecturerUsername == lecturer.username } else 0
    val gradient = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface))

    Box(Modifier.fillMaxSize().background(gradient)) {
        Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(32.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val welcomeName = when {
                        user.position == Position.LECTURER && lecturer != null -> "${lecturer.title} ${lecturer.name}"
                        user.position == Position.ADMIN && user.name.isNotBlank() -> "Admin ${user.name} ${user.surname}"
                        else -> "Administrator"
                    }
                    
                    val avatarName = when {
                        user.position == Position.LECTURER && lecturer != null -> lecturer.name
                        user.position == Position.ADMIN && user.name.isNotBlank() -> "${user.name} ${user.surname}"
                        else -> welcomeName
                    }
                    
                    UserAvatar(name = avatarName, size = 80.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("Welcome,", style = MaterialTheme.typography.bodyLarge)
                    
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

            if (user.position == Position.ADMIN) {
                Spacer(Modifier.height(24.dp))
                Text("Schedule Status Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(16.dp))

                val deptLecturers = lecturers.filter { it.department == user.department }
                val deptCourses = courses.filter { it.department == user.department }
                val deptClassrooms = classrooms.filter { it.department == user.department }

                val assignedLecturersCount = deptLecturers.count { l -> scheduleEntries.any { it.lecturerUsername == l.username } }
                val assignedCoursesCount = deptCourses.count { c -> scheduleEntries.any { it.courseCode == c.code } }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        title = "Lecturers Assigned",
                        current = assignedLecturersCount,
                        total = deptLecturers.size,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Courses Scheduled",
                        current = assignedCoursesCount,
                        total = deptCourses.size,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Classroom Weekly Occupancy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        
                        if (deptClassrooms.isEmpty()) {
                            Text("No classrooms available.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        } else {
                            deptClassrooms.forEach { room ->
                                val roomSlots = scheduleEntries.count { it.roomCode == room.roomCode }
                                val occupancy = roomSlots.toFloat() / 50f // 5 days * 10 slots
                                
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(room.roomCode, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                                    LinearProgressIndicator(
                                        progress = { occupancy },
                                        modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                                        color = if (occupancy > 0.8f) Color.Red else MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surface
                                    )
                                    Text("${(occupancy * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 8.dp).width(35.dp), textAlign = TextAlign.End)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, current: Int, total: Int, color: Color, modifier: Modifier = Modifier) {
    val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("$current / $total", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = color,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
fun ClassroomScreen(viewModel: CourseViewModel) {
    val context = LocalContext.current
    val classrooms by viewModel.classroomsState.collectAsStateWithLifecycle()
    var showAddRoom by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    
    var searchQuery by remember { mutableStateOf("") }
    val filteredClassrooms by remember(classrooms, searchQuery) {
        derivedStateOf {
            classrooms.filter { it.department == viewModel.userSettings.department }
                .filter { it.roomCode.contains(searchQuery, ignoreCase = true) }
        }
    }

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
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            placeholder = { Text("Search Classroom...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        LazyColumn(Modifier.fillMaxSize()) {
            items(filteredClassrooms) { room ->
                ListItem(
                    headlineContent = { Text(room.roomCode, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Capacity: ${room.capacity} | Dept: ${room.department.displayName}") },
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
        AlertDialog(onDismissRequest = { showClearConfirm = false }, title = { Text("Clear Classrooms") }, text = { Text("Proceed with clearing all classrooms?") }, confirmButton = { Button(onClick = { viewModel.clearClassrooms(); showClearConfirm = false }) { Text("Clear Classrooms") } })
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

    var searchQuery by remember { mutableStateOf("") }
    
    val excelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { viewModel.importDataFromExcel(context, it) } }
    val templateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { it?.let { viewModel.writeExcelTemplate(context, it) } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val title = if (showLogs) "Logs" else "Lecturers"
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (viewModel.userSettings.position == Position.ADMIN) {
                if (!showLogs) IconButton(onClick = { showClearConfirm = true }) { Icon(Icons.Default.DeleteSweep, null, tint = Color.Red) }
                IconButton(onClick = { showLogs = !showLogs }) { Icon(if (showLogs) Icons.AutoMirrored.Filled.List else Icons.Default.History, null) }
                IconButton(onClick = { templateLauncher.launch("University_Template.xlsx") }) { Icon(Icons.Default.Download, null) }
                IconButton(onClick = { excelLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }) { Icon(Icons.Default.CloudUpload, null) }
            }
        }

        if (!showLogs) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("Search Lecturer...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        Box(Modifier.weight(1f)) {
            if (showLogs) {
                LazyColumn { items(auditLogs) { log -> ListItem(headlineContent = { Text("${log.user}: ${log.action}") }, supportingContent = { Text(log.details) }) } }
            } else {
                when (val state = uiState) {
                    is UiState.Success -> {
                        val filteredLecturers by remember(state.data, searchQuery) {
                            derivedStateOf {
                                state.data.filter { it.department == viewModel.userSettings.department }
                                    .filter { it.name.contains(searchQuery, ignoreCase = true) }
                            }
                        }

                        LazyColumn { items(filteredLecturers) { l -> 
                            ListItem(
                                leadingContent = { UserAvatar(name = l.name, size = 40.dp) },
                                headlineContent = { Text("${l.title} ${l.name}", Modifier.clickable { selectedLecturerForDetails = l }) }, 
                                supportingContent = { Text(l.department.displayName) }, 
                                trailingContent = { IconButton(onClick = { viewModel.selectedLecturerForCalendar = l; onGoToCalendar() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) } }
                            ) 
                        } }
                    }
                    is UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else -> Unit
                }
            }
        }
    }
    if (showClearConfirm) {
        AlertDialog(onDismissRequest = { showClearConfirm = false }, title = { Text("Clear Lecturers & Courses") }, text = { Text("Proceed with clearing all lecturers and courses?") }, confirmButton = { Button(onClick = { viewModel.clearLecturersAndCourses(); showClearConfirm = false }) { Text("Clear Data") } })
    }
    if (selectedLecturerForDetails != null) {
        val l = selectedLecturerForDetails!!
        val lecturerCourses = courses.filter { it.lecturerName.contains(l.name) }
        AlertDialog(onDismissRequest = { selectedLecturerForDetails = null }, title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(name = l.name, size = 32.dp)
                Spacer(Modifier.width(12.dp))
                Text("${l.title} ${l.name}")
            }
        }, text = {
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
                OutlinedButton(onClick = { exp = true }, Modifier.fillMaxWidth()) { Text(if(selected != null) "${selected.title} ${selected.name}" else "Select Lecturer View") }
                DropdownMenu(exp, { exp = false }) {
                    val state = uiState
                    if (state is UiState.Success) {
                        state.data.filter { it.department == viewModel.userSettings.department }.forEach { l -> DropdownMenuItem(text = { Text("${l.title} ${l.name}") }, onClick = { viewModel.selectedLecturerForCalendar = l; exp = false }) }
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
fun SettingsScreen(
    user: UserSettings, 
    isDarkMode: Boolean, 
    onDarkModeToggle: (Boolean) -> Unit, 
    onCheckAdminPassword: (Department, String) -> Boolean,
    onUpdateAdminPassword: (Department, String) -> Unit,
    onSave: (UserSettings) -> Unit
) {
    var name by remember { mutableStateOf(user.name) }; var surname by remember { mutableStateOf(user.surname) }; var dept by remember { mutableStateOf(user.department ?: Department.COMPUTER_ENGINEERING) }
    var isVerified by remember { mutableStateOf(user.department != null) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val gradient = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface))
    Box(Modifier.fillMaxSize().background(gradient)) {
        Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("User Profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(name, { name = it }, label = { Text("First Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(surname, { surname = it }, label = { Text("Last Name") }, modifier = Modifier.fillMaxWidth())
            
            Text("Department Selection", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Department.entries.forEach { d -> 
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { 
                    if (user.position == Position.ADMIN) {
                        dept = d
                        isVerified = false // Reset verification when department changes
                    } else {
                        dept = d
                    }
                }) { 
                    RadioButton(dept == d, onClick = { 
                        if (user.position == Position.ADMIN) {
                            dept = d
                            isVerified = false
                        } else {
                            dept = d
                        }
                    })
                    Text(d.displayName) 
                } 
            }
            
            if (user.position == Position.ADMIN) {
                if (!isVerified) {
                    Button(onClick = { showAuthDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Verify Department Admin Password")
                    }
                } else {
                    OutlinedButton(onClick = { showChangePasswordDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Lock, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Change Admin Password")
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Dark Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Toggle dark or light theme", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Switch(checked = isDarkMode, onCheckedChange = onDarkModeToggle)
            }

            Button(
                onClick = { onSave(user.copy(name = name, surname = surname, department = dept, isRegistered = true)) }, 
                modifier = Modifier.fillMaxWidth(),
                enabled = user.position != Position.ADMIN || isVerified
            ) { 
                Text("Save & Continue") 
            }
        }
    }

    if (showAuthDialog) {
        var pass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAuthDialog = false },
            title = { Text("Verify Admin Password for ${dept.displayName}") },
            text = {
                Column {
                    Text("Enter password to manage this department.")
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (onCheckAdminPassword(dept, pass)) {
                        isVerified = true
                        showAuthDialog = false
                    } else {
                        Toast.makeText(context, "Incorrect Password", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Verify") }
            },
            dismissButton = { TextButton(onClick = { showAuthDialog = false }) { Text("Cancel") } }
        )
    }

    if (showChangePasswordDialog) {
        var newPass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("Update Password for ${dept.displayName}") },
            text = {
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text("New Admin Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newPass.length >= 4) {
                        onUpdateAdminPassword(dept, newPass)
                        showChangePasswordDialog = false
                        Toast.makeText(context, "Password Updated", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Update") }
            },
            dismissButton = { TextButton(onClick = { showChangePasswordDialog = false }) { Text("Cancel") } }
        )
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
