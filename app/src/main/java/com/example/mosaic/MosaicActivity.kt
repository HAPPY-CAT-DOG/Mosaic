package com.example.mosaic

import android.content.ContentValues
import android.graphics.*
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.max
import kotlin.math.min
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MosaicActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var original: Bitmap
    private lateinit var result: Bitmap

    private var brushSize = 40
    private var mode = Mode.MOSAIC
    private var currentPath: Path? = null

    private val layers = mutableListOf<Layer>()  // undo 대상

    enum class Mode { MOSAIC, BLUR, ERASE }

    data class Layer(
        val mask: Bitmap,
        val mode: Mode,
        val size: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mosaic)

        imageView = findViewById(R.id.imageView)

        val uri = Uri.parse(intent.getStringExtra("imageUri"))
        original = MediaStore.Images.Media.getBitmap(contentResolver, uri)
        result = original.copy(Bitmap.Config.ARGB_8888, true)

        imageView.setImageBitmap(result)

        setupButtons()
        setupTouch()
    }

    private fun setupButtons() {

        findViewById<Button>(R.id.btn_mosaic).setOnClickListener { mode = Mode.MOSAIC }
        findViewById<Button>(R.id.btn_blur).setOnClickListener { mode = Mode.BLUR }
        findViewById<Button>(R.id.btn_erase).setOnClickListener { mode = Mode.ERASE }

        findViewById<Button>(R.id.btn_undo).setOnClickListener {
            if (layers.isNotEmpty()) {
                layers.removeAt(layers.lastIndex)
                applyAllLayers()
            }
        }

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            saveImage(result)
            Toast.makeText(this, "저장됨", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<SeekBar>(R.id.seekbar_strength).apply {
            progress = brushSize
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, from: Boolean) {
                    brushSize = max(v, 8)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
    }

    private fun setupTouch() {

        imageView.setOnTouchListener { v: View, e: MotionEvent ->

            val p = mapToBitmap(e.x, e.y) ?: return@setOnTouchListener true

            when (e.action) {

                MotionEvent.ACTION_DOWN -> {
                    currentPath = Path().apply { moveTo(p.x, p.y) }
                }

                MotionEvent.ACTION_MOVE -> {
                    currentPath?.lineTo(p.x, p.y)
                    preview()                 // 미리보기
                }

                MotionEvent.ACTION_UP -> {
                    currentPath?.let { createLayer(it) }
                    currentPath = null
                    applyAllLayers()          // 최종 반영
                }
            }

            v.performClick()
            true
        }
    }

    private fun mapToBitmap(x: Float, y: Float): PointF? {

        val m = imageView.imageMatrix
        val v = FloatArray(9)
        m.getValues(v)

        val bx = (x - v[Matrix.MTRANS_X]) / v[Matrix.MSCALE_X]
        val by = (y - v[Matrix.MTRANS_Y]) / v[Matrix.MSCALE_Y]

        if (bx < 0 || by < 0 || bx >= original.width || by >= original.height) return null
        return PointF(bx, by)
    }

    // ─────────────────────────────────────────────
    // PATH → MASK → LAYER 생성
    // ─────────────────────────────────────────────
    private fun createLayer(path: Path) {

        val mask = Bitmap.createBitmap(
            original.width, original.height,
            Bitmap.Config.ALPHA_8
        )

        val c = Canvas(mask)
        val p = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = brushSize.toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        c.drawPath(path, p)
        layers.add(Layer(mask, mode, brushSize))
    }

    // ─────────────────────────────────────────────
    // PREVIEW
    // ─────────────────────────────────────────────
    private fun preview() {

        val temp = original.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(temp)

        for (l in layers) applyLayer(c, l)

        // 현재 stroke 미리 반영
        currentPath?.let {
            val mask = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ALPHA_8)
            val mc = Canvas(mask)
            val mp = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = brushSize.toFloat()
                strokeCap = Paint.Cap.ROUND
            }
            mc.drawPath(it, mp)

            applyLayer(c, Layer(mask, mode, brushSize))
        }

        imageView.setImageBitmap(temp)
    }

    // ─────────────────────────────────────────────
    // 전체 레이어 적용 (UNDO의 핵심)
    // ─────────────────────────────────────────────
    private fun applyAllLayers() {

        result = original.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(result)

        for (l in layers) applyLayer(c, l)

        imageView.setImageBitmap(result)
    }

    // ─────────────────────────────────────────────
    // 레이어 적용 (모자이크 / 블러 / 지우기)
    // ─────────────────────────────────────────────
    private fun applyLayer(canvas: Canvas, layer: Layer) {

        when (layer.mode) {

            // -------------------------
            // ⭐ 모자이크 = block 단위
            // -------------------------
            Mode.MOSAIC -> {
                val block = layer.size

                for (y in 0 until original.height step block) {
                    for (x in 0 until original.width step block) {

                        if (layer.mask.getPixel(x, y) != 0) {

                            val col = original.getPixel(x, y)

                            canvas.drawRect(
                                x.toFloat(), y.toFloat(),
                                (x + block).toFloat(), (y + block).toFloat(),
                                Paint().apply { color = col }
                            )
                        }
                    }
                }
            }

            // -------------------------
            // ⭐ 블러 = "block 단위 부분 블러"
            // -------------------------
            Mode.BLUR -> {

                val size = layer.size
                val half = size / 2

                // block 단위 반복
                for (y in 0 until original.height step size) {
                    for (x in 0 until original.width step size) {

                        if (layer.mask.getPixel(x, y) != 0) {

                            val left = max(x - half, 0)
                            val top = max(y - half, 0)
                            val right = min(x + half, original.width - 1)
                            val bottom = min(y + half, original.height - 1)

                            val w = max(right - left, 1)
                            val h = max(bottom - top, 1)

                            val region = Bitmap.createBitmap(original, left, top, w, h)

                            val small = Bitmap.createScaledBitmap(
                                region,
                                max(w / 5, 1),
                                max(h / 5, 1),
                                true
                            )

                            val blur = Bitmap.createScaledBitmap(small, w, h, true)

                            canvas.drawBitmap(blur, left.toFloat(), top.toFloat(), null)
                        }
                    }
                }
            }

            // -------------------------
            // 원본 복구
            // -------------------------
            Mode.ERASE -> {
                canvas.drawBitmap(original, 0f, 0f, null)
            }
        }
    }

    // ─────────────────────────────────────────────
    // 저장
    // ─────────────────────────────────────────────
    private fun saveImage(bitmap: Bitmap) {
        val filename = "mosaic_${System.currentTimeMillis()}.jpg"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MosaicApp")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv
            )
            val fos: OutputStream? = uri?.let { contentResolver.openOutputStream(it) }

            fos?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }

            cv.clear()
            cv.put(MediaStore.Images.Media.IS_PENDING, 0)
            uri?.let { contentResolver.update(it, cv, null, null) }

        } else {

            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "MosaicApp"
            )
            dir.mkdirs()

            val file = File(dir, filename)
            val fos = FileOutputStream(file)

            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
            fos.close()

            MediaScannerConnection.scanFile(this,
                arrayOf(file.absolutePath),
                arrayOf("image/jpeg"), null
            )
        }
    }
}