package com.example.universityschedulesystem.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CourseRepository(private val dao: AppDao) {

    // DAO zaten Flow döndürdüğü için doğrudan döndürüyoruz
    fun getLecturers(): Flow<List<Lecturer>> = dao.getAllLecturers()

    fun getCourses(): Flow<List<Course>> = dao.getAllCourses()

    fun getAuditLogs(): Flow<List<AuditLog>> = dao.getAllAuditLogs()

    suspend fun insertAuditLog(log: AuditLog) = withContext(Dispatchers.IO) {
        dao.insertAuditLog(log)
    }

    suspend fun insertLecturers(lecturers: List<Lecturer>) = withContext(Dispatchers.IO) {
        dao.insertLecturers(lecturers)
    }

    suspend fun insertCourses(courses: List<Course>) = withContext(Dispatchers.IO) {
        dao.insertCourses(courses)
    }

    suspend fun updateLecturer(lecturer: Lecturer) = withContext(Dispatchers.IO) {
        dao.updateLecturer(lecturer)
    }

    suspend fun updateCourse(course: Course) = withContext(Dispatchers.IO) {
        dao.updateCourse(course)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearLecturers()
        dao.clearCourses()
        dao.clearAuditLogs()
    }
}
