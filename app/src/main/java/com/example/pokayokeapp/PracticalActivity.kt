
package com.example.pokayokeapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.pokayokeapp.databinding.ActivityPracticalBinding
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.AdapterView
import android.widget.Spinner

class PracticalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPracticalBinding
    private lateinit var cameraExecutor: ExecutorService

    // 音声再生
    private var toneGenerator: ToneGenerator? = null

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

    // 親品番ごとの子かんばん必要枚数
    private val parentChildKanbanCounts =
        mutableMapOf<String, MutableMap<String, Int>>()

    // 子品番ごとの部品名
    private val childNames =
        mutableMapOf<String, String>()

    // 子品番ごとのロケNo.
    private val childLocationNumbers =
        mutableMapOf<String, String>()

    // 子品番ごとの供給番号（E列）
    private val childSupplyNumbers =
        mutableMapOf<String, String>()

    // 選択中の供給番号
    private var selectedSupplyNumber = ""

    // 選択中の子品番
    private var selectedChildCode = ""
    private var selectedChildName = ""
    private var selectedLocationNumber = ""

    // 親品番確定状態
    private var isParentConfirmed = false

    // 子かんばんチェック中かどうか
    private var isKanbanCheckMode = false

    // 子かんばんチェック対象の必要枚数
    private val requiredKanbanCounts =
        mutableMapOf<String, Int>()

    // 子かんばんチェック対象の実読取枚数
    private val scannedKanbanCounts =
        mutableMapOf<String, Int>()

    // Excel読み込み状態
    private var isExcelLoaded = false

    // 画面遷移中の二重処理防止
    private var isNavigating = false

    // 上側パネル
    private lateinit var topPanel: View

    // =========================================================
    // 言語切り替え設定
    // =========================================================

    private var currentLanguage = "ja"

    private val languagePrefs by lazy {
        getSharedPreferences("language_settings", MODE_PRIVATE)
    }

    private fun loadLanguage() {
        currentLanguage =
            languagePrefs.getString("language", "ja") ?: "ja"
    }

    private fun saveLanguage(language: String) {
        currentLanguage = language

        languagePrefs.edit()
            .putString("language", language)
            .apply()

        updateLanguage()
    }


