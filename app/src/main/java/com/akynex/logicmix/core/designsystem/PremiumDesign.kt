package com.akynex.logicmix.core.designsystem

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One place for the premium look: palette, radii and glow strength. Components below read only these. */
object PremiumTokens {
    val Bg = Color(0xFF03172F)
    val BgDeep = Color(0xFF062B50)
    val BgLit = Color(0xFF07577C)
    val Teal = Color(0xFF00D8E8)
    val Blue = Color(0xFF2468CF)
    val Violet = Color(0xFF6242C5)
    val GoldHi = Color(0xFFFFF5B0)
    val GoldTop = Color(0xFFFFE56A)
    val GoldMid = Color(0xFFFFC400)
    val GoldLow = Color(0xFFF5A000)
    val GoldEdge = Color(0xFFC97900)
    val OnGold = Color(0xFF07182D)
    val RadiusCard = 22.dp
    const val PressScale = 0.975f
    const val GlowStrength = 1f
}

enum class PremiumTone { Blue, Violet, Gold }

@Composable
internal fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
}

/**
 * Layered glass surface: soft outer shadow, base gradient, local corner light, top sheen, bottom shade, gradient rim and an
 * optional neon halo. [glow] is read in the draw phase only (0..1), so animating it never recomposes. Static brushes are cached.
 */
@Composable
fun Modifier.premiumSurface(tone: PremiumTone = PremiumTone.Blue, radius: Dp = PremiumTokens.RadiusCard, glow: () -> Float = { 0f }): Modifier {
    val dark = LogicMixTokens.colors.isDark
    return drawWithCache {
        val r = radius.toPx()
        val cr = CornerRadius(r)
        val w = size.width
        val h = size.height
        val (top, bottom, light, rimA, rimB) = when (tone) {
            PremiumTone.Blue -> if (dark) Five(Color(0xFF0E3A6E), Color(0xFF06234A), PremiumTokens.Teal, PremiumTokens.Teal, PremiumTokens.Blue)
            else Five(Color(0xFFFFFFFF), Color(0xFFDCEBFF), PremiumTokens.Teal, Color(0xFF7FB6F5), Color(0xFFB9A6F2))
            PremiumTone.Violet -> if (dark) Five(Color(0xFF4A3AA8), Color(0xFF241A66), Color(0xFFB08CFF), Color(0xFFB08CFF), PremiumTokens.Blue)
            else Five(Color(0xFFF3EDFF), Color(0xFFD9CCFA), Color(0xFFB08CFF), Color(0xFFB39AF2), Color(0xFF8EB6F5))
            PremiumTone.Gold -> if (dark) Five(Color(0xFF1B3E78), Color(0xFF0A2350), PremiumTokens.GoldMid, PremiumTokens.GoldTop, PremiumTokens.Teal)
            else Five(Color(0xFFFFFBEA), Color(0xFFFFE9B0), PremiumTokens.GoldMid, PremiumTokens.GoldLow, Color(0xFF8EB6F5))
        }
        val base = Brush.verticalGradient(listOf(top, bottom))
        val corner = Brush.radialGradient(
            listOf(light.copy(alpha = if (dark) 0.30f else 0.22f), Color.Transparent),
            center = Offset(w * 0.12f, h * 0.05f), radius = maxOf(w, h) * 0.75f,
        )
        val sheen = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = if (dark) 0.16f else 0.55f), Color.Transparent), startY = 0f, endY = h * 0.42f,
        )
        val shade = Brush.verticalGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = if (dark) 0.30f else 0.08f)), startY = h * 0.55f, endY = h,
        )
        val rim = Brush.linearGradient(
            listOf(rimA.copy(alpha = if (dark) 0.85f else 0.8f), rimB.copy(alpha = if (dark) 0.35f else 0.45f)),
            start = Offset.Zero, end = Offset(w, h),
        )
        val shadowColor = if (dark) Color.Black else Color(0xFF2C3E73)
        val stroke = Stroke(1.5.dp.toPx())
        val neonColor = if (tone == PremiumTone.Gold) PremiumTokens.GoldMid else InteractiveTokens.neon(dark)
        val inset = 1.5.dp.toPx()
        onDrawBehind {
            for (i in 1..3) {
                val s = i * 2.5.dp.toPx()
                drawRoundRect(
                    shadowColor.copy(alpha = (if (dark) 0.16f else 0.07f) * (4 - i) / 3f),
                    topLeft = Offset(-s / 2f, s * 0.9f), size = Size(w + s, h + s * 0.4f), cornerRadius = CornerRadius(r + s / 2f),
                )
            }
            val g = glow() * PremiumTokens.GlowStrength
            if (g > 0.01f) drawOuterNeon(g, r, neonColor, dark)
            drawRoundRect(base, cornerRadius = cr)
            drawRoundRect(corner, cornerRadius = cr)
            drawRoundRect(sheen, topLeft = Offset(inset, inset), size = Size(w - 2 * inset, h * 0.42f), cornerRadius = CornerRadius(r - inset))
            drawRoundRect(shade, cornerRadius = cr)
            drawRoundRect(rim, topLeft = Offset(stroke.width / 2f, stroke.width / 2f), size = Size(w - stroke.width, h - stroke.width), cornerRadius = CornerRadius(r), style = stroke)
            if (g > 0.01f) drawInnerNeon(g, r, neonColor, dark)
        }
    }
}

