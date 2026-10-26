package com.example.pokayokeapp

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ModeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = intent.getStringExtra("MODE") ?: "未選択"

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL

        val text = TextView(this)
        text.textSize = 20f
        text.text = "$mode モード（未実装）"
        text.gravity = Gravity.CENTER

        val btnPicking = Button(this)
        btnPicking.text = "ピッキング"

        val btnReturn = Button(this)
        btnReturn.text = "Returnモード"

        val btnSpecial = Button(this)
        btnSpecial.text = "Specialモード"

        layout.addView(text)
        layout.addView(btnPicking)
        layout.addView(btnReturn)
        layout.addView(btnSpecial)

        setContentView(layout)

        btnPicking.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        btnReturn.setOnClickListener {
            switchMode("RETURN")
        }

        btnSpecial.setOnClickListener {
            switchMode("SPECIAL")
        }
    }

    private fun switchMode(mode: String) {
        val intent = Intent(this, ModeActivity::class.java)
        intent.putExtra("MODE", mode)
        startActivity(intent)
        finish()
    }
}