package com.example.epubreader.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import java.io.File
import java.io.FileOutputStream

/**
 * 图像处理工具类
 */
object ImageUtils {

    /**
     * 加载图片并缩放到指定尺寸
     */
    fun loadImage(path: String, maxWidth: Int, maxHeight: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null

        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)

            val scaleFactor = minOf(
                options.outWidth / maxWidth,
                options.outHeight / maxHeight
            ).coerceAtLeast(1)

            options.apply {
                inJustDecodeBounds = false
                inSampleSize = scaleFactor
            }

            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 应用灰度滤镜（用于夜间模式）
     */
    fun applyGrayscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)

        val filter = ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = filter

        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    /**
     * 应用反色滤镜（用于阅读器背景）
     */
    fun applyInvertColors(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        val colorMatrix = ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        ))

        val filter = ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = filter

        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    /**
     * 模糊图片
     */
    fun blurImage(context: Context, input: Bitmap, radius: Float = 25f): Bitmap? {
        if (radius < 1f || radius > 25f) return input

        return try {
            val width = input.width
            val height = input.height

            val scaledDown = Bitmap.createScaledBitmap(input, width / 4, height / 4, true)

            val output = Bitmap.createBitmap(scaledDown)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val script = RenderScript.create(context)
                val blur = ScriptIntrinsicBlur.create(script, Element.U8_4(script))

                val inputAlloc = Allocation.createFromBitmap(script, scaledDown)
                val outputAlloc = Allocation.createFromBitmap(script, output)

                blur.setRadius(radius / 4)
                blur.setInput(inputAlloc)
                blur.forEach(outputAlloc)

                outputAlloc.copyTo(output)

                inputAlloc.destroy()
                outputAlloc.destroy()
                blur.destroy()
                script.destroy()
            } else {
                // 降级方案：使用简单的缩放模糊
                output
            }

            val result = Bitmap.createScaledBitmap(output, width, height, true)
            if (output != result) {
                output.recycle()
            }
            if (scaledDown != result) {
                scaledDown.recycle()
            }

            result
        } catch (e: Exception) {
            input
        }
    }

    /**
     * 保存图片到缓存目录
     */
    fun saveToCache(context: Context, bitmap: Bitmap, filename: String): String? {
        return try {
            val cacheDir = context.cacheDir
            val file = File(cacheDir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * 文件工具类
 */
object FileUtils {

    /**
     * 获取文件大小格式化字符串
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.1f GB", gb)
    }

    /**
     * 清理书籍的临时文件
     */
    fun cleanBookCache(bookPath: String) {
        val bookDir = File(bookPath)
        if (bookDir.exists()) {
            bookDir.deleteRecursively()
        }
    }

    /**
     * 获取EPUB文件的临时解压目录
     */
    fun getBookExtractDir(context: Context, bookId: String): File {
        val cacheDir = File(context.cacheDir, "books")
        cacheDir.mkdirs()
        return File(cacheDir, bookId)
    }
}

