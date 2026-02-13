package com.example.epubreader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 领域模型单元测试。 */
class DomainModelTest {
    @Test
    fun `Book progressText returns correct percentage`() {
        val book = createTestBook(readingProgress = 0.75f)
        assertEquals("75%", book.progressText)
    }

    @Test
    fun `Book hasProgress returns true when progress greater than zero`() {
        val book = createTestBook(readingProgress = 0.1f)
        assertTrue(book.hasProgress)
    }

    @Test
    fun `Book hasProgress returns false when progress equals zero`() {
        val book = createTestBook(readingProgress = 0f)
        assertFalse(book.hasProgress)
    }

    @Test
    fun `ReadingState chapterInfo returns correct format`() {
        val state = ReadingState(
            currentChapter = 5,
            totalChapters = 10,
            chapterTitle = "Chapter 5",
            scrollPosition = 0f,
            isLoading = false,
            error = null
        )
        assertEquals("第 6 章 / 共 10 章", state.chapterInfo)
    }

    @Test
    fun `ReadingState hasError returns true when error is present`() {
        val state = ReadingState.initial().copy(error = "Test error")
        assertTrue(state.hasError)
    }

    @Test
    fun `ReadingState initial state is loading`() {
        val state = ReadingState.initial()
        assertTrue(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `ReaderTheme has expected colors`() {
        val lightTheme = ReaderTheme.LIGHT
        assertEquals(0xFFFFFBF5, lightTheme.backgroundColor)
        assertEquals(0xFF1A1A1A, lightTheme.textColor)

        val darkTheme = ReaderTheme.DARK
        assertEquals(0xFF121212, darkTheme.backgroundColor)
        assertEquals(0xFFE0E0E0, darkTheme.textColor)
    }

    /**
     * createTestBook 方法。 
     */
    private fun createTestBook(readingProgress: Float = 0f) = Book(
        id = "test-id",
        title = "Test Book",
        author = "Test Author",
        coverPath = null,
        filePath = "/path/to/book.epub",
        fileSize = "1.0 MB",
        importTime = "2024-01-01 00:00",
        lastReadTime = "2024-01-01 00:00",
        currentChapter = 0,
        totalChapters = 10,
        readingProgress = readingProgress
    )
}
