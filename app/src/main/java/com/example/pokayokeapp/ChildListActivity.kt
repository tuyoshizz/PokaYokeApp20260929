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

    private val list = mutableListOf<ChildItem>()

    private var parentCode: String = ""

    private lateinit var cameraExecutor: ExecutorService

    private var cameraProvider: ProcessCameraProvider? = null

    private var isCameraRunning = false

    companion object {

        private const val REQUEST_STORAGE = 1001

        // スマホ内部ストレージのフォルダ名
        private const val FOLDER_NAME = "PokaYoke"

        // Excelファイル名
        private const val EXCEL_FILE_NAME = "parts.xlsx"
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

        // 親品番
        parentCode =
            intent
                .getStringExtra("QR_CODE")
                ?.trim()
                ?: ""

        // =====================================================
        // RecyclerView
        // =====================================================

        adapter = ChildAdapter(

            list = list,

            // -----------------------------------------
            // 行をタップした場合
            // -----------------------------------------

            onClick = { item ->

                item.checked = !item.checked

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

            // 読み取りから1秒後に開始
            startDelay = 1000L,

            // 1秒間隔
            blinkInterval = 1000L,

            // 3回点滅
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
                ) != PackageManager.PERMISSION_GRANTED
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
            requestCode == REQUEST_STORAGE
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

        if (!folder.exists()) {

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

            // -----------------------------------------
            // Excelがない
            // -----------------------------------------

            if (!file.exists()) {

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
                XSSFWorkbook(inputStream)

            val sheet =
                workbook.getSheetAt(0)

            list.clear()

            // -----------------------------------------
            // Excel読み込み
            // -----------------------------------------

            // 1行目はタイトル行
            for (
            i in 1..sheet.lastRowNum
            ) {

                val row =
                    sheet.getRow(i)
                        ?: continue

                // A列：親品番
                val p =
                    getCellValue(
                        row.getCell(0)
                    )

                // B列：子品番
                val c =
                    getCellValue(
                        row.getCell(1)
                    )

                // C列：名称
                val name =
                    getCellValue(
                        row.getCell(2)
                    )

                // D列：場所
                val location =
                    getCellValue(
                        row.getCell(3)
                    )

                // -----------------------------------------
                // 親品番一致
                // -----------------------------------------

                if (
                    p.trim() ==
                    parentCode.trim()
                ) {

                    list.add(

                        ChildItem(

                            code = c,

                            name = name,

                            location = location,

                            checked = false,

                            parentCode = p
                        )
                    )
                }
            }

            workbook.close()

            inputStream.close()

            // -----------------------------------------
            // RecyclerView更新
            // -----------------------------------------

            adapter.notifyDataSetChanged()

            // -----------------------------------------
            // 残り件数
            // -----------------------------------------

            updateRemainingCount()

            // -----------------------------------------
            // 対象品番なし
            // -----------------------------------------

            if (list.isEmpty()) {

                binding.emptyText.text =
                    "品番が登録されていません"

            } else {

                binding.emptyText.text = ""

                Log.d(
                    "Excel",
                    "${list.size}件の部品を読み込みました"
                )
            }

        } catch (e: Exception) {

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
        cell: org.apache.poi.ss.usermodel.Cell?
    ): String {

        if (cell == null) {

            return ""
        }

        return when (
            cell.cellType
        ) {

            org.apache.poi.ss.usermodel.CellType.STRING -> {

                cell.stringCellValue.trim()
            }

            org.apache.poi.ss.usermodel.CellType.NUMERIC -> {

                val value =
                    cell.numericCellValue

                if (
                    value ==
                    value.toLong().toDouble()
                ) {

                    value.toLong().toString()

                } else {

                    value.toString()
                }
            }

            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> {

                cell.booleanCellValue.toString()
            }

            else -> {

                cell.toString().trim()
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

        if (isCameraRunning) {

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
    // QRコード処理
    // =========================================================

    private fun processScannedCode(
        code: String
    ) {

        // -----------------------------------------
        // 対象部品を検索
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

        // -----------------------------------------
        // チェック済みにする
        // -----------------------------------------

        target.checked = true

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
    // 全部チェック完了
    // =========================================================

    private fun checkCompletion() {

        if (
            list.isNotEmpty() &&
            list.all {

                it.checked
            }
        ) {

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
