package com.example.easy_atm_mapper

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val activityInfo = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)
        bundle = activityInfo.metaData

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val pb : View = findViewById(R.id.progressBar)
        pb.visibility = View.GONE

        val perms = arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.INTERNET)

        ActivityCompat.requestPermissions(this, perms, 1)

//        enableEdgeToEdge()
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
    }

    companion object {
        lateinit var bundle: Bundle
    }
}