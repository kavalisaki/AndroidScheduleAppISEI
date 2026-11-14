package com.example.iseuschedule.data.settings

import com.example.iseuschedule.data.ScheduleRepository
import com.example.iseuschedule.data.parser.ScheduleParser
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserSettingsTest {

    private lateinit var userSettingsRepository: UserSettingsRepository

    @Before
    fun setUp() {
        // Runs in-memory without needing Android Context
        userSettingsRepository = UserSettingsRepository()
    }

    @Test
    fun defaultSettings_hasSensibleDefaults() {
        val settings = userSettingsRepository.getSettings()
        assertEquals("4", settings.course)
        assertEquals("31ПИ1", settings.group)
        assertEquals(0, settings.subgroup)
        assertEquals("SYSTEM", settings.themeMode)
        assertEquals(true, settings.showTomorrowPreview)
    }

    @Test
    fun updateShowTomorrowPreview_updatesStateAndFlow() {
        userSettingsRepository.updateShowTomorrowPreview(false)

        assertEquals(false, userSettingsRepository.getSettings().showTomorrowPreview)
        assertEquals(false, userSettingsRepository.settingsFlow.value.showTomorrowPreview)
    }

    @Test
    fun updateThemeMode_updatesStateAndFlow() {
        userSettingsRepository.updateThemeMode("DARK")

        assertEquals("DARK", userSettingsRepository.getSettings().themeMode)
        assertEquals("DARK", userSettingsRepository.settingsFlow.value.themeMode)
    }

    @Test
    fun updateCourse_updatesStateAndFlow() {
        userSettingsRepository.updateCourse("3")

        assertEquals("3", userSettingsRepository.getSettings().course)
        assertEquals("3", userSettingsRepository.settingsFlow.value.course)
        assertEquals("31ПИ1", userSettingsRepository.getSettings().group) // group intact
    }

    @Test
    fun updateCourse_toCourse5_persistsAndUpdatesFlow() {
        userSettingsRepository.updateCourse("5")

        assertEquals("5", userSettingsRepository.getSettings().course)
        assertEquals("5", userSettingsRepository.settingsFlow.value.course)
        assertEquals("31ПИ1", userSettingsRepository.getSettings().group) // group intact
    }

    @Test
    fun updateGroup_updatesStateAndFlow() {
        userSettingsRepository.updateGroup("21ИС1")

        assertEquals("21ИС1", userSettingsRepository.getSettings().group)
        assertEquals("21ИС1", userSettingsRepository.settingsFlow.value.group)
        assertEquals("4", userSettingsRepository.getSettings().course) // course intact
    }

    @Test
    fun updateSubgroup_updatesStateAndFlow() {
        userSettingsRepository.updateSubgroup(2)

        assertEquals(2, userSettingsRepository.getSettings().subgroup)
        assertEquals(2, userSettingsRepository.settingsFlow.value.subgroup)
    }

    @Test
    fun updateSettings_updatesAllFieldsSimultaneously() {
        userSettingsRepository.updateSettings(course = "2", group = "12ПИ2", subgroup = 1)

        val settings = userSettingsRepository.getSettings()
        assertEquals("2", settings.course)
        assertEquals("12ПИ2", settings.group)
        assertEquals(1, settings.subgroup)
    }

    @Test
    fun scheduleParser_extractGroups_parsesSelectOptionsCorrectly() {
        val mockHtml = """
            <html><body>
            <select id="ddlGroup">
                <option value="">-- Выберите группу --</option>
                <option value="1">31ПИ1</option>
                <option value="2">32ПИ2</option>
                <option value="3">33ИСТ1</option>
            </select>
            </body></html>
        """.trimIndent()

        val doc = Jsoup.parse(mockHtml)
        val groups = ScheduleParser.extractGroups(doc)

        assertEquals(3, groups.size)
        assertTrue(groups.contains("31ПИ1"))
        assertTrue(groups.contains("32ПИ2"))
        assertTrue(groups.contains("33ИСТ1"))
    }

    @Test
    fun scheduleRepository_dynamicTargetReflectsUserSettings() {
        val customSettings = UserSettingsRepository()
        customSettings.updateSettings(course = "3", group = "22МЕД1")

        val repo = ScheduleRepository(userSettingsRepository = customSettings)

        // Without override, should take user settings
        assertEquals("3", repo.getTargetCourse())
        assertEquals("22МЕД1", repo.getTargetGroup())

        // With explicit override, should respect override
        assertEquals("1", repo.getTargetCourse("1"))
        assertEquals("11ЭКО1", repo.getTargetGroup("11ЭКО1"))
    }
}
