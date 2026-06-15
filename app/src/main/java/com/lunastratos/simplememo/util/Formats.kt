package com.lunastratos.simplememo.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 날짜 표시 포맷 유틸 */
object Formats {

    /** 목록용: 오늘이면 시각, 올해면 월.일, 그 외에는 연.월.일 */
    fun listDate(time: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = time }
        val sameYear = now[Calendar.YEAR] == target[Calendar.YEAR]
        val sameDay = sameYear && now[Calendar.DAY_OF_YEAR] == target[Calendar.DAY_OF_YEAR]
        val pattern = when {
            sameDay -> "HH:mm"
            sameYear -> "M.d"
            else -> "yyyy.M.d"
        }
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
    }

    /** 편집 화면용 전체 날짜·시각 */
    fun dateTime(time: Long): String =
        SimpleDateFormat("yyyy.M.d HH:mm", Locale.getDefault()).format(Date(time))
}
