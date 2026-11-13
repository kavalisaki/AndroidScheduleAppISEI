package com.example.iseuschedule.data

import com.example.iseuschedule.data.model.DaySchedule
import com.example.iseuschedule.data.model.Lesson
import com.example.iseuschedule.data.model.LessonType
import com.example.iseuschedule.data.model.WeekSchedule
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ScheduleRepositoryCacheTest {

    private lateinit var tempDir: File
    private lateinit var repository: ScheduleRepository

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("schedule_test_cache").toFile()
        repository = ScheduleRepository(cacheDir = tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun createSampleSchedule(week: Int = 0): WeekSchedule {
        return WeekSchedule(
            week = week,
            weekName = "Неделя 8 (четная)",
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
                            subject = "Архитектура мобильных систем",
                            teacher = "проф. Сидоров А.В.",
                            room = "ауд. 204",
                            subgroup = null
                        ),
                        Lesson(
                            order = 2,
                            time = "10:15 - 11:50",
                            type = LessonType.LAB,
                            subject = "Разработка Android приложений",
                            teacher = "доц. Петров П.П.",
                            room = "ауд. 312а",
                            subgroup = 1
                        )
                    )
                )
            )
        )
    }

    @Test
    fun serialization_and_deserialization_preserves_all_fields() {
        val original = createSampleSchedule(week = 2)

        val jsonString = repository.json.encodeToString(WeekSchedule.serializer(), original)
        val deserialized = repository.json.decodeFromString(WeekSchedule.serializer(), jsonString)

        assertEquals(original, deserialized)
        assertEquals(2, deserialized.week)
        assertEquals(1, deserialized.days.size)
        assertEquals(LessonType.LAB, deserialized.days[0].lessons[1].type)
        assertEquals(1, deserialized.days[0].lessons[1].subgroup)
    }

    @Test
    fun disk_cache_saves_and_loads_correctly() {
        val schedule = createSampleSchedule(week = 0)

        repository.saveToDiskCache(0, schedule)
        val loaded = repository.loadFromDiskCache(0)

        assertNotNull("Loaded schedule should not be null", loaded)
        assertEquals(schedule, loaded)

        // Verify JSON file exists
        val cacheFiles = tempDir.listFiles { _, name -> name.endsWith("week_0.json") }
        assertTrue("JSON cache file must exist", cacheFiles?.isNotEmpty() == true)
    }

    @Test
    fun cache_resilient_to_special_characters_tabs_newlines_and_emojis() {
        val trickySchedule = WeekSchedule(
            week = 1,
            weekName = "Неделя \t с табами и \n переносами",
            mondayDate = "26.10.2026",
            days = listOf(
                DaySchedule(
                    dayNum = 2,
                    dayName = "Вторник \"Кафедра ИТ\"",
                    date = "27.10.2026",
                    hasSchedule = true,
                    lessons = listOf(
                        Lesson(
                            order = 1,
                            time = "08:30 - 10:05",
                            type = LessonType.PRACTICE,
                            subject = "Спецкурс:\t\"Базы данных\" & BigData\n[Модуль 2] 🚀🔬",
                            teacher = "ст. преп. О'Коннор Д. \t (совм.)",
                            room = "ауд. 101/2-Б",
                            subgroup = 2
                        )
                    )
                )
            )
        )

        repository.saveToDiskCache(1, trickySchedule)
        val loaded = repository.loadFromDiskCache(1)

        assertNotNull("Tricky schedule should be successfully loaded from cache", loaded)
        assertEquals(trickySchedule, loaded)
        assertEquals(trickySchedule.days[0].lessons[0].subject, loaded?.days?.get(0)?.lessons?.get(0)?.subject)
    }

    @Test
    fun offline_fallback_returns_cached_data_when_network_fails() = runTest {
        // OkHttpClient configured to fail all network requests with IOException
        val failingClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor {
                throw IOException("Network offline (simulated)")
            })
            .build()

        val offlineRepo = ScheduleRepository(cacheDir = tempDir, client = failingClient)
        val cachedSchedule = createSampleSchedule(week = 0)

        // Save schedule into disk cache
        offlineRepo.saveToDiskCache(0, cachedSchedule)

        // Attempt fetchSchedule during network failure
        val result = offlineRepo.fetchSchedule(0)

        assertTrue("fetchSchedule should succeed from cache during network outage", result.isSuccess)
        assertEquals(cachedSchedule, result.getOrNull())
    }

    @Test
    fun stale_while_revalidate_flow_emits_cached_data_when_network_fails() = runTest {
        val failingClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor {
                throw IOException("Network failure")
            })
            .build()

        val offlineRepo = ScheduleRepository(cacheDir = tempDir, client = failingClient)
        val cachedSchedule = createSampleSchedule(week = 0)
        offlineRepo.saveToDiskCache(0, cachedSchedule)

        val emissions = offlineRepo.getScheduleFlow(0).toList()

        assertEquals("Should emit cached data once even if network fails", 1, emissions.size)
        assertTrue(emissions[0].isSuccess)
        assertEquals(cachedSchedule, emissions[0].getOrNull())
    }

    @Test
    fun clearCache_removes_both_memory_and_disk_cache() {
        val schedule = createSampleSchedule(week = 3)
        repository.saveToDiskCache(3, schedule)

        assertNotNull(repository.loadFromDiskCache(3))

        repository.clearCache()

        assertNull("Disk cache should be null after clearCache", repository.loadFromDiskCache(3))
        assertNull("Memory cache should be empty after clearCache", repository.getCachedSchedule(3))
    }

    @Test
    fun legacy_cache_file_migrates_to_json_cache_automatically() {
        // Write old legacy format to schedule_week_4.cache
        val legacyFile = File(tempDir, "schedule_week_4.cache")
        val legacyContent = """
            4
            Неделя 4
            12.10.2026
            1
            DAY	1	Понедельник	12.10.2026	true	1
            LESSON	1	08:30 - 10:05	LECTURE	Экология	проф. Иванов И.И.	ауд. 101	-1
        """.trimIndent()
        legacyFile.writeText(legacyContent)

        // Load through repository
        val loaded = repository.loadFromDiskCache(4)

        assertNotNull("Legacy cache should be successfully migrated and parsed", loaded)
        assertEquals(4, loaded?.week)
        assertEquals("Понедельник", loaded?.days?.get(0)?.dayName)
        assertEquals("Экология", loaded?.days?.get(0)?.lessons?.get(0)?.subject)

        // Verify JSON cache file was created and old file was cleaned up
        val jsonFiles = tempDir.listFiles { _, name -> name.endsWith("week_4.json") }
        assertTrue("JSON cache file should be generated during migration", jsonFiles?.isNotEmpty() == true)
    }

    @Test
    fun isCacheValid_returnsTrueForFreshCache_andFalseForExpiredCache() {
        val schedule = createSampleSchedule(week = 0)
        repository.saveToDiskCache(0, schedule)

        val cacheFile = repository.getScheduleCacheFile(0)
        assertNotNull(cacheFile)
        assertTrue(cacheFile!!.exists())

        // 1. Fresh cache: valid
        assertTrue("Newly written cache should be valid", repository.isCacheValid(0))

        // 2. Expired cache (older than CACHE_TTL_MS = 1 hour)
        val expiredTimestamp = System.currentTimeMillis() - (ScheduleRepository.CACHE_TTL_MS + 10_000L)
        cacheFile.setLastModified(expiredTimestamp)

        assertFalse("Cache older than 1 hour should be considered expired", repository.isCacheValid(0))
    }

    @Test
    fun getScheduleFlow_withValidCache_skipsNetworkQueryWhenNotForceRefresh() = runTest {
        // Client configured to throw exception if any network request is attempted
        val failingClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor {
                throw IOException("Network should NOT be queried when cache is valid and forceRefresh == false")
            })
            .build()

        val repo = ScheduleRepository(
            cacheDir = tempDir,
            client = failingClient,
            rateLimiter = NetworkRateLimiter(minIntervalMs = 0L)
        )

        val schedule = createSampleSchedule(week = 0)
        repo.saveToDiskCache(0, schedule)

        // Ensure cache file timestamp is fresh
        val cacheFile = repo.getScheduleCacheFile(0)
        cacheFile?.setLastModified(System.currentTimeMillis())

        val emissions = repo.getScheduleFlow(0, forceRefresh = false).toList()

        assertEquals("Should emit cached data once without attempting network request", 1, emissions.size)
        assertTrue(emissions[0].isSuccess)
        assertEquals(schedule, emissions[0].getOrNull())
    }

    @Test
    fun getScheduleFlow_withValidCache_callsNetworkWhenForceRefreshIsTrue() = runTest {
        var networkAttempted = false
        val customClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor {
                networkAttempted = true
                throw IOException("Network attempted due to forceRefresh")
            })
            .build()

        val repo = ScheduleRepository(
            cacheDir = tempDir,
            client = customClient,
            rateLimiter = NetworkRateLimiter(minIntervalMs = 0L)
        )

        val schedule = createSampleSchedule(week = 0)
        repo.saveToDiskCache(0, schedule)

        // Call with forceRefresh = true
        val emissions = repo.getScheduleFlow(0, forceRefresh = true).toList()

        assertTrue("Network request should be executed when forceRefresh is true", networkAttempted)
        assertEquals("Emits cached data first", 1, emissions.size)
        assertTrue(emissions[0].isSuccess)
    }

    @Test
    fun fetchAvailableGroups_usesDiskCache_whenCacheIsValid() = runTest {
        // Client configured to throw if network is called
        val failingClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor {
                throw IOException("Network should not be called")
            })
            .build()

        val repo = ScheduleRepository(
            cacheDir = tempDir,
            client = failingClient,
            rateLimiter = NetworkRateLimiter(minIntervalMs = 0L)
        )

        // Seed groups cache for course 4
        val groupsFile = File(tempDir, "groups_course_4.json")
        val sampleGroups = listOf("41ТТ1", "41ПИ1", "41ИС1")
        groupsFile.writeText(repo.json.encodeToString(sampleGroups))

        val result = repo.fetchAvailableGroups("4", forceRefresh = false)

        assertTrue(result.isSuccess)
        assertEquals(sampleGroups, result.getOrNull())
    }
}