// =========================================================
// 翻訳辞書
// =========================================================

    private val translations = mapOf(

        "ja" to mapOf(
            "read" to "読み取り",
            "reading" to "読み取り中...",
            "parent_wait" to "親品番のQRコードを読み取ってください",
            "part_wait" to "部品QR待ち",
            "location_wait" to "ロケQR待ち",
            "read_location" to "ロケQRを読み取ってください",
            "success" to "照合完了！",
            "success_status" to "照合成功！",
            "next_part" to "次の部品QRを読み取ってください",
            "mismatch" to "ロケQRが一致しません。\n正しいQRを読んでください",
            "mismatch_toast" to "ロケQRが一致しません",
            "ok" to "OK",
            "part_name" to "部品名",
            "part_code" to "部品品番",
            "location" to "ロケNo.",
            "Supply_number" to "供給番号",
            "parent" to "親品番",
            "reset" to "リセット",
            "parent_part" to "親品番",
            "back" to "戻る",
            "map" to "マップ",
            "standard" to "標準",
            "unknown" to "名前未登録",
            "not_registered" to "未登録",
            "no_excel" to "Excelを読み込めていません",

            "kanban_check" to "子かんばんチェック",
            "complete_count" to "完了",
            "next_target" to "次の対象",
            "required_count" to "必要枚数",
            "read_count" to "読取済み",
            "sheets" to "枚",
            "read_kanban" to "子かんばんQRを読み取ってください",
            "check_in_progress" to "子かんばんチェック中です\n必要枚数分のQRを読み取ってください",

            "check_excluded" to "段替えチェック対象外の子かんばんです",
            "not_check_target" to "この親品番のチェック対象ではありません"
        ),

        "en" to mapOf(
            "read" to "Scan",
            "reading" to "Scanning...",
            "parent_wait" to "Please scan the parent QR code",
            "part_wait" to "Waiting for part QR",
            "location_wait" to "Waiting for location QR",
            "read_location" to "Please scan the location QR",
            "success" to "Matching Complete!",
            "success_status" to "Match successful!",
            "next_part" to "Please scan the next part QR",
            "mismatch" to "Location QR does not match.\nPlease scan the correct QR.",
            "mismatch_toast" to "Location QR mismatch",
            "ok" to "OK",
            "part_name" to "Part Name",
            "part_code" to "Part Number",
            "location" to "Location No.",
            "Supply_number" to "Supply No.",
            "parent" to "Parent Part No.",
            "reset" to "Reset",
            "parent_part" to "Parent Part No.",
            "back" to "Back",
            "map" to "Map",
            "standard" to "Standard",
            "unknown" to "Unknown",
            "not_registered" to "Not registered",
            "no_excel" to "Excel data is not loaded",

            "kanban_check" to "Kanban Check",
            "complete_count" to "Completed",
            "next_target" to "Next Target",
            "required_count" to "Required",
            "read_count" to "Scanned",
            "sheets" to "pcs",
            "read_kanban" to "Please scan the child kanban QR code",
            "check_in_progress" to "Kanban check in progress.Please\n scan the required number of QR codes",

            "check_excluded" to "This child kanban is not included in the changeover check",
            "not_check_target" to "This item is not a check target for the selected parent part number"
        ),

        "km" to mapOf(
            "read" to "ស្កេន",
            "reading" to "កំពុងស្កេន...",
            "parent_wait" to "សូមស្កេន QR កូដផលិតផលមេ",
            "part_wait" to "រង់ចាំ QR គ្រឿងបន្លាស់",
            "location_wait" to "រង់ចាំ QR ទីតាំង",
            "read_location" to "សូមស្កេន QR ទីតាំង",
            "success" to "ផ្គូផ្គងបានជោគជ័យ!",
            "success_status" to "ផ្គូផ្គងជោគជ័យ!",
            "next_part" to "សូមស្កេន QR គ្រឿងបន្លាស់បន្ទាប់",
            "mismatch" to "QR ទីតាំងមិនត្រូវគ្នាទេ។\nសូមស្កេន QR ត្រឹមត្រូវ។",
            "mismatch_toast" to "QR ទីតាំងមិនត្រូវគ្នា",
            "ok" to "ត្រឹមត្រូវ",
            "part_name" to "ឈ្មោះគ្រឿងបន្លាស់",
            "part_code" to "លេខកូដគ្រឿងបន្លាស់",
            "location" to "លេខទីតាំង",
            "Supply_number" to "លេខផ្គត់ផ្គង់",
            "parent" to "លេខផលិតផលមេ",
            "reset" to "កំណត់ឡើងវិញ",
            "parent_part" to "លេខផលិតផលមេ",
            "back" to "ត្រឡប់ក្រោយ",
            "map" to "ផែនទី",
            "standard" to "ស្តង់ដារ",
            "unknown" to "មិនស្គាល់ឈ្មោះ",
            "not_registered" to "មិនបានចុះបញ្ជី",
            "no_excel" to "មិនទាន់បានផ្ទុកទិន្នន័យ Excel",

            "kanban_check" to "ការត្រួតពិនិត្យកាន់បាន",
            "complete_count" to "បានបញ្ចប់",
            "next_target" to "គោលដៅបន្ទាប់",
            "required_count" to "ចំនួនត្រូវការ",
            "read_count" to "បានស្កេន",
            "sheets" to "សន្លឹក",
            "read_kanban" to "សូមស្កេន QR កូដកាន់បានរបស់គ្រឿងបន្លាស់",
            "check_in_progress" to "កំពុងត្រួតពិនិត្យកាន់បាន\nសូមស្កេន QR កូដតាមចំនួនដែលត្រូវការ",

            "check_excluded" to "កាន់បានគ្រឿងបន្លាស់នេះមិនស្ថិតក្នុងការត្រួតពិនិត្យប្តូរម៉ូដែលទេ",
            "not_check_target" to "ធាតុនេះមិនស្ថិតក្នុងគោលដៅត្រួតពិនិត្យសម្រាប់លេខផលិតផលមេនេះទេ"
        )
    )

    private fun t(key: String): String {
        return translations[currentLanguage]?.get(key)
            ?: translations["ja"]?.get(key)
            ?: key
    }

    // =========================================================
