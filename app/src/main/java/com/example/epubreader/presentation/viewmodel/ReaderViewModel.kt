package com.example.epubreader.presentation.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubreader.EpubReaderApplication
import com.example.epubreader.core.epub.EpubParser
import com.example.epubreader.data.model.EpubBook
import com.example.epubreader.data.model.NavPoint
import com.example.epubreader.data.model.ParseResult
import com.example.epubreader.domain.model.ReadingState
import com.example.epubreader.domain.model.ReaderTheme
import com.example.epubreader.domain.usecase.GetReadingProgressUseCase
import com.example.epubreader.domain.usecase.GetReadingSettingsUseCase
import com.example.epubreader.domain.usecase.SaveReadingSettingsUseCase
import com.example.epubreader.domain.usecase.UpdateReadingProgressUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "EpubDebug"
    private val container = (application as EpubReaderApplication).container
    private val epubRepository = container.epubRepository
    private val bookRepository = container.bookRepository

    private val getReadingSettingsUseCase = GetReadingSettingsUseCase(bookRepository)
    private val saveReadingSettingsUseCase = SaveReadingSettingsUseCase(bookRepository)
    private val updateReadingProgressUseCase = UpdateReadingProgressUseCase(bookRepository)
    private val getReadingProgressUseCase = GetReadingProgressUseCase(bookRepository)
    private val parser = EpubParser()

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentBook: EpubBook? = null
    private var readableChapterIndices: List<Int> = emptyList()
    private val chapterContentCache = mutableMapOf<Int, String>()
    private val chapterScrollMemory = mutableMapOf<Int, Float>()
    private var lastAutoSavedChapter = -1
    private var lastAutoSavedScroll = -1f
    private var lastAutoSaveAt = 0L

    fun openBook(bookPath: String, bookId: String) {
        viewModelScope.launch {
            Log.d(tag, "openBook start: bookId=$bookId, bookPath=$bookPath")
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                currentBookId = bookId
            )

            val settings = getReadingSettingsUseCase()
            _uiState.value = _uiState.value.copy(
                fontSize = settings.fontSize,
                isNightMode = settings.isNightMode
            )
            Log.d(tag, "openBook settings: fontSize=${settings.fontSize}, night=${settings.isNightMode}")

            val savedProgress = getReadingProgressUseCase(bookId)
            Log.d(tag, "openBook savedProgress: $savedProgress")

            when (val parseResult = epubRepository.parseEpubFromPath(bookPath)) {
                is ParseResult.Success<*> -> {
                    val epubBook = parseResult.data as EpubBook
                    currentBook = epubBook
                    chapterContentCache.clear()
                    chapterScrollMemory.clear()

                    readableChapterIndices = buildReadableChapterIndex(epubBook)
                    if (readableChapterIndices.isEmpty()) {
                        Log.e(tag, "openBook failed: no readable chapter")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            readingState = ReadingState.initial().copy(
                                isLoading = false,
                                error = "未找到可阅读章节，请更换 EPUB 文件重试。"
                            ),
                            chapterTitles = emptyList()
                        )
                        return@launch
                    }

                    val chapterTitles = buildChapterTitles(epubBook, readableChapterIndices)
                    val chapterIndex = savedProgress?.currentChapter
                        ?.coerceIn(0, readableChapterIndices.lastIndex)
                        ?: 0
                    val initialScroll = savedProgress?.scrollPosition?.coerceIn(0f, 1f) ?: 0f
                    chapterScrollMemory[chapterIndex] = initialScroll
                    val chapterTitle = chapterTitles.getOrNull(chapterIndex) ?: "第${chapterIndex + 1}章"

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        chapterTitles = chapterTitles,
                        readingState = ReadingState(
                            currentChapter = chapterIndex,
                            totalChapters = readableChapterIndices.size,
                            chapterTitle = chapterTitle,
                            scrollPosition = initialScroll,
                            isLoading = false,
                            error = null
                        )
                    )

                    Log.d(
                        tag,
                        "openBook ui ready: chapter=$chapterIndex, total=${readableChapterIndices.size}, title=$chapterTitle"
                    )
                }

                is ParseResult.Error -> {
                    Log.e(tag, "openBook parse failed: ${parseResult.message}", parseResult.exception)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        readingState = ReadingState.initial().copy(
                            isLoading = false,
                            error = parseResult.message
                        )
                    )
                }
            }
        }
    }

    fun goToChapter(chapterIndex: Int) {
        val state = _uiState.value.readingState
        if (chapterIndex !in 0 until state.totalChapters) {
            Log.w(tag, "goToChapter ignored: index=$chapterIndex, total=${state.totalChapters}")
            return
        }

        chapterScrollMemory[state.currentChapter] = state.scrollPosition
        persistProgress(
            chapterIndex = state.currentChapter,
            scrollPosition = state.scrollPosition,
            reason = "chapter_switch"
        )

        val title = _uiState.value.chapterTitles.getOrNull(chapterIndex) ?: "第${chapterIndex + 1}章"
        val restoredScroll = chapterScrollMemory[chapterIndex] ?: 0f
        _uiState.value = _uiState.value.copy(
            readingState = state.copy(
                currentChapter = chapterIndex,
                chapterTitle = title,
                scrollPosition = restoredScroll
            )
        )
        Log.d(tag, "goToChapter: index=$chapterIndex, title=$title, restoredScroll=$restoredScroll")
    }

    fun nextChapter() {
        val state = _uiState.value.readingState
        if (state.currentChapter < state.totalChapters - 1) {
            goToChapter(state.currentChapter + 1)
        } else {
            Log.d(tag, "nextChapter ignored: already at last chapter")
        }
    }

    fun previousChapter() {
        val state = _uiState.value.readingState
        if (state.currentChapter > 0) {
            goToChapter(state.currentChapter - 1)
        } else {
            Log.d(tag, "previousChapter ignored: already at first chapter")
        }
    }

    fun updateScrollPosition(position: Float) {
        val normalized = position.coerceIn(0f, 1f)
        val state = _uiState.value.readingState
        _uiState.value = _uiState.value.copy(
            readingState = state.copy(scrollPosition = normalized)
        )
        chapterScrollMemory[state.currentChapter] = normalized
        maybePersistProgress(state.currentChapter, normalized)
    }

    fun increaseFontSize() {
        val current = _uiState.value.fontSize
        if (current >= 32) return
        val next = current + 2
        _uiState.value = _uiState.value.copy(fontSize = next)
        saveFontSize(next)
    }

    fun decreaseFontSize() {
        val current = _uiState.value.fontSize
        if (current <= 12) return
        val next = current - 2
        _uiState.value = _uiState.value.copy(fontSize = next)
        saveFontSize(next)
    }

    fun toggleNightMode() {
        val next = !_uiState.value.isNightMode
        _uiState.value = _uiState.value.copy(isNightMode = next)
        viewModelScope.launch {
            saveReadingSettingsUseCase(isNightMode = next)
            Log.d(tag, "toggleNightMode: $next")
        }
    }

    fun selectTheme(theme: ReaderTheme) {
        _uiState.value = _uiState.value.copy(selectedTheme = theme)
        Log.d(tag, "selectTheme: $theme")
    }

    private fun saveFontSize(size: Int) {
        viewModelScope.launch {
            saveReadingSettingsUseCase(fontSize = size)
            Log.d(tag, "saveFontSize: $size")
        }
    }

    fun saveProgress() {
        val state = _uiState.value.readingState
        chapterScrollMemory[state.currentChapter] = state.scrollPosition
        persistProgress(
            chapterIndex = state.currentChapter,
            scrollPosition = state.scrollPosition,
            reason = "manual"
        )
    }

    fun setBookId(bookId: String) {
        _uiState.value = _uiState.value.copy(currentBookId = bookId)
    }

    fun getCurrentChapterContent(): String {
        return getChapterContent(_uiState.value.readingState.currentChapter)
    }

    fun getChapterContent(chapterIndex: Int): String {
        val book = currentBook ?: run {
            Log.w(tag, "getChapterContent: currentBook is null")
            return ""
        }

        val rawIndex = readableChapterIndices.getOrNull(chapterIndex) ?: run {
            Log.w(tag, "getChapterContent: logical index out of range, index=$chapterIndex")
            return ""
        }

        chapterContentCache[rawIndex]?.let { return it }

        val content = parser.getChapterContent(book, rawIndex).orEmpty()
        if (content.isNotBlank()) {
            chapterContentCache[rawIndex] = content
        }
        Log.d(tag, "getChapterContent: logical=$chapterIndex, raw=$rawIndex, chars=${content.length}")
        return content
    }

    private fun maybePersistProgress(chapterIndex: Int, scrollPosition: Float) {
        val now = System.currentTimeMillis()
        val chapterChanged = chapterIndex != lastAutoSavedChapter
        val scrollDelta = abs(scrollPosition - lastAutoSavedScroll)
        val enoughTimePassed = now - lastAutoSaveAt >= 2000L
        if (!chapterChanged && scrollDelta < 0.05f && !enoughTimePassed) return

        persistProgress(chapterIndex, scrollPosition, reason = "auto")
        lastAutoSavedChapter = chapterIndex
        lastAutoSavedScroll = scrollPosition
        lastAutoSaveAt = now
    }

    private fun persistProgress(chapterIndex: Int, scrollPosition: Float, reason: String) {
        val bookId = _uiState.value.currentBookId ?: return
        viewModelScope.launch {
            updateReadingProgressUseCase(
                bookId = bookId,
                currentChapter = chapterIndex,
                scrollPosition = scrollPosition
            )
            Log.d(
                tag,
                "persistProgress($reason): bookId=$bookId, chapter=$chapterIndex, scroll=$scrollPosition"
            )
        }
    }

    private fun buildReadableChapterIndex(epubBook: EpubBook): List<Int> {
        val rawIndices = epubBook.spine.indices.toList()
        if (rawIndices.isEmpty()) return emptyList()

        val tocEntries = flattenToc(epubBook.toc)
        if (tocEntries.isEmpty()) {
            Log.d(tag, "buildReadableChapterIndex: toc empty, use spine directly size=${rawIndices.size}")
            return rawIndices
        }

        val spineHrefToIndex = mutableMapOf<String, Int>()
        rawIndices.forEach { rawIndex ->
            val href = normalizeHref(epubBook.spine[rawIndex].href)
            if (href.isNotBlank() && !spineHrefToIndex.containsKey(href)) {
                spineHrefToIndex[href] = rawIndex
            }

            val fileName = href.substringAfterLast('/')
            if (fileName.isNotBlank() && !spineHrefToIndex.containsKey(fileName)) {
                spineHrefToIndex[fileName] = rawIndex
            }
        }

        val mappedByToc = tocEntries.mapNotNull { entry ->
            val href = normalizeHref(entry.href)
            spineHrefToIndex[href] ?: spineHrefToIndex[href.substringAfterLast('/')]
        }.distinct()

        val result = if (mappedByToc.isNotEmpty()) mappedByToc else rawIndices
        Log.d(
            tag,
            "buildReadableChapterIndex: raw=${rawIndices.size}, toc=${tocEntries.size}, matched=${mappedByToc.size}, final=${result.size}"
        )
        return result
    }

    private fun buildChapterTitles(epubBook: EpubBook, chapterMap: List<Int>): List<String> {
        val tocEntries = flattenToc(epubBook.toc)
        val tocTitleMap = buildTocTitleMap(tocEntries)

        val titleByRawIndex = mutableMapOf<Int, String>()
        val spineHrefToIndex = mutableMapOf<String, Int>()
        chapterMap.forEach { rawIndex ->
            val href = normalizeHref(epubBook.spine[rawIndex].href)
            if (href.isNotBlank() && !spineHrefToIndex.containsKey(href)) {
                spineHrefToIndex[href] = rawIndex
            }
            val fileName = href.substringAfterLast('/')
            if (fileName.isNotBlank() && !spineHrefToIndex.containsKey(fileName)) {
                spineHrefToIndex[fileName] = rawIndex
            }
        }
        tocEntries.forEach { entry ->
            val href = normalizeHref(entry.href)
            val rawIndex = spineHrefToIndex[href] ?: spineHrefToIndex[href.substringAfterLast('/')] ?: return@forEach
            if (entry.title.isNotBlank() && !looksLikeFileName(entry.title)) {
                titleByRawIndex.putIfAbsent(rawIndex, entry.title.trim())
            }
        }

        return chapterMap.mapIndexed { logicalIndex, rawIndex ->
            val spineItem = epubBook.spine[rawIndex]
            val href = normalizeHref(spineItem.href)
            val tocTitle = titleByRawIndex[rawIndex]
                ?: tocTitleMap[href]
                ?: tocTitleMap[href.substringAfterLast('/')]
            val preferred = tocTitle
                ?.takeIf { it.isNotBlank() && !looksLikeFileName(it) }
                ?: spineItem.title.takeIf { it.isNotBlank() && !looksLikeFileName(it) && !it.startsWith("item", ignoreCase = true) }
                ?: "第 ${logicalIndex + 1} 章"
            preferred.trim()
        }
    }

    private fun looksLikeFileName(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.endsWith(".xhtml") ||
            normalized.endsWith(".html") ||
            normalized.endsWith(".xml") ||
            normalized.contains("/") ||
            normalized.contains("\\")
    }

    private fun buildTocTitleMap(tocEntries: List<TocEntry>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        tocEntries.forEach { entry ->
            val href = normalizeHref(entry.href)
            if (href.isNotBlank() && entry.title.isNotBlank()) {
                result.putIfAbsent(href, entry.title)
                result.putIfAbsent(href.substringAfterLast('/'), entry.title)
            }
        }
        return result
    }

    private fun flattenToc(toc: List<NavPoint>): List<TocEntry> {
        val result = mutableListOf<TocEntry>()

        fun collect(nodes: List<NavPoint>) {
            nodes.forEach { node ->
                val href = normalizeHref(node.href)
                if (href.isNotBlank()) {
                    result += TocEntry(href = href, title = node.title.trim())
                }
                if (node.children.isNotEmpty()) {
                    collect(node.children)
                }
            }
        }

        collect(toc)
        return result.distinctBy { it.href }
    }

    private fun normalizeHref(href: String): String {
        return Uri.decode(href)
            .substringBefore('#')
            .removePrefix("./")
            .trim()
    }

    override fun onCleared() {
        saveProgress()
        super.onCleared()
    }
}

private data class TocEntry(
    val href: String,
    val title: String
)

data class ReaderUiState(
    val readingState: ReadingState = ReadingState.initial(),
    val fontSize: Int = 18,
    val isNightMode: Boolean = false,
    val selectedTheme: ReaderTheme = ReaderTheme.LIGHT,
    val isLoading: Boolean = true,
    val currentBookId: String? = null,
    val chapterTitles: List<String> = emptyList()
) {
    val hasContent: Boolean
        get() = readingState.totalChapters > 0

    val progressText: String
        get() = "${readingState.currentChapter + 1} / ${readingState.totalChapters}"
}
