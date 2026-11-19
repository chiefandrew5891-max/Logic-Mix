package io.github.chiefandrew5891max.logicmix.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Tinted vector icon. Pass a description only when the icon is the sole content of a control. */
@Composable
fun LogicMixIcon(
    @DrawableRes id: Int,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 24.dp,
) {
    Icon(painterResource(id), contentDescription, modifier.size(size), tint)
}
