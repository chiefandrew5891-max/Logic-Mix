package io.github.chiefandrew5891max.logicmix.core.content

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Контракт: LogicMIX-backend/docs/CONTRACT.md. Чистый Kotlin/JVM — без Android API, тестируется на JVM. */
const val SUPPORTED_SCHEMA = 1
const val CLIENT_CONTENT_VERSION = 1
const val MAX_PACK_BYTES = 8L * 1024 * 1024

data class PackInfo(
    val packId: String, val type: String, val language: String, val dataVersion: Int,
    val path: String, val sha256: String, val size: Long, val minClientVersion: Int,
)

data class PackRow(val word: String, val clue: String?, val tier: Int)

class PackException(msg: String) : Exception(msg)

object PackManifest {
    fun parse(bytes: ByteArray): List<PackInfo> {
        val o = JSONObject(String(bytes, Charsets.UTF_8))
        if (o.optInt("schema", -1) != SUPPORTED_SCHEMA) throw PackException("unsupported manifest schema")
        val a: JSONArray = o.getJSONArray("packs")
        return (0 until a.length()).map {
            val p = a.getJSONObject(it)
            PackInfo(
                p.getString("packId"), p.getString("type"), p.getString("language"), p.getInt("dataVersion"),
                p.getString("path"), p.getString("sha256"), p.optLong("size", 0), p.optInt("minClientVersion", 1),
            )
        }
    }
}

object PackFormat {
    fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /** Полная проверка: SHA-256, распаковка, схема, версия клиента, язык и тип. Возвращает строки пакета. */
    fun validate(gz: ByteArray, info: PackInfo): List<PackRow> {
        if (gz.size > MAX_PACK_BYTES) throw PackException("pack too large")
        if (sha256(gz) != info.sha256.lowercase()) throw PackException("checksum mismatch")
        val doc = try {
            JSONObject(String(GZIPInputStream(ByteArrayInputStream(gz)).readBytes(), Charsets.UTF_8))
        } catch (e: Exception) {
            throw PackException("corrupted pack: ${e.message}")
        }
        if (doc.optInt("schema", -1) != SUPPORTED_SCHEMA) throw PackException("unsupported pack schema")
        if (doc.optInt("minClientVersion", 1) > CLIENT_CONTENT_VERSION) throw PackException("client too old")
        if (doc.getString("language") != info.language || doc.getString("type") != info.type ||
            doc.getInt("dataVersion") != info.dataVersion
        ) throw PackException("pack does not match manifest")
        val rows = doc.getJSONArray("rows")
        return (0 until rows.length()).map {
            val r = rows.getJSONArray(it)
            when (info.type) {
                "crossword" -> PackRow(r.getString(0), r.getString(1), r.getInt(2))
                else -> PackRow(r.getString(0), null, r.optInt(1, 2))
            }
        }
    }
}

/**
 * Локальное хранилище пакетов. Файл `<root>/packs/<id>/<version>.json.gz` появляется целиком (tmp → rename),
 * указатель `installed.json` переключается последним. Старые версии не удаляются, пока их использует незавершённая игра.
 */
class PackStore(private val root: File) {
    private val pointer get() = File(root, "installed.json")

    fun installedVersions(): Map<String, Pair<Int, String>> {
        if (!pointer.exists()) return emptyMap()
        return try {
            val o = JSONObject(pointer.readText())
            o.keys().asSequence().associateWith { o.getJSONObject(it).let { j -> j.getInt("v") to j.getString("sha") } }
        } catch (e: Exception) {
            emptyMap()   // повреждённый указатель: работаем со встроенными данными
        }
    }

    fun file(packId: String, version: Int) = File(root, "packs/$packId/$version.json.gz")

    fun install(info: PackInfo, gz: ByteArray) {
        val target = file(info.packId, info.dataVersion)
        target.parentFile!!.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(gz)
        if (!tmp.renameTo(target)) { tmp.delete(); throw PackException("rename failed") }
        val state = installedVersions().toMutableMap()
        state[info.packId] = info.dataVersion to info.sha256
        val json = JSONObject()
        state.forEach { (k, v) -> json.put(k, JSONObject().put("v", v.first).put("sha", v.second)) }
        val ptmp = File(root, "installed.json.tmp")
        root.mkdirs()
        ptmp.writeText(json.toString())
        if (!ptmp.renameTo(pointer)) { ptmp.delete(); throw PackException("pointer switch failed") }
    }

    /** Строки для новой игры: активная версия или null → вызывающий код использует встроенные данные. */
    fun activeRows(info: PackInfo): List<PackRow>? = pinnedRows(info, installedVersions()[info.packId]?.first)

    /** Незавершённая игра читает ту версию словаря, на которой была создана. */
    fun pinnedRows(info: PackInfo, version: Int?): List<PackRow>? {
        if (version == null) return null
        val f = file(info.packId, version)
        if (!f.exists()) return null
        val bytes = f.readBytes()
        return try { PackFormat.validate(bytes, info.copy(dataVersion = version, sha256 = PackFormat.sha256(bytes))) } catch (e: Exception) { null }
    }
}

class SyncReport(val installed: MutableList<String> = mutableListOf(), val skipped: MutableList<String> = mutableListOf(),
                 val failed: MutableList<Pair<String, String>> = mutableListOf())

object PackSync {
    /**
     * fetch(relativeKey) → байты или null (нет сети/ошибка). Любая ошибка оставляет прежнюю рабочую версию.
     * wanted — только выбранные пользователем языки/типы (не качаем всё).
     */
    fun sync(
        fetch: (String) -> ByteArray?, store: PackStore, channel: String,
        wanted: (PackInfo) -> Boolean, freeBytes: () -> Long,
    ): SyncReport {
        val report = SyncReport()
        val packs = try {
            PackManifest.parse(fetch("public/$channel/v1/manifest.json") ?: throw PackException("manifest unavailable"))
        } catch (e: Exception) {
            report.failed += "manifest" to (e.message ?: "error"); return report
        }
        val state = store.installedVersions()
        for (p in packs.filter(wanted)) {
            val cur = state[p.packId]
            if (cur != null && cur.first >= p.dataVersion) { report.skipped += p.packId; continue }
            try {
                if (p.minClientVersion > CLIENT_CONTENT_VERSION) throw PackException("client too old")
                if (freeBytes() < p.size * 3 + 1_000_000) throw PackException("not enough space")
                val data = fetch("public/$channel/v1/${p.path}") ?: throw PackException("download failed")
                PackFormat.validate(data, p)
                store.install(p, data)
                report.installed += p.packId
            } catch (e: Exception) {
                report.failed += p.packId to (e.message ?: "error")
            }
        }
        return report
    }
}
