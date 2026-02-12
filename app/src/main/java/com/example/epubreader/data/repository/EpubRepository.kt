package com.example.epubreader.data.repository

import android.net.Uri
import com.example.epubreader.data.model.EpubBook
import com.example.epubreader.data.model.ParseResult

interface EpubRepository {
    suspend fun parseEpub(uri: Uri): ParseResult<EpubBook>
    suspend fun parseEpubFromPath(path: String): ParseResult<EpubBook>
}
