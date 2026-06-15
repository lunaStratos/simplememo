package com.lunastratos.simplememo

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.lunastratos.simplememo.data.MemoBackup
import com.lunastratos.simplememo.data.MemoDbHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 하루 한 번 메모 전체를 앱 전용 폴더(Android/data/.../files/backups)에
 * JSON 으로 저장한다. 최근 [KEEP_COUNT]개만 유지한다.
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result = runCatching {
        val dir = File(
            applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir,
            BACKUP_DIR
        ).apply { mkdirs() }

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val json = MemoBackup.toJson(MemoDbHelper.get(applicationContext).allMemos())
        File(dir, "simplememo-auto-$stamp.json").writeText(json, Charsets.UTF_8)

        // 오래된 백업 정리
        dir.listFiles { f -> f.name.startsWith("simplememo-auto-") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP_COUNT)
            ?.forEach { it.delete() }
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )

    companion object {
        private const val WORK_NAME = "auto_backup"
        private const val BACKUP_DIR = "backups"
        private const val KEEP_COUNT = 7

        fun schedule(context: Context) {
            val request = PeriodicWorkRequest.Builder(AutoBackupWorker::class.java, 1, TimeUnit.DAYS)
                .build()
            // KEEP: 앱 시작 시마다 호출되므로 REPLACE 를 쓰면 주기가 매번 리셋되어
            // 백업이 영영 실행되지 않을 수 있다. 끄고 켤 때는 cancel 후 재등록된다.
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
