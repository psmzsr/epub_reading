package com.example.epubreader.data.repository

import com.example.epubreader.data.model.BookInfo
import com.example.epubreader.data.model.ReadingProgress
import com.example.epubreader.data.model.ReadingSettings
import kotlinx.coroutines.flow.Flow

/** 书架与阅读状态的数据访问接口。 */
interface BookRepository {
    /** 监听书架数据变化（用于实时刷新 UI）。 */
    fun getAllBooks(): Flow<List<BookInfo>>

    /** 根据书籍 id 查询单本书信息。 */
    suspend fun getBook(bookId: String): BookInfo?

    /** 新增书籍到书架。 */
    suspend fun addBook(book: BookInfo)

    /** 更新书籍信息（当前章节/进度/最近阅读时间等）。 */
    suspend fun updateBook(book: BookInfo)

    /** 删除书籍以及关联缓存/进度。 */
    suspend fun deleteBook(bookId: String)

    /** 保存单本书的阅读进度。 */
    suspend fun saveReadingProgress(progress: ReadingProgress)

    /** 读取单本书的阅读进度。 */
    suspend fun getReadingProgress(bookId: String): ReadingProgress?

    /** 保存全局阅读设置。 */
    suspend fun saveReadingSettings(settings: ReadingSettings)

    /** 获取全局阅读设置。 */
    suspend fun getReadingSettings(): ReadingSettings
}
