package com.example.pokayokeapp

data class HistoryItem(

    val date: String,

    val mode: String,

    val action: String,

    val parentCode: String,

    val childCode: String,

    val location: String,

    val method: String
)