// 言語設定画面への反映
// =========================================================

    private fun setupLanguageSpinner() {

        val spinner = findViewById<Spinner>(
            R.id.languageSpinner
        )

        val savedLanguage = currentLanguage

        val position = when (savedLanguage) {
            "km" -> 1
            "en" -> 2
            else -> 0
        }

        spinner.setSelection(position, false)

        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val language = when (position) {
                        1 -> "km"
                        2 -> "en"
                        else -> "ja"
                    }

                    if (language != currentLanguage) {
                        saveLanguage(language)
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}
            }
    }

    // =========================================================
// 言語変更後の画面更新
// =========================================================

    private fun updateLanguage() {

        binding.modeText.text = t("standard")
        binding.readButton.text = t("read")
        binding.backButton.text = t("back")
        binding.resetParentButton.text = t("reset")
        binding.shelfMapButton.text = t("map")

        binding.parentCodeText.text =
            "${t("parent")}：$parentCode"

        if (!isParentConfirmed) {

            binding.statusText.text = t("parent_wait")
            binding.topCodeText.text = t("parent_wait")

        } else if (isKanbanCheckMode) {

            showKanbanCheckProgress()

        } else if (selectedChildCode.isNotEmpty()) {

            binding.topCodeText.text =
                "${t("part_name")}：$selectedChildName\n" +
                        "${t("part_code")}：$selectedChildCode\n" +
                        "${t("location")}：$selectedLocationNumber"

            binding.bottomStatusText.text = t("location_wait")
            binding.statusText.text = t("read_location")

        } else {

            binding.topCodeText.text = t("part_wait")
            binding.statusText.text = t("read_next_part")
        }
    }

    companion object {


        private const val REQUEST_CAMERA = 100
        private const val REQUEST_STORAGE = 101

        private const val FOLDER_NAME = "PokaYoke"
        private const val EXCEL_FILE_NAME = "parts.xlsx"

        private const val MODE_NAME = "買物"
        private const val TAG = "Practical"

        // =========================================================
        // 音・バイブレーション機能 ON/OFF設定
        // =========================================================

        // 照合完了音
        private const val ENABLE_OK_SOUND = true

        // 照合不一致音
        private const val ENABLE_NG_SOUND = true

        // 照合不一致時のバイブレーション
        private const val ENABLE_NG_VIBRATION = true

    }

    // 照合成功後の表示リセット
    private val mainHandler = Handler(Looper.getMainLooper())

    private val clearCompletionRunnable = Runnable {
        if (
            !isFinishing &&
            !isDestroyed &&
            isParentConfirmed &&
            !isKanbanCheckMode &&
            selectedChildCode.isNotEmpty()
        ) {
            selectedChildCode = ""
            selectedChildName = ""
            selectedLocationNumber = ""
            selectedSupplyNumber = ""

            binding.topCodeText.text = t("part_wait")

            binding.statusText.text = t("read_next_part")

            topPanel.setBackgroundColor(Color.WHITE)
            binding.topCodeText.setTextColor(Color.BLACK)

            binding.bottomStatusText.setTextColor(Color.BLACK)

            binding.statusText.text =t("next_part")
        }
    }

    // =========================================================
    // onCreate
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPracticalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadLanguage()
        setupLanguageSpinner()

        cameraExecutor = Executors.newSingleThreadExecutor()

        // 音声初期化
        try {
            toneGenerator = ToneGenerator(
                AudioManager.STREAM_NOTIFICATION,
                100
            )
        } catch (e: Exception) {
            Log.e(TAG, "音声初期化失敗", e)
        }

        topPanel = findViewById(R.id.topPanel)

// 画面の文字を翻訳辞書から取得
        binding.modeText.text = t("standard")
        binding.readButton.text = t("read")

        binding.statusText.text = t("parent_wait")

        binding.parentCodeText.text = t("parent") + "："

        binding.splitView.visibility = View.GONE

        binding.topCodeText.text = t("parent_wait")

        topPanel.setBackgroundColor(Color.WHITE)

        binding.bottomStatusText.text = ""

// 戻るボタンの動作
        binding.backButton.text = t("back")
        binding.backButton.setOnClickListener {
            finish()
        }

// 読み取りボタンの動作
        binding.readButton.setOnClickListener {
            requestScan()
        }

