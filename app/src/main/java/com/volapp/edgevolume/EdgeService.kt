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
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
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
        private var shown = 0f // animated level
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 24, 26, 34) }
        private val fg = Paint(Paint.ANTI_ALIAS_FLAG)
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255)
            strokeWidth = 2 * d
        }
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * d
            color = Color.argb(60, 255, 255, 255)
        }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15 * d
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 2.2f * d
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        private val iconFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val r = RectF()
        private val clip = Path()
        private val spk = Path()
        private val top get() = 0f
        private val bottom get() = height.toFloat()

        fun setLevel(l: Float) {
            level = l
            postInvalidateOnAnimation()
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            fg.shader = LinearGradient(
                0f, h.toFloat(), 0f, 0f,
                Color.rgb(0, 122, 255), Color.rgb(90, 220, 255), Shader.TileMode.CLAMP
            )
            r.set(0f, 0f, w.toFloat(), h.toFloat())
            clip.reset()
            clip.addRoundRect(r, w / 2f, w / 2f, Path.Direction.CW)
        }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            shown += (level - shown) * 0.35f
            val animating = Math.abs(level - shown) > 0.002f
            if (!animating) shown = level

            c.save()
            c.clipPath(clip)
            c.drawRect(0f, 0f, w, h, bg)
            val fy = h - h * shown
            c.drawRect(0f, fy, w, h, fg)
            if (shown > 0.01f && shown < 0.99f) c.drawLine(0f, fy, w, fy, edge)
            c.restore()
            c.drawRoundRect(0.75f * d, 0.75f * d, w - 0.75f * d, h - 0.75f * d, w / 2f, w / 2f, border)

            c.drawText("${Math.round(shown * 100)}", w / 2, 40 * d, txt)

            // speaker icon near the bottom
            val cx = w / 2 - 4 * d
            val cy = h - 40 * d
            spk.reset()
            spk.moveTo(cx - 9 * d, cy - 4 * d)
            spk.lineTo(cx - 4 * d, cy - 4 * d)
            spk.lineTo(cx + 2 * d, cy - 10 * d)
            spk.lineTo(cx + 2 * d, cy + 10 * d)
            spk.lineTo(cx - 4 * d, cy + 4 * d)
            spk.lineTo(cx - 9 * d, cy + 4 * d)
            spk.close()
            c.drawPath(spk, iconFill)
            val waves = when {
                shown < 0.01f -> 0
                shown < 0.34f -> 1
                shown < 0.67f -> 2
                else -> 3
            }
            for (i in 1..waves) {
                val rad = (4 + i * 4) * d
                r.set(cx + 2 * d - rad + 4 * d, cy - rad, cx + 2 * d + rad + 4 * d, cy + rad)
                c.drawArc(r, -45f, 90f, false, icon)
            }
            if (waves == 0) {
                c.drawLine(cx + 7 * d, cy - 5 * d, cx + 15 * d, cy + 5 * d, icon)
                c.drawLine(cx + 15 * d, cy - 5 * d, cx + 7 * d, cy + 5 * d, icon)
            }
            if (animating) postInvalidateOnAnimation()
        }

        private var downY = 0f
        private var downLevel = 0f

        // Relative drag: touching does not jump to that position; moving adjusts from the current level.
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = e.y
                    downLevel = level
                }
                MotionEvent.ACTION_MOVE ->
                    onTouchLevel((downLevel + (downY - e.y) / (bottom - top)).coerceIn(0f, 1f))
            }
            return true
        }
    }

    private fun showPanel(side: Int, p: Prefs) {
        val d = resources.displayMetrics
        val panelH = d.heightPixels * p.pillHeightPct / 100
        if (panel == null) {
            val v = PanelView(this) { setVolume(Math.round(it * maxVol)) }
            val lp = WindowManager.LayoutParams(
                (p.pillWidthDp * d.density).toInt(),
                panelH,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = side or Gravity.TOP
            lp.x = (4 * d.density).toInt()
            lp.y = (d.heightPixels - panelH) * p.pillPosPct / 100
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
