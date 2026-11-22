package com.akynex.logicmix.core.privacy

import java.util.Properties

sealed interface PolicyBlock {
    data class Title(val text: String) : PolicyBlock
    data class Meta(val text: String) : PolicyBlock
    data class Heading(val text: String) : PolicyBlock
    data class Paragraph(val text: String) : PolicyBlock
    data class Bullet(val text: String) : PolicyBlock
}

data class PrivacyConfig(
    val version: String,
    val effectiveDate: String,
    val contactEmail: String,
    val policyUrl: String,
) {
    /** Only a non-blank HTTPS URL counts as published; otherwise "View online" stays disabled. */
    val onlineUrl: String? get() = policyUrl.trim().takeIf { it.startsWith("https://") }

    companion object {
        fun parse(text: String): PrivacyConfig {
            val p = Properties().apply { load(text.reader()) }
            fun v(key: String) = p.getProperty(key, "").trim()
            return PrivacyConfig(v("version"), v("effectiveDate"), v("contactEmail"), v("policyUrl"))
        }
    }
}

object PrivacyPolicyParser {
    const val CONTACT_PLACEHOLDER = "[CONTACT_EMAIL]"
    const val NOT_PUBLISHED = "—"

    fun parse(markdown: String, config: PrivacyConfig): List<PolicyBlock> {
        val out = ArrayList<PolicyBlock>()
        val para = StringBuilder()
        fun flush() {
            if (para.isNotEmpty()) { out += PolicyBlock.Paragraph(substitute(para.toString().trim(), config)); para.clear() }
        }
        var metaLeft = 0
        for (raw in markdown.lines()) {
            val line = raw.trimEnd()
            when {
                line.isBlank() -> flush()
                line.startsWith("## ") -> { flush(); out += PolicyBlock.Heading(substitute(line.substring(3), config)) }
                line.startsWith("# ") -> { flush(); out += PolicyBlock.Title(substitute(line.substring(2), config)); metaLeft = 2 }
                line.startsWith("- ") -> { flush(); out += PolicyBlock.Bullet(substitute(line.substring(2), config)) }
                metaLeft > 0 -> { flush(); out += PolicyBlock.Meta(substitute(line, config)); metaLeft-- }
                else -> { if (para.isNotEmpty()) para.append('\n'); para.append(line) }
            }
        }
        flush()
        return out
    }

    /** Unset values keep their visible placeholder so a draft is never mistaken for a final text. */
    fun substitute(text: String, config: PrivacyConfig): String {
        var r = text
        if (config.version.isNotEmpty()) r = r.replace("[VERSION]", config.version)
        if (config.effectiveDate.isNotEmpty()) r = r.replace("[EFFECTIVE_DATE]", config.effectiveDate)
        if (config.contactEmail.isNotEmpty()) r = r.replace(CONTACT_PLACEHOLDER, config.contactEmail)
        return r
    }

    fun hasUnresolvedPlaceholders(config: PrivacyConfig) =
        config.contactEmail.isEmpty() || config.version.isEmpty() || config.version.contains("DRAFT", ignoreCase = true)

    private val urlRegex = Regex("https://[^\\s)]+")
    fun firstUrl(text: String): String? = urlRegex.find(text)?.value
}

object PrivacyAssets {
    fun fileName(languageTag: String) = "privacy/policy_$languageTag.md"
    const val CONFIG = "privacy/config.properties"
    const val FALLBACK_TAG = "en"
}
