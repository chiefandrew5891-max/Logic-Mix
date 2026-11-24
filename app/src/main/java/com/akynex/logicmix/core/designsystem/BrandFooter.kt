package com.akynex.logicmix.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.akynex.logicmix.R

/**
 * The one AKYneX studio footer. It is mounted once by the app shell (MainActivity), pinned under every screen, so
 * no screen draws its own. Text, size and colours are the approved design; do not duplicate it per screen.
 */
@Composable
fun BrandFooter(modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.fillMaxWidth().navigationBarsPadding().padding(top = LogicMixTokens.spacingS, bottom = LogicMixTokens.spacingS),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("© 2026 AKYneX studio™", color = muted.copy(alpha = 0.9f), fontSize = 12.sp, textAlign = TextAlign.Center)
        Text(stringResource(R.string.footer_rights), color = muted.copy(alpha = 0.65f), fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}
