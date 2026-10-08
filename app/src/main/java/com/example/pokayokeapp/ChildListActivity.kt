package com.example.pokayokeapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.pokayokeapp.databinding.ActivityChildListBinding
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class ChildListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChildListBinding

    private lateinit var adapter: ChildAdapter

    private val list =
        mutableListOf<ChildItem>()

    private var parentCode: String = ""

    private lateinit var cameraExecutor: ExecutorService

    private var cameraProvider:
            ProcessCameraProvider? = null

    private var isCameraRunning = false


    companion object {

        private const val REQUEST_STORAGE = 1001

        // -----------------------------------------
        // スマホ内部ストレージのフォルダ名
        // -----------------------------------------

        private const val FOLDER_NAME =
            "PokaYoke"

        // -----------------------------------------
        // Excelファイル名
        // -----------------------------------------

        private const val EXCEL_FILE_NAME =
            "parts.xlsx"

        // -----------------------------------------
        // 前回生産親品番を保存するPreference
        // -----------------------------------------

        private const val PREF_NAME =
            "PokaYokeSettings"

        private const val KEY_LAST_PARENT_CODE =
            "LAST_PARENT_CODE"
    }


    // =========================================================
    // onCreate
    // =========================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)


        binding =
            ActivityChildListBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)


        // =====================================================
        // 親品番取得
        // =====================================================

        val qrCode =
            intent
                .getStringExtra("QR_CODE")
                ?.trim()
                ?: ""


        parentCode =
            extractParentCode(qrCode)


        Log.d(
            "PokaYoke",
            "QR読み取り結果: [$qrCode]"
        )


        Log.d(
            "PokaYoke",
            "今回の親品番: [$parentCode]"
        )


        // =====================================================
        // 親品番読み取り履歴
        // =====================================================

        HistoryManager.addHistory(

            this,

            mode = "ピッキング",

            action = "親品番読取",

            parentCode =
                parentCode,

            method = "QR"
        )


        // =====================================================
        // RecyclerView
        // =====================================================

        adapter =
            ChildAdapter(

                list = list,

                // -----------------------------------------
                // 行をタップした場合
                // -----------------------------------------

                onClick = { item ->

                    // -----------------------------------------
                    // チェック状態変更
                    // -----------------------------------------

                    item.checked =
                        !item.checked


                    // =================================================
                    // ★ 点滅開始時刻
                    // =================================================

                    if (item.checked) {

                        // チェックした瞬間を記録

                        item.blinkStartTime =
                            System.currentTimeMillis()

                    } else {

                        // チェック解除

                        item.blinkStartTime =
                            0L
                    }


                    // -----------------------------------------
                    // 履歴記録
                    // -----------------------------------------

                    HistoryManager.addHistory(

                        this,

                        mode = "ピッキング",

                        action =
                            if (item.checked) {
                                "部品チェック"
                            } else {
                                "部品チェック解除"
                            },

                        parentCode =
                            parentCode,

                        childCode =
                            item.code,

                        location =
                            item.location,

                        method = "タップ"
                    )


                    // -----------------------------------------
                    // 表示更新
                    // -----------------------------------------

                    val position =
                        list.indexOf(item)


                    if (
                        position != -1
                    ) {

                        adapter.notifyItemChanged(
                            position
                        )
                    }


                    updateRemainingCount()

                    checkCompletion()
                },


                // -----------------------------------------
                // 点滅設定
                // -----------------------------------------

                startDelay = 500L,

                blinkInterval = 200L,

                blinkCount = 3
            )


        binding.recyclerView.layoutManager =
            GridLayoutManager(
                this,
                2
            )


        binding.recyclerView.adapter =
            adapter


        // =====================================================
        // 戻るボタン
        // =====================================================

        binding.backButton.setOnClickListener {

            showBackConfirmDialog()
        }


        // =====================================================
        // Camera
        // =====================================================

        cameraExecutor =
            Executors.newSingleThreadExecutor()


        // =====================================================
        // Excel読み込み
        // =====================================================

        checkStoragePermission()
    }


    // =========================================================
    // QRコードから親品番を抽出
    // =========================================================

    private fun extractParentCode(
        code: String
    ): String {

        val text =
            code.trim()


        Log.d(
            "PokaYoke",
            "親品番抽出前QR: [$text]"
        )


        // -----------------------------------------
        // 6文字 + "-" + 3文字
        // -----------------------------------------

        val pattern =
            Regex(
                "[A-Za-z0-9]{6}-[A-Za-z0-9]{3}"
            )


        // -----------------------------------------
        // QR全体から検索
        // -----------------------------------------

        val match =
            pattern.find(text)


        if (
            match != null
        ) {

            val result =
                match.value


            Log.d(
                "PokaYoke",
                "抽出した親品番: [$result]"
            )


            return result
        }


        Log.w(
            "PokaYoke",
            "6文字-3文字の親品番を検出できませんでした"
        )


        Log.w(
            "PokaYoke",
            "QR内容: [$text]"
        )


        return text
    }


    // =========================================================
    // ストレージ権限確認
    // =========================================================

    private fun checkStoragePermission() {

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.M
        ) {

            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {

                ActivityCompat.requestPermissions(

                    this,

                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ),

                    REQUEST_STORAGE
                )

            } else {

                loadExcelData()

                initCamera()
            }

        } else {

            loadExcelData()

            initCamera()
        }
    }


    // =========================================================
    // 権限結果
    // =========================================================

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )


        if (
            requestCode ==
            REQUEST_STORAGE
        ) {

            if (
                grantResults.isNotEmpty() &&
                grantResults[0] ==
                PackageManager.PERMISSION_GRANTED
            ) {

                loadExcelData()

                initCamera()

            } else {

                binding.emptyText.text =
                    "ストレージへのアクセスが許可されていません"


                Toast.makeText(
                    this,
                    "Excelを読み込むにはストレージ権限が必要です",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    // =========================================================
    // Excelファイル取得
    // =========================================================

    private fun getExcelFile(): File {

        val storagePath =
            android.os.Environment
                .getExternalStorageDirectory()


        val folder =
            File(
                storagePath,
                FOLDER_NAME
            )


        if (
            !folder.exists()
        ) {

            folder.mkdirs()
        }


        return File(
            folder,
            EXCEL_FILE_NAME
        )
    }


    // =========================================================
    // Excel読込
    // =========================================================

    private fun loadExcelData() {

        try {

            val file =
                getExcelFile()


            Log.d(
                "Excel",
                "Excelパス: ${file.absolutePath}"
            )


            if (
                !file.exists()
            ) {

                list.clear()

                adapter.notifyDataSetChanged()


                binding.emptyText.text =
                    """
                    Excelファイルがありません。

                    スマホをPCに接続して、

                    内部ストレージ
                    ↓
                    PokaYoke

                    に parts.xlsx をコピーしてください。
                    """.trimIndent()


                binding.remainingText.text =
                    "残り: 0"


                Log.e(
                    "Excel",
                    "Excelが存在しません: ${file.absolutePath}"
                )


                return
            }


            Log.d(
                "Excel",
                "Excel読み込み開始"
            )


            val inputStream =
                file.inputStream()


            val workbook =
                XSSFWorkbook(
                    inputStream
                )


            val sheet =
                workbook.getSheetAt(0)


            list.clear()


            // =================================================
            // Excel読み込み
            // =================================================

            for (
            i in 1..sheet.lastRowNum
            ) {

                val row =
                    sheet.getRow(i)
                        ?: continue


                // -----------------------------------------
                // A列：親品番
                // -----------------------------------------

                val p =
                    getCellValue(
                        row.getCell(0)
                    )


                // -----------------------------------------
                // B列：子品番
                // -----------------------------------------

                val c =
                    getCellValue(
                        row.getCell(1)
                    )


                // -----------------------------------------
                // C列：名称
                // -----------------------------------------

                val name =
                    getCellValue(
                        row.getCell(2)
                    )


                // -----------------------------------------
                // D列：場所
                // -----------------------------------------

                val location =
                    getCellValue(
                        row.getCell(3)
                    )


                // -----------------------------------------
                // 親品番一致
                // -----------------------------------------

                if (
                    p.trim()
                        .equals(
                            parentCode.trim(),
                            ignoreCase = true
                        )
                ) {

                    list.add(

                        ChildItem(

                            code = c,

                            name = name,

                            location = location,

                            checked = false,

                            parentCode = p,

                            blinkStartTime = 0L
                        )
                    )
                }
            }


            workbook.close()

            inputStream.close()


            // =================================================
            // RecyclerView更新
            // =================================================

            adapter.notifyDataSetChanged()


            // =================================================
            // 残り件数
            // =================================================

            updateRemainingCount()


            // =================================================
            // 対象品番なし
            // =================================================

            if (
                list.isEmpty()
            ) {

                binding.emptyText.text =
                    "品番が登録されていません"


                Log.w(
                    "Excel",
                    "親品番 [$parentCode] に一致するデータがありません"
                )

            } else {

                binding.emptyText.text = ""


                Log.d(
                    "Excel",
                    "${list.size}件の部品を読み込みました"
                )
            }

        } catch (
            e: Exception
        ) {

            Log.e(
                "Excel",
                "Excel読み込み失敗",
                e
            )


            binding.emptyText.text =
                """
                Excel読み込みエラー

                ${e.message}
                """.trimIndent()
        }
    }


    // =========================================================
    // Excelセルの値取得
    // =========================================================

    private fun getCellValue(
        cell:
        org.apache.poi.ss.usermodel.Cell?
    ): String {

        if (
            cell == null
        ) {

            return ""
        }


        return when (
            cell.cellType
        ) {

            org.apache.poi.ss.usermodel.CellType.STRING -> {

                cell.stringCellValue
                    .trim()
            }


            org.apache.poi.ss.usermodel.CellType.NUMERIC -> {

                val value =
                    cell.numericCellValue


                if (
                    value ==
                    value.toLong().toDouble()
                ) {

                    value.toLong()
                        .toString()

                } else {

                    value.toString()
                }
            }


            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> {

                cell.booleanCellValue
                    .toString()
            }


            else -> {

                cell.toString()
                    .trim()
            }
        }
    }


    // =========================================================
    // Camera初期化
    // =========================================================

    private fun initCamera() {

        val future =
            ProcessCameraProvider
                .getInstance(this)


        future.addListener(

            {

                cameraProvider =
                    future.get()


                startCamera()

            },

            ContextCompat.getMainExecutor(
                this
            )
        )
    }


    // =========================================================
    // Camera開始
    // =========================================================

    private fun startCamera() {

        if (
            isCameraRunning
        ) {

            return
        }


        val provider =
            cameraProvider
                ?: return


        provider.unbindAll()


        // =====================================================
        // Preview
        // =====================================================

        val preview =
            Preview.Builder()
                .build()
                .also {

                    it.setSurfaceProvider(
                        binding.previewView
                            .surfaceProvider
                    )
                }


        // =====================================================
        // ImageAnalysis
        // =====================================================

        val analysis =
            ImageAnalysis.Builder()
                .setBackpressureStrategy(
                    ImageAnalysis
                        .STRATEGY_KEEP_ONLY_LATEST
                )
                .build()


        // =====================================================
        // QR / Barcode Scanner
        // =====================================================

        val scanner =
            BarcodeScanning.getClient()


        analysis.setAnalyzer(
            cameraExecutor
        ) { imageProxy ->

            val mediaImage =
                imageProxy.image


            if (
                mediaImage != null
            ) {

                val image =
                    InputImage.fromMediaImage(

                        mediaImage,

                        imageProxy
                            .imageInfo
                            .rotationDegrees
                    )


                scanner.process(image)

                    .addOnSuccessListener {

                            barcodes ->

                        val code =
                            barcodes
                                .firstOrNull()
                                ?.rawValue
                                ?.trim()
                                ?: ""


                        if (
                            code.isNotEmpty()
                        ) {

                            runOnUiThread {

                                processScannedCode(
                                    code
                                )
                            }
                        }
                    }

                    .addOnCompleteListener {

                        imageProxy.close()
                    }

            } else {

                imageProxy.close()
            }
        }


        // =====================================================
        // Camera起動
        // =====================================================

        provider.bindToLifecycle(

            this,

            CameraSelector
                .DEFAULT_BACK_CAMERA,

            preview,

            analysis
        )


        isCameraRunning = true
    }


    // =========================================================
    // 子部品QRコード処理
    // =========================================================

    private fun processScannedCode(
        code: String
    ) {

        // -----------------------------------------
        // 子部品QRは読み取った内容そのままで照合
        // -----------------------------------------

        val target =
            list.firstOrNull {

                it.code.trim() ==
                        code.trim()
            }


        // -----------------------------------------
        // 対象外
        // -----------------------------------------

        if (
            target == null
        ) {

            val toast =
                Toast.makeText(
                    this,
                    "対象外部品です",
                    Toast.LENGTH_SHORT
                )


            toast.show()


            Handler(
                Looper.getMainLooper()
            ).postDelayed(
                {
                    toast.cancel()
                },
                700
            )


            return
        }


        // -----------------------------------------
        // すでにチェック済み
        // -----------------------------------------

        if (
            target.checked
        ) {

            return
        }


        // =================================================
        // ★ チェック済みにする
        // =================================================

        target.checked = true


        // =================================================
        // ★ 点滅開始時刻を記録
        // =================================================

        target.blinkStartTime =
            System.currentTimeMillis()


        // -----------------------------------------
        // 履歴
        // -----------------------------------------

        HistoryManager.addHistory(

            this,

            mode = "ピッキング",

            action = "部品チェック",

            parentCode =
                parentCode,

            childCode =
                target.code,

            location =
                target.location,

            method = "QR"
        )


        // -----------------------------------------
        // 対象行だけ更新
        // -----------------------------------------

        val position =
            list.indexOf(target)


        if (
            position != -1
        ) {

            adapter.notifyItemChanged(
                position
            )
        }


        // -----------------------------------------
        // 残り件数
        // -----------------------------------------

        updateRemainingCount()


        // -----------------------------------------
        // 完了チェック
        // -----------------------------------------

        checkCompletion()
    }


    // =========================================================
    // 残り件数
    // =========================================================

    private fun updateRemainingCount() {

        val remaining =
            list.count {

                !it.checked
            }


        binding.remainingText.text =
            "残り: $remaining"
    }


    // =========================================================
    // 前回生産親品番を保存
    // =========================================================

    private fun saveLastProductionParentCode() {

        val preferences =
            getSharedPreferences(
                PREF_NAME,
                MODE_PRIVATE
            )


        val code =
            parentCode.trim()


        preferences
            .edit()
            .putString(
                KEY_LAST_PARENT_CODE,
                code
            )
            .apply()


        Log.d(
            "PokaYoke",
            "前回生産親品番を保存しました: $code"
        )
    }


    // =========================================================
    // 全部チェック完了
    // =========================================================

    private fun checkCompletion() {

        if (
            list.isNotEmpty() &&
            list.all {
                it.checked
            }
        ) {

            // =============================================
            // ピッキング完了
            // =============================================

            HistoryManager.addHistory(

                this,

                mode = "ピッキング",

                action = "ピッキング完了",

                parentCode =
                    parentCode
            )


            // =============================================
            // 前回生産親品番として保存
            // =============================================

            saveLastProductionParentCode()


            Log.d(
                "PokaYoke",
                "ピッキング完了"
            )


            Log.d(
                "PokaYoke",
                "次回段替え用の前回親品番: $parentCode"
            )


            // =============================================
            // 次の画面
            // =============================================

            startActivity(

                Intent(
                    this,
                    NextActivity::class.java
                )
            )


            finish()
        }
    }


    // =========================================================
    // 戻る確認
    // =========================================================

    private fun showBackConfirmDialog() {

        AlertDialog.Builder(this)

            .setTitle(
                "確認"
            )

            .setMessage(
                "戻りますか？"
            )

            .setPositiveButton(
                "はい"
            ) { _, _ ->

                finish()
            }

            .setNegativeButton(
                "いいえ",
                null
            )

            .show()
    }


    // =========================================================
    // Resume
    // =========================================================

    override fun onResume() {

        super.onResume()

        startCamera()
    }


    // =========================================================
    // Pause
    // =========================================================

    override fun onPause() {

        super.onPause()

        cameraProvider?.unbindAll()

        isCameraRunning = false
    }


    // =========================================================
    // Destroy
    // =========================================================

    override fun onDestroy() {

        super.onDestroy()

        cameraExecutor.shutdown()
    }
}