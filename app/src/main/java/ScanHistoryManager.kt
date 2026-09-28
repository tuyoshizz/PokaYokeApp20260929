package com.example.pokayokeapp

import android.content.Context

object ScanHistoryManager {

    private const val PREF_NAME = "scan_history"
    private const val KEY_LIST = "history_list"

    fun save(context: Context, code: String) {
        val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = get(context).toMutableList()

        current.add(0, code) // 先頭に追加

        // 最大10件まで
        val trimmed = current.take(10)

        pref.edit().putString(KEY_LIST, trimmed.joinToString(",")).apply()
    }

    fun get(context: Context): List<String> {
        val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val data = pref.getString(KEY_LIST, "") ?: ""
        if (data.isEmpty()) return emptyList()
        return data.split(",")
    }

    fun clear(context: Context) {
        val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        pref.edit().clear().apply()
    }
}