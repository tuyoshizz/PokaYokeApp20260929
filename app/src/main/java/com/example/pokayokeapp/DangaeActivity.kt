package com.example.pokayokeapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class DangaeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 段替えモードの画面は後から実装
        setContentView(R.layout.activity_dangae)
    }
}
