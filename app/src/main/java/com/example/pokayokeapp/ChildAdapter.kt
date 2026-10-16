package com.example.pokayokeapp

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChildAdapter(
    private val list: MutableList<ChildItem>,
    private val onClick: (ChildItem) -> Unit,

    // チェックされてから点滅開始まで
    private val startDelay: Long = 500L,

    // 点滅間隔
    private val blinkInterval: Long = 200L,

    // 点滅回数
    private val blinkCount: Int = 3

) : RecyclerView.Adapter<ChildAdapter.ViewHolder>() {

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val CARD_RADIUS_DP = 12f
        private const val CARD_STROKE_DP = 1f

        private const val COLOR_WHITE = Color.WHITE
        private const val COLOR_GREEN = Color.GREEN
        private const val COLOR_BORDER = 0xFFD6DCE5.toInt()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val code: TextView = view.findViewById(R.id.txtCode)
        val name: TextView = view.findViewById(R.id.txtName)
        val loc: TextView = view.findViewById(R.id.txtLoc)
        val root: View = view

        var blinkRunnable: Runnable? = null
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_item, parent, false)

        return ViewHolder(view)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        val item = list[position]

        // 再利用されるViewの点滅を停止
        holder.blinkRunnable?.let {
            handler.removeCallbacks(it)
        }
        holder.blinkRunnable = null

        // 部品情報
        holder.code.text = item.code
        holder.name.text = item.name
        holder.loc.text = item.location

// チェック状態に応じて背景を設定
        if (item.checked) {

            // チェック済み：緑色に固定
            setCardColor(holder, COLOR_GREEN)

        } else {

            // 未チェック：白色に固定
            setCardColor(holder, COLOR_WHITE)
        }
        // 既存のタップ処理を維持
        holder.root.setOnClickListener {
            onClick(item)
        }
    }

    // 角丸を維持しながら背景色を変更
    private fun setCardColor(
        holder: ViewHolder,
        color: Int
    ) {

        val density =
            holder.root.resources.displayMetrics.density

        val radius = CARD_RADIUS_DP * density
        val strokeWidth = (CARD_STROKE_DP * density).toInt()

        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
            setStroke(strokeWidth, COLOR_BORDER)
        }

        holder.root.background = background
    }

    // 現在の経過時間に合わせて点滅
    private fun startBlinkFromCurrentState(
        holder: ViewHolder,
        item: ChildItem
    ) {

        if (!item.checked) {
            setCardColor(holder, COLOR_WHITE)
            holder.blinkRunnable = null
            return
        }

        if (blinkCount <= 0 || blinkInterval <= 0L) {
            setCardColor(holder, COLOR_GREEN)
            holder.blinkRunnable = null
            return
        }

        val elapsed =
            System.currentTimeMillis() - item.blinkStartTime

        if (elapsed < startDelay) {
            setCardColor(holder, COLOR_GREEN)

            val runnable = object : Runnable {
                override fun run() {
                    startBlinkFromCurrentState(holder, item)
                }
            }

            holder.blinkRunnable = runnable

            handler.postDelayed(
                runnable,
                (startDelay - elapsed).coerceAtLeast(1L)
            )

            return
        }

        val blinkElapsed = elapsed - startDelay

        val totalBlinkDuration =
            blinkInterval * blinkCount * 2

        if (blinkElapsed >= totalBlinkDuration) {
            setCardColor(holder, COLOR_GREEN)
            holder.blinkRunnable = null
            return
        }

        val count =
            (blinkElapsed / blinkInterval).toInt()

        if (count % 2 == 0) {
            setCardColor(holder, COLOR_GREEN)
        } else {
            setCardColor(holder, COLOR_WHITE)
        }

        val nextChange =
            blinkInterval - (blinkElapsed % blinkInterval)

        val runnable = object : Runnable {
            override fun run() {
                startBlinkFromCurrentState(holder, item)
            }
        }

        holder.blinkRunnable = runnable

        handler.postDelayed(
            runnable,
            nextChange.coerceAtLeast(1L)
        )
    }

    // ViewHolder再利用時の処理
    override fun onViewRecycled(holder: ViewHolder) {

        holder.blinkRunnable?.let {
            handler.removeCallbacks(it)
        }

        holder.blinkRunnable = null
        holder.root.setOnClickListener(null)

        setCardColor(holder, COLOR_WHITE)

        super.onViewRecycled(holder)
    }
}