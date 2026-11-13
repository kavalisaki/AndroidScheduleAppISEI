package com.example.iseuschedule.data.parser

import com.example.iseuschedule.data.model.DaySchedule
import com.example.iseuschedule.data.model.Lesson
import com.example.iseuschedule.data.model.LessonType
import com.example.iseuschedule.data.model.WeekSchedule
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.regex.Pattern

object ScheduleParser {

    private val DATE_PATTERN = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4}")
    private val TIME_PATTERN = Pattern.compile("\\d{1,2}:\\d{2}")

    private val DAYS_MAP = mapOf(
        "Понедельник" to 1,
        "Вторник" to 2,
        "Среда" to 3,
        "Четверг" to 4,
        "Пятница" to 5,
        "Суббота" to 6,
        "Воскресенье" to 7
    )

    private val DAY_ORDER = listOf(
        "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
    )

    fun normalizeString(s: String): String {
        val trans = mapOf(
            'A' to 'А', 'B' to 'В', 'C' to 'С', 'E' to 'Е', 'H' to 'Н',
            'K' to 'К', 'M' to 'М', 'O' to 'О', 'P' to 'Р', 'T' to 'Т', 'X' to 'Х',
            'a' to 'а', 'b' to 'в', 'c' to 'с', 'e' to 'е', 'h' to 'н',
            'k' to 'к', 'm' to 'м', 'o' to 'о', 'p' to 'р', 't' to 'т', 'x' to 'х'
        )
        return s.map { trans[it] ?: it }.joinToString("").trim().lowercase()
    }

    fun extractAspnetFormState(doc: Document): MutableMap<String, String> {
        val data = mutableMapOf<String, String>()
        for (input in doc.select("input")) {
            val name = input.attr("name")
            if (name.isNotEmpty()) {
                data[name] = input.attr("value")
            }
        }
        for (select in doc.select("select")) {
            val name = select.attr("name")
            if (name.isNotEmpty()) {
                val selected = select.select("option[selected]").first() ?: select.select("option").first()
                if (selected != null) {
                    data[name] = selected.attr("value")
                }
            }
        }
        return data
    }

    fun extractGroups(doc: Document): List<String> {
        val groupSelect = doc.selectFirst("select#ddlGroup") ?: return emptyList()
        return groupSelect.select("option")
            .map { it.text().trim() }
            .filter { it.isNotBlank() && !it.startsWith("--") && !it.contains("выберите", ignoreCase = true) }
    }

    fun parseGroupsHtml(html: String): List<String> {
        return extractGroups(Jsoup.parse(html))
    }

    /**
     * Resolves group name by prioritizing:
     * 1. Exact match
     * 2. Normalized equals (handling Cyrillic / Latin lookalikes)
     * 3. StartsWith match (prefix match)
     * 4. Contains match (fallback)
     */
    fun findMatchingGroup(groups: List<String>, targetGroup: String): String? {
        val targetTrim = targetGroup.trim()
        if (targetTrim.isEmpty()) return null

        // 1. Exact match
        groups.firstOrNull { it.trim() == targetTrim }?.let { return it }

        // 2. Normalized equals
        val targetNorm = normalizeString(targetTrim)
        groups.firstOrNull { normalizeString(it) == targetNorm }?.let { return it }

        // 3. StartsWith
        groups.firstOrNull { normalizeString(it).startsWith(targetNorm) }?.let { return it }

        // 4. Fallback contains
        return groups.firstOrNull { normalizeString(it).contains(targetNorm) }
    }

    /**
     * Resolves group option (pair of text and value) using strict priority matching:
     * 1. Exact text match
     * 2. Normalized equals
     * 3. StartsWith match
     * 4. Fallback contains
     */
    fun findMatchingGroupOption(options: List<Pair<String, String>>, targetGroup: String): Pair<String, String>? {
        val targetTrim = targetGroup.trim()
        if (targetTrim.isEmpty()) return null

        // 1. Exact match
        options.firstOrNull { it.first.trim() == targetTrim }?.let { return it }

        // 2. Normalized equals
        val targetNorm = normalizeString(targetTrim)
        options.firstOrNull { normalizeString(it.first) == targetNorm }?.let { return it }

        // 3. StartsWith
        options.firstOrNull { normalizeString(it.first).startsWith(targetNorm) }?.let { return it }

        // 4. Fallback contains
        return options.firstOrNull { normalizeString(it.first).contains(targetNorm) }
    }

    fun parseScheduleHtml(html: String, weekOffset: Int = 0): WeekSchedule {
        val doc = Jsoup.parse(html)
        var targetTable = doc.selectFirst("table#TT")
        if (targetTable == null) {
            for (table in doc.select("table")) {
                if (table.select("tr").size > 5) {
                    targetTable = table
                    break
                }
            }
        }

        if (targetTable == null) {
            return WeekSchedule(
                week = weekOffset,
                weekName = getWeekName(weekOffset),
                mondayDate = getFallbackMondayDate(weekOffset),
                days = emptyList()
            )
        }

        val rows = targetTable.select("tr")
        val scheduleByDay = mutableMapOf<String, MutableList<List<String>>>()
        val dayDates = mutableMapOf<String, String>()
        var currentDay: String? = null

        for (row in rows) {
            val cells = row.select("td, th")
            if (cells.isEmpty()) continue
            val cellTexts = cells.map { it.text().trim() }
            if (cellTexts.all { it.isEmpty() }) continue

            var dayFound: String? = null
            var dateFound: String? = null
            val firstCell = cellTexts.firstOrNull() ?: ""

            for (dayName in DAYS_MAP.keys) {
                if (firstCell.contains(dayName, ignoreCase = true)) {
                    dayFound = dayName
                    val matcher = DATE_PATTERN.matcher(firstCell)
                    if (matcher.find()) {
                        dateFound = matcher.group()
                    }
                    break
                }
            }

            if (dayFound != null) {
                currentDay = dayFound
                if (dateFound != null) {
                    dayDates[currentDay] = dateFound
                }
                if (!scheduleByDay.containsKey(currentDay)) {
                    scheduleByDay[currentDay] = mutableListOf()
                }
                if (cellTexts.size >= 4) {
                    val lessonData = cellTexts.drop(1).filter { it.isNotEmpty() }
                    if (lessonData.isNotEmpty()) {
                        scheduleByDay[currentDay]?.add(lessonData)
                    }
                }
                continue
            }

            if (currentDay != null && cellTexts.isNotEmpty()) {
                val cleanData = cellTexts.filter { it.isNotEmpty() }
                if (cleanData.size >= 2) {
                    val firstClean = cleanData.first()
                    val isOtherDay = DAYS_MAP.keys.any { firstClean.contains(it, ignoreCase = true) }
                    if (!isOtherDay) {
                        scheduleByDay[currentDay]?.add(cleanData)
                    }
                }
            }
        }

        // Extract monday date from caption or fallback (needed before building days for Saturday date calc)
        val caption = targetTable.selectFirst("caption")?.text() ?: ""
        val captionMatcher = DATE_PATTERN.matcher(caption)
        val mondayDate = if (captionMatcher.find()) {
            captionMatcher.group()
        } else {
            getFallbackMondayDate(weekOffset)
        }

        val daysResult = mutableListOf<DaySchedule>()
        for (dayName in DAY_ORDER) {
            val rawLessons = scheduleByDay[dayName]

            // Skip days not present in HTML (except Saturday — guaranteed below)
            if (rawLessons == null && dayName != "Суббота") continue

            val lessons = mutableListOf<Lesson>()
            if (rawLessons != null) {
                for ((idx, raw) in rawLessons.withIndex()) {
                    val parsed = parseLesson(idx + 1, raw)
                    if (parsed != null) {
                        lessons.add(parsed)
                    }
                }
            }

            val dayDate = dayDates[dayName] ?: if (dayName == "Суббота") {
                // Auto-calculate Saturday date as mondayDate + 5 days
                calculateSaturdayDate(mondayDate)
            } else {
                ""
            }

            daysResult.add(
                DaySchedule(
                    dayNum = DAYS_MAP[dayName] ?: 1,
                    dayName = dayName,
                    date = dayDate,
                    hasSchedule = lessons.isNotEmpty(),
                    lessons = lessons
                )
            )
        }

        return WeekSchedule(
            week = weekOffset,
            weekName = getWeekName(weekOffset),
            mondayDate = mondayDate,
            days = daysResult
        )
    }

    private val TITLE_PREFIX_REGEX = Regex(
        "^(?:ст\\.\\s*преп\\.?|ст\\.\\s*пр\\.?|зав\\.\\s*каф\\.?|доц\\.?|проф\\.?|асс\\.?|преп\\.?|доцент|профессор|ассистент)\\s*",
        RegexOption.IGNORE_CASE
    )

    private val TEACHER_NAME_REGEX = Regex(
        "([А-ЯЁ][а-яёА-ЯЁA-Za-z'’\\-]+)\\s+([А-ЯЁ])\\.?\\s*([А-ЯЁ])?\\.?"
    )

    fun determineLessonOrder(timeSlot: String, fallbackOrder: Int): Int {
        val time = timeSlot.replace(" ", "")
        return when {
            time.contains("08:30") || time.contains("8:30") -> 1
            time.contains("10:15") -> 2
            time.contains("12:15") || time.contains("12:00") -> 3
            time.contains("14:00") -> 4
            time.contains("15:45") -> 5
            time.contains("17:30") -> 6
            time.contains("19:10") || time.contains("19:00") -> 7
            else -> fallbackOrder
        }
    }

    fun parseTeacher(rawItem: String): String {
        val trimmed = rawItem.trim()
        val cleaned = trimmed.replace(TITLE_PREFIX_REGEX, "").trim()
        val match = TEACHER_NAME_REGEX.find(cleaned)
        return if (match != null) {
            val lastName = match.groupValues[1]
            val initial1 = match.groupValues[2]
            val initial2 = match.groupValues[3]
            if (initial2.isNotEmpty()) {
                "$lastName $initial1.$initial2."
            } else {
                "$lastName $initial1."
            }
        } else {
            cleaned.ifEmpty { trimmed }
        }
    }

    private fun parseLesson(order: Int, items: List<String>): Lesson? {
        if (items.isEmpty()) return null

        val timeSlot = items.firstOrNull() ?: ""
        val resolvedOrder = determineLessonOrder(timeSlot, order)
        var lessonType = LessonType.UNKNOWN
        var subject = ""
        var teacher = ""
        var room = ""
        var subgroup: Int? = null

        for ((idx, rawItem) in items.withIndex()) {
            val item = rawItem.trim()
            val itemLower = item.lowercase()

            if (lessonType == LessonType.UNKNOWN) {
                if (itemLower.contains("лек")) {
                    lessonType = LessonType.LECTURE
                } else if (itemLower.contains("лаб")) {
                    lessonType = LessonType.LAB
                } else if (itemLower.contains("пр") && !itemLower.contains("препод")) {
                    lessonType = LessonType.PRACTICE
                }
            }

            if (subject.isEmpty() && idx >= 1) {
                val isRoleOrRoom = listOf("доц.", "ст.пр.", "ст. преп.", "пр.", "проф.", "асс.", "зав. каф.", "ауд.", "к2", "к1")
                    .any { itemLower.contains(it) }
                if (!isRoleOrRoom && item.length > 3) {
                    val clean = item
                        .replace(Regex("\\s*[12][пp]/гр\\s*$", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("^(лек\\.|лаб\\.|пр\\.)\\s*", RegexOption.IGNORE_CASE), "")
                        .trim()
                    subject = clean
                }
            }

            if (teacher.isEmpty()) {
                val hasTitle = listOf("доц", "преп", "проф", "асс", "зав. каф", "зав.каф").any { itemLower.contains(it) }
                val isNameLike = TEACHER_NAME_REGEX.containsMatchIn(item)
                if (hasTitle || (isNameLike && !itemLower.contains("ауд") && !itemLower.contains("лек") && !itemLower.contains("лаб") && !itemLower.contains("пр."))) {
                    teacher = parseTeacher(item)
                }
            }

            if (room.isEmpty()) {
                if (itemLower.contains("ауд.")) {
                    val r = item.replace("ауд.", "", ignoreCase = true).trim()
                    if (r.isNotEmpty() && r != "," && r != ", ") {
                        room = r
                    }
                } else if (Regex("\\d+\\s*,?\\s*[Кк]\\d").containsMatchIn(item)) {
                    room = item
                }
            }

            if (subgroup == null) {
                if (itemLower.contains("1п/гр") || itemLower.contains("1 подгр")) {
                    subgroup = 1
                } else if (itemLower.contains("2п/гр") || itemLower.contains("2 подгр")) {
                    subgroup = 2
                }
            }
        }

        if (subject.isEmpty() && items.size > 2) {
            subject = items[1]
                .replace(Regex("^(лек\\.|лаб\\.|пр\\.)\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*[12][пp]/гр\\s*$", RegexOption.IGNORE_CASE), "")
                .trim()
        }

        return Lesson(
            order = resolvedOrder,
            time = timeSlot,
            type = lessonType,
            subject = subject,
            teacher = teacher,
            room = room,
            subgroup = subgroup
        )
    }

    private fun getWeekName(offset: Int): String {
        return when (offset) {
            0 -> "Текущая неделя"
            1 -> "Следующая неделя"
            2 -> "Через 2 недели"
            else -> if (offset > 0) "+$offset нед." else "$offset нед."
        }
    }

    /**
     * Calculates Saturday's date (mondayDate + 5 days) from a monday date string in dd.MM.yyyy format.
     * Returns empty string if mondayDate is blank or cannot be parsed.
     */
    private fun calculateSaturdayDate(mondayDate: String): String {
        if (mondayDate.isBlank()) return ""
        return try {
            val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            val monday = LocalDate.parse(mondayDate, formatter)
            val saturday = monday.plusDays(5)
            saturday.format(formatter)
        } catch (_: Exception) {
            ""
        }
    }

    private fun getFallbackMondayDate(offset: Int): String {
        return try {
            val now = LocalDate.now()
            val monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
            monday.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        } catch (_: Exception) {
            ""
        }
    }
}
