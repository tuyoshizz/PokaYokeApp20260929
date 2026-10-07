package com.example.pokayokeapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.pokayokeapp.databinding.ActivityMenuBinding

class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding =
            ActivityMenuBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        // =====================================================
        // ピッキングモード
        // =====================================================

        binding.btnPicking.setOnClickListener {

            HistoryManager.addHistory(
                this,
                mode = "ピッキング",
                action = "ピッキングモード開始"
            )

            startActivity(
                Intent(
                    this,
                    MainActivity::class.java
                )
            )
        }


        // =====================================================
        // 段替えモード
        // =====================================================

        binding.btnReturn.setOnClickListener {

            HistoryManager.addHistory(
                this,
                mode = "段替え",
                action = "段替えモード開始",
                method = "-"
            )

            startActivity(
                Intent(
                    this,
                    DangaeActivity::class.java
                )
            )
        }


        // =====================================================
        // 読取履歴
        // =====================================================

        binding.btnSpecial.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    HistoryActivity::class.java
                )
            )
        }
    }
}
