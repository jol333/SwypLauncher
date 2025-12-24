package com.joyal.swyplauncher.util

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import java.util.concurrent.TimeUnit

object UsageStatsHelper {
    
    /**
     * Check if the app has usage stats permission
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    /**
     * Open the usage access settings screen and try to highlight/scroll to this app.
     * Uses a combined approach for best compatibility across different Android versions and OEMs:
     * 1. Data URI method (standard)
     * 2. Fragment args method (undocumented, for scrolling/highlighting)
     * 
     * Includes robust error handling to prevent crashes on devices where the settings
     * activity may not be available.
     */
    fun openUsageAccessSettings(context: Context) {
        val pkgName = context.packageName
        
        // Try the enhanced intent with highlighting
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                
                // Method 1: Data URI - helps some OEMs/versions identify the app
                data = android.net.Uri.fromParts("package", pkgName, null)
                
                // Method 2: Undocumented extras for scrolling/highlighting (works on many devices)
                val extras = android.os.Bundle().apply {
                    putString(":settings:fragment_args_key", pkgName)
                }
                putExtra(":settings:fragment_args_key", pkgName)
                putExtra(":settings:show_fragment_args", extras)
            }
            context.startActivity(intent)
            return
        } catch (_: android.content.ActivityNotFoundException) {
            // Enhanced intent not supported, try fallback
        } catch (_: Exception) {
            // Any other error, try fallback
        }
        
        // Fallback: Try plain usage access settings without extras
        try {
            val fallbackIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        } catch (_: android.content.ActivityNotFoundException) {
            // Not supported - silently fail
        } catch (_: Exception) {
            // Any other error - silently fail
        }
    }
    
    /**
     * Get app usage statistics for the specified time range
     * Returns a map of package name to total time in foreground (in milliseconds)
     */
    fun getAppUsageStats(
        context: Context,
        startTime: Long = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30),
        endTime: Long = System.currentTimeMillis()
    ): Map<String, Long> {
        if (!hasUsageStatsPermission(context)) {
            return emptyMap()
        }
        
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        )
        
        return usageStats
            .filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .mapValues { (_, stats) ->
                stats.sumOf { it.totalTimeInForeground }
            }
    }
    
    /**
     * Get app launch count for the specified time range
     * Returns a map of package name to launch count
     */
    fun getAppLaunchCounts(
        context: Context,
        startTime: Long = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30),
        endTime: Long = System.currentTimeMillis()
    ): Map<String, Int> {
        if (!hasUsageStatsPermission(context)) {
            return emptyMap()
        }
        
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        )
        
        return usageStats
            .groupBy { it.packageName }
            .mapValues { (_, stats) ->
                // Sum up all the times the app was moved to foreground
                stats.sumOf { 
                    // Use lastTimeUsed as a proxy for launch count
                    if (it.lastTimeUsed > 0) 1 else 0
                }.toInt()
            }
            .filter { it.value > 0 }
    }
}
