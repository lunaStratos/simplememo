package com.lunastratos.simplememo

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.lunastratos.simplememo.data.Memo
import com.lunastratos.simplememo.data.MemoDbHelper
import com.lunastratos.simplememo.data.Prefs
import com.lunastratos.simplememo.util.Formats

/** 위젯 ListView 에 메모 목록을 공급한다 */
class MemoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        MemoWidgetFactory(applicationContext)
}

private class MemoWidgetFactory(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {

    private var memos: List<Memo> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        memos = MemoDbHelper.get(context)
            .list(inTrash = false, query = null, sort = Prefs.sort(context))
            .take(MAX_ITEMS)
    }

    override fun onDestroy() {
        memos = emptyList()
    }

    override fun getCount(): Int = memos.size

    override fun getViewAt(position: Int): RemoteViews {
        val memo = memos[position]
        return RemoteViews(context.packageName, R.layout.widget_memo_item).apply {
            setTextViewText(
                R.id.widget_item_title,
                memo.title.ifEmpty { context.getString(R.string.no_title) }
            )
            setTextViewText(R.id.widget_item_date, Formats.listDate(memo.updated))
            setOnClickFillInIntent(
                R.id.widget_item_root,
                Intent().putExtra(EditActivity.EXTRA_ID, memo.id)
            )
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = memos.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    companion object {
        private const val MAX_ITEMS = 50
    }
}
