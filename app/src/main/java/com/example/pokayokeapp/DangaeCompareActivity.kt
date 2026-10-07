package com.example.pokayokeapp

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.example.pokayokeapp.databinding.ActivityDangaeCompareBinding
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File


class DangaeCompareActivity : AppCompatActivity() {

    private lateinit var binding:
            ActivityDangaeCompareBinding


    // =========================================================
    // Excel設定
    // =========================================================

    companion object {

        private const val FOLDER_NAME =
            "PokaYoke"

        private const val EXCEL_FILE_NAME =
            "parts.xlsx"
    }


    // =========================================================
    // 親品番
    // =========================================================

    private var lastParentCode = ""

    private var currentParentCode = ""


    // =========================================================
    // onCreate
    // =========================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)


        // =====================================================
        // Binding
        // =====================================================

        binding =
            ActivityDangaeCompareBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)


        // =====================================================
        // 親品番取得
        // =====================================================

        lastParentCode =
            intent
                .getStringExtra(
                    "LAST_PARENT_CODE"
                )
                ?.trim()
                ?: ""


        currentParentCode =
            intent
                .getStringExtra(
                    "CURRENT_PARENT_CODE"
                )
                ?.trim()
                ?: ""


        // =====================================================
        // 親品番表示
        // =====================================================

        binding.lastParentText.text =
            "前回：$lastParentCode"


        binding.currentParentText.text =
            "今回：$currentParentCode"


        // =====================================================
        // 比較実行
        // =====================================================

        compareParts(
            lastParentCode,
            currentParentCode
        )


        // =====================================================
        // 段替えを行うボタン
        // =====================================================

        binding.changeoverButton.setOnClickListener {

            HistoryManager.addHistory(

                this,

                mode = "段替え",

                action = "段替え部品供給開始",

                parentCode =
                    currentParentCode
            )

            openDangaePicking()
        }



        // =====================================================
        // 戻る
        // =====================================================

        binding.backButton.setOnClickListener {

            finish()
        }
    }


    // =========================================================
    // 段替えピッキング画面へ
    // =========================================================

    private fun openDangaePicking() {

        // -----------------------------------------------------
        // 今回品番がない場合は遷移しない
        // -----------------------------------------------------

        if (
            currentParentCode.isEmpty()
        ) {

            return
        }


        val intent =
            Intent(
                this,
                DangaePickingActivity::class.java
            )


        // -----------------------------------------------------
        // 前回親品番
        // -----------------------------------------------------

        intent.putExtra(
            "LAST_PARENT_CODE",
            lastParentCode
        )


        // -----------------------------------------------------
        // 今回親品番
        // -----------------------------------------------------

        intent.putExtra(
            "CURRENT_PARENT_CODE",
            currentParentCode
        )


        startActivity(intent)
    }


    // =========================================================
    // Excel取得
    // =========================================================

    private fun getExcelFile(): File {

        val storagePath =
            Environment
                .getExternalStorageDirectory()


        val folder =
            File(
                storagePath,
                FOLDER_NAME
            )


        return File(
            folder,
            EXCEL_FILE_NAME
        )
    }


    // =========================================================
    // 部品比較
    // =========================================================

    private fun compareParts(
        lastParentCode: String,
        currentParentCode: String
    ) {

        try {

            // =================================================
            // Excelファイル
            // =================================================

            val file =
                getExcelFile()


            if (
                !file.exists()
            ) {

                binding.resultText.text =
                    "Excelファイルがありません"

                binding.summaryText.text =
                    ""

                binding.changeoverButton.isEnabled =
                    false

                return
            }


            // =================================================
            // Excelオープン
            // =================================================

            val inputStream =
                file.inputStream()


            val workbook =
                XSSFWorkbook(
                    inputStream
                )


            val sheet =
                workbook.getSheetAt(0)


            // =================================================
            // 前回部品
            //
            // key   = 場所
            // value = 子品番
            // =================================================

            val lastParts =
                mutableMapOf<String, String>()


            // =================================================
            // 今回部品
            //
            // key   = 場所
            // value = 子品番
            // =================================================

            val currentParts =
                mutableMapOf<String, String>()


            // =================================================
            // Excel読み込み
            // =================================================

            for (
            i in 1..sheet.lastRowNum
            ) {

                val row =
                    sheet.getRow(i)
                        ?: continue


                // ---------------------------------------------
                // A列：親品番
                // ---------------------------------------------

                val parent =
                    getCellValue(
                        row.getCell(0)
                    )
                        .trim()


                // ---------------------------------------------
                // B列：子品番
                // ---------------------------------------------

                val child =
                    getCellValue(
                        row.getCell(1)
                    )
                        .trim()


                // ---------------------------------------------
                // D列：場所
                // ---------------------------------------------

                val location =
                    getCellValue(
                        row.getCell(3)
                    )
                        .trim()


                // ---------------------------------------------
                // 不正データ
                // ---------------------------------------------

                if (
                    parent.isEmpty() ||
                    child.isEmpty()
                ) {

                    continue
                }


                // =================================================
                // 前回親品番
                // =================================================

                if (
                    parent ==
                    lastParentCode
                ) {

                    lastParts[
                        location
                    ] = child
                }


                // =================================================
                // 今回親品番
                // =================================================

                if (
                    parent ==
                    currentParentCode
                ) {

                    currentParts[
                        location
                    ] = child
                }
            }


            // =================================================
            // Excel終了
            // =================================================

            workbook.close()

            inputStream.close()


            // =================================================
            // 場所をまとめる
            // =================================================

            val locations =
                (
                        lastParts.keys +
                                currentParts.keys
                        )
                    .toSortedSet()


            // =================================================
            // 比較結果
            // =================================================

            val resultList =
                mutableListOf<String>()


            var matchCount =
                0


            var changeCount =
                0


            // =================================================
            // 場所ごとに比較
            // =================================================

            for (
            location in locations
            ) {

                val oldCode =
                    lastParts[
                        location
                    ]


                val newCode =
                    currentParts[
                        location
                    ]


                // =================================================
                // 前回・今回どちらも存在
                // =================================================

                if (
                    oldCode != null &&
                    newCode != null
                ) {

                    // -----------------------------------------
                    // 一致
                    // -----------------------------------------

                    if (
                        oldCode ==
                        newCode
                    ) {

                        resultList.add(

                            "○ [$location] " +
                                    "$oldCode ⇔ $newCode"
                        )


                        matchCount++


                    } else {

                        // -------------------------------------
                        // 変更
                        // -------------------------------------

                        resultList.add(

                            "× [$location] " +
                                    "$oldCode ⇒ $newCode"
                        )


                        changeCount++
                    }


                    // =================================================
                    // 前回だけ存在
                    // =================================================

                } else if (
                    oldCode != null
                ) {

                    resultList.add(

                        "削除 [$location] " +
                                "$oldCode ⇒ なし"
                    )


                    changeCount++


                    // =================================================
                    // 今回だけ存在
                    // =================================================

                } else if (
                    newCode != null
                ) {

                    resultList.add(

                        "追加 [$location] " +
                                "なし ⇒ $newCode"
                    )


                    changeCount++
                }
            }


            // =================================================
            // 件数表示
            // =================================================

            binding.summaryText.text =
                """
                一致：${matchCount}件
                変更・追加・削除：${changeCount}件
                """.trimIndent()


            // =================================================
            // ListView
            // =================================================

            val adapter =
                ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_1,
                    resultList
                )


            binding.resultList.adapter =
                adapter


            // =================================================
            // 比較結果なし
            // =================================================

            if (
                resultList.isEmpty()
            ) {

                binding.resultText.text =
                    "比較対象の部品がありません"


                binding.changeoverButton.isEnabled =
                    false


                return
            }


            // =================================================
            // 比較結果あり
            // =================================================

            binding.resultText.text =
                ""


            // =================================================
            // 段替え対象なし
            // =================================================

            if (
                changeCount == 0
            ) {

                binding.changeoverButton.isEnabled =
                    false

                binding.resultText.text =
                    "前回と同じ品番です"

            } else {

                // ---------------------------------------------
                // 段替え対象あり
                // ---------------------------------------------

                binding.changeoverButton.isEnabled =
                    true
            }

        } catch (
            e: Exception
        ) {

            binding.resultText.text =
                """
                比較エラー

                ${e.message}
                """.trimIndent()


            binding.summaryText.text =
                ""


            binding.changeoverButton.isEnabled =
                false
        }
    }


    // =========================================================
    // Excelセル値取得
    // =========================================================

    private fun getCellValue(
        cell: Cell?
    ): String {

        if (
            cell == null
        ) {

            return ""
        }


        return when (
            cell.cellType
        ) {

            // =================================================
            // 文字列
            // =================================================

            CellType.STRING -> {

                cell.stringCellValue
                    .trim()
            }


            // =================================================
            // 数値
            // =================================================

            CellType.NUMERIC -> {

                val value =
                    cell.numericCellValue


                if (
                    value ==
                    value.toLong().toDouble()
                ) {

                    value
                        .toLong()
                        .toString()

                } else {

                    value.toString()
                }
            }


            // =================================================
            // Boolean
            // =================================================

            CellType.BOOLEAN -> {

                cell.booleanCellValue
                    .toString()
            }


            // =================================================
            // その他
            // =================================================

            else -> {

                cell.toString()
                    .trim()
            }
        }
    }
}
