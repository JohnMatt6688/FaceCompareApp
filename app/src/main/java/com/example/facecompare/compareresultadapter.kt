package com.example.facecompare

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.facecompare.databinding.ItemCompareResultBinding

/**
 * 两两比对结果列表 Adapter
 */
class CompareResultAdapter :
    RecyclerView.Adapter<CompareResultAdapter.VH>() {

    private val items = mutableListOf<Item>()

    data class Item(
        val image1Uri: android.net.Uri,
        val image2Uri: android.net.Uri,
        val faceThumb1: Bitmap?,
        val faceThumb2: Bitmap?,
        val similarity: Float,   // 余弦相似度 [−1, 1]
        val isSame: Boolean
    )

    fun setResults(list: List<Item>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemCompareResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(items[pos], pos + 1)

    override fun getItemCount() = items.size

    class VH(val b: ItemCompareResultBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: Item, idx: Int) {
            b.textPairLabel.text = "比对 #$idx"
            b.imageFace1.setImageURI(item.image1Uri)
            b.imageFace2.setImageURI(item.image2Uri)

            val pct = "%.2f".format(item.similarity * 100) + "%"
            b.textScore.text = "余弦相似度: $pct"

            if (item.isSame) {
                b.textVerdict.text = "✅ 同一人"
                b.textVerdict.setTextColor(b.root.context.getColor(android.R.color.holo_green_dark))
            } else {
                b.textVerdict.text = "❌ 不同人"
                b.textVerdict.setTextColor(b.root.context.getColor(android.R.color.holo_red_dark))
            }

            // 渐变进度条：0→100%
            b.progressSimilarity.progress = ((item.similarity + 1f) / 2f * 100).toInt()
        }
    }
}
