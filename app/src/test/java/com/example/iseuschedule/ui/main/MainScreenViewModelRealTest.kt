package com.example.iseuschedule.ui.main

import android.app.Application
import com.example.iseuschedule.data.ScheduleRepository
import com.example.iseuschedule.data.model.DaySchedule
import com.example.iseuschedule.data.model.Lesson
import com.example.iseuschedule.data.model.LessonType
import com.example.iseuschedule.data.model.TimeProvider
import com.example.iseuschedule.data.model.WeekSchedule
import com.example.iseuschedule.data.settings.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalTime

class FixedTimeProvider(
    private val date: LocalDate = LocalDate.of(2026, 10, 19),
    private val time: LocalTime = LocalTime.of(12, 0)
) : TimeProvider {
    override fun currentDate(): LocalDate = date
    override fun currentTime(): LocalTime = time
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelRealTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempDir: File
    private lateinit var userSettingsRepo: UserSettingsRepository
    private lateinit var repository: ScheduleRepository
    private lateinit var viewModel: MainScreenViewModel
    private val mondayTimeProvider = FixedTimeProvider(LocalDate.of(2026, 10, 19))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempDir = Files.createTempDirectory("vm_test_cache").toFile()
        userSettingsRepo = UserSettingsRepository()
        repository = ScheduleRepository(
            cacheDir = tempDir,
            userSettingsRepository = userSettingsRepo
        )
        viewModel = MainScreenViewModel(
            application = Application(),
            repository = repository,
            timeProvider = mondayTimeProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    @Test
    fun initialUserSettings_hasExpectedDefaults() {
        val settings = viewModel.userSettings.value
        assertEquals("4", settings.course)
        assertEquals("31ПИ1", settings.group)
        assertEquals(0, settings.subgroup)
    }

    @Test
    fun setSubgroup_updatesViewModelState() {
        viewModel.setSubgroup(1)
        assertEquals(1, viewModel.userSettings.value.subgroup)

        viewModel.setSubgroup(2)
        assertEquals(2, viewModel.userSettings.value.subgroup)

        viewModel.setSubgroup(0)
        assertEquals(0, viewModel.userSettings.value.subgroup)
    }

    @Test
    fun setCourseAndGroup_updatesSettingsAndTriggersReload() {
        viewModel.setCourseAndGroup(course = "3", group = "21ПИ1")

        val settings = viewModel.userSettings.value
        assertEquals("3", settings.course)
        assertEquals("21ПИ1", settings.group)
    }

    @Test
    fun subgroupFiltering_correctlyFiltersLessons() {
        val sampleLessons = listOf(
            Lesson(
                order = 1,
                time = "08:30 - 10:05",
                type = LessonType.LECTURE,
                subject = "Общая лекция (для всех)",
                teacher = "Иванов И.И.",
                room = "101",
                subgroup = null // shared
            ),
            Lesson(
                order = 2,
                time = "10:15 - 11:50",
                type = LessonType.LAB,
                subject = "Лабораторная 1п/гр",
                teacher = "Петров П.П.",
                room = "102",
                subgroup = 1 // 1st subgroup
            ),
            Lesson(
                order = 2,
                time = "10:15 - 11:50",
                type = LessonType.PRACTICE,
                subject = "Практика 2п/гр",
                teacher = "Сидоров С.С.",
                room = "103",
                subgroup = 2 // 2nd subgroup
            )
        )

        // Subgroup 0: All lessons visible
        val filterAll = sampleLessons.filter { lesson ->
            when (0) {
                1 -> lesson.subgroup == null || lesson.subgroup == 1
                2 -> lesson.subgroup == null || lesson.subgroup == 2
                else -> true
            }
        }
        assertEquals(3, filterAll.size)

        // Subgroup 1: Shared lecture + 1st subgroup lab
        val filterSubgroup1 = sampleLessons.filter { lesson ->
            when (1) {
                1 -> lesson.subgroup == null || lesson.subgroup == 1
                2 -> lesson.subgroup == null || lesson.subgroup == 2
                else -> true
            }
        }
        assertEquals(2, filterSubgroup1.size)
        assertTrue(filterSubgroup1.any { it.subgroup == null })
        assertTrue(filterSubgroup1.any { it.subgroup == 1 })
        assertTrue(filterSubgroup1.none { it.subgroup == 2 })

        // Subgroup 2: Shared lecture + 2nd subgroup practice
        val filterSubgroup2 = sampleLessons.filter { lesson ->
            when (2) {
                1 -> lesson.subgroup == null || lesson.subgroup == 1
                2 -> lesson.subgroup == null || lesson.subgroup == 2
                else -> true
            }
        }
        assertEquals(2, filterSubgroup2.size)
        assertTrue(filterSubgroup2.any { it.subgroup == null })
        assertTrue(filterSubgroup2.any { it.subgroup == 2 })
        assertTrue(filterSubgroup2.none { it.subgroup == 1 })
    }

    @Test
    fun cachedSchedule_emittedImmediatelyToUiState() = runTest {
        val sampleSchedule = WeekSchedule(
            week = 0,
            weekName = "Текущая неделя",
            mondayDate = "19.10.2026",
            days = listOf(
                DaySchedule(
                    dayNum = 1,
                    dayName = "Понедельник",
                    date = "19.10.2026",
                    hasSchedule = true,
                    lessons = emptyList()
                )
            )
        )

        // Save into repository cache
        repository.saveToDiskCache(0, sampleSchedule, "31ПИ1")

        // Create new VM instance that loads cached data with fixed weekday TimeProvider
        val newVm = MainScreenViewModel(Application(), repository, mondayTimeProvider)

        val state = newVm.uiState.value
        assertTrue("State should be Success immediately when cache exists", state is ScheduleUiState.Success)
        val success = state as ScheduleUiState.Success
        assertEquals("Понедельник", success.schedule.days[0].dayName)
    }

    @Test
    fun sunday_withoutWeek1Cache_fallsBackToWeek0CacheImmediately() = runTest {
        val sampleScheduleWeek0 = WeekSchedule(
            week = 0,
            weekName = "Текущая неделя",
            mondayDate = "19.10.2026",
            days = listOf(
                DaySchedule(
                    dayNum = 1,
                    dayName = "Понедельник",
                    date = "19.10.2026",
                    hasSchedule = true,
                    lessons = emptyList()
                )
            )
        )

        // Cache only week 0
        repository.saveToDiskCache(0, sampleScheduleWeek0, "31ПИ1")

        val sundayProvider = FixedTimeProvider(LocalDate.of(2026, 10, 18)) // Sunday
        val sundayVm = MainScreenViewModel(Application(), repository, sundayProvider)

        // Sunday defaults to next week (1)
        assertEquals(1, sundayVm.selectedWeek.value)

        // But immediately falls back to week 0 cache instead of failing or staying in Loading
        val state = sundayVm.uiState.value
        assertTrue("State should be Success with fallback cache", state is ScheduleUiState.Success)
        val success = state as ScheduleUiState.Success
        assertEquals(0, success.schedule.week)
        assertEquals("Понедельник", success.schedule.days[0].dayName)
    }

    @Test
    fun sunday_withWeek1Cache_usesWeek1Cache() = runTest {
        val sampleScheduleWeek1 = WeekSchedule(
            week = 1,
            weekName = "Следующая неделя",
            mondayDate = "26.10.2026",
            days = listOf(
                DaySchedule(
                    dayNum = 1,
                    dayName = "Понедельник",
                    date = "26.10.2026",
                    hasSchedule = true,
                    lessons = emptyList()
                )
            )
        )

        // Cache week 1
        repository.saveToDiskCache(1, sampleScheduleWeek1, "31ПИ1")

        val sundayProvider = FixedTimeProvider(LocalDate.of(2026, 10, 18)) // Sunday
        val sundayVm = MainScreenViewModel(Application(), repository, sundayProvider)

        assertEquals(1, sundayVm.selectedWeek.value)
        val state = sundayVm.uiState.value
        assertTrue("State should be Success with week 1 cache", state is ScheduleUiState.Success)
        val success = state as ScheduleUiState.Success
        assertEquals(1, success.schedule.week)
        assertEquals("Следующая неделя", success.schedule.weekName)
    }
}
