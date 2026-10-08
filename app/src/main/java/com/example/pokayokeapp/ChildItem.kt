package com.example.pokayokeapp

data class ChildItem(

    val code: String,

    val name: String,

    val location: String,

    var checked: Boolean = false,

    val parentCode: String = "",

    // =========================================
    // 点滅開始時刻
    //
    // 0L = まだ点滅していない
    // =========================================

    var blinkStartTime: Long = 0L
)