package com.example.universityschedulesystem.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

class CourseRepository(private val dao: AppDao) {
    private val firestore = FirebaseFirestore.getInstance()
    private val lecturersColl = firestore.collection("lecturers")
    private val coursesColl = firestore.collection("courses")
    private val classroomsColl = firestore.collection("classrooms")
    private val auditLogsColl = firestore.collection("audit_logs")
    private val scheduleColl = firestore.collection("schedule_entries")

    fun getLecturers(): Flow<List<Lecturer>> = callbackFlow {
        val subscription = lecturersColl.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) trySend(snapshot.toObjects(Lecturer::class.java))
        }
        awaitClose { subscription.remove() }
    }

    fun getCourses(): Flow<List<Course>> = callbackFlow {
        val subscription = coursesColl.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) trySend(snapshot.toObjects(Course::class.java))
        }
        awaitClose { subscription.remove() }
    }

    fun getClassrooms(): Flow<List<Classroom>> = callbackFlow {
        val subscription = classroomsColl.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) trySend(snapshot.toObjects(Classroom::class.java))
        }
        awaitClose { subscription.remove() }
    }

    fun getAuditLogs(): Flow<List<AuditLog>> = callbackFlow {
        val subscription = auditLogsColl.orderBy("timestamp").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) trySend(snapshot.toObjects(AuditLog::class.java))
        }
        awaitClose { subscription.remove() }
    }

    fun getScheduleEntries(): Flow<List<ScheduleEntry>> = callbackFlow {
        val subscription = scheduleColl.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) trySend(snapshot.toObjects(ScheduleEntry::class.java))
        }
        awaitClose { subscription.remove() }
    }

    suspend fun insertLecturers(lecturers: List<Lecturer>) = withContext(Dispatchers.IO) {
        val batch = firestore.batch()
        lecturers.forEach { l -> 
            val docId = if (l.username.isNotEmpty()) l.username else l.id.toString()
            batch.set(lecturersColl.document(docId), l) 
        }
        batch.commit().await()
    }

    suspend fun insertCourses(courses: List<Course>) = withContext(Dispatchers.IO) {
        val batch = firestore.batch()
        courses.forEach { c -> batch.set(coursesColl.document(c.code), c) }
        batch.commit().await()
    }

    suspend fun insertClassrooms(classrooms: List<Classroom>) = withContext(Dispatchers.IO) {
        val batch = firestore.batch()
        classrooms.forEach { c -> batch.set(classroomsColl.document(c.roomCode), c) }
        batch.commit().await()
    }

    suspend fun addClassroom(classroom: Classroom) = withContext(Dispatchers.IO) {
        classroomsColl.document(classroom.roomCode).set(classroom).await()
    }

    suspend fun deleteClassroom(classroom: Classroom) = withContext(Dispatchers.IO) {
        classroomsColl.document(classroom.roomCode).delete().await()
    }

    suspend fun insertAuditLog(log: AuditLog) = withContext(Dispatchers.IO) {
        auditLogsColl.add(log).await()
    }

    suspend fun updateLecturer(lecturer: Lecturer) = withContext(Dispatchers.IO) {
        val docId = if (lecturer.username.isNotEmpty()) lecturer.username else lecturer.id.toString()
        lecturersColl.document(docId).set(lecturer).await()
    }

    suspend fun insertScheduleEntry(entry: ScheduleEntry) = withContext(Dispatchers.IO) {
        val entryId = "${entry.lecturerUsername}_${entry.day}_${entry.timeSlot}"
        scheduleColl.document(entryId).set(entry).await()
    }

    suspend fun deleteScheduleEntry(entry: ScheduleEntry) = withContext(Dispatchers.IO) {
        val entryId = "${entry.lecturerUsername}_${entry.day}_${entry.timeSlot}"
        scheduleColl.document(entryId).delete().await()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        deleteCollection("lecturers")
        deleteCollection("courses")
        deleteCollection("classrooms")
        deleteCollection("audit_logs")
        deleteCollection("schedule_entries")
    }

    suspend fun clearLecturers() = withContext(Dispatchers.IO) {
        deleteCollection("lecturers")
    }

    suspend fun clearCourses() = withContext(Dispatchers.IO) {
        deleteCollection("courses")
    }

    suspend fun clearClassrooms() = withContext(Dispatchers.IO) {
        deleteCollection("classrooms")
    }

    private suspend fun deleteCollection(path: String) {
        val col = firestore.collection(path)
        val snap = col.get().await()
        for (doc in snap.documents) doc.reference.delete().await()
    }
}
