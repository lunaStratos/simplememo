package com.lunastratos.simplememo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * 메모 목록 터치 제스처 콜백.
 * - 오른쪽 스와이프: 청록 배경 + 팔레트 아이콘 (색상 지정)
 * - 길게 눌러 위/아래 드래그: 순서 변경
 */
abstract class MemoTouchCallback(context: Context) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    ItemTouchHelper.RIGHT,
) {

    private val colorBackground = ColorDrawable(0xFF26A69A.toInt())
    private val paletteIcon = ContextCompat.getDrawable(context, R.drawable.ic_palette)!!
        .mutate().apply { setTint(Color.WHITE) }
    private val iconMargin = (20 * context.resources.displayMetrics.density).toInt()

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val itemView = viewHolder.itemView
            if (dX > 0) {
                colorBackground.setBounds(
                    itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom
                )
                drawBackgroundWithIcon(
                    c, colorBackground, paletteIcon,
                    iconLeft = itemView.left + iconMargin, itemView = itemView
                )
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun drawBackgroundWithIcon(
        c: Canvas,
        background: ColorDrawable,
        icon: Drawable,
        iconLeft: Int,
        itemView: android.view.View,
    ) {
        background.draw(c)
        val iconTop = itemView.top + (itemView.height - icon.intrinsicHeight) / 2
        icon.setBounds(
            iconLeft, iconTop, iconLeft + icon.intrinsicWidth, iconTop + icon.intrinsicHeight
        )
        // 드러난 배경 영역 안에서만 아이콘이 보이도록 클리핑
        val save = c.save()
        c.clipRect(background.bounds)
        icon.draw(c)
        c.restoreToCount(save)
    }
}
