package com.example.pokayokeapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pokayokeapp.databinding.ActivityHistoryBinding
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.IOException


class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    private val historyList =
        mutableListOf<HistoryItem>()

    private lateinit var adapter: HistoryAdapter


    companion object {

        // =====================================================
        // PokaYokeフォルダ選択
        // =====================================================

        private const val REQUEST_FOLDER = 2001

        // =====================================================
        // 保存先URIを記憶するPreference
        // =====================================================

        private const val PREF_NAME =
            "HistoryExportSettings"

        private const val KEY_FOLDER_URI =
            "POKAYOKE_FOLDER_URI"

        // =====================================================
        // Excelファイル名
        // =====================================================

        private const val EXCEL_FILE_NAME =
            "読取データ.xlsx"
    }


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
            ActivityHistoryBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)


        // =====================================================
        // RecyclerView
        // =====================================================

        adapter =
            HistoryAdapter(
                historyList
            )

        binding.historyRecyclerView.layoutManager =
            LinearLayoutManager(this)

        binding.historyRecyclerView.adapter =
            adapter


        // =====================================================
        // Excel出力
        // =====================================================

        binding.btnExport.setOnClickListener {

            exportHistoryToExcel()
        }


        // =====================================================
        // 戻る
        // =====================================================

        binding.btnBack.setOnClickListener {

            finish()
        }


        // =====================================================
        // 履歴削除
        // =====================================================

        binding.btnClear.setOnClickListener {

            showClearConfirmDialog()
        }


        // =====================================================
        // 履歴読み込み
        // =====================================================

        loadHistory()
    }


    // =========================================================
    // 履歴削除確認
    // =========================================================

    private fun showClearConfirmDialog() {

        AlertDialog.Builder(this)

            .setTitle(
                "履歴削除"
            )

            .setMessage(
                "読み取り履歴をすべて削除しますか？"
            )

            .setPositiveButton(
                "削除"
            ) { _, _ ->

                HistoryManager.clear(
                    this
                )

                loadHistory()
            }

            .setNegativeButton(
                "キャンセル",
                null
            )

            .show()
    }


    // =========================================================
    // Excel出力
    // =========================================================

    private fun exportHistoryToExcel() {

        // -----------------------------------------------------
        // 履歴取得
        // -----------------------------------------------------

        val history =
            HistoryManager.getHistory(
                this
            )


        // -----------------------------------------------------
        // 履歴がない
        // -----------------------------------------------------

        if (history.isEmpty()) {

            Toast.makeText(
                this,
                "出力する履歴がありません",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        // -----------------------------------------------------
        // 前回選択したフォルダを取得
        // -----------------------------------------------------

        val preferences =
            getSharedPreferences(
                PREF_NAME,
                MODE_PRIVATE
            )

        val savedUriString =
            preferences.getString(
                KEY_FOLDER_URI,
                null
            )


        // -----------------------------------------------------
        // 保存先が未設定
        // -----------------------------------------------------

        if (
            savedUriString.isNullOrEmpty()
        ) {

            selectExportFolder()

            return
        }


        try {

            val folderUri =
                Uri.parse(
                    savedUriString
                )


            // -------------------------------------------------
            // 権限確認
            // -------------------------------------------------

            val hasPermission =
                contentResolver.persistedUriPermissions.any {

                    it.uri == folderUri &&
                            it.isWritePermission
                }


            if (!hasPermission) {

                // ---------------------------------------------
                // 権限がなくなっている
                // ---------------------------------------------

                selectExportFolder()

                return
            }


            // -------------------------------------------------
            // Excel作成
            // -------------------------------------------------

            saveExcelToFolder(
                folderUri,
                history
            )

        } catch (
            e: Exception
        ) {

            Log.e(
                "History",
                "保存先確認失敗",
                e
            )

            // -------------------------------------------------
            // 保存先を再選択
            // -------------------------------------------------

            selectExportFolder()
        }
    }


    // =========================================================
    // 保存先フォルダ選択
    // =========================================================

    private fun selectExportFolder() {

        AlertDialog.Builder(this)

            .setTitle(
                "Excel保存先"
            )

            .setMessage(
                "「PokaYoke」フォルダを選択してください。"
            )

            .setPositiveButton(
                "選択"
            ) { _, _ ->

                val intent =
                    Intent(
                        Intent.ACTION_OPEN_DOCUMENT_TREE
                    )

                intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                )


                // -------------------------------------------------
                // Androidのファイル選択画面を開く
                // -------------------------------------------------

                startActivityForResult(
                    intent,
                    REQUEST_FOLDER
                )
            }

            .setNegativeButton(
                "キャンセル",
                null
            )

            .show()
    }


    // =========================================================
    // フォルダ選択結果
    // =========================================================

    @Deprecated(
        "Activity Result APIへ移行可能ですが、現在のプロジェクトではこの方法で動作します"
    )
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )


        if (
            requestCode != REQUEST_FOLDER ||
            resultCode != RESULT_OK ||
            data?.data == null
        ) {

            return
        }


        val folderUri =
            data.data
                ?: return


        try {

            // =================================================
            // 永続的な読み書き権限を取得
            // =================================================

            val takeFlags =
                data.flags and
                        (
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                )


            contentResolver.takePersistableUriPermission(
                folderUri,
                takeFlags
            )


            // =================================================
            // 保存先を記憶
            // =================================================

            getSharedPreferences(
                PREF_NAME,
                MODE_PRIVATE
            )
                .edit()
                .putString(
                    KEY_FOLDER_URI,
                    folderUri.toString()
                )
                .apply()


            // =================================================
            // 履歴取得
            // =================================================

            val history =
                HistoryManager.getHistory(
                    this
                )


            if (history.isEmpty()) {

                Toast.makeText(
                    this,
                    "出力する履歴がありません",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }


            // =================================================
            // Excel保存
            // =================================================

            saveExcelToFolder(
                folderUri,
                history
            )

        } catch (
            e: Exception
        ) {

            Log.e(
                "History",
                "保存先設定失敗",
                e
            )

            Toast.makeText(
                this,
                "保存先の設定に失敗しました\n${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    // =========================================================
    // Excel保存
    // =========================================================

    private fun saveExcelToFolder(
        folderUri: Uri,
        history: List<HistoryItem>
    ) {

        var workbook:
                XSSFWorkbook? = null

        try {

            // =================================================
            // フォルダ取得
            // =================================================

            val folder =
                DocumentFile.fromTreeUri(
                    this,
                    folderUri
                )


            if (
                folder == null ||
                !folder.isDirectory
            ) {

                Toast.makeText(
                    this,
                    "保存先フォルダを確認できません",
                    Toast.LENGTH_LONG
                ).show()

                return
            }


            // =================================================
            // 既存のExcelを取得
            // =================================================

            val oldFile =
                folder.findFile(
                    EXCEL_FILE_NAME
                )


            // =================================================
            // 既存ファイル削除
            // =================================================

            if (oldFile != null) {

                oldFile.delete()
            }


            // =================================================
            // 新しいExcelファイル作成
            // =================================================

            val file =
                folder.createFile(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    EXCEL_FILE_NAME
                )


            if (file == null) {

                Toast.makeText(
                    this,
                    "Excelファイルを作成できませんでした",
                    Toast.LENGTH_LONG
                ).show()

                return
            }


            // =================================================
            // Excel作成
            // =================================================

            workbook =
                XSSFWorkbook()


            val sheet =
                workbook.createSheet(
                    "読取履歴"
                )


            // =================================================
            // ヘッダー
            // =================================================

            val headerRow =
                sheet.createRow(0)


            headerRow
                .createCell(0)
                .setCellValue("日時")


            headerRow
                .createCell(1)
                .setCellValue("モード")


            headerRow
                .createCell(2)
                .setCellValue("操作")


            headerRow
                .createCell(3)
                .setCellValue("親品番")


            headerRow
                .createCell(4)
                .setCellValue("子品番")


            headerRow
                .createCell(5)
                .setCellValue("場所")


            headerRow
                .createCell(6)
                .setCellValue("方法")


            // =================================================
            // 履歴データ
            // =================================================

            history.forEachIndexed { index, item ->

                val row =
                    sheet.createRow(
                        index + 1
                    )


                row
                    .createCell(0)
                    .setCellValue(
                        item.date
                    )


                row
                    .createCell(1)
                    .setCellValue(
                        item.mode
                    )


                row
                    .createCell(2)
                    .setCellValue(
                        item.action
                    )


                row
                    .createCell(3)
                    .setCellValue(
                        item.parentCode
                    )


                row
                    .createCell(4)
                    .setCellValue(
                        item.childCode
                    )


                row
                    .createCell(5)
                    .setCellValue(
                        item.location
                    )


                row
                    .createCell(6)
                    .setCellValue(
                        item.method
                    )
            }


            // =================================================
            // 列幅
            // =================================================

            sheet.setColumnWidth(
                0,
                20 * 256
            )

            sheet.setColumnWidth(
                1,
                12 * 256
            )

            sheet.setColumnWidth(
                2,
                25 * 256
            )

            sheet.setColumnWidth(
                3,
                20 * 256
            )

            sheet.setColumnWidth(
                4,
                20 * 256
            )

            sheet.setColumnWidth(
                5,
                15 * 256
            )

            sheet.setColumnWidth(
                6,
                12 * 256
            )


            // =================================================
            // Excel書き込み
            // =================================================

            contentResolver
                .openOutputStream(
                    file.uri
                )
                ?.use { outputStream ->

                    workbook.write(
                        outputStream
                    )
                }
                ?: throw IOException(
                    "Excelファイルを開けませんでした"
                )


            // =================================================
            // 完了
            // =================================================

            Toast.makeText(
                this,
                "Excelを保存しました\n読取データ.xlsx",
                Toast.LENGTH_LONG
            ).show()


            Log.d(
                "History",
                "Excel保存成功: ${file.uri}"
            )

        } catch (
            e: Exception
        ) {

            Log.e(
                "History",
                "Excel出力失敗",
                e
            )

            Toast.makeText(
                this,
                "Excel出力に失敗しました\n${e.message}",
                Toast.LENGTH_LONG
            ).show()

        } finally {

            // =================================================
            // Workbook解放
            // =================================================

            try {

                workbook?.close()

            } catch (
                ignored: Exception
            ) {
            }
        }
    }


    // =========================================================
    // 画面に戻った時
    // =========================================================

    override fun onResume() {

        super.onResume()

        loadHistory()
    }


    // =========================================================
    // 履歴読み込み
    // =========================================================

    private fun loadHistory() {

        val history =
            HistoryManager.getHistory(
                this
            )


        historyList.clear()

        historyList.addAll(
            history
        )


        adapter.notifyDataSetChanged()


        if (
            historyList.isEmpty()
        ) {

            binding.emptyText.text =
                "読み取り履歴はありません"

        } else {

            binding.emptyText.text =
                "履歴 ${historyList.size} 件"
        }
    }
}