// リセットボタンの表示と動作
        binding.resetParentButton.text = t("reset")
        binding.resetParentButton.setOnClickListener {
            mainHandler.removeCallbacks(clearCompletionRunnable)
            resetPracticalState()
        }

        binding.shelfMapButton.setOnClickListener {
            startActivity(
                Intent(this, ShelfMapActivity::class.java)
            )
        }
        updateLanguage()

        checkStoragePermission()
    }

    // =========================================================
    // ストレージ権限
    // =========================================================

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) {
                loadExcelData()
                checkCameraPermission()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_STORAGE
                )
            }
        } else {
            loadExcelData()
            checkCameraPermission()
        }
    }

    // =========================================================
    // カメラ権限
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
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
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
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
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
        val storagePath = Environment.getExternalStorageDirectory()
        val folder = File(storagePath, FOLDER_NAME)

        if (!folder.exists()) {
            folder.mkdirs()
        }

        return File(folder, EXCEL_FILE_NAME)
    }

    // =========================================================
    // Excel読み込み
    //
    // A列：親品番
    // B列：子品番
    // C列：部品名
    // D列：ロケNo.
    // E列：既存情報（保持）
    // F列：子かんばん枚数（0～2）
    // 1行目：見出し
    // =========================================================

    private fun loadExcelData() {
        val file = getExcelFile()

        val loadedSupplyNumbers =
            mutableMapOf<String, String>()

        Log.d(TAG, "Excelパス: ${file.absolutePath}")

        if (!file.exists()) {
            isExcelLoaded = false
            binding.readButton.isEnabled = false

            binding.statusText.text =
                "parts.xlsx が見つかりません。\n" +
                        "内部ストレージ/PokaYoke に配置してください。"

            Log.e(TAG, "Excelファイルがありません")
            return
        }

        try {
            val loadedChildren =
                mutableMapOf<String, MutableSet<String>>()

            val loadedKanbanCounts =
                mutableMapOf<String, MutableMap<String, Int>>()

            val loadedChildNames =
                mutableMapOf<String, String>()

            val loadedLocationNumbers =
                mutableMapOf<String, String>()

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
                            getCellValue(row.getCell(0)).trim()

                        // B列：子品番
                        val child =
                            getCellValue(row.getCell(1)).trim()

                        // C列：部品名
                        val childName =
                            getCellValue(row.getCell(2)).trim()

                        // D列：ロケNo.
                        val locationNumber =
                            getCellValue(row.getCell(3)).trim()

                        // E列：供給番号
                        val supplyNumber =
                            getCellValue(row.getCell(4)).trim()

                        // F列：子かんばん枚数
                        val kanbanCountText =
                            getCellValue(row.getCell(5)).trim()

                        if (parent.isEmpty() || child.isEmpty()) {
                            continue
                        }

                        // F列は0、1、2のみ許可
                        if (
                            kanbanCountText.isEmpty() ||
                            !kanbanCountText.matches(Regex("[0-2]"))
                        ) {
                            throw IllegalStateException(
                                "${i + 1}行目のF列が不正です。" +
                                        "子品番=$child、値='$kanbanCountText'。" +
                                        "F列には0、1、2を入力してください。"
                            )
                        }

                        val parentKey = normalizeCode(parent)
                        val childKey = normalizeCode(child)
                        val kanbanCount = kanbanCountText.toInt()

                        // 親品番と子品番を紐づける
                        loadedChildren
                            .getOrPut(parentKey) {
                                mutableSetOf()
                            }
                            .add(child)

                        // 親品番ごとの子かんばん枚数を保存
                        val countsForParent =
                            loadedKanbanCounts.getOrPut(parentKey) {
                                mutableMapOf()
                            }

                        // 同じ親品番・子品番の枚数設定が
                        // Excel内で食い違っていないか確認
                        val previousCount =
                            countsForParent[childKey]

                        if (
                            previousCount != null &&
                            previousCount != kanbanCount
                        ) {
                            throw IllegalStateException(
                                "${i + 1}行目の子かんばん枚数が、" +
                                        "同じ親品番・子品番の設定と一致しません。" +
                                        "親品番=$parent、子品番=$child"
                            )
                        }

                        countsForParent[childKey] = kanbanCount

                        // 子品番と部品名を紐づける
                        if (childName.isNotEmpty()) {
                            loadedChildNames[childKey] = childName
                        }

// 子品番とロケNo.を紐づける
                        if (locationNumber.isNotEmpty()) {
                            loadedLocationNumbers[childKey] = locationNumber
                        }

// 子品番と供給番号を紐づける
                        if (supplyNumber.isNotEmpty()) {
                            loadedSupplyNumbers[childKey] = supplyNumber
                        }

                    }
                }
            }

            // 読み込んだデータを反映
            parentChildren.clear()
            parentChildren.putAll(loadedChildren)

            parentChildKanbanCounts.clear()
            parentChildKanbanCounts.putAll(loadedKanbanCounts)

            childNames.clear()
            childNames.putAll(loadedChildNames)

            childLocationNumbers.clear()
            childLocationNumbers.putAll(loadedLocationNumbers)

            childSupplyNumbers.clear()
            childSupplyNumbers.putAll(loadedSupplyNumbers)

            isExcelLoaded = true
            binding.readButton.isEnabled = true

            binding.statusText.text =t("parent_wait")

            Log.d(
                TAG,
                "Excel読み込み成功: ${parentChildren.size}種類の親品番"
            )

        } catch (e: Exception) {
            isExcelLoaded = false
            binding.readButton.isEnabled = false

            binding.statusText.text =
                "Excelの読み込みに失敗しました。\n${e.message ?: ""}"

            Log.e(TAG, "Excel読み込み失敗", e)

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
            CellType.STRING -> cell.stringCellValue.trim()

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

                    else -> cell.toString().trim()
                }
            }

            else -> cell.toString().trim()
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
    binding.readButton.text = t("reading")
}

