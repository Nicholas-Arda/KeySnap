package com.example.service.adb

import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdvancedAutostartSchedulingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(AdvancedAutostart.PREFS_NAME, Context.MODE_PRIVATE)
    private val scheduler get() = context.getSystemService(JobScheduler::class.java)

    @Before fun reset() { prefs.edit().clear().commit(); scheduler.cancelAll() }

    @Test fun defaultOffNeverSchedulesAfterBoot() {
        assertFalse(AdvancedAutostart.enabled(context))
        prefs.edit().putBoolean("advanced_mode_enabled", true).commit()
        AdvancedAutostart.afterBoot(context)
        assertTrue(scheduler.allPendingJobs.isEmpty())
    }

    @Test fun enabledButAdvancedOffDoesNotScheduleAndCancelsDailyJob() {
        prefs.edit().putBoolean(AdvancedAutostart.PREF_ENABLED, true).commit()
        AdvancedAutostart.scheduleKeepAlive(context)
        AdvancedAutostart.afterBoot(context)
        assertTrue(scheduler.allPendingJobs.isEmpty())
    }

    @Test fun enabledBootSchedulesWifiJobAndResetsOnlyBootOwnership() {
        prefs.edit().putBoolean(AdvancedAutostart.PREF_ENABLED, true)
            .putBoolean("advanced_mode_enabled", true)
            .putBoolean("advanced_autostart_enabled_wifi_debugging", true).commit()
        AdvancedAutostartReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        val job = scheduler.getPendingJob(AdvancedAutostart.JOB_ID)!!
        assertFalse(job.isPersisted)
        assertTrue(job.requiredNetwork!!.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
        assertFalse(AdvancedAutostart.ownsWifiDebugging(context))
        assertTrue(AdvancedAutostart.enabled(context))
        assertTrue(WirelessAdbManager.isAdvancedModeWanted(context))
    }

    @Test fun unrelatedBroadcastDoesNotRunBootCleanup() {
        prefs.edit().putBoolean("advanced_autostart_enabled_wifi_debugging", true).commit()
        AdvancedAutostartReceiver().onReceive(context, Intent(Intent.ACTION_PACKAGE_REPLACED))
        assertTrue(AdvancedAutostart.ownsWifiDebugging(context))
        assertTrue(scheduler.allPendingJobs.isEmpty())
    }

    @Test fun dailyJobPersistsAcrossBootAndBothJobsCancelTogether() {
        prefs.edit().putBoolean(AdvancedAutostart.PREF_ENABLED, true).putBoolean("advanced_mode_enabled", true).commit()
        AdvancedAutostart.afterBoot(context)
        AdvancedAutostart.scheduleKeepAlive(context)
        val daily = scheduler.getPendingJob(AdvancedAutostart.KEEP_ALIVE_JOB_ID)!!
        assertTrue(daily.isPersisted)
        assertTrue(daily.isPeriodic)
        assertEquals(24 * 60 * 60_000L, daily.intervalMillis)
        AdvancedAutostart.cancelJobs(context)
        assertTrue(scheduler.allPendingJobs.isEmpty())
    }
}
