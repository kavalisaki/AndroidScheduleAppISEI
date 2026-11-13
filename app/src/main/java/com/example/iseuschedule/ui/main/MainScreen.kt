package com.example.iseuschedule.ui.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.iseuschedule.R
import com.example.iseuschedule.data.model.LessonTimeHelper
import com.example.iseuschedule.data.model.LiveLessonStatus
import com.example.iseuschedule.data.model.ScheduleDiffResult
import com.example.iseuschedule.ui.components.LiveLessonTimerCard
import com.example.iseuschedule.ui.components.ScheduleChangesBanner
import com.example.iseuschedule.ui.components.ScheduleChangesDialog
import com.example.iseuschedule.ui.components.WeekendEmptyState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.delay
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iseuschedule.data.model.DaySchedule
import com.example.iseuschedule.data.model.Lesson
import com.example.iseuschedule.data.model.LessonType
import com.example.iseuschedule.data.model.WeekSchedule
import com.example.iseuschedule.theme.ISEUScheduleTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedWeek by viewModel.selectedWeek.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val scheduleChanges by viewModel.scheduleChanges.collectAsStateWithLifecycle()
    var showChangesDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDataSourceDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Расписание",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Группа ${userSettings.group} • ${userSettings.course} курс",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDataSourceDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "О приложении / Источник данных"
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки группы"
                        )
                    }
                    IconButton(onClick = { viewModel.refresh(forceRefresh = true) }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Обновить"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (scheduleChanges?.hasChanges == true) {
                ScheduleChangesBanner(
                    diff = scheduleChanges!!,
                    onClick = { showChangesDialog = true },
                    onDismiss = { viewModel.dismissScheduleChanges() }
                )
            }

            // Week switcher
            WeekSelectorBar(
                selectedWeek = selectedWeek,
                onWeekSelected = { viewModel.selectWeek(it) },
                onOpenDatePicker = { showDatePickerDialog = true }
            )

            // Subgroup filter
            SubgroupFilterBar(
                selectedSubgroup = userSettings.subgroup,
                onSubgroupSelected = { viewModel.setSubgroup(it) }
            )

            // Content based on UiState
            when (val s = state) {
                is ScheduleUiState.Loading -> {
                    LoadingView()
                }
                is ScheduleUiState.Error -> {
                    ErrorView(
                        message = s.message,
                        onRetry = { viewModel.refresh(forceRefresh = true) }
                    )
                }
                is ScheduleUiState.Success -> {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refresh(forceRefresh = true) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        ScheduleList(
                            schedule = s.schedule,
                            selectedSubgroup = userSettings.subgroup
                        )
                    }
                }
            }
        }
    }

    if (showChangesDialog && scheduleChanges != null) {
        ScheduleChangesDialog(
            diff = scheduleChanges!!,
            onDismiss = {
                showChangesDialog = false
                viewModel.dismissScheduleChanges()
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentCourse = userSettings.course,
            currentGroup = userSettings.group,
            currentThemeMode = userSettings.themeMode,
            onDismiss = { showSettingsDialog = false },
            onSave = { course, group ->
                viewModel.setCourseAndGroup(course, group)
                showSettingsDialog = false
            },
            onThemeModeChange = { mode ->
                viewModel.setThemeMode(mode)
            },
            onFetchGroups = { course ->
                viewModel.fetchGroups(course)
            }
        )
    }

    if (showDataSourceDialog) {
        DataSourceDialog(
            onDismiss = { showDataSourceDialog = false }
        )
    }

    if (showDatePickerDialog) {
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePickerDialog = false
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            val now = LocalDate.now()
                            val todayMonday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            val targetMonday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            val offset = ChronoUnit.WEEKS.between(todayMonday, targetMonday).toInt()
                            viewModel.selectWeek(offset)
                        }
                    }
                ) {
                    Text("Выбрать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Отмена")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun WeekSelectorBar(
    selectedWeek: Int,
    onWeekSelected: (Int) -> Unit,
    onOpenDatePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedWeek == 0,
                onClick = { onWeekSelected(0) },
                label = { Text("Текущая неделя") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            )
        }
        item {
            FilterChip(
                selected = selectedWeek == 1,
                onClick = { onWeekSelected(1) },
                label = { Text("Следующая неделя") }
            )
        }
        item {
            val isCustomWeek = selectedWeek != 0 && selectedWeek != 1
            val customLabel = if (isCustomWeek) {
                if (selectedWeek > 0) "+$selectedWeek нед." else "$selectedWeek нед."
            } else {
                "Выбрать дату"
            }
            FilterChip(
                selected = isCustomWeek,
                onClick = onOpenDatePicker,
                label = { Text(customLabel) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            )
        }
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Загрузка расписания...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Прямой запрос к rsp.iseu.by",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Не удалось загрузить данные",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Text("Повторить попытку")
                }
            }
        }
    }
}

