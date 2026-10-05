package com.example.pokayokeapp

import android.graphics.Color
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

    // =========================================
    // 点滅設定
    // =========================================

    // QR読み取りから点滅開始まで
    // 1000L = 1秒
    private val startDelay: Long = 200L,

    // 点滅間隔
    // 1000L = 1秒
    private val blinkInterval: Long = 200L,

    // 点滅回数
    private val blinkCount: Int = 5

) : RecyclerView.Adapter<ChildAdapter.ViewHolder>() {

    private val handler = Handler(Looper.getMainLooper())

    // =========================================
    // ViewHolder
    // =========================================

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val code: TextView =
            view.findViewById(R.id.txtCode)

        val name: TextView =
            view.findViewById(R.id.txtName)

        val loc: TextView =
            view.findViewById(R.id.txtLoc)

        val root: View = view

        // 現在実行中の点滅Runnable
        var blinkRunnable: Runnable? = null
    }

    // =========================================
    // ViewHolder作成
    // =========================================

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(
                R.layout.row_item,
                parent,
                false
            )

        return ViewHolder(view)
    }

    // =========================================
    // 件数
    // =========================================

    override fun getItemCount(): Int {
        return list.size
    }

    // =========================================
    // ViewHolderへのデータ設定
    // =========================================

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        val item = list[position]

        // -----------------------------------------
        // 以前の点滅処理を停止
        // -----------------------------------------

        holder.blinkRunnable?.let {

            handler.removeCallbacks(it)

            holder.blinkRunnable = null
        }

        // -----------------------------------------
        // テキスト設定
        // -----------------------------------------

        holder.code.text = item.code
        holder.name.text = item.name
        holder.loc.text = item.location

        // -----------------------------------------
        // 背景色
        // -----------------------------------------

        if (item.checked) {

            // チェック済み
            holder.root.setBackgroundColor(
                Color.GREEN
            )

            // 点滅開始
            startBlink(holder)

        } else {

            // 未チェック
            holder.root.setBackgroundColor(
                Color.WHITE
            )
        }

        // -----------------------------------------
        // タップ処理
        // -----------------------------------------

        holder.root.setOnClickListener {

            onClick(item)
        }
    }

    // =========================================
    // 点滅処理
    // =========================================

    private fun startBlink(
        holder: ViewHolder
    ) {

        // 点滅回数が0以下の場合
        if (blinkCount <= 0) {

            holder.root.setBackgroundColor(
                Color.GREEN
            )

            return
        }

        // 現在の切り替え回数
        var count = 0

        val runnable = object : Runnable {

            override fun run() {

                count++

                // -----------------------------------------
                // 点滅中
                // -----------------------------------------

                if (count <= blinkCount * 2) {

                    if (count % 2 == 1) {

                        // 白
                        holder.root.setBackgroundColor(
                            Color.WHITE
                        )

                    } else {

                        // 緑
                        holder.root.setBackgroundColor(
                            Color.GREEN
                        )
                    }

                    // 次の切り替え
                    handler.postDelayed(
                        this,
                        blinkInterval
                    )

                } else {

                    // -----------------------------------------
                    // 点滅終了
                    // 最後は必ず緑
                    // -----------------------------------------

                    holder.root.setBackgroundColor(
                        Color.GREEN
                    )

                    holder.blinkRunnable = null
                }
            }
        }

        holder.blinkRunnable = runnable

        // -----------------------------------------
        // startDelay後に点滅開始
        // -----------------------------------------

        handler.postDelayed(
            runnable,
            startDelay
        )
    }

    // =========================================
    // ViewHolder再利用時
    // =========================================

    override fun onViewRecycled(
        holder: ViewHolder
    ) {

        holder.blinkRunnable?.let {

            handler.removeCallbacks(it)

            holder.blinkRunnable = null
        }

        // 再利用前は白に戻す
        holder.root.setBackgroundColor(
            Color.WHITE
        )

        super.onViewRecycled(holder)
    }
}
