package com.lunastratos.simplememo.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

import com.lunastratos.simplememo.MemoWidget

/** 메모 저장소. 단일 테이블 SQLite 로 메모와 휴지통을 함께 관리한다. */
class MemoDbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private val appContext = context.applicationContext

    /** 쓰기 작업 후 호출. 홈 화면 위젯 목록을 갱신한다. */
    private fun notifyDataChanged() = MemoWidget.notifyChanged(appContext)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_BODY TEXT NOT NULL,
                $COL_CREATED INTEGER NOT NULL,
                $COL_UPDATED INTEGER NOT NULL,
                $COL_DELETED INTEGER NOT NULL DEFAULT 0,
                $COL_COLOR INTEGER NOT NULL DEFAULT 0,
                $COL_POSITION INTEGER NOT NULL DEFAULT 0,
                $COL_PINNED INTEGER NOT NULL DEFAULT 0,
                $COL_REMINDER INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_COLOR INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_POSITION INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_PINNED INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_REMINDER INTEGER NOT NULL DEFAULT 0")
        }
    }

    fun insert(body: String, now: Long = System.currentTimeMillis()): Long {
        val values = ContentValues().apply {
            put(COL_BODY, body)
            put(COL_CREATED, now)
            put(COL_UPDATED, now)
            put(COL_DELETED, 0L)
            // 직접 정렬순에서 새 메모가 맨 위에 오도록 가장 작은 position 보다 앞에 둔다
            put(COL_POSITION, minPosition() - 1)
        }
        return writableDatabase.insert(TABLE, null, values).also { notifyDataChanged() }
    }

    private fun minPosition(): Long =
        readableDatabase.rawQuery("SELECT MIN($COL_POSITION) FROM $TABLE", null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        }

    fun update(id: Long, body: String, now: Long = System.currentTimeMillis()) {
        val values = ContentValues().apply {
            put(COL_BODY, body)
            put(COL_UPDATED, now)
        }
        writableDatabase.update(TABLE, values, "$COL_ID = ?", arrayOf(id.toString()))
        notifyDataChanged()
    }

    fun get(id: Long): Memo? =
        readableDatabase.query(
            TABLE, null, "$COL_ID = ?", arrayOf(id.toString()), null, null, null
        ).use { c -> if (c.moveToFirst()) c.toMemo() else null }

    /**
     * 메모 목록 조회. 일반 메모는 고정(핀)이 항상 먼저 온다.
     * @param inTrash true 면 휴지통 메모(삭제 최신순), false 면 일반 메모
     * @param query 본문 부분 일치 검색어
     * @param sort [Prefs.SORT_UPDATED] 등 정렬 모드 (일반 메모에만 적용)
     * @param color 0이 아니면 해당 색상의 메모만
     * @param tag null 이 아니면 본문에 #tag 가 포함된 메모만
     */
    fun list(
        inTrash: Boolean,
        query: String?,
        sort: Int,
        color: Int = 0,
        tag: String? = null,
    ): List<Memo> {
        val selection = StringBuilder(if (inTrash) "$COL_DELETED > 0" else "$COL_DELETED = 0")
        val args = mutableListOf<String>()
        if (!query.isNullOrBlank()) {
            selection.append(" AND $COL_BODY LIKE ?")
            args.add("%${query.trim()}%")
        }
        if (color != 0) {
            selection.append(" AND $COL_COLOR = ?")
            args.add(color.toString())
        }
        if (!tag.isNullOrBlank()) {
            selection.append(" AND $COL_BODY LIKE ?")
            args.add("%#${tag.trim()}%")
        }
        val orderBy = if (inTrash) "$COL_DELETED DESC" else "$COL_PINNED DESC, " + when (sort) {
            Prefs.SORT_CREATED_DESC -> "$COL_CREATED DESC"
            Prefs.SORT_CREATED_ASC -> "$COL_CREATED ASC"
            Prefs.SORT_TITLE -> "$COL_BODY COLLATE LOCALIZED ASC"
            Prefs.SORT_MANUAL -> "$COL_POSITION ASC, $COL_UPDATED DESC"
            else -> "$COL_UPDATED DESC"
        }
        return readableDatabase.query(
            TABLE, null, selection.toString(), args.toTypedArray(), null, null, orderBy
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(c.toMemo())
            }
        }
    }

    /** 휴지통을 제외한 메모 본문에서 수집한 #태그 목록(가나다순) */
    fun allTags(): List<String> =
        readableDatabase.query(
            TABLE, arrayOf(COL_BODY), "$COL_DELETED = 0", null, null, null, null
        ).use { c ->
            val tags = sortedSetOf(String.CASE_INSENSITIVE_ORDER)
            while (c.moveToNext()) {
                Memo.TAG_REGEX.findAll(c.getString(0)).forEach { tags.add(it.groupValues[1]) }
            }
            tags.toList()
        }

    /** 메모 색상 변경. color 는 ARGB 값, 0이면 색상 해제. */
    fun setColor(id: Long, color: Int) = setColor(listOf(id), color)

    fun setColor(ids: List<Long>, color: Int) {
        val values = ContentValues().apply { put(COL_COLOR, color) }
        updateAll(ids, values)
    }

    /** 메모 고정/해제 */
    fun setPinned(ids: List<Long>, pinned: Boolean) {
        val values = ContentValues().apply { put(COL_PINNED, if (pinned) 1L else 0L) }
        updateAll(ids, values)
    }

    /** 알림 시각 설정. 0이면 알림 해제. */
    fun setReminder(id: Long, time: Long) {
        val values = ContentValues().apply { put(COL_REMINDER, time) }
        writableDatabase.update(TABLE, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    /** 아직 울리지 않은 알림이 있는 일반 메모 (부팅 후 재예약용) */
    fun pendingReminders(): List<Memo> =
        readableDatabase.query(
            TABLE, null, "$COL_REMINDER > 0 AND $COL_DELETED = 0", null, null, null, null
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(c.toMemo())
            }
        }

    private fun updateAll(ids: List<Long>, values: ContentValues) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.forEach { id ->
                db.update(TABLE, values, "$COL_ID = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyDataChanged()
    }

    /** 드래그로 정한 순서를 저장한다. 리스트 순서대로 position 을 0부터 다시 매긴다. */
    fun reorder(orderedIds: List<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            orderedIds.forEachIndexed { index, id ->
                val values = ContentValues().apply { put(COL_POSITION, index.toLong()) }
                db.update(TABLE, values, "$COL_ID = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyDataChanged()
    }

    fun moveToTrash(id: Long, now: Long = System.currentTimeMillis()) =
        moveToTrash(listOf(id), now)

    fun moveToTrash(ids: List<Long>, now: Long = System.currentTimeMillis()) {
        val values = ContentValues().apply { put(COL_DELETED, now) }
        updateAll(ids, values)
    }

    fun restore(id: Long) {
        val values = ContentValues().apply { put(COL_DELETED, 0L) }
        writableDatabase.update(TABLE, values, "$COL_ID = ?", arrayOf(id.toString()))
        notifyDataChanged()
    }

    fun deleteForever(id: Long) {
        writableDatabase.delete(TABLE, "$COL_ID = ?", arrayOf(id.toString()))
        notifyDataChanged()
    }

    fun emptyTrash() {
        writableDatabase.delete(TABLE, "$COL_DELETED > 0", null)
        notifyDataChanged()
    }

    /** 휴지통 포함 전체 메모. 백업용. */
    fun allMemos(): List<Memo> =
        readableDatabase.query(TABLE, null, null, null, null, null, "$COL_ID ASC").use { c ->
            buildList {
                while (c.moveToNext()) add(c.toMemo())
            }
        }

    /** 전체 메모를 주어진 목록으로 교체한다. 복원용. */
    fun replaceAll(memos: List<Memo>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE, null, null)
            memos.forEach { memo ->
                val values = ContentValues().apply {
                    put(COL_BODY, memo.body)
                    put(COL_CREATED, memo.created)
                    put(COL_UPDATED, memo.updated)
                    put(COL_DELETED, memo.deleted)
                    put(COL_COLOR, memo.color)
                    put(COL_POSITION, memo.position)
                    put(COL_PINNED, if (memo.pinned) 1L else 0L)
                    put(COL_REMINDER, memo.reminder)
                }
                db.insert(TABLE, null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyDataChanged()
    }

    private fun Cursor.toMemo() = Memo(
        id = getLong(getColumnIndexOrThrow(COL_ID)),
        body = getString(getColumnIndexOrThrow(COL_BODY)),
        created = getLong(getColumnIndexOrThrow(COL_CREATED)),
        updated = getLong(getColumnIndexOrThrow(COL_UPDATED)),
        deleted = getLong(getColumnIndexOrThrow(COL_DELETED)),
        color = getInt(getColumnIndexOrThrow(COL_COLOR)),
        position = getLong(getColumnIndexOrThrow(COL_POSITION)),
        pinned = getLong(getColumnIndexOrThrow(COL_PINNED)) > 0,
        reminder = getLong(getColumnIndexOrThrow(COL_REMINDER)),
    )

    companion object {
        private const val DB_NAME = "memo.db"
        private const val DB_VERSION = 3
        private const val TABLE = "memos"
        private const val COL_ID = "id"
        private const val COL_BODY = "body"
        private const val COL_CREATED = "created"
        private const val COL_UPDATED = "updated"
        private const val COL_DELETED = "deleted"
        private const val COL_COLOR = "color"
        private const val COL_POSITION = "position"
        private const val COL_PINNED = "pinned"
        private const val COL_REMINDER = "reminder"

        @Volatile
        private var instance: MemoDbHelper? = null

        fun get(context: Context): MemoDbHelper =
            instance ?: synchronized(this) {
                instance ?: MemoDbHelper(context.applicationContext).also { instance = it }
            }
    }
}
