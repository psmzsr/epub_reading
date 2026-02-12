package com.example.epubreader.data.repository

import android.content.Context
import com.example.epubreader.core.util.FileUtils
import com.example.epubreader.data.model.BookInfo
import com.example.epubreader.data.model.ReadingProgress
import com.example.epubreader.data.model.ReadingSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 书籍仓库实现
 */
class BookRepositoryImpl(
    private val context: Context
) : BookRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val booksFile: File
        get() = File(context.filesDir, "books.json")

    private val progressFile: File
        get() = File(context.filesDir, "reading_progress.json")

    private val settingsFile: File
        get() = File(context.filesDir, "reading_settings.json")

    private val _booksFlow = MutableStateFlow<List<BookInfo>>(emptyList())

    init {
        // 加载已保存的书籍
        loadBooks()
    }

    private fun loadBooks() {
        try {
            if (booksFile.exists()) {
                val jsonString = booksFile.readText()
                val books = json.decodeFromString<List<BookInfo>>(jsonString)
                _booksFlow.value = books
            }
        } catch (e: Exception) {
            _booksFlow.value = emptyList()
        }
    }

    private suspend fun saveBooks() {
        withContext(Dispatchers.IO) {
            try {
                val jsonString = json.encodeToString(_booksFlow.value)
                booksFile.writeText(jsonString)
            } catch (e: Exception) {
                // Handle error silently or log
            }
        }
    }

    override fun getAllBooks(): Flow<List<BookInfo>> = _booksFlow.asStateFlow()

    override suspend fun getBook(bookId: String): BookInfo? {
        return _booksFlow.value.find { it.id == bookId }
    }

    override suspend fun addBook(book: BookInfo) {
        val currentBooks = _booksFlow.value.toMutableList()
        currentBooks.add(book)
        _booksFlow.value = currentBooks
        saveBooks()
    }

    override suspend fun updateBook(book: BookInfo) {
        val currentBooks = _booksFlow.value.toMutableList()
        val index = currentBooks.indexOfFirst { it.id == book.id }
        if (index != -1) {
            currentBooks[index] = book
            _booksFlow.value = currentBooks
            saveBooks()
        }
    }

    override suspend fun deleteBook(bookId: String) {
        val book = getBook(bookId)
        if (book != null) {
            // 删除缓存文件
            FileUtils.cleanBookCache(book.filePath)
        }

        val currentBooks = _booksFlow.value.filterNot { it.id == bookId }
        _booksFlow.value = currentBooks
        saveBooks()

        // 删除阅读进度
        deleteReadingProgress(bookId)
    }

    override suspend fun saveReadingProgress(progress: ReadingProgress) {
        withContext(Dispatchers.IO) {
            try {
                val progressMap = loadProgressMap().toMutableMap()
                progressMap[progress.bookId] = progress
                val jsonString = json.encodeToString(progressMap)
                progressFile.writeText(jsonString)
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    override suspend fun getReadingProgress(bookId: String): ReadingProgress? {
        return try {
            loadProgressMap()[bookId]
        } catch (e: Exception) {
            null
        }
    }

    private fun loadProgressMap(): Map<String, ReadingProgress> {
        return try {
            if (progressFile.exists()) {
                val jsonString = progressFile.readText()
                json.decodeFromString(jsonString)
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private suspend fun deleteReadingProgress(bookId: String) {
        withContext(Dispatchers.IO) {
            try {
                val progressMap = loadProgressMap().toMutableMap()
                progressMap.remove(bookId)
                val jsonString = json.encodeToString(progressMap)
                progressFile.writeText(jsonString)
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    override suspend fun saveReadingSettings(settings: ReadingSettings) {
        withContext(Dispatchers.IO) {
            try {
                val jsonString = json.encodeToString(settings)
                settingsFile.writeText(jsonString)
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    override suspend fun getReadingSettings(): ReadingSettings {
        return try {
            if (settingsFile.exists()) {
                val jsonString = settingsFile.readText()
                json.decodeFromString(jsonString)
            } else {
                ReadingSettings()
            }
        } catch (e: Exception) {
            ReadingSettings()
        }
    }
}

