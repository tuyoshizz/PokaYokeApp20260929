package com.example.pokayokeapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HistoryManager {

    private const val PREF_NAME = "PokaYokeHistory"
    private const val KEY_HISTORY = "HISTORY"

    // =========================================================
    // 履歴追加
    // =========================================================

    fun addHistory(
        context: Context,
        mode: String,
        action: String,
        parentCode: String = "",
        childCode: String = "",
        location: String = "",
        method: String = ""
    ) {

        val preferences =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        val jsonText =
            preferences.getString(
                KEY_HISTORY,
                "[]"
            ) ?: "[]"

        val array =
            JSONArray(jsonText)

        val item =
            JSONObject()

        // -----------------------------------------------------
        // 日時
        // -----------------------------------------------------

        val date =
            SimpleDateFormat(
                "yyyy/MM/dd HH:mm:ss",
                Locale.JAPAN
            ).format(Date())

        item.put(
            "date",
            date
        )

        // -----------------------------------------------------
        // モード
        // -----------------------------------------------------

        item.put(
            "mode",
            mode
        )

        // -----------------------------------------------------
        // 操作
        // -----------------------------------------------------

        item.put(
            "action",
            action
        )

        // -----------------------------------------------------
        // 親品番
        // -----------------------------------------------------

        item.put(
            "parentCode",
            parentCode
        )

        // -----------------------------------------------------
        // 子品番
        // -----------------------------------------------------

        item.put(
            "childCode",
            childCode
        )

        // -----------------------------------------------------
        // 場所
        // -----------------------------------------------------

        item.put(
            "location",
            location
        )

        // -----------------------------------------------------
        // 操作方法
        // QR / タップ
        // -----------------------------------------------------

        item.put(
            "method",
            method
        )

        // -----------------------------------------------------
        // 最新を先頭に追加
        // -----------------------------------------------------

        val newArray =
            JSONArray()

        newArray.put(item)

        for (
        i in 0 until array.length()
        ) {

            newArray.put(
                array.getJSONObject(i)
            )
        }

        // -----------------------------------------------------
        // 保存
        // -----------------------------------------------------

        preferences
            .edit()
            .putString(
                KEY_HISTORY,
                newArray.toString()
            )
            .apply()
    }


    // =========================================================
    // 履歴取得
    // =========================================================

    fun getHistory(
        context: Context
    ): List<HistoryItem> {

        val preferences =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        val jsonText =
            preferences.getString(
                KEY_HISTORY,
                "[]"
            ) ?: "[]"

        val array =
            JSONArray(jsonText)

        val result =
            mutableListOf<HistoryItem>()

        for (
        i in 0 until array.length()
        ) {

            val item =
                array.getJSONObject(i)

            result.add(

                HistoryItem(

                    date =
                        item.optString(
                            "date"
                        ),

                    mode =
                        item.optString(
                            "mode"
                        ),

                    action =
                        item.optString(
                            "action"
                        ),

                    parentCode =
                        item.optString(
                            "parentCode"
                        ),

                    childCode =
                        item.optString(
                            "childCode"
                        ),

                    location =
                        item.optString(
                            "location"
                        ),

                    method =
                        item.optString(
                            "method"
                        )
                )
            )
        }

        return result
    }


    // =========================================================
    // 履歴削除
    // =========================================================

    fun clear(
        context: Context
    ) {

        context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .remove(KEY_HISTORY)
            .apply()
    }
}
