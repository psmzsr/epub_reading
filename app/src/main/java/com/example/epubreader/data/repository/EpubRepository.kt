package com.example.epubreader.data.repository

import android.net.Uri
import com.example.epubreader.data.model.EpubBook
import com.example.epubreader.data.model.ParseResult

/** EPUB 解析数据访问接口。 */
interface EpubRepository {
    /** 从内容 Uri 解析 EPUB（文件选择器导入场景）。 */
    suspend fun parseEpub(uri: Uri): ParseResult<EpubBook>

    /** 从本地路径解析 EPUB（缓存目录重开书籍场景）。 */
    suspend fun parseEpubFromPath(path: String): ParseResult<EpubBook>
}
