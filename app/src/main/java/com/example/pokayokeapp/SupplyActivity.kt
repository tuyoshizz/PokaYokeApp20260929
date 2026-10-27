
package com.example.pokayokeapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import android.graphics.Color
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.pokayokeapp.databinding.ActivitySupplyBinding
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

class SupplyActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySupplyBinding
    private lateinit var cameraExecutor: ExecutorService

    private var cameraProvider: ProcessCameraProvider? = null
    private var isCameraRunning = false

    @Volatile
    private var isScanRequested = false

    @Volatile
    private var isProcessingScan = false

    // 親品番
    private var parentCode = ""

    // 親品番ごとの子品番一覧
    private val parentChildren =
        mutableMapOf<String, MutableSet<String>>()

    // 1回目に読み取った子品番
    private var selectedChildCode = ""

    // ★ 子品番の名前
    private var selectedChildName = ""

    // 供給番号
    private var selectedSupplyNo = ""

    // 子品番ごとの供給番号
    private val supplyNumbers = mutableMapOf<String, String>()

    // 子品番ごとの名前
    private val childNames = mutableMapOf<String, String>()

    // 親品番がExcelに存在するか
    private var isParentConfirmed = false

    // Excel読み込み完了
    private var isExcelLoaded = false

    // 次画面への二重遷移防止
    private var isNavigating = false

    // 分割ビュー上側の領域全体
    private lateinit var topPanel: View

    // 異常ロック中かどうか
    @Volatile
    private var isAbnormalLocked = false

    companion object {

        // 異常ロック機能
        private const val ENABLE_ABNORMAL_LOCK = true

        // 異常解除用QR
        private const val RELEASE_QR_CODE = "kaijo"

        private const val REQUEST_CAMERA = 100
        private const val REQUEST_STORAGE = 101

        private const val FOLDER_NAME = "PokaYoke"
        private const val EXCEL_FILE_NAME = "parts.xlsx"

        private const val MODE_NAME = "供給"

        private const val EXTRA_PARENT_CODE = "PARENT_CODE"
        private const val EXTRA_CHILD_CODE = "CHILD_CODE"
    }

    // =========================================================
// 異常ロック
// =========================================================

    private fun activateAbnormalLock(
        expectedCode: String,
        scannedCode: String
    ) {
        if (!ENABLE_ABNORMAL_LOCK) return

        isAbnormalLocked = true
        isScanRequested = false

        mainHandler.removeCallbacks(clearCompletionRunnable)

        // 異常表示
        topPanel.setBackgroundColor(Color.RED)

        binding.topCodeText.text =
            "異常エラー！\n照合不一致"

        binding.topCodeText.setTextColor(Color.WHITE)

        binding.bottomStatusText.text =
            "異常停止中"

        binding.bottomStatusText.setTextColor(Color.RED)

        binding.statusText.text =
            "異常エラー：照合不一致\n" +
                    "解除用QR「kaijo」を読み取ってください"

        HistoryManager.addHistory(
            this,
            mode = MODE_NAME,
            action = "異常ロック発生",
            parentCode = parentCode,
            childCode = scannedCode,
            method = "QR"
        )

        Log.e(
            "Supply",
            "異常ロック: 期待値=$expectedCode, 読取値=$scannedCode"
        )
    }


