package com.example.pokayokeapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
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

    // =========================================================
    // QR読み取り状態
    //
    // false
    // → 待機中。QRを読み取らない
    //
    // true
    // → 「段替え開始」を押した後。QRを読み取る
    // =========================================================

    private var isScanning = false

    // =========================================================
    // 前回生産親品番
    // =========================================================

    private var lastParentCode = ""


    companion object {

        private const val REQUEST_CAMERA = 100
    }


    // =========================================================
    // onCreate
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // =====================================================
        // Binding
        // =====================================================

        binding =
            ActivityDangaeBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)


        // =====================================================
        // 前回生産品番取得
        // =====================================================

        loadLastParentCode()


        // =====================================================
        // 前回生産品番表示
        //
        // txtParentだけを使用
        // =====================================================

        updateParentText()


        // =====================================================
        // 段替え開始ボタン設定
        // =====================================================

        setupStartButton()


        // =====================================================
        // メニュー画面へ
        // =====================================================

        binding.btnmenu.setOnClickListener {

            val intent =
                Intent(
                    this,
                    MenuActivity::class.java
                )

            startActivity(intent)
        }


        // =====================================================
        // Camera Executor
        // =====================================================

        cameraExecutor =
            Executors.newSingleThreadExecutor()


        // =====================================================
        // Camera権限確認
        // =====================================================

        checkCameraPermission()
    }


    // =========================================================
    // 前回生産品番取得
    // =========================================================

    private fun loadLastParentCode() {

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
    }


    // =========================================================
    // 前回生産品番表示
    //
    // ★txtParentのみ使用
    // ★ここでは他のTextViewを使用しない
    // =========================================================

    private fun updateParentText() {

        if (lastParentCode.isEmpty()) {

            binding.txtParent.text =
                "前回生産品番：未登録"

        } else {

            binding.txtParent.text =
                "前回生産品番：$lastParentCode"
        }
    }


    // =========================================================
    // 段替え開始ボタン設定
    // =========================================================

    private fun setupStartButton() {

        // -----------------------------------------------------
        // ボタン文字
        // -----------------------------------------------------

        binding.button.text =
            "段替え開始"


        // -----------------------------------------------------
        // 緑色
        // -----------------------------------------------------

        binding.button.backgroundTintList =
            ColorStateList.valueOf(
                Color.rgb(
                    76,
                    175,
                    80
                )
            )


        // -----------------------------------------------------
        // ボタン有効
        // -----------------------------------------------------

        binding.button.isEnabled = true


        // -----------------------------------------------------
        // クリック
        // -----------------------------------------------------

        binding.button.setOnClickListener {

            startParentScan()
        }
    }


    // =========================================================
    // 段替え開始
    // =========================================================

    private fun startParentScan() {

        // -----------------------------------------------------
        // QR読み取り開始
        // -----------------------------------------------------

        isScanning = true


        // -----------------------------------------------------
        // 二重押下防止
        // -----------------------------------------------------

        binding.button.isEnabled = false


        // -----------------------------------------------------
        // 読み取り中表示
        // -----------------------------------------------------

        binding.button.text =
            "読み取り中..."


        // -----------------------------------------------------
        // 前回生産品番表示は変更しない
        // -----------------------------------------------------

        updateParentText()
    }


    // =========================================================
    // Camera権限確認
    // =========================================================

    private fun checkCameraPermission() {

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
                REQUEST_CAMERA
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


        // =====================================================
        // Preview
        // =====================================================

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

                        // =====================================
                        // 段替え開始ボタンが押されていなければ
                        // QRコードを無視
                        // =====================================

                        if (!isScanning) {

                            return@addOnSuccessListener
                        }


                        val code =
                            barcodes
                                .firstOrNull()
                                ?.rawValue
                                ?.trim()
                                ?: ""


                        if (code.isNotEmpty()) {

                            // ---------------------------------
                            // 1回読み取ったら停止
                            // ---------------------------------

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
    // 親品番比較
    // =========================================================

    private fun compareParentCode(
        currentParentCode: String
    ) {

        // =====================================================
        // 前回品番が未登録
        // =====================================================

        if (lastParentCode.isEmpty()) {

            binding.txtParent.text =
                "前回生産品番：未登録"


            binding.button.isEnabled = true

            binding.button.text =
                "比較する"


            binding.button.setOnClickListener {

                openComparison(
                    currentParentCode
                )
            }


            return
        }


        // =====================================================
        // 前回と同じ
        // =====================================================

        if (
            lastParentCode ==
            currentParentCode
        ) {

            binding.txtParent.text =
                "前回生産品番：$lastParentCode"


            binding.button.isEnabled = true

            binding.button.text =
                "再読取"


            binding.button.setOnClickListener {

                resetScan()
            }


            return
        }


        // =====================================================
        // 前回と違う
        // =====================================================

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
    // 再読取
    // =========================================================

    private fun resetScan() {

        // -----------------------------------------------------
        // QR読み取り停止
        // -----------------------------------------------------

        isScanning = false


        // -----------------------------------------------------
        // ボタンを初期状態へ
        // -----------------------------------------------------

        setupStartButton()


        // -----------------------------------------------------
        // 前回生産品番を再表示
        // -----------------------------------------------------

        loadLastParentCode()

        updateParentText()
    }


    // =========================================================
    // Resume
    // =========================================================

    override fun onResume() {

        super.onResume()


        // -----------------------------------------------------
        // 自動読み取りしない
        // -----------------------------------------------------

        isScanning = false


        // -----------------------------------------------------
        // 前回生産品番を再取得
        // -----------------------------------------------------

        loadLastParentCode()


        // -----------------------------------------------------
        // 前回生産品番表示
        // -----------------------------------------------------

        updateParentText()


        // -----------------------------------------------------
        // 段替え開始ボタンへ戻す
        // -----------------------------------------------------

        setupStartButton()


        // -----------------------------------------------------
        // Camera再開
        // -----------------------------------------------------

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
    // Camera Permission
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
            requestCode == REQUEST_CAMERA &&
            grantResults.isNotEmpty() &&
            grantResults[0] ==
            PackageManager.PERMISSION_GRANTED
        ) {

            initCamera()
        }
    }
}
