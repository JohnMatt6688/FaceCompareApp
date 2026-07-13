package com.example.facecompare

import android.graphics.Bitmap
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.facecompare.databinding.ItemFaceBinding

/**
 * 已选人脸照片横向列表 Adapter
 */
class FaceImageAdapter(
    private val onRemove: (Uri) -> Unit
) : RecyclerView.Adapter<FaceImageAdapter.VH>() {

    private val items = mutableListOf<Item>()

    data class Item(
        val uri: Uri,
        val status: Status = Status.LOADING,
        val faceBitmap: Bitmap? = null,
        val faceCount: Int = 0,
        val errorMsg: String? = null
    )

    enum class Status { LOADING, DETECTING, OK, ERROR }

    fun setAll(newList: List<Item>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    fun updateStatus(uri: Uri, status: Status, faceBitmap: Bitmap? = null, faceCount: Int = 0, errorMsg: String? = null) {
        val idx = items.indexOfFirst { it.uri == uri }
        if (idx >= 0) {
            items[idx] = items[idx].copy(
                status = status, faceBitmap = faceBitmap, faceCount = faceCount, errorMsg = errorMsg
            )
            notifyItemChanged(idx)
        }
    }

    val currentItems: List<Item> get() = items.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemFaceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(items[pos])

    override fun getItemCount() = items.size

    inner class VH(val b: ItemFaceBinding) : RecyclerView.ViewHolder(b.root) {
        init {
            b.btnRemove.setOnClickListener { onRemove(items[adapterPosition].uri) }
        }

        fun bind(item: Item) {
            b.imageFace.setImageURI(item.uri)
            b.progressBar.visibility = if (item.status == Status.LOADING || item.status == Status.DETECTING)
                android.view.View.VISIBLE else android.view.View.GONE

            b.textStatus.visibility = if (item.status != Status.LOADING && item.status != Status.DETECTING)
                android.view.View.VISIBLE else android.view.View.GONE

            b.textStatus.text = when {
                item.errorMsg != null -> item.errorMsg
                item.status == Status.OK && item.faceCount > 1 -> "⚠ 多人脸 (${item.faceCount})"
                item.status == Status.OK -> "✓ 已检测"
                else -> ""
            }
        }
    }
}
