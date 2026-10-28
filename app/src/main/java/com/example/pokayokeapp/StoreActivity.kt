
package com.example.pokayokeapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.pokayokeapp.databinding.ActivityStoreBinding
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.util.Size
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode

class StoreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStoreBinding
    private lateinit var cameraExecutor: ExecutorService

    private var cameraProvider: ProcessCameraProvider? = null
    private var isCameraRunning = false

    @Volatile
    private var isScanRequested = false

    @Volatile
    private var isProcessingScan = false

    // 1回目に読み取って記憶した品番
    private var selectedCode = ""

    // 二重遷移防止
    private var isNavigating = false

    private lateinit var topPanel: View

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val MODE_NAME = "格納"
    }

    // 照合完了後、次の読み取り状態に戻す
    private val clearCompletionRunnable = Runnable {
        if (!isFinishing && !isDestroyed && selectedCode.isNotEmpty()) {

            selectedCode = ""

            binding.topCodeText.text = "かんばんQR待ち"
            binding.topCodeText.setTextColor(Color.BLACK)

            topPanel.setBackgroundColor(Color.WHITE)

            binding.bottomStatusText.text = ""
            binding.bottomStatusText.setTextColor(Color.BLACK)

            binding.statusText.text =
                "次のQRを読み取ってください"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityStoreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        topPanel = findViewById(R.id.topPanel)

        binding.modeText.text = "格納"
        binding.readButton.text = "読み取り"

        binding.statusText.text =
            "かんばんQRを読み取ってください"

        binding.splitView.visibility = View.VISIBLE

        binding.topCodeText.text = "かんばんQR待ち"
        binding.bottomStatusText.text = ""

        topPanel.setBackgroundColor(Color.WHITE)

        binding.backButton.setOnClickListener {
            startActivity(Intent(this, MenuActivity::class.java))
            finish()
        }

        binding.readButton.setOnClickListener {
            requestScan()
        }

        binding.resetParentButton.setOnClickListener {
            mainHandler.removeCallbacks(clearCompletionRunnable)
            resetStoreState()
        }

        binding.shelfMapButton.setOnClickListener {
            startActivity(
                Intent(this, ShelfMapActivity::class.java)
            )
        }

        checkCameraPermission()
    }

    // カメラ権限
    private fun checkCameraPermission() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            initCamera()
        } else {
            requestPermissions(
                arrayOf(android.Manifest.permission.CAMERA),
                100
            )
        }
    }

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

        if (requestCode == 100) {
            if (
                grantResults.isNotEmpty() &&
                grantResults[0] ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                initCamera()
            } else {
                binding.statusText.text =
                    "カメラの使用が許可されていません"
            }
        }
    }

    // カメラ初期化
    private fun initCamera() {
        val future = ProcessCameraProvider.getInstance(this)

        future.addListener(
            {
                try {
                    cameraProvider = future.get()
                    startCamera()
                } catch (e: Exception) {
                    Log.e("格納", "カメラ初期化失敗", e)
                    binding.statusText.text =
                        "カメラを初期化できませんでした"
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    // カメラ開始
    private fun startCamera() {
        if (isCameraRunning) return

        val provider = cameraProvider ?: return

        if (
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            provider.unbindAll()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(
                        binding.previewView.surfaceProvider
                    )
                }



            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(
                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                )
                .build()

// QRコードだけを読み取る
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()

            val scanner = BarcodeScanning.getClient(options)

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->

                // 読み取りボタンが押されていない場合は解析しない
                if (!isScanRequested || isProcessingScan) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                val mediaImage = imageProxy.image

                if (mediaImage == null) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                isProcessingScan = true

                Log.d(
                    "格納",
                    "解析画像サイズ: ${imageProxy.width} x ${imageProxy.height}"
                )

                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->

                        Log.d(
                            "格納",
                            "検出されたバーコード数: ${barcodes.size}"
                        )

                        for ((index, barcode) in barcodes.withIndex()) {
                            Log.d(
                                "格納",
                                "QR[$index]: " +
                                        "format=${barcode.format}, " +
                                        "rawValue=${barcode.rawValue}, " +
                                        "valueType=${barcode.valueType}, " +
                                        "displayValue=${barcode.displayValue}, " +
                                        "boundingBox=${barcode.boundingBox}"
                            )
                        }

                        val barcode = barcodes.firstOrNull {
                            it.format == Barcode.FORMAT_QR_CODE &&
                                    (!it.rawValue.isNullOrBlank() ||
                                            !it.displayValue.isNullOrBlank())
                        }

                        val code = (
                                barcode?.rawValue
                                    ?: barcode?.displayValue
                                    ?: ""
                                ).trim()

                        if (code.isNotEmpty() && isScanRequested) {
                            isScanRequested = false

                            Log.d("格納", "QR読み取り成功: $code")

                            runOnUiThread {
                                try {
                                    processScannedCode(code)
                                } catch (e: Exception) {
                                    Log.e("格納", "QR処理失敗", e)
                                } finally {
                                    finishScanButton()
                                }
                            }
                        }
                    }

                    .addOnFailureListener { e ->
                        Log.e(
                            "格納",
                            "QR解析失敗",
                            e
                        )
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                        isProcessingScan = false
                    }
            }

            val camera = provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )

// QRコードを少し大きく映す
            camera.cameraControl.setLinearZoom(0.2f)

            isCameraRunning = true

        } catch (e: Exception) {
            isCameraRunning = false
            Log.e("格納", "カメラ起動失敗", e)

            binding.statusText.text =
                "カメラを起動できませんでした"
        }
    }

    // 読み取り要求
    private fun requestScan() {
        if (!isCameraRunning) {
            Toast.makeText(
                this,
                "カメラを起動しています。少しお待ちください",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (isNavigating || isScanRequested || isProcessingScan) return

        isScanRequested = true
        binding.readButton.isEnabled = false
        binding.readButton.text = "読み取り中..."
    }

    // 読み取りボタンを待機状態に戻す
    private fun finishScanButton() {
        isScanRequested = false

        if (!isFinishing && !isDestroyed && !isNavigating) {
            binding.readButton.isEnabled = true
            binding.readButton.text = "読み取り"
        }
    }

    // QRコード処理
    private fun processScannedCode(rawCode: String) {
        val code = rawCode.trim()

        if (code.isEmpty() || isNavigating) return

        // 1回目：品番を抽出して記憶
        if (selectedCode.isEmpty()) {

            val extractedCode = extractStoreCode(code)

            if (extractedCode.isEmpty()) {
                binding.statusText.text =
                    "QRコードから品番を抽出できません"

                Toast.makeText(
                    this,
                    "品番を確認してください",
                    Toast.LENGTH_SHORT
                ).show()

                HistoryManager.addHistory(
                    this,
                    mode = MODE_NAME,
                    action = "品番抽出失敗",
                    childCode = code,
                    method = "QR"
                )

                return
            }

            selectedCode = extractedCode

            HistoryManager.addHistory(
                this,
                mode = MODE_NAME,
                action = "品番読取",
                childCode = selectedCode,
                method = "QR"
            )
            binding.topCodeText.text =
                "記憶した品番：\n$selectedCode"

            binding.topCodeText.setTextColor(Color.BLACK)
            topPanel.setBackgroundColor(Color.GREEN)

            binding.bottomStatusText.text = "ロケQR待ち"

            binding.statusText.text =
                "次のQRコードを読み取ってください"

            Log.d(
                "格納",
                "品番記憶: $selectedCode"
            )

            return
        }

        // 2回目：記憶した品番 + # と照合
        val expectedCode = "${selectedCode}#"

        if (code == expectedCode) {
            HistoryManager.addHistory(
                this,
                mode = MODE_NAME,
                action = "照合完了",
                childCode = code,
                method = "QR"
            )

            mainHandler.removeCallbacks(clearCompletionRunnable)

            topPanel.setBackgroundColor(Color.rgb(20, 145, 75))

            binding.topCodeText.text =
                "✓\n照 合 完 了！\n品番：$selectedCode"

            binding.topCodeText.setTextColor(Color.WHITE)

            binding.bottomStatusText.text = "OK"
            binding.bottomStatusText.setTextColor(
                Color.rgb(20, 145, 75)
            )

            binding.statusText.text = "照合成功！"

            Toast.makeText(
                this,
                "照合完了",
                Toast.LENGTH_SHORT
            ).show()

            Log.d(
                "格納",
                "照合完了: 品番=$selectedCode"
            )

            mainHandler.postDelayed(
                clearCompletionRunnable,
                1000L
            )

        } else {
            HistoryManager.addHistory(
                this,
                mode = MODE_NAME,
                action = "照合不一致",
                childCode = code,
                method = "QR"
            )

            binding.statusText.text =
                "QRコードが一致しません。\n正しいQRを読み取ってください"

            Toast.makeText(
                this,
                "QRコードが一致しません",
                Toast.LENGTH_SHORT
            ).show()

            Log.w(
                "格納",
                "不一致: 期待値=$expectedCode, 読取値=$code"
            )
        }
    }

    // QRコード全体から品番を抽出
// 例：... GS P17K762-7020 GS Q50 ...
// 抽出結果：17K762-702
    private fun extractStoreCode(code: String): String {
        val text = code.trim()

        // GS（グループ区切り）直後の P 項目を探す
        val match = Regex(
            "(?:^|\\u001D)P([^\\u001D]+)"
        ).find(text) ?: return ""

        val partNumber = match.groupValues[1].trim()

        // 最初のハイフンより前が6文字、
        // ハイフンより後ろの先頭3文字を品番とする
        val hyphenIndex = partNumber.indexOf('-')

        if (hyphenIndex != 6) return ""
        if (partNumber.length < hyphenIndex + 4) return ""

        val beforeHyphen = partNumber.substring(0, hyphenIndex)
        val afterHyphen = partNumber.substring(
            hyphenIndex + 1,
            hyphenIndex + 4
        )

        if (!beforeHyphen.all { it.isLetterOrDigit() }) return ""
        if (!afterHyphen.all { it.isLetterOrDigit() }) return ""

        return "$beforeHyphen-$afterHyphen"
    }

    // 初期状態に戻す
    private fun resetStoreState() {
        isScanRequested = false
        selectedCode = ""

        binding.readButton.text = "読み取り"
        binding.readButton.isEnabled = isCameraRunning

        binding.statusText.text =
            "かんばんQRを読み取ってください"

        binding.topCodeText.text = "かんばんQR待ち"
        binding.topCodeText.setTextColor(Color.BLACK)

        binding.bottomStatusText.text = ""
        binding.bottomStatusText.setTextColor(Color.BLACK)

        topPanel.setBackgroundColor(Color.WHITE)
    }

    override fun onResume() {
        super.onResume()

        if (::cameraExecutor.isInitialized) {
            startCamera()
        }
    }

    override fun onPause() {
        super.onPause()

        isScanRequested = false

        cameraProvider?.unbindAll()
        isCameraRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()

        cameraProvider?.unbindAll()
        mainHandler.removeCallbacks(clearCompletionRunnable)

        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }
}
