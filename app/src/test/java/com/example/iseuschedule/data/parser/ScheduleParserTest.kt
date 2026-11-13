package com.example.iseuschedule.data.parser

import com.example.iseuschedule.data.model.LessonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ScheduleParserTest {

    @Test
    fun normalizeString_correctlyReplacesCyrillicAndLatinLookalikes() {
        val mixed = "31ПИ1" // Cyrillic П and И
        val normalized = ScheduleParser.normalizeString(mixed)
        assertNotNull(normalized)
    }

    @Test
    fun parseScheduleHtml_withEmptyHtml_returnsEmptyDays() {
        val schedule = ScheduleParser.parseScheduleHtml("<html><body></body></html>", 0)
        assertEquals(0, schedule.week)
        assertEquals(0, schedule.days.size)
    }

    @Test
    fun parseScheduleHtml_withMockTable_extractsDaysAndLessons() {
        val mockHtml = """
            <html><body>
            <table id="TT">
                <caption>Расписание на неделю 19.10.2026</caption>
                <tr>
                    <td>Понедельник 19.10.2026</td>
                    <td>08:30 - 10:05</td>
                    <td>лек.</td>
                    <td>Сетевые технологии</td>
                    <td>доц. Иванов И.И.</td>
                    <td>ауд. 101</td>
                </tr>
            </table>
            </body></html>
        """.trimIndent()

        val schedule = ScheduleParser.parseScheduleHtml(mockHtml, 0)
        // Monday from HTML + guaranteed Saturday (auto-generated with hasSchedule=false)
        assertEquals(2, schedule.days.size)
        val day = schedule.days[0]
        assertEquals("Понедельник", day.dayName)
        assertEquals(1, day.lessons.size)
        val lesson = day.lessons[0]
        assertEquals(1, lesson.order)
        assertEquals("08:30 - 10:05", lesson.time)
        assertEquals("Сетевые технологии", lesson.subject)

        // Verify guaranteed Saturday is auto-generated
        val saturday = schedule.days[1]
        assertEquals("Суббота", saturday.dayName)
        assertEquals(6, saturday.dayNum)
        assertEquals(false, saturday.hasSchedule)
        assertEquals(0, saturday.lessons.size)
        // Saturday date should be mondayDate + 5 days: 19.10.2026 + 5 = 24.10.2026
        assertEquals("24.10.2026", saturday.date)
    }

    @Test
    fun group_starting_from_second_pair_assigns_order_2() {
        val mockHtml = """
            <html><body>
            <table id="TT">
                <caption>Расписание на неделю 19.10.2026</caption>
                <tr>
                    <td>Понедельник 19.10.2026</td>
                    <td>10:15 - 11:50</td>
                    <td>лек.</td>
                    <td>Методы оптимизации</td>
                    <td>доц. Сидоров С.С.</td>
                    <td>ауд. 201</td>
                </tr>
            </table>
            </body></html>
        """.trimIndent()

        val schedule = ScheduleParser.parseScheduleHtml(mockHtml, 0)
        val lessons = schedule.days[0].lessons
        assertEquals(1, lessons.size)
        assertEquals("First lesson starting at 10:15 must have order 2", 2, lessons[0].order)
    }

    @Test
    fun two_subgroups_in_same_slot_have_same_lesson_order() {
        val mockHtml = """
            <html><body>
            <table id="TT">
                <caption>Расписание на неделю 19.10.2026</caption>
                <tr>
                    <td>Понедельник 19.10.2026</td>
                    <td>12:15 - 13:50</td>
                    <td>лаб.</td>
                    <td>Программирование 1п/гр</td>
                    <td>доц. Иванов И.И.</td>
                    <td>ауд. 101</td>
                </tr>
                <tr>
                    <td>12:15 - 13:50</td>
                    <td>лаб.</td>
                    <td>Программирование 2п/гр</td>
                    <td>асс. Петров П.П.</td>
                    <td>ауд. 102</td>
                </tr>
            </table>
            </body></html>
        """.trimIndent()

        val schedule = ScheduleParser.parseScheduleHtml(mockHtml, 0)
        val lessons = schedule.days[0].lessons
        assertEquals(2, lessons.size)
        assertEquals("Both subgroups in slot 12:15 must have order 3", 3, lessons[0].order)
        assertEquals("Both subgroups in slot 12:15 must have order 3", 3, lessons[1].order)
        assertEquals(1, lessons[0].subgroup)
        assertEquals(2, lessons[1].subgroup)
    }

    @Test
    fun parseTeacher_supports_complex_titles_and_formats() {
        assertEquals("Иванов И.И.", ScheduleParser.parseTeacher("ст. преп. Иванов И.И."))
        assertEquals("Петров П.П.", ScheduleParser.parseTeacher("доц. Петров П. П."))
        assertEquals("Сидоров С.С.", ScheduleParser.parseTeacher("асс. Сидоров С.С."))
        assertEquals("Кузнецов К.К.", ScheduleParser.parseTeacher("зав. каф. Кузнецов К. К."))
        assertEquals("Смирнова Е.В.", ScheduleParser.parseTeacher("ст.пр. Смирнова Е.В."))
        assertEquals("Смирнов А.", ScheduleParser.parseTeacher("проф. Смирнов А."))
        assertEquals("О'Коннор Д.Д.", ScheduleParser.parseTeacher("доцент О'Коннор Д.Д."))
    }

    @Test
    fun subject_name_is_not_truncated_by_parser() {
        val longSubject = "Проектирование и разработка высоконагруженных распределенных информационных систем корпоративного уровня"
        val mockHtml = """
            <html><body>
            <table id="TT">
                <caption>Расписание на неделю 19.10.2026</caption>
                <tr>
                    <td>Понедельник 19.10.2026</td>
                    <td>08:30 - 10:05</td>
                    <td>лек.</td>
                    <td>$longSubject</td>
                    <td>проф. Сидоров С.С.</td>
                    <td>ауд. 101</td>
                </tr>
            </table>
            </body></html>
        """.trimIndent()

        val schedule = ScheduleParser.parseScheduleHtml(mockHtml, 0)
        val parsedSubject = schedule.days[0].lessons[0].subject
        assertEquals("Parser should preserve the full subject title without ellipsis truncation", longSubject, parsedSubject)
    }

    @Test
    fun determineLessonOrder_maps_all_standard_time_slots() {
        assertEquals(1, ScheduleParser.determineLessonOrder("08:30 - 10:05", 99))
        assertEquals(2, ScheduleParser.determineLessonOrder("10:15 - 11:50", 99))
        assertEquals(3, ScheduleParser.determineLessonOrder("12:15 - 13:50", 99))
        assertEquals(4, ScheduleParser.determineLessonOrder("14:00 - 15:35", 99))
        assertEquals(5, ScheduleParser.determineLessonOrder("15:45 - 17:20", 99))
        assertEquals(6, ScheduleParser.determineLessonOrder("17:30 - 19:05", 99))
        assertEquals(7, ScheduleParser.determineLessonOrder("19:10 - 20:45", 99))
        assertEquals(99, ScheduleParser.determineLessonOrder("09:00 - 10:00", 99))
    }

    @Test
    fun findMatchingGroup_exactMatch_takesPrecedenceOverSubstrings() {
        // Prevents fuzzy matching bug where "31ПИ1" mistakenly matched "131ПИ1" or "31ПИ10"
        val groups = listOf("131ПИ1", "31ПИ10", "31ПИ1", "31ПИ", "31ПИ11")

        val match = ScheduleParser.findMatchingGroup(groups, "31ПИ1")
        assertEquals("Exact target '31ПИ1' must be matched rather than substrings like '131ПИ1'", "31ПИ1", match)

        val matchShort = ScheduleParser.findMatchingGroup(groups, "31ПИ")
        assertEquals("Exact target '31ПИ' must be matched rather than prefixes like '31ПИ1'", "31ПИ", matchShort)
    }

    @Test
    fun findMatchingGroup_normalizedCyrillicAndLatin_matchesCorrectly() {
        val groups = listOf("31ПИ1", "41ТТ1", "А31ТТ1")
        // "A31TT1" typed with Latin A and T (lookalikes of Cyrillic А and Т)
        val match = ScheduleParser.findMatchingGroup(groups, "A31TT1")
        assertEquals("Normalized match should resolve Latin/Cyrillic lookalikes", "А31ТТ1", match)

        // "41TT1" typed with Latin T
        val matchTT = ScheduleParser.findMatchingGroup(groups, "41TT1")
        assertEquals("Normalized match should resolve Latin/Cyrillic lookalikes", "41ТТ1", matchTT)
    }

    @Test
    fun findMatchingGroupOption_resolvesCorrectOptionValue() {
        val options = listOf(
            "131ПИ1" to "val_131",
            "31ПИ10" to "val_3110",
            "31ПИ1" to "val_311"
        )
        val matchedOption = ScheduleParser.findMatchingGroupOption(options, "31ПИ1")
        assertNotNull(matchedOption)
        assertEquals("val_311", matchedOption?.second)
    }

    @Test
    fun rateLimiter_allowsFirstRequestImmediately_andEnforcesInterval() = kotlinx.coroutines.test.runTest {
        var currentTime = 1000L
        val rateLimiter = com.example.iseuschedule.data.NetworkRateLimiter(
            minIntervalMs = 3000L,
            timeProvider = { currentTime }
        )

        // First request: allowed immediately
        org.junit.Assert.assertTrue(rateLimiter.isRequestAllowed())
        assertEquals(0L, rateLimiter.getRemainingWaitMs())
        rateLimiter.acquire()

        // 500ms later: not allowed
        currentTime += 500L
        org.junit.Assert.assertFalse(rateLimiter.isRequestAllowed())
        assertEquals(2500L, rateLimiter.getRemainingWaitMs())

        // 3000ms after initial request: allowed again
        currentTime += 2500L
        org.junit.Assert.assertTrue(rateLimiter.isRequestAllowed())
        assertEquals(0L, rateLimiter.getRemainingWaitMs())
    }

    @Test
    fun saturday_with_lessons_hasSchedule_is_true() {
        val mockHtml = """
            <html><body>
            <table id="TT">
                <caption>Расписание на неделю 19.10.2026</caption>
                <tr>
                    <td>Суббота 24.10.2026</td>
                    <td>08:30 - 10:05</td>
                    <td>лек.</td>
                    <td>Экология человека</td>
                    <td>проф. Петров П.П.</td>
                    <td>ауд. 305</td>
                </tr>
            </table>
            </body></html>
        """.trimIndent()

        val schedule = ScheduleParser.parseScheduleHtml(mockHtml, 0)
        val saturday = schedule.days.find { it.dayName == "Суббота" }
        assertNotNull("Saturday must be present", saturday)
        assertEquals(true, saturday?.hasSchedule)
        assertEquals(1, saturday?.lessons?.size)
        assertEquals("Экология человека", saturday?.lessons?.get(0)?.subject)
        assertEquals("24.10.2026", saturday?.date)
    }

    @Test
    fun saturday_without_lessons_in_html_hasSchedule_is_false() {
        val mockHtml = """
            <html><body>
            <table id="TT">
                <caption>Расписание на неделю 19.10.2026</caption>
                <tr>
                    <td>Понедельник 19.10.2026</td>
                    <td>08:30 - 10:05</td>
                    <td>лек.</td>
                    <td>Физика</td>
                    <td>доц. Сидоров С.С.</td>
                    <td>ауд. 201</td>
                </tr>
                <tr>
                    <td>Суббота 24.10.2026</td>
                </tr>
            </table>
            </body></html>
        """.trimIndent()

        val schedule = ScheduleParser.parseScheduleHtml(mockHtml, 0)
        val saturday = schedule.days.find { it.dayName == "Суббота" }
        assertNotNull("Saturday must be present", saturday)
        assertEquals(false, saturday?.hasSchedule)
        assertEquals(0, saturday?.lessons?.size)
        assertEquals("24.10.2026", saturday?.date)
    }

    @Test
    fun saturday_missing_from_html_is_auto_generated_with_calculated_date_and_hasSchedule_false() {
        val mockHtml = """
            <html><body>
            <table id="TT">
                <caption>Расписание на неделю 12.10.2026</caption>
                <tr>
                    <td>Вторник 13.10.2026</td>
                    <td>10:15 - 11:50</td>
                    <td>практ.</td>
                    <td>Иностранный язык</td>
                    <td>преп. Ковалева А.В.</td>
                    <td>ауд. 410</td>
                </tr>
            </table>
            </body></html>
        """.trimIndent()

        val schedule = ScheduleParser.parseScheduleHtml(mockHtml, 0)
        val saturday = schedule.days.find { it.dayName == "Суббота" }
        assertNotNull("Saturday must be auto-generated when missing from HTML", saturday)
        assertEquals(6, saturday?.dayNum)
        assertEquals(false, saturday?.hasSchedule)
        assertEquals(0, saturday?.lessons?.size)
        // mondayDate is 12.10.2026 -> saturdayDate is 12 + 5 = 17.10.2026
        assertEquals("17.10.2026", saturday?.date)
    }
}

