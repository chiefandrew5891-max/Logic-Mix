package io.github.chiefandrew5891max.logicmix.core.designsystem

import io.github.chiefandrew5891max.logicmix.core.audio.withTap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.chiefandrew5891max.logicmix.domain.content.GameCategory
import io.github.chiefandrew5891max.logicmix.domain.content.GameStyle

private val NeonCyan = Color(0xFF22E5F5)
private val NeonViolet = Color(0xFF8E5CFF)
private val NeonMagenta = Color(0xFFE94BD8)
private val NeonGold = Color(0xFFFFB84D)

/**
 * Looks a PNG up by resource name so the project compiles without the artwork: 0 means "not added yet".
 * Dropping the correctly named file into res/drawable-nodpi (see IMAGE_ASSETS.md) switches it on.
 */
@Composable
fun rememberDrawableId(name: String): Int {
    val context = LocalContext.current
    return remember(name) { context.resources.getIdentifier(name, "drawable", context.packageName) }
}

/** The supplied PNG when present (never stretched), otherwise a Compose-drawn gem placeholder. */
@Composable
fun AssetImage(name: String, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val id = rememberDrawableId(name)
    if (id != 0) {
        Image(painterResource(id), contentDescription, modifier, contentScale = ContentScale.Fit)
    } else {
        AssetPlaceholder(modifier)
    }
}

@Composable
private fun AssetPlaceholder(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val gem = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.86f, h * 0.5f)
            lineTo(w * 0.5f, h * 0.88f)
            lineTo(w * 0.14f, h * 0.5f)
            close()
        }
        drawPath(gem, Brush.linearGradient(listOf(NeonCyan, NeonViolet, NeonMagenta), Offset.Zero, Offset(w, h)), alpha = 0.85f)
        drawPath(gem, Brush.linearGradient(listOf(NeonGold, NeonMagenta)), style = Stroke(w * 0.025f))
    }
}

/**
 * Glossy card surface drawn entirely in Compose: themed background, static multicolour neon outline and
 * soft glow. Everything is derived from the final size, so it stays smooth when a card grows taller.
 */
@Composable
fun Modifier.neonFrame(shape: Shape = RoundedCornerShape(20.dp), emphasis: Boolean = false): Modifier {
    val colors = LogicMixTokens.colors
    val glow = if (colors.isDark) 0.35f else 0.22f
    return this
        .shadow(if (emphasis) 10.dp else 5.dp, shape, clip = false, ambientColor = NeonViolet, spotColor = NeonCyan)
        .clip(shape)
        .background(colors.cardBrush)
        .drawBehind {
            val sweep = Brush.sweepGradient(
                listOf(NeonCyan, NeonViolet, NeonMagenta, NeonGold, NeonMagenta, NeonViolet, NeonCyan),
                center,
            )
            val outline = shape.createOutline(size, layoutDirection, this)
            val base = if (emphasis) 3.dp.toPx() else 2.dp.toPx()
            drawOutline(outline, sweep, alpha = glow * 0.5f, style = Stroke(base * 5))
            drawOutline(outline, sweep, alpha = glow, style = Stroke(base * 3))
            drawOutline(outline, sweep, alpha = if (emphasis) 1f else 0.9f, style = Stroke(base))
        }
}

/** Rounded, glowing label with real (localized, multi-line) text; grows vertically with its content. */
@Composable
fun TitleCapsule(text: String, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    val dark = LogicMixTokens.colors.isDark
    val fill = if (dark) Color(0xCC1A1050) else Color(0x1F6C3FE0)
    val rim = if (highlighted) {
        Brush.linearGradient(listOf(NeonGold, NeonMagenta))
    } else {
        Brush.linearGradient(listOf(NeonCyan, NeonViolet))
    }
    Box(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(fill)
            .border(BorderStroke(if (highlighted) 2.dp else 1.dp, rim), CircleShape)
            .padding(horizontal = LogicMixTokens.spacingS, vertical = LogicMixTokens.spacingS),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = LogicMixTokens.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Category card: illustration on top (fixed square, aspect preserved), flexible themed space, title capsule
 * pinned to the bottom. Use with weight(1f).fillMaxHeight() inside an IntrinsicSize.Min row to equalise heights.
 */
@Composable
fun CategoryCard(
    category: GameCategory,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    role: Role = Role.Button,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .neonFrame(shape, emphasis = selected == true)
            .then(if (selected != null) Modifier.semantics { this.selected = selected } else Modifier)
            .clickable(role = role, onClick = withTap(onClick))
            .padding(LogicMixTokens.spacingS),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AssetImage(category.imageName, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(LogicMixTokens.spacingS))
        Spacer(Modifier.weight(1f))
        TitleCapsule(title, highlighted = selected == true)
    }
}

/** Wide card for a game style (onboarding and settings). */
@Composable
fun GameStyleCard(
    style: GameStyle,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .neonFrame(RoundedCornerShape(20.dp), emphasis = selected)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = withTap(onClick))
            .padding(LogicMixTokens.spacingM),
        horizontalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssetImage(style.imageName, Modifier.width(STYLE_IMAGE_SIZE).aspectRatio(1f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingXs)) {
            Text(
                title,
                style = LogicMixTokens.typography.titleMedium,
                color = if (selected) LogicMixTokens.colors.gold else MaterialTheme.colorScheme.onSurface,
            )
            Text(description, style = LogicMixTokens.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val STYLE_IMAGE_SIZE: Dp = 84.dp
