package com.example.epubreader.domain.usecase

import com.example.epubreader.core.epub.EpubParser
import com.example.epubreader.data.model.EpubBook
import com.example.epubreader.data.model.ParseResult
import com.example.epubreader.data.repository.BookRepository
import com.example.epubreader.data.repository.EpubRepository
import com.example.epubreader.domain.model.ReadingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class OpenBookUseCase(
    private val epubRepository: EpubRepository
) {
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

class GetChapterContentUseCase {
    private val parser = EpubParser()

    suspend operator fun invoke(epubBook: EpubBook, chapterIndex: Int): String? {
        return parser.getChapterContent(epubBook, chapterIndex)
    }
}

class SaveReadingSettingsUseCase(
    private val bookRepository: BookRepository
) {
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

class GetReadingSettingsUseCase(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke() = bookRepository.getReadingSettings()
}

class GetReadingProgressUseCase(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(bookId: String) = bookRepository.getReadingProgress(bookId)
}
