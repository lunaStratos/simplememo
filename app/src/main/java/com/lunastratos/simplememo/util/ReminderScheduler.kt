package com.lunastratos.simplememo.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lunastratos.simplememo.ReminderReceiver
import com.lunastratos.simplememo.data.MemoDbHelper

/** 메모 알림을 AlarmManager 에 등록/해제한다. */
object ReminderScheduler {

    fun schedule(context: Context, memoId: Long, time: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, memoId)
        when {
            Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms() ->
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            Build.VERSION.SDK_INT >= 23 ->
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            else -> am.setExact(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }

    fun cancel(context: Context, memoId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, memoId))
    }

    /** 부팅 후 등 알람이 사라졌을 때 미발송 알림을 다시 등록한다 */
    fun rescheduleAll(context: Context) {
        MemoDbHelper.get(context).pendingReminders().forEach { memo ->
            // 이미 지난 시각은 즉시 발화된다
            schedule(context, memo.id, memo.reminder)
        }
    }

    private fun pendingIntent(context: Context, memoId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_MEMO_ID, memoId)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 23) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, memoId.toInt(), intent, flags)
    }
}
