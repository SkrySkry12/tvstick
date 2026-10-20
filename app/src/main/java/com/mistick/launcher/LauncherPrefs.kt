package com.mistick.launcher

import android.content.Context
import android.content.SharedPreferences

/** Tiny wrapper around the launcher's private SharedPreferences. */
class LauncherPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    /** Include pre-installed/system apps in the "My apps" row. */
    var showSystemInMyApps: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SYSTEM, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SYSTEM, value).apply()

    /** Show the "All apps" row. */
    var showAllAppsRow: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ALL, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_ALL, value).apply()

    /** Prefer 16:9 TV banners over square icons on cards. */
    var preferBanners: Boolean
        get() = prefs.getBoolean(KEY_BANNERS, true)
        set(value) = prefs.edit().putBoolean(KEY_BANNERS, value).apply()

    /** Most recently launched packages, newest first. */
    val recentPackages: List<String>
        get() = prefs.getString(KEY_RECENT, "").orEmpty().split(',').filter { it.isNotBlank() }

    fun markLaunched(pkg: String) {
        if (pkg.isBlank()) return
        val list = (listOf(pkg) + recentPackages.filter { it != pkg }).take(MAX_RECENT)
        prefs.edit().putString(KEY_RECENT, list.joinToString(",")).apply()
    }

    /** User-defined order of the "My apps" row (package names). */
    var appOrder: List<String>
        get() = prefs.getString(KEY_ORDER, "").orEmpty().split(',').filter { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_ORDER, value.joinToString(",")).apply()

    private companion object {
        const val KEY_ORDER = "app_order"
        const val KEY_RECENT = "recent_packages"
        const val MAX_RECENT = 24
        const val KEY_SHOW_SYSTEM = "show_system_in_my_apps"
        const val KEY_SHOW_ALL = "show_all_apps_row"
        const val KEY_BANNERS = "prefer_banners"
    }
}
