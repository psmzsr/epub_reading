package com.example.epubreader.domain.usecase

import com.example.epubreader.core.epub.EpubParser
import com.example.epubreader.data.model.EpubBook
import com.example.epubreader.data.model.ParseResult
import com.example.epubreader.data.repository.BookRepository
import com.example.epubreader.data.repository.EpubRepository
import com.example.epubreader.domain.model.ReadingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 打开书籍并返回阅读初始状态流。
 */
class OpenBookUseCase(
    private val epubRepository: EpubRepository
) {
    /**
     * 从路径解析 EPUB，并发出加载态和结果态。
     */
    suspend operator fun invoke(bookPath: String): Flow<ReadingState> = flow {
        emit(ReadingState.initial())

        when (val parseResult = epubRepository.parseEpubFromPath(bookPath)) {
            is ParseResult.Success<*> -> {
                val epubBook = parseResult.data as EpubBook
                emit(
                    ReadingState(
                        currentChapter = 0,
                        totalChapters = epubBook.spine.size,
                        chapterTitle = "第 1 章",
                        scrollPosition = 0f,
                        isLoading = false,
                        error = null
                    )
                )
            }

            is ParseResult.Error -> {
                emit(
                    ReadingState.initial().copy(
                        isLoading = false,
                        error = parseResult.message
                    )
                )
            }
        }
    }
}

/**
 * 获取章节正文。
 */
class GetChapterContentUseCase {
    private val parser = EpubParser()

    /**
     * 返回指定章节纯文本内容。
     */
    suspend operator fun invoke(epubBook: EpubBook, chapterIndex: Int): String? {
        return parser.getChapterContent(epubBook, chapterIndex)
    }
}

/**
 * 保存阅读设置。
 */
class SaveReadingSettingsUseCase(
    private val bookRepository: BookRepository
) {
    /**
     * 对字号和夜间模式进行增量更新。
     */
    suspend operator fun invoke(
        fontSize: Int? = null,
        isNightMode: Boolean? = null
    ) {
        val current = bookRepository.getReadingSettings()
        val next = current.copy(
            fontSize = fontSize ?: current.fontSize,
            isNightMode = isNightMode ?: current.isNightMode
        )
        bookRepository.saveReadingSettings(next)
    }
}

/**
 * 获取阅读设置。
 */
class GetReadingSettingsUseCase(
    private val bookRepository: BookRepository
) {
    /**
     * 读取全局阅读设置。
     */
    suspend operator fun invoke() = bookRepository.getReadingSettings()
}

/**
 * 获取阅读进度。
 */
class GetReadingProgressUseCase(
    private val bookRepository: BookRepository
) {
    /**
     * 读取单本书进度。
     */
    suspend operator fun invoke(bookId: String) = bookRepository.getReadingProgress(bookId)
}
