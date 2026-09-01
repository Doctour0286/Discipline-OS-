package com.disciplineos.app.applist

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable

/**
 * Backs the app picker in Mission Profile Setup (§2.8) — see that screen's kdoc for why this
 * replaces a free-text "type the package id" field. Queries only *launchable* apps (apps with
 * a `MAIN`/`LAUNCHER` activity), not every installed package, since a user picking "apps to
 * allow/block during a Mission" has no reason to see system services, content providers, or
 * other non-app packages that could never appear in front of them anyway.
 *
 * **No `QUERY_ALL_PACKAGES` permission required.** [PackageManager.queryIntentActivities]
 * against an explicit `ACTION_MAIN`/`CATEGORY_LAUNCHER` [Intent] is the standard, unrestricted
 * way to enumerate launchable apps on modern Android — the broader "query every installed
 * package" permission (which *does* require a Play Store declaration) is a different API
 * surface this deliberately avoids needing, consistent with this project's existing
 * `AndroidManifest.xml` posture of not requesting permissions before a feature actually needs
 * them.
 *
 * Excludes this app's own package — a Mission Profile allow/blocking itself makes no sense.
 */
object InstalledAppsProvider {

    data class InstalledApp(
        val packageName: String,
        val label: String,
        val icon: Drawable?,
    )

    fun loadLaunchableApps(context: Context): List<InstalledApp> {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolved: List<ResolveInfo> = packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )

        return resolved
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .mapNotNull { packageName ->
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    InstalledApp(
                        packageName = packageName,
                        label = packageManager.getApplicationLabel(appInfo).toString(),
                        icon = try {
                            packageManager.getApplicationIcon(appInfo)
                        } catch (e: PackageManager.NameNotFoundException) {
                            null
                        },
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    // Uninstalled between queryIntentActivities and here — skip rather than
                    // crash; the picker just won't offer an app that no longer exists.
                    null
                }
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
