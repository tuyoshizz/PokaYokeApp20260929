package com.example.pokayokeapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.pokayokeapp.databinding.ActivityDangaeBinding
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DangaeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDangaeBinding

    private lateinit var cameraExecutor: ExecutorService

    private var cameraProvider: ProcessCameraProvider? = null

    private var isCameraRunning = false

    private var isScanning = true

    private var lastParentCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding =
            ActivityDangaeBinding.inflate(layoutInflater)

        setContentView(binding.root)

        cameraExecutor =
            Executors.newSingleThreadExecutor()

        // -----------------------------------------
        // 前回生産親品番を取得
        // -----------------------------------------

        val preferences =
            getSharedPreferences(
                "PokaYokeSettings",
                MODE_PRIVATE
            )

        lastParentCode =
            preferences
                .getString(
                    "LAST_PARENT_CODE",
                    ""
                )
                ?.trim()
                ?: ""

        // -----------------------------------------
        // 戻るボタン
        // -----------------------------------------

        binding.backButton.setOnClickListener {

            finish()
        }

        // -----------------------------------------
        // カメラ権限
        // -----------------------------------------

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {

            initCamera()

        } else {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CAMERA
                ),
                100
            )
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

        // -----------------------------------------
        // Preview
        // -----------------------------------------

        val preview =
            Preview.Builder()
                .setTargetRotation(
                    Surface.ROTATION_0
                )
                .build()
                .also {

                    it.setSurfaceProvider(
                        binding.previewView
                            .surfaceProvider
                    )
                }

        // -----------------------------------------
        // ImageAnalysis
        // -----------------------------------------

        val analysis =
            ImageAnalysis.Builder()
                .setBackpressureStrategy(
                    ImageAnalysis
                        .STRATEGY_KEEP_ONLY_LATEST
                )
                .build()

        // -----------------------------------------
        // QR Scanner
        // -----------------------------------------

        val scanner =
            BarcodeScanning.getClient()

        analysis.setAnalyzer(
            cameraExecutor
        ) { imageProxy ->

            val mediaImage =
                imageProxy.image

            if (mediaImage != null) {

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

                        if (!isScanning) {
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

                            isScanning = false

                            runOnUiThread {

                                compareParentCode(
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

        // -----------------------------------------
        // Camera起動
        // -----------------------------------------

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
    // 親品番比較
    // =========================================================

    private fun compareParentCode(
        currentParentCode: String
    ) {

        // -----------------------------------------
        // 前回品番がまだ存在しない
        // -----------------------------------------

        if (
            lastParentCode.isEmpty()
        ) {

            binding.messageText.text =
                "前回生産品番が登録されていません。\n\n今回品番：$currentParentCode"

            binding.compareButton.text =
                "比較する"

            binding.compareButton.setOnClickListener {

                openComparison(
                    currentParentCode
                )
            }

            return
        }

        // -----------------------------------------
        // 前回と同じ
        // -----------------------------------------

        if (
            lastParentCode ==
            currentParentCode
        ) {

            binding.messageText.text =
                """
                前回と同じ品番です

                親品番
                $currentParentCode
                """.trimIndent()

            binding.compareButton.text =
                "DangaeActivityに戻る"

            binding.compareButton.setOnClickListener {

                resetScan()
            }

            return
        }

        // -----------------------------------------
        // 前回と違う
        // -----------------------------------------

        openComparison(
            currentParentCode
        )
    }

    // =========================================================
    // 比較画面へ
    // =========================================================

    private fun openComparison(
        currentParentCode: String
    ) {

        val intent =
            Intent(
                this,
                DangaeCompareActivity::class.java
            )

        intent.putExtra(
            "LAST_PARENT_CODE",
            lastParentCode
        )

        intent.putExtra(
            "CURRENT_PARENT_CODE",
            currentParentCode
        )

        startActivity(intent)
    }

    // =========================================================
    // 再読取
    // =========================================================

    private fun resetScan() {

        isScanning = true

        binding.messageText.text =
            "今回生産する親品番のQRコードを読み取ってください"

        binding.compareButton.text =
            "DangaeActivityに戻る"

        binding.compareButton.setOnClickListener {

            finish()
        }
    }

    // =========================================================
    // Resume
    // =========================================================

    override fun onResume() {

        super.onResume()

        isScanning = true

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

    // =========================================================
    // Permission
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
            requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] ==
            PackageManager.PERMISSION_GRANTED
        ) {

            initCamera()
        }
    }
}
