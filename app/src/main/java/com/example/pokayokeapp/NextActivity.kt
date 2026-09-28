package com.example.pokayokeapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class NextActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complete)

        val btnBack = findViewById<Button>(R.id.btnBack)

        btnBack.setOnClickListener {

            val intent = Intent(this, MainActivity::class.java)

            // 👉 これだけでOK（シンプルに戻る）
            startActivity(intent)

            // 👉 今の画面を閉じる
            finish()
        }
    }
}