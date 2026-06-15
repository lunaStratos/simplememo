package com.lunastratos.simplememo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lunastratos.simplememo.util.ReminderScheduler

/** 재부팅 시 AlarmManager 알람이 사라지므로 미발송 메모 알림을 다시 등록한다 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.rescheduleAll(context)
        }
    }
}
