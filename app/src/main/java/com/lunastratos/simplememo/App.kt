package com.lunastratos.simplememo

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.lunastratos.simplememo.data.Prefs

class App : Application(), Application.ActivityLifecycleCallbacks {

    /** 현재 시작된 액티비티 수. 0 → 1 전환이 포그라운드 진입이다. */
    private var startedCount = 0

    override fun onCreate() {
        super.onCreate()
        Prefs.applyTheme(this)
        registerActivityLifecycleCallbacks(this)
        createNotificationChannel()
        if (Prefs.autoBackup(this)) AutoBackupWorker.schedule(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            getString(R.string.notification_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        )
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    override fun onActivityStarted(activity: Activity) {
        // 백그라운드 → 포그라운드 진입 시 앱 잠금
        if (startedCount == 0 && activity !is LockActivity && Prefs.appLock(this)) {
            activity.startActivity(Intent(activity, LockActivity::class.java))
        }
        startedCount++
    }

    override fun onActivityStopped(activity: Activity) {
        startedCount--
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
    }
}
