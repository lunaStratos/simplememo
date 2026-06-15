package com.lunastratos.simplememo.data

import org.json.JSONArray
import org.json.JSONObject

/** 메모 전체를 JSON 으로 내보내고 다시 읽어 들이는 백업 직렬화. */
object MemoBackup {

    private const val FORMAT_VERSION = 1

    fun toJson(memos: List<Memo>): String {
        val arr = JSONArray()
        memos.forEach { memo ->
            arr.put(JSONObject().apply {
                put("body", memo.body)
                put("created", memo.created)
                put("updated", memo.updated)
                put("deleted", memo.deleted)
                put("color", memo.color)
                put("position", memo.position)
                put("pinned", memo.pinned)
                put("reminder", memo.reminder)
            })
        }
        return JSONObject().apply {
            put("app", "simplememo")
            put("version", FORMAT_VERSION)
            put("memos", arr)
        }.toString(2)
    }

    /** 형식이 잘못된 경우 [org.json.JSONException] 을 던진다. */
    fun fromJson(text: String): List<Memo> {
        val arr = JSONObject(text).getJSONArray("memos")
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    Memo(
                        id = 0L,
                        body = o.getString("body"),
                        created = o.optLong("created"),
                        updated = o.optLong("updated", o.optLong("created")),
                        deleted = o.optLong("deleted"),
                        color = o.optInt("color"),
                        position = o.optLong("position"),
                        pinned = o.optBoolean("pinned"),
                        reminder = o.optLong("reminder"),
                    )
                )
            }
        }
    }
}
