package com.example.epubreader

import android.app.Application
import android.content.Context
import com.example.epubreader.data.repository.BookRepository
import com.example.epubreader.data.repository.BookRepositoryImpl
import com.example.epubreader.data.repository.EpubRepository
import com.example.epubreader.data.repository.EpubRepositoryImpl

class EpubReaderApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(private val context: Context) {
    val epubRepository: EpubRepository by lazy {
        EpubRepositoryImpl(context)
    }

    val bookRepository: BookRepository by lazy {
        BookRepositoryImpl(context)
    }
}

