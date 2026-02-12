package com.example.epubreader.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
/**
 * 数据模型序列化测试
 */
class BookInfoSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }
    @Test
    fun `BookInfo serializes and deserializes correctly`() {
        val bookInfo = BookInfo(
            id = "test-id",
            title = "Test Book",
            author = "Test Author",
            coverPath = "/path/to/cover.jpg",
            filePath = "/path/to/book.epub",
            fileSize = 1024L,
            importTime = 1234567890L,
            lastReadTime = 1234567900L,
            currentChapter = 5,
            readingProgress = 0.5f,
            totalChapters = 10
        )
        val jsonString = json.encodeToString(bookInfo)
        val decoded = json.decodeFromString<BookInfo>(jsonString)
        assertEquals(bookInfo.id, decoded.id)
        assertEquals(bookInfo.title, decoded.title)
        assertEquals(bookInfo.author, decoded.author)
        assertEquals(bookInfo.readingProgress, decoded.readingProgress)
    }
    @Test
    fun `ReadingSettings has correct defaults`() {
        val settings = ReadingSettings()
        assertEquals(18, settings.fontSize)
        assertEquals(-1f, settings.brightness)
        assertFalse(settings.isNightMode)
        assertEquals("default", settings.fontFamily)
    }
}

