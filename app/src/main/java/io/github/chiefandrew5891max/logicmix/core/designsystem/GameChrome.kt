package io.github.chiefandrew5891max.logicmix.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import io.github.chiefandrew5891max.logicmix.R

private const val MIN_FONT_SP = 10f
private const val MIN_FONT_RATIO = 0.75f
private val TOOL_PADDING = 8.dp

/** Applies [ButtonFitter] to real text measurements. [available] is the full button width, including its padding. */
@Composable
fun rememberButtonFit(labels: List<String>, style: TextStyle, available: Dp, basePadding: Dp): ButtonFit {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, style, available, basePadding, density.density, density.fontScale) {
        val baseFont = if (style.fontSize.isSpecified) style.fontSize.value else 14f
        val minFont = maxOf(MIN_FONT_SP, baseFont * MIN_FONT_RATIO)
        ButtonFitter.group(
            labels.map { label ->
                ButtonFitter.fit(available.value, basePadding.value, baseFont, minFont) { font ->
                    measurer.measure(label, style.copy(fontSize = font.sp), softWrap = false, maxLines = 1).size.width / density.density
                }
            },
        )
    }
}

/** Label text following [fit]: no breaks inside words; wrapping between words only when [ButtonFit.wrap] says so. */
@Composable
fun FitLabel(label: String, fit: ButtonFit, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val canWrap = fit.wrap && label.contains(' ')
    Text(
        label,
        style = style.copy(fontSize = fit.fontSize.sp),
        color = color,
        textAlign = TextAlign.Center,
        softWrap = canWrap,
        maxLines = if (canWrap) Int.MAX_VALUE else 1,
        overflow = TextOverflow.Visible,
        modifier = modifier,
    )
}

/** Top bar of a game screen: back arrow, title, optional subtitle and (for every game) the confirmed Restart button. */
@Composable
fun GameHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onRestart: (() -> Unit)? = null,
    restartEnabled: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM)) {
        HeaderIconButton(R.drawable.ic_back, stringResource(R.string.back), onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = LogicMixTokens.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = LogicMixTokens.typography.labelMedium, color = LogicMixTokens.colors.gold)
        }
        trailing()
        if (onRestart != null) RestartButton(onRestart, restartEnabled)
    }
}

@Composable
private fun HeaderIconButton(icon: Int, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = LogicMixTokens.colors
    val shape = LogicMixTokens.radii.medium
    Box(
        Modifier
            .size(LogicMixTokens.minTouchTarget)
            .clip(shape)
            .background(colors.cardBrush)
            .border(BorderStroke(1.dp, colors.cardBorder), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LogicMixIcon(icon, MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f), contentDescription = description)
    }
}

/** The one Restart control of Logic Mix: a circular-arrow button that always asks for confirmation first. */
@Composable
fun RestartButton(onConfirm: () -> Unit, enabled: Boolean = true) {
    var asking by remember { mutableStateOf(false) }
    HeaderIconButton(R.drawable.ic_restart, stringResource(R.string.restart), { asking = true }, enabled)
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.restart_title)) },
            text = { Text(stringResource(R.string.restart_body)) },
            confirmButton = { TextButton({ asking = false; onConfirm() }) { Text(stringResource(R.string.restart_confirm)) } },
            dismissButton = { TextButton({ asking = false }) { Text(stringResource(R.string.restart_cancel)) } },
        )
    }
}

data class GameTool(val label: String, val onClick: () -> Unit, val enabled: Boolean = true, val selected: Boolean = false)

/** Equal-width, equal-height row of tool buttons whose labels adapt together (see [ButtonFitter]). */
@Composable
fun GameToolRow(tools: List<GameTool>, modifier: Modifier = Modifier) {
    val style = LogicMixTokens.typography.labelLarge
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = LogicMixTokens.spacingS
        val each = (maxWidth - gap * (tools.size - 1)) / tools.size
        val fit = rememberButtonFit(tools.map { it.label }, style, each, TOOL_PADDING)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
            tools.forEach { ToolButton(it, fit, style, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

@Composable
private fun ToolButton(tool: GameTool, fit: ButtonFit, style: TextStyle, modifier: Modifier) {
    val colors = LogicMixTokens.colors
    val shape = LogicMixTokens.radii.medium
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .heightIn(min = LogicMixTokens.minTouchTarget)
            .clip(shape)
            .then(if (tool.selected) Modifier.background(primary.copy(alpha = 0.35f)) else Modifier.background(colors.cardBrush))
            .border(BorderStroke(if (tool.selected) 2.dp else 1.dp, if (tool.selected) primary else colors.cardBorder), shape)
            .clickable(enabled = tool.enabled, role = Role.Button, onClick = tool.onClick)
            .padding(horizontal = fit.padding.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        FitLabel(tool.label, fit, style, MaterialTheme.colorScheme.onSurface.copy(alpha = if (tool.enabled) 1f else 0.4f))
    }
}