private data class Five(val a: Color, val b: Color, val c: Color, val d: Color, val e: Color)

/** Premium glass card with press feedback (scale ~97.5% and a brief glow). [selected] adds a clear neon rim, not just a thicker border. */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    tone: PremiumTone = PremiumTone.Blue,
    selected: Boolean = false,
    radius: Dp = PremiumTokens.RadiusCard,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = rememberMotionEnabled()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val spec = if (motion) tween<Float>(if (pressed) InteractiveTokens.PressInMs else InteractiveTokens.TransitionMs) else snap()
    val scale by animateFloatAsState(if (pressed) PremiumTokens.PressScale else 1f, spec, label = "premiumScale")
    val glow by animateFloatAsState(if (pressed) 1f else if (selected) 0.9f else 0f, spec, label = "premiumGlow")
    val shape = RoundedCornerShape(radius)
    var m = modifier.graphicsLayer { scaleX = scale; scaleY = scale }.premiumSurface(tone, radius) { glow }.clip(shape)
    if (onClick != null) m = m.instantPress(source).clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
    Box(m, contentAlignment = contentAlignment, content = content)
}

/** Home backdrop; the same silk-ribbon layer every screen uses, with [content] above it. */
@Composable
fun PremiumAuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        LogicMixBackdrop(Modifier.matchParentSize())
        content()
    }
}

