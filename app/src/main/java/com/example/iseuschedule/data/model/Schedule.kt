package com.example.iseuschedule.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WeekSchedule(
    val week: Int,
    val weekName: String,
    val mondayDate: String,
    val days: List<DaySchedule>
)

@Serializable
data class DaySchedule(
    val dayNum: Int,
    val dayName: String,
    val date: String,
    val hasSchedule: Boolean,
    val lessons: List<Lesson>
)

@Serializable
data class Lesson(
    val order: Int,
    val time: String,
    val type: LessonType,
    val subject: String,
    val teacher: String,
    val room: String,
    val subgroup: Int? = null
)

@Serializable
enum class LessonType(val displayName: String, val emoji: String) {
    LECTURE("Лекция", "📖"),
    PRACTICE("Практика", "✏️"),
    LAB("Лаб.", "🔬"),
    UNKNOWN("-", "📌")
}
