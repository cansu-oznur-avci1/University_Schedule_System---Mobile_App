package com.example.universityschedulesystem.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class Department(val displayName: String) {
    COMPUTER_ENGINEERING("Computer Engineering"),
    ELECTRICAL_ELECTRONICS("Electrical and Electronics Engineering"),
    MECHANICAL_ENGINEERING("Mechanical Engineering"),
    AERONAUTICAL_ENGINEERING("Aeronautical Engineering"),
    AGRICULTURAL_DEPARTMENT("Agricultural Department")
}

enum class Position(val displayName: String) {
    ADMIN("Admin (IT)"),
    LECTURER("Lecturer")
}

enum class EducationLevel(val displayName: String) {
    UNDERGRADUATE("Undergraduate"),
    GRADUATE("Graduate")
}

data class UserSettings(
    val name: String = "",
    val surname: String = "",
    val department: Department? = null,
    val position: Position? = null,
    val educationLevel: EducationLevel? = null,
    val isRegistered: Boolean = false,
    val lecturerId: Int? = null // Link to Lecturer entity if position is LECTURER
)

@Entity(tableName = "lecturers")
data class Lecturer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val title: String,
    val department: Department,
    var username: String = "",
    var password: String = "",
    var availability: List<AvailabilitySlot> = emptyList()
)

data class AvailabilitySlot(
    val day: String,
    val timeSlot: String,
    val isAvailable: Boolean = true
)

data class ScheduledSlot(
    val day: String,
    val timeSlot: String
)

@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val code: String,
    val name: String,
    val lecturerName: String,
    val department: Department,
    val educationLevel: EducationLevel = EducationLevel.UNDERGRADUATE,
    val scheduledSlots: List<ScheduledSlot> = emptyList()
)

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val user: String,
    val action: String,
    val details: String
)

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromAvailabilityList(value: List<AvailabilitySlot>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toAvailabilityList(value: String): List<AvailabilitySlot> {
        val listType = object : TypeToken<List<AvailabilitySlot>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromScheduledSlotList(value: List<ScheduledSlot>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toScheduledSlotList(value: String): List<ScheduledSlot> {
        val listType = object : TypeToken<List<ScheduledSlot>>() {}.type
        return gson.fromJson(value, listType)
    }
}
