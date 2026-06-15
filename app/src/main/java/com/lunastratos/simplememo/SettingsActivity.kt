package com.lunastratos.simplememo

import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lunastratos.simplememo.data.MemoBackup
import com.lunastratos.simplememo.data.MemoDbHelper
import com.lunastratos.simplememo.data.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 설정 화면: 테마, 글자 크기, 정렬, 백업/복원 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var themeValue: TextView
    private lateinit var fontValue: TextView
    private lateinit var sortValue: TextView
    private lateinit var appLockValue: TextView
    private lateinit var autoBackupValue: TextView

    private val themeLabels by lazy {
        arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
        )
    }
    private val fontLabels by lazy {
        arrayOf(
            getString(R.string.font_small),
            getString(R.string.font_normal),
            getString(R.string.font_large),
            getString(R.string.font_xlarge),
        )
    }
    private val sortLabels by lazy {
        arrayOf(
            getString(R.string.sort_updated),
            getString(R.string.sort_created_desc),
            getString(R.string.sort_created_asc),
            getString(R.string.sort_title),
            getString(R.string.sort_manual),
        )
    }

    private val backupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { exportBackup(it) }
        }

    private val restoreLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { confirmAndRestore(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        themeValue = findViewById(R.id.theme_value)
        fontValue = findViewById(R.id.font_value)
        sortValue = findViewById(R.id.sort_value)
        appLockValue = findViewById(R.id.app_lock_value)
        autoBackupValue = findViewById(R.id.auto_backup_value)

        findViewById<TextView>(R.id.version_value).text =
            packageManager.getPackageInfo(packageName, 0).versionName

        findViewById<android.view.View>(R.id.row_theme).setOnClickListener {
            showChoiceDialog(R.string.setting_theme, themeLabels, Prefs.theme(this)) {
                Prefs.setTheme(this, it)
            }
        }
        findViewById<android.view.View>(R.id.row_font).setOnClickListener {
            showChoiceDialog(R.string.setting_font_size, fontLabels, Prefs.fontSizeIndex(this)) {
                Prefs.setFontSizeIndex(this, it)
            }
        }
        findViewById<android.view.View>(R.id.row_sort).setOnClickListener {
            showChoiceDialog(R.string.setting_sort, sortLabels, Prefs.sort(this)) {
                Prefs.setSort(this, it)
            }
        }
        findViewById<android.view.View>(R.id.row_app_lock).setOnClickListener { toggleAppLock() }
        findViewById<android.view.View>(R.id.row_auto_backup).setOnClickListener {
            toggleAutoBackup()
        }
        findViewById<android.view.View>(R.id.row_backup).setOnClickListener {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            backupLauncher.launch("simplememo-backup-$stamp.json")
        }
        findViewById<android.view.View>(R.id.row_restore).setOnClickListener {
            restoreLauncher.launch(
                arrayOf("application/json", "application/octet-stream", "text/plain")
            )
        }
        refreshValues()
    }

    /** 앱 잠금 토글. 켤 때는 기기에 인증 수단이 있는지 먼저 확인한다. */
    private fun toggleAppLock() {
        val enable = !Prefs.appLock(this)
        if (enable) {
            val can = BiometricManager.from(this)
                .canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            if (can != BiometricManager.BIOMETRIC_SUCCESS) {
                Toast.makeText(this, R.string.app_lock_unavailable, Toast.LENGTH_LONG).show()
                return
            }
        }
        Prefs.setAppLock(this, enable)
        refreshValues()
    }

    private fun toggleAutoBackup() {
        val enable = !Prefs.autoBackup(this)
        Prefs.setAutoBackup(this, enable)
        if (enable) AutoBackupWorker.schedule(this) else AutoBackupWorker.cancel(this)
        refreshValues()
    }

    private fun exportBackup(uri: Uri) {
        runCatching {
            val json = MemoBackup.toJson(MemoDbHelper.get(this).allMemos())
            contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("출력 스트림을 열 수 없음")
        }.onSuccess {
            Toast.makeText(this, R.string.backup_done, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmAndRestore(uri: Uri) {
        val memos = runCatching {
            val text = contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: error("입력 스트림을 열 수 없음")
            MemoBackup.fromJson(text)
        }.getOrElse {
            Toast.makeText(this, R.string.restore_failed, Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.setting_restore)
            .setMessage(R.string.restore_confirm)
            .setPositiveButton(R.string.restore) { _, _ ->
                MemoDbHelper.get(this).replaceAll(memos)
                Toast.makeText(
                    this, getString(R.string.restore_done, memos.size), Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showChoiceDialog(
        titleRes: Int,
        labels: Array<String>,
        checked: Int,
        onSelect: (Int) -> Unit,
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                onSelect(which)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refreshValues() {
        themeValue.text = themeLabels[Prefs.theme(this).coerceIn(themeLabels.indices)]
        fontValue.text = fontLabels[Prefs.fontSizeIndex(this)]
        sortValue.text = sortLabels[Prefs.sort(this).coerceIn(sortLabels.indices)]
        appLockValue.setText(if (Prefs.appLock(this)) R.string.on else R.string.off)
        autoBackupValue.setText(
            if (Prefs.autoBackup(this)) R.string.auto_backup_on else R.string.off
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (item.itemId == android.R.id.home) {
            finish()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
}
