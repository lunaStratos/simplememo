package com.lunastratos.simplememo

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lunastratos.simplememo.data.Memo
import com.lunastratos.simplememo.data.MemoDbHelper
import com.lunastratos.simplememo.util.ReminderScheduler

/** 휴지통 화면. 메모 선택 시 복원하거나 완전 삭제한다. */
class TrashActivity : AppCompatActivity() {

    private lateinit var adapter: MemoAdapter
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash)

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        emptyView = findViewById(R.id.empty_view)
        adapter = MemoAdapter(onClick = { memo -> showItemDialog(memo) })
        findViewById<RecyclerView>(R.id.memo_list).apply {
            layoutManager = LinearLayoutManager(this@TrashActivity)
            addItemDecoration(
                DividerItemDecoration(this@TrashActivity, DividerItemDecoration.VERTICAL)
            )
            adapter = this@TrashActivity.adapter
        }
        reload()
    }

    private fun reload() {
        val list = MemoDbHelper.get(this).list(inTrash = true, query = null, sort = 0)
        adapter.submit(list)
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showItemDialog(memo: Memo) {
        val actions = arrayOf(getString(R.string.restore), getString(R.string.delete_forever))
        MaterialAlertDialogBuilder(this)
            .setTitle(memo.title.ifEmpty { getString(R.string.no_title) })
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> {
                        MemoDbHelper.get(this).restore(memo.id)
                        // 미래 시각 알림은 복원과 함께 다시 등록
                        if (memo.reminder > System.currentTimeMillis()) {
                            ReminderScheduler.schedule(this, memo.id, memo.reminder)
                        }
                        Toast.makeText(this, R.string.restored, Toast.LENGTH_SHORT).show()
                        reload()
                    }
                    1 -> confirmDeleteForever(memo)
                }
            }
            .show()
    }

    private fun confirmDeleteForever(memo: Memo) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_forever)
            .setMessage(R.string.delete_forever_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                MemoDbHelper.get(this).deleteForever(memo.id)
                reload()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_trash, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        R.id.action_empty_trash -> {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.empty_trash)
                .setMessage(R.string.empty_trash_confirm)
                .setPositiveButton(R.string.delete) { _, _ ->
                    MemoDbHelper.get(this).emptyTrash()
                    reload()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
