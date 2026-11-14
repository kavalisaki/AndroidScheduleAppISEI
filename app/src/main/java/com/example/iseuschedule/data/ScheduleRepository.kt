package com.example.iseuschedule.data

import android.content.Context
import com.example.iseuschedule.data.model.Lesson
import com.example.iseuschedule.data.model.LessonType
import com.example.iseuschedule.data.model.ScheduleDiffHelper
import com.example.iseuschedule.data.model.ScheduleDiffResult
import com.example.iseuschedule.data.model.WeekSchedule
import com.example.iseuschedule.data.parser.ScheduleParser
import com.example.iseuschedule.data.settings.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class NetworkRateLimiter(
    val minIntervalMs: Long = 3000L,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private var lastRequestTimestamp: Long = 0L
    private val mutex = Mutex()

    /**
     * Checks if a request can be executed immediately without waiting.
     */
    fun isRequestAllowed(): Boolean {
        val now = timeProvider()
        return lastRequestTimestamp == 0L || (now - lastRequestTimestamp >= minIntervalMs)
    }

    /**
     * Returns remaining delay in milliseconds before the next request is permitted.
     */
    fun getRemainingWaitMs(): Long {
        val now = timeProvider()
        val elapsed = now - lastRequestTimestamp
        return if (lastRequestTimestamp == 0L) 0L else (minIntervalMs - elapsed).coerceAtLeast(0L)
    }

    /**
     * Suspends until the rate limit interval has passed, then records current timestamp.
     */
    suspend fun acquire() {
        mutex.withLock {
            val now = timeProvider()
            val elapsed = now - lastRequestTimestamp
            if (lastRequestTimestamp > 0L && elapsed < minIntervalMs) {
                val delayTime = minIntervalMs - elapsed
                delay(delayTime)
            }
            lastRequestTimestamp = timeProvider()
        }
    }

    /**
     * Resets the rate limiter (e.g. for testing).
     */
    fun reset() {
        lastRequestTimestamp = 0L
    }
}

