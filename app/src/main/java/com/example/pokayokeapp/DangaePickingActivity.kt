package com.example.pokayokeapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
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
import com.example.pokayokeapp.databinding.ActivityDangaePickingBinding
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class DangaePickingActivity : AppCompatActivity() {

    private lateinit var binding:
            ActivityDangaePickingBinding

    private lateinit var adapter:
            ChildAdapter

    private val list =
        mutableListOf<ChildItem>()

    // =========================================================
    // 親品番
    // =========================================================

    private var lastParentCode = ""

    private var currentParentCode = ""


    // =========================================================
    // Camera
    // =========================================================

    private lateinit var cameraExecutor:
            ExecutorService

    private var cameraProvider:
            ProcessCameraProvider? = null

    private var isCameraRunning = false


    // =========================================================
    // 完了フラグ
    // =========================================================

    private var isCompleted = false


    // =========================================================
    // 設定
    // =========================================================

    companion object {

        private const val REQUEST_STORAGE = 1002

        private const val FOLDER_NAME =
            "PokaYoke"

        private const val EXCEL_FILE_NAME =
            "parts.xlsx"

        private const val SETTINGS_NAME =
            "PokaYokeSettings"

        private const val LAST_PARENT_KEY =
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
            ActivityDangaePickingBinding.inflate(
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


        Log.d(
            "Dangae",
            "前回親品番: $lastParentCode"
        )

        Log.d(
            "Dangae",
            "今回親品番: $currentParentCode"
        )


        // =====================================================
        // タイトル
        // =====================================================

        binding.titleText.text =
            "段替え部品供給"


        binding.parentText.text =
            "今回生産品番：$currentParentCode"


        // =====================================================
        // RecyclerView
        // =====================================================

        adapter =
            ChildAdapter(

                list = list,

                // -----------------------------------------
                // 行タップ
                // -----------------------------------------

                onClick = { item ->

                    if (isCompleted) {
                        return@ChildAdapter
                    }

                    item.checked =
                        !item.checked

                    HistoryManager.addHistory(

                        this,

                        mode = "段替え",

                        action =
                            if (item.checked) {
                                "段替え部品チェック"
                            } else {
                                "段替え部品チェック解除"
                            },

                        parentCode =
                            currentParentCode,

                        childCode =
                            item.code,

                        location =
                            item.location,

                        method = "タップ"
                    )

                    val position =
                        list.indexOf(item)

                    if (position != -1) {

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
        // 戻る
        // =====================================================

        binding.backButton.setOnClickListener {

            showBackConfirmDialog()
        }


        // =====================================================
        // Camera Executor
        // =====================================================

        cameraExecutor =
            Executors.newSingleThreadExecutor()


        // =====================================================
        // Excel
        // =====================================================

        checkStoragePermission()
    }


    // =========================================================
    // ストレージ権限
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

                loadChangeoverParts()

                initCamera()
            }

        } else {

            loadChangeoverParts()

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

                loadChangeoverParts()

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
    // 段替え対象部品読み込み
    // =========================================================

    private fun loadChangeoverParts() {

        try {

            val file =
                getExcelFile()


            // =================================================
            // Excel存在確認
            // =================================================

            if (
                !file.exists()
            ) {

                binding.emptyText.text =
                    "Excelファイルがありません"

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
            // 場所 → 子品番
            // =================================================

            val lastParts =
                mutableMapOf<String, String>()


            // =================================================
            // 今回部品
            //
            // 場所 → ChildItem
            // =================================================

            val currentParts =
                mutableMapOf<String, ChildItem>()


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

                val parent =
                    getCellValue(
                        row.getCell(0)
                    )
                        .trim()


                // -----------------------------------------
                // B列：子品番
                // -----------------------------------------

                val child =
                    getCellValue(
                        row.getCell(1)
                    )
                        .trim()


                // -----------------------------------------
                // C列：名称
                // -----------------------------------------

                val name =
                    getCellValue(
                        row.getCell(2)
                    )
                        .trim()


                // -----------------------------------------
                // D列：場所
                // -----------------------------------------

                val location =
                    getCellValue(
                        row.getCell(3)
                    )
                        .trim()


                // -----------------------------------------
                // 不正データ
                // -----------------------------------------

                if (
                    parent.isEmpty() ||
                    child.isEmpty() ||
                    location.isEmpty()
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
                    ] =
                        ChildItem(

                            code = child,

                            name = name,

                            location = location,

                            checked = false,

                            parentCode = parent
                        )
                }
            }


            // =================================================
            // Excel終了
            // =================================================

            workbook.close()

            inputStream.close()


            // =================================================
            // リストクリア
            // =================================================

            list.clear()


            // =================================================
            // 段替え対象を抽出
            // =================================================

            for (
            entry in currentParts
            ) {

                val location =
                    entry.key

                val currentItem =
                    entry.value

                val oldCode =
                    lastParts[
                        location
                    ]


                // -----------------------------------------
                // 前回に存在しない
                // → 新規追加
                // -----------------------------------------

                if (
                    oldCode == null
                ) {

                    list.add(
                        currentItem
                    )

                    continue
                }


                // -----------------------------------------
                // 前回と違う
                // → 段替え対象
                // -----------------------------------------

                if (
                    oldCode !=
                    currentItem.code
                ) {

                    list.add(
                        currentItem
                    )
                }
            }


            // =================================================
            // RecyclerView更新
            // =================================================

            adapter.notifyDataSetChanged()


            // =================================================
            // 残り件数
            // =================================================

            updateRemainingCount()


            // =================================================
            // 対象なし
            // =================================================

            if (
                list.isEmpty()
            ) {

                binding.emptyText.text =
                    "段替え対象の部品はありません"

            } else {

                binding.emptyText.text = ""

                Log.d(
                    "Dangae",
                    "段替え対象: ${list.size}件"
                )
            }

        } catch (
            e: Exception
        ) {

            Log.e(
                "Dangae",
                "段替え部品読み込み失敗",
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
    // Excelセル値取得
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

            // =================================================
            // 文字列
            // =================================================

            org.apache.poi.ss.usermodel.CellType.STRING -> {

                cell.stringCellValue
                    .trim()
            }


            // =================================================
            // 数値
            // =================================================

            org.apache.poi.ss.usermodel.CellType.NUMERIC -> {

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

            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> {

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
        // QR Scanner
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

                        // -------------------------------------
                        // 完了後はQR処理しない
                        // -------------------------------------

                        if (isCompleted) {
                            return@addOnSuccessListener
                        }


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
    // QRコード処理
    // =========================================================

    private fun processScannedCode(
        code: String
    ) {

        // 完了後は処理しない
        if (isCompleted) {
            return
        }


        val target =
            list.firstOrNull {

                it.code.trim() ==
                        code.trim()
            }


        // =====================================================
        // 対象外
        // =====================================================

        if (
            target == null
        ) {

            val toast =
                Toast.makeText(
                    this,
                    "段替え対象外の部品です",
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


        // =====================================================
        // すでに完了
        // =====================================================

        if (
            target.checked
        ) {

            return
        }


        // =====================================================
        // チェック
        // =====================================================

        target.checked = true

        HistoryManager.addHistory(

            this,

            mode = "段替え",

            action = "段替え部品チェック",

            parentCode =
                currentParentCode,

            childCode =
                target.code,

            location =
                target.location,

            method = "QR"
        )



        val position =
            list.indexOf(target)


        if (
            position != -1
        ) {

            adapter.notifyItemChanged(
                position
            )
        }


        // =====================================================
        // 残り件数
        // =====================================================

        updateRemainingCount()


        // =====================================================
        // 完了確認
        // =====================================================

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
    // 完了確認
    // =========================================================

    private fun checkCompletion() {

        // =================================================
        // すでに完了している場合は何もしない
        // =================================================

        if (isCompleted) {
            return
        }


        // =================================================
        // 全部チェック完了したか確認
        // =================================================

        if (
            list.isNotEmpty() &&
            list.all {
                it.checked
            }
        ) {

            // =================================================
            // 完了フラグ
            // =================================================

            isCompleted = true


            // =================================================
            // ★ ここで初めて「段替え完了」を履歴保存
            // =================================================

            HistoryManager.addHistory(

                this,

                mode = "段替え",

                action = "段替え完了",

                parentCode =
                    currentParentCode
            )


            Log.d(
                "Dangae",
                "段替え部品供給完了"
            )


            // =================================================
            // 今回親品番を前回生産品番として保存
            // =================================================

            if (
                currentParentCode.isNotEmpty()
            ) {

                val preferences =
                    getSharedPreferences(
                        SETTINGS_NAME,
                        MODE_PRIVATE
                    )

                preferences
                    .edit()
                    .putString(
                        LAST_PARENT_KEY,
                        currentParentCode
                    )
                    .apply()

                Log.d(
                    "Dangae",
                    "LAST_PARENT_CODEを更新: $currentParentCode"
                )

            } else {

                Log.e(
                    "Dangae",
                    "currentParentCodeが空のためLAST_PARENT_CODEを更新できません"
                )
            }


            // =================================================
            // カメラ停止
            // =================================================

            cameraProvider?.unbindAll()

            isCameraRunning = false


            // =================================================
            // 完了画面へ
            // =================================================

            val intent =
                Intent(
                    this,
                    DangaeCompleteActivity::class.java
                )

            intent.putExtra(
                "CURRENT_PARENT_CODE",
                currentParentCode
            )

            startActivity(intent)

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
                "段替え作業を中断して戻りますか？"
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

        if (!isCompleted) {
            startCamera()
        }
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

        cameraProvider?.unbindAll()

        cameraExecutor.shutdown()
    }
}
