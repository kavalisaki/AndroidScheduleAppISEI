package com.example.iseuschedule.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ScheduleDiffHelperTest {

    private fun createSampleLesson(
        order: Int = 1,
        time: String = "08:30 - 10:05",
        subject: String = "Математический анализ",
        teacher: String = "Иванов И.И.",
        room: String = "214-1",
        type: LessonType = LessonType.LECTURE
    ) = Lesson(
        order = order,
        time = time,
        type = type,
        subject = subject,
        teacher = teacher,
        room = room
    )

    private fun createDay(
        dayNum: Int = 1,
        dayName: String = "Понедельник",
        date: String = "13.10.2026",
        lessons: List<Lesson> = listOf(createSampleLesson())
    ) = DaySchedule(
        dayNum = dayNum,
        dayName = dayName,
        date = date,
        hasSchedule = lessons.isNotEmpty(),
        lessons = lessons
    )

    @Test
    fun findDifferences_identicalSchedules_returnsNoChanges() {
        val week1 = WeekSchedule(0, "Текущая неделя", "13.10.2026", listOf(createDay()))
        val week2 = WeekSchedule(0, "Текущая неделя", "13.10.2026", listOf(createDay()))

        val result = ScheduleDiffHelper.findDifferences(week1, week2)
        assertFalse(result.hasChanges)
        assertTrue(result.changes.isEmpty())
    }

    @Test
    fun findDifferences_modifiedRoom_detectsChange() {
        val oldLesson = createSampleLesson(room = "214-1")
        val newLesson = createSampleLesson(room = "305-2")

        val weekOld = WeekSchedule(0, "Текущая неделя", "13.10.2026", listOf(createDay(lessons = listOf(oldLesson))))
        val weekNew = WeekSchedule(0, "Текущая неделя", "13.10.2026", listOf(createDay(lessons = listOf(newLesson))))

        val result = ScheduleDiffHelper.findDifferences(weekOld, weekNew)
        assertTrue(result.hasChanges)
        assertEquals(1, result.changes.size)
        assertEquals(ScheduleChange.ChangeType.MODIFIED, result.changes[0].type)
        assertTrue(result.changes[0].description.contains("214-1"))
        assertTrue(result.changes[0].description.contains("305-2"))
    }

    @Test
    fun findDifferences_addedLesson_detectsAddition() {
        val lesson1 = createSampleLesson(order = 1)
        val lesson2 = createSampleLesson(order = 2, subject = "Физика")

        val weekOld = WeekSchedule(0, "Текущая неделя", "13.10.2026", listOf(createDay(lessons = listOf(lesson1))))
        val weekNew = WeekSchedule(0, "Текущая неделя", "13.10.2026", listOf(createDay(lessons = listOf(lesson1, lesson2))))

        val result = ScheduleDiffHelper.findDifferences(weekOld, weekNew)
        assertTrue(result.hasChanges)
        assertEquals(1, result.changes.size)
        assertEquals(ScheduleChange.ChangeType.ADDED, result.changes[0].type)
        assertTrue(result.changes[0].description.contains("Физика"))
    }

    @Test
    fun calculateLiveStatus_duringActiveLesson_returnsActive() {
        val lesson = createSampleLesson(order = 1, time = "08:30 - 10:05")
        val now = LocalTime.of(9, 0) // 09:00 is during 08:30 - 10:05

        val status = LessonTimeHelper.calculateLiveStatus(listOf(lesson), isToday = true, now = now)
        assertTrue(status is LiveLessonStatus.Active)
        val active = status as LiveLessonStatus.Active
        assertEquals(1, active.lesson.order)
        assertEquals(3900L, active.remainingSeconds) // 10:05 - 09:00 = 65 mins = 3900s
    }

    @Test
    fun calculateLiveStatus_duringBreak_returnsBreak() {
        val lesson1 = createSampleLesson(order = 1, time = "08:30 - 10:05")
        val lesson2 = createSampleLesson(order = 2, time = "10:15 - 11:50")
        val now = LocalTime.of(10, 10) // during break between 10:05 and 10:15

        val status = LessonTimeHelper.calculateLiveStatus(listOf(lesson1, lesson2), isToday = true, now = now)
        assertTrue(status is LiveLessonStatus.Break)
        val breakStatus = status as LiveLessonStatus.Break
        assertEquals(2, breakStatus.nextLesson.order)
        assertEquals(300L, breakStatus.remainingSeconds) // 5 minutes = 300s
    }
}
