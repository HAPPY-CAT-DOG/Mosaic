package com.example.mosaic

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class HomeActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var mosaicBtn: Button
    private lateinit var backgdBtn: Button

    private var selectedImageUri: Uri? = null

    companion object {
        private const val REQUEST_GALLERY = 1001

        // 🔥 Map 기능 비활성화 — 사용 X
         const val REQUEST_MAP = 2001

        private lateinit var originalBitmap: android.graphics.Bitmap

        fun setOriginalBitmap(bitmap: android.graphics.Bitmap) {
            originalBitmap = bitmap
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // UI 연결
        imageView = findViewById(R.id.image_preview)
        mosaicBtn = findViewById(R.id.btn_mosaic)
        backgdBtn = findViewById(R.id.btn_backgd)

        // 갤러리 선택
        imageView.setOnClickListener {
            checkPermissionAndOpenGallery()
        }

        // 모자이크 기능 이동
        mosaicBtn.setOnClickListener {
            selectedImageUri?.let { uri ->
                val intent = Intent(this, MosaicActivity::class.java)
                intent.putExtra("imageUri", uri.toString())
                startActivity(intent)
            } ?: Toast.makeText(this, "먼저 사진을 선택하세요.", Toast.LENGTH_SHORT).show()
        }

        // 배경 삽입 기능 이동 (지도 아님!)
        backgdBtn.setOnClickListener {
            selectedImageUri?.let { uri ->
                val intent = Intent(this, BackgdActivity::class.java)
                intent.putExtra("imageUri", uri.toString())
                startActivity(intent)
            } ?: Toast.makeText(this, "먼저 사진을 선택하세요.", Toast.LENGTH_SHORT).show()
        }

        // 🔥 지도 버튼이 있었으면 여기서 삭제됨
        // (MapActivity 호출 코드 없음)
    }

    // 권한 처리
    private fun checkPermissionAndOpenGallery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(permission),
                REQUEST_GALLERY
            )
        } else {
            openGallery()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_GALLERY)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {

            REQUEST_GALLERY -> {
                val uri = data?.data ?: return
                selectedImageUri = uri

                try {
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    imageView.setImageBitmap(bitmap)
                    setOriginalBitmap(bitmap)

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "이미지 로딩 실패", Toast.LENGTH_SHORT).show()
                }
            }

             REQUEST_MAP -> {
                 val mapUriString = data?.getStringExtra("mapImageUri")
                 val mapUri = mapUriString?.let { Uri.parse(it) }

                 if (mapUri != null) {
                     try {
                         val mapBitmap =
                             MediaStore.Images.Media.getBitmap(contentResolver, mapUri)
                         imageView.setImageBitmap(mapBitmap)
                         setOriginalBitmap(mapBitmap)

                     } catch (e: Exception) {
                         e.printStackTrace()
                         Toast.makeText(this, "지도 이미지 로딩 실패", Toast.LENGTH_SHORT).show()
                     }
                 }
             }
        }
    }
}