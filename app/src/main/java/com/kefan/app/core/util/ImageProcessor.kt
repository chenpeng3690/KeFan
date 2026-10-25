package com.kefan.app.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 教案照片统一压缩：长边 1600、JPEG 85，并按 EXIF 修正方向。
 */
object ImageProcessor {

    private const val MAX_EDGE = 1600
    private const val JPEG_QUALITY = 85
    private var sequence = 0

    suspend fun compressToFile(
        context: Context,
        source: Uri,
        targetDir: File
    ): File = withContext(Dispatchers.IO) {
        targetDir.mkdirs()
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("无法读取图片")
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE)
        }
        val decoded = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IOException("图片解码失败")

        val orientation = runCatching {
            resolver.openInputStream(source)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val rotated = rotate(decoded, orientation)
        val scale = MAX_EDGE.toFloat() / max(rotated.width, rotated.height)
        val finalBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                rotated,
                (rotated.width * scale).roundToInt().coerceAtLeast(1),
                (rotated.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            rotated
        }

        sequence += 1
        val target = File(
            targetDir,
            "img_${System.currentTimeMillis()}_${sequence}.jpg"
        )
        FileOutputStream(target).use { output ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }
        target
    }

    fun loadThumbnail(context: Context, uri: Uri, maxPx: Int = 480): Bitmap? {
        return runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxPx)
            }
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull()
    }

    /**
     * 按归一化坐标 [0,1] 裁剪题目区域，用于手动匡题。
     */
    suspend fun cropToFile(
        context: Context,
        source: Uri,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        targetDir: File
    ): File = withContext(Dispatchers.IO) {
        targetDir.mkdirs()
        val bitmap = loadThumbnail(context, source, 2400) ?: throw IOException("图片解码失败")

        val safeLeft = left.coerceIn(0f, 1f)
        val safeTop = top.coerceIn(0f, 1f)
        val safeWidth = width.coerceIn(0f, 1f - safeLeft)
        val safeHeight = height.coerceIn(0f, 1f - safeTop)
        if (safeWidth <= 0f || safeHeight <= 0f) throw IOException("框选区域太小")

        val x = (safeLeft * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val y = (safeTop * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val w = (safeWidth * bitmap.width).roundToInt().coerceIn(1, bitmap.width - x)
        val h = (safeHeight * bitmap.height).roundToInt().coerceIn(1, bitmap.height - y)

        val cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
        sequence += 1
        val target = File(
            targetDir,
            "crop_${System.currentTimeMillis()}_${sequence}.jpg"
        )
        FileOutputStream(target).use { output ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 88, output)
        }
        target
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= target) {
            sample *= 2
        }
        return sample
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }
}
