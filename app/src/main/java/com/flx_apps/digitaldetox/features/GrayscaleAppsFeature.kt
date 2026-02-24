package com.flx_apps.digitaldetox.features

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.FeatureTexts
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.feature_types.OnAppOpenedSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.OnScreenTurnedOffSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.ScreenTimeTrackingFeature
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.feature_types.SupportsScheduleFeature
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature.eventuallyIncreaseUsedUpScreenTime
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature.onAppOpened
import com.flx_apps.digitaldetox.system_integration.GrayscaleOverlayManager
import com.flx_apps.digitaldetox.ui.screens.feature.grayscale_apps.GrayscaleAppsFeatureSettingsSection
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.util.AccessibilityEventUtil
import timber.log.Timber

val GrayscaleAppsFeatureId = Feature.createId(GrayscaleAppsFeature::class.java)

/**
 * A simple NeedsPermissionsFeature implementation that doesn't require any permissions.
 * Used by GrayscaleAppsFeature since it now works without WRITE_SECURE_SETTINGS.
 */
class NoPermissionsRequiredFeature : NeedsPermissionsFeature {
    override fun hasPermissions(context: Context): Boolean = true

    override fun requestPermissions(context: Context, navViewModel: NavViewModel) {
        // No permissions needed
    }
}

/**
 * The grayscale feature can be used to turn the screen grayscale depending on the schedule and
 * which app is currently in the foreground.
 *
 * IMPLEMENTATION NOTE:
 * This feature now uses a full-screen overlay rendered via AccessibilityService overlay type (TYPE_ACCESSIBILITY_OVERLAY)
 * instead of the previous system-wide Settings.Secure approach. This eliminates the need for WRITE_SECURE_SETTINGS
 * permission and provides more reliable, no-permission grayscale filtering.
 */
