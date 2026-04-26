package com.example.universityschedulesystem

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.universityschedulesystem.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.appDao()

    var userSettings by mutableStateOf(UserSettings())
    var lecturers by mutableStateOf<List<Lecturer>>(emptyList())
    var courses by mutableStateOf<List<Course>>(emptyList())
    var auditLogs by mutableStateOf<List<AuditLog>>(emptyList())
    var isDataImported by mutableStateOf(false)
    
    var loggedInLecturer by mutableStateOf<Lecturer?>(null)
    var selectedLecturerForCalendar by mutableStateOf<Lecturer?>(null)

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            lecturers = dao.getAllLecturers()
            courses = dao.getAllCourses()
            auditLogs = dao.getAllAuditLogs()
            isDataImported = lecturers.isNotEmpty()
        }
    }

    fun clearDatabase() {
        viewModelScope.launch {
            dao.clearLecturers()
            dao.clearCourses()
            dao.clearAuditLogs()
            loadData()
        }
    }

    fun saveSettings(settings: UserSettings) {
        userSettings = settings
        viewModelScope.launch {
            dao.insertAuditLog(AuditLog(user = "${settings.name} ${settings.surname}", action = "Update Settings", details = "Position: ${settings.position}"))
            refreshLogs()
        }
    }

    suspend fun login(username: String, password: String): Boolean {
        val lecturer = dao.loginLecturer(username, password)
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
            dao.insertAuditLog(AuditLog(user = lecturer.name, action = "Login", details = "Lecturer logged in"))
            refreshLogs()
            true
        } else {
            false
        }
    }

    fun logout() {
        userSettings = UserSettings()
        loggedInLecturer = null
        selectedLecturerForCalendar = null
    }

    fun updateLecturerAvailability(lecturer: Lecturer, availability: List<AvailabilitySlot>) {
        val updatedLecturer = lecturer.copy(availability = availability)
        viewModelScope.launch {
            dao.updateLecturer(updatedLecturer)
            dao.insertAuditLog(AuditLog(user = lecturer.name, action = "Update Availability", details = "Updated slots"))
            lecturers = dao.getAllLecturers()
            if (loggedInLecturer?.id == lecturer.id) loggedInLecturer = updatedLecturer
            refreshLogs()
        }
    }

    fun assignCourse(courseId: Int, slots: List<ScheduledSlot>) {
        viewModelScope.launch {
            val course = courses.find { it.id == courseId } ?: return@launch
            val updatedCourse = course.copy(scheduledSlots = slots)
            dao.updateCourse(updatedCourse)
            dao.insertAuditLog(AuditLog(user = "Admin", action = "Assign Multi-Slot Course", details = "${course.code} to ${slots.size} slots"))
            courses = dao.getAllCourses()
            refreshLogs()
        }
    }
    
    fun removeCourseAssignment(courseId: Int) {
        viewModelScope.launch {
            val course = courses.find { it.id == courseId } ?: return@launch
            val updatedCourse = course.copy(scheduledSlots = emptyList())
            dao.updateCourse(updatedCourse)
            dao.insertAuditLog(AuditLog(user = "Admin", action = "Remove Assignment", details = "Removed ${course.code}"))
            courses = dao.getAllCourses()
            refreshLogs()
        }
    }

    private suspend fun refreshLogs() {
        auditLogs = dao.getAllAuditLogs()
    }

    fun writeExcelTemplate(context: Context, uri: Uri) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Template")
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("Course Code")
            headerRow.createCell(1).setCellValue("Course Name")
            headerRow.createCell(2).setCellValue("Lecturer")

            val sampleRow = sheet.createRow(1)
            sampleRow.createCell(0).setCellValue("CNG 101")
            sampleRow.createCell(1).setCellValue("Intro to Computer Engineering")
            sampleRow.createCell(2).setCellValue("Prof. Dr. Halit Bakır")

            context.contentResolver.openOutputStream(uri)?.use { os ->
                workbook.write(os)
            }
            workbook.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun String.toTurkishUsername(): String {
        val turkishLocale = Locale("tr", "TR")
        return this.lowercase(turkishLocale)
            .replace(" ", "_")
            .replace("ı", "i")
            .replace("ğ", "g")
            .replace("ü", "u")
            .replace("ş", "s")
            .replace("ö", "o")
            .replace("ç", "c")
    }

    fun importDataFromExcel(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream: InputStream? = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)
                }
                val workbook = withContext(Dispatchers.IO) {
                    WorkbookFactory.create(inputStream)
                }
                val sheet = workbook.getSheetAt(0)
                
                val existingLecturers = dao.getAllLecturers()
                val existingCourses = dao.getAllCourses()
                
                val fileCourses = mutableListOf<Course>()
                val fileLecturers = mutableMapOf<String, Lecturer>()

                for (i in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue
                    val courseCode = row.getCell(0)?.toString()?.trim() ?: ""
                    val courseName = row.getCell(1)?.toString()?.trim() ?: ""
                    val lecturerFullName = row.getCell(2)?.toString()?.trim() ?: ""

                    if (courseCode.isBlank() || lecturerFullName.isBlank()) continue

                    val titles = listOf("Assoc. Prof.", "Assist. Prof.", "Prof.", "Dr.")
                    var nameWithoutTitle = lecturerFullName
                    var foundTitle = ""
                    for (title in titles) {
                        if (lecturerFullName.startsWith(title)) {
                            nameWithoutTitle = lecturerFullName.removePrefix(title).trim()
                            foundTitle = title
                            break
                        }
                    }

                    if (!fileLecturers.containsKey(nameWithoutTitle)) {
                        val username = nameWithoutTitle.toTurkishUsername()
                        val password = (100000..999999).random().toString()
                        fileLecturers[nameWithoutTitle] = Lecturer(
                            name = nameWithoutTitle,
                            title = foundTitle,
                            department = userSettings.department ?: Department.COMPUTER_ENGINEERING,
                            username = username,
                            password = password
                        )
                    }

                    fileCourses.add(Course(
                        code = courseCode,
                        name = courseName,
                        lecturerName = lecturerFullName,
                        department = userSettings.department ?: Department.COMPUTER_ENGINEERING
                    ))
                }

                val allCoursesExist = fileCourses.all { fileCourse -> 
                    existingCourses.any { it.code == fileCourse.code }
                }

                if (allCoursesExist && fileCourses.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "This file's content is already in the system.", Toast.LENGTH_LONG).show()
                    }
                    workbook.close()
                    return@launch
                }

                val newLecturersToInsert = mutableListOf<Lecturer>()
                fileLecturers.forEach { (name, lecturer) ->
                    if (existingLecturers.none { it.name == name }) {
                        newLecturersToInsert.add(lecturer)
                    }
                }

                val newCoursesToInsert = fileCourses.filter { fileCourse ->
                    existingCourses.none { it.code == fileCourse.code }
                }

                if (newLecturersToInsert.isNotEmpty()) {
                    dao.insertLecturers(newLecturersToInsert)
                }
                if (newCoursesToInsert.isNotEmpty()) {
                    dao.insertCourses(newCoursesToInsert)
                }
                
                dao.insertAuditLog(AuditLog(
                    user = "Admin", 
                    action = "Import Data", 
                    details = "Added ${newCoursesToInsert.size} courses and ${newLecturersToInsert.size} lecturers"
                ))
                
                loadData()
                workbook.close()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Import complete: ${newCoursesToInsert.size} new courses added.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error importing Excel: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
