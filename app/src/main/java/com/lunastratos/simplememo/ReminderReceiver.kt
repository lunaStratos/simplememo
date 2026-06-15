package com.lunastratos.simplememo

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import com.lunastratos.simplememo.data.MemoDbHelper

/** 알림 시각이 되면 메모 내용으로 알림을 띄운다. 발송 후 알림 설정은 해제된다. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val memoId = intent.getLongExtra(EXTRA_MEMO_ID, -1L)
        if (memoId < 0) return
        val memo = MemoDbHelper.get(context).get(memoId) ?: return
        if (memo.deleted > 0 || memo.reminder == 0L) return

        val openIntent = Intent(context, EditActivity::class.java)
            .putExtra(EditActivity.EXTRA_ID, memo.id)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 23) flags = flags or PendingIntent.FLAG_IMMUTABLE
        val contentIntent = TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(openIntent)
            .getPendingIntent(memo.id.toInt(), flags)

        val notification = NotificationCompat.Builder(context, App.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(memo.title.ifEmpty { context.getString(R.string.no_title) })
            .setContentText(memo.preview.ifEmpty { memo.title })
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // 33+ 에서 알림 권한이 없으면 SecurityException 이 날 수 있으므로 방어
        runCatching {
            NotificationManagerCompat.from(context).notify(memo.id.toInt(), notification)
        }
        // 일회성 알림이므로 발송 후 해제
        MemoDbHelper.get(context).setReminder(memo.id, 0L)
    }

    companion object {
        const val EXTRA_MEMO_ID = "memoId"
    }
}
