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
import com.example.pokayokeapp.databinding.ActivityMainBinding
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    private var isCameraRunning = false
    private var isNavigating = false
    private var isScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.editText.setText("")
        binding.button.text = "読取"

        binding.button.setOnClickListener {
            isScanning = true
            binding.editText.setText("")
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            initCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                100
            )
        }
    }

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

        val preview = Preview.Builder()
            .setTargetRotation(Surface.ROTATION_0)
            .build()
            .also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

        val analyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(
                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            )
            .build()

        val scanner = BarcodeScanning.getClient()

        analyzer.setAnalyzer(cameraExecutor) { imageProxy ->

            val mediaImage = imageProxy.image

            if (mediaImage != null) {

                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->

                        if (!isScanning) {
                            return@addOnSuccessListener
                        }

                        val code = barcodes.firstOrNull()
                            ?.rawValue
                            ?.trim()

                        if (!code.isNullOrEmpty() && !isNavigating) {

                            isNavigating = true
                            isScanning = false

                            runOnUiThread {
                                binding.editText.setText(code)
                                moveToNext(code)
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
            analyzer
        )

        isCameraRunning = true
    }

    private fun moveToNext(code: String) {
        val intent = Intent(
            this,
            ChildListActivity::class.java
        )

        intent.putExtra("QR_CODE", code)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()

        isNavigating = false
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
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
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            initCamera()
        }
    }
}