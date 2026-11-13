package com.example.iseuschedule.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.iseuschedule.data.model.DaySchedule
import com.example.iseuschedule.data.model.Lesson
import com.example.iseuschedule.data.model.LessonType
import com.example.iseuschedule.data.model.WeekSchedule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.iseuschedule.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent {
      ScheduleList(
        schedule = WeekSchedule(
          week = 0,
          weekName = "Текущая неделя",
          mondayDate = "19.10.2026",
          days = listOf(
            DaySchedule(
              dayNum = 1,
              dayName = "Понедельник",
              date = "19.10.2026",
              hasSchedule = true,
              lessons = listOf(
                Lesson(
                  order = 1,
                  time = "08:30 - 10:05",
                  type = LessonType.LECTURE,
                  subject = "Проектирование информационных систем",
                  teacher = "Иванов И.И.",
                  room = "101 к.1"
                )
              )
            )
          )
        )
      )
    }
  }

  @Test
  fun lessonSubject_isDisplayed() {
    composeTestRule.onNodeWithText("Понедельник").assertExists()
    composeTestRule.onNodeWithText("Проектирование информационных систем").assertExists()
  }
}
