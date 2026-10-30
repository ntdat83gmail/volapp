package com.volapp.edgevolume

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var toggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(TextView(this).apply {
            text = "Edge Volume\nKéo từ cạnh màn hình vào trong để hiện thanh âm lượng, rồi kéo lên/xuống để chỉnh."
            textSize = 18f
        })

        toggle = Button(this).apply { setOnClickListener { onToggle() } }
        root.addView(toggle)

        root.addView(check("Cạnh trái", prefs.left) { prefs.left = it; refresh() })
        root.addView(check("Cạnh phải", prefs.right) { prefs.right = it; refresh() })
        root.addView(check("Hiện dải cảm ứng (để căn chỉnh)", prefs.showStrip) { prefs.showStrip = it; refresh() })

        root.addView(slider("Độ rộng dải (dp)", 10, 80, prefs.widthDp) { prefs.widthDp = it })
        root.addView(slider("Chiều cao dải (% màn hình)", 20, 100, prefs.heightPct) { prefs.heightPct = it })
        root.addView(slider("Độ rộng viên thuốc (dp)", 40, 160, prefs.pillWidthDp) { prefs.pillWidthDp = it })
        root.addView(slider("Chiều cao viên thuốc (% màn hình)", 30, 100, prefs.pillHeightPct) { prefs.pillHeightPct = it })
        root.addView(slider("Vị trí viên thuốc (0 = trên, 100 = dưới)", 0, 100, prefs.pillPosPct) { prefs.pillPosPct = it })
        root.addView(slider("Quãng vuốt cho full âm lượng (%)", 20, 100, prefs.sensitivityPct) { prefs.sensitivityPct = it })
    }

    // Close the settings screen when it leaves the foreground so its views are freed;
    // only the lightweight edge service stays resident.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }

    override fun onResume() {
        super.onResume()
        // The service may have been killed (app update, low memory); restart it if it should be on.
        if (prefs.enabled && canOverlay()) EdgeService.start(this)
        updateButton()
    }

    private fun canOverlay() =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun onToggle() {
        if (prefs.enabled) {
            prefs.enabled = false
            EdgeService.stop(this)
        } else if (!canOverlay()) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        } else {
            prefs.enabled = true
            EdgeService.start(this)
        }
        updateButton()
    }

    /** Restart the service so strips pick up the new settings. */
    private fun refresh() {
        if (prefs.enabled) EdgeService.start(this)
    }

    private fun updateButton() {
        toggle.text = when {
            prefs.enabled -> "Tắt"
            !canOverlay() -> "Cấp quyền hiển thị trên ứng dụng khác"
            else -> "Bật"
        }
    }

    private fun check(label: String, init: Boolean, onChange: (Boolean) -> Unit) =
        CheckBox(this).apply {
            text = label
            isChecked = init
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }

    private fun slider(label: String, min: Int, max: Int, init: Int, onChange: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tv = TextView(this).apply { text = "$label: $init" }
        val sb = SeekBar(this).apply {
            this.max = max - min
            progress = init - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, user: Boolean) {
                    tv.text = "$label: ${p + min}"
                }

                override fun onStartTrackingTouch(s: SeekBar) {}

                override fun onStopTrackingTouch(s: SeekBar) {
                    onChange(s.progress + min)
                    refresh()
                }
            })
        }
        box.addView(tv)
        box.addView(sb)
        return box
    }
}
