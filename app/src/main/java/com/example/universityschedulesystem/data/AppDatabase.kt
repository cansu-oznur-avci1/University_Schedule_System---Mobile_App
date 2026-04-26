package com.example.universityschedulesystem.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM lecturers")
    fun getAllLecturers(): Flow<List<Lecturer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLecturers(lecturers: List<Lecturer>)

    @Update
    suspend fun updateLecturer(lecturer: Lecturer)

    @Query("SELECT * FROM courses")
    fun getAllCourses(): Flow<List<Course>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<Course>)

    @Update
    suspend fun updateCourse(course: Course)

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<AuditLog>>

    @Insert
    suspend fun insertAuditLog(log: AuditLog)

    @Query("SELECT * FROM lecturers WHERE username = :username AND password = :password LIMIT 1")
    suspend fun loginLecturer(username: String, password: String): Lecturer?

    @Query("DELETE FROM lecturers")
    suspend fun clearLecturers()

    @Query("DELETE FROM courses")
    suspend fun clearCourses()

    @Query("DELETE FROM audit_logs")
    suspend fun clearAuditLogs()
}

@Database(entities = [Lecturer::class, Course::class, AuditLog::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "university_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
