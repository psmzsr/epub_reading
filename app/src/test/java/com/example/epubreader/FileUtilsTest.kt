package com.example.epubreader.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** FileUtils 单元测试。 */
class FileUtilsTest {
    @Test
    fun `formatFileSize formats bytes correctly`() {
        assertEquals("0 B", FileUtils.formatFileSize(0))
        assertEquals("500 B", FileUtils.formatFileSize(500))
        assertEquals("1.0 KB", FileUtils.formatFileSize(1024))
        assertEquals("1.5 KB", FileUtils.formatFileSize(1536))
        assertEquals("1.0 MB", FileUtils.formatFileSize(1024 * 1024))
        assertEquals("2.5 MB", FileUtils.formatFileSize((2.5 * 1024 * 1024).toLong()))
        assertEquals("1.0 GB", FileUtils.formatFileSize(1024L * 1024 * 1024))
    }
}
