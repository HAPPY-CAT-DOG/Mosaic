package com.example.mosaic

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class BackgdActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_MAP = 2001
        private lateinit var originalBitmap: Bitmap

        fun setOriginalBitmap(bitmap: Bitmap) {
            originalBitmap = bitmap
        }
    }

    private lateinit var imageView: ImageView
    private lateinit var templateBtn: Button
//    private lateinit var mapBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var cancelBtn: Button
    private var selectedImageUri: Uri? = null
    private var resultBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backgd)

        imageView = findViewById(R.id.imageView_backgd)
        templateBtn = findViewById(R.id.btn_template)
//        mapBtn = findViewById(R.id.btn_map)
        saveBtn = findViewById(R.id.btn_save)
        cancelBtn = findViewById(R.id.btn_cancel)

        selectedImageUri = intent.getStringExtra("imageUri")?.let { Uri.parse(it) }

        if (selectedImageUri != null) {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, selectedImageUri)
                imageView.setImageBitmap(bitmap)
                setOriginalBitmap(bitmap)
            } catch (e: Exception) {
                Toast.makeText(this, "이미지 로딩 실패", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            Toast.makeText(this, "이미지가 전달되지 않았습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }

        templateBtn.setOnClickListener {
            showTemplateSelectionDialog()
        }

//        mapBtn.setOnClickListener {
//            selectedImageUri?.let {
//                val intent = Intent(this, MapActivity::class.java)
//                intent.putExtra("imageUri", it.toString())
//                startActivityForResult(intent, REQUEST_MAP)
//            } ?: Toast.makeText(this, "이미지를 먼저 선택해주세요.", Toast.LENGTH_SHORT).show()
//        }

        saveBtn.setOnClickListener {
            Toast.makeText(this, "저장 기능은 아직 구현되지 않았습니다.", Toast.LENGTH_SHORT).show()
        }

        cancelBtn.setOnClickListener {
            finish()
        }
    }

    private fun showTemplateSelectionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_background_selection, null)
        val imgBeach = dialogView.findViewById<ImageView>(R.id.img_beach)
        val imgNature = dialogView.findViewById<ImageView>(R.id.img_nature)
        val imgSky = dialogView.findViewById<ImageView>(R.id.img_sky)

        val dialog = AlertDialog.Builder(this)
            .setTitle("배경 템플릿 선택")
            .setView(dialogView)
            .create()

        imgBeach.setOnClickListener {
            applyBackground(R.drawable.beach_background)
            dialog.dismiss()
        }

        imgNature.setOnClickListener {
            applyBackground(R.drawable.nature_background)
            dialog.dismiss()
        }

        imgSky.setOnClickListener {
            applyBackground(R.drawable.sky_background)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun applyBackground(backgroundResId: Int) {
        val backgroundBitmap = BitmapFactory.decodeResource(resources, backgroundResId)
        val mergedBitmap = mergeBitmaps(backgroundBitmap, originalBitmap)
        imageView.setImageBitmap(mergedBitmap)
        resultBitmap = mergedBitmap
        Toast.makeText(this, "배경이 적용되었습니다", Toast.LENGTH_SHORT).show()
    }

    private fun mergeBitmaps(foreground: Bitmap, background: Bitmap): Bitmap {
        val width = background.width
        val height = background.height
        val config = background.config ?: Bitmap.Config.ARGB_8888
        val result = Bitmap.createBitmap(width, height, config)

        val canvas = Canvas(result)
        canvas.drawBitmap(background, 0f, 0f, null)
        val scaledForeground = Bitmap.createScaledBitmap(foreground, width, height, true)
        canvas.drawBitmap(scaledForeground, 0f, 0f, null)

        return result
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_MAP && resultCode == Activity.RESULT_OK) {
            val mapImageUriString = data?.getStringExtra("mapImageUri")
            val mapImageUri = mapImageUriString?.let { Uri.parse(it) }
            if (mapImageUri != null) {
                try {
                    val mapBitmap = MediaStore.Images.Media.getBitmap(contentResolver, mapImageUri)

                    val merged = mergeBitmaps(mapBitmap, originalBitmap)
                    imageView.setImageBitmap(merged)

                    resultBitmap = merged
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "지도 이미지 로딩 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}