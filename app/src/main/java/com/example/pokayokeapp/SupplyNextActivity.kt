
package com.example.pokayokeapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.pokayokeapp.databinding.ActivitySupplyNextBinding

class SupplyNextActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySupplyNextBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySupplyNextBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // SupplyActivity から受け取った品番
        val parentCode =
            intent.getStringExtra("PARENT_CODE").orEmpty()

        val childCode =
            intent.getStringExtra("CHILD_CODE").orEmpty()

        // 品番情報を表示
        binding.tvPartInfo.text =
            "親品番：$parentCode\n部品品番：$childCode"

        // 最初に戻る：SupplyActivity に戻る
        // SupplyActivity 側で品番情報をリセットする
        binding.btnBack.setOnClickListener {
            finish()
        }

        // メニュー画面に戻る
        binding.btnMenu.setOnClickListener {
            val intent = Intent(
                this,
                MenuActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            startActivity(intent)
            finish()
        }
    }
}
