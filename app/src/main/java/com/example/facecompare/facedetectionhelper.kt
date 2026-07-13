package com.example.facecompare

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 人脸检测辅助工具。
 * 仅负责 ML Kit 人脸检测与人脸区域裁剪，不涉及特征提取。
 */
class FaceDetectionHelper {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
    )

    /**
     * 检测图片中的所有人脸，按面积降序排列。
     *
     * @return Pair(检测到的人脸列表, 对应的处理后的 InputImage)
     */
    suspend fun detectFaces(bitmap: Bitmap): List<Face> =
        withContext(Dispatchers.Default) {
            val image = InputImage.fromBitmap(bitmap, 0)
            val task = detector.process(image)
            suspendCancellableCoroutine { cont ->
                task.addOnSuccessListener { faces ->
                    if (cont.isActive) cont.resume(faces)
                }
                task.addOnFailureListener { e ->
                    if (cont.isActive) cont.resume(emptyList())
                }
            }
        }

    /**
     * 从原图中裁剪人脸区域，带 30% padding。
     */
    fun cropFace(bitmap: Bitmap, face: Face): Bitmap? {
        return try {
            val box = face.boundingBox
            val paddingX = (box.width() * 0.3f).toInt()
            val paddingY = (box.height() * 0.3f).toInt()
            val left = (box.left - paddingX).coerceAtLeast(0)
            val top = (box.top - paddingY).coerceAtLeast(0)
            val right = (box.right + paddingX).coerceAtMost(bitmap.width)
            val bottom = (box.bottom + paddingY).coerceAtMost(bitmap.height)
            val w = right - left
            val h = bottom - top
            if (w > 0 && h > 0) Bitmap.createBitmap(bitmap, left, top, w, h) else null
        } catch (_: Exception) {
            null
        }
    }

    fun close() {
        detector.close()
    }
}
