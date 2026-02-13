package com.example.epubreader.domain.model

/** 面向 UI 的书籍展示模型。 */
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val filePath: String,
    val fileSize: String,
    val importTime: String,
    val lastReadTime: String,
    val currentChapter: Int,
    val totalChapters: Int,
    val readingProgress: Float
) {
    /** 是否已有阅读记录。 */
    val hasProgress: Boolean
        get() = readingProgress > 0f

    /** 进度百分比文案。 */
    val progressText: String
        get() = "${(readingProgress * 100).toInt()}%"
}

/** 阅读页状态。 */
data class ReadingState(
    val currentChapter: Int,
    val totalChapters: Int,
    val chapterTitle: String,
    val scrollPosition: Float,
    val isLoading: Boolean,
    val error: String?
) {
    companion object {
        /** 初始状态：加载中。 */
        fun initial(): ReadingState = ReadingState(
            currentChapter = 0,
            totalChapters = 0,
            chapterTitle = "",
            scrollPosition = 0f,
            isLoading = true,
            error = null
        )
    }

    /** 是否有可展示错误。 */
    val hasError: Boolean
        get() = !error.isNullOrBlank()

    /** 章节进度文案。 */
    val chapterInfo: String
        get() = "第 ${currentChapter + 1} 章 / 共 $totalChapters 章"
}

/** 阅读主题预设。 */
enum class ReaderTheme(val backgroundColor: Long, val textColor: Long) {
    LIGHT(0xFFFFFBF5, 0xFF1A1A1A),
    SEPIA(0xFFF5E6D3, 0xFF5B4636),
    DARK(0xFF121212, 0xFFE0E0E0),
    GREEN(0xFFE8F5E9, 0xFF1B5E20),
    NIGHT(0xFF1A1A2E, 0xFFB8B8D1)
}
