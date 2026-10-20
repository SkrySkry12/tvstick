package com.mistick.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity

/** Simple D-pad friendly settings screen for the launcher. */
class SettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val prefs = LauncherPrefs(this)

        bindSwitch(R.id.switch_show_system, prefs.showSystemInMyApps) { prefs.showSystemInMyApps = it }
        bindSwitch(R.id.switch_show_all, prefs.showAllAppsRow) { prefs.showAllAppsRow = it }
        bindSwitch(R.id.switch_banners, prefs.preferBanners) { prefs.preferBanners = it }

        findViewById<Button>(R.id.btn_default_home).setOnClickListener {
            openFirst(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        }
        findViewById<Button>(R.id.btn_system_settings).setOnClickListener {
            openFirst(Intent(Settings.ACTION_SETTINGS))
        }

        findViewById<TextView>(R.id.text_version).text =
            getString(R.string.settings_version, BuildConfigCompat.versionName(this))

        findViewById<CompoundButton>(R.id.switch_show_system).requestFocus()
    }

    private fun bindSwitch(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        findViewById<CompoundButton>(id).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
    }

    private fun openFirst(vararg intents: Intent) {
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        Toast.makeText(this, R.string.error_cannot_launch, Toast.LENGTH_SHORT).show()
    }
}

/** Reads versionName from PackageManager (avoids enabling the BuildConfig feature). */
private object BuildConfigCompat {
    fun versionName(activity: FragmentActivity): String =
        runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        }.getOrNull() ?: "?"
}
