package com.example.facecompare

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.facecompare.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var faceAdapter: FaceImageAdapter
    private lateinit var resultAdapter: CompareResultAdapter

    private val pickMultipleImages = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.addUris(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = MainViewModel(application)

        faceAdapter = FaceImageAdapter { uri -> viewModel.removeUri(uri) }
        resultAdapter = CompareResultAdapter()

        setupViews()
        observeState()
    }

    private fun setupViews() {
        binding.recyclerFaces.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerFaces.adapter = faceAdapter

        binding.recyclerResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerResults.adapter = resultAdapter

        binding.btnSelectPhotos.setOnClickListener {
            if (PermissionHelper.needsPermission(this)) {
                PermissionHelper.requestPermission(this)
            } else {
                pickMultipleImages.launch("image/*")
            }
        }

        binding.btnCompare.setOnClickListener { viewModel.startComparison() }
        binding.btnClear.setOnClickListener { viewModel.clearAll() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.imageStates.collectLatest { states ->
                val items = viewModel.selectedUris.value.map { uri ->
                    val state = states[uri] ?: ImageState.Loading
                    when (state) {
                        is ImageState.Loading -> FaceImageAdapter.Item(uri, FaceImageAdapter.Status.LOADING)
                        is ImageState.Detecting -> FaceImageAdapter.Item(uri, FaceImageAdapter.Status.DETECTING)
                        is ImageState.Done -> FaceImageAdapter.Item(
                            uri, FaceImageAdapter.Status.OK,
                            faceCount = state.faceCount, faceBitmap = state.faceBitmap
                        )
                        is ImageState.Error -> FaceImageAdapter.Item(
                            uri, FaceImageAdapter.Status.ERROR, errorMsg = state.message
                        )
                    }
                }
                faceAdapter.setAll(items)

                val count = viewModel.selectedUris.value.size
                binding.textPhotoCount.text = "已选 $count 张照片"
                binding.btnCompare.isEnabled = states.values.count { it is ImageState.Done } >= 2
                binding.btnClear.isEnabled = count > 0
            }
        }

        lifecycleScope.launch {
            viewModel.compareResults.collectLatest { results ->
                resultAdapter.setResults(results)
                binding.recyclerResults.scrollToPosition(0)
            }
        }

        lifecycleScope.launch {
            viewModel.conclusionText.collectLatest { text ->
                binding.textProgress.text = text
            }
        }

        lifecycleScope.launch {
            viewModel.isComparing.collectLatest { comparing ->
                binding.progressBar.visibility =
                    if (comparing) android.view.View.VISIBLE else android.view.View.GONE
                binding.btnCompare.isEnabled = !comparing &&
                        viewModel.imageStates.value.values.count { it is ImageState.Done } >= 2

                if (!comparing && viewModel.compareResults.value.isNotEmpty()) {
                    showResultDialog()
                }
            }
        }
    }

    private fun showResultDialog() {
        val results = viewModel.compareResults.value
        val allSame = results.all { it.isSame }
        val title = if (allSame) "✅ 判定为同一人" else "⚠️ 可能存在不同人"
        val avgScore = if (results.isNotEmpty()) {
            results.map { it.similarity }.average().toFloat()
        } else 0f

        val message = buildString {
            append("比对组数：${results.size}\n")
            append("平均余弦相似度：${"%.2f".format(avgScore * 100)}%\n")
            append("阈值：${"%.2f".format(FaceEmbeddingEngine.DEFAULT_THRESHOLD * 100)}%\n\n")
            if (allSame) append("所有配对的人脸余弦相似度均超过阈值，判定为同一人。")
            else append("至少有一组人脸相似度低于阈值，请查看下方详细结果。")
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (PermissionHelper.isGranted(requestCode, grantResults)) {
            pickMultipleImages.launch("image/*")
        } else {
            Toast.makeText(this, "需要存储权限才能选择照片", Toast.LENGTH_LONG).show()
        }
    }
}
