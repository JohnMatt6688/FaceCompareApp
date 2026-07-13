package com.example.facecompare

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面 ViewModel：管理选图、检测、比对全流程状态。
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val detectionHelper = FaceDetectionHelper()
    private val embeddingEngine = FaceEmbeddingEngine(application)

    /** 已选照片 URI 列表 */
    private val _selectedUris = MutableStateFlow<List<Uri>>(emptyList())
    val selectedUris: StateFlow<List<Uri>> get() = _selectedUris

    /** 每张图片的检测状态 */
    private val _imageStates = MutableStateFlow<Map<Uri, ImageState>>(emptyMap())
    val imageStates: StateFlow<Map<Uri, ImageState>> get() = _imageStates

    /** 比对结果 */
    private val _compareResults = MutableStateFlow<List<CompareResultAdapter.Item>>(emptyList())
    val compareResults: StateFlow<List<CompareResultAdapter.Item>> get() = _compareResults

    /** 是否正在比对 */
    private val _isComparing = MutableStateFlow(false)
    val isComparing: StateFlow<Boolean> get() = _isComparing

    /** 综合结论文本 */
    private val _conclusionText = MutableStateFlow("请选择多张含人脸的照片进行比对")
    val conclusionText: StateFlow<String> get() = _conclusionText

    private var detectJob: Job? = null
    private var compareJob: Job? = null

    // ── 图片管理 ──

    fun addUris(uris: List<Uri>) {
        val existing = _selectedUris.value.toSet()
        val newUris = uris.filter { it !in existing }
        if (newUris.isEmpty()) return
        _selectedUris.value = _selectedUris.value + newUris

        // 初始化状态
        val states = _imageStates.value.toMutableMap()
        newUris.forEach { states[it] = ImageState.Loading }
        _imageStates.value = states

        // 开始检测
        detectFaces()
    }

    fun removeUri(uri: Uri) {
        _selectedUris.value = _selectedUris.value.filter { it != uri }
        val states = _imageStates.value.toMutableMap()
        states.remove(uri)
        _imageStates.value = states
        _compareResults.value = emptyList()
        updateConclusion()
    }

    fun clearAll() {
        _selectedUris.value = emptyList()
        _imageStates.value = emptyMap()
        _compareResults.value = emptyList()
        _conclusionText.value = "请选择多张含人脸的照片进行比对"
    }

    // ── 人脸检测 ──

    private fun detectFaces() {
        detectJob?.cancel()
        detectJob = viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver

            for (uri in _selectedUris.value) {
                val currentState = _imageStates.value[uri] ?: continue
                if (currentState is ImageState.Done) continue // 已检测完成则跳过

                _imageStates.value = _imageStates.value.toMutableMap().apply {
                    this[uri] = ImageState.Detecting
                }

                try {
                    // 加载图片
                    val bitmap = withContext(Dispatchers.IO) {
                        BitmapUtils.decodeSampledBitmap(resolver, uri)
                    } ?: run {
                        updateImageState(uri, ImageState.Error("无法加载图片"))
                        continue
                    }

                    // ML Kit 检测人脸
                    val faces = detectionHelper.detectFaces(bitmap)

                    if (faces.isEmpty()) {
                        updateImageState(uri, ImageState.Error("未检测到人脸"))
                        bitmap.recycle()
                        continue
                    }

                    // 取最大人脸
                    val mainFace = faces.maxByOrNull {
                        it.boundingBox.width() * it.boundingBox.height()
                    }!!

                    // 裁剪
                    val faceBitmap = withContext(Dispatchers.Default) {
                        detectionHelper.cropFace(bitmap, mainFace)
                    }

                    // TFLite 提取特征
                    val embedding = if (faceBitmap != null) {
                        embeddingEngine.extractEmbedding(faceBitmap)
                    } else {
                        null
                    }

                    updateImageState(
                        uri,
                        ImageState.Done(
                            faceCount = faces.size,
                            faceBitmap = faceBitmap,
                            embedding = embedding
                        )
                    )

                    bitmap.recycle()
                } catch (e: Exception) {
                    updateImageState(uri, ImageState.Error("检测失败: ${e.message}"))
                }
            }

            updateConclusion()
        }
    }

    private fun updateImageState(uri: Uri, state: ImageState) {
        _imageStates.value = _imageStates.value.toMutableMap().apply {
            this[uri] = state
        }
    }

    // ── 比对 ──

    fun startComparison() {
        val doneItems = _imageStates.value.entries
            .filter { it.value is ImageState.Done && (it.value as ImageState.Done).embedding != null }
            .map { it.key to (it.value as ImageState.Done) }

        if (doneItems.size < 2) {
            _conclusionText.value = "至少需要 2 张有效人脸才能比对"
            return
        }

        _isComparing.value = true
        compareJob?.cancel()
        compareJob = viewModelScope.launch(Dispatchers.Default) {
            val results = mutableListOf<CompareResultAdapter.Item>()
            val allScores = mutableListOf<Float>()

            for (i in doneItems.indices) {
                for (j in i + 1 until doneItems.size) {
                    val (uri1, state1) = doneItems[i]
                    val (uri2, state2) = doneItems[j]

                    val emb1 = state1.embedding!!
                    val emb2 = state2.embedding!!

                    val (isSame, similarity) = embeddingEngine.isSamePerson(emb1, emb2)
                    allScores.add(similarity)

                    results.add(
                        CompareResultAdapter.Item(
                            image1Uri = uri1,
                            image2Uri = uri2,
                            faceThumb1 = state1.faceBitmap,
                            faceThumb2 = state2.faceBitmap,
                            similarity = similarity,
                            isSame = isSame
                        )
                    )
                }
            }

            withContext(Dispatchers.Main) {
                _compareResults.value = results
                _isComparing.value = false

                val allSame = results.all { it.isSame }
                val avgScore = if (allScores.isNotEmpty()) allScores.average().toFloat() else 0f

                val conclusion = buildString {
                    append("比对完成：${doneItems.size} 张照片，${results.size} 组比对\n")
                    append("阈值: ${"%.2f".format(embeddingEngine.threshold)} | ")
                    append("平均相似度: ${"%.2f".format(avgScore * 100)}%\n")
                    if (allSame) append("✅ 结论：所有照片为同一人")
                    else append("⚠️ 结论：检测到不同人脸，可能不是同一人")
                }
                _conclusionText.value = conclusion
            }
        }
    }

    private fun updateConclusion() {
        val doneCount = _imageStates.value.values.count { it is ImageState.Done }
        val errorCount = _imageStates.value.values.count { it is ImageState.Error }
        val total = _selectedUris.value.size
        _conclusionText.value = buildString {
            append("已选 $total 张 | 检测完成 $doneCount")
            if (errorCount > 0) append(" | 失败 $errorCount")
            if (doneCount >= 2) append("\n可进行比对")
        }
    }

    override fun onCleared() {
        super.onCleared()
        detectionHelper.close()
        embeddingEngine.close()
    }
}

/** 单张图片的检测状态 */
sealed class ImageState {
    data object Loading : ImageState()
    data object Detecting : ImageState()
    data class Done(
        val faceCount: Int,
        val faceBitmap: Bitmap?,
        val embedding: FloatArray?
    ) : ImageState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Done) return false
            return faceCount == other.faceCount &&
                    embedding?.contentEquals(other.embedding) == true
        }
        override fun hashCode(): Int = faceCount
    }
    data class Error(val message: String) : ImageState()
}
