package com.example.pokayokeapp

data class ChildItem(
    val code: String,
    val name: String,
    val location: String,
    var checked: Boolean = false,
    val parentCode: String = ""
)