package com.mistick.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

/** Loads launchable apps from PackageManager. All heavy work runs off the main thread. */
object AppRepository {

    suspend fun loadApps(context: Context): List<AppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val ownPackage = context.packageName
        val result = LinkedHashMap<String, AppItem>()

        // TV apps first (they provide banners), then regular launcher apps as fallback.
        val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        for (category in categories) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in queryActivities(pm, query)) {
                val pkg = info.activityInfo.packageName
                if (pkg == ownPackage || pkg.startsWith("$ownPackage.") || result.containsKey(pkg)) continue
                val item = runCatching { toAppItem(pm, info, category) }.getOrNull() ?: continue
                result[pkg] = item
            }
        }

        val collator = Collator.getInstance()
        result.values.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun queryActivities(pm: PackageManager, intent: Intent): List<ResolveInfo> =
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

    private fun toAppItem(pm: PackageManager, info: ResolveInfo, category: String): AppItem {
        val activity = info.activityInfo
        val appInfo = activity.applicationInfo
        val isSystem = appInfo.flags and
            (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

        val launch = Intent(Intent.ACTION_MAIN)
            .addCategory(category)
            .setClassName(activity.packageName, activity.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        return AppItem(
            label = info.loadLabel(pm).toString().trim(),
            packageName = activity.packageName,
            icon = info.loadIcon(pm),
            banner = activity.loadBanner(pm) ?: appInfo.loadBanner(pm),
            launchIntent = launch,
            isSystem = isSystem,
            isTvApp = category == Intent.CATEGORY_LEANBACK_LAUNCHER,
        )
    }
}
