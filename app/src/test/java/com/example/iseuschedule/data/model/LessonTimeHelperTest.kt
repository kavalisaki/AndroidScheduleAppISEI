package com.example.iseuschedule.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class LessonTimeHelperTest {

    private val slot = "08:30 - 10:05"

    @Test
    fun parseTimeSlot_validFormat_extractsCorrectRange() {
        val range = LessonTimeHelper.parseTimeSlot(slot)
        assertNotNull(range)
        assertEquals(LocalTime.of(8, 30), range?.start)
        assertEquals(LocalTime.of(10, 5), range?.end)
    }

    @Test
    fun parseTimeSlot_invalidFormat_returnsNull() {
        assertNull(LessonTimeHelper.parseTimeSlot("Invalid"))
        assertNull(LessonTimeHelper.parseTimeSlot("10:00"))
        assertNull(LessonTimeHelper.parseTimeSlot(""))
    }

    @Test
    fun isLessonActive_beforeLesson_returnsFalse() {
        val timeBefore = LocalTime.of(8, 29, 59)
        assertFalse(LessonTimeHelper.isLessonActive(slot, timeBefore))
    }

    @Test
    fun isLessonActive_atStartBoundary_returnsTrue() {
        val timeStart = LocalTime.of(8, 30, 0)
        assertTrue(LessonTimeHelper.isLessonActive(slot, timeStart))
    }

    @Test
    fun isLessonActive_duringLesson_returnsTrue() {
        val timeDuring = LocalTime.of(9, 15, 0)
        assertTrue(LessonTimeHelper.isLessonActive(slot, timeDuring))
    }

    @Test
    fun isLessonActive_atEndBoundary_returnsTrue() {
        val timeEnd = LocalTime.of(10, 5, 0)
        assertTrue(LessonTimeHelper.isLessonActive(slot, timeEnd))
    }

    @Test
    fun isLessonActive_afterLesson_returnsFalse() {
        val timeAfter = LocalTime.of(10, 5, 1)
        assertFalse(LessonTimeHelper.isLessonActive(slot, timeAfter))
    }

    @Test
    fun isLessonActive_betweenLessonsBreak_returnsFalse() {
        val breakTime = LocalTime.of(10, 10, 0)
        val secondSlot = "10:15 - 11:50"

        assertFalse("Break time is after first lesson", LessonTimeHelper.isLessonActive(slot, breakTime))
        assertFalse("Break time is before second lesson", LessonTimeHelper.isLessonActive(secondSlot, breakTime))
    }

    @Test
    fun isToday_matchesSameDate() {
        val today = LocalDate.of(2026, 10, 19)
        assertTrue(LessonTimeHelper.isToday("19.10.2026", today))
        assertFalse(LessonTimeHelper.isToday("20.10.2026", today))
        assertFalse(LessonTimeHelper.isToday("", today))
        assertFalse(LessonTimeHelper.isToday("invalid", today))
    }

    @Test
    fun shouldDefaultToNextWeek_isTrueOnlyOnSunday() {
        val sunday = LocalDate.of(2026, 10, 18) // Sunday
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
        assertTrue(LessonTimeHelper.shouldDefaultToNextWeek(sunday))

        val monday = LocalDate.of(2026, 10, 19) // Monday
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
        assertFalse(LessonTimeHelper.shouldDefaultToNextWeek(monday))

        val saturday = LocalDate.of(2026, 10, 17) // Saturday
        assertEquals(DayOfWeek.SATURDAY, saturday.dayOfWeek)
        assertFalse(LessonTimeHelper.shouldDefaultToNextWeek(saturday))
    }

    @Test
    fun getTodayDayOfWeekNumber_mapsMondayTo1AndSundayTo7() {
        val monday = LocalDate.of(2026, 10, 19)
        assertEquals(1, LessonTimeHelper.getTodayDayOfWeekNumber(monday))

        val sunday = LocalDate.of(2026, 10, 18)
        assertEquals(7, LessonTimeHelper.getTodayDayOfWeekNumber(sunday))
    }

    @Test
    fun isLessonActive_lessonObject_returnsFalseIfNotToday() {
        val lesson = Lesson(
            order = 1,
            time = "08:30 - 10:05",
            type = LessonType.LECTURE,
            subject = "Информатика",
            teacher = "Иванов",
            room = "101"
        )
        val duringTime = LocalTime.of(9, 0)
        assertFalse(LessonTimeHelper.isLessonActive(lesson, isToday = false, now = duringTime))
    }

    @Test
    fun isLessonActive_lessonObject_returnsTrueIfTodayAndDuringTime() {
        val lesson = Lesson(
            order = 1,
            time = "08:30 - 10:05",
            type = LessonType.LECTURE,
            subject = "Информатика",
            teacher = "Иванов",
            room = "101"
        )
        val duringTime = LocalTime.of(9, 0)
        assertTrue(LessonTimeHelper.isLessonActive(lesson, isToday = true, now = duringTime))
    }

    @Test
    fun isLessonActive_lessonObject_usesOrderFallbackWhenTimeHasNoHyphen() {
        val lesson = Lesson(
            order = 2,
            time = "10:15",
            type = LessonType.PRACTICE,
            subject = "Физика",
            teacher = "Петров",
            room = "202"
        )
        val duringTime = LocalTime.of(10, 30) // Order 2 slot: 10:15 - 11:50
        assertTrue(LessonTimeHelper.isLessonActive(lesson, isToday = true, now = duringTime))

        val outsideTime = LocalTime.of(12, 0)
        assertFalse(LessonTimeHelper.isLessonActive(lesson, isToday = true, now = outsideTime))
    }
}
