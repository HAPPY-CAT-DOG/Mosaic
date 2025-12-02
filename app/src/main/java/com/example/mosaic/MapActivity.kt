package com.example.mosaic

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.StreetViewPanorama
import com.google.android.gms.maps.StreetViewPanoramaView
import com.google.android.gms.maps.model.LatLng

class MapActivity : AppCompatActivity() {

    private lateinit var panoramaView: StreetViewPanoramaView
    private var panorama: StreetViewPanorama? = null
    private var isPanoramaReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        panoramaView = findViewById(R.id.mapView)
        panoramaView.onCreate(savedInstanceState)

        val latLng = LatLng(37.5665, 126.9780) // 서울 시청

        panoramaView.getStreetViewPanoramaAsync { p ->
            panorama = p
            p.setPosition(latLng)
            isPanoramaReady = true
        }

        findViewById<Button>(R.id.btn_use_map).setOnClickListener {
            if (isPanoramaReady) {
                capturePanoramaBitmap()
            } else {
                Toast.makeText(this, "스트리트뷰 준비 중입니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun capturePanoramaBitmap() {
        panoramaView.isDrawingCacheEnabled = true
        panoramaView.buildDrawingCache()
        val bitmap = Bitmap.createBitmap(panoramaView.drawingCache)
        panoramaView.isDrawingCacheEnabled = false

        val uri = UriHelper.saveBitmapAndGetUri(this, bitmap)
        val resultIntent = Intent()
        resultIntent.putExtra("mapImageUri", uri.toString())
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        panoramaView.onResume()
    }

    override fun onPause() {
        panoramaView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        panoramaView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        panoramaView.onLowMemory()
    }
}