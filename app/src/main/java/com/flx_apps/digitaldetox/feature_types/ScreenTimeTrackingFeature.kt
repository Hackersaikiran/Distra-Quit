package com.flx_apps.digitaldetox.feature_types

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.data.DataStorePropertyTransformer
import java.time.LocalDate

/**
 * This feature can track the screen time of the user under certain conditions.
 * Example use cases are tracking the screen time of the user to disable or grayscale apps only
 * after a certain amount of time.
 */
interface ScreenTimeTrackingFeature {
    /**
     * The timestamp when the user did something that should be tracked.
     */
    var trackingSinceTimestamp: Long

    /**
     * The time the user has already used up their daily screen time.
     */
    var usedUpScreenTime: Long

    /**
     * The current date. If the date changes, the [usedUpScreenTime] is reset.
     */
    var today: LocalDate

    /**
     * This method should be called when the user does something that should be tracked, e.g. when
     * an app that should be disabled after a certain time is opened.
     */
    fun eventuallyStartTracking()

    /**
     * Increases the used up screen time by the time since [eventuallyStartTracking] was called.
     *
     * We track these times very tightly using this approach. We could consider using the
     * [UsageStatsProvider] as an alternative. This would require the usage stats permission,
     * but would also allow us to track the screen time even if DetoxDroid has been killed in the
     * meantime.
     *
     * Another advantage of the current approach is that we can also track the screen time for apps
     * that are installed within the Work Profile (whose usage stats are not available to the
     * main profile).
     */
    fun eventuallyIncreaseUsedUpScreenTime()

    /**
     * Returns the current total used up screen time including any active tracking session.
     * This is used to check if the limit has been exceeded without having to close the current session.
     */
    fun getCurrentUsedUpScreenTime(): Long

    class Impl(private val featureId: FeatureId) : ScreenTimeTrackingFeature {
        override var trackingSinceTimestamp: Long = 0L
        override var usedUpScreenTime: Long by DataStoreProperty(
            longPreferencesKey("${featureId}_usedUpScreenTime"), 0L
        )
        override var today: LocalDate by DataStoreProperty(
            stringPreferencesKey("${featureId}_today"),
            LocalDate.now(),
            dataTransformer = object : DataStorePropertyTransformer<LocalDate, String> {
                override fun transformTo(value: LocalDate): String = value.toString()
                override fun transformFrom(value: String): LocalDate = LocalDate.parse(value)
            }
        )

        override fun eventuallyStartTracking() {
            val currentDate = LocalDate.now()
            if (currentDate != today) {
                // the date has changed, so we reset the used up screen time
                usedUpScreenTime = 0L
                today = currentDate
                trackingSinceTimestamp = 0L
            }
            if (trackingSinceTimestamp == 0L) {
                // we are not tracking yet, so we start tracking now
                trackingSinceTimestamp = System.currentTimeMillis()
            }
        }

        override fun eventuallyIncreaseUsedUpScreenTime() {
            val currentDate = LocalDate.now()
            // Check if the date has changed and reset if necessary
            if (currentDate.isAfter(today) || currentDate.isBefore(today)) {
                // the date has changed, so we reset the used up screen time
                usedUpScreenTime = 0L
                today = currentDate
                trackingSinceTimestamp = 0L // reset tracking timestamp as well when date changes
                return
            }
            if (trackingSinceTimestamp == 0L) return // there is no tracking timestamp
            usedUpScreenTime += System.currentTimeMillis() - trackingSinceTimestamp
            trackingSinceTimestamp = 0L
        }

        override fun getCurrentUsedUpScreenTime(): Long {
            val currentDate = LocalDate.now()
            // Check if the date has changed and reset if necessary
            if (currentDate.isAfter(today) || currentDate.isBefore(today)) {
                return 0L
            }
            // Return stored time plus any currently tracking time
            return if (trackingSinceTimestamp > 0L) {
                usedUpScreenTime + (System.currentTimeMillis() - trackingSinceTimestamp)
            } else {
                usedUpScreenTime
            }
        }
    }
}