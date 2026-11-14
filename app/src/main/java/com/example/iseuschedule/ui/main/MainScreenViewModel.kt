package com.example.iseuschedule.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.iseuschedule.data.ScheduleRepository
import com.example.iseuschedule.data.model.DefaultTimeProvider
import com.example.iseuschedule.data.model.LessonTimeHelper
import com.example.iseuschedule.data.model.ScheduleDiffResult
import com.example.iseuschedule.data.model.TimeProvider
import com.example.iseuschedule.data.model.WeekSchedule
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScheduleUiState {
    object Loading : ScheduleUiState
    data class Success(val schedule: WeekSchedule) : ScheduleUiState
    data class Error(val message: String) : ScheduleUiState
}

class MainScreenViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ScheduleRepository,
    private val timeProvider: TimeProvider = DefaultTimeProvider
) : AndroidViewModel(application) {

    // Constructor required by AndroidViewModelFactory
    constructor(application: Application) : this(application, ScheduleRepository(application), DefaultTimeProvider)

    // Default secondary constructor for preview / testing without DI
    constructor() : this(Application(), ScheduleRepository(null), DefaultTimeProvider)

    val userSettingsRepository = repository.userSettingsRepository
    val userSettings = userSettingsRepository.settingsFlow

    private val _selectedWeek = MutableStateFlow(0)
    val selectedWeek: StateFlow<Int> = _selectedWeek.asStateFlow()

    private val _uiState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val scheduleChanges: StateFlow<ScheduleDiffResult?> = repository.recentScheduleChanges

    fun dismissScheduleChanges() {
        repository.dismissScheduleChanges()
    }

    private var fetchJob: Job? = null

    init {
        val initialWeek = if (LessonTimeHelper.shouldDefaultToNextWeek(timeProvider.currentDate())) 1 else 0
        _selectedWeek.value = initialWeek
        loadSchedule(initialWeek)
    }

    fun selectWeek(week: Int) {
        if (_selectedWeek.value == week && _uiState.value !is ScheduleUiState.Error) return
        _selectedWeek.value = week
        loadSchedule(week)
    }

    fun refresh(forceRefresh: Boolean = true) {
        loadSchedule(_selectedWeek.value, forceRefresh = forceRefresh, isPullRefresh = true)
    }

    fun setSubgroup(subgroup: Int) {
        userSettingsRepository.updateSubgroup(subgroup)
    }

    fun setFaculty(faculty: String) {
        userSettingsRepository.updateFaculty(faculty)
    }

    fun setCourseAndGroup(course: String, group: String, faculty: String = userSettings.value.faculty) {
        userSettingsRepository.updateSettings(
            course = course,
            group = group,
            subgroup = userSettings.value.subgroup,
            themeMode = userSettings.value.themeMode,
            showTomorrowPreview = userSettings.value.showTomorrowPreview,
            faculty = faculty
        )
        loadSchedule(_selectedWeek.value, forceRefresh = true)
    }

    fun setThemeMode(mode: String) {
        userSettingsRepository.updateThemeMode(mode)
    }

    fun setShowTomorrowPreview(enabled: Boolean) {
        userSettingsRepository.updateShowTomorrowPreview(enabled)
    }

    suspend fun fetchGroups(course: String, faculty: String = userSettings.value.faculty): List<String> {
        return repository.fetchAvailableGroups(course, faculty = faculty).getOrDefault(emptyList())
    }

    private fun mapErrorMessage(error: Throwable): String {
        val raw = error.message ?: ""
        return when {
            raw.contains("500") || raw.contains("HttpException", ignoreCase = true) ->
                "Сервер расписания временно перегружен или недоступен (ошибка 500). Попробуйте позже."
            raw.contains("Timeout", ignoreCase = true) ||
            raw.contains("UnknownHost", ignoreCase = true) ||
            raw.contains("ConnectException", ignoreCase = true) ->
                "Не удалось подключиться к серверу МГЭИ. Проверьте интернет-соединение."
            raw.contains("Rate limit", ignoreCase = true) ->
                "Слишком частые запросы к серверу. Пожалуйста, подождите 3 секунды."
            raw.isNotBlank() && !raw.contains("Exception") && !raw.contains("at ") ->
                raw
            else ->
                "Не удалось загрузить расписание. Проверьте подключение к сети."
        }
    }

    private fun loadSchedule(weekOffset: Int, forceRefresh: Boolean = false, isPullRefresh: Boolean = false) {
        fetchJob?.cancel()

        if (isPullRefresh) {
            _isRefreshing.value = true
        }

        // Immediate cache hit for fast UI (Stale-While-Revalidate)
        var cached = repository.getCachedSchedule(weekOffset)
        val isSunday = LessonTimeHelper.shouldDefaultToNextWeek(timeProvider.currentDate())

        // Sunday fallback: if week 1 requested but not cached yet, fallback to week 0 cache
        if (cached == null && weekOffset == 1 && isSunday) {
            cached = repository.getCachedSchedule(0)
        }

        if (cached != null) {
            _uiState.value = ScheduleUiState.Success(cached)
        } else {
            _uiState.value = ScheduleUiState.Loading
        }

        fetchJob = viewModelScope.launch {
            try {
                repository.getScheduleFlow(weekOffset, forceRefresh = forceRefresh).collect { result ->
                    _isRefreshing.value = false
                    result.onSuccess { schedule ->
                        _uiState.value = ScheduleUiState.Success(schedule)
                    }.onFailure { error ->
                        // Sunday fallback: if week 1 network request failed and we don't have success state yet,
                        // check if week 0 cache exists before displaying error screen
                        if (weekOffset == 1 && isSunday) {
                            val fallback = repository.getCachedSchedule(0)
                            if (fallback != null) {
                                _uiState.value = ScheduleUiState.Success(fallback)
                                return@collect
                            }
                        }
                        if (_uiState.value !is ScheduleUiState.Success) {
                            _uiState.value = ScheduleUiState.Error(mapErrorMessage(error))
                        }
                    }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
