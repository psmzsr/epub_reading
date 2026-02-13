package com.example.epubreader.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import java.io.File
import java.io.FileOutputStream

/** 图像相关工具。 */
object ImageUtils {

    /**
     * 按目标尺寸加载并下采样图片，避免大图直接解码导致内存峰值过高。
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
        } catch (_: Exception) {
            null
        }
    }

    /** 灰度滤镜。 */
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

    /** 反色滤镜。 */
    fun applyInvertColors(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        val colorMatrix = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val filter = ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = filter

        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    /**
     * 轻量模糊：先缩小再模糊再放大，平衡效果与性能。
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
            }

            val result = Bitmap.createScaledBitmap(output, width, height, true)
            if (output != result) output.recycle()
            if (scaledDown != result) scaledDown.recycle()

            result
        } catch (_: Exception) {
            input
        }
    }

    /** 将图片写入应用缓存目录。 */
    fun saveToCache(context: Context, bitmap: Bitmap, filename: String): String? {
        return try {
            val cacheDir = context.cacheDir
            val file = File(cacheDir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}

/** 文件相关工具。 */
object FileUtils {

    /** 将字节大小格式化为可读字符串。 */
    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.1f GB", gb)
    }

    /** 删除单本书籍的解压缓存目录。 */
    fun cleanBookCache(bookPath: String) {
        val bookDir = File(bookPath)
        if (bookDir.exists()) {
            bookDir.deleteRecursively()
        }
    }

    /** 获取书籍解压目录路径。 */
    fun getBookExtractDir(context: Context, bookId: String): File {
        val cacheDir = File(context.cacheDir, "books")
        cacheDir.mkdirs()
        return File(cacheDir, bookId)
    }
}
