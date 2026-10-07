package com.example.pokayokeapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pokayokeapp.databinding.ActivityHistoryBinding
import androidx.appcompat.app.AlertDialog


class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    private val historyList =
        mutableListOf<HistoryItem>()

    private lateinit var adapter: HistoryAdapter

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        binding =
            ActivityHistoryBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        // -----------------------------------------
        // RecyclerView設定
        // -----------------------------------------

        adapter =
            HistoryAdapter(historyList)

        binding.historyRecyclerView.layoutManager =
            LinearLayoutManager(this)

        binding.historyRecyclerView.adapter =
            adapter

        // -----------------------------------------
        // 戻る
        // -----------------------------------------

        binding.btnBack.setOnClickListener {

            finish()
        }

// -----------------------------------------
// 履歴削除
// -----------------------------------------

        binding.btnClear.setOnClickListener {

            AlertDialog.Builder(this)

                .setTitle("履歴削除")

                .setMessage(
                    "読み取り履歴をすべて削除しますか？"
                )

                .setPositiveButton(
                    "削除"
                ) { _, _ ->

                    // -----------------------------------------
                    // 履歴を削除
                    // -----------------------------------------

                    HistoryManager.clear(this)

                    // -----------------------------------------
                    // 表示を更新
                    // -----------------------------------------

                    loadHistory()
                }

                .setNegativeButton(
                    "キャンセル",
                    null
                )

                .show()
        }


        // -----------------------------------------
        // 履歴読み込み
        // -----------------------------------------

        loadHistory()
    }

    // ---------------------------------------------
    // 画面に戻った時も最新履歴を表示
    // ---------------------------------------------

    override fun onResume() {

        super.onResume()

        loadHistory()
    }

    // ---------------------------------------------
    // 履歴読み込み
    // ---------------------------------------------

    private fun loadHistory() {

        val history =
            HistoryManager.getHistory(this)

        historyList.clear()

        historyList.addAll(history)

        adapter.notifyDataSetChanged()

        if (historyList.isEmpty()) {

            binding.emptyText.text =
                "読み取り履歴はありません"

        } else {

            binding.emptyText.text =
                "履歴 ${historyList.size} 件"
        }
    }
}
