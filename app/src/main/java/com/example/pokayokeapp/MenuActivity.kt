
package com.example.pokayokeapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.pokayokeapp.databinding.ActivityMenuBinding

class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ピッキングモード
        binding.btnPicking.setOnClickListener {
            HistoryManager.addHistory(
                this,
                mode = "ピッキング",
                action = "ピッキングモード開始"
            )

            startActivity(
                Intent(this, MainActivity::class.java)
            )
        }

        // 段替えモード
        binding.btnReturn.setOnClickListener {
            HistoryManager.addHistory(
                this,
                mode = "段替え",
                action = "段替えモード開始",
                method = "-"
            )

            startActivity(
                Intent(this, DangaeActivity::class.java)
            )
        }

        // 買物モード
        binding.btnBuy.setOnClickListener {
            HistoryManager.addHistory(
                this,
                mode = "買物",
                action = "買物モード開始",
                method = "-"
            )

            startActivity(
                Intent(this, BuyActivity::class.java)
            )
        }

        // 供給モード
        binding.btnSupply.setOnClickListener {
            HistoryManager.addHistory(
                this,
                mode = "供給",
                action = "供給モード開始",
                method = "-"
            )

            startActivity(
                Intent(this, SupplyActivity::class.java)
            )
        }

        // 読み取り履歴
        binding.btnSpecial.setOnClickListener {
            startActivity(
                Intent(this, HistoryActivity::class.java)
            )
        }
    }
}