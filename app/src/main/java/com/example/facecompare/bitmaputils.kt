package com.example.facecompare

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.ContentResolver

/**
 * 图片加载工具：从 URI 加载 Bitmap，自动压缩大图防止 OOM。
 */
object BitmapUtils {

    /** 最大边长的限制 */
    private const val MAX_DIMENSION = 1920

    /**
     * 从 Content URI 安全加载 Bitmap。
     * - 先读取尺寸，计算采样率
     * - 确保解码后不超过 MAX_DIMENSION
     * - 最终宽高取 8 的倍数（JPEG 硬件解码器偏好）
     */
    fun decodeSampledBitmap(resolver: ContentResolver, uri: Uri): Bitmap? {
        return try {
            // 第一遍：仅读取尺寸
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // 计算采样率
            options.inSampleSize = calculateInSampleSize(
                options.outWidth, options.outHeight, MAX_DIMENSION, MAX_DIMENSION
            )
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565 // 节省内存

            val bitmap = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // 如果仍然太大，二次缩放
            bitmap?.let {
                if (it.width > MAX_DIMENSION || it.height > MAX_DIMENSION) {
                    val scale = MAX_DIMENSION.toFloat() / maxOf(it.width, it.height)
                    val w = (it.width * scale).toInt()
                    val h = (it.height * scale).toInt()
                    val scaled = Bitmap.createScaledBitmap(it, w, h, true)
                    it.recycle()
                    scaled
                } else it
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(
        rawWidth: Int, rawHeight: Int,
        reqWidth: Int, reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
