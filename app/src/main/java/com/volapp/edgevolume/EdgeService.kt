package com.volapp.edgevolume

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Thin edge strips act as triggers only: dragging inward from the edge reveals a volume
 * panel (created on demand, removed when idle to keep RAM low). The volume is then changed
 * by dragging vertically, either continuing the same gesture or directly on the panel.
 */
class EdgeService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var audio: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val strips = mutableListOf<View>()
    private var panel: PanelView? = null
    private val hidePanel = Runnable { removePanel() }

    private val maxVol get() = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private val curVol get() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())
        rebuildStrips()
        return START_STICKY
    }

    override fun onDestroy() {
        removePanel()
        removeStrips()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun setVolume(v: Int) {
        val t = v.coerceIn(0, maxVol)
        if (t != curVol) audio.setStreamVolume(AudioManager.STREAM_MUSIC, t, 0)
        panel?.setLevel(t.toFloat() / maxVol)
        scheduleHide()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hidePanel)
        handler.postDelayed(hidePanel, 2000)
    }

    // ---- panel (shown only on demand) ----

    private class PanelView(ctx: Context, val onTouchLevel: (Float) -> Unit) : View(ctx) {
        private var level = 0f
        private val d = resources.displayMetrics.density
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(215, 30, 30, 30) }
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 255, 255, 255) }
        private val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 170, 255) }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 16 * d
            textAlign = Paint.Align.CENTER
        }
        private val r = RectF()
        private val top get() = 16 * d
        private val bottom get() = height - 48 * d

        fun setLevel(l: Float) {
            level = l
            invalidate()
        }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            r.set(0f, 0f, w, height.toFloat())
            c.drawRoundRect(r, 18 * d, 18 * d, bg)
            r.set(w / 2 - 9 * d, top, w / 2 + 9 * d, bottom)
            c.drawRoundRect(r, 9 * d, 9 * d, track)
            r.top = bottom - (bottom - top) * level
            c.drawRoundRect(r, 9 * d, 9 * d, fg)
            c.drawText("${(level * 100).toInt()}%", w / 2, height - 16 * d, txt)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE) {
                onTouchLevel(((bottom - e.y) / (bottom - top)).coerceIn(0f, 1f))
            }
            return true
        }
    }

    private fun showPanel(side: Int, p: Prefs) {
        val d = resources.displayMetrics
        if (panel == null) {
            val v = PanelView(this) { setVolume(Math.round(it * maxVol)) }
            val lp = WindowManager.LayoutParams(
                (72 * d.density).toInt(),
                d.heightPixels * p.heightPct / 100,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = side or Gravity.CENTER_VERTICAL
            lp.x = (4 * d.density).toInt()
            wm.addView(v, lp)
            panel = v
        }
        panel?.setLevel(curVol.toFloat() / maxVol)
        scheduleHide()
    }

    private fun removePanel() {
        handler.removeCallbacks(hidePanel)
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
    }

    // ---- edge triggers ----

    private fun removeStrips() {
        strips.forEach { runCatching { wm.removeView(it) } }
        strips.clear()
    }

    private fun rebuildStrips() {
        removePanel()
        removeStrips()
        val p = Prefs(this)
        if (p.left) addStrip(p, Gravity.START)
        if (p.right) addStrip(p, Gravity.END)
    }

    private fun addStrip(p: Prefs, side: Int) {
        val dm = resources.displayMetrics
        val view = View(this)
        view.setBackgroundColor(if (p.showStrip) Color.argb(70, 0, 150, 255) else Color.TRANSPARENT)

        val stripH = dm.heightPixels * p.heightPct / 100
        val params = WindowManager.LayoutParams(
            (p.widthDp * dm.density).toInt(),
            stripH,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = side or Gravity.CENTER_VERTICAL

        val revealDist = 16 * dm.density
        val travel = stripH * p.sensitivityPct / 100f
        var startX = 0f
        var startY = 0f
        var startVol = 0f
        var revealed = false
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.rawX
                    startY = e.rawY
                    revealed = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!revealed) {
                        val inward = if (side == Gravity.START) e.rawX - startX else startX - e.rawX
                        if (inward > revealDist) {
                            revealed = true
                            startY = e.rawY
                            startVol = curVol.toFloat()
                            showPanel(side, p)
                        }
                    } else {
                        setVolume((startVol + (startY - e.rawY) / travel * maxVol).toInt())
                    }
                }
            }
            true
        }
        wm.addView(view, params)
        strips += view
    }

    private fun buildNotification(): Notification {
        val chId = "edge_volume"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(chId, "Edge Volume", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, chId)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("Edge Volume đang chạy")
            .setContentText("Kéo từ cạnh màn hình vào để chỉnh âm lượng")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        fun start(ctx: Context) {
            val i = Intent(ctx, EdgeService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, EdgeService::class.java))
        }
    }
}
