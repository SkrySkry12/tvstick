package com.mistick.launcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mistick.launcher.MainActivity

/** Launches MiStick Launcher immediately after the device finishes booting. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            try {
                context.startActivity(launchIntent)
            } catch (_: Exception) {
                // Background activity start may be restricted; HOME intent still applies.
            }
        }
    }
}
