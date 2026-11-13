package com.example.iseuschedule.data.model

/**
 * Represents a single detected change in the schedule for a specific day.
 */
data class ScheduleChange(
    val dayName: String,
    val date: String,
    val description: String,
    val type: ChangeType
) {
    enum class ChangeType {
        ADDED,
        REMOVED,
        MODIFIED
    }
}

data class ScheduleDiffResult(
    val hasChanges: Boolean,
    val changes: List<ScheduleChange>
)

object ScheduleDiffHelper {

    /**
     * Compares two week schedules (typically cached vs freshly fetched from network).
     * Returns a detailed list of human-readable differences.
     */
    fun findDifferences(oldSchedule: WeekSchedule?, newSchedule: WeekSchedule?): ScheduleDiffResult {
        if (oldSchedule == null || newSchedule == null) {
            return ScheduleDiffResult(hasChanges = false, changes = emptyList())
        }

        // Only compare if they are for the same week
        if (oldSchedule.week != newSchedule.week) {
            return ScheduleDiffResult(hasChanges = false, changes = emptyList())
        }

        val changes = mutableListOf<ScheduleChange>()

        val oldDaysMap = oldSchedule.days.associateBy { it.dayNum }
        val newDaysMap = newSchedule.days.associateBy { it.dayNum }

        val allDayNums = (oldDaysMap.keys + newDaysMap.keys).sorted()

        for (dayNum in allDayNums) {
            val oldDay = oldDaysMap[dayNum]
            val newDay = newDaysMap[dayNum]

            if (oldDay == null && newDay != null && newDay.lessons.isNotEmpty()) {
                changes.add(
                    ScheduleChange(
                        dayName = newDay.dayName,
                        date = newDay.date,
                        description = "Появились новые занятия (${newDay.lessons.size} пар)",
                        type = ScheduleChange.ChangeType.ADDED
                    )
                )
                continue
            }

            if (oldDay != null && oldDay.lessons.isNotEmpty() && (newDay == null || newDay.lessons.isEmpty())) {
                changes.add(
                    ScheduleChange(
                        dayName = oldDay.dayName,
                        date = oldDay.date,
                        description = "Все занятия отменены",
                        type = ScheduleChange.ChangeType.REMOVED
                    )
                )
                continue
            }

            if (oldDay != null && newDay != null) {
                compareDayLessons(oldDay, newDay, changes)
            }
        }

        return ScheduleDiffResult(
            hasChanges = changes.isNotEmpty(),
            changes = changes
        )
    }

    private fun compareDayLessons(
        oldDay: DaySchedule,
        newDay: DaySchedule,
        changes: MutableList<ScheduleChange>
    ) {
        val oldLessonsMap = oldDay.lessons.associateBy { it.order }
        val newLessonsMap = newDay.lessons.associateBy { it.order }

        val allOrders = (oldLessonsMap.keys + newLessonsMap.keys).sorted()

        for (order in allOrders) {
            val oldLesson = oldLessonsMap[order]
            val newLesson = newLessonsMap[order]

            when {
                oldLesson == null && newLesson != null -> {
                    changes.add(
                        ScheduleChange(
                            dayName = newDay.dayName,
                            date = newDay.date,
                            description = "Добавлена $order пара: ${newLesson.subject} (${newLesson.room})",
                            type = ScheduleChange.ChangeType.ADDED
                        )
                    )
                }
                oldLesson != null && newLesson == null -> {
                    changes.add(
                        ScheduleChange(
                            dayName = oldDay.dayName,
                            date = oldDay.date,
                            description = "Отменена $order пара: ${oldLesson.subject}",
                            type = ScheduleChange.ChangeType.REMOVED
                        )
                    )
                }
                oldLesson != null && newLesson != null && oldLesson != newLesson -> {
                    val diffs = mutableListOf<String>()
                    if (oldLesson.subject != newLesson.subject) {
                        diffs.add("предмет с «${oldLesson.subject}» на «${newLesson.subject}»")
                    }
                    if (oldLesson.room != newLesson.room) {
                        diffs.add("ауд. с ${oldLesson.room} на ${newLesson.room}")
                    }
                    if (oldLesson.teacher != newLesson.teacher) {
                        diffs.add("преподаватель с «${oldLesson.teacher}» на «${newLesson.teacher}»")
                    }
                    if (oldLesson.type != newLesson.type) {
                        diffs.add("тип занятия (${oldLesson.type.displayName} -> ${newLesson.type.displayName})")
                    }

                    val diffText = if (diffs.isNotEmpty()) diffs.joinToString(", ") else "изменены детали"
                    changes.add(
                        ScheduleChange(
                            dayName = newDay.dayName,
                            date = newDay.date,
                            description = "В $order паре заменено: $diffText",
                            type = ScheduleChange.ChangeType.MODIFIED
                        )
                    )
                }
            }
        }
    }
}
