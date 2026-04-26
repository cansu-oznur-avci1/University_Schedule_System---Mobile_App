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
import java.util.Locale

class CourseViewModel(private val repository: CourseRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Lecturer>>>(UiState.Idle)
    val uiState: StateFlow<UiState<List<Lecturer>>> = _uiState.asStateFlow()

    private val _coursesState = MutableStateFlow<List<Course>>(emptyList())
    val coursesState: StateFlow<List<Course>> = _coursesState.asStateFlow()

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
                // Independent observation of data streams
                repository.getLecturers()
                    .onEach { lecturers ->
                        _uiState.value = UiState.Success(lecturers)
                        selectedLecturerForCalendar = lecturers.find { it.id == selectedLecturerForCalendar?.id }
                        loggedInLecturer = lecturers.find { it.id == loggedInLecturer?.id }
                    }
                    .catch { e -> _uiState.value = UiState.Error(e.message ?: "Lecturer load failed") }
                    .launchIn(this)

                repository.getCourses()
                    .onEach { _coursesState.value = it }
                    .launchIn(this)

                repository.getAuditLogs()
                    .onEach { _auditLogs.value = it }
                    .launchIn(this)

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = UiState.Error(e.message ?: "Data sync failed")
            }
        }
    }

    private fun String.toTurkishUsername(): String {
        return this.lowercase(Locale("tr", "TR"))
            .replace(" ", "_")
            .replace("ı", "i")
            .replace("ğ", "g")
            .replace("ü", "u")
            .replace("ş", "s")
            .replace("ö", "o")
            .replace("ç", "c")
    }

    fun writeExcelTemplate(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val workbook = XSSFWorkbook()
                    val sheet = workbook.createSheet("Template")
                    val headerRow = sheet.createRow(0)
                    headerRow.createCell(0).setCellValue("Course Code")
                    headerRow.createCell(1).setCellValue("Course Name")
                    headerRow.createCell(2).setCellValue("Lecturer")

                    val sampleRow = sheet.createRow(1)
                    sampleRow.createCell(0).setCellValue("CNG 101")
                    sampleRow.createCell(1).setCellValue("Programming")
                    sampleRow.createCell(2).setCellValue("Prof. Dr. Halit Bakır")

                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        workbook.write(os)
                    }
                    workbook.close()
                }
                repository.insertAuditLog(AuditLog(user = "Admin", action = "Template", details = "Exported"))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    suspend fun login(username: String, password: String, dao: AppDao): Boolean {
        val normalized = username.toTurkishUsername()
        val lecturer = dao.loginLecturer(normalized, password)
        return if (lecturer != null) {
            loggedInLecturer = lecturer
            userSettings = userSettings.copy(
                name = lecturer.name.split(" ").firstOrNull() ?: "",
                surname = lecturer.name.split(" ").lastOrNull() ?: "",
                position = Position.LECTURER,
                department = lecturer.department,
                isRegistered = true,
                lecturerId = lecturer.id
            )
            repository.insertAuditLog(AuditLog(user = lecturer.name, action = "Login", details = "Success"))
            true
        } else false
    }

    fun logout() {
        userSettings = UserSettings()
        loggedInLecturer = null
        selectedLecturerForCalendar = null
    }

    fun updateLecturerAvailability(lecturer: Lecturer, availability: List<AvailabilitySlot>) {
        viewModelScope.launch {
            repository.updateLecturer(lecturer.copy(availability = availability))
        }
    }

    fun assignCourse(courseId: Int, slots: List<ScheduledSlot>) {
        viewModelScope.launch {
            val course = _coursesState.value.find { it.id == courseId } ?: return@launch
            repository.updateCourse(course.copy(scheduledSlots = slots))
        }
    }

    fun removeCourseAssignment(courseId: Int) {
        viewModelScope.launch {
            val course = _coursesState.value.find { it.id == courseId } ?: return@launch
            repository.updateCourse(course.copy(scheduledSlots = emptyList()))
        }
    }

    fun importDataFromExcel(context: Context, uri: Uri) {
        viewModelScope.launch {
            _importStatus.value = UiState.Loading
            try {
                val result = withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val workbook = WorkbookFactory.create(inputStream)
                    val sheet = workbook.getSheetAt(0)
                    
                    val existingLecturers = repository.getLecturers().first()
                    val existingCourses = repository.getCourses().first()
                    
                    val fileCourses = mutableListOf<Course>()
                    val fileLecturers = mutableMapOf<String, Lecturer>()

                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        val code = row.getCell(0)?.toString()?.trim() ?: ""
                        val name = row.getCell(1)?.toString()?.trim() ?: ""
                        val lName = row.getCell(2)?.toString()?.trim() ?: ""
                        if (code.isBlank() || lName.isBlank()) continue

                        var nameNoTitle = lName
                        listOf("Assoc. Prof. Dr.", "Assist. Prof. Dr.", "Assoc. Prof.", "Assist. Prof.", "Prof. Dr.", "Prof.", "Dr.")
                            .forEach { nameNoTitle = nameNoTitle.removePrefix(it).trim() }

                        if (!fileLecturers.containsKey(nameNoTitle)) {
                            fileLecturers[nameNoTitle] = Lecturer(
                                name = nameNoTitle,
                                title = lName.substring(0, lName.length - nameNoTitle.length).trim().ifBlank { "Lecturer" },
                                department = userSettings.department ?: Department.COMPUTER_ENGINEERING,
                                username = nameNoTitle.toTurkishUsername(),
                                password = (100000..999999).random().toString()
                            )
                        }
                        fileCourses.add(Course(code = code, name = name, lecturerName = lName, department = userSettings.department ?: Department.COMPUTER_ENGINEERING))
                    }

                    val newLecs = fileLecturers.values.filter { fl -> existingLecturers.none { it.name == fl.name } }
                    val newCurs = fileCourses.filter { fc -> existingCourses.none { it.code == fc.code } }

                    if (newLecs.isNotEmpty()) repository.insertLecturers(newLecs)
                    if (newCurs.isNotEmpty()) repository.insertCourses(newCurs)
                    
                    workbook.close()
                    inputStream?.close()
                    "Added ${newCurs.size} courses."
                }
                _importStatus.value = UiState.Success(result)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _importStatus.value = UiState.Error(e.message ?: "Failed")
            }
        }
    }

    fun clearDatabase() {
        viewModelScope.launch { repository.clearAll() }
    }

    fun resetImportStatus() { _importStatus.value = UiState.Idle }
}
