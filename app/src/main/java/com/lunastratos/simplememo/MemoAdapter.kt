package com.lunastratos.simplememo

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lunastratos.simplememo.data.Memo
import com.lunastratos.simplememo.util.Formats

/**
 * 메모 목록/휴지통 공용 어댑터.
 * 선택 모드에서는 클릭이 항목 선택/해제로 동작한다.
 */
class MemoAdapter(
    private val onClick: (Memo) -> Unit,
    private val onSelectionChanged: (Int) -> Unit = {},
) : RecyclerView.Adapter<MemoAdapter.Holder>() {

    private val items = mutableListOf<Memo>()
    private val selected = linkedSetOf<Long>()

    var selectionMode = false
        private set

    fun submit(list: List<Memo>) {
        items.clear()
        items.addAll(list)
        // 목록 갱신으로 사라진 메모는 선택에서도 제거
        selected.retainAll(items.map { it.id }.toSet())
        notifyDataSetChanged()
    }

    fun itemAt(position: Int): Memo? = items.getOrNull(position)

    /** 드래그로 항목을 옮긴다. */
    fun move(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        items.add(to, items.removeAt(from))
        notifyItemMoved(from, to)
    }

    /** 현재 화면에 표시 중인 순서대로의 메모 id 목록 */
    fun currentIds(): List<Long> = items.map { it.id }

    fun startSelection() {
        selectionMode = true
        selected.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun endSelection() {
        selectionMode = false
        selected.clear()
        notifyDataSetChanged()
    }

    fun selectedIds(): List<Long> = selected.toList()

    fun selectedMemos(): List<Memo> = items.filter { it.id in selected }

    private fun toggleSelection(memo: Memo) {
        if (!selected.remove(memo.id)) selected.add(memo.id)
        onSelectionChanged(selected.size)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_memo, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val memo = items[position]
        holder.title.text = memo.title.ifEmpty {
            holder.itemView.context.getString(R.string.no_title)
        }
        // 휴지통 메모는 삭제 시각, 일반 메모는 수정 시각 표시
        holder.date.text = Formats.listDate(if (memo.deleted > 0) memo.deleted else memo.updated)
        holder.preview.text = memo.preview
        holder.preview.visibility = if (memo.preview.isEmpty()) View.GONE else View.VISIBLE
        if (memo.color != 0) {
            holder.colorStrip.setBackgroundColor(memo.color)
            holder.colorStrip.visibility = View.VISIBLE
        } else {
            holder.colorStrip.visibility = View.INVISIBLE
        }
        holder.pin.visibility = if (memo.pinned) View.VISIBLE else View.GONE
        holder.alarm.visibility = if (memo.reminder > 0) View.VISIBLE else View.GONE

        if (selectionMode && memo.id in selected) {
            holder.itemView.setBackgroundColor(SELECTED_COLOR)
        } else {
            val tv = TypedValue()
            holder.itemView.context.theme
                .resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            holder.itemView.setBackgroundResource(tv.resourceId)
        }
        holder.itemView.setOnClickListener {
            if (selectionMode) toggleSelection(memo) else onClick(memo)
        }
    }

    override fun getItemCount(): Int = items.size

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val colorStrip: View = view.findViewById(R.id.memo_color_strip)
        val title: TextView = view.findViewById(R.id.memo_title)
        val pin: ImageView = view.findViewById(R.id.memo_pin)
        val alarm: ImageView = view.findViewById(R.id.memo_alarm)
        val date: TextView = view.findViewById(R.id.memo_date)
        val preview: TextView = view.findViewById(R.id.memo_preview)
    }

    companion object {
        private const val SELECTED_COLOR = 0x33FFB300
    }
}
