package com.example.iseuschedule.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
    val course: String = DEFAULT_COURSE,
    val group: String = DEFAULT_GROUP,
    val subgroup: Int = DEFAULT_SUBGROUP, // 0 = Все, 1 = 1-я подгруппа, 2 = 2-я подгруппа
    val themeMode: String = DEFAULT_THEME_MODE, // SYSTEM, LIGHT, DARK
    val showTomorrowPreview: Boolean = DEFAULT_SHOW_TOMORROW_PREVIEW
) {
    companion object {
        const val DEFAULT_COURSE = "4"
        const val DEFAULT_GROUP = "31ПИ1"
        const val DEFAULT_SUBGROUP = 0
        const val DEFAULT_THEME_MODE = "SYSTEM"
        const val DEFAULT_SHOW_TOMORROW_PREVIEW = true
    }
}

class UserSettingsRepository(
    private val context: Context? = null,
    private val prefs: SharedPreferences? = null
) {
    companion object {
        private const val PREFS_NAME = "iseu_user_settings"
        private const val KEY_COURSE = "key_course"
        private const val KEY_GROUP = "key_group"
        private const val KEY_SUBGROUP = "key_subgroup"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_SHOW_TOMORROW_PREVIEW = "key_show_tomorrow_preview"
    }

    private val sharedPreferences: SharedPreferences? =
        prefs ?: context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // In-memory backing for unit tests or when context is null
    private var inMemorySettings = UserSettings()

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<UserSettings> = _settingsFlow.asStateFlow()

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settingsFlow.value = loadSettings()
    }

    init {
        sharedPreferences?.registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    fun getSettings(): UserSettings = _settingsFlow.value

    private fun loadSettings(): UserSettings {
        val sp = sharedPreferences ?: return inMemorySettings
        return UserSettings(
            course = sp.getString(KEY_COURSE, UserSettings.DEFAULT_COURSE) ?: UserSettings.DEFAULT_COURSE,
            group = sp.getString(KEY_GROUP, UserSettings.DEFAULT_GROUP) ?: UserSettings.DEFAULT_GROUP,
            subgroup = sp.getInt(KEY_SUBGROUP, UserSettings.DEFAULT_SUBGROUP),
            themeMode = sp.getString(KEY_THEME_MODE, UserSettings.DEFAULT_THEME_MODE) ?: UserSettings.DEFAULT_THEME_MODE,
            showTomorrowPreview = sp.getBoolean(KEY_SHOW_TOMORROW_PREVIEW, UserSettings.DEFAULT_SHOW_TOMORROW_PREVIEW)
        )
    }

    fun updateCourse(newCourse: String) {
        val current = _settingsFlow.value
        if (current.course != newCourse) {
            persist(current.copy(course = newCourse))
        }
    }

    fun updateGroup(newGroup: String) {
        val current = _settingsFlow.value
        if (current.group != newGroup) {
            persist(current.copy(group = newGroup))
        }
    }

    fun updateSubgroup(newSubgroup: Int) {
        val current = _settingsFlow.value
        if (current.subgroup != newSubgroup) {
            persist(current.copy(subgroup = newSubgroup))
        }
    }

    fun updateThemeMode(newThemeMode: String) {
        val current = _settingsFlow.value
        if (current.themeMode != newThemeMode) {
            persist(current.copy(themeMode = newThemeMode))
        }
    }

    fun updateShowTomorrowPreview(enabled: Boolean) {
        val current = _settingsFlow.value
        if (current.showTomorrowPreview != enabled) {
            persist(current.copy(showTomorrowPreview = enabled))
        }
    }

    fun updateSettings(
        course: String,
        group: String,
        subgroup: Int = 0,
        themeMode: String = _settingsFlow.value.themeMode,
        showTomorrowPreview: Boolean = _settingsFlow.value.showTomorrowPreview
    ) {
        persist(
            UserSettings(
                course = course,
                group = group,
                subgroup = subgroup,
                themeMode = themeMode,
                showTomorrowPreview = showTomorrowPreview
            )
        )
    }

    private fun persist(settings: UserSettings) {
        inMemorySettings = settings
        _settingsFlow.value = settings
        sharedPreferences?.edit()?.apply {
            putString(KEY_COURSE, settings.course)
            putString(KEY_GROUP, settings.group)
            putInt(KEY_SUBGROUP, settings.subgroup)
            putString(KEY_THEME_MODE, settings.themeMode)
            putBoolean(KEY_SHOW_TOMORROW_PREVIEW, settings.showTomorrowPreview)
            apply()
        }
        context?.let { ctx ->
            try {
                com.example.iseuschedule.widget.ScheduleWidgetProvider.updateAllWidgets(ctx)
            } catch (_: Exception) {}
        }
    }
}
