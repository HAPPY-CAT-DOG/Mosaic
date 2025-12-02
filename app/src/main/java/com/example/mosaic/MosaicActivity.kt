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
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.*

class MosaicActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private lateinit var originalBitmap: Bitmap
    private lateinit var editedBitmap: Bitmap
    private val historyStack = Stack<Bitmap>()
    private var currentMode: Mode = Mode.MOSAIC
    private var blockSize = 30

    enum class Mode {
        MOSAIC, BLUR, ERASE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mosaic)

        imageView = findViewById(R.id.imageView)
        val uri = Uri.parse(intent.getStringExtra("imageUri"))
        originalBitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
        editedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        imageView.setImageBitmap(editedBitmap)

        findViewById<Button>(R.id.btn_mosaic).setOnClickListener {
            currentMode = Mode.MOSAIC
        }

        findViewById<Button>(R.id.btn_blur).setOnClickListener {
            currentMode = Mode.BLUR
        }

        findViewById<Button>(R.id.btn_ai_remove).setOnClickListener {
            Toast.makeText(this, "AI 제거 기능은 아직 구현되지 않았습니다.", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_undo).setOnClickListener {
            if (historyStack.isNotEmpty()) {
                editedBitmap = historyStack.pop()
                imageView.setImageBitmap(editedBitmap)
            }
        }

        findViewById<Button>(R.id.btn_erase).setOnClickListener {
            currentMode = Mode.ERASE
        }

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            saveImageToGallery(editedBitmap)
            Toast.makeText(this, "갤러리에 저장되었습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            finish()
        }

        findViewById<SeekBar>(R.id.seekbar_strength).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                blockSize = progress.coerceAtLeast(5)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        imageView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                val imageMatrix = imageView.imageMatrix
                val drawable = imageView.drawable ?: return@setOnTouchListener true

                val values = FloatArray(9)
                imageMatrix.getValues(values)
                val scaleX = values[Matrix.MSCALE_X]
                val scaleY = values[Matrix.MSCALE_Y]
                val transX = values[Matrix.MTRANS_X]
                val transY = values[Matrix.MTRANS_Y]

                val touchX = ((event.x - transX) / scaleX).toInt()
                val touchY = ((event.y - transY) / scaleY).toInt()

                if (touchX in 0 until editedBitmap.width && touchY in 0 until editedBitmap.height) {
                    historyStack.push(editedBitmap.copy(Bitmap.Config.ARGB_8888, true))
                    when (currentMode) {
                        Mode.MOSAIC -> applyMosaic(touchX, touchY)
                        Mode.BLUR -> applyBlur(touchX, touchY)
                        Mode.ERASE -> restoreOriginal(touchX, touchY)
                    }
                    imageView.setImageBitmap(editedBitmap)
                }
            }
            true
        }
    }

    private fun applyMosaic(cx: Int, cy: Int) {
        val canvas = Canvas(editedBitmap)
        val paint = Paint()
        for (y in cy - blockSize until cy + blockSize step blockSize) {
            for (x in cx - blockSize until cx + blockSize step blockSize) {
                if (x in 0 until editedBitmap.width && y in 0 until editedBitmap.height) {
                    val color = editedBitmap.getPixel(x, y)
                    paint.color = color
                    canvas.drawRect(Rect(x, y, x + blockSize, y + blockSize), paint)
                }
            }
        }
    }

    private fun applyBlur(cx: Int, cy: Int) {
        val radius = blockSize / 2
        val paint = Paint()
        val srcRect = Rect(
            (cx - radius).coerceAtLeast(0),
            (cy - radius).coerceAtLeast(0),
            (cx + radius).coerceAtMost(editedBitmap.width - 1),
            (cy + radius).coerceAtMost(editedBitmap.height - 1)
        )
        val dstRect = srcRect
        val region = Bitmap.createBitmap(editedBitmap, srcRect.left, srcRect.top, srcRect.width(), srcRect.height())
        val blurred = Bitmap.createScaledBitmap(region, 1, 1, true)
        val restored = Bitmap.createScaledBitmap(blurred, region.width, region.height, true)
        val canvas = Canvas(editedBitmap)
        canvas.drawBitmap(restored, null, dstRect, paint)
    }

    private fun restoreOriginal(cx: Int, cy: Int) {
        val canvas = Canvas(editedBitmap)
        val paint = Paint()
        val srcRect = Rect(
            (cx - blockSize).coerceAtLeast(0),
            (cy - blockSize).coerceAtLeast(0),
            (cx + blockSize).coerceAtMost(originalBitmap.width - 1),
            (cy + blockSize).coerceAtMost(originalBitmap.height - 1)
        )
        canvas.drawBitmap(originalBitmap, srcRect, srcRect, paint)
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        val filename = "mosaic_${System.currentTimeMillis()}.jpg"
        val fos: OutputStream?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MosaicApp")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val contentResolver = contentResolver
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                fos = contentResolver.openOutputStream(uri)
                fos?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }
        } else {
            val imagesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MosaicApp")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val imageFile = File(imagesDir, filename)
            fos = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
            fos.close()
            MediaScannerConnection.scanFile(this, arrayOf(imageFile.absolutePath), arrayOf("image/jpeg"), null)
        }
    }
}
