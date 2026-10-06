package com.example.pokayokeapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HistoryDao {

    // =========================================================
    // 履歴追加
    // =========================================================

    @Insert
    suspend fun insert(
        history: HistoryEntity
    )

    // =========================================================
    // 全履歴取得
    // 新しいものを上にする
    // =========================================================

    @Query(
        "SELECT * FROM history ORDER BY timestamp DESC"
    )
    suspend fun getAll(): List<HistoryEntity>

    // =========================================================
    // 全履歴削除
    // =========================================================

    @Query("DELETE FROM history")
    suspend fun deleteAll()
}
