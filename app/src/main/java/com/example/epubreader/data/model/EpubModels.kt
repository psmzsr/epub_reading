package com.example.epubreader.data.model

/** EPUB 元数据。 */
data class EpubMetadata(
    val title: String,
    val author: String,
    val language: String,
    val publisher: String,
    val description: String,
    val publishDate: String,
    val identifier: String
)

/** spine 条目：定义阅读顺序。 */
data class SpineItem(
    val id: String,
    val href: String,
    val mediaType: String,
    val title: String
)

/** TOC 目录节点（支持树形层级）。 */
data class NavPoint(
    val id: String,
    val playOrder: Int,
    val title: String,
    val href: String,
    val level: Int,
    val children: List<NavPoint> = emptyList()
)

/** 完整 EPUB 解析结果。 */
data class EpubBook(
    val metadata: EpubMetadata,
    val spine: List<SpineItem>,
    val toc: List<NavPoint>,
    val coverImagePath: String? = null,
    val extractedPath: String
)

/** 统一解析返回结构。 */
sealed class ParseResult<out T> {
    data class Success<T>(val data: T) : ParseResult<T>()
    data class Error(val message: String, val exception: Throwable? = null) : ParseResult<Nothing>()
}
