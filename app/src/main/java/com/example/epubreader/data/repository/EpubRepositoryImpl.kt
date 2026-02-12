package com.example.epubreader.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.epubreader.core.epub.EpubParser
import com.example.epubreader.data.model.EpubBook
import com.example.epubreader.data.model.ParseResult
import java.io.File

class EpubRepositoryImpl(
    private val context: Context
) : EpubRepository {

    private val tag = "EpubDebug"
    private val parser = EpubParser()

    override suspend fun parseEpub(uri: Uri): ParseResult<EpubBook> {
        Log.d(tag, "parseEpub(uri) start: $uri")

        val inputStream = context.contentResolver.openInputStream(uri)
            ?: run {
                Log.e(tag, "parseEpub(uri) failed: openInputStream returns null")
                return ParseResult.Error("无法打开文件")
            }

        val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.epub")
        runCatching {
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }.onFailure { error ->
            Log.e(tag, "parseEpub(uri) failed while copying temp file", error)
            return ParseResult.Error("读取文件失败：${error.message}", error)
        }

        Log.d(tag, "parseEpub(uri) temp file: ${tempFile.absolutePath}")
        val result = parser.parseEpub(tempFile)
        when (result) {
            is ParseResult.Success -> {
                Log.d(tag, "parseEpub(uri) success: spine=${result.data.spine.size}, toc=${result.data.toc.size}")
            }

            is ParseResult.Error -> {
                Log.e(tag, "parseEpub(uri) parse error: ${result.message}", result.exception)
            }
        }
        return result
    }

    override suspend fun parseEpubFromPath(path: String): ParseResult<EpubBook> {
        Log.d(tag, "parseEpubFromPath start: $path")
        val file = File(path)
        if (!file.exists()) {
            Log.e(tag, "parseEpubFromPath failed: file not found")
            return ParseResult.Error("文件不存在：$path")
        }

        val result = if (file.isDirectory) {
            Log.d(tag, "parseEpubFromPath detected extracted directory")
            parser.parseExtractedEpub(file)
        } else {
            parser.parseEpub(file)
        }

        when (result) {
            is ParseResult.Success -> {
                Log.d(tag, "parseEpubFromPath success: spine=${result.data.spine.size}, toc=${result.data.toc.size}")
            }

            is ParseResult.Error -> {
                Log.e(tag, "parseEpubFromPath parse error: ${result.message}", result.exception)
            }
        }
        return result
    }
}
