package com.example.multibarcode

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Simple list of the codes currently visible, one row per code, numbered to match the overlay badges. */
class BarcodeResultAdapter : RecyclerView.Adapter<BarcodeResultAdapter.ViewHolder>() {

    private val items = mutableListOf<DetectedBarcode>()

    fun submit(newItems: List<DetectedBarcode>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class ViewHolder(view: ViewGroup) : RecyclerView.ViewHolder(view) {
        val indexBadge: TextView = view.findViewById(R.id.indexBadge)
        val valueText: TextView = view.findViewById(R.id.valueText)
        val formatText: TextView = view.findViewById(R.id.formatText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_barcode, parent, false) as ViewGroup
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.indexBadge.text = (position + 1).toString()
        holder.valueText.text = item.value
        holder.formatText.text = item.format
    }

    override fun getItemCount(): Int = items.size
}
