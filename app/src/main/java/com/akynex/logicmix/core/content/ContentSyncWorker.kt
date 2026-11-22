package com.akynex.logicmix.core.content

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Адрес публичного канала пакетов (https://storage.googleapis.com/<bucket>/). Пусто = синхронизация выключена. */
object ContentSyncConfig {
    const val BASE_URL = ""
    const val CHANNEL = "staging"
    /** Языки/типы, выбранные пользователем; не скачиваем все восемь языков. */
    val wantedLanguages = setOf("en", "ru")
    val wantedTypes = setOf("wordsearch", "crossword")
}

/** Периодическая (~24 ч, система может сдвигать запуск) загрузка пакетов. Ошибки не влияют на игру: офлайн → встроенные данные. */
class ContentSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (ContentSyncConfig.BASE_URL.isEmpty()) return Result.success()
        val dir = File(applicationContext.filesDir, "content")
        val report = try {
            PackSync.sync(
                fetch = ::download, store = PackStore(dir), channel = ContentSyncConfig.CHANNEL,
                wanted = { it.language in ContentSyncConfig.wantedLanguages && it.type in ContentSyncConfig.wantedTypes },
                freeBytes = { dir.apply { mkdirs() }.usableSpace },
            )
        } catch (e: Exception) {
            com.akynex.logicmix.core.monetization.AppServices.nonFatal(
                com.akynex.logicmix.core.analytics.CrashKind.PACK_VALIDATION, e)
            return Result.retry()
        }
        return if (report.failed.isEmpty()) Result.success() else Result.retry()
    }

    private fun download(key: String): ByteArray? = try {
        val c = URL(ContentSyncConfig.BASE_URL + key).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000
        if (c.responseCode == 200) c.inputStream.use { it.readNBytes((MAX_PACK_BYTES + 1).toInt()) } else null
    } catch (e: Exception) { null }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<ContentSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresStorageNotLow(true).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("content-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
