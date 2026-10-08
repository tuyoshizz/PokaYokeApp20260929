package com.example.pokayokeapp

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(
    private val list: MutableList<HistoryItem>
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    // =========================================================
    // ViewHolder
    // =========================================================

    class ViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {

        val dateText: TextView =
            view.findViewById(R.id.txtDate)

        val modeText: TextView =
            view.findViewById(R.id.txtMode)

        val actionText: TextView =
            view.findViewById(R.id.txtAction)

        val parentCodeText: TextView =
            view.findViewById(R.id.txtParentCode)

        val childCodeText: TextView =
            view.findViewById(R.id.txtChildCode)

        val methodText: TextView =
            view.findViewById(R.id.txtMethod)
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
                    R.layout.row_history,
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
    // データ設定
    // =========================================================

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        val item =
            list[position]


        // -----------------------------------------
        // 日時
        // -----------------------------------------

        holder.dateText.text =
            item.date


        // -----------------------------------------
        // モード
        // -----------------------------------------

        holder.modeText.text =
            item.mode


        // -----------------------------------------
        // 操作
        // -----------------------------------------

        holder.actionText.text =
            item.action


        // -----------------------------------------
        // 親品番
        // -----------------------------------------

        holder.parentCodeText.text =
            item.parentCode


        // -----------------------------------------
        // 子品番
        // -----------------------------------------

        holder.childCodeText.text =
            item.childCode


        // -----------------------------------------
        // 操作方法
        // -----------------------------------------

        holder.methodText.text =
            item.method


        // =====================================================
        // 履歴の種類によって背景色を変更
        // =====================================================

        when {

            // =================================================
            // 赤色
            // =================================================
            // ・親品番読取
            // ・ピッキング完了
            // =================================================

            item.action == "親品番読取" ||
                    item.action == "ピッキング完了" -> {

                holder.itemView.setBackgroundColor(
                    Color.rgb(
                        255,
                        220,
                        220
                    )
                )
            }


            // =================================================
            // 青色
            // =================================================
            // ・段替え部品供給開始
            // ・段替え完了
            // =================================================

            item.action == "段替え部品供給開始" ||
                    item.action == "段替え完了" -> {

                holder.itemView.setBackgroundColor(
                    Color.rgb(
                        220,
                        235,
                        255
                    )
                )
            }


            // =================================================
            // 緑色
            // =================================================
            // ・段替えモード開始
            // ・ピッキングモード開始
            // =================================================

            item.action == "段替えモード開始" ||
                    item.action == "ピッキングモード開始" -> {

                holder.itemView.setBackgroundColor(
                    Color.rgb(
                        220,
                        255,
                        220
                    )
                )
            }


            // =================================================
            // その他
            // =================================================

            else -> {

                holder.itemView.setBackgroundColor(
                    Color.TRANSPARENT
                )
            }
        }
    }
}