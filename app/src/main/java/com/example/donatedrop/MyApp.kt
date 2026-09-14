package com.example.donatedrop

import android.app.Application
import com.cloudinary.android.MediaManager

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val config = HashMap<String, String>().apply {
            put("cloud_name", "dpvst39rd")
            put("api_key", "269982389636177") // optional for unsigned; do NOT include api_secret
        }
        MediaManager.init(this, config)
    }
}
