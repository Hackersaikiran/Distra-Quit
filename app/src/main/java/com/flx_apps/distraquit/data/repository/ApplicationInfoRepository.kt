package com.flx_apps.distraquit.data.repository

import android.content.pm.PackageManager
import com.flx_apps.distraquit.DistraQuitApplication
import com.flx_apps.distraquit.util.getAppCategoryTitle
import com.flx_apps.distraquit.util.isSystemApp

/**
 * Holds the list of all installed apps and the set of all app categories.
 */
data class ApplicationInfoData(
    val packageName: String, val appName: String, val appCategory: String, val isSystemApp: Boolean
)

/**
 * Repository for app information.
 */
object ApplicationInfoRepository {
    /**
     * Returns a list of all installed apps and a set of all app categories.
     */
    fun getInstalledApps(): List<ApplicationInfoData> {
        val context = DistraQuitApplication.appContext
        val packageManager = DistraQuitApplication.appContext.packageManager
        val installedApps =
            packageManager.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
                .map { appInfo ->
                    ApplicationInfoData(
                        packageName = appInfo.packageName,
                        appName = appInfo.loadLabel(packageManager).toString(),
                        appCategory = appInfo.getAppCategoryTitle(context),
                        isSystemApp = appInfo.isSystemApp()
                    )
                }
        return installedApps
    }
}