// =========================================================
// 読み取りボタンを待機状態に戻す
// =========================================================

private fun finishScanButton() {
    isScanRequested = false

    if (!isFinishing && !isDestroyed && !isNavigating) {
        binding.readButton.isEnabled = isExcelLoaded
        binding.readButton.text = t("read")
    }
}
// =========================================================
// 照合完了音
// =========================================================

    private fun playOkSound() {
        if (!ENABLE_OK_SOUND) return

        try {
            toneGenerator?.startTone(
                ToneGenerator.TONE_PROP_ACK,
                250
            )
        } catch (e: Exception) {
            Log.e(TAG, "完了音再生失敗", e)
        }
    }


// =========================================================
// 照合不一致音
// =========================================================

private fun playNgSound() {
    if (!ENABLE_NG_SOUND) return

    try {
        toneGenerator?.startTone(
            ToneGenerator.TONE_PROP_NACK,
            400
        )
    } catch (e: Exception) {
        Log.e(TAG, "NG音再生失敗", e)
    }
}

// =========================================================
// 照合不一致時のバイブレーション
// =========================================================

private fun vibrateNg() {
    if (!ENABLE_NG_VIBRATION) return

    try {
        val vibrator =
            getSystemService(Vibrator::class.java)

        if (vibrator?.hasVibrator() == true) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        300,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(300)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "バイブレーション失敗", e)
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
                Log.e(TAG, "カメラ初期化失敗", e)
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
                                Log.e(TAG, "QRコード処理失敗", e)

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
                    Log.e(TAG, "QR解析失敗", e)
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
        Log.e(TAG, "カメラ起動失敗", e)

        binding.statusText.text =
            "カメラを起動できませんでした"
    }
}

// =========================================================
// QRコード処理の振り分け
// =========================================================

private fun processScannedCode(rawCode: String) {
    val code = rawCode.trim()

    if (code.isEmpty() || isNavigating) return

    when {
        !isParentConfirmed -> processParentCode(code)

        isKanbanCheckMode -> processKanbanCode(code)

        else -> processChildCode(code)
    }
}

// =========================================================
// 親品番処理
// =========================================================

