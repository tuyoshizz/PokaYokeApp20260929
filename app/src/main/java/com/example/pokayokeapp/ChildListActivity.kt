package com.example.pokayokeapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.pokayokeapp.databinding.ActivityChildListBinding
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper


class ChildListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChildListBinding
    private lateinit var adapter: ChildAdapter

    private val list = mutableListOf<ChildItem>()
    private var parentCode: String = ""

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var isCameraRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChildListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        parentCode = intent.getStringExtra("QR_CODE")?.trim() ?: ""

        adapter = ChildAdapter(list) { item ->
            item.checked = !item.checked
            adapter.notifyDataSetChanged()
            updateRemainingCount()
            checkCompletion()
        }

        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.adapter = adapter

        binding.backButton.setOnClickListener {
            showBackConfirmDialog()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // 🔥 内部ストレージにコピー
        copyExcelIfNeeded()

        // 🔥 読み込み
        loadExcelData()

        initCamera()
    }

    // =========================
    // Excelコピー（内部ストレージ）
    // =========================
    private fun copyExcelIfNeeded() {

        val file = File(filesDir, "parts.xlsx")

        if (!file.exists()) {
            try {
                val input = assets.open("parts.xlsx")
                val output = file.outputStream()

                input.copyTo(output)

                input.close()
                output.close()

                Log.d("FILE", "内部ストレージにコピー完了")

            } catch (e: Exception) {
                Log.e("FILE", "コピー失敗", e)
            }
        }
    }

    // =========================
    // Excel読込
    // =========================
    private fun loadExcelData() {

        try {

            val file = File(filesDir, "parts.xlsx")

            if (!file.exists()) {
                binding.emptyText.text = "Excelが存在しません"
                return
            }

            val inputStream = file.inputStream()
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)

            list.clear()

            for (i in 1..sheet.lastRowNum) {

                val row = sheet.getRow(i) ?: continue

                val p = row.getCell(0)?.toString()?.replace(".0", "") ?: ""
                val c = row.getCell(1)?.toString()?.replace(".0", "") ?: ""

                if (p == parentCode) {
                    list.add(
                        ChildItem(
                            code = c,
                            name = row.getCell(2)?.toString() ?: "",
                            location = row.getCell(3)?.toString() ?: "",
                            checked = false,
                            parentCode = p
                        )
                    )
                }
            }

            workbook.close()
            inputStream.close()

            adapter.notifyDataSetChanged()
            updateRemainingCount()

            if (list.isEmpty()) {
                binding.emptyText.text = "対象部品が見つかりません"
            }

        } catch (e: Exception) {
            Log.e("Excel", "読み込み失敗", e)
            binding.emptyText.text = "Excel読み込みエラー"
        }
    }

    // =========================
    // Camera
    // =========================
    private fun initCamera() {

        val future = ProcessCameraProvider.getInstance(this)

        future.addListener({
            cameraProvider = future.get()
            startCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startCamera() {

        if (isCameraRunning) return

        val provider = cameraProvider ?: return

        provider.unbindAll()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.previewView.surfaceProvider)
        }

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val scanner = BarcodeScanning.getClient()

        analysis.setAnalyzer(cameraExecutor) { imageProxy ->

            val mediaImage = imageProxy.image

            if (mediaImage != null) {

                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->

                        val code = barcodes.firstOrNull()?.rawValue?.trim() ?: ""

                        if (code.isNotEmpty()) {
                            runOnUiThread {
                                processScannedCode(code)
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

        provider.bindToLifecycle(
            this,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis
        )

        isCameraRunning = true
    }

    private fun processScannedCode(code: String) {

        val target = list.firstOrNull { it.code.trim() == code }

        if (target == null) {
            val toast = Toast.makeText(this, "対象外部品です", Toast.LENGTH_SHORT)
            toast.show()

            Handler(Looper.getMainLooper()).postDelayed({
                toast.cancel()
            }, 700)

            return
        }

        if (target.checked) return

        target.checked = true

        adapter.notifyDataSetChanged()
        updateRemainingCount()
        checkCompletion()
    }

    // =========================
    // 共通
    // =========================
    private fun updateRemainingCount() {
        val remaining = list.count { !it.checked }
        binding.remainingText.text = "残り: $remaining"
    }

    private fun checkCompletion() {
        if (list.isNotEmpty() && list.all { it.checked }) {
            startActivity(Intent(this, NextActivity::class.java))
            finish()
        }
    }

    private fun showBackConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("確認")
            .setMessage("戻りますか？")
            .setPositiveButton("はい") { _, _ -> finish() }
            .setNegativeButton("いいえ", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        startCamera()
    }

    override fun onPause() {
        super.onPause()
        cameraProvider?.unbindAll()
        isCameraRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}