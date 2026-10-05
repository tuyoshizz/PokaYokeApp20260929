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

        // 1番目：ピッキングモード
        binding.btnPicking.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        // 2番目：段替えモード
        binding.btnReturn.setOnClickListener {
            startActivity(Intent(this, DangaeActivity::class.java))
        }

        // 3番目：特殊モード（現在は未開発）
        binding.btnSpecial.setOnClickListener {
            val intent = Intent(this, ModeActivity::class.java)
            intent.putExtra("MODE", "SPECIAL")
            startActivity(intent)
        }
    }
}