private fun processParentCode(qrCode: String) {
    val extractedCode = extractParentCode(qrCode)
    val parentKey = normalizeCode(extractedCode)

    val children = parentChildren[parentKey]

    if (children.isNullOrEmpty()) {
        parentCode = ""
        selectedChildCode = ""
        isParentConfirmed = false

        binding.parentCodeText.text = t("parent")+"："
        binding.splitView.visibility = View.GONE

        binding.topCodeText.text =
            "親品番のQRコードを\n読み込んでください"

        topPanel.setBackgroundColor(Color.WHITE)

        binding.bottomStatusText.text = ""
        binding.statusText.text =
            "親品番が登録されていません"

        Toast.makeText(
            this,
            "親品番が parts.xlsx に登録されていません",
            Toast.LENGTH_LONG
        ).show()

        Log.w(TAG, "親品番なし: $extractedCode")
        return
    }

    parentCode = extractedCode
    selectedChildCode = ""
    selectedChildName = ""
    selectedLocationNumber = ""
    isParentConfirmed = true

    binding.parentCodeText.text = t("parent")+"：$parentCode"
    binding.splitView.visibility = View.VISIBLE

    prepareKanbanCheck(parentKey)

    HistoryManager.addHistory(
        this,
        mode = MODE_NAME,
        action = "親品番決定",
        parentCode = parentCode,
        method = "QR"
    )

    Log.d(TAG, "親品番確定: $parentCode")
}

// =========================================================
// 子かんばんチェック対象の準備
// =========================================================

private fun prepareKanbanCheck(parentKey: String) {
    requiredKanbanCounts.clear()
    scannedKanbanCounts.clear()

    val counts = parentChildKanbanCounts[parentKey]
        ?: emptyMap<String, Int>()

    for ((childKey, count) in counts) {
        if (count > 0) {
            requiredKanbanCounts[childKey] = count
            scannedKanbanCounts[childKey] = 0
        }
    }

    if (requiredKanbanCounts.isEmpty()) {
        isKanbanCheckMode = false

        binding.topCodeText.text = t("part_wait")
        topPanel.setBackgroundColor(Color.WHITE)
        binding.topCodeText.setTextColor(Color.BLACK)

        binding.bottomStatusText.text = ""

        binding.statusText.text =
            "子かんばんチェック対象なし\n部品QRを読み取ってください"

        Log.d(TAG, "子かんばんチェック対象なし")
        return
    }

    isKanbanCheckMode = true
    showKanbanCheckProgress()

    Log.d(
        TAG,
        "子かんばんチェック開始: ${requiredKanbanCounts.size}種類"
    )
}

// =========================================================
// 子かんばんQR処理
//
// QR内容：B列の子品番そのもの
// 必要枚数2の場合、同じQRを2回読み取る
// =========================================================

private fun processKanbanCode(code: String) {
    val childKey = normalizeCode(code)

    val requiredCount = requiredKanbanCounts[childKey]

    if (requiredCount == null) {

        binding.statusText.text = t("check_excluded")

        Toast.makeText(
            this,
            t("not_check_target"),
            Toast.LENGTH_SHORT
        ).show()

        HistoryManager.addHistory(
            this,
            mode = MODE_NAME,
            action = "対象外子かんばん",
            parentCode = parentCode,
            childCode = code,
            method = "QR"
        )
        Log.w(TAG, "対象外子かんばん: $code")

        return
    }


    val scannedCount = scannedKanbanCounts[childKey] ?: 0

    if (scannedCount >= requiredCount) {
        binding.statusText.text =
            "この子かんばんは必要枚数分チェック済みです"

        Toast.makeText(
            this,
            "この子かんばんはチェック済みです",
            Toast.LENGTH_SHORT
        ).show()

        return
    }

    scannedKanbanCounts[childKey] = scannedCount + 1

    HistoryManager.addHistory(
        this,
        mode = MODE_NAME,
        action = "子かんばん読取",
        parentCode = parentCode,
        childCode = code,
        method = "QR"
    )

    Log.d(
        TAG,
        "子かんばん読取: $code " +
                "${scannedCount + 1}/$requiredCount"
    )

    if (isKanbanCheckComplete()) {
        finishKanbanCheck()
    } else {
        showKanbanCheckProgress()
    }
}

// =========================================================
// 子かんばんの全件完了判定
// =========================================================

private fun isKanbanCheckComplete(): Boolean {
    if (requiredKanbanCounts.isEmpty()) return true

    return requiredKanbanCounts.all { (childKey, required) ->
        (scannedKanbanCounts[childKey] ?: 0) >= required
    }
}

// =========================================================
// 子かんばんチェックの進捗表示
// =========================================================

