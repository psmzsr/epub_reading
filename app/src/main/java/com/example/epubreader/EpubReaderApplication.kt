package com.example.epubreader

import android.app.Application
import android.content.Context
import com.example.epubreader.data.repository.BookRepository
import com.example.epubreader.data.repository.BookRepositoryImpl
import com.example.epubreader.data.repository.EpubRepository
import com.example.epubreader.data.repository.EpubRepositoryImpl

/**
 * 应用入口：在进程启动时创建全局依赖容器。
 */
class EpubReaderApplication : Application() {

    /** 轻量依赖容器，供 ViewModel/页面统一获取仓库实例。 */
    lateinit var container: AppContainer
        private set

    /**
     * 初始化应用级容器。
     */
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * 简化版 DI 容器：按需懒加载仓库，避免引入额外框架成本。
 */
class AppContainer(private val context: Context) {
    /** EPUB 解析数据源。 */
    val epubRepository: EpubRepository by lazy {
        EpubRepositoryImpl(context)
    }

    /** 书架与阅读进度数据源。 */
    val bookRepository: BookRepository by lazy {
        BookRepositoryImpl(context)
    }
}

