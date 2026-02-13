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
 * 基于本地 JSON 文件的仓库实现。
 */
class BookRepositoryImpl(
    private val context: Context
) : BookRepository {

    private val json = Json { ignoreUnknownKeys = true }

    /** 书架列表文件。 */
    private val booksFile: File
        get() = File(context.filesDir, "books.json")

    /** 阅读进度文件。 */
    private val progressFile: File
        get() = File(context.filesDir, "reading_progress.json")

    /** 阅读设置文件。 */
    private val settingsFile: File
        get() = File(context.filesDir, "reading_settings.json")

    private val _booksFlow = MutableStateFlow<List<BookInfo>>(emptyList())

    init {
        loadBooks()
    }

    /**
     * 从本地文件加载书架数据到内存流。
     */
    private fun loadBooks() {
        try {
            if (booksFile.exists()) {
                val jsonString = booksFile.readText()
                val books = json.decodeFromString<List<BookInfo>>(jsonString)
                _booksFlow.value = books
            }
        } catch (_: Exception) {
            _booksFlow.value = emptyList()
        }
    }

    /**
     * 将当前内存中的书架列表写回文件。
     */
    private suspend fun saveBooks() {
        withContext(Dispatchers.IO) {
            try {
                val jsonString = json.encodeToString(_booksFlow.value)
                booksFile.writeText(jsonString)
            } catch (_: Exception) {
                // 按需接入统一日志系统。
            }
        }
    }

    /**
     * 返回书架数据流。
     */
    override fun getAllBooks(): Flow<List<BookInfo>> = _booksFlow.asStateFlow()

    /**
     * 根据 bookId 获取书籍。
     */
    override suspend fun getBook(bookId: String): BookInfo? {
        return _booksFlow.value.find { it.id == bookId }
    }

    /**
     * 新增书籍并落盘。
     */
    override suspend fun addBook(book: BookInfo) {
        val currentBooks = _booksFlow.value.toMutableList()
        currentBooks.add(book)
        _booksFlow.value = currentBooks
        saveBooks()
    }

    /**
     * 更新书籍并落盘。
     */
    override suspend fun updateBook(book: BookInfo) {
        val currentBooks = _booksFlow.value.toMutableList()
        val index = currentBooks.indexOfFirst { it.id == book.id }
        if (index != -1) {
            currentBooks[index] = book
            _booksFlow.value = currentBooks
            saveBooks()
        }
    }

    /**
     * 删除书籍、缓存目录和对应阅读进度。
     */
    override suspend fun deleteBook(bookId: String) {
        val book = getBook(bookId)
        if (book != null) {
            FileUtils.cleanBookCache(book.filePath)
        }

        val currentBooks = _booksFlow.value.filterNot { it.id == bookId }
        _booksFlow.value = currentBooks
        saveBooks()
        deleteReadingProgress(bookId)
    }

    /**
     * 保存单本书阅读进度。
     */
    override suspend fun saveReadingProgress(progress: ReadingProgress) {
        withContext(Dispatchers.IO) {
            try {
                val progressMap = loadProgressMap().toMutableMap()
                progressMap[progress.bookId] = progress
                val jsonString = json.encodeToString(progressMap)
                progressFile.writeText(jsonString)
            } catch (_: Exception) {
                // 按需接入统一日志系统。
            }
        }
    }

    /**
     * 读取单本书阅读进度。
     */
    override suspend fun getReadingProgress(bookId: String): ReadingProgress? {
        return try {
            loadProgressMap()[bookId]
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 读取全部阅读进度映射。
     */
    private fun loadProgressMap(): Map<String, ReadingProgress> {
        return try {
            if (progressFile.exists()) {
                val jsonString = progressFile.readText()
                json.decodeFromString(jsonString)
            } else {
                emptyMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * 删除某本书的阅读进度记录。
     */
    private suspend fun deleteReadingProgress(bookId: String) {
        withContext(Dispatchers.IO) {
            try {
                val progressMap = loadProgressMap().toMutableMap()
                progressMap.remove(bookId)
                val jsonString = json.encodeToString(progressMap)
                progressFile.writeText(jsonString)
            } catch (_: Exception) {
                // 按需接入统一日志系统。
            }
        }
    }

    /**
     * 保存全局阅读设置。
     */
    override suspend fun saveReadingSettings(settings: ReadingSettings) {
        withContext(Dispatchers.IO) {
            try {
                val jsonString = json.encodeToString(settings)
                settingsFile.writeText(jsonString)
            } catch (_: Exception) {
                // 按需接入统一日志系统。
            }
        }
    }

    /**
     * 读取全局阅读设置。
     */
    override suspend fun getReadingSettings(): ReadingSettings {
        return try {
            if (settingsFile.exists()) {
                val jsonString = settingsFile.readText()
                json.decodeFromString(jsonString)
            } else {
                ReadingSettings()
            }
        } catch (_: Exception) {
            ReadingSettings()
        }
    }
}
