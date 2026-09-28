package com.example.pokayokeapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChildAdapter(
    private val list: MutableList<ChildItem>,
    private val onClick: (ChildItem) -> Unit
) : RecyclerView.Adapter<ChildAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val code: TextView = view.findViewById(R.id.txtCode)
        val name: TextView = view.findViewById(R.id.txtName)
        val loc: TextView = view.findViewById(R.id.txtLoc)
        val root: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]

        holder.code.text = item.code
        holder.name.text = item.name
        holder.loc.text = item.location

        // 🔥 チェック済み色
        if (item.checked) {
            holder.root.setBackgroundColor(0xFF4CAF50.toInt()) // 緑
        } else {
            holder.root.setBackgroundColor(0xFFFFFFFF.toInt()) // 白
        }

        holder.root.setOnClickListener {
            onClick(item)
        }
    }
}