@Composable
fun ScheduleList(
    schedule: WeekSchedule,
    selectedSubgroup: Int = 0,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val todayDayNum = remember { LessonTimeHelper.getTodayDayOfWeekNumber() }

    LaunchedEffect(schedule) {
        if (schedule.days.isNotEmpty()) {
            val todayIndex = schedule.days.indexOfFirst { it.dayNum == todayDayNum }
            if (todayIndex >= 0) {
                listState.animateScrollToItem(todayIndex)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val todayDay = schedule.days.find { LessonTimeHelper.isToday(it.date) }
            ?: schedule.days.find { it.dayNum == todayDayNum }

        if (todayDay != null && todayDay.lessons.isNotEmpty()) {
            item(key = "live_timer_card") {
                LiveLessonTimerCard(
                    lessons = todayDay.lessons,
                    selectedSubgroup = selectedSubgroup
                )
            }
        }

        if (schedule.days.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Расписание отсутствует",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "На выбранную неделю пар не запланировано.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(schedule.days, key = { it.dayNum }) { day ->
                DayCard(day = day, selectedSubgroup = selectedSubgroup)
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Источник данных: rsp.iseu.by",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun DayCard(
    day: DaySchedule,
    selectedSubgroup: Int = 0,
    modifier: Modifier = Modifier
) {
    val isToday = remember(day.date, day.dayNum) {
        if (day.date.isNotBlank()) {
            LessonTimeHelper.isToday(day.date)
        } else {
            day.dayNum == LessonTimeHelper.getTodayDayOfWeekNumber()
        }
    }
    val visibleLessons = day.lessons.filter { lesson ->
        when (selectedSubgroup) {
            1 -> lesson.subgroup == null || lesson.subgroup == 1
            2 -> lesson.subgroup == null || lesson.subgroup == 2
            else -> true
        }
    }

    val cardModifier = if (isToday) {
        modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
    } else {
        modifier.fillMaxWidth()
    }

    ElevatedCard(
        modifier = cardModifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Day Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = day.dayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (isToday) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Сегодня",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                if (day.date.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = day.date,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (day.lessons.isEmpty()) {
                WeekendEmptyState(
                    title = "Свободный день 🎉",
                    subtitle = "Пар нет. Отличный повод отдохнуть или закрыть долги!"
                )
            } else if (visibleLessons.isEmpty()) {
                WeekendEmptyState(
                    title = "Пар для подгруппы нет ✨",
                    subtitle = "Для выбранной ${selectedSubgroup}-й подгруппы на этот день занятий не стоит."
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    visibleLessons.forEach { lesson ->
                        LessonRow(lesson = lesson, isDayToday = isToday)
                    }
                }
            }
        }
    }
}

@Composable
fun LessonRow(
    lesson: Lesson,
    isDayToday: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isActive = LessonTimeHelper.isLessonActive(lesson, isDayToday)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Order circle badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lesson.order.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Time & Type Chip & Active badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = lesson.time,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isActive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "● Идёт сейчас",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (lesson.subgroup != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isDark) MaterialTheme.colorScheme.surfaceVariant
                                        else MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "${lesson.subgroup} п/гр",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        LessonTypeTag(type = lesson.type)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Subject name (primary hierarchy anchor - maximum contrast and clarity)
                Text(
                    text = lesson.subject,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFFFFFFF) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Teacher and Room
                if (lesson.teacher.isNotEmpty() || lesson.room.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (lesson.teacher.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = lesson.teacher,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        if (lesson.room.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            RoomBadge(room = lesson.room)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoomBadge(
    room: String,
    modifier: Modifier = Modifier
) {
    if (room.isBlank()) return

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Аудитория",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = room,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun LessonTypeTag(type: LessonType) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (bgColor, textColor) = if (isDark) {
        when (type) {
            LessonType.LECTURE -> Color(0xFF132235) to Color(0xFF7FAAD6)
            LessonType.PRACTICE -> Color(0xFF14271A) to Color(0xFF86BA8D)
            LessonType.LAB -> Color(0xFF2E2012) to Color(0xFFC7A277)
            LessonType.UNKNOWN -> Color(0xFF222224) to Color(0xFF9E9E9E)
        }
    } else {
        when (type) {
            LessonType.LECTURE -> Color(0xFFE3F2FD) to Color(0xFF1976D2)
            LessonType.PRACTICE -> Color(0xFFE8F5E9) to Color(0xFF388E3C)
            LessonType.LAB -> Color(0xFFFFF3E0) to Color(0xFFF57C00)
            LessonType.UNKNOWN -> Color(0xFFF5F5F5) to Color(0xFF616161)
        }
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor
    ) {
        Text(
            text = "${type.emoji} ${type.displayName}",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    ISEUScheduleTheme {
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
                                subject = "Технологии разработки ПО",
                                teacher = "Иванов И.И.",
                                room = "101 к.1"
                            ),
                            Lesson(
                                order = 2,
                                time = "10:15 - 11:50",
                                type = LessonType.LAB,
                                subject = "Сетевые технологии",
                                teacher = "Петров П.П.",
                                room = "204 к.1",
                                subgroup = 1
                            )
                        )
                    )
                )
            )
        )
    }
}

@Composable
fun SubgroupFilterBar(
    selectedSubgroup: Int,
    onSubgroupSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Подгруппа:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FilterChip(
            selected = selectedSubgroup == 0,
            onClick = { onSubgroupSelected(0) },
            label = { Text("Все") }
        )
        FilterChip(
            selected = selectedSubgroup == 1,
            onClick = { onSubgroupSelected(1) },
            label = { Text("1-я") }
        )
        FilterChip(
            selected = selectedSubgroup == 2,
            onClick = { onSubgroupSelected(2) },
            label = { Text("2-я") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    currentCourse: String,
    currentGroup: String,
    currentThemeMode: String = "SYSTEM",
    onDismiss: () -> Unit,
    onSave: (course: String, group: String) -> Unit,
    onThemeModeChange: (String) -> Unit = {},
    onFetchGroups: suspend (course: String) -> List<String>
) {
    var selectedCourse by remember { mutableStateOf(currentCourse) }
    var selectedGroup by remember { mutableStateOf(currentGroup) }
    var selectedThemeMode by remember { mutableStateOf(currentThemeMode) }
    var availableGroups by remember { mutableStateOf<List<String>>(emptyList()) }
    var groupSearchQuery by remember { mutableStateOf("") }
    var isLoadingGroups by remember { mutableStateOf(false) }

    LaunchedEffect(selectedCourse) {
        isLoadingGroups = true
        groupSearchQuery = ""
        try {
            val groups = onFetchGroups(selectedCourse)
            availableGroups = groups
            // Automatic fallback if current group does not belong to the selected course
            if (groups.isNotEmpty() && !groups.contains(selectedGroup)) {
                selectedGroup = groups.first()
            }
        } catch (_: Exception) {
            availableGroups = emptyList()
        } finally {
            isLoadingGroups = false
        }
    }

    val filteredGroups = remember(availableGroups, groupSearchQuery) {
        if (groupSearchQuery.isBlank()) {
            availableGroups
        } else {
            availableGroups.filter { it.contains(groupSearchQuery, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Настройки приложения",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Course selection
                Column {
                    Text(
                        text = "Курс:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(listOf("1", "2", "3", "4", "5")) { course ->
                            FilterChip(
                                selected = selectedCourse == course,
                                onClick = { selectedCourse = course },
                                label = { Text("$course курс") }
                            )
                        }
                    }
                }

                // Group selection
                Column {
                    Text(
                        text = "Группа:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (isLoadingGroups) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Загрузка групп с сайта...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (availableGroups.isNotEmpty()) {
                        OutlinedTextField(
                            value = groupSearchQuery,
                            onValueChange = { groupSearchQuery = it },
                            placeholder = { Text("Поиск группы...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Поиск",
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        )

                        if (filteredGroups.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(filteredGroups) { group ->
                                    FilterChip(
                                        selected = selectedGroup == group,
                                        onClick = { selectedGroup = group },
                                        label = { Text(group) }
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Группы не найдены",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        Text(
                            text = "Для $selectedCourse курса группы не найдены. Введите название группы вручную:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    OutlinedTextField(
                        value = selectedGroup,
                        onValueChange = { selectedGroup = it },
                        label = { Text("Название группы") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Theme selection
                Column {
                    Text(
                        text = "Тема оформления:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themes = listOf(
                            "SYSTEM" to "Авто",
                            "LIGHT" to "Светлая",
                            "DARK" to "Тёмная"
                        )
                        themes.forEach { (mode, label) ->
                            FilterChip(
                                selected = selectedThemeMode == mode,
                                onClick = {
                                    selectedThemeMode = mode
                                    onThemeModeChange(mode)
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedCourse, selectedGroup) },
                enabled = selectedGroup.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun DataSourceDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val officialUrl = "http://rsp.iseu.by/Raspisanie/TimeTable/umu.aspx"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "О приложении и источнике",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Официальный источник:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "МГЭИ им. А.Д. Сахарова БГУ",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "rsp.iseu.by",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(officialUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Открыть сайт расписания")
                }

                Text(
                    text = "Приложение является независимым автономным клиентом. Все запросы выполняются напрямую к сайту университета без сбора или передачи данных на сторонние серверы.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Понятно")
            }
        }
    )
}
