package com.example.iseuschedule.data.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

interface TimeProvider {
    fun currentDate(): LocalDate = LocalDate.now()
    fun currentTime(): LocalTime = LocalTime.now()
}

object DefaultTimeProvider : TimeProvider

data class TimeRange(val start: LocalTime, val end: LocalTime)

sealed class LiveLessonStatus {
    object NoLessons : LiveLessonStatus()
    data class BeforeFirst(val firstLesson: Lesson, val remainingSeconds: Long) : LiveLessonStatus()
    data class Active(val lesson: Lesson, val remainingSeconds: Long, val progress: Float) : LiveLessonStatus()
    data class Break(val nextLesson: Lesson, val remainingSeconds: Long) : LiveLessonStatus()
    object Finished : LiveLessonStatus()
}

object LessonTimeHelper {

    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm")
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    var timeProvider: TimeProvider = DefaultTimeProvider

    fun parseTimeSlot(timeSlot: String): TimeRange? {
        val parts = timeSlot.split("-").map { it.trim() }
        if (parts.size != 2) return null
        return try {
            val start = LocalTime.parse(parts[0], TIME_FORMATTER)
            val end = LocalTime.parse(parts[1], TIME_FORMATTER)
            TimeRange(start, end)
        } catch (_: Exception) {
            null
        }
    }

    fun getTimeRangeForLesson(lesson: Lesson): TimeRange? {
        val timeSlot = if (lesson.time.contains("-")) lesson.time else {
            when (lesson.order) {
                1 -> "08:30 - 10:05"
                2 -> "10:15 - 11:50"
                3 -> "12:15 - 13:50"
                4 -> "14:00 - 15:35"
                5 -> "15:45 - 17:20"
                6 -> "17:30 - 19:05"
                7 -> "19:10 - 20:45"
                else -> return null
            }
        }
        return parseTimeSlot(timeSlot)
    }

    /**
     * Checks if the lesson is actively taking place at [now].
     */
    fun isLessonActive(timeSlot: String, now: LocalTime = timeProvider.currentTime()): Boolean {
        val range = parseTimeSlot(timeSlot) ?: return false
        return !now.isBefore(range.start) && !now.isAfter(range.end)
    }

    fun isLessonActive(timeSlot: String, timeProvider: TimeProvider): Boolean {
        return isLessonActive(timeSlot, timeProvider.currentTime())
    }

    /**
     * Checks if [lesson] is actively taking place right now.
     */
    fun isLessonActive(lesson: Lesson, isToday: Boolean, now: LocalTime = timeProvider.currentTime()): Boolean {
        if (!isToday) return false
        val range = getTimeRangeForLesson(lesson) ?: return false
        return !now.isBefore(range.start) && !now.isAfter(range.end)
    }

    fun isLessonActive(lesson: Lesson, isToday: Boolean, timeProvider: TimeProvider): Boolean {
        return isLessonActive(lesson, isToday, timeProvider.currentTime())
    }

    /**
     * Calculates the real-time live status and remaining seconds for today's lessons.
     */
    fun calculateLiveStatus(
        lessons: List<Lesson>,
        isToday: Boolean,
        now: LocalTime = timeProvider.currentTime()
    ): LiveLessonStatus {
        if (!isToday || lessons.isEmpty()) return LiveLessonStatus.NoLessons

        val lessonRanges = lessons.mapNotNull { lesson ->
            getTimeRangeForLesson(lesson)?.let { range -> lesson to range }
        }.sortedBy { it.second.start }

        if (lessonRanges.isEmpty()) return LiveLessonStatus.NoLessons

        val firstLesson = lessonRanges.first()
        if (now.isBefore(firstLesson.second.start)) {
            val remaining = Duration.between(now, firstLesson.second.start).seconds
            return LiveLessonStatus.BeforeFirst(firstLesson.first, remaining.coerceAtLeast(0))
        }

        for (i in lessonRanges.indices) {
            val (lesson, range) = lessonRanges[i]
            if (!now.isBefore(range.start) && !now.isAfter(range.end)) {
                val totalSeconds = Duration.between(range.start, range.end).seconds.coerceAtLeast(1)
                val remainingSeconds = Duration.between(now, range.end).seconds.coerceAtLeast(0)
                val progress = ((totalSeconds - remainingSeconds).toFloat() / totalSeconds).coerceIn(0f, 1f)
                return LiveLessonStatus.Active(lesson, remainingSeconds, progress)
            }

            if (i < lessonRanges.size - 1) {
                val nextLesson = lessonRanges[i + 1]
                if (now.isAfter(range.end) && now.isBefore(nextLesson.second.start)) {
                    val remaining = Duration.between(now, nextLesson.second.start).seconds.coerceAtLeast(0)
                    return LiveLessonStatus.Break(nextLesson.first, remaining)
                }
            }
        }

        val lastLesson = lessonRanges.last()
        return if (now.isAfter(lastLesson.second.end)) {
            LiveLessonStatus.Finished
        } else {
            LiveLessonStatus.NoLessons
        }
    }

    fun formatSecondsToTime(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * Checks if [dateStr] in "dd.MM.yyyy" format corresponds to [today].
     */
    fun isToday(dateStr: String, today: LocalDate = timeProvider.currentDate()): Boolean {
        if (dateStr.isBlank()) return false
        return try {
            val date = LocalDate.parse(dateStr, DATE_FORMATTER)
            date == today
        } catch (_: Exception) {
            false
        }
    }

    fun isToday(dateStr: String, timeProvider: TimeProvider): Boolean {
        return isToday(dateStr, timeProvider.currentDate())
    }

    /**
     * Returns 1 for Monday .. 7 for Sunday.
     */
    fun getTodayDayOfWeekNumber(today: LocalDate = timeProvider.currentDate()): Int {
        return today.dayOfWeek.value
    }

    fun getTodayDayOfWeekNumber(timeProvider: TimeProvider): Int {
        return getTodayDayOfWeekNumber(timeProvider.currentDate())
    }

    /**
     * If today is Sunday, default to the upcoming week (offset = 1).
     */
    fun shouldDefaultToNextWeek(today: LocalDate = timeProvider.currentDate()): Boolean {
        return today.dayOfWeek == DayOfWeek.SUNDAY
    }

    fun shouldDefaultToNextWeek(timeProvider: TimeProvider): Boolean {
        return shouldDefaultToNextWeek(timeProvider.currentDate())
    }
}
