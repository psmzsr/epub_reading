package com.example.epubreader.data.repository

import com.example.epubreader.data.model.BookInfo
import com.example.epubreader.data.model.ReadingProgress
import com.example.epubreader.data.model.ReadingSettings
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<BookInfo>>
    suspend fun getBook(bookId: String): BookInfo?
    suspend fun addBook(book: BookInfo)
    suspend fun updateBook(book: BookInfo)
    suspend fun deleteBook(bookId: String)
    suspend fun saveReadingProgress(progress: ReadingProgress)
    suspend fun getReadingProgress(bookId: String): ReadingProgress?
    suspend fun saveReadingSettings(settings: ReadingSettings)
    suspend fun getReadingSettings(): ReadingSettings
}