private fun showKanbanCheckProgress() {
    isKanbanCheckMode = true

    val totalRequired = requiredKanbanCounts.values.sum()
    val totalScanned = scannedKanbanCounts.values.sum()

    val nextChild = requiredKanbanCounts.keys.firstOrNull { childKey ->
        (scannedKanbanCounts[childKey] ?: 0) <
                (requiredKanbanCounts[childKey] ?: 0)
    }

    val nextRequired = nextChild?.let {
        requiredKanbanCounts[it] ?: 0
    } ?: 0

    val nextScanned = nextChild?.let {
        scannedKanbanCounts[it] ?: 0
    } ?: 0

    val nextChildName = nextChild?.let {
        childNames[it] ?: "名前未登録"
    } ?: ""

    topPanel.setBackgroundColor(Color.rgb(255, 244, 214))
    binding.topCodeText.setTextColor(Color.BLACK)
    binding.topCodeText.text =
        "${t("kanban_check")}\n" +
                "${t("complete_count")}：$totalScanned / $totalRequired ${t("sheets")}\n" +
                "${t("next_target")}：$nextChildName\n" +
                "${t("part_code")}：${nextChild ?: ""}\n" +
                "${t("required_count")}：$nextRequired ${t("sheets")}\n" +
                "${t("read_count")}：$nextScanned ${t("sheets")}"

    binding.bottomStatusText.text = t("read_kanban")

    binding.bottomStatusText.setTextColor(Color.BLACK)

    binding.statusText.text = t("check_in_progress")
}

// =========================================================
// 子かんばんチェック完了
// =========================================================

private fun finishKanbanCheck() {
    isKanbanCheckMode = false

    val totalRequired = requiredKanbanCounts.values.sum()

    topPanel.setBackgroundColor(Color.rgb(20, 145, 75))

    binding.topCodeText.text =
        "✓\n段替えチェック完了！\n" +
                "子かんばん：$totalRequired 枚"

    binding.topCodeText.setTextColor(Color.WHITE)

    binding.bottomStatusText.text = "OK"
    binding.bottomStatusText.setTextColor(Color.rgb(20, 145, 75))

    binding.statusText.text =
        "段替えチェック完了\n部品QRを読み取ってください"

    HistoryManager.addHistory(
        this,
        mode = MODE_NAME,
        action = "子かんばんチェック完了",
        parentCode = parentCode,
        method = "QR"
    )

    Toast.makeText(
        this,
        "子かんばんチェック完了",
        Toast.LENGTH_SHORT
    ).show()

    Log.d(TAG, "子かんばんチェック完了: 親品番=$parentCode")

    mainHandler.removeCallbacks(clearCompletionRunnable)

    mainHandler.postDelayed({
        if (
            !isFinishing &&
            !isDestroyed &&
            isParentConfirmed &&
            !isKanbanCheckMode
        ) {
            binding.topCodeText.text = t("part_wait")
            topPanel.setBackgroundColor(Color.WHITE)
            binding.topCodeText.setTextColor(Color.BLACK)

            binding.bottomStatusText.text = ""
            binding.bottomStatusText.setTextColor(Color.BLACK)

            binding.statusText.text =
                "部品QRを読み取ってください"
        }
    }, 1200L)
}

// =========================================================
// 通常の買物処理
//
// 1回目：部品QR（B列の子品番）
// 2回目：ロケQR（子品番 + #）
// =========================================================

