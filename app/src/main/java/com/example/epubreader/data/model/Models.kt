package com.example.epubreader.data.model

import kotlinx.serialization.Serializable

/**
 * 书籍基础信息（持久化到 books.json）。
 */
@Serializable
data class BookInfo(
    val id: String,
    val title: String,
    val author: String,
    val coverPath: String? = null,
    val filePath: String,
    val fileSize: Long,
    val importTime: Long,
    val lastReadTime: Long = 0,
    val currentChapter: Int = 0,
    val readingProgress: Float = 0f,
    val totalChapters: Int = 0
)

/**
 * 阅读设置（全局级别，持久化到 reading_settings.json）。
 */
@Serializable
data class ReadingSettings(
    val fontSize: Int = 18,
    val brightness: Float = -1f,
    val isNightMode: Boolean = false,
    val fontFamily: String = "default"
)

/**
 * 阅读进度（按 bookId 粒度，持久化到 reading_progress.json）。
 */
@Serializable
data class ReadingProgress(
    val bookId: String,
    val currentChapter: Int,
    val scrollPosition: Float,
    val timestamp: Long
)
