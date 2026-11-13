package com.example.iseuschedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SecurityValidationTest {

    private lateinit var tempDir: File
    private lateinit var repository: ScheduleRepository

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("security_test_cache").toFile()
        repository = ScheduleRepository(cacheDir = tempDir)
    }

    @Test
    fun sanitizeGroup_stripsPathTraversalSequences() {
        val unixTraversal = ScheduleRepository.sanitizeGroup("../../etc/passwd")
        assertFalse("Should not contain '..'", unixTraversal.contains(".."))
        assertFalse("Should not contain '/'", unixTraversal.contains("/"))
        assertEquals("etcpasswd", unixTraversal)

        val windowsTraversal = ScheduleRepository.sanitizeGroup("..\\..\\windows\\system32")
        assertFalse("Should not contain '..'", windowsTraversal.contains(".."))
        assertFalse("Should not contain '\\'", windowsTraversal.contains("\\"))
        assertEquals("windowssystem32", windowsTraversal)

        val mixedTraversal = ScheduleRepository.sanitizeGroup("foo/../../bar")
        assertFalse(mixedTraversal.contains(".."))
        assertFalse(mixedTraversal.contains("/"))
        assertEquals("foobar", mixedTraversal)
    }

    @Test
    fun sanitizeGroup_stripsNullBytesAndControlChars() {
        val nullByteInput = "31ПИ1\u0000malicious"
        val sanitized = ScheduleRepository.sanitizeGroup(nullByteInput)
        assertFalse("Should not contain null bytes", sanitized.contains("\u0000"))
        assertEquals("31ПИ1malicious", sanitized)
    }

    @Test
    fun sanitizeGroup_enforcesLengthConstraint() {
        val excessivelyLong = "A".repeat(100)
        val sanitized = ScheduleRepository.sanitizeGroup(excessivelyLong)
        assertTrue("Length must be restricted to 32 characters", sanitized.length <= 32)
        assertEquals(32, sanitized.length)
    }

    @Test
    fun sanitizeGroup_handlesBlankAndNullDefaults() {
        assertEquals(ScheduleRepository.DEFAULT_GROUP, ScheduleRepository.sanitizeGroup(null))
        assertEquals(ScheduleRepository.DEFAULT_GROUP, ScheduleRepository.sanitizeGroup(""))
        assertEquals(ScheduleRepository.DEFAULT_GROUP, ScheduleRepository.sanitizeGroup("    "))
        assertEquals(ScheduleRepository.DEFAULT_GROUP, ScheduleRepository.sanitizeGroup("../..///"))
    }

    @Test
    fun sanitizeGroup_preservesLegitimateGroupNames() {
        assertEquals("31ПИ1", ScheduleRepository.sanitizeGroup("31ПИ1"))
        assertEquals("А31ТТ1", ScheduleRepository.sanitizeGroup("А31ТТ1"))
        assertEquals("41ИС1", ScheduleRepository.sanitizeGroup("41ИС1"))
        assertEquals("31ПИ1 (б)", ScheduleRepository.sanitizeGroup("31ПИ1 (б)"))
        assertEquals("11-А", ScheduleRepository.sanitizeGroup("11-А"))
    }

    @Test
    fun validateCourse_enforcesValidCourseRange() {
        assertEquals("1", ScheduleRepository.validateCourse("1"))
        assertEquals("4", ScheduleRepository.validateCourse("4"))
        assertEquals("5", ScheduleRepository.validateCourse("5"))
        assertEquals("6", ScheduleRepository.validateCourse("6"))

        // Invalid courses fallback to DEFAULT_COURSE ("4")
        assertEquals(ScheduleRepository.DEFAULT_COURSE, ScheduleRepository.validateCourse("0"))
        assertEquals(ScheduleRepository.DEFAULT_COURSE, ScheduleRepository.validateCourse("7"))
        assertEquals(ScheduleRepository.DEFAULT_COURSE, ScheduleRepository.validateCourse("-1"))
        assertEquals(ScheduleRepository.DEFAULT_COURSE, ScheduleRepository.validateCourse("abc"))
        assertEquals(ScheduleRepository.DEFAULT_COURSE, ScheduleRepository.validateCourse(null))
    }

    @Test
    fun validateWeekOffset_clampsToPermittedRange() {
        assertEquals(0, ScheduleRepository.validateWeekOffset(0))
        assertEquals(1, ScheduleRepository.validateWeekOffset(1))
        assertEquals(-1, ScheduleRepository.validateWeekOffset(-1))
        assertEquals(26, ScheduleRepository.validateWeekOffset(26))
        assertEquals(-26, ScheduleRepository.validateWeekOffset(-26))

        // Clamping out-of-bounds offsets
        assertEquals(26, ScheduleRepository.validateWeekOffset(999))
        assertEquals(-26, ScheduleRepository.validateWeekOffset(-999))
    }

    @Test
    fun getScheduleCacheFile_doesNotEscapeCacheDirectory() {
        val file = repository.getScheduleCacheFile(weekOffset = -100, group = "../../etc/passwd")
        assertNotNull(file)
        val canonicalCacheDir = tempDir.canonicalPath
        val canonicalFilePath = file!!.canonicalPath

        assertTrue(
            "Cache file must strictly reside inside the designated cache directory",
            canonicalFilePath.startsWith(canonicalCacheDir)
        )
    }

    private fun assertNotNull(obj: Any?) {
        org.junit.Assert.assertNotNull(obj)
    }
}
