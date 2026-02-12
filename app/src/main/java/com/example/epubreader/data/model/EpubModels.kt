package com.example.epubreader.data.model

/**
 * EPUB解析相关的数据模型
 */

/**
 * EPUB元数据
 */
data class EpubMetadata(
    val title: String,
    val author: String,
    val language: String,
    val publisher: String,
    val description: String,
    val publishDate: String,
    val identifier: String
)

/**
 * EPUB spine项目（阅读顺序）
 */
data class SpineItem(
    val id: String,
    val href: String,
    val mediaType: String,
    val title: String
)

/**
 * EPUB目录项
 */
data class NavPoint(
    val id: String,
    val playOrder: Int,
    val title: String,
    val href: String,
    val level: Int,
    val children: List<NavPoint> = emptyList()
)

/**
 * 完整的EPUB结构
 */
data class EpubBook(
    val metadata: EpubMetadata,
    val spine: List<SpineItem>,
    val toc: List<NavPoint>,
    val coverImagePath: String? = null,
    val extractedPath: String
)

/**
 * 解析结果
 */
sealed class ParseResult<out T> {
    data class Success<T>(val data: T) : ParseResult<T>()
    data class Error(val message: String, val exception: Throwable? = null) : ParseResult<Nothing>()
}

