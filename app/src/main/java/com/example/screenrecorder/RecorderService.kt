package com.example.screenrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class RecorderService : Service() {

    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var display: VirtualDisplay? = null
    private var videoUri: Uri? = null
    private var paused = false

    private var wm: WindowManager? = null
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var posX = 0
    private var posY = 0

    private val factors = floatArrayOf(0.015f, 0.025f, 0.04f, 0.07f, 0.12f)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> { stopSelf(); return START_NOT_STICKY }
            "TOGGLE" -> { setPaused(!paused); return START_NOT_STICKY }
        }
        if (recorder != null) return START_NOT_STICKY

        startAsForeground()

        val code = intent?.getIntExtra("resultCode", 0) ?: 0
        val mic = intent?.getBooleanExtra("mic", true) ?: true
        val quality = intent?.getIntExtra("quality", 0) ?: 0
        val fps = intent?.getIntExtra("fps", 30) ?: 30
        val weight = intent?.getIntExtra("weight", 2) ?: 2
        val floating = intent?.getBooleanExtra("floating", true) ?: true
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra("data", Intent::class.java)
        else
            @Suppress("DEPRECATION") intent?.getParcelableExtra("data")

        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, null)

        try {
            startRecording(mic, quality, fps, weight)
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur: " + e.message, Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        if (floating) showFloating()
        return START_NOT_STICKY
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildNotification(): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val toggleIntent = Intent(this, RecorderService::class.java).apply { action = "TOGGLE" }
        val stopIntent = Intent(this, RecorderService::class.java).apply { action = "STOP" }
        val togglePi = PendingIntent.getService(this, 1, toggleIntent, flags)
        val stopPi = PendingIntent.getService(this, 2, stopIntent, flags)
        val toggleAction = Notification.Action.Builder(
            Icon.createWithResource(this,
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause),
            if (paused) "REPRENDRE" else "PAUSE", togglePi
        ).build()
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "ARRETER", stopPi
        ).build()
        return Notification.Builder(this, "recorder")
            .setContentTitle("Super Screen Recorder")
            .setContentText(if (paused) "En pause" else "Enregistrement en cours")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(toggleAction)
            .addAction(stopAction)
            .build()
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("recorder", "Enregistrement", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
    }

    private fun setPaused(p: Boolean) {
        val r = recorder ?: return
        try {
            if (p) r.pause() else r.resume()
        } catch (e: Exception) {
            return
        }
        paused = p
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1, buildNotification())
        updateBubble()
    }

    private fun overlayParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = posX
            y = posY
        }
    }

    private fun detach(v: View?) {
        if (v == null) return
        try { wm?.removeView(v) } catch (e: Exception) { }
    }

    private fun updateBubble() {
        val b = bubble ?: return
        b.text = if (paused) "II" else "REC"
        b.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (paused) Color.parseColor("#FF9800") else Color.RED)
        }
    }

    private fun showFloating() {
        if (!Settings.canDrawOverlays(this)) return
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        posX = dp(8)
        posY = dp(180)

        val b = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            alpha = 0.75f
            setWidth(dp(48))
            setHeight(dp(48))
        }
        bubble = b
        updateBubble()

        val params = overlayParams()
        bubbleParams = params

        b.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                val p = bubbleParams ?: return false
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = p.x
                        startY = p.y
                        touchX = e.rawX
                        touchY = e.rawY
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - touchX).toInt()
                        val dy = (e.rawY - touchY).toInt()
                        if (abs(dx) > 12 || abs(dy) > 12) moved = true
                        if (moved) {
                            p.x = startX + dx
                            p.y = startY + dy
                            try { wm?.updateViewLayout(v, p) } catch (ex: Exception) { }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) openPanel()
                    }
                }
                return true
            }
        })

        try { wm?.addView(b, params) } catch (e: Exception) { }
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setMinWidth(0)
            setMinimumWidth(0)
            setOnClickListener { action() }
        }
    }

    private fun openPanel() {
        val p = bubbleParams ?: return
        posX = p.x
        posY = p.y
        detach(bubble)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.parseColor("#E6202020"))
            }
        }
        box.addView(makeButton(if (paused) "Reprendre" else "Pause") {
            closePanel()
            setPaused(!paused)
        })
        box.addView(makeButton("Stop") { stopSelf() })
        box.addView(makeButton("X") { closePanel() })
        panel = box
        try { wm?.addView(box, overlayParams()) } catch (e: Exception) { }
    }

    private fun closePanel() {
        detach(panel)
        panel = null
        val b = bubble ?: return
        updateBubble()
        val p = overlayParams()
        bubbleParams = p
        try { wm?.addView(b, p) } catch (e: Exception) { }
    }

    private fun startRecording(mic: Boolean, quality: Int, fps: Int, weight: Int) {
        val metrics = resources.displayMetrics
        var w = metrics.widthPixels
        var h = metrics.heightPixels
        if (quality > 0) {
            val shortSide = minOf(w, h)
            if (shortSide > quality) {
                val s = quality.toFloat() / shortSide
                w = (w * s).toInt()
                h = (h * s).toInt()
            }
        }
        val width = w / 2 * 2
        val height = h / 2 * 2
        val factor = factors[weight.coerceIn(0, factors.size - 1)]
        val bitrate = (width.toLong() * height * fps * factor).toInt().coerceIn(250_000, 20_000_000)

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "rec_" + System.currentTimeMillis() + ".mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ScreenRecorder")
        }
        videoUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        val pfd = contentResolver.openFileDescriptor(videoUri!!, "w")!!

        recorder = MediaRecorder().apply {
            if (mic) setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (mic) {
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(if (weight <= 1) 64_000 else 128_000)
                setAudioSamplingRate(44100)
            }
            setVideoSize(width, height)
            setVideoFrameRate(fps)
            setVideoEncodingBitRate(bitrate)
            setOutputFile(pfd.fileDescriptor)
            prepare()
        }

        display = projection?.createVirtualDisplay(
            "ScreenRecorder", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder?.surface, null, null
        )
        recorder?.start()
    }

    override fun onDestroy() {
        detach(bubble)
        detach(panel)
        val wasRecording = recorder != null
        try { recorder?.stop() } catch (e: Exception) { }
        recorder?.release()
        recorder = null
        display?.release()
        projection?.stop()
        if (wasRecording) {
            Toast.makeText(this, "Video enregistree dans Films/ScreenRecorder", Toast.LENGTH_LONG).show()
        }
        super.onDestroy()
    }
}
