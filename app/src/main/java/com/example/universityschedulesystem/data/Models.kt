package com.example.universityschedulesystem.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.firebase.firestore.PropertyName
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class Department(val displayName: String) {
    COMPUTER_ENGINEERING("Computer Engineering"),
    ELECTRICAL_ELECTRONICS("Electrical and Electronics Engineering"),
    MECHANICAL_ENGINEERING("Mechanical Engineering"),
    AERONAUTICAL_ENGINEERING("Aeronautical Engineering"),
    AGRICULTURAL_DEPARTMENT("Agricultural Department");
    constructor() : this("Computer Engineering")
}

enum class Position(val displayName: String) {
    ADMIN("Admin (IT)"),
    LECTURER("Lecturer");
    constructor() : this("Lecturer")
}

enum class EducationLevel(val displayName: String) {
    UNDERGRADUATE("Undergraduate"),
    GRADUATE("Graduate");
    constructor() : this("Undergraduate")
}

data class UserSettings(
    val name: String = "",
    val surname: String = "",
    val department: Department? = null,
    val position: Position? = null,
    val educationLevel: EducationLevel? = null,
    val isRegistered: Boolean = false,
    val lecturerId: Int? = null,
    val lecturerUsername: String? = null
)

@Entity(tableName = "lecturers")
data class Lecturer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
    val title: String = "",
    val department: Department = Department.COMPUTER_ENGINEERING,
    var username: String = "",
    var password: String = "", 
    var availability: List<AvailabilitySlot> = emptyList(),
    @get:PropertyName("must_change_password")
    @set:PropertyName("must_change_password")
    var must_change_password: Boolean = true
)

data class AvailabilitySlot(
    val day: String = "",
    val timeSlot: String = "",
    @get:PropertyName("isAvailable")
    @set:PropertyName("isAvailable")
    var isAvailable: Boolean = true
)

@Entity(tableName = "classrooms")
data class Classroom(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val roomCode: String = "",
    val capacity: Int = 0,
    val department: Department = Department.COMPUTER_ENGINEERING
)

@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val code: String = "",
    val name: String = "",
    val lecturerName: String = "",
    val department: Department = Department.COMPUTER_ENGINEERING,
    val educationLevel: EducationLevel = EducationLevel.UNDERGRADUATE
)

@Entity(tableName = "schedule_entries")
data class ScheduleEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val courseCode: String = "",
    val lecturerUsername: String = "",
    val roomCode: String = "",
    val day: String = "",
    val timeSlot: String = ""
)

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val user: String = "",
    val action: String = "",
    val details: String = ""
)

class Converters {
    private val gson = Gson()
    @TypeConverter
    fun fromAvailabilityList(value: List<AvailabilitySlot>): String = gson.toJson(value)
    @TypeConverter
    fun toAvailabilityList(value: String): List<AvailabilitySlot> {
        val listType = object : TypeToken<List<AvailabilitySlot>>() {}.type
        return gson.fromJson(value, listType)
    }
}
