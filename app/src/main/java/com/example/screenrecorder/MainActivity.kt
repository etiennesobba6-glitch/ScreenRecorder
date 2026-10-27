package com.example.screenrecorder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var micBox: CheckBox
    private lateinit var floatBox: CheckBox
    private lateinit var qualitySpinner: Spinner
    private lateinit var fpsSpinner: Spinner
    private lateinit var weightSpinner: Spinner

    private val qualityLabels = arrayOf("Ecran natif", "1080p", "720p", "480p")
    private val qualityValues = intArrayOf(0, 1080, 720, 480)
    private val fpsValues = intArrayOf(30, 24, 15, 60)
    private val weightLabels = arrayOf(
        "Ultra leger (~4 Mo/min)",
        "Tres leger (~6 Mo/min)",
        "Leger (~10 Mo/min)",
        "Equilibre (~18 Mo/min)",
        "Haute qualite (~30 Mo/min)"
    )

    private val screenCapture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val useMic = micBox.isChecked &&
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                val intent = Intent(this, RecorderService::class.java).apply {
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", result.data)
                    putExtra("mic", useMic)
                    putExtra("quality", qualityValues[qualitySpinner.selectedItemPosition])
                    putExtra("fps", fpsValues[fpsSpinner.selectedItemPosition])
                    putExtra("weight", weightSpinner.selectedItemPosition)
                    putExtra("floating", floatBox.isChecked)
                }
                startForegroundService(intent)
                Toast.makeText(this, "Enregistrement demarre", Toast.LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).postDelayed({ finishAndRemoveTask() }, 700)
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
            textSize = 18f
        }
        micBox = CheckBox(this).apply {
            text = "Enregistrer le micro"
            isChecked = true
        }
        floatBox = CheckBox(this).apply {
            text = "Bouton flottant (pause / stop)"
            isChecked = true
        }
        val qualityTitle = TextView(this).apply { text = "Resolution" }
        qualitySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item, qualityLabels)
            setSelection(2)
        }
        val fpsTitle = TextView(this).apply { text = "Images par seconde" }
        fpsSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                fpsValues.map { "$it fps" })
        }
        val weightTitle = TextView(this).apply {
            text = "Poids de la video (estimation en 720p, 30 fps)"
        }
        weightSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item, weightLabels)
            setSelection(2)
        }
        val startBtn = Button(this).apply { text = "Demarrer" }
        val stopBtn = Button(this).apply { text = "Arreter" }

        layout.addView(status)
        layout.addView(micBox)
        layout.addView(floatBox)
        layout.addView(qualityTitle)
        layout.addView(qualitySpinner)
        layout.addView(fpsTitle)
        layout.addView(fpsSpinner)
        layout.addView(weightTitle)
        layout.addView(weightSpinner)
        layout.addView(startBtn)
        layout.addView(stopBtn)
        setContentView(ScrollView(this).apply { addView(layout) })

        startBtn.setOnClickListener {
            if (floatBox.isChecked && !Settings.canDrawOverlays(this)) {
                status.text = "Active 'Afficher par-dessus les autres applis' pour Super Screen Recorder, puis reviens ici et appuie sur Demarrer."
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + packageName)))
                return@setOnClickListener
            }
            val needed = mutableListOf<String>()
            if (micBox.isChecked) needed.add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
            if (needed.isEmpty()) askScreenCapture() else permissions.launch(needed.toTypedArray())
        }

        stopBtn.setOnClickListener {
            stopService(Intent(this, RecorderService::class.java))
            status.text = "Video enregistree dans Films/ScreenRecorder"
        }
    }
}
