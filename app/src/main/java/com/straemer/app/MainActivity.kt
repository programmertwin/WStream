package com.straemer.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import com.straemer.app.R

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnEnable = findViewById<Button>(R.id.btnEnable)
        val btnStart = findViewById<Button>(R.id.btnStartServer)
        val btnShow  = findViewById<Button>(R.id.btnShowXml)
        val tv       = findViewById<TextView>(R.id.tv)

        btnEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnStart.setOnClickListener {
            startService(Intent(this, HttpForegroundService::class.java))
        }
        btnShow.setOnClickListener {
            tv.text = UiDumpAccessibilityService.lastXml.get()
        }
    }
}
