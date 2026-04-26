package com.example.universityschedulesystem.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

class CourseRepository(private val dao: AppDao) {
    private val firestore = FirebaseFirestore.getInstance()
    private val lecturersColl = firestore.collection("lecturers")
    private val coursesColl = firestore.collection("courses")
    private val classroomsColl = firestore.collection("classrooms")
    private val auditLogsColl = firestore.collection("audit_logs")

    // --- Firestore Methods ---

    fun getLecturers(): Flow<List<Lecturer>> = callbackFlow {
        val subscription = lecturersColl.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                trySend(snapshot.toObjects(Lecturer::class.java))
            }
        }
        awaitClose { subscription.remove() }
    }

    fun getCourses(): Flow<List<Course>> = callbackFlow {
        val subscription = coursesColl.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                trySend(snapshot.toObjects(Course::class.java))
            }
        }
        awaitClose { subscription.remove() }
    }

    fun getClassrooms(): Flow<List<Classroom>> = callbackFlow {
        val subscription = classroomsColl.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                trySend(snapshot.toObjects(Classroom::class.java))
            }
        }
        awaitClose { subscription.remove() }
    }

    fun getAuditLogs(): Flow<List<AuditLog>> = callbackFlow {
        val subscription = auditLogsColl.orderBy("timestamp").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                trySend(snapshot.toObjects(AuditLog::class.java))
            }
        }
        awaitClose { subscription.remove() }
    }

    suspend fun insertLecturers(lecturers: List<Lecturer>) = withContext(Dispatchers.IO) {
        val batch = firestore.batch()
        lecturers.forEach { lecturer ->
            val docRef = if (lecturer.id != 0) lecturersColl.document(lecturer.id.toString()) else lecturersColl.document()
            batch.set(docRef, lecturer)
        }
        batch.commit().await()
    }

    suspend fun insertCourses(courses: List<Course>) = withContext(Dispatchers.IO) {
        val batch = firestore.batch()
        courses.forEach { course ->
            val docRef = if (course.id != 0) coursesColl.document(course.id.toString()) else coursesColl.document()
            batch.set(docRef, course)
        }
        batch.commit().await()
    }

    suspend fun insertClassrooms(classrooms: List<Classroom>) = withContext(Dispatchers.IO) {
        val batch = firestore.batch()
        classrooms.forEach { classroom ->
            val docRef = if (classroom.id != 0) classroomsColl.document(classroom.id.toString()) else classroomsColl.document()
            batch.set(docRef, classroom)
        }
        batch.commit().await()
    }

    suspend fun insertAuditLog(log: AuditLog) = withContext(Dispatchers.IO) {
        auditLogsColl.add(log).await()
    }

    suspend fun updateLecturer(lecturer: Lecturer) = withContext(Dispatchers.IO) {
        lecturersColl.document(lecturer.id.toString()).set(lecturer).await()
    }

    suspend fun updateCourse(course: Course) = withContext(Dispatchers.IO) {
        coursesColl.document(course.id.toString()).set(course).await()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        // Firestore'da toplu silme işlemi batch ile yapılır (limitli)
        // Basitlik için burada tek tek siliyoruz (Phase 2 kapsamında)
        deleteCollection("lecturers")
        deleteCollection("courses")
        deleteCollection("classrooms")
        deleteCollection("audit_logs")
    }

    private suspend fun deleteCollection(collectionPath: String) {
        val collection = firestore.collection(collectionPath)
        val snapshot = collection.get().await()
        for (doc in snapshot.documents) {
            doc.reference.delete().await()
        }
    }

    // --- Local Room Methods (Fallback or Sync) ---
    // Not: Artık Firestore ana veritabanı olduğu için Room metodları opsiyoneldir.
}