private fun processChildCode(code: String) {

    // -----------------------------------------------------
    // 1回目：部品QR
    // -----------------------------------------------------

    if (selectedChildCode.isEmpty()) {
        val children =
            parentChildren[normalizeCode(parentCode)]
                ?: emptySet<String>()

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

        selectedChildCode = matchedChild

        val childKey = normalizeCode(selectedChildCode)

        selectedChildName =
            childNames[childKey] ?: "名前未登録"

        selectedLocationNumber =
            childLocationNumbers[childKey] ?: "未登録"

        selectedSupplyNumber =
            childSupplyNumbers[childKey] ?: ""

        binding.topCodeText.text =
            t("part_name")+"：$selectedChildName\n" +
                    t("part_code")+"：$selectedChildCode\n" +
                    t("location")+"：$selectedLocationNumber\n"+
                        t("Supply_number")+"：$selectedSupplyNumber"

        topPanel.setBackgroundColor(Color.GREEN)
        binding.topCodeText.setTextColor(Color.BLACK)

        binding.bottomStatusText.text = "ロケQR待ち"
        binding.bottomStatusText.setTextColor(Color.BLACK)

        binding.statusText.text =
            "ロケQRを読み取ってください"

        HistoryManager.addHistory(
            this,
            mode = MODE_NAME,
            action = "子品番読取",
            parentCode = parentCode,
            childCode = selectedChildCode,
            method = "QR"
        )

        Log.d(TAG, "部品QR記憶: $selectedChildCode")
        return
    }

    // -----------------------------------------------------
    // 2回目：ロケQR
    // -----------------------------------------------------

    // -----------------------------------------------------
    // 2回目：ロケQR・供給番号照合
    // -----------------------------------------------------

    // D列：ロケNo. + #
    val expectedBuyCode = "${selectedChildCode}#"

    // E列：供給番号
    val expectedSupplyCode = selectedSupplyNumber

    // D列との照合
    val isLocationMatched = code == expectedBuyCode

    // E列との照合（OFFにする場合は以下をコメントアウト）
    val isSupplyMatched =
        expectedSupplyCode.isNotEmpty() &&
                code == expectedSupplyCode

    // どちらか一致すれば照合完了
    if (isLocationMatched || isSupplyMatched) {

        mainHandler.removeCallbacks(clearCompletionRunnable)

        // 完了音
        playOkSound()

        HistoryManager.addHistory(
            this,
            mode = MODE_NAME,
            action = "QR照合OK",
            parentCode = parentCode,
            childCode = code,
            method = "QR"
        )

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
            TAG,
            "照合完了: 親品番=$parentCode, 子品番=$selectedChildCode"
        )

        mainHandler.postDelayed(
            clearCompletionRunnable,
            1000L
        )

        return
    }

    // -----------------------------------------------------
    // ロケQR不一致
    // -----------------------------------------------------

    binding.statusText.text =
        "ロケQRが一致しません。\n正しいQRを読んでください"

    // NG音
    playNgSound()

    // バイブレーション
    vibrateNg()

    Toast.makeText(
        this,
        "ロケQRが一致しません",
        Toast.LENGTH_SHORT
    ).show()

    HistoryManager.addHistory(
        this,
        mode = MODE_NAME,
        action = "ロケQR不一致",
        parentCode = parentCode,
        childCode = code,
        method = "QR"
    )

    Log.w(
        TAG,
        "ロケQR不一致: 期待値=$expectedBuyCode, 読取値=$code"
    )
}

// =========================================================
// 初期状態に戻す
// =========================================================

private fun resetPracticalState() {
    isScanRequested = false
    isNavigating = false

    parentCode = ""
    selectedChildCode = ""
    selectedChildName = ""
    selectedLocationNumber = ""
    selectedSupplyNumber = ""

    isParentConfirmed = false
    isKanbanCheckMode = false

    requiredKanbanCounts.clear()
    scannedKanbanCounts.clear()

    binding.modeText.text = t("standard")
    binding.readButton.text = t("read")

    binding.statusText.text = t("parent_wait")

    binding.parentCodeText.text = "${t("parent")}："

    binding.topCodeText.text = t("parent_wait")
    binding.readButton.isEnabled = isExcelLoaded

    binding.splitView.visibility = View.GONE

    binding.topCodeText.setTextColor(Color.BLACK)
    topPanel.setBackgroundColor(Color.WHITE)

    binding.bottomStatusText.text = ""
    binding.bottomStatusText.setTextColor(Color.BLACK)
}

// =========================================================
// QRコードから親品番を抽出
// 形式：英数字6文字-英数字3文字
// =========================================================

private fun extractParentCode(code: String): String {
    val text = code.trim()
    val pattern = Regex("[A-Za-z0-9]{6}-[A-Za-z0-9]{3}")

    return pattern.find(text)?.value ?: text
}

// =========================================================
// Resume
// =========================================================

override fun onResume() {
    super.onResume()

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

        // 完了音出し
        toneGenerator?.release()
        toneGenerator = null
    }

// PracticalActivityクラス終了
}