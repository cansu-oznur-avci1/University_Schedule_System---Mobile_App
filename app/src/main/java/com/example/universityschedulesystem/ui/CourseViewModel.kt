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
                    // Sync the logged in lecturer reference with the fresh list from Firestore
                    loggedInLecturer?.let { current ->
                        lecturers.find { it.username == current.username }?.let {
                            loggedInLecturer = it
                        }
                    }
                    selectedLecturerForCalendar?.let { current ->
                        selectedLecturerForCalendar = lecturers.find { it.id == current.id }
                    }
                }.launchIn(this)

                repository.getCourses().onEach { _coursesState.value = it }.launchIn(this)
                repository.getClassrooms().onEach { _classroomsState.value = it }.launchIn(this)
                repository.getAuditLogs().onEach { _auditLogs.value = it }.launchIn(this)
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

    fun checkConflict(lecturer: Lecturer, slots: List<ScheduledSlot>, classroomId: Int?): String? {
        val currentCourses = _coursesState.value
        for (slot in slots) {
            val lecturerConflict = currentCourses.any { course ->
                course.lecturerName.contains(lecturer.name) && 
                course.scheduledSlots.any { it.day == slot.day && it.timeSlot == slot.timeSlot }
            }
            if (lecturerConflict) return "${lecturer.name} is already teaching during ${slot.day} ${slot.timeSlot}"

            if (classroomId != null) {
                val classroomConflict = currentCourses.any { course ->
                    course.classroomId == classroomId && 
                    course.scheduledSlots.any { it.day == slot.day && it.timeSlot == slot.timeSlot }
                }
                if (classroomConflict) return "The selected classroom is already occupied during ${slot.day} ${slot.timeSlot}"
            }
        }
        return null
    }

    fun assignCourse(courseId: Int, slots: List<ScheduledSlot>, classroomId: Int? = null) {
        viewModelScope.launch {
            val course = _coursesState.value.find { it.id == courseId } ?: return@launch
            repository.updateCourse(course.copy(scheduledSlots = slots, classroomId = classroomId))
            repository.insertAuditLog(AuditLog(user = "Admin", action = "Schedule", details = "${course.code} assigned to classroom $classroomId"))
        }
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
                lecturerId = lecturer.id
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
            // Optimistic UI update for the current user
            if (loggedInLecturer?.id == lecturer.id) {
                loggedInLecturer = loggedInLecturer?.copy(availability = availability)
            }
            repository.updateLecturer(lecturer.copy(availability = availability))
        }
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val workbook = WorkbookFactory.create(inputStream)
                    
                    val classroomSheet = workbook.getSheet("DERSLİKLER")
                    val courseSheet = workbook.getSheet("LİSANS") ?: workbook.getSheetAt(0)

                    if (classroomSheet != null) {
                        val newClassrooms = mutableListOf<Classroom>()
                        for (i in 1..classroomSheet.lastRowNum) {
                            val row = classroomSheet.getRow(i) ?: continue
                            val code = row.getCell(0)?.toString()?.trim() ?: ""
                            val cap = row.getCell(1)?.toString()?.toDouble()?.toInt() ?: 0
                            if (code.isNotEmpty()) {
                                newClassrooms.add(Classroom(id = i, roomCode = code, capacity = cap, department = userSettings.department ?: Department.COMPUTER_ENGINEERING))
                            }
                        }
                        repository.insertClassrooms(newClassrooms)
                    }

                    val fileCourses = mutableListOf<Course>()
                    val fileLecturers = mutableMapOf<String, Lecturer>()
                    val academicTitles = listOf(
                        "Prof. Dr.", "Doç. Dr.", "Dr. Öğr. Üyesi", "Dr. Öğretim Üyesi",
                        "Öğr. Gör. Dr.", "Öğr. Gör.", "Arş. Gör. Dr.", "Arş. Gör.",
                        "Prof.", "Doç.", "Dr.", "Assoc. Prof.", "Assist. Prof.", "Assoc.", "Assist."
                    ).sortedByDescending { it.length }

                    for (i in 1..courseSheet.lastRowNum) {
                        val row = courseSheet.getRow(i) ?: continue
                        val code = row.getCell(0)?.toString()?.trim() ?: ""
                        val name = row.getCell(1)?.toString()?.trim() ?: ""
                        val lNameRaw = row.getCell(2)?.toString()?.trim() ?: ""
                        if (code.isBlank() || lNameRaw.isBlank()) continue

                        var nameNoTitle = lNameRaw
                        var titleStr = ""
                        
                        for (title in academicTitles) {
                            if (lNameRaw.startsWith(title, ignoreCase = true)) {
                                titleStr = title
                                nameNoTitle = lNameRaw.substring(title.length).trim()
                                break
                            }
                        }

                        if (!fileLecturers.containsKey(nameNoTitle)) {
                            val rawPassword = (100000..999999).random().toString()
                            println("DEBUG: Lecturer ${nameNoTitle} password: $rawPassword")
                            
                            fileLecturers[nameNoTitle] = Lecturer(
                                id = i,
                                name = nameNoTitle,
                                title = titleStr.ifBlank { "Lecturer" },
                                department = userSettings.department ?: Department.COMPUTER_ENGINEERING,
                                username = nameNoTitle.normalizeForUsername(),
                                password = hashPassword(rawPassword),
                                must_change_password = true
                            )
                        }
                        fileCourses.add(Course(id = i, code = code, name = name, lecturerName = lNameRaw, department = userSettings.department ?: Department.COMPUTER_ENGINEERING))
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

    fun removeCourseAssignment(courseId: Int) {
        viewModelScope.launch {
            val course = _coursesState.value.find { it.id == courseId } ?: return@launch
            repository.updateCourse(course.copy(scheduledSlots = emptyList(), classroomId = null))
        }
    }

    fun clearDatabase() { viewModelScope.launch { repository.clearAll() } }
    fun resetImportStatus() { _importStatus.value = UiState.Idle }
}
