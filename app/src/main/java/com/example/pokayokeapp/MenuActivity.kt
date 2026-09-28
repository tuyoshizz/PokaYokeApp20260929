package com.example.pokayokeapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.pokayokeapp.databinding.ActivityMenuBinding

class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔥 これを追加（最重要）
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ↓ここからはOK
        binding.btnPicking.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        binding.btnReturn.setOnClickListener {
            val intent = Intent(this, ModeActivity::class.java)
            intent.putExtra("MODE", "RETURN")
            startActivity(intent)
        }

        binding.btnSpecial.setOnClickListener {
            val intent = Intent(this, ModeActivity::class.java)
            intent.putExtra("MODE", "SPECIAL")
            startActivity(intent)
        }
    }
}