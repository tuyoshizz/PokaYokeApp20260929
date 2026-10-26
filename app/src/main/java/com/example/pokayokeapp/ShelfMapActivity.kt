
package com.example.pokayokeapp

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.math.max
import kotlin.math.min

class ShelfMapActivity : Activity() {

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage = 0

    private lateinit var pdfView: ZoomPdfView
    private lateinit var pageText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(35, 35, 35))
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.rgb(25, 25, 25))
        }

        val closeButton = Button(this).apply {
            text = "閉じる"
            setOnClickListener { finish() }
        }

        pageText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
        }

        val minusButton = Button(this).apply {
            text = "−"
            textSize = 16f
            setOnClickListener { pdfView.zoomBy(0.8f) }
        }

        val plusButton = Button(this).apply {
            text = "＋"
            textSize = 16f
            setOnClickListener { pdfView.zoomBy(1.25f) }
        }

        toolbar.addView(
            closeButton,
            LinearLayout.LayoutParams(
                0, dp(48), 1f
            )
        )
        toolbar.addView(
            minusButton,
            LinearLayout.LayoutParams(dp(60), dp(48))
        )
        toolbar.addView(
            plusButton,
            LinearLayout.LayoutParams(dp(60), dp(48))
        )

        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        pdfView = ZoomPdfView(this)

        root.addView(
            pdfView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        )

        val pageControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(25, 25, 25))
        }

        val prevButton = Button(this).apply {
            text = "◀ 前のページ"
            setOnClickListener { showPage(currentPage - 1) }
        }

        val nextButton = Button(this).apply {
            text = "次のページ ▶"
            setOnClickListener { showPage(currentPage + 1) }
        }

        pageControls.addView(prevButton)
        pageControls.addView(pageText)
        pageControls.addView(nextButton)

        root.addView(
            pageControls,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)

        openPdf()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun openPdf() {
        try {
            val folder = File(
                Environment.getExternalStorageDirectory(),
                "PokaYoke"
            )

            val pdfFile = File(folder, "shelf_map.pdf")

            if (!pdfFile.exists()) {
                Toast.makeText(
                    this,
                    "PDFが見つかりません。\n" +
                            "PokaYoke/shelf_map.pdf を確認してください。",
                    Toast.LENGTH_LONG
                ).show()
                finish()
                return
            }

            fileDescriptor = ParcelFileDescriptor.open(
                pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )

            pdfRenderer = PdfRenderer(fileDescriptor!!)
            showPage(0)

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "PDFを開けませんでした: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private fun showPage(index: Int) {
        val renderer = pdfRenderer ?: return

        if (index !in 0 until renderer.pageCount) return

        try {
            val page = renderer.openPage(index)

            val bitmap = Bitmap.createBitmap(
                page.width,
                page.height,
                Bitmap.Config.ARGB_8888
            )

            bitmap.eraseColor(Color.WHITE)

            page.render(
                bitmap,
                null,
                null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )

            page.close()

            currentPage = index
            pageText.text = "${index + 1} / ${renderer.pageCount}"

            pdfView.setBitmap(bitmap)

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "PDFページの表示に失敗しました",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroy() {
        pdfView.recycleBitmap()
        pdfRenderer?.close()
        pdfRenderer = null
        fileDescriptor = null
        super.onDestroy()
    }



    private class ZoomPdfView(
        context: android.content.Context
    ) : View(context) {

        private var bitmap: Bitmap? = null

        private val paint = android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG or
                    android.graphics.Paint.FILTER_BITMAP_FLAG
        )

        // 画面に収まる初期倍率を1.0とする
        private var zoom = 1f
        private val maxZoom = 6f
        private var baseScale = 1f

        // 画像左上の画面座標
        private var offsetX = 0f
        private var offsetY = 0f

        private var lastX = 0f
        private var lastY = 0f
        private var dragging = false
        private var initialized = false

        private val detector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

                override fun onScale(
                    detector: ScaleGestureDetector
                ): Boolean {
                    val oldZoom = zoom
                    val newZoom = (oldZoom * detector.scaleFactor)
                        .coerceIn(1f, maxZoom)

                    if (oldZoom == newZoom) return true

                    val ratio = newZoom / oldZoom

                    // ピンチ中心を固定して拡大・縮小する
                    offsetX = detector.focusX -
                            (detector.focusX - offsetX) * ratio

                    offsetY = detector.focusY -
                            (detector.focusY - offsetY) * ratio

                    zoom = newZoom

                    invalidate()
                    return true
                }
            }
        )

        fun setBitmap(newBitmap: Bitmap) {
            bitmap?.recycle()
            bitmap = newBitmap

            zoom = 1f
            offsetX = 0f
            offsetY = 0f
            initialized = false

            invalidate()
        }

        fun zoomBy(factor: Float) {
            val oldZoom = zoom
            val newZoom = (oldZoom * factor)
                .coerceIn(1f, maxZoom)

            if (oldZoom == newZoom) return

            // ＋－ボタンではビュー中央を基準に拡大
            val ratio = newZoom / oldZoom
            val centerX = width / 2f
            val centerY = height / 2f

            offsetX = centerX -
                    (centerX - offsetX) * ratio

            offsetY = centerY -
                    (centerY - offsetY) * ratio

            zoom = newZoom
            invalidate()
        }

        fun recycleBitmap() {
            bitmap?.recycle()
            bitmap = null
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            initialized = false
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.rgb(55, 55, 55))

            val image = bitmap ?: return
            if (width <= 0 || height <= 0) return

            baseScale = min(
                width.toFloat() / image.width,
                height.toFloat() / image.height
            )

            val drawScale = baseScale * zoom
            val drawWidth = image.width * drawScale
            val drawHeight = image.height * drawScale

            // 初回は画面中央に配置
            if (!initialized) {
                offsetX = (width - drawWidth) / 2f
                offsetY = (height - drawHeight) / 2f
                initialized = true
            }

            // 画像が画面より小さい場合は中央に配置。
            // 大きい場合は端から背景が見えないように制限する。
            offsetX = if (drawWidth <= width) {
                (width - drawWidth) / 2f
            } else {
                offsetX.coerceIn(width - drawWidth, 0f)
            }

            offsetY = if (drawHeight <= height) {
                (height - drawHeight) / 2f
            } else {
                offsetY.coerceIn(height - drawHeight, 0f)
            }

            val matrix = android.graphics.Matrix()
            matrix.setScale(drawScale, drawScale)
            matrix.postTranslate(offsetX, offsetY)

            canvas.drawBitmap(image, matrix, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            detector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                    dragging = true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (
                        dragging &&
                        !detector.isInProgress &&
                        event.pointerCount == 1 &&
                        zoom > 1f
                    ) {
                        offsetX += event.x - lastX
                        offsetY += event.y - lastY
                        invalidate()
                    }

                    lastX = event.x
                    lastY = event.y
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    val remainingIndex =
                        if (event.actionIndex == 0) 1 else 0

                    if (remainingIndex < event.pointerCount) {
                        lastX = event.getX(remainingIndex)
                        lastY = event.getY(remainingIndex)
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                }
            }

            return true
        }
    }
}
