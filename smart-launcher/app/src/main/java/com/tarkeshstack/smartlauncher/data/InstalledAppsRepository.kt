package com.tarkeshstack.smartlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.tarkeshstack.smartlauncher.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the list of launchable apps from PackageManager. */
class InstalledAppsRepository(private val context: Context) {

    suspend fun loadLaunchableApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)

        resolved
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { resolveInfo ->
                AppInfo(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(pm).toString(),
                    icon = resolveInfo.loadIcon(pm),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun launchIntentFor(packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)
}
