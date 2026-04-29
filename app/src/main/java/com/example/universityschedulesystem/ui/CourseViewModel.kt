package com.example.universityschedulesystem.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.universityschedulesystem.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale

class CourseViewModel(private val repository: CourseRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Lecturer>>>(UiState.Idle)
    val uiState: StateFlow<UiState<List<Lecturer>>> = _uiState.asStateFlow()

    private val _coursesState = MutableStateFlow<List<Course>>(emptyList())
    val coursesState: StateFlow<List<Course>> = _coursesState.asStateFlow()

    private val _classroomsState = MutableStateFlow<List<Classroom>>(emptyList())
    val classroomsState: StateFlow<List<Classroom>> = _classroomsState.asStateFlow()

    private val _scheduleEntriesState = MutableStateFlow<List<ScheduleEntry>>(emptyList())
    val scheduleEntriesState: StateFlow<List<ScheduleEntry>> = _scheduleEntriesState.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditLog>>(emptyList())
    val auditLogs: StateFlow<List<AuditLog>> = _auditLogs.asStateFlow()

    private val _importStatus = MutableStateFlow<UiState<String>>(UiState.Idle)
    val importStatus: StateFlow<UiState<String>> = _importStatus.asStateFlow()

    var userSettings by mutableStateOf(UserSettings())
    var loggedInLecturer by mutableStateOf<Lecturer?>(null)
    var selectedLecturerForCalendar by mutableStateOf<Lecturer?>(null)

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                repository.getLecturers().onEach { lecturers ->
                    _uiState.value = UiState.Success(lecturers)
                    loggedInLecturer?.let { current ->
                        lecturers.find { it.username == current.username }?.let { loggedInLecturer = it }
                    }
                    selectedLecturerForCalendar?.let { current ->
                        selectedLecturerForCalendar = lecturers.find { it.username == current.username }
                    }
                }.launchIn(this)

                repository.getCourses().onEach { _coursesState.value = it }.launchIn(this)
                repository.getClassrooms().onEach { _classroomsState.value = it }.launchIn(this)
                repository.getAuditLogs().onEach { _auditLogs.value = it }.launchIn(this)
                repository.getScheduleEntries().onEach { _scheduleEntriesState.value = it }.launchIn(this)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = UiState.Error(e.message ?: "Load failed")
            }
        }
    }

    private fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    suspend fun login(username: String, password: String, dao: AppDao): Boolean {
        if (username.isBlank() || password.isBlank()) return false
        
        val lecturers = _uiState.value.let { if (it is UiState.Success) it.data else emptyList() }
        val hashedPassword = hashPassword(password)
        val lecturer = lecturers.find { it.username == username && it.password == hashedPassword }
        
        return if (lecturer != null) {
            loggedInLecturer = lecturer
            val parts = lecturer.name.trim().split(" ")
            val surname = parts.lastOrNull() ?: ""
            val name = parts.dropLast(1).joinToString(" ")
            
            userSettings = userSettings.copy(
                name = if (name.isEmpty()) surname else name,
                surname = if (name.isEmpty()) "" else surname,
                department = lecturer.department,
                position = Position.LECTURER,
                isRegistered = true,
                lecturerId = lecturer.id,
                lecturerUsername = lecturer.username
            )
            true
        } else false
    }

    fun logout() {
        loggedInLecturer = null
        selectedLecturerForCalendar = null
        userSettings = UserSettings()
    }

    fun changePassword(newPassword: String) {
        val current = loggedInLecturer ?: return
        if (newPassword.length < 4) return
        
        viewModelScope.launch {
            val updated = current.copy(password = hashPassword(newPassword), must_change_password = false)
            repository.updateLecturer(updated)
            loggedInLecturer = updated
        }
    }

    fun changePasswordWithVerification(currentPasswordInput: String, newPassword: String) {
        val lecturer = loggedInLecturer ?: return
        if (hashPassword(currentPasswordInput) == lecturer.password) {
            changePassword(newPassword)
        }
    }

    fun updateLecturerAvailability(lecturer: Lecturer, availability: List<AvailabilitySlot>) {
        viewModelScope.launch {
            val updatedLecturer = lecturer.copy(availability = availability)
            if (loggedInLecturer?.id == lecturer.id) {
                loggedInLecturer = updatedLecturer
            }
            repository.updateLecturer(updatedLecturer)
        }
    }

    fun addClassroom(classroom: Classroom) {
        if (classroom.roomCode.isBlank() || classroom.capacity <= 0) return
        viewModelScope.launch { repository.addClassroom(classroom) }
    }

    fun deleteClassroom(classroom: Classroom) {
        viewModelScope.launch { repository.deleteClassroom(classroom) }
    }

    fun checkScheduleConflict(lecturer: Lecturer, classroom: Classroom, day: String, timeSlot: String): String? {
        val entries = _scheduleEntriesState.value
        if (entries.any { it.lecturerUsername == lecturer.username && it.day == day && it.timeSlot == timeSlot }) {
            return "Lecturer is already assigned at this time."
        }
        if (entries.any { it.roomCode == classroom.roomCode && it.day == day && it.timeSlot == timeSlot }) {
            return "Classroom is already occupied at this time."
        }
        return null
    }

    fun assignSchedule(course: Course, lecturer: Lecturer, classroom: Classroom, day: String, timeSlot: String) {
        viewModelScope.launch {
            val entry = ScheduleEntry(
                courseCode = course.code,
                lecturerUsername = lecturer.username,
                roomCode = classroom.roomCode,
                day = day,
                timeSlot = timeSlot
            )
            repository.insertScheduleEntry(entry)
            repository.insertAuditLog(
                AuditLog(
                    user = if (userSettings.name.isBlank()) "Admin" else "${userSettings.name} ${userSettings.surname}", 
                    action = "Schedule Assignment", 
                    details = "Course: ${course.code}, Lecturer: ${lecturer.name}, Room: ${classroom.roomCode}, Time: $day $timeSlot"
                )
            )
        }
    }

    fun removeScheduleEntry(entry: ScheduleEntry) {
        viewModelScope.launch { repository.deleteScheduleEntry(entry) }
    }

    fun writeExcelTemplate(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val workbook = XSSFWorkbook()
                    val sheet1 = workbook.createSheet("LİSANS")
                    val header1 = sheet1.createRow(0)
                    header1.createCell(0).setCellValue("Course Code")
                    header1.createCell(1).setCellValue("Course Name")
                    header1.createCell(2).setCellValue("Lecturer")

                    val sheet2 = workbook.createSheet("DERSLİKLER")
                    val header2 = sheet2.createRow(0)
                    header2.createCell(0).setCellValue("Room Code")
                    header2.createCell(1).setCellValue("Capacity")

                    val outputStream = context.contentResolver.openOutputStream(uri)
                    workbook.write(outputStream)
                    workbook.close()
                    outputStream?.close()
                }
            } catch (e: Exception) { Log.e("EXCEL", "Template write failed", e) }
        }
    }

    private fun String.normalizeForUsername(): String {
        return this.replace('ç', 'c').replace('Ç', 'c')
            .replace('ğ', 'g').replace('Ğ', 'g')
            .replace('ı', 'i').replace('İ', 'i')
            .replace('ö', 'o').replace('Ö', 'o')
            .replace('ş', 's').replace('Ş', 's')
            .replace('ü', 'u').replace('Ü', 'u')
            .lowercase(Locale.ENGLISH)
            .replace(" ", "_")
            .filter { it.isLetterOrDigit() || it == '_' }
    }

    private fun parseLecturerNameAndTitle(input: String): Pair<String, String> {
        val academicTitles = listOf(
            "Assist. Prof. Dr.", "Assist. Prof.", "Assoc. Prof. Dr.", "Assoc. Prof.",
            "Prof. Dr.", "Prof.Dr.", "Prof Dr", "Prof.", "Prof",
            "Doç. Dr.", "Doç.Dr.", "Doç Dr", "Doç.", "Doç",
            "Dr. Öğr. Üyesi", "Dr.Öğr.Üyesi", "Dr. Öğretim Üyesi", "Dr.", "Dr",
            "Arş. Gör. Dr.", "Arş. Gör.", "Öğr. Gör. Dr.", "Öğr. Gör.", "Arş.Gör.", "Öğr.Gör.",
            "Assist Prof", "Assoc Prof", "Lecturer"
        ).sortedByDescending { it.length }

        val cleanInput = input.trim()
        val lowerInput = cleanInput.lowercase()

        // Handle underscore format (e.g. profdr_metin_zontul)
        val prefixMap = mapOf(
            "profdr_" to "Prof. Dr.",
            "prof_" to "Prof.",
            "docdr_" to "Doç. Dr.",
            "doc_" to "Doç.",
            "dr_" to "Dr.",
            "assistprof_" to "Assist. Prof.",
            "assocprof_" to "Assoc. Prof.",
            "ogrgor_" to "Öğr. Gör.",
            "arsgor_" to "Arş. Gör."
        )

        for ((prefix, titleVal) in prefixMap) {
            if (lowerInput.startsWith(prefix)) {
                val namePart = cleanInput.substring(prefix.length).replace("_", " ").trim()
                val capitalizedName = namePart.split(" ").filter { it.isNotBlank() }
                    .joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    }
                return capitalizedName to titleVal
            }
        }

        // Handle standard prefixes (with spaces/dots)
        for (t in academicTitles) {
            if (cleanInput.startsWith(t, ignoreCase = true)) {
                val namePart = cleanInput.substring(t.length).trim().removePrefix(".").trim()
                return namePart to t
            }
        }

        return cleanInput to ""
    }

    fun importDataFromExcel(context: Context, uri: Uri) {
        viewModelScope.launch {
            _importStatus.value = UiState.Loading
            try {
                val result = withContext(Dispatchers.IO) {
                    val currentDept = userSettings.department ?: Department.COMPUTER_ENGINEERING
                    val existingLecturers = repository.getLecturers().first().associateBy { it.username }
                    
                    val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("File access denied")
                    val workbook = WorkbookFactory.create(inputStream)
                    val sheets = (0 until workbook.numberOfSheets).map { workbook.getSheetAt(it) }

                    var classroomCount = 0
                    var courseCount = 0

                    // Classroom Import
                    for (sheet in sheets) {
                        var headerRowIdx = -1; var codeIdx = -1; var capIdx = -1
                        for (r in 0 until Math.min(sheet.lastRowNum + 1, 10)) {
                            val row = sheet.getRow(r) ?: continue
                            for (c in 0 until row.lastCellNum.toInt()) {
                                val v = row.getCell(c)?.toString()?.trim()?.uppercase() ?: ""
                                if (v == "ROOM CODE" || v == "DERSLİK KODU" || v == "ROOM") codeIdx = c
                                if (v == "CAPACITY" || v.contains("KAPASİTE") || v == "CAP") capIdx = c
                            }
                            if (codeIdx != -1) { headerRowIdx = r; break }
                        }

                        if (codeIdx != -1) {
                            val rooms = mutableListOf<Classroom>()
                            for (r in (headerRowIdx + 1)..sheet.lastRowNum) {
                                val row = sheet.getRow(r) ?: continue
                                val code = row.getCell(codeIdx)?.let { if(it.cellType == CellType.NUMERIC) it.numericCellValue.toLong().toString() else it.toString().trim() } ?: ""
                                if (code.isBlank()) continue
                                val cap = row.getCell(capIdx)?.let { if(it.cellType == CellType.NUMERIC) it.numericCellValue.toInt() else it.toString().toDoubleOrNull()?.toInt() } ?: 0
                                rooms.add(Classroom(roomCode = code, capacity = cap, department = currentDept))
                            }
                            if (rooms.isNotEmpty()) { repository.insertClassrooms(rooms); classroomCount += rooms.size }
                        }
                    }

                    // Course and Lecturer Import
                    val courseSheet = sheets.find { s -> s.sheetName.uppercase().contains("LİSANS") || s.sheetName.uppercase().contains("COURSE") } ?: sheets.first()
                    val cHeader = courseSheet.getRow(0)
                    var cIdx = -1; var nIdx = -1; var lIdx = -1
                    for (i in 0 until (cHeader?.lastCellNum?.toInt() ?: 0)) {
                        val s = cHeader?.getCell(i)?.toString()?.trim()?.uppercase() ?: ""
                        if (s == "COURSE CODE" || s == "KOD") cIdx = i
                        if (s == "COURSE NAME" || s == "AD") nIdx = i
                        if (s == "LECTURER" || s.contains("HOCA") || s.contains("ÖĞRETİM")) lIdx = i
                    }

                    if (cIdx != -1 && lIdx != -1) {
                        val fileCourses = mutableListOf<Course>()
                        val fileLecturers = mutableMapOf<String, Lecturer>()

                        for (i in 1..courseSheet.lastRowNum) {
                            val row = courseSheet.getRow(i) ?: continue
                            val code = row.getCell(cIdx)?.toString()?.trim() ?: ""
                            val lRaw = row.getCell(lIdx)?.toString()?.trim() ?: ""
                            if (code.isBlank() || lRaw.isBlank()) continue

                            val (name, title) = parseLecturerNameAndTitle(lRaw)
                            val username = name.normalizeForUsername()

                            // Merging logic: Check if lecturer already exists in DB or current import batch
                            val existingLecturer = fileLecturers[username] ?: existingLecturers[username]
                            
                            if (existingLecturer != null) {
                                // Update title if current one is better (not Lecturer/blank)
                                val finalTitle = if (title.isNotBlank() && title != "Lecturer") title else existingLecturer.title
                                fileLecturers[username] = existingLecturer.copy(
                                    title = finalTitle,
                                    department = currentDept
                                )
                            } else {
                                val pass = (100000..999999).random().toString()
                                Log.e("IMPORT_PASSWORD", "LECTURER: $name | USER: $username | PASS: $pass")
                                fileLecturers[username] = Lecturer(
                                    name = name, 
                                    title = title.ifBlank { "Lecturer" }, 
                                    department = currentDept, 
                                    username = username, 
                                    password = hashPassword(pass), 
                                    must_change_password = true
                                )
                            }
                            fileCourses.add(Course(code = code, name = row.getCell(nIdx)?.toString()?.trim() ?: "", lecturerName = lRaw, department = currentDept))
                        }
                        repository.insertLecturers(fileLecturers.values.toList())
                        repository.insertCourses(fileCourses)
                        courseCount = fileCourses.size
                    }
                    
                    workbook.close(); inputStream.close()
                    "Import Successful: $classroomCount rooms, $courseCount courses."
                }
                _importStatus.value = UiState.Success(result)
            } catch (e: Exception) {
                Log.e("EXCEL_IMPORT", "Failed", e)
                _importStatus.value = UiState.Error(e.message ?: "Import Error")
            }
        }
    }

    fun clearDatabase() { viewModelScope.launch { repository.clearAll() } }
    fun clearLecturersAndCourses() { viewModelScope.launch { repository.clearLecturers(); repository.clearCourses() } }
    fun clearClassrooms() { viewModelScope.launch { repository.clearClassrooms() } }
    fun resetImportStatus() { _importStatus.value = UiState.Idle }
}
