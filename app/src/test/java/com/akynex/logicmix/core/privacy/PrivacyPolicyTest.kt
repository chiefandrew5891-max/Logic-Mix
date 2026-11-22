package com.akynex.logicmix.core.privacy

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPolicyTest {
    private val tags = listOf("en", "ru", "uk", "it", "de", "fr", "es", "zh-CN")
    private val dir = listOf("src/main/assets/privacy", "app/src/main/assets/privacy").map(::File).first { it.isDirectory }
    private fun policy(tag: String) = File(dir, "policy_$tag.md").readText(Charsets.UTF_8)
    private val config get() = PrivacyConfig.parse(File(dir, "config.properties").readText())

    @Test fun allLanguagesHaveSameStructure() {
        val ref = PrivacyPolicyParser.parse(policy("en"), config).map { it::class }
        for (t in tags) {
            val blocks = PrivacyPolicyParser.parse(policy(t), config)
            assertEquals(t, 16, blocks.count { it is PolicyBlock.Heading })
            assertEquals(t, 4, blocks.count { it is PolicyBlock.Bullet })
            assertEquals(t, 1, blocks.count { it is PolicyBlock.Title })
            assertEquals(t, 2, blocks.count { it is PolicyBlock.Meta })
            assertEquals(t, ref, blocks.map { it::class })
        }
    }

    @Test fun linksAreIdenticalAcrossLanguages() {
        val ref = Regex("https://\\S+").findAll(policy("en")).map { it.value }.toList()
        for (t in tags) assertEquals(t, ref, Regex("https://\\S+").findAll(policy(t)).map { it.value }.toList())
    }

    @Test fun placeholdersAreKeptUntilConfigured() {
        val empty = PrivacyConfig("", "", "", "")
        val text = PrivacyPolicyParser.parse(policy("en"), empty).joinToString { it.toString() }
        assertTrue(text.contains("[CONTACT_EMAIL]"))
        val set = PrivacyConfig("1.0", "2026-01-01", "a@b.c", "")
        val filled = PrivacyPolicyParser.parse(policy("en"), set).joinToString { it.toString() }
        assertFalse(filled.contains("[CONTACT_EMAIL]") || filled.contains("[VERSION]"))
    }

    @Test fun onlineUrlRequiresHttps() {
        assertNull(PrivacyConfig("", "", "", "").onlineUrl)
        assertNull(PrivacyConfig("", "", "", "http://x.y").onlineUrl)
        assertEquals("https://x.y", PrivacyConfig("", "", "", " https://x.y ").onlineUrl)
    }

    @Test fun settingsNoLongerShowsPlaceholderForPrivacy() {
        val src = listOf("src/main/java", "app/src/main/java").map(::File).first { it.isDirectory }
        val text = File(src, "com/akynex/logicmix/feature/settings/SettingsScreen.kt").readText()
        assertFalse(text.contains("settings_privacy), stringResource(R.string.settings_planned"))
    }
}
