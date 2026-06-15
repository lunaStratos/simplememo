package com.lunastratos.simplememo

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.lunastratos.simplememo.data.MemoDbHelper
import com.lunastratos.simplememo.data.Prefs
import com.lunastratos.simplememo.util.MemoColors
import com.lunastratos.simplememo.util.ReminderScheduler

/** 메모 목록 화면 */
class MainActivity : AppCompatActivity() {

    private lateinit var adapter: MemoAdapter
    private lateinit var emptyView: TextView
    private lateinit var fab: FloatingActionButton
    private lateinit var recyclerView: RecyclerView
    private var query: String? = null
    private var colorFilter: Int = 0
    private var tagFilter: String? = null
    private var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        emptyView = findViewById(R.id.empty_view)

        adapter = MemoAdapter(
            onClick = { memo ->
                startActivity(
                    Intent(this, EditActivity::class.java).putExtra(EditActivity.EXTRA_ID, memo.id)
                )
            },
            onSelectionChanged = { count ->
                actionMode?.title = getString(R.string.selected_count, count)
            },
        )
        recyclerView = findViewById(R.id.memo_list)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            addItemDecoration(
                DividerItemDecoration(this@MainActivity, DividerItemDecoration.VERTICAL)
            )
            adapter = this@MainActivity.adapter
            attachTouchGestures(this)
        }
        fab = findViewById(R.id.fab_add)
        fab.setOnClickListener {
            startActivity(Intent(this, EditActivity::class.java))
        }
    }

    /**
     * 목록 제스처 연결.
     * 오른쪽 스와이프 → 색상 선택,
     * 길게 눌러 드래그 → 순서 변경(직접 정렬순으로 전환). 선택 모드에서는 모두 비활성.
     */
    private fun attachTouchGestures(recyclerView: RecyclerView) {
        val callback = object : MemoTouchCallback(this) {
            private var dragged = false

            // 검색/필터 중에는 일부 항목만 보이므로 순서 저장이 어긋난다 → 드래그 비활성
            override fun getDragDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ): Int = if (query == null && colorFilter == 0 && tagFilter == null &&
                !adapter.selectionMode
            ) {
                super.getDragDirs(recyclerView, viewHolder)
            } else 0

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ): Int = if (adapter.selectionMode) 0 else super.getSwipeDirs(recyclerView, viewHolder)

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                dragged = true
                adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ) {
                super.clearView(recyclerView, viewHolder)
                if (!dragged) return
                dragged = false
                MemoDbHelper.get(this@MainActivity).reorder(adapter.currentIds())
                if (Prefs.sort(this@MainActivity) != Prefs.SORT_MANUAL) {
                    Prefs.setSort(this@MainActivity, Prefs.SORT_MANUAL)
                    Snackbar.make(recyclerView, R.string.sort_changed_manual, Snackbar.LENGTH_SHORT)
                        .setAnchorView(fab)
                        .show()
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val memo = adapter.itemAt(position)
                if (memo == null) {
                    reload()
                    return
                }
                // 오른쪽 스와이프: 밀려난 행을 되돌리고 색상 선택 다이얼로그 표시
                adapter.notifyItemChanged(position)
                showColorPicker(listOf(memo.id), memo.color)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
    }

    /** 휴지통으로 이동하고 실행 취소 스낵바를 보여준다 */
    private fun moveToTrashWithUndo(ids: List<Long>) {
        val db = MemoDbHelper.get(this)
        ids.forEach { ReminderScheduler.cancel(this, it) }
        db.moveToTrash(ids)
        reload()
        Snackbar.make(recyclerView, R.string.deleted_to_trash, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) {
                ids.forEach { id ->
                    db.restore(id)
                    // 미래 시각 알림은 복원과 함께 다시 등록
                    db.get(id)?.let { memo ->
                        if (memo.reminder > System.currentTimeMillis()) {
                            ReminderScheduler.schedule(this, memo.id, memo.reminder)
                        }
                    }
                }
                reload()
            }
            .setAnchorView(fab)
            .show()
    }

    /** 메모 색상 선택 다이얼로그. 첫 항목은 색상 해제. */
    private fun showColorPicker(ids: List<Long>, current: Int) {
        showColorChooser(R.string.memo_color, current, R.string.color_none) { color ->
            MemoDbHelper.get(this).setColor(ids, color)
            actionMode?.finish()
            reload()
        }
    }

    /**
     * 색상 원형 선택 다이얼로그 공통 구현.
     * 팔레트의 0 값(빈 원)은 [noneLabelRes] 의미(색상 없음 또는 전체)로 쓰인다.
     */
    private fun showColorChooser(
        titleRes: Int,
        current: Int,
        noneLabelRes: Int,
        onPick: (Int) -> Unit,
    ) {
        val names = resources.getStringArray(R.array.memo_color_names)
        val density = resources.displayMetrics.density
        val size = (36 * density).toInt()
        val margin = (4 * density).toInt()
        val padding = (16 * density).toInt()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(padding, padding / 2, padding, padding / 2)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setView(row)
            .setNegativeButton(R.string.cancel, null)
            .create()

        MemoColors.PALETTE.forEachIndexed { index, color ->
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = margin
                    marginEnd = margin
                }
                contentDescription =
                    if (color == 0) getString(noneLabelRes) else names.getOrNull(index)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (color == 0) {
                        // 색상 없음/전체: 회색 테두리만 있는 빈 원
                        setStroke((2 * density).toInt(), 0xFF9E9E9E.toInt())
                    } else {
                        setColor(color)
                        if (color == current) {
                            setStroke((3 * density).toInt(), 0xFF616161.toInt())
                        }
                    }
                }
                setOnClickListener {
                    onPick(color)
                    dialog.dismiss()
                }
            })
        }
        dialog.show()
    }

    // ----- 필터 -----

    private fun showColorFilterDialog() {
        showColorChooser(R.string.filter_color, colorFilter, R.string.filter_all) { color ->
            colorFilter = color
            reload()
        }
    }

    private fun showTagFilterDialog() {
        val tags = MemoDbHelper.get(this).allTags()
        if (tags.isEmpty()) {
            Toast.makeText(this, R.string.no_tags, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = arrayOf(getString(R.string.filter_all)) + tags.map { "#$it" }
        val checked = tagFilter?.let { current -> tags.indexOf(current) + 1 } ?: 0
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.filter_tag)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                tagFilter = if (which == 0) null else tags[which - 1]
                reload()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateFilterSubtitle() {
        val names = resources.getStringArray(R.array.memo_color_names)
        val parts = mutableListOf<String>()
        val colorIndex = MemoColors.PALETTE.indexOf(colorFilter)
        if (colorFilter != 0 && colorIndex >= 0) names.getOrNull(colorIndex)?.let { parts.add(it) }
        tagFilter?.let { parts.add("#$it") }
        supportActionBar?.subtitle =
            if (parts.isEmpty()) null
            else getString(R.string.filter_subtitle, parts.joinToString(" · "))
    }

    // ----- 선택 모드 -----

    private fun startSelectionMode() {
        if (actionMode != null) return
        adapter.startSelection()
        actionMode = startSupportActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                mode.menuInflater.inflate(R.menu.menu_selection, menu)
                mode.title = getString(R.string.selected_count, 0)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val ids = adapter.selectedIds()
                if (ids.isEmpty()) return true
                when (item.itemId) {
                    R.id.action_selection_delete -> {
                        moveToTrashWithUndo(ids)
                        mode.finish()
                    }
                    R.id.action_selection_color -> {
                        val colors = adapter.selectedMemos().map { it.color }.distinct()
                        showColorPicker(ids, colors.singleOrNull() ?: 0)
                    }
                    R.id.action_selection_pin -> {
                        // 하나라도 미고정이면 모두 고정, 전부 고정이면 모두 해제
                        val pin = adapter.selectedMemos().any { !it.pinned }
                        MemoDbHelper.get(this@MainActivity).setPinned(ids, pin)
                        mode.finish()
                        reload()
                    }
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                adapter.endSelection()
                actionMode = null
            }
        })
    }

    // ----- 목록 -----

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val list = MemoDbHelper.get(this).list(
            inTrash = false,
            query = query,
            sort = Prefs.sort(this),
            color = colorFilter,
            tag = tagFilter,
        )
        adapter.submit(list)
        val filtered = !query.isNullOrBlank() || colorFilter != 0 || tagFilter != null
        emptyView.setText(if (filtered) R.string.empty_search else R.string.empty_list)
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        updateFilterSubtitle()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchView = menu.findItem(R.id.action_search).actionView as SearchView
        searchView.queryHint = getString(R.string.search)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(text: String?): Boolean = true

            override fun onQueryTextChange(text: String?): Boolean {
                query = text?.takeIf { it.isNotBlank() }
                reload()
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_filter_color -> {
            showColorFilterDialog()
            true
        }
        R.id.action_filter_tag -> {
            showTagFilterDialog()
            true
        }
        R.id.action_filter_clear -> {
            colorFilter = 0
            tagFilter = null
            reload()
            true
        }
        R.id.action_select -> {
            startSelectionMode()
            true
        }
        R.id.action_trash -> {
            startActivity(Intent(this, TrashActivity::class.java))
            true
        }
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
