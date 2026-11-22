package io.github.chiefandrew5891max.logicmix.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PackSyncTest {
    @get:Rule val tmp = TemporaryFolder()

    // Образец собран реальным backend (Hunspell ru_RU + wordfreq), см. resources/contract
    private val root = File(javaClass.getResource("/contract/staging/v1/manifest.json")!!.toURI()).parentFile.parentFile.parentFile
    private val served = HashMap<String, ByteArray?>()

    private fun fetch(key: String): ByteArray? =
        if (served.containsKey(key)) served[key] else File(root, key.removePrefix("public/staging/").let { "staging/$it" }).takeIf { it.exists() }?.readBytes()

    private val ruWordsearch: (PackInfo) -> Boolean = { it.language == "ru" && it.type == "wordsearch" }

    @Test fun installsRealBackendPackAndReadsRows() {
        val store = PackStore(tmp.newFolder())
        val r = PackSync.sync(::fetch, store, "staging", ruWordsearch) { 1L shl 30 }
        assertEquals(listOf("wordsearch-ru"), r.installed)
        val info = PackManifest.parse(fetch("public/staging/v1/manifest.json")!!).single()
        val rows = store.activeRows(info)!!
        assertTrue(rows.size > 100 && rows.all { it.word == it.word.uppercase() })
        val again = PackSync.sync(::fetch, store, "staging", ruWordsearch) { 1L shl 30 }
        assertEquals(listOf("wordsearch-ru"), again.skipped)
    }

    @Test fun unwantedLanguageIsNotDownloaded() {
        val r = PackSync.sync(::fetch, PackStore(tmp.newFolder()), "staging", { it.language == "de" }) { 1L shl 30 }
        assertTrue(r.installed.isEmpty() && r.failed.isEmpty())
    }

    @Test fun corruptedDownloadKeepsPreviousState() {
        val store = PackStore(tmp.newFolder())
        val path = PackManifest.parse(fetch("public/staging/v1/manifest.json")!!).single().path
        val good = fetch("public/staging/v1/$path")!!
        served["public/staging/v1/$path"] = good.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        val r = PackSync.sync(::fetch, store, "staging", ruWordsearch) { 1L shl 30 }
        assertEquals("wordsearch-ru", r.failed.single().first)
        assertTrue(store.installedVersions().isEmpty())
    }

    @Test fun offlineAndNoSpaceFallBackToBuiltIn() {
        val store = PackStore(tmp.newFolder())
        served["public/staging/v1/manifest.json"] = null
        assertEquals("manifest", PackSync.sync(::fetch, store, "staging", ruWordsearch) { 1L shl 30 }.failed.single().first)
        served.clear()
        assertEquals("not enough space", PackSync.sync(::fetch, store, "staging", ruWordsearch) { 10L }.failed.single().second)
        val info = PackManifest.parse(fetch("public/staging/v1/manifest.json")!!).single()
        assertNull(store.activeRows(info))   // null → генераторы используют встроенные данные
    }

    @Test fun activeGameStaysOnItsDictionaryVersion() {
        val store = PackStore(tmp.newFolder())
        PackSync.sync(::fetch, store, "staging", ruWordsearch) { 1L shl 30 }
        val info = PackManifest.parse(fetch("public/staging/v1/manifest.json")!!).single()
        val v1 = store.pinnedRows(info, 1)!!
        val v2 = info.copy(dataVersion = 2)
        // пакет версии 2 установлен позже — игра, созданная на v1, по-прежнему читает v1
        store.file("wordsearch-ru", 1).copyTo(store.file("wordsearch-ru", 2).also { it.parentFile.mkdirs() })
        assertEquals(v1, store.pinnedRows(info, 1))
        assertNull(store.pinnedRows(v2, 3))
    }
}
