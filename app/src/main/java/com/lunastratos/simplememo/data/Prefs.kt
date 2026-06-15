package com.lunastratos.simplememo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/** 앱 설정(테마, 글자 크기, 정렬) 저장소 */
object Prefs {

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    const val SORT_UPDATED = 0
    const val SORT_CREATED_DESC = 1
    const val SORT_CREATED_ASC = 2
    const val SORT_TITLE = 3
    const val SORT_MANUAL = 4

    /** 글자 크기 인덱스(작게/보통/크게/아주 크게)에 대응하는 sp 값 */
    val FONT_SIZES_SP = intArrayOf(14, 16, 18, 22)

    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun theme(context: Context): Int = sp(context).getInt(KEY_THEME, THEME_SYSTEM)

    fun setTheme(context: Context, value: Int) {
        sp(context).edit().putInt(KEY_THEME, value).apply()
        applyTheme(context)
    }

    /** 저장된 테마 설정을 AppCompat 야간 모드에 반영 */
    fun applyTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme(context)) {
                THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    fun fontSizeIndex(context: Context): Int =
        sp(context).getInt(KEY_FONT, 1).coerceIn(0, FONT_SIZES_SP.lastIndex)

    fun setFontSizeIndex(context: Context, value: Int) {
        sp(context).edit().putInt(KEY_FONT, value).apply()
    }

    fun fontSizeSp(context: Context): Float = FONT_SIZES_SP[fontSizeIndex(context)].toFloat()

    fun sort(context: Context): Int = sp(context).getInt(KEY_SORT, SORT_UPDATED)

    fun setSort(context: Context, value: Int) {
        sp(context).edit().putInt(KEY_SORT, value).apply()
    }

    fun appLock(context: Context): Boolean = sp(context).getBoolean(KEY_APP_LOCK, false)

    fun setAppLock(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_APP_LOCK, value).apply()
    }

    fun autoBackup(context: Context): Boolean = sp(context).getBoolean(KEY_AUTO_BACKUP, false)

    fun setAutoBackup(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_AUTO_BACKUP, value).apply()
    }

    private const val KEY_THEME = "theme"
    private const val KEY_FONT = "fontSize"
    private const val KEY_SORT = "sort"
    private const val KEY_APP_LOCK = "appLock"
    private const val KEY_AUTO_BACKUP = "autoBackup"
}
