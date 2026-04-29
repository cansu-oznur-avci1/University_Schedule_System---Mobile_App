package com.example.universityschedulesystem.ui

import android.content.Context
import android.net.Uri
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
        userSettings = UserSettings()
    }

    fun changePassword(newPassword: String) {
        val current = loggedInLecturer ?: return
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
            if (loggedInLecturer?.id == lecturer.id) {
                loggedInLecturer = loggedInLecturer?.copy(availability = availability)
            }
            repository.updateLecturer(lecturer.copy(availability = availability))
        }
    }

    fun addClassroom(classroom: Classroom) {
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
            repository.insertAuditLog(AuditLog(user = userSettings.name.ifEmpty { "Admin" }, action = "Assignment", details = "${course.code} assigned to ${lecturer.name} in ${classroom.roomCode} at $day $timeSlot"))
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
            } catch (e: Exception) { e.printStackTrace() }
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

    fun importDataFromExcel(context: Context, uri: Uri) {
        viewModelScope.launch {
            _importStatus.value = UiState.Loading
            try {
                val result = withContext(Dispatchers.IO) {
                    val currentDept = userSettings.department ?: Department.COMPUTER_ENGINEERING
                    val existingLecturers = repository.getLecturers().first().associateBy { it.username }
                    
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val workbook = WorkbookFactory.create(inputStream)
                    val sheets = (0 until workbook.numberOfSheets).map { workbook.getSheetAt(it) }

                    // Classroom Import
                    val classroomSheet = sheets.find { s ->
                        s.sheetName.contains("DERSLİKLER", true) || s.sheetName.contains("Room", true) || s.sheetName.contains("Classroom", true)
                    }

                    if (classroomSheet != null) {
                        val newClassrooms = mutableListOf<Classroom>()
                        val header = classroomSheet.getRow(0)
                        val codeIdx = (0 until (header?.lastCellNum?.toInt() ?: 1)).find { header?.getCell(it)?.toString()?.contains("Room Code", true) == true || header?.getCell(it)?.toString()?.contains("Oda", true) == true || header?.getCell(it)?.toString()?.contains("Code", true) == true } ?: 0
                        val capIdx = (0 until (header?.lastCellNum?.toInt() ?: 2)).find { header?.getCell(it)?.toString()?.contains("Cap", true) == true || header?.getCell(it)?.toString()?.contains("Kapasite", true) == true || header?.getCell(it)?.toString()?.contains("Capacity", true) == true } ?: 1

                        for (i in 1..classroomSheet.lastRowNum) {
                            val row = classroomSheet.getRow(i) ?: continue
                            val code = row.getCell(codeIdx)?.toString()?.trim() ?: ""
                            val capRaw = row.getCell(capIdx)?.toString()?.trim() ?: "0"
                            val cap = capRaw.toDoubleOrNull()?.toInt() ?: 0
                            if (code.isNotEmpty()) {
                                newClassrooms.add(Classroom(roomCode = code, capacity = cap, department = currentDept))
                            }
                        }
                        repository.insertClassrooms(newClassrooms)
                    }

                    // Course & Lecturer Import
                    val courseSheet = sheets.find { s ->
                        s.sheetName.contains("LİSANS", true) || s.sheetName.contains("Course", true) || s.sheetName.contains("Ders", true)
                    } ?: workbook.getSheetAt(0)

                    val fileCourses = mutableListOf<Course>()
                    val fileLecturers = mutableMapOf<String, Lecturer>()
                    
                    val academicTitles = listOf(
                        "Prof. Dr.", "Prof.Dr.", "Prof Dr", "Prof.", "Prof",
                        "Doç. Dr.", "Doç.Dr.", "Doç Dr", "Doç.", "Doç",
                        "Dr. Öğr. Üyesi", "Dr.Öğr.Üyesi", "Dr. Öğretim Üyesi", "Dr.Öğretim Üyesi",
                        "Öğr. Gör. Dr.", "Öğr.Gör.Dr.", "Öğr. Gör.", "Öğr.Gör.",
                        "Arş. Gör. Dr.", "Arş.Gör.Dr.", "Arş. Gör.", "Arş.Gör.",
                        "Dr.", "Dr", "Assoc. Prof.", "Assist. Prof.", "Assoc.", "Assist."
                    ).sortedByDescending { it.length }

                    val cHeader = courseSheet.getRow(0)
                    val cCodeIdx = (0 until (cHeader?.lastCellNum?.toInt() ?: 1)).find { cHeader?.getCell(it)?.toString()?.contains("Course Code", true) == true || cHeader?.getCell(it)?.toString()?.contains("Kod", true) == true || (cHeader?.getCell(it)?.toString()?.contains("Code", true) == true && !cHeader?.getCell(it)?.toString()?.contains("Room", true)!!) } ?: 0
                    val cNameIdx = (0 until (cHeader?.lastCellNum?.toInt() ?: 2)).find { cHeader?.getCell(it)?.toString()?.contains("Course Name", true) == true || cHeader?.getCell(it)?.toString()?.contains("Ad", true) == true || cHeader?.getCell(it)?.toString()?.contains("Name", true) == true } ?: 1
                    val cLectIdx = (0 until (cHeader?.lastCellNum?.toInt() ?: 3)).find { cHeader?.getCell(it)?.toString()?.contains("Lecturer", true) == true || cHeader?.getCell(it)?.toString()?.contains("Hoca", true) == true } ?: 2

                    for (i in 1..courseSheet.lastRowNum) {
                        val row = courseSheet.getRow(i) ?: continue
                        val code = row.getCell(cCodeIdx)?.toString()?.trim() ?: ""
                        val name = row.getCell(cNameIdx)?.toString()?.trim() ?: ""
                        val lNameRaw = row.getCell(cLectIdx)?.toString()?.trim() ?: ""
                        if (code.isBlank() || lNameRaw.isBlank()) continue

                        var nameNoTitle = lNameRaw
                        var titleStr = ""
                        for (title in academicTitles) {
                            if (lNameRaw.startsWith(title, ignoreCase = true)) {
                                titleStr = title
                                nameNoTitle = lNameRaw.substring(title.length).trim().removePrefix(".").trim()
                                break
                            }
                        }

                        val username = nameNoTitle.normalizeForUsername()
                        if (!fileLecturers.containsKey(username)) {
                            val existing = existingLecturers[username]
                            if (existing != null) {
                                fileLecturers[username] = existing.copy(title = titleStr.ifBlank { "Lecturer" }, department = currentDept)
                            } else {
                                val rawPassword = (100000..999999).random().toString()
                                println("DEBUG: Lecturer ${nameNoTitle} password: $rawPassword")
                                fileLecturers[username] = Lecturer(
                                    name = nameNoTitle,
                                    title = titleStr.ifBlank { "Lecturer" },
                                    department = currentDept,
                                    username = username,
                                    password = hashPassword(rawPassword),
                                    must_change_password = true
                                )
                            }
                        }
                        fileCourses.add(Course(code = code, name = name, lecturerName = lNameRaw, department = currentDept))
                    }
                    repository.insertLecturers(fileLecturers.values.toList())
                    repository.insertCourses(fileCourses)
                    workbook.close(); inputStream?.close()
                    "Import Successful"
                }
                _importStatus.value = UiState.Success(result)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _importStatus.value = UiState.Error(e.message ?: "Failed")
            }
        }
    }

    fun clearDatabase() { viewModelScope.launch { repository.clearAll() } }
    fun resetImportStatus() { _importStatus.value = UiState.Idle }
}
