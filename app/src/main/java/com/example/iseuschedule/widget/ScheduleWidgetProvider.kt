package com.example.iseuschedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.iseuschedule.MainActivity
import com.example.iseuschedule.R
import com.example.iseuschedule.data.ScheduleRepository
import com.example.iseuschedule.data.model.Lesson
import com.example.iseuschedule.data.model.LessonTimeHelper
import com.example.iseuschedule.data.settings.UserSettingsRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val componentName = ComponentName(context, ScheduleWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName) ?: return
            for (id in appWidgetIds) {
                updateWidget(context, appWidgetManager, id)
            }
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val componentName = ComponentName(context, ScheduleWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName) ?: return
                for (id in appWidgetIds) {
                    updateWidget(context, appWidgetManager, id)
                }
            } catch (_: Exception) {
                // Ignore widget update failures during background lifecycle
            }
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule)

            // Click on widget opens MainActivity
            val clickIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            // Load user settings
            val userSettingsRepo = UserSettingsRepository(context)
            val settings = userSettingsRepo.getSettings()
            val targetGroup = settings.group.ifBlank { ScheduleRepository.DEFAULT_GROUP }
            val subgroup = settings.subgroup

            views.setTextViewText(R.id.widget_group, targetGroup)

            // Date format
            val today = LocalDate.now()
            val dayOfWeek = today.dayOfWeek
            val dayFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))
            val dayName = when (dayOfWeek) {
                DayOfWeek.MONDAY -> "Понедельник"
                DayOfWeek.TUESDAY -> "Вторник"
                DayOfWeek.WEDNESDAY -> "Среда"
                DayOfWeek.THURSDAY -> "Четверг"
                DayOfWeek.FRIDAY -> "Пятница"
                DayOfWeek.SATURDAY -> "Суббота"
                DayOfWeek.SUNDAY -> "Воскресенье"
            }
            views.setTextViewText(R.id.widget_date, "$dayName, ${today.format(dayFormatter)}")

            // Load cached schedule
            val scheduleRepo = ScheduleRepository(context = context)
            val weekSchedule = scheduleRepo.loadFromDiskCache(0, targetGroup)

            val todaySchedule = weekSchedule?.days?.firstOrNull { day ->
                LessonTimeHelper.isToday(day.date) || day.dayNum == dayOfWeek.value
            }

            val rawLessons = todaySchedule?.lessons ?: emptyList()
            val lessons: List<Lesson> = rawLessons.filter { lesson ->
                when (subgroup) {
                    1 -> lesson.subgroup == null || lesson.subgroup == 1
                    2 -> lesson.subgroup == null || lesson.subgroup == 2
                    else -> true
                }
            }

            if (lessons.isEmpty()) {
                views.setViewVisibility(R.id.widget_lessons_container, View.GONE)
                views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
                views.setTextViewText(R.id.widget_status_badge, "Пар нет")
            } else {
                views.setViewVisibility(R.id.widget_empty_container, View.GONE)
                views.setViewVisibility(R.id.widget_lessons_container, View.VISIBLE)

                var hasActive = false
                val rowIds = intArrayOf(
                    R.id.widget_lesson_row_1,
                    R.id.widget_lesson_row_2,
                    R.id.widget_lesson_row_3,
                    R.id.widget_lesson_row_4,
                    R.id.widget_lesson_row_5
                )
                val timeIds = intArrayOf(
                    R.id.widget_lesson_time_1,
                    R.id.widget_lesson_time_2,
                    R.id.widget_lesson_time_3,
                    R.id.widget_lesson_time_4,
                    R.id.widget_lesson_time_5
                )
                val titleIds = intArrayOf(
                    R.id.widget_lesson_title_1,
                    R.id.widget_lesson_title_2,
                    R.id.widget_lesson_title_3,
                    R.id.widget_lesson_title_4,
                    R.id.widget_lesson_title_5
                )
                val roomIds = intArrayOf(
                    R.id.widget_lesson_room_1,
                    R.id.widget_lesson_room_2,
                    R.id.widget_lesson_room_3,
                    R.id.widget_lesson_room_4,
                    R.id.widget_lesson_room_5
                )

                for (i in 0 until 5) {
                    if (i < lessons.size) {
                        val lesson = lessons[i]
                        val isActive = LessonTimeHelper.isLessonActive(lesson, isToday = true)
                        if (isActive) hasActive = true

                        views.setViewVisibility(rowIds[i], View.VISIBLE)
                        val timeText = lesson.time.ifBlank {
                            when (lesson.order) {
                                1 -> "08:30"
                                2 -> "10:15"
                                3 -> "12:15"
                                4 -> "14:00"
                                5 -> "15:45"
                                6 -> "17:30"
                                7 -> "19:10"
                                else -> "#${lesson.order}"
                            }
                        }
                        val titlePrefix = if (isActive) "● " else ""
                        val titleText = "$titlePrefix${lesson.subject}"
                        val roomText = buildString {
                            if (lesson.room.isNotBlank()) append(lesson.room)
                            if (lesson.subgroup != null) {
                                if (isNotEmpty()) append(", ")
                                append("${lesson.subgroup} п/г")
                            }
                        }

                        views.setTextViewText(timeIds[i], timeText)
                        views.setTextViewText(titleIds[i], titleText)
                        views.setTextViewText(roomIds[i], roomText)

                        if (isActive) {
                            views.setInt(rowIds[i], "setBackgroundResource", R.drawable.widget_item_active_bg)
                        } else {
                            views.setInt(rowIds[i], "setBackgroundResource", R.drawable.widget_item_bg)
                        }
                    } else {
                        views.setViewVisibility(rowIds[i], View.GONE)
                    }
                }

                if (lessons.size > 5) {
                    views.setViewVisibility(R.id.widget_more_lessons, View.VISIBLE)
                    views.setTextViewText(R.id.widget_more_lessons, "+ ещё ${lessons.size - 5} пар(ы)")
                } else {
                    views.setViewVisibility(R.id.widget_more_lessons, View.GONE)
                }

                if (hasActive) {
                    views.setTextViewText(R.id.widget_status_badge, "● Идёт пара")
                } else {
                    views.setTextViewText(R.id.widget_status_badge, "${lessons.size} пар(ы)")
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
