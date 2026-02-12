package com.example.epubreader.data.model

import kotlinx.serialization.Serializable

/**
 * EPUB书籍信息数据类
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
 * 阅读设置
 */
@Serializable
data class ReadingSettings(
    val fontSize: Int = 18,
    val brightness: Float = -1f,
    val isNightMode: Boolean = false,
    val fontFamily: String = "default"
)

/**
 * 阅读进度
 */
@Serializable
data class ReadingProgress(
    val bookId: String,
    val currentChapter: Int,
    val scrollPosition: Float,
    val timestamp: Long
)

