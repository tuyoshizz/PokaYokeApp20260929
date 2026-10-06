package com.example.pokayokeapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.pokayokeapp.databinding.ActivityDangaeCompleteBinding

class DangaeCompleteActivity : AppCompatActivity() {

    private lateinit var binding:
            ActivityDangaeCompleteBinding

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        binding =
            ActivityDangaeCompleteBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        // =====================================================
        // 今回生産した親品番を取得
        // =====================================================

        val currentParentCode =
            intent
                .getStringExtra(
                    "CURRENT_PARENT_CODE"
                )
                ?.trim()
                ?: ""


        // =====================================================
        // 段替え完了
        // =====================================================
        // 今回の親品番を「前回生産品番」として保存
        // =====================================================

        if (
            currentParentCode.isNotEmpty()
        ) {

            val preferences =
                getSharedPreferences(
                    "PokaYokeSettings",
                    MODE_PRIVATE
                )

            preferences
                .edit()
                .putString(
                    "LAST_PARENT_CODE",
                    currentParentCode
                )
                .apply()
        }


        // =====================================================
        // 段替えモードへ
        // =====================================================

        binding.dangaeButton.setOnClickListener {

            val intent =
                Intent(
                    this,
                    DangaeActivity::class.java
                )

            intent.flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

            startActivity(intent)

            finish()
        }


        // =====================================================
        // メニューへ
        // =====================================================

        binding.menuButton.setOnClickListener {

            val intent =
                Intent(
                    this,
                    MenuActivity::class.java
                )

            intent.flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

            startActivity(intent)

            finish()
        }
    }
}
