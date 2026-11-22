package com.akynex.logicmix.feature.privacy

import android.content.Context
import androidx.lifecycle.ViewModel
import com.akynex.logicmix.core.data.SettingsRepository
import com.akynex.logicmix.core.locale.effectiveLanguage
import com.akynex.logicmix.core.privacy.PolicyBlock
import com.akynex.logicmix.core.privacy.PrivacyAssets
import com.akynex.logicmix.core.privacy.PrivacyConfig
import com.akynex.logicmix.core.privacy.PrivacyPolicyParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

data class PrivacyPolicyState(
    val config: PrivacyConfig,
    val blocks: List<PolicyBlock>,
    val isDraft: Boolean,
)

/** Reads only bundled assets; opening the policy never touches consent, ads or analytics. */
@HiltViewModel
class PrivacyPolicyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    settings: SettingsRepository,
) : ViewModel() {
    val state: Flow<PrivacyPolicyState> = settings.effectiveLanguage().map { lang ->
        val config = PrivacyConfig.parse(read(PrivacyAssets.CONFIG) ?: "")
        val text = read(PrivacyAssets.fileName(lang.tag ?: PrivacyAssets.FALLBACK_TAG))
            ?: read(PrivacyAssets.fileName(PrivacyAssets.FALLBACK_TAG)) ?: ""
        PrivacyPolicyState(config, PrivacyPolicyParser.parse(text, config), PrivacyPolicyParser.hasUnresolvedPlaceholders(config))
    }.flowOn(Dispatchers.IO)

    private fun read(path: String): String? =
        runCatching { context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull()
}
