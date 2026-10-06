package com.example.pokayokeapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // 操作した日時
    val timestamp: Long,

    // 履歴の種類
    // READ / MODE_CHANGE
    val type: String,

    // モード
    // PICKING / DANGAE
    val mode: String? = null,

    // 親品番
    val parentCode: String? = null,

    // 子品番
    val childCode: String? = null,

    // 操作方法
    // QR / TAP / TAP_CANCEL
    val action: String? = null,

    // モード変更前
    val fromMode: String? = null,

    // モード変更後
    val toMode: String? = null
)
