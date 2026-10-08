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

    // チェックされてから点滅開始まで
    private val startDelay: Long = 500L,

    // 点滅間隔
    private val blinkInterval: Long = 200L,

    // 点滅回数
    private val blinkCount: Int = 3

) : RecyclerView.Adapter<ChildAdapter.ViewHolder>() {

    private val handler =
        Handler(Looper.getMainLooper())


    // =========================================================
    // ViewHolder
    // =========================================================

    class ViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {

        val code: TextView =
            view.findViewById(R.id.txtCode)

        val name: TextView =
            view.findViewById(R.id.txtName)

        val loc: TextView =
            view.findViewById(R.id.txtLoc)

        val root: View =
            view

        // 現在実行中の点滅Runnable
        var blinkRunnable: Runnable? = null
    }


    // =========================================================
    // ViewHolder作成
    // =========================================================

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val view =
            LayoutInflater.from(parent.context)
                .inflate(
                    R.layout.row_item,
                    parent,
                    false
                )

        return ViewHolder(view)
    }


    // =========================================================
    // 件数
    // =========================================================

    override fun getItemCount(): Int {

        return list.size
    }


    // =========================================================
    // ViewHolderへのデータ設定
    // =========================================================

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        val item =
            list[position]


        // =====================================================
        // 以前の点滅処理を停止
        // =====================================================

        holder.blinkRunnable?.let {

            handler.removeCallbacks(it)

            holder.blinkRunnable = null
        }


        // =====================================================
        // テキスト設定
        // =====================================================

        holder.code.text =
            item.code

        holder.name.text =
            item.name

        holder.loc.text =
            item.location


        // =====================================================
        // 背景色
        // =====================================================

        if (!item.checked) {

            // -----------------------------------------
            // 未チェック
            // -----------------------------------------

            holder.root.setBackgroundColor(
                Color.WHITE
            )

        } else {

            // -----------------------------------------
            // チェック済み
            // -----------------------------------------

            // 点滅開始時刻がない場合
            // 念のため緑色
            if (item.blinkStartTime <= 0L) {

                holder.root.setBackgroundColor(
                    Color.GREEN
                )

            } else {

                // 現在時刻から
                // 点滅開始から何ms経過したか計算

                val elapsed =
                    System.currentTimeMillis() -
                            item.blinkStartTime


                val blinkDuration =
                    startDelay +
                            (blinkInterval *
                                    blinkCount *
                                    2)


                // -----------------------------------------
                // まだ点滅時間内
                // -----------------------------------------

                if (elapsed < blinkDuration) {

                    startBlinkFromCurrentState(
                        holder,
                        item
                    )

                } else {

                    // -------------------------------------
                    // 点滅終了
                    // 緑色のまま
                    // -------------------------------------

                    holder.root.setBackgroundColor(
                        Color.GREEN
                    )
                }
            }
        }


        // =====================================================
        // タップ処理
        // =====================================================

        holder.root.setOnClickListener {

            onClick(item)
        }
    }


    // =========================================================
    // 現在の時間位置から点滅開始
    // =========================================================
    //
    // 画面外にあった部品が表示された場合、
    // 「最初から」ではなく、
    // 現在の経過時間に合わせて点滅する。
    //
    // =========================================================

    private fun startBlinkFromCurrentState(
        holder: ViewHolder,
        item: ChildItem
    ) {

        if (
            blinkCount <= 0
        ) {

            holder.root.setBackgroundColor(
                Color.GREEN
            )

            return
        }


        val elapsed =
            System.currentTimeMillis() -
                    item.blinkStartTime


        // =====================================================
        // まだ開始待ち時間中
        // =====================================================

        if (
            elapsed < startDelay
        ) {

            holder.root.setBackgroundColor(
                Color.GREEN
            )


            val remaining =
                startDelay - elapsed


            val runnable =
                object : Runnable {

                    override fun run() {

                        startBlinkFromCurrentState(
                            holder,
                            item
                        )
                    }
                }


            holder.blinkRunnable =
                runnable


            handler.postDelayed(
                runnable,
                remaining
            )


            return
        }


        // =====================================================
        // 点滅開始後の経過時間
        // =====================================================

        val blinkElapsed =
            elapsed - startDelay


        val totalBlinkDuration =
            blinkInterval *
                    blinkCount *
                    2


        // =====================================================
        // 点滅終了
        // =====================================================

        if (
            blinkElapsed >=
            totalBlinkDuration
        ) {

            holder.root.setBackgroundColor(
                Color.GREEN
            )

            holder.blinkRunnable = null

            return
        }


        // =====================================================
        // 現在何回目の切り替えか計算
        // =====================================================

        val count =
            (blinkElapsed / blinkInterval)
                .toInt()


        // =====================================================
        // 現在の色
        // =====================================================

        if (
            count % 2 == 0
        ) {

            holder.root.setBackgroundColor(
                Color.GREEN
            )

        } else {

            holder.root.setBackgroundColor(
                Color.WHITE
            )
        }


        // =====================================================
        // 次の切り替えまでの残り時間
        // =====================================================

        val nextChange =
            blinkInterval -
                    (
                            blinkElapsed %
                                    blinkInterval
                            )


        val runnable =
            object : Runnable {

                override fun run() {

                    startBlinkFromCurrentState(
                        holder,
                        item
                    )
                }
            }


        holder.blinkRunnable =
            runnable


        handler.postDelayed(
            runnable,
            nextChange
        )
    }


    // =========================================================
    // ViewHolder再利用時
    // =========================================================

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


        super.onViewRecycled(
            holder
        )
    }
}