package com.lunastratos.simplememo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews

/** 홈 화면 메모 목록 위젯. 항목을 누르면 편집, + 버튼으로 새 메모. */
class MemoWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context, id))
        }
    }

    private fun buildViews(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_memo_list)

        val adapterIntent = Intent(context, MemoWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_list, adapterIntent)
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)

        // 목록 항목 클릭 → 편집 화면 (항목별 fillInIntent 로 메모 id 전달)
        val editIntent = Intent(context, EditActivity::class.java)
        views.setPendingIntentTemplate(
            R.id.widget_list,
            PendingIntent.getActivity(context, 1, editIntent, mutableFlags())
        )
        // 헤더 제목 → 앱 열기
        views.setOnClickPendingIntent(
            R.id.widget_title,
            PendingIntent.getActivity(
                context, 2, Intent(context, MainActivity::class.java), immutableFlags()
            )
        )
        // + 버튼 → 새 메모
        views.setOnClickPendingIntent(
            R.id.widget_add,
            PendingIntent.getActivity(
                context, 3, Intent(context, EditActivity::class.java), immutableFlags()
            )
        )
        return views
    }

    private fun immutableFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 23) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return flags
    }

    /** 컬렉션 템플릿은 fillInIntent 병합을 위해 mutable 이어야 한다 */
    private fun mutableFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
        return flags
    }

    companion object {
        /** 메모 데이터가 바뀌었을 때 위젯 목록을 갱신한다 */
        fun notifyChanged(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, MemoWidget::class.java))
            if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }
    }
}
