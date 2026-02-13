package com.example.epubreader.domain.usecase

import android.net.Uri
import com.example.epubreader.core.util.FileUtils
import com.example.epubreader.data.model.BookInfo
import com.example.epubreader.data.model.EpubBook
import com.example.epubreader.data.model.ParseResult
import com.example.epubreader.data.model.ReadingProgress
import com.example.epubreader.data.repository.BookRepository
import com.example.epubreader.data.repository.EpubRepository
import com.example.epubreader.domain.model.Book
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 导入一本 EPUB 到书架。
 */
class ImportBookUseCase(
    private val epubRepository: EpubRepository,
    private val bookRepository: BookRepository
) {
    /**
     * 解析文件并写入书架，返回领域层 Book。
     */
    suspend operator fun invoke(uri: Uri, fileSize: Long): Result<Book> {
        return try {
            when (val parseResult = epubRepository.parseEpub(uri)) {
                is ParseResult.Success<*> -> {
                    val epubBook = parseResult.data as EpubBook
                    val bookInfo = BookInfo(
                        id = UUID.randomUUID().toString(),
                        title = epubBook.metadata.title,
                        author = epubBook.metadata.author,
                        coverPath = epubBook.coverImagePath,
                        filePath = epubBook.extractedPath,
                        fileSize = fileSize,
                        importTime = System.currentTimeMillis(),
                        totalChapters = epubBook.spine.size
                    )
                    bookRepository.addBook(bookInfo)
                    Result.success(bookInfo.toDomain())
                }

                is ParseResult.Error -> Result.failure(Exception(parseResult.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * 获取书架列表。
 */
class GetBooksUseCase(
    private val bookRepository: BookRepository
) {
    /**
     * 按最近阅读时间倒序输出 UI 需要的 Book 列表。
     */
    operator fun invoke(): Flow<List<Book>> {
        return bookRepository.getAllBooks().map { books ->
            books.sortedByDescending { it.lastReadTime }.map { it.toDomain() }
        }
    }
}

/**
 * 获取单本书信息。
 */
class GetBookUseCase(
    private val bookRepository: BookRepository
) {
    /**
     * 根据 bookId 查询书籍并转换为领域模型。
     */
    suspend operator fun invoke(bookId: String): Book? {
        return bookRepository.getBook(bookId)?.toDomain()
    }
}

/**
 * 删除书籍。
 */
class DeleteBookUseCase(
    private val bookRepository: BookRepository
) {
    /**
     * 删除指定书籍并返回执行结果。
     */
    suspend operator fun invoke(bookId: String): Result<Unit> {
        return try {
            bookRepository.deleteBook(bookId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * 更新阅读进度（书架汇总进度 + 细粒度章节进度）。
 */
class UpdateReadingProgressUseCase(
    private val bookRepository: BookRepository
) {
    /**
     * 保存章节索引与章节内滚动位置。
     */
    suspend operator fun invoke(
        bookId: String,
        currentChapter: Int,
        scrollPosition: Float
    ) {
        val book = bookRepository.getBook(bookId)
        book?.let {
            val progress = if (it.totalChapters > 0) {
                (currentChapter.toFloat() + scrollPosition) / it.totalChapters.toFloat()
            } else {
                0f
            }

            bookRepository.updateBook(
                it.copy(
                    currentChapter = currentChapter,
                    readingProgress = progress.coerceIn(0f, 1f),
                    lastReadTime = System.currentTimeMillis()
                )
            )
        }

        bookRepository.saveReadingProgress(
            ReadingProgress(
                bookId = bookId,
                currentChapter = currentChapter,
                scrollPosition = scrollPosition,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}

/**
 * 数据层模型转换为领域层模型。
 */
private fun BookInfo.toDomain(): Book {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val importTimeStr = dateFormat.format(Date(importTime))
    val lastReadTimeStr = if (lastReadTime > 0) {
        dateFormat.format(Date(lastReadTime))
    } else {
        "从未阅读"
    }

    return Book(
        id = id,
        title = title,
        author = author.ifBlank { "未知作者" },
        coverPath = coverPath,
        filePath = filePath,
        fileSize = FileUtils.formatFileSize(fileSize),
        importTime = importTimeStr,
        lastReadTime = lastReadTimeStr,
        currentChapter = currentChapter,
        totalChapters = totalChapters,
        readingProgress = readingProgress
    )
}
