package com.flx_apps.digitaldetox.system_integration

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.TenSecondsInMs
import java.time.LocalDate
import java.time.ZoneId

/**
 * A singleton that provides access to the [UsageStatsManager] and caches the usage stats for the
 * current day. This is used, for example, to determine the screen time of the current day.
 */
object UsageStatsProvider {
    /**
     * The timestamp of the last refresh of [usageStatsToday]. This is used to cache the usage stats
     * for one minute.
     */
    private var usageStatsTodayLastRefresh = 0L

    /**
     * The timestamp of the last refresh of [appUsageTodayByEvents].
     */
    private var appUsageTodayByEventsLastRefresh = 0L

    /**
     * Returns the usage stats for the current day. This list is cached for one minute to reduce
     * the number of calls to the system service.
     */
    var usageStatsToday: Map<String, UsageStats> = mapOf()
        get() {
            val now = System.currentTimeMillis()
            if (now - usageStatsTodayLastRefresh > TenSecondsInMs) {
                // retrieve usage stats for the current day
                val usageStatsManager = DetoxDroidApplication.appContext.getSystemService(
                    Context.USAGE_STATS_SERVICE
                ) as UsageStatsManager
                val dayBeginningMs =
                    LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()
                        .toEpochMilli()
                field = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, dayBeginningMs, now
                ).filter {
                    // filter out apps that were not used today
                    it.lastTimeUsed >= dayBeginningMs && getAppScreenTime(it) > 0
                }.groupingBy {
                    // group by package name...
                    it.packageName
                }.aggregate { _, accumulator, element, first ->
                    // ... and sum up the usage stats for each package name
                    if (first) element else accumulator!!.apply { add(element) }
                }
                usageStatsTodayLastRefresh = now
            }
            return field
        }

    /**
     * Returns per-app usage durations for the current day using usage events.
     * This is more accurate on older Android versions (e.g., Android 8).
     */
    var appUsageTodayByEvents: Map<String, Long> = mapOf()
        get() {
            val now = System.currentTimeMillis()
            if (now - appUsageTodayByEventsLastRefresh > TenSecondsInMs) {
                val usageStatsManager = DetoxDroidApplication.appContext.getSystemService(
                    Context.USAGE_STATS_SERVICE
                ) as UsageStatsManager
                val dayBeginningMs =
                    LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()
                        .toEpochMilli()
                val events = usageStatsManager.queryEvents(dayBeginningMs, now)
                val event = UsageEvents.Event()
                val lastResumed = mutableMapOf<String, Long>()
                val totals = mutableMapOf<String, Long>()

                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val pkg = event.packageName ?: continue
                    when (event.eventType) {
                        UsageEvents.Event.MOVE_TO_FOREGROUND,
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            lastResumed[pkg] = event.timeStamp
                        }

                        UsageEvents.Event.MOVE_TO_BACKGROUND,
                        UsageEvents.Event.ACTIVITY_PAUSED -> {
                            val start = lastResumed.remove(pkg)
                            if (start != null && event.timeStamp >= start) {
                                totals[pkg] = (totals[pkg] ?: 0L) + (event.timeStamp - start)
                            }
                        }
                    }
                }

                // close any running sessions until now
                lastResumed.forEach { (pkg, start) ->
                    if (now >= start) {
                        totals[pkg] = (totals[pkg] ?: 0L) + (now - start)
                    }
                }

                field = totals
                appUsageTodayByEventsLastRefresh = now
            }
            return field
        }

    /**
     * Returns the usage stats for the current day without consulting the cache.
     */
    fun getUpdatedUsageStatsToday(): Map<String, UsageStats> {
        usageStatsTodayLastRefresh = 0L
        return usageStatsToday
    }

    /**
     * Returns per-app usage durations for the current day without consulting the cache.
     */
    fun getUpdatedAppUsageTodayByEvents(): Map<String, Long> {
        appUsageTodayByEventsLastRefresh = 0L
        return appUsageTodayByEvents
    }

    /**
     * Returns the screen time of the given apps in milliseconds.
     */
    fun getScreenTimeForApps(apps: List<String>): Long {
        return apps.sumOf { usageStatsToday[it]?.let { stats -> getAppScreenTime(stats) } ?: 0L }
    }

    /**
     * Returns the app screen time for a single [UsageStats]. On Android Q+ this uses
     * [UsageStats.totalTimeVisible] to better match Digital Wellbeing's definition of screen time.
     * On older versions it falls back to [UsageStats.totalTimeInForeground].
     */
    fun getAppScreenTime(stats: UsageStats): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            stats.totalTimeVisible
        } else {
            stats.totalTimeInForeground
        }
    }
}