// =========================================================
// 異常解除
// =========================================================

    private fun releaseAbnormalLock() {
        isAbnormalLocked = false

        // 異常発生時に選択していた部品は破棄し、
        // 同じ親品番から部品QRを読み直す
        selectedChildCode = ""
        selectedChildName = ""
        selectedSupplyNo = ""

        mainHandler.removeCallbacks(clearCompletionRunnable)

        topPanel.setBackgroundColor(Color.WHITE)

        binding.topCodeText.text = "部品QR待ち"
        binding.topCodeText.setTextColor(Color.BLACK)

        binding.bottomStatusText.text = ""
        binding.bottomStatusText.setTextColor(Color.BLACK)

        binding.statusText.text =
            "異常解除完了\n部品QRを読み取ってください"

        HistoryManager.addHistory(
            this,
            mode = MODE_NAME,
            action = "異常解除",
            parentCode = parentCode,
            method = "QR"
        )

        Toast.makeText(
            this,
            "異常を解除しました",
            Toast.LENGTH_SHORT
        ).show()

        Log.d("Supply", "異常ロック解除")
    }

    // 照合完了表示を消すタイマー
    private val mainHandler = Handler(Looper.getMainLooper())

    private val clearCompletionRunnable = Runnable {
        if (
            !isFinishing &&
            !isDestroyed &&
            isParentConfirmed &&
            selectedChildCode.isNotEmpty()
        ) {
            // 部品の照合状態だけをリセット
            selectedChildCode = ""

            binding.topCodeText.text = "部品QR待ち"
            topPanel.setBackgroundColor(Color.WHITE)

            binding.topCodeText.setTextColor(Color.BLACK)

            binding.bottomStatusText.text = ""

            binding.statusText.text =
                "次の部品QRを読み取ってください"
        }
    }

    // =========================================================
    // onCreate
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySupplyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // 分割ビュー上側の背景領域を取得
        topPanel = findViewById(R.id.topPanel)

        // モード表示
        binding.modeText.text = "供給"

        // 読み取りボタン
        binding.readButton.text = "読み取り"

        // 初期メッセージ
        binding.statusText.text =
            "親品番のQRコードを読み取ってください"

        // カメラの下に親品番を1行表示
        binding.parentCodeText.text = "親品番："

        // 親品番が確定するまで分割ビューを非表示
        binding.splitView.visibility = View.GONE

        // 上側の初期表示
        binding.topCodeText.text =
            "親品番のQRコードを\n読み込んでください"

        topPanel.setBackgroundColor(Color.WHITE)

        // 下側の初期表示
        binding.bottomStatusText.text = ""

        // 戻るボタン
        binding.backButton.setOnClickListener {
            val intent = Intent(this, MenuActivity::class.java)
            startActivity(intent)
            finish()
        }

        // QR読み取りボタン
        binding.readButton.setOnClickListener {
            requestScan()
        }

        // 権限確認・Excel読み込み・カメラ起動
        checkStoragePermission()

        binding.resetParentButton.setOnClickListener {
            mainHandler.removeCallbacks(clearCompletionRunnable)
            resetSupplyState()
        }

        binding.shelfMapButton.setOnClickListener {
            val intent = Intent(this, ShelfMapActivity::class.java)
            startActivity(intent)
        }


    }

    // =========================================================
    // ストレージ権限確認
    // =========================================================

    private fun checkStoragePermission() {

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.M
        ) {

            val granted =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED

            if (granted) {

                loadExcelData()
                checkCameraPermission()

            } else {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ),
                    REQUEST_STORAGE
                )
            }

        } else {

            loadExcelData()
            checkCameraPermission()
        }
    }

    // =========================================================
    // カメラ権限確認
    // =========================================================

    private fun checkCameraPermission() {

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
                REQUEST_CAMERA
            )
        }
    }

    // =========================================================
    // 権限取得結果
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

        when (requestCode) {

            REQUEST_STORAGE -> {

                if (
                    grantResults.isNotEmpty() &&
                    grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED
                ) {

                    loadExcelData()
                    checkCameraPermission()

                } else {

                    isExcelLoaded = false
                    binding.readButton.isEnabled = false

                    binding.statusText.text =
                        "Excelファイルへのアクセスが許可されていません"

                    Toast.makeText(
                        this,
                        "Excelを読み込むための権限が必要です",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            REQUEST_CAMERA -> {

                if (
                    grantResults.isNotEmpty() &&
                    grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED
                ) {

                    initCamera()

                } else {

                    binding.statusText.text =
                        "カメラの使用が許可されていません"

                    Toast.makeText(
                        this,
                        "QR読み取りにはカメラ権限が必要です",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // =========================================================
    // Excelファイル取得
    // =========================================================

    private fun getExcelFile(): File {

        val storagePath =
            android.os.Environment.getExternalStorageDirectory()

        val folder = File(
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
    // Excel読み込み
    //
    // A列：親品番
    // B列：子品番
    // 1行目：見出し
    // =========================================================

    private fun loadExcelData() {

        val file = getExcelFile()

        val loadedChildNames = mutableMapOf<String, String>()
        val loadedSupplyNumbers = mutableMapOf<String, String>()

        Log.d(
            "Supply",
            "Excelパス: ${file.absolutePath}"
        )

        if (!file.exists()) {

            isExcelLoaded = false
            binding.readButton.isEnabled = false

            binding.statusText.text =
                "parts.xlsx が見つかりません。\n" +
                        "内部ストレージ/PokaYoke に配置してください。"

            Log.e(
                "Supply",
                "Excelファイルがありません"
            )

            return
        }

        try {

            val loadedData =
                mutableMapOf<String, MutableSet<String>>()

            file.inputStream().use { inputStream ->

                XSSFWorkbook(inputStream).use { workbook ->

                    if (workbook.numberOfSheets == 0) {
                        throw IllegalStateException(
                            "Excelにシートがありません"
                        )
                    }

                    val sheet = workbook.getSheetAt(0)

                    for (i in 1..sheet.lastRowNum) {

                        val row = sheet.getRow(i) ?: continue

                        // A列：親品番
                        val parent =
                            getCellValue(
                                row.getCell(0)
                            ).trim()

                        // B列：子品番
                        val child =
                            getCellValue(
                                row.getCell(1)
                            ).trim()


                        // C列：子品番の名前
                        val childName =
                            getCellValue(
                                row.getCell(2)
                            ).trim()

                        // E列：供給番号
                        val supplyNo =
                            getCellValue(
                                row.getCell(4)
                            ).trim()

                        if (
                            parent.isNotEmpty() &&
                            child.isNotEmpty()
                        ) {

                            val key = normalizeCode(parent)

                            loadedData
                                .getOrPut(key) {
                                    mutableSetOf()
                                }
                                .add(child)

                            // ★ 子品番と名前を対応付ける
                            if (childName.isNotEmpty()) {
                                loadedChildNames[normalizeCode(child)] = childName
                            }
                            // 子品番と供給番号を対応付ける
                            if (supplyNo.isNotEmpty()) {
                                loadedSupplyNumbers[normalizeCode(child)] = supplyNo
                            }
                        }
                    }
                }
            }

            parentChildren.clear()
            parentChildren.putAll(loadedData)

            // ★ 子品番の名前一覧を更新
            childNames.clear()
            childNames.putAll(loadedChildNames)

            // 供給番号一覧を更新
            supplyNumbers.clear()
            supplyNumbers.putAll(loadedSupplyNumbers)

            parentChildren.clear()
            parentChildren.putAll(loadedData)

            isExcelLoaded = true
            binding.readButton.isEnabled = true

            binding.statusText.text =
                "親品番のQRコードを読み取ってください"

            Log.d(
                "Supply",
                "Excel読み込み成功: " +
                        "${parentChildren.size}種類の親品番"
            )

        } catch (e: Exception) {

            isExcelLoaded = false
            binding.readButton.isEnabled = false

            binding.statusText.text =
                "Excelの読み込みに失敗しました"

            Log.e(
                "Supply",
                "Excel読み込み失敗",
                e
            )

            Toast.makeText(
                this,
                "parts.xlsx の内容・形式を確認してください",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // =========================================================
    // Excelセルの値取得
    // =========================================================

    private fun getCellValue(cell: Cell?): String {

        if (cell == null) return ""

        return when (cell.cellType) {

            CellType.STRING ->
                cell.stringCellValue.trim()

            CellType.NUMERIC -> {

                val value = cell.numericCellValue

                if (value == value.toLong().toDouble()) {
                    value.toLong().toString()
                } else {
                    value.toString()
                }
            }

            CellType.BOOLEAN ->
                cell.booleanCellValue.toString()

            CellType.FORMULA -> {

                when (cell.cachedFormulaResultType) {

                    CellType.STRING ->
                        cell.stringCellValue.trim()

                    CellType.NUMERIC -> {

                        val value = cell.numericCellValue

                        if (value == value.toLong().toDouble()) {
                            value.toLong().toString()
                        } else {
                            value.toString()
                        }
                    }

                    else ->
                        cell.toString().trim()
                }
            }

            else ->
                cell.toString().trim()
        }
    }


// =========================================================
// 品番比較用
// =========================================================

    private fun normalizeCode(code: String): String {
        return code.trim().uppercase(Locale.ROOT)
    }

// =========================================================
// 読み取り要求
// =========================================================

    private fun requestScan() {

        if (!isExcelLoaded) {
            Toast.makeText(
                this,
                "Excelを読み込めていません",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!isCameraRunning) {
            Toast.makeText(
                this,
                "カメラを起動しています。少しお待ちください",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (isNavigating) return
        if (isScanRequested || isProcessingScan) return

        isScanRequested = true

        binding.readButton.isEnabled = false
        binding.readButton.text = "読み取り中..."
    }

// =========================================================
// 読み取りボタンを待機状態に戻す
// =========================================================

    private fun finishScanButton() {

        isScanRequested = false

        if (!isFinishing && !isDestroyed && !isNavigating) {
            binding.readButton.isEnabled =
                isExcelLoaded && isCameraRunning

            binding.readButton.text = "読み取り"
        }
    }

// =========================================================
// カメラ初期化
// =========================================================

    private fun initCamera() {

        val future = ProcessCameraProvider.getInstance(this)

        future.addListener(
            {
                try {
                    cameraProvider = future.get()
                    startCamera()
                } catch (e: Exception) {
                    Log.e("Supply", "カメラ初期化失敗", e)
                    binding.statusText.text =
                        "カメラを初期化できませんでした"
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

// =========================================================
// カメラ開始
// =========================================================

    private fun startCamera() {

        if (isCameraRunning) return

        val provider = cameraProvider ?: return

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
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

            val scanner = BarcodeScanning.getClient()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->

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

                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->

                        val code = barcodes
                            .firstOrNull {
                                !it.rawValue.isNullOrBlank()
                            }
                            ?.rawValue
                            ?.trim()
                            .orEmpty()

                        if (code.isNotEmpty() && isScanRequested) {

                            isScanRequested = false

                            runOnUiThread {
                                try {
                                    processScannedCode(code)
                                } catch (e: Exception) {
                                    Log.e(
                                        "Supply",
                                        "QRコード処理失敗",
                                        e
                                    )

                                    Toast.makeText(
                                        this,
                                        "QRコードの処理に失敗しました",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    finishScanButton()
                                }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("Supply", "QR解析失敗", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                        isProcessingScan = false
                    }
            }

            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )

            isCameraRunning = true

        } catch (e: Exception) {

            isCameraRunning = false

            Log.e("Supply", "カメラ起動失敗", e)

            binding.statusText.text =
                "カメラを起動できませんでした"
        }
    }


    // =========================================================
    // 供給モードを初期状態に戻す
    // =========================================================

    private fun resetSupplyState() {


        // 異常中はﾘｾｯﾄ禁止
        if (ENABLE_ABNORMAL_LOCK && isAbnormalLocked) {
            Toast.makeText(
                this,
                "異常停止中です。解除用QRを読み取ってください",
                Toast.LENGTH_SHORT
            ).show()
            return
        }


        // 読み取り状態をリセット
        isScanRequested = false
        isProcessingScan = false
        isNavigating = false

        // 品番情報をクリア
        parentCode = ""
        selectedChildCode = ""
        isParentConfirmed = false

        // 画面表示を初期状態に戻す
        binding.modeText.text = "供給"
        binding.readButton.text = "読み取り"
        binding.readButton.isEnabled = isExcelLoaded
        binding.statusText.text =
            "親品番のQRコードを読み取ってください"

        binding.parentCodeText.text = "親品番：$parentCode"

        binding.splitView.visibility = View.GONE

        binding.topCodeText.text =
            "親品番のQRコードを\n読み込んでください"

        topPanel.setBackgroundColor(Color.WHITE)

        binding.bottomStatusText.text = ""

        binding.topCodeText.setTextColor(Color.BLACK)
        binding.bottomStatusText.setTextColor(Color.BLACK)
    }


// =========================================================
// QRコード処理
// =========================================================

    private fun processScannedCode(rawCode: String) {

        val code = rawCode.trim()

        if (code.isEmpty() || isNavigating) return

        // 異常ロック機能が有効な場合
        if (ENABLE_ABNORMAL_LOCK) {

            // 異常中は解除用QRだけを受け付ける
            if (isAbnormalLocked) {

                if (code == RELEASE_QR_CODE) {
                    releaseAbnormalLock()
                } else {
                    binding.statusText.text =
                        "異常停止中です。\n" +
                                "解除用QR「kaijo」を読み取ってください"

                    Log.w(
                        "Supply",
                        "異常ロック中のQRを拒否: $code"
                    )
                }

                return
            }

            // 通常時でも解除用QRが読み取られた場合は
            // 通常の部品・供給番号として処理しない
            if (code == RELEASE_QR_CODE) {
                Toast.makeText(
                    this,
                    "現在、解除が必要な異常はありません",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }
        }

        if (!isParentConfirmed) {
            processParentCode(code)
        } else {
            processChildCode(code)
        }
    }

// =========================================================
// 親品番処理
// =========================================================

    private fun processParentCode(qrCode: String) {

        val extractedCode = extractParentCode(qrCode)

        val children =
            parentChildren[normalizeCode(extractedCode)]

        // Excelに親品番が存在しない
        if (children.isNullOrEmpty()) {

            parentCode = ""
            selectedChildCode = ""
            isParentConfirmed = false

            binding.parentCodeText.text = "親品番："
            binding.splitView.visibility = View.GONE

            binding.topCodeText.text =
                "親品番のQRコードを\n読み込んでください"

            topPanel.setBackgroundColor(Color.WHITE)

            binding.bottomStatusText.text =
                "親品番のQRコードを\n読み込んでください"

            binding.statusText.text =
                "親品番が登録されていません"

            Toast.makeText(
                this,
                "親品番が parts.xlsx に登録されていません",
                Toast.LENGTH_LONG
            ).show()

            Log.w(
                "Supply",
                "親品番なし: $extractedCode"
            )

            return
        }

        // 親品番を確定
        parentCode = extractedCode
        selectedChildCode = ""
        isParentConfirmed = true

        // カメラの下に親品番を表示
        binding.parentCodeText.text =
            "親品番：$parentCode"

        // 分割ビューを表示
        binding.splitView.visibility = View.VISIBLE

        // 親品番を読み取った時点では白色のまま
        binding.topCodeText.text =
            "部品QR待ち"

        topPanel.setBackgroundColor(Color.WHITE)

        // 部品QRを読み取るまで下側は空欄
        binding.bottomStatusText.text = ""

        // ステータス表示
        binding.statusText.text =
            "部品QRを読み取ってください"

        HistoryManager.addHistory(
            this,
            mode = MODE_NAME,
            action = "親品番決定",
            parentCode = parentCode,
            method = "QR"
        )

        Log.d(
            "Supply",
            "親品番確定: $parentCode"
        )
    }



    // =====================================================
    // 子品番・供給場所QR処理
    // 1回目：部品QR
    // 2回目：部品QR + #
    // =====================================================

    private fun processChildCode(code: String) {

        // -------------------------------------------------
        // 1回目：部品QRを読み取る
        // -------------------------------------------------

        if (selectedChildCode.isEmpty()) {

            val children =
                parentChildren[normalizeCode(parentCode)]
                    ?: emptySet<String>()

            // Excelに登録されている子品番か確認
            val matchedChild = children.firstOrNull {
                normalizeCode(it) == normalizeCode(code)
            }

            if (matchedChild == null) {

                binding.statusText.text =
                    "この親品番に登録されていない部品QRです"

                Toast.makeText(
                    this,
                    "対象外の子品番です",
                    Toast.LENGTH_SHORT
                ).show()

                HistoryManager.addHistory(
                    this,
                    mode = MODE_NAME,
                    action = "対象外部品",
                    parentCode = parentCode,
                    childCode = code,
                    method = "QR"
                )

                return
            }


            // 部品QRを記憶
            selectedChildCode = matchedChild

            // 子品番に対応する名前を取得
            selectedChildName =
                childNames[normalizeCode(selectedChildCode)] ?: "名前未登録"

            // 子品番に対応する供給番号を取得
            selectedSupplyNo =
                supplyNumbers[normalizeCode(selectedChildCode)] ?: "供給番号未登録"

            // 部品名・品番・供給番号を3行で表示
            binding.topCodeText.text =
                "部品：$selectedChildName\n" +
                        "品番：$selectedChildCode\n" +
                            "供給番号：$selectedSupplyNo"

            // 上側を緑色にする
            topPanel.setBackgroundColor(Color.GREEN)

            // ★ 文字色を黒色に戻す
            binding.topCodeText.setTextColor(Color.BLACK)

            binding.bottomStatusText.text = "供給QR待ち"

            binding.statusText.text =
                "供給場所QRを読み取ってください"


            HistoryManager.addHistory(
                this,
                mode = MODE_NAME,
                action = "子品番読取",
                parentCode = parentCode,
                childCode = selectedChildCode,
                method = "QR"
            )

            Log.d(
                "Supply",
                "部品QR記憶: $selectedChildCode"
            )

            return
        }

// -------------------------------------------------
// 2回目：供給番号QRを照合
// -------------------------------------------------

// 選択中の子品番に対応する供給番号を取得
        val expectedSupplyCode =
            supplyNumbers[normalizeCode(selectedChildCode)].orEmpty()

// 供給番号が未登録の場合
        if (expectedSupplyCode.isEmpty()) {

            binding.statusText.text =
                "この部品の供給番号が登録されていません"

            Toast.makeText(
                this,
                "ExcelのE列に供給番号を登録してください",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

// 読み取ったQRと供給番号を比較
        if (normalizeCode(code) == normalizeCode(expectedSupplyCode)) {

            // 前回の完了タイマーを解除
            mainHandler.removeCallbacks(clearCompletionRunnable)

            // 照合成功を記録
            HistoryManager.addHistory(
                this,
                mode = MODE_NAME,
                action = "供給QR読取",
                parentCode = parentCode,
                childCode = code,
                method = "QR"
            )

            // 照合完了を華やかに表示
            topPanel.setBackgroundColor(Color.rgb(20, 145, 75))

            binding.topCodeText.text =
                "✓\n照 合 完 了！\n部品品番：$selectedChildCode"

            binding.topCodeText.setTextColor(Color.WHITE)
            binding.bottomStatusText.text = "OK"
            binding.bottomStatusText.setTextColor(Color.rgb(20, 145, 75))

            binding.statusText.text = "照合成功！"

            Toast.makeText(
                this,
                "照合完了",
                Toast.LENGTH_SHORT
            ).show()

            Log.d(
                "Supply",
                "照合完了: 親品番=$parentCode, " +
                        "子品番=$selectedChildCode, " +
                        "供給番号=$expectedSupplyCode"
            )

            // 1秒後に部品の照合状態だけをリセット
            mainHandler.postDelayed(
                clearCompletionRunnable,
                1000L
            )

            return
        }


// -------------------------------------------------
// 供給場所QRが一致しない場合
// -------------------------------------------------

        HistoryManager.addHistory(
            this,
            mode = MODE_NAME,
            action = "供給QR不一致",
            parentCode = parentCode,
            childCode = code,
            method = "QR"
        )

        Log.w(
            "Supply",
            "供給番号不一致: " +
                    "期待値=$expectedSupplyCode, 読取値=$code"
        )

        if (ENABLE_ABNORMAL_LOCK) {

            // 異常ロックを有効にしている場合
            activateAbnormalLock(
                expectedCode = expectedSupplyCode,
                scannedCode = code
            )

        } else {

            // 無効の場合は従来どおりの警告表示
            binding.statusText.text =
                "供給場所QRが一致しません。\n正しいQRを読んでください"

            Toast.makeText(
                this,
                "供給場所QRが一致しません",
                Toast.LENGTH_SHORT
            ).show()
        }

        Toast.makeText(
            this,
            "供給場所QRが一致しません",
            Toast.LENGTH_SHORT
        ).show()

        HistoryManager.addHistory(
            this,
            mode = MODE_NAME,
            action = "供給QR不一致",
            parentCode = parentCode,
            childCode = code,
            method = "QR"
        )

        Log.w(
            "Supply",
            "供給番号不一致: " +
                    "期待値=$expectedSupplyCode, 読取値=$code"
        )
    }


// =========================================================
// QRコードから親品番を抽出
// 形式：英数字6文字-英数字3文字
// =========================================================

    private fun extractParentCode(code: String): String {

        val text = code.trim()

        val pattern =
            Regex("[A-Za-z0-9]{6}-[A-Za-z0-9]{3}")

        return pattern.find(text)?.value ?: text
    }


    // =========================================================
    // Resume
    // =========================================================

    override fun onResume() {
        super.onResume()

        if (::binding.isInitialized && isNavigating) {
            resetSupplyState()
        }

        if (::cameraExecutor.isInitialized) {
            startCamera()
        }
    }


// =========================================================
// Pause
// =========================================================

    override fun onPause() {
        super.onPause()

        isScanRequested = false

        cameraProvider?.unbindAll()
        isCameraRunning = false
    }

// =========================================================
// Destroy
// =========================================================

    override fun onDestroy() {
        super.onDestroy()

        cameraProvider?.unbindAll()

        mainHandler.removeCallbacks(clearCompletionRunnable)

        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }
}