class ScheduleRepository(
    private val context: Context? = null,
    private val cacheDir: File? = null,
    private val customClient: OkHttpClient? = null,
    val userSettingsRepository: UserSettingsRepository = UserSettingsRepository(context),
    val rateLimiter: NetworkRateLimiter = NetworkRateLimiter(),
    client: OkHttpClient? = null
) {

    companion object {
        const val SITE_URL = "http://rsp.iseu.by/Raspisanie/TimeTable/umu.aspx"
        const val DEFAULT_FACULTY = "2"
        const val DEFAULT_COURSE = "4"
        const val DEFAULT_GROUP = "31ПИ1"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        const val CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour
        const val GROUPS_CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000L // 7 days

        val ALLOWED_FACULTIES = setOf("2", "4")
        val ALLOWED_COURSES = setOf("1", "2", "3", "4", "5", "6")
        const val MIN_WEEK_OFFSET = -26
        const val MAX_WEEK_OFFSET = 26

        fun validateFaculty(faculty: String?): String {
            val trimmed = faculty?.trim() ?: DEFAULT_FACULTY
            return if (trimmed in ALLOWED_FACULTIES) trimmed else DEFAULT_FACULTY
        }

        /**
         * Sanitizes group name against Path Traversal and filesystem injection attacks.
         * Strips path traversal sequences (.., /, \), null bytes, restricts length to 32 chars,
         * and preserves valid alphanumeric and safe separator characters.
         */
        fun sanitizeGroup(group: String?): String {
            if (group.isNullOrBlank()) return DEFAULT_GROUP

            // Remove null bytes and control chars
            var cleaned = group.replace("\u0000", "").trim()

            // Remove path traversal patterns
            cleaned = cleaned.replace("..", "")
                .replace("/", "")
                .replace("\\", "")
                .replace(":", "")

            // Keep only safe characters: letters, digits, spaces, hyphens, underscores, dots, parentheses
            cleaned = cleaned.filter { ch ->
                ch.isLetterOrDigit() || ch in setOf('-', '_', '.', ' ', '(', ')')
            }.trim()

            while (cleaned.contains("..")) {
                cleaned = cleaned.replace("..", "")
            }
            cleaned = cleaned.trim('.', ' ')

            if (cleaned.isBlank()) return DEFAULT_GROUP

            // Restrict maximum length to 32 characters
            return if (cleaned.length > 32) cleaned.substring(0, 32).trim() else cleaned
        }

        fun validateCourse(course: String?): String {
            val trimmed = course?.trim() ?: DEFAULT_COURSE
            return if (trimmed in ALLOWED_COURSES) trimmed else DEFAULT_COURSE
        }

        fun validateWeekOffset(weekOffset: Int): Int {
            return weekOffset.coerceIn(MIN_WEEK_OFFSET, MAX_WEEK_OFFSET)
        }
    }

    private val memoryCache = ConcurrentHashMap<String, WeekSchedule>()

    private class SimpleCookieJar : CookieJar {
        private val cookieStore = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(cookieStore) {
                for (newCookie in cookies) {
                    cookieStore.removeAll { it.name == newCookie.name }
                    cookieStore.add(newCookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            synchronized(cookieStore) {
                val now = System.currentTimeMillis()
                cookieStore.removeAll { it.expiresAt < now }
                return cookieStore.filter { it.matches(url) }
            }
        }
    }

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
    }

    private fun getCacheDirectory(): File? = cacheDir ?: context?.cacheDir

    private val initialClient: OkHttpClient? = customClient ?: client

    private val client: OkHttpClient by lazy {
        initialClient ?: OkHttpClient.Builder()
            .cookieJar(SimpleCookieJar())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val _recentScheduleChanges = MutableStateFlow<ScheduleDiffResult?>(null)
    val recentScheduleChanges: StateFlow<ScheduleDiffResult?> = _recentScheduleChanges.asStateFlow()

    fun dismissScheduleChanges() {
        _recentScheduleChanges.value = null
    }

    fun createSessionClient(): OkHttpClient {
        return client.newBuilder()
            .cookieJar(SimpleCookieJar())
            .build()
    }

    fun getTargetFaculty(faculty: String? = null): String {
        val raw = faculty?.takeIf { it.isNotBlank() }
            ?: userSettingsRepository.getSettings().faculty.ifBlank { DEFAULT_FACULTY }
        return validateFaculty(raw)
    }

    fun getTargetCourse(course: String? = null): String {
        val raw = course?.takeIf { it.isNotBlank() }
            ?: userSettingsRepository.getSettings().course.ifBlank { DEFAULT_COURSE }
        return validateCourse(raw)
    }

    fun getTargetGroup(group: String? = null): String {
        val raw = group?.takeIf { it.isNotBlank() }
            ?: userSettingsRepository.getSettings().group.ifBlank { DEFAULT_GROUP }
        return sanitizeGroup(raw)
    }

    private fun getCacheKey(group: String, weekOffset: Int): String {
        val targetGroup = getTargetGroup(group)
        val validWeek = validateWeekOffset(weekOffset)
        return "${targetGroup}_$validWeek"
    }

    fun getScheduleCacheFile(weekOffset: Int, group: String? = null): File? {
        val baseDir = getCacheDirectory() ?: return null
        val targetGroup = getTargetGroup(group)
        val validWeek = validateWeekOffset(weekOffset)
        val file = File(baseDir, "schedule_${targetGroup}_week_$validWeek.json")
        if (file.exists()) return file
        val legacy = File(baseDir, "schedule_week_$validWeek.json")
        if (legacy.exists()) return legacy
        return file
    }

    fun isCacheValid(weekOffset: Int, group: String? = null): Boolean {
        val file = getScheduleCacheFile(validateWeekOffset(weekOffset), group) ?: return false
        if (!file.exists()) return false
        val age = System.currentTimeMillis() - file.lastModified()
        return age in 0..CACHE_TTL_MS
    }

    fun getCachedSchedule(weekOffset: Int = 0, group: String? = null): WeekSchedule? {
        val targetGroup = getTargetGroup(group)
        val validWeek = validateWeekOffset(weekOffset)
        val key = getCacheKey(targetGroup, validWeek)
        return memoryCache[key] ?: loadFromDiskCache(validWeek, targetGroup)
    }

    /**
     * Stale-While-Revalidate pattern:
     * Immediately emits cached data (if available). If the cache is valid (TTL <= 1h)
     * and [forceRefresh] is false, network query is bypassed.
     * If cache is expired or [forceRefresh] is true, queries the network and emits fresh data.
     */
    fun getScheduleFlow(
        weekOffset: Int = 0,
        course: String? = null,
        group: String? = null,
        faculty: String? = null,
        forceRefresh: Boolean = false
    ): Flow<Result<WeekSchedule>> = flow {
        val targetFaculty = getTargetFaculty(faculty)
        val targetCourse = getTargetCourse(course)
        val targetGroup = getTargetGroup(group)
        val key = getCacheKey(targetGroup, weekOffset)

        val cached = getCachedSchedule(weekOffset, targetGroup)
        val isFresh = isCacheValid(weekOffset, targetGroup)

        if (cached != null && cached.days.isNotEmpty()) {
            emit(Result.success(cached))
            if (isFresh && !forceRefresh) {
                return@flow
            }
        }

        try {
            val freshSchedule = loadFromNetwork(weekOffset, targetCourse, targetGroup, targetFaculty)
            if (cached != null && cached.days.isNotEmpty()) {
                val diff = ScheduleDiffHelper.findDifferences(cached, freshSchedule)
                if (diff.hasChanges) {
                    _recentScheduleChanges.value = diff
                }
            }
            memoryCache[key] = freshSchedule
            saveToDiskCache(weekOffset, freshSchedule, targetGroup)
            emit(Result.success(freshSchedule))
        } catch (e: Exception) {
            if (cached == null || cached.days.isEmpty()) {
                emit(Result.failure(e))
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun fetchSchedule(
        weekOffset: Int = 0,
        course: String? = null,
        group: String? = null,
        faculty: String? = null
    ): Result<WeekSchedule> = withContext(Dispatchers.IO) {
        val targetFaculty = getTargetFaculty(faculty)
        val targetCourse = getTargetCourse(course)
        val targetGroup = getTargetGroup(group)
        val key = getCacheKey(targetGroup, weekOffset)

        try {
            val cached = getCachedSchedule(weekOffset, targetGroup)
            val freshSchedule = loadFromNetwork(weekOffset, targetCourse, targetGroup, targetFaculty)
            if (cached != null && cached.days.isNotEmpty()) {
                val diff = ScheduleDiffHelper.findDifferences(cached, freshSchedule)
                if (diff.hasChanges) {
                    _recentScheduleChanges.value = diff
                }
            }
            memoryCache[key] = freshSchedule
            saveToDiskCache(weekOffset, freshSchedule, targetGroup)
            Result.success(freshSchedule)
        } catch (e: Exception) {
            val cached = getCachedSchedule(weekOffset, targetGroup)
            if (cached != null && cached.days.isNotEmpty()) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun fetchAvailableGroups(
        course: String,
        faculty: String? = null,
        forceRefresh: Boolean = false
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val validCourse = validateCourse(course)
        val validFaculty = getTargetFaculty(faculty)
        val baseDir = getCacheDirectory()
        val primaryCacheFile = baseDir?.let { File(it, "groups_fac_${validFaculty}_course_${validCourse}.json") }
        val legacyCacheFile = if (validFaculty == DEFAULT_FACULTY) baseDir?.let { File(it, "groups_course_${validCourse}.json") } else null
        val groupsCacheFile = if (primaryCacheFile != null && primaryCacheFile.exists()) primaryCacheFile else (legacyCacheFile ?: primaryCacheFile)

        if (!forceRefresh && groupsCacheFile != null && groupsCacheFile.exists()) {
            val age = System.currentTimeMillis() - groupsCacheFile.lastModified()
            if (age in 0..GROUPS_CACHE_TTL_MS) {
                try {
                    val content = groupsCacheFile.readText()
                    if (content.isNotBlank()) {
                        val cachedGroups = json.decodeFromString<List<String>>(content)
                        if (cachedGroups.isNotEmpty()) {
                            return@withContext Result.success(cachedGroups)
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        try {
            rateLimiter.acquire()
            val sessionClient = createSessionClient()

            val req1 = Request.Builder()
                .url(SITE_URL)
                .header("User-Agent", USER_AGENT)
                .header("Connection", "close")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            val html1 = sessionClient.newCall(req1).execute().use { resp1 ->
                resp1.body?.string() ?: throw IllegalStateException("Empty response from $SITE_URL")
            }
            val doc1 = Jsoup.parse(html1)
            var formState = ScheduleParser.extractAspnetFormState(doc1)

            if (validFaculty != DEFAULT_FACULTY) {
                formState["__EVENTTARGET"] = "ddlFac"
                formState["__EVENTARGUMENT"] = ""
                formState["ddlFac"] = validFaculty

                val facBody = FormBody.Builder()
                formState.forEach { (k, v) -> facBody.add(k, v) }

                val reqFac = Request.Builder()
                    .url(SITE_URL)
                    .header("User-Agent", USER_AGENT)
                    .header("Connection", "close")
                    .header("Origin", "http://rsp.iseu.by")
                    .header("Referer", SITE_URL)
                    .post(facBody.build())
                    .build()

                val htmlFac = sessionClient.newCall(reqFac).execute().use { respFac ->
                    respFac.body?.string() ?: throw IllegalStateException("Empty faculty response")
                }
                val docFac = Jsoup.parse(htmlFac)
                formState = ScheduleParser.extractAspnetFormState(docFac)
            }

            formState["__EVENTTARGET"] = "ddlCourse"
            formState["__EVENTARGUMENT"] = ""
            formState["ddlFac"] = validFaculty
            formState["ddlCourse"] = validCourse

            val formBody2 = FormBody.Builder()
            formState.forEach { (k, v) -> formBody2.add(k, v) }

            val req2 = Request.Builder()
                .url(SITE_URL)
                .header("User-Agent", USER_AGENT)
                .header("Connection", "close")
                .header("Origin", "http://rsp.iseu.by")
                .header("Referer", SITE_URL)
                .post(formBody2.build())
                .build()

            val html2 = sessionClient.newCall(req2).execute().use { resp2 ->
                resp2.body?.string() ?: throw IllegalStateException("Empty course response")
            }
            val doc2 = Jsoup.parse(html2)
            val groups = ScheduleParser.extractGroups(doc2)

            if (groups.isNotEmpty() && primaryCacheFile != null) {
                try {
                    if (!baseDir.exists()) baseDir.mkdirs()
                    primaryCacheFile.writeText(json.encodeToString(groups))
                } catch (_: Exception) {}
            }

            Result.success(groups)
        } catch (e: Exception) {
            // Fallback to cached groups if network fails
            if (groupsCacheFile != null && groupsCacheFile.exists()) {
                try {
                    val fallback = json.decodeFromString<List<String>>(groupsCacheFile.readText())
                    if (fallback.isNotEmpty()) return@withContext Result.success(fallback)
                } catch (_: Exception) {}
            }
            Result.failure(e)
        }
    }

    private suspend fun loadFromNetwork(
        weekOffset: Int,
        targetCourse: String,
        targetGroup: String,
        targetFaculty: String = DEFAULT_FACULTY
    ): WeekSchedule {
        rateLimiter.acquire()
        val sessionClient = createSessionClient()

        // 1. Initial GET
        val req1 = Request.Builder()
            .url(SITE_URL)
            .header("User-Agent", USER_AGENT)
            .header("Connection", "close")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()

        val html1 = sessionClient.newCall(req1).execute().use { resp1 ->
            resp1.body?.string() ?: throw IllegalStateException("Empty response from $SITE_URL")
        }
        val doc1 = Jsoup.parse(html1)
        var formCurrent = ScheduleParser.extractAspnetFormState(doc1)

        // 1.5 If faculty is not default, switch faculty
        if (targetFaculty != DEFAULT_FACULTY) {
            formCurrent["__EVENTTARGET"] = "ddlFac"
            formCurrent["__EVENTARGUMENT"] = ""
            formCurrent["ddlFac"] = targetFaculty

            val facBody = FormBody.Builder()
            formCurrent.forEach { (k, v) -> facBody.add(k, v) }

            val reqFac = Request.Builder()
                .url(SITE_URL)
                .header("User-Agent", USER_AGENT)
                .header("Connection", "close")
                .header("Origin", "http://rsp.iseu.by")
                .header("Referer", SITE_URL)
                .post(facBody.build())
                .build()

            val htmlFac = sessionClient.newCall(reqFac).execute().use { respFac ->
                respFac.body?.string() ?: throw IllegalStateException("Empty faculty response")
            }
            formCurrent = ScheduleParser.extractAspnetFormState(Jsoup.parse(htmlFac))
        }

        // 2. Select Course (ddlCourse = targetCourse)
        formCurrent["__EVENTTARGET"] = "ddlCourse"
        formCurrent["__EVENTARGUMENT"] = ""
        formCurrent["ddlFac"] = targetFaculty
        formCurrent["ddlCourse"] = targetCourse

        val formBody2 = FormBody.Builder()
        formCurrent.forEach { (k, v) -> formBody2.add(k, v) }

        val req2 = Request.Builder()
            .url(SITE_URL)
            .header("User-Agent", USER_AGENT)
            .header("Connection", "close")
            .header("Origin", "http://rsp.iseu.by")
            .header("Referer", SITE_URL)
            .post(formBody2.build())
            .build()

        val html2 = sessionClient.newCall(req2).execute().use { resp2 ->
            resp2.body?.string() ?: throw IllegalStateException("Empty course response")
        }
        val doc2 = Jsoup.parse(html2)
        val form2 = ScheduleParser.extractAspnetFormState(doc2)

        // 3. Find group in ddlGroup with strict priority matching
        val groupSelect = doc2.selectFirst("select#ddlGroup")
            ?: throw IllegalStateException("Element #ddlGroup not found")

        val options = groupSelect.select("option").map { it.text().trim() to it.attr("value") }
        val matchedOption = ScheduleParser.findMatchingGroupOption(options, targetGroup)
            ?: options.firstOrNull { it.second == targetGroup }

        val groupValue = matchedOption?.second
        if (groupValue == null) {
            val available = groupSelect.select("option").map { it.text() }
            throw IllegalStateException("Group '$targetGroup' not found among: $available")
        }

        form2["__EVENTTARGET"] = "ddlGroup"
        form2["__EVENTARGUMENT"] = ""
        form2["ddlFac"] = targetFaculty
        form2["ddlCourse"] = targetCourse
        form2["ddlGroup"] = groupValue

        val formBody3 = FormBody.Builder()
        form2.forEach { (k, v) -> formBody3.add(k, v) }

        val req3 = Request.Builder()
            .url(SITE_URL)
            .header("User-Agent", USER_AGENT)
            .header("Connection", "close")
            .header("Origin", "http://rsp.iseu.by")
            .header("Referer", SITE_URL)
            .post(formBody3.build())
            .build()

        val html3 = sessionClient.newCall(req3).execute().use { resp3 ->
            resp3.body?.string() ?: throw IllegalStateException("Empty group response")
        }
        val doc3 = Jsoup.parse(html3)
        formCurrent = ScheduleParser.extractAspnetFormState(doc3)

        // 4. Select Week (if weekOffset != 0)
        if (weekOffset != 0) {
            val weekSelect = doc3.selectFirst("select#ddlWeek")
            if (weekSelect != null) {
                val weekOptions = weekSelect.select("option")
                val now = LocalDate.now()
                val targetMonday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(weekOffset.toLong())
                val targetDateStr = targetMonday.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

                var weekValue: String? = null
                for (opt in weekOptions) {
                    if (opt.text().contains(targetDateStr)) {
                        weekValue = opt.attr("value")
                        break
                    }
                }

                if (weekValue == null) {
                    val selIndex = weekOptions.indexOfFirst { it.hasAttr("selected") }.let { if (it >= 0) it else 0 }
                    val targetIdx = selIndex + weekOffset
                    if (targetIdx in weekOptions.indices) {
                        weekValue = weekOptions[targetIdx].attr("value")
                    }
                }

                if (weekValue != null) {
                    formCurrent["__EVENTTARGET"] = "ddlWeek"
                    formCurrent["__EVENTARGUMENT"] = ""
                    formCurrent["ddlFac"] = targetFaculty
                    formCurrent["ddlCourse"] = targetCourse
                    formCurrent["ddlGroup"] = groupValue
                    formCurrent["ddlWeek"] = weekValue

                    val weekBody = FormBody.Builder()
                    formCurrent.forEach { (k, v) -> weekBody.add(k, v) }

                    val reqWeek = Request.Builder()
                        .url(SITE_URL)
                        .header("User-Agent", USER_AGENT)
                        .header("Connection", "close")
                        .header("Origin", "http://rsp.iseu.by")
                        .header("Referer", SITE_URL)
                        .post(weekBody.build())
                        .build()

                    val htmlWeek = sessionClient.newCall(reqWeek).execute().use { respWeek ->
                        respWeek.body?.string() ?: ""
                    }
                    formCurrent = ScheduleParser.extractAspnetFormState(Jsoup.parse(htmlWeek))
                }
            }
        }

        // 5. Click ShowTT ("Показать")
        formCurrent["__EVENTTARGET"] = ""
        formCurrent["__EVENTARGUMENT"] = ""
        formCurrent["ddlFac"] = targetFaculty
        formCurrent["ddlCourse"] = targetCourse
        formCurrent["ddlGroup"] = groupValue
        formCurrent["ShowTT"] = "Показать"

        val formBodyFinal = FormBody.Builder()
        formCurrent.forEach { (k, v) -> formBodyFinal.add(k, v) }

        val reqFinal = Request.Builder()
            .url(SITE_URL)
            .header("User-Agent", USER_AGENT)
            .header("Connection", "close")
            .header("Origin", "http://rsp.iseu.by")
            .header("Referer", SITE_URL)
            .post(formBodyFinal.build())
            .build()

        val htmlFinal = sessionClient.newCall(reqFinal).execute().use { respFinal ->
            respFinal.body?.string() ?: throw IllegalStateException("Empty timetable response")
        }

        return ScheduleParser.parseScheduleHtml(htmlFinal, weekOffset)
    }

    fun saveToDiskCache(
        weekOffset: Int,
        schedule: WeekSchedule,
        group: String? = null
    ) {
        val baseDir = getCacheDirectory() ?: return
        val targetGroup = getTargetGroup(group)
        val validWeek = validateWeekOffset(weekOffset)
        try {
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }
            val file = File(baseDir, "schedule_${targetGroup}_week_$validWeek.json")
            file.writeText(json.encodeToString(WeekSchedule.serializer(), schedule))
            context?.let { ctx ->
                try {
                    com.example.iseuschedule.widget.ScheduleWidgetProvider.updateAllWidgets(ctx)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    fun loadFromDiskCache(
        weekOffset: Int,
        group: String? = null
    ): WeekSchedule? {
        val baseDir = getCacheDirectory() ?: return null
        val targetGroup = getTargetGroup(group)
        val validWeek = validateWeekOffset(weekOffset)
        val jsonFile = File(baseDir, "schedule_${targetGroup}_week_$validWeek.json")
        if (jsonFile.exists()) {
            return try {
                val content = jsonFile.readText()
                if (content.isNotBlank()) {
                    json.decodeFromString(WeekSchedule.serializer(), content)
                } else null
            } catch (_: Exception) {
                null
            }
        }

        // Backward compatibility: check without group prefix
        val fallbackJsonFile = File(baseDir, "schedule_week_$validWeek.json")
        if (fallbackJsonFile.exists()) {
            return try {
                val content = fallbackJsonFile.readText()
                if (content.isNotBlank()) {
                    json.decodeFromString(WeekSchedule.serializer(), content)
                } else null
            } catch (_: Exception) {
                null
            }
        }

        // Migration from legacy .cache format if present
        val legacyFile = File(baseDir, "schedule_week_$validWeek.cache")
        if (legacyFile.exists()) {
            val legacySchedule = loadLegacyCache(legacyFile, validWeek)
            if (legacySchedule != null) {
                saveToDiskCache(validWeek, legacySchedule, targetGroup)
                try { legacyFile.delete() } catch (_: Exception) {}
                return legacySchedule
            }
        }
        return null
    }

    fun clearCache() {
        memoryCache.clear()
        val baseDir = getCacheDirectory() ?: return
        try {
            baseDir.listFiles { _, name -> name.startsWith("schedule_") || name.startsWith("groups_") }?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    private fun loadLegacyCache(file: File, weekOffset: Int): WeekSchedule? {
        try {
            val lines = file.readLines()
            if (lines.size < 4) return null
            val week = lines[0].toIntOrNull() ?: weekOffset
            val weekName = lines[1]
            val mondayDate = lines[2]
            val days = mutableListOf<com.example.iseuschedule.data.model.DaySchedule>()

            var currentDayNum = 1
            var currentDayName = ""
            var currentDate = ""
            var currentHasSchedule = false
            var currentLessons = mutableListOf<Lesson>()

            for (i in 4 until lines.size) {
                val parts = lines[i].split("\t")
                if (parts[0] == "DAY") {
                    if (currentDayName.isNotEmpty()) {
                        days.add(com.example.iseuschedule.data.model.DaySchedule(currentDayNum, currentDayName, currentDate, currentHasSchedule, currentLessons))
                        currentLessons = mutableListOf()
                    }
                    currentDayNum = parts.getOrNull(1)?.toIntOrNull() ?: 1
                    currentDayName = parts.getOrNull(2) ?: ""
                    currentDate = parts.getOrNull(3) ?: ""
                    currentHasSchedule = parts.getOrNull(4)?.toBoolean() ?: false
                } else if (parts[0] == "LESSON") {
                    val order = parts.getOrNull(1)?.toIntOrNull() ?: 1
                    val time = parts.getOrNull(2) ?: ""
                    val type = try { LessonType.valueOf(parts.getOrNull(3) ?: "UNKNOWN") } catch (_: Exception) { LessonType.UNKNOWN }
                    val subject = parts.getOrNull(4) ?: ""
                    val teacher = parts.getOrNull(5) ?: ""
                    val room = parts.getOrNull(6) ?: ""
                    val sgRaw = parts.getOrNull(7)?.toIntOrNull() ?: -1
                    val subgroup = if (sgRaw > 0) sgRaw else null
                    currentLessons.add(Lesson(order, time, type, subject, teacher, room, subgroup))
                }
            }
            if (currentDayName.isNotEmpty()) {
                days.add(com.example.iseuschedule.data.model.DaySchedule(currentDayNum, currentDayName, currentDate, currentHasSchedule, currentLessons))
            }
            return WeekSchedule(week, weekName, mondayDate, days)
        } catch (_: Exception) {
            return null
        }
    }
}
