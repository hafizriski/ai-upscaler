package com.arthexdev.exups.ui.gallery

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.arthexdev.exups.R
import com.arthexdev.exups.data.repository.GalleryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GalleryAdapter(
    private var items: List<GalleryRepository.GalleryItem> = emptyList(),
    private val onItemClick: (GalleryRepository.GalleryItem) -> Unit = {}
) : RecyclerView.Adapter<GalleryAdapter.VH>() {

    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    private val scope = CoroutineScope(Dispatchers.Main)

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.ivGalleryItem)
        val date: TextView = v.findViewById(R.id.tvGalleryDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.date.text = dateFormat.format(Date(item.timestamp))
        holder.img.setImageBitmap(null)
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try { BitmapFactory.decodeFile(item.file.absolutePath) }
                catch (_: Throwable) { null }
            }
            if (bmp != null && holder.adapterPosition == position) {
                holder.img.setImageBitmap(bmp)
            }
        }
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<GalleryRepository.GalleryItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