object GrayscaleAppsFeature : Feature(), OnAppOpenedSubscriptionFeature,
    OnScreenTurnedOffSubscriptionFeature,
    SupportsScheduleFeature by SupportsScheduleFeature.Impl(GrayscaleAppsFeatureId),
    SupportsAppExceptionsFeature by SupportsAppExceptionsFeature.Impl(GrayscaleAppsFeatureId),
    ScreenTimeTrackingFeature by ScreenTimeTrackingFeature.Impl(GrayscaleAppsFeatureId),
    NeedsPermissionsFeature by NoPermissionsRequiredFeature() {
    override val texts: FeatureTexts = FeatureTexts(
        R.string.feature_grayscale,
        R.string.feature_grayscale_subtitle,
        R.string.feature_grayscale_description,
    )
    override val iconRes: Int = R.drawable.ic_contrast
    override val settingsContent: @Composable () -> Unit = { GrayscaleAppsFeatureSettingsSection() }

    /**
     * Represents, whether the grayscale filter is currently active.
     * We use this variable in order to avoid unnecessary calls to the overlay manager (i.e. only
     * call the overlay manager when the grayscale filter should be turned on or off).
     */
    private var isCurrentlyGrayscale: Boolean = false

    /**
     * The package name of the app that is currently being tracked for grayscale purposes.
     * Used to detect when switching between apps to properly close/open tracking sessions.
     */
    private var lastTrackedPackageName: String? = null

    /**
     * Whether the extra dim filter should be turned on when the grayscale filter is active.
     */
    var extraDim: Boolean by DataStoreProperty(
        booleanPreferencesKey("${id}_extraDim"), true
    )

    /**
     * Whether the grayscale filter should be ignored when the current app is not in full screen mode.
     * This is the case for example when the keyboard, sound controls or the notification bar are
     * shown.
     */
    var ignoreNonFullScreenApps: Boolean by DataStoreProperty(
        booleanPreferencesKey("${id}_ignoreNonFullScreenApps"), true
    )

    /**
     * The allowed daily color screen time in milliseconds. Once this limit is reached, the grayscale
     * filter will be turned on and will stay on until the next day.
     */
    var allowedDailyColorScreenTime: Long by DataStoreProperty(
        longPreferencesKey("${id}_allowedDailyColorScreenTime"), 0L
    )

    /**
     * On start, we trigger [onAppOpened] once to turn the grayscale filter on or off depending on
     * the current app.
     */
    override fun onStart(context: Context) {
        val accessibilityEvent = AccessibilityEventUtil.createEvent()
        onAppOpened(context, accessibilityEvent.packageName.toString(), accessibilityEvent)
    }

    /**
     * On a pause, turn the grayscale filter off.
     */
    override fun onPause(context: Context) {
        setGrayscale(context, false)
    }

    /**
     * Packages that are known to work well even if they don't report isFullScreen=true.
     * This is a workaround for custom ROMs (e.g., ColorOS on Realme devices) where popular apps
     * like Instagram, TikTok, and YouTube don't properly set the fullscreen flag.
     */
    private val knownFullscreenPackages = setOf(
        "com.instagram.android",
        "com.tiktok",
        "com.android.youtube",
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.twitter.android",
        "com.reddit.frontpage"
    )

    /**
     * Check if an app should be considered fullscreen, with special handling for apps
     * that may not report fullscreen correctly on custom ROMs.
     */
    private fun isEffectivelyFullscreen(packageName: String, isFullScreen: Boolean): Boolean {
        // First check: if AccessibilityEvent reports fullscreen, trust it
        if (isFullScreen) {
            return true
        }
        
        // Second check: if the app is in our known fullscreen packages list, assume fullscreen
        if (knownFullscreenPackages.contains(packageName)) {
            Timber.d("GrayscaleAppsFeature.isEffectivelyFullscreen: $packageName is a known fullscreen app, allowing despite isFullScreen=false")
            return true
        }
        
        // Otherwise, respect the isFullScreen flag
        return false
    }

    /**
     * If an app is opened, turn the grayscale filter on or off depending on the app that is
     * currently in the foreground.
     */
    override fun onAppOpened(
        context: Context, packageName: String, accessibilityEvent: AccessibilityEvent
    ) {
        val exceptionsContainApp = appExceptions.contains(packageName)
        Timber.d("GrayscaleAppsFeature.onAppOpened: packageName=$packageName, isFullScreen=${accessibilityEvent.isFullScreen}, exceptionsContainApp=$exceptionsContainApp, appExceptionListType=$appExceptionListType")
        
        // Determine if this app should be TRACKED for color screen time
        // (based on the exception list type)
        val shouldTrackForColorTime = when (appExceptionListType) {
            AppExceptionListType.NOT_LIST -> !exceptionsContainApp  // track all except those in list
            AppExceptionListType.ONLY_LIST -> exceptionsContainApp  // track only those in list
        }
        
        Timber.d("GrayscaleAppsFeature.onAppOpened: shouldTrackForColorTime=$shouldTrackForColorTime")
        
        // Check fullscreen status only for apps that are NOT explicitly selected in the feature.
        // If an app is selected in the feature, we want grayscale to apply regardless of fullscreen status.
        // This allows users to force grayscale on apps that don't properly report fullscreen on custom ROMs.
        if (!shouldTrackForColorTime && ignoreNonFullScreenApps && !accessibilityEvent.isFullScreen) {
            Timber.d("GrayscaleAppsFeature.onAppOpened: App not selected and not fullscreen, ignoring")
            // we are not in full screen mode and app is not selected, so we do not want to interfere
            // close previous tracking session if we're switching away
            if (lastTrackedPackageName != null) {
                eventuallyIncreaseUsedUpScreenTime()
                lastTrackedPackageName = null
            }
            return
        }
        
        if (!shouldTrackForColorTime) {
            Timber.d("GrayscaleAppsFeature.onAppOpened: App should not be tracked, removing grayscale")
            // this app should not be tracked for color screen time, so close any previous tracking session
            if (lastTrackedPackageName != null) {
                eventuallyIncreaseUsedUpScreenTime()
                lastTrackedPackageName = null
            }
            // no grayscale for this app
            if (isCurrentlyGrayscale) {
                setGrayscale(context, false)
                isCurrentlyGrayscale = false
            }
        } else {
            Timber.d("GrayscaleAppsFeature.onAppOpened: App should be tracked")
            // this is an app that should be tracked for color screen time
            // if we were tracking a different app, close that session first
            if (lastTrackedPackageName != packageName && lastTrackedPackageName != null) {
                eventuallyIncreaseUsedUpScreenTime()
            }
            // start tracking this app (only if not already tracking it)
            if (lastTrackedPackageName != packageName) {
                eventuallyStartTracking()
                lastTrackedPackageName = packageName
            }
            
            // Check current total time including active session to determine if grayscale should be applied
            val currentTotalTime = getCurrentUsedUpScreenTime()
            // Apply grayscale if:
            // 1. Daily limit is 0 (immediate grayscale), OR
            // 2. Daily limit > 0 AND current time exceeds the limit
            val shouldApplyGrayscale = allowedDailyColorScreenTime == 0L || (allowedDailyColorScreenTime > 0 && currentTotalTime >= allowedDailyColorScreenTime)
            
            Timber.d("GrayscaleAppsFeature.onAppOpened: currentTotalTime=$currentTotalTime, allowedDailyColorScreenTime=$allowedDailyColorScreenTime, shouldApplyGrayscale=$shouldApplyGrayscale")
            
            // Apply or remove grayscale filter as needed
            if (shouldApplyGrayscale != isCurrentlyGrayscale) {
                Timber.i("GrayscaleAppsFeature.onAppOpened: Applying grayscale=$shouldApplyGrayscale")
                setGrayscale(context, shouldApplyGrayscale)
                isCurrentlyGrayscale = shouldApplyGrayscale
            }
        }
    }

    /**
     * When the screen is turned off, the used up screen time is increased by the time since the
     * last grayscale app was opened.
     * @see eventuallyIncreaseUsedUpScreenTime
     */
    override fun onScreenTurnedOff(context: Context?) {
        eventuallyIncreaseUsedUpScreenTime()
    }

    /**
     * Function to turn the grayscale filter on or off.
     * The grayscale filter is implemented as a full-screen overlay using WindowManager
     * with TYPE_ACCESSIBILITY_OVERLAY. This approach:
     * - Requires no special permissions (no WRITE_SECURE_SETTINGS needed)
     * - Works on all Android versions >= 8
     * - Does not interfere with system settings
     * - Supports dynamic alpha adjustment for extra dim simulation
     *
     * @param context The context.
     * @param grayscale Whether the grayscale filter should be turned on.
     */
    private fun setGrayscale(
        context: Context, grayscale: Boolean
    ) {
        Timber.d("GrayscaleAppsFeature.setGrayscale: grayscale=$grayscale, extraDim=$extraDim")
        
        if (grayscale) {
            // Calculate alpha based on extraDim setting
            // When extraDim is enabled, increase opacity for more dimming
            // alpha = 1.0f means fully opaque (darkest), 0.5f means 50% transparent (lightest)
            // Default: extraDim checked = 0.9f (darker), unchecked = 0.7f (lighter)
            val alpha = if (extraDim) 0.9f else 0.7f
            Timber.i("GrayscaleAppsFeature.setGrayscale: ✓ Showing overlay with alpha=$alpha (extraDim=$extraDim)")
            GrayscaleOverlayManager.showOverlay(context, alpha)
        } else {
            Timber.i("GrayscaleAppsFeature.setGrayscale: ✓ Removing overlay")
            GrayscaleOverlayManager.removeOverlay()
        }
    }

}
