package com.lunastratos.simplememo

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lunastratos.simplememo.data.Memo
import com.lunastratos.simplememo.data.MemoDbHelper
import com.lunastratos.simplememo.data.Prefs
import com.lunastratos.simplememo.util.Formats
import com.lunastratos.simplememo.util.ReminderScheduler
import java.util.Calendar

/**
 * 메모 편집 화면. 저장 버튼 없이 화면을 벗어날 때 자동 저장한다.
 * 빈 메모는 저장하지 않고, 기존 메모를 비우면 삭제한다.
 * 실행 취소/다시 실행, 체크리스트, 고정, 알림을 지원한다.
 */
class EditActivity : AppCompatActivity() {

    private lateinit var editor: EditText
    private lateinit var info: TextView
    private var memoId: Long = NO_ID
    private var savedBody: String = ""
    private var updatedAt: Long = 0L
    private var skipSave = false
    private var pinned = false
    private var reminderAt = 0L

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var editingHistory = false
    private var lastText = ""

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        editor = findViewById(R.id.editor)
        info = findViewById(R.id.edit_info)
        editor.textSize = Prefs.fontSizeSp(this)

        memoId = intent.getLongExtra(EXTRA_ID, NO_ID)
        if (memoId != NO_ID) {
            val memo = MemoDbHelper.get(this).get(memoId)
            if (memo == null) {
                memoId = NO_ID
            } else {
                savedBody = memo.body
                updatedAt = memo.updated
                pinned = memo.pinned
                reminderAt = memo.reminder
                editor.setText(memo.body)
                editor.setSelection(memo.body.length)
            }
        }
        if (memoId == NO_ID) {
            // 새 메모는 바로 입력할 수 있게 키보드를 띄운다
            editor.requestFocus()
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        lastText = editor.text.toString()

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                updateInfo()
                recordHistory(s?.toString().orEmpty())
            }
        })
        updateInfo()
    }

    // ----- 실행 취소 / 다시 실행 -----

    private fun recordHistory(text: String) {
        if (editingHistory || text == lastText) return
        undoStack.addLast(lastText)
        if (undoStack.size > HISTORY_LIMIT) undoStack.removeFirst()
        redoStack.clear()
        lastText = text
        invalidateOptionsMenu()
    }

    private fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(editor.text.toString())
        applyHistory(prev)
    }

    private fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(editor.text.toString())
        applyHistory(next)
    }

    private fun applyHistory(text: String) {
        editingHistory = true
        editor.setText(text)
        editor.setSelection(text.length)
        editingHistory = false
        lastText = text
        invalidateOptionsMenu()
    }

    // ----- 체크리스트 -----

    /**
     * 커서가 놓인 줄(또는 선택된 여러 줄)의 체크리스트 상태를 순환시킨다:
     * 일반 → 미완료(- [ ]) → 완료(- [x]) → 일반
     */
    private fun toggleChecklist() {
        val text = editor.text.toString()
        val selStart = editor.selectionStart.coerceAtLeast(0)
        val selEnd = editor.selectionEnd.coerceAtLeast(selStart)
        val blockStart = text.lastIndexOf('\n', selStart - 1) + 1
        val blockEnd = text.indexOf('\n', selEnd).let { if (it < 0) text.length else it }

        val converted = text.substring(blockStart, blockEnd).split("\n").joinToString("\n") {
            when {
                it.startsWith(Memo.CHECK_DONE) ->
                    it.removePrefix(Memo.CHECK_DONE).trimStart()
                it.startsWith(Memo.CHECK_TODO) ->
                    Memo.CHECK_DONE + it.removePrefix(Memo.CHECK_TODO)
                else -> "${Memo.CHECK_TODO} $it"
            }
        }
        editor.text.replace(blockStart, blockEnd, converted)
        editor.setSelection((blockStart + converted.length).coerceAtMost(editor.text.length))
    }

    // ----- 고정 / 알림 -----

    /** 본문이 저장돼 있어야 하는 동작 전에 호출. 저장 후에도 빈 메모면 false. */
    private fun ensureSaved(): Boolean {
        save()
        if (memoId == NO_ID) {
            Toast.makeText(this, R.string.needs_content, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun togglePin() {
        if (!ensureSaved()) return
        pinned = !pinned
        MemoDbHelper.get(this).setPinned(listOf(memoId), pinned)
        Toast.makeText(this, if (pinned) R.string.pinned else R.string.unpinned, Toast.LENGTH_SHORT)
            .show()
        invalidateOptionsMenu()
    }

    private fun showReminderDialog() {
        if (!ensureSaved()) return
        if (reminderAt > System.currentTimeMillis()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reminder)
                .setMessage(Formats.dateTime(reminderAt))
                .setPositiveButton(R.string.change) { _, _ -> pickReminderDateTime() }
                .setNegativeButton(R.string.reminder_remove) { _, _ -> clearReminder() }
                .setNeutralButton(R.string.cancel, null)
                .show()
        } else {
            pickReminderDateTime()
        }
    }

    private fun pickReminderDateTime() {
        val cal = Calendar.getInstance().apply {
            if (reminderAt > System.currentTimeMillis()) {
                timeInMillis = reminderAt
            } else {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
            }
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                cal.set(year, month, day)
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, minute)
                        setReminderAt(cal.timeInMillis)
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(this),
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun setReminderAt(time: Long) {
        if (time <= System.currentTimeMillis()) {
            Toast.makeText(this, R.string.reminder_past, Toast.LENGTH_SHORT).show()
            return
        }
        requestNotificationPermissionIfNeeded()
        reminderAt = time
        MemoDbHelper.get(this).setReminder(memoId, time)
        ReminderScheduler.schedule(this, memoId, time)
        Toast.makeText(this, getString(R.string.reminder_set, Formats.dateTime(time)), Toast.LENGTH_SHORT)
            .show()
        warnIfExactAlarmUnavailable()
    }

    /** 정확한 알람 권한이 없으면 알림이 지연될 수 있음을 알리고 설정으로 안내한다 (API 31+) */
    private fun warnIfExactAlarmUnavailable() {
        if (Build.VERSION.SDK_INT < 31) return
        val am = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
        if (am.canScheduleExactAlarms()) return
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.reminder_inexact)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                runCatching {
                    startActivity(
                        Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearReminder() {
        reminderAt = 0L
        MemoDbHelper.get(this).setReminder(memoId, 0L)
        ReminderScheduler.cancel(this, memoId)
        Toast.makeText(this, R.string.reminder_removed, Toast.LENGTH_SHORT).show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ----- 저장 -----

    private fun updateInfo() {
        val count = getString(R.string.char_count, editor.text.length)
        info.text = if (updatedAt > 0) "${Formats.dateTime(updatedAt)}  ·  $count" else count
    }

    override fun onPause() {
        super.onPause()
        if (!skipSave) save()
    }

    private fun save() {
        val body = editor.text.toString()
        val db = MemoDbHelper.get(this)
        when {
            memoId == NO_ID -> if (body.isNotBlank()) {
                memoId = db.insert(body)
                updatedAt = System.currentTimeMillis()
            }
            body != savedBody -> if (body.isBlank()) {
                ReminderScheduler.cancel(this, memoId)
                db.deleteForever(memoId)
                memoId = NO_ID
                updatedAt = 0L
                reminderAt = 0L
                pinned = false
                Toast.makeText(this, R.string.blank_deleted, Toast.LENGTH_SHORT).show()
            } else {
                db.update(memoId, body)
                updatedAt = System.currentTimeMillis()
            }
        }
        savedBody = body
    }

    // ----- 메뉴 -----

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_edit, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_undo)?.isEnabled = undoStack.isNotEmpty()
        menu.findItem(R.id.action_redo)?.isEnabled = redoStack.isNotEmpty()
        menu.findItem(R.id.action_pin)?.setTitle(if (pinned) R.string.unpin else R.string.pin)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        R.id.action_undo -> {
            undo()
            true
        }
        R.id.action_redo -> {
            redo()
            true
        }
        R.id.action_checklist -> {
            toggleChecklist()
            true
        }
        R.id.action_pin -> {
            togglePin()
            true
        }
        R.id.action_reminder -> {
            showReminderDialog()
            true
        }
        R.id.action_share -> {
            shareMemo()
            true
        }
        R.id.action_delete -> {
            confirmDelete()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun shareMemo() {
        val body = editor.text.toString()
        if (body.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (memoId != NO_ID) {
                    ReminderScheduler.cancel(this, memoId)
                    MemoDbHelper.get(this).moveToTrash(memoId)
                }
                skipSave = true
                Toast.makeText(this, R.string.deleted_to_trash, Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_ID = "memoId"
        private const val NO_ID = -1L
        private const val HISTORY_LIMIT = 100
    }
}