/** Capsule track with a teal-to-sky fill, top gloss and inner shade. [label] is drawn inside when given. */
@Composable
fun PremiumProgressBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 22.dp, label: String? = null) {
    val motion = rememberMotionEnabled()
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), if (motion) tween(600) else snap(), label = "premiumProgress")
    val dark = LogicMixTokens.colors.isDark
    Box(
        modifier.height(height).drawWithCache {
            val r = size.height / 2f
            val track = Brush.verticalGradient(
                if (dark) listOf(Color(0xFF020E20), Color(0xFF0B2547)) else listOf(Color(0xFFB9C9E6), Color(0xFFE3ECFB)),
            )
            val fill = Brush.horizontalGradient(listOf(Color(0xFF00C2D8), Color(0xFF4DD8FF), Color(0xFF8AE9FF)))
            val gloss = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), 0f, size.height * 0.55f)
            val inner = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), 0f, size.height * 0.35f)
            val rim = Color.White.copy(alpha = if (dark) 0.18f else 0.5f)
            onDrawBehind {
                drawRoundRect(track, cornerRadius = CornerRadius(r))
                drawRoundRect(inner, cornerRadius = CornerRadius(r))
                val fw = size.width * f
                if (fw > 0f) {
                    val fwMin = maxOf(fw, size.height)
                    drawRoundRect(fill, size = Size(fwMin, size.height), cornerRadius = CornerRadius(r))
                    drawRoundRect(gloss, topLeft = Offset(r * 0.4f, size.height * 0.08f), size = Size(maxOf(fwMin - r * 0.8f, 0f), size.height * 0.5f), cornerRadius = CornerRadius(r))
                }
                drawRoundRect(rim, cornerRadius = CornerRadius(r), style = Stroke(1.dp.toPx()))
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(
                label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

/** Round avatar with a teal-violet gradient ring and soft glow; [image] null draws a neutral silhouette. */
@Composable
fun PremiumAvatar(size: Dp, modifier: Modifier = Modifier, image: ImageBitmap? = null) {
    Box(
        modifier.size(size).drawWithCache {
            val ring = Brush.linearGradient(listOf(PremiumTokens.Teal, PremiumTokens.Violet), Offset.Zero, Offset(this.size.width, this.size.height))
            val glow = Brush.radialGradient(listOf(PremiumTokens.Teal.copy(alpha = 0.35f), Color.Transparent), radius = this.size.width * 0.75f)
            onDrawBehind {
                drawCircle(glow, radius = this.size.width * 0.62f)
                drawCircle(Color.Black.copy(alpha = 0.25f), radius = this.size.width * 0.5f, center = Offset(this.size.width / 2f, this.size.height / 2f + 2.dp.toPx()))
                drawCircle(ring, radius = this.size.width / 2f)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        val inner = Modifier.size(size - 6.dp).clip(CircleShape)
        if (image != null) {
            Image(image, null, inner, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        } else {
            Canvas(inner) {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF2A4A9A), Color(0xFF14275E))))
                val c = Color(0xFFBFD3FF)
                drawCircle(c, radius = this.size.width * 0.19f, center = Offset(this.size.width / 2f, this.size.height * 0.38f))
                drawCircle(c, radius = this.size.width * 0.34f, center = Offset(this.size.width / 2f, this.size.height * 0.98f))
            }
        }
    }
}

/** Main call-to-action: layered gold capsule (light top, saturated middle, orange bottom, lip, gloss, halo) that sinks when pressed. */
@Composable
fun PremiumGoldButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, minHeight: Dp = 56.dp, icon: @Composable (() -> Unit)? = null) {
    val motion = rememberMotionEnabled()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) 1f else 0f, if (motion) tween(if (pressed) InteractiveTokens.PressInMs else InteractiveTokens.TransitionMs) else snap(), label = "goldSink")
    var shrink by remember(label) { mutableFloatStateOf(1f) }
    Box(
        modifier
            .graphicsLayer { translationY = sink * 3.dp.toPx() }
            .drawWithCache {
                val r = size.height / 2f
                val lip = 4.dp.toPx() * (1f - 0.6f * sink)
                val body = size.height - 4.dp.toPx()
                val fill = Brush.verticalGradient(
                    0f to PremiumTokens.GoldTop, 0.45f to PremiumTokens.GoldMid, 1f to PremiumTokens.GoldLow, endY = body,
                )
                val gloss = Brush.verticalGradient(listOf(PremiumTokens.GoldHi.copy(alpha = 0.85f), Color.Transparent), 0f, body * 0.5f)
                onDrawBehind {
                    if (sink > 0.01f) drawOuterNeon(sink, size.height / 2f, PremiumTokens.GoldMid, true)
                    drawRoundRect(Color.Black.copy(alpha = 0.28f), topLeft = Offset(0f, lip + 3.dp.toPx()), size = Size(size.width, body), cornerRadius = CornerRadius(r))
                    drawRoundRect(PremiumTokens.GoldEdge, topLeft = Offset(0f, lip), size = Size(size.width, body), cornerRadius = CornerRadius(r))
                    drawRoundRect(fill, size = Size(size.width, body), cornerRadius = CornerRadius(r))
                    drawRoundRect(gloss, topLeft = Offset(r * 0.5f, 2.dp.toPx()), size = Size(size.width - r, body * 0.45f), cornerRadius = CornerRadius(r * 0.7f))
                    drawRoundRect(Color.White.copy(alpha = 0.55f), size = Size(size.width, body), cornerRadius = CornerRadius(r), style = Stroke(1.2.dp.toPx()))
                }
            }
            .instantPress(source)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(bottom = 4.dp)
            .defaultMinSize(minHeight = minHeight),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.invoke()
            Text(
                label.uppercase(),
                fontSize = (22f * shrink).sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                color = PremiumTokens.OnGold,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                onTextLayout = { if (it.didOverflowWidth && shrink > 0.5f) shrink *= 0.9f },
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** Circular gradient chevron badge used as the "go" affordance in cards (the whole card is the tap target). */
@Composable
fun PremiumIconButton(size: Dp = 26.dp, gold: Boolean = false, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val ring = if (gold) listOf(PremiumTokens.GoldTop, PremiumTokens.GoldLow) else listOf(Color(0xFF5BE7F2), Color(0xFF2468CF))
        drawCircle(Brush.linearGradient(ring, Offset.Zero, Offset(this.size.width, this.size.height)), radius = this.size.width / 2f)
        drawCircle(Color.White.copy(alpha = 0.35f), radius = this.size.width / 2f, style = Stroke(1.dp.toPx()))
        val s = this.size.width * 0.14f
        val p = Path().apply { moveTo(c.x - s * 0.6f, c.y - s * 1.5f); lineTo(c.x + s * 0.9f, c.y); lineTo(c.x - s * 0.6f, c.y + s * 1.5f) }
        drawPath(p, if (gold) PremiumTokens.OnGold else Color.White, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Line - title - line header; the lines fade out toward the outer edges. */
@Composable
fun PremiumSectionHeader(text: String, modifier: Modifier = Modifier) {
    val dark = LogicMixTokens.colors.isDark
    val line = if (dark) PremiumTokens.Teal else PremiumTokens.Blue
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.5.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, line.copy(alpha = 0.8f)))))
        Text(
            text, Modifier.padding(horizontal = 12.dp),
            style = LogicMixTokens.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center, maxLines = 2,
        )
        Box(Modifier.weight(1f).height(1.5.dp).background(Brush.horizontalGradient(listOf(line.copy(alpha = 0.8f), Color.Transparent))))
    }
}
