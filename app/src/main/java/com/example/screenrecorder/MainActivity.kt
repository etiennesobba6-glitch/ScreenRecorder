package com.example.screenrecorder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private var recording = false

    private val screenCapture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val intent = Intent(this, RecorderService::class.java).apply {
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", result.data)
                }
                startForegroundService(intent)
                recording = true
                status.text = "Enregistrement en cours..."
            } else {
                status.text = "Permission refusee"
            }
        }

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            askScreenCapture()
        }

    private fun askScreenCapture() {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCapture.launch(mpm.createScreenCaptureIntent())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        status = TextView(this).apply {
            text = "Pret a enregistrer"
            textSize = 20f
        }
        val startBtn = Button(this).apply { text = "Demarrer" }
        val stopBtn = Button(this).apply { text = "Arreter" }
        layout.addView(status)
        layout.addView(startBtn)
        layout.addView(stopBtn)
        setContentView(layout)

        startBtn.setOnClickListener {
            if (recording) return@setOnClickListener
            val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.launch(needed.toTypedArray())
        }

        stopBtn.setOnClickListener {
            stopService(Intent(this, RecorderService::class.java))
            recording = false
            status.text = "Video enregistree dans Films/ScreenRecorder"
        }
    }
}
