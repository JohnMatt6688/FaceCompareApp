package com.example.facecompare

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * MobileFaceNet 人脸特征提取引擎。
 *
 * 流程：
 * 1. 将裁剪好的人脸 Bitmap 缩放到 112×112
 * 2. 归一化到 [−1, 1]（(pixel/127.5)−1）
 * 3. 送入 MobileFaceNet TFLite 模型推理
 * 4. 输出 128 维浮点嵌入向量（已 L2 归一化）
 * 5. 余弦相似度 = 两点积（模型输出已归一化，cosSim = dotProduct）
 *
 * @param threshold 判定阈值，默认 0.60。余弦相似度 >= 阈值判定为同一人。
 */
class FaceEmbeddingEngine(
    context: Context,
    val threshold: Float = DEFAULT_THRESHOLD
) {
    companion object {
        private const val MODEL_FILE = "mobilefacenet.tflite"
        private const val INPUT_SIZE = 112
        private const val EMBEDDING_SIZE = 128
        private const val PIXEL_MEAN = 127.5f
        private const val PIXEL_SCALE = 127.5f
        const val DEFAULT_THRESHOLD = 0.60f
    }

    private val interpreter: Interpreter

    /** 输入张量 shape: [1, 112, 112, 3] */
    private val inputShape: IntArray

    init {
        val modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE)
        interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
            setNumThreads(4)
            setUseNNAPI(false)
        })
        inputShape = interpreter.getInputTensor(0).shape()
    }

    /**
     * 从人脸 Bitmap 提取 128 维特征向量。
     *
     * @param faceBitmap 裁剪后的人脸图片（任意尺寸）
     * @return 128 维 L2 归一化嵌入向量
     */
    suspend fun extractEmbedding(faceBitmap: Bitmap): FloatArray =
        withContext(Dispatchers.Default) {
            // 1. 预处理：缩放 + 归一化
            val inputBuffer = preprocess(faceBitmap)

            // 2. 创建输出 buffer
            val outputSize = inputShape[0] * EMBEDDING_SIZE
            val outputBuffer = TensorBuffer.createFixedSize(
                intArrayOf(1, EMBEDDING_SIZE),
                org.tensorflow.lite.DataType.FLOAT32
            )

            // 3. 推理
            interpreter.run(inputBuffer, outputBuffer.buffer)

            // 4. 提取并 L2 归一化
            val raw = outputBuffer.floatArray
            l2Normalize(raw)

            raw
        }

    /**
     * 预处理人脸图片：
     * - 缩放到 112×112（保持比例，填充黑边）
     * - RGB 像素归一化到 [−1, 1]
     * - 输出 ByteBuffer（float32）
     */
    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        // 缩放到 112×112（居中裁剪保持比例）
        val scaled = scaleCenterCrop(bitmap, INPUT_SIZE, INPUT_SIZE)

        // 图片尺寸验证: 不是 112×112 就无法正确读取像素
        if (scaled.width != INPUT_SIZE || scaled.height != INPUT_SIZE) {
            val final = Bitmap.createScaledBitmap(scaled, INPUT_SIZE, INPUT_SIZE, true)
            if (final !== scaled) scaled.recycle()
            return pixelsToBuffer(final, alsoRecycle = final !== scaled)
        }

        return pixelsToBuffer(scaled, alsoRecycle = scaled !== bitmap)
    }

    /**
     * 读取 Bitmap 像素 → float32 ByteBuffer，归一化到 [−1, 1]。
     */
    private fun pixelsToBuffer(bitmap: Bitmap, alsoRecycle: Boolean): ByteBuffer {
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val buffer = ByteBuffer.allocateDirect(
            INPUT_SIZE * INPUT_SIZE * 3 * java.lang.Float.SIZE / java.lang.Byte.SIZE
        ).order(ByteOrder.nativeOrder())
        val floatBuffer = buffer.asFloatBuffer()

        for (pixel in pixels) {
            // Android Bitmap pixel 是 ARGB
            val r = ((pixel shr 16) and 0xFF).toFloat()
            val g = ((pixel shr 8) and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()
            // 归一化到 [−1, 1]: (pixel − 127.5) / 127.5
            floatBuffer.put((r - PIXEL_MEAN) / PIXEL_SCALE)
            floatBuffer.put((g - PIXEL_MEAN) / PIXEL_SCALE)
            floatBuffer.put((b - PIXEL_MEAN) / PIXEL_SCALE)
        }

        floatBuffer.flip()

        if (alsoRecycle && !bitmap.isRecycled) {
            bitmap.recycle()
        }

        return buffer
    }

    /**
     * 保持比例缩放 + 居中裁剪到 targetW×targetH。
     */
    private fun scaleCenterCrop(source: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()
        val scale = maxOf(targetW / srcW, targetH / srcH)
        val scaledW = (srcW * scale).toInt()
        val scaledH = (srcH * scale).toInt()

        val scaled = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
        val startX = (scaledW - targetW) / 2
        val startY = (scaledH - targetH) / 2
        val result = Bitmap.createBitmap(scaled, startX, startY, targetW, targetH)
        if (scaled !== source) scaled.recycle()
        return result
    }

    /**
     * L2 归一化（in-place）。
     */
    private fun l2Normalize(vector: FloatArray) {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = sqrt(norm)
        if (norm > 1e-10f) {
            for (i in vector.indices) vector[i] /= norm
        }
    }

    /**
     * 余弦相似度。
     * 模型输出已经 L2 归一化，cosSim = dotProduct。
     */
    fun cosineSimilarity(emb1: FloatArray, emb2: FloatArray): Float {
        if (emb1.size != EMBEDDING_SIZE || emb2.size != EMBEDDING_SIZE) return 0f
        var dot = 0f
        for (i in 0 until EMBEDDING_SIZE) {
            dot += emb1[i] * emb2[i]
        }
        return dot.coerceIn(-1f, 1f)
    }

    /**
     * 判定是否为同一人。
     *
     * @return Pair(是否同一人, 余弦相似度)
     */
    fun isSamePerson(emb1: FloatArray, emb2: FloatArray): Pair<Boolean, Float> {
        val similarity = cosineSimilarity(emb1, emb2)
        return Pair(similarity >= threshold, similarity)
    }

    fun close() {
        interpreter.close()
    }
}

/**
 * 单张图片的人脸检测结果
 */
data class FaceResult(
    val faceBitmap: Bitmap,
    val embedding: FloatArray,
    val faceCount: Int  // 整张图中检测到的人脸总数
)
