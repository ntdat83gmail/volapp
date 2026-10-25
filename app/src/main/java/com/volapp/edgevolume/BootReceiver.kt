package com.volapp.edgevolume

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs(context).enabled) return
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) return
        EdgeService.start(context)
    }
}
