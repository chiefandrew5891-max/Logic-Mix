package com.akynex.logicmix.feature.arcade

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akynex.logicmix.R
import com.akynex.logicmix.core.designsystem.LogicMixTokens
import com.akynex.logicmix.domain.arcade.ArcadeAchievement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.math.exp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val Gold = Color(0xFFF5C84B)
private val GoldSoft = Color(0xFFFFE9A8)
private const val SCENE_MS = 3600
private const val REDUCED_MS = 1800
private const val GRAVITY = 1.7f

/**
 * Victory scene: the artwork pops in, two horns shoot confetti/sparks/stars that fall under gravity, the localized
 * title is fitted into the empty ribbon and the matching WAV starts with the animation (see ArcadeViewModel).
 * The reward is already booked before this overlay appears, so skipping (tap) or re-composition never re-awards it.
 */
@Composable
fun ArcadeCelebration(c: Celebration, viewModel: ArcadeViewModel) {
    val context = LocalContext.current
    val reduced = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val progress = remember(c.id) { Animatable(0f) }
    val pop = remember(c.id) { Animatable(if (reduced) 1f else 0f) }
    val points = remember(c.id) { Animatable(0f) }
    LaunchedEffect(c.id) { viewModel.playCelebrationSounds(c) }
    LaunchedEffect(c.id) { if (!reduced) pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    LaunchedEffect(c.id) { points.animateTo(c.award.toFloat(), tween(if (reduced) 1 else 900)) }
    LaunchedEffect(c.id) {
        val ms = if (reduced) REDUCED_MS else if (c.levelUp) SCENE_MS + 600 else SCENE_MS
        progress.animateTo(1f, tween(ms, easing = LinearEasing))
        viewModel.celebrationDone(c.id)
    }
    val particles = remember(c.id) { if (reduced || c.skipped) emptyList() else buildParticles(c.id.hashCode(), if (c.levelUp) 50 else 34) }
    val starPath = remember { unitStar() }
    var imageRect by remember(c.id) { mutableStateOf<Rect?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val totalSeconds = (if (c.levelUp) SCENE_MS + 600 else SCENE_MS) / 1000f
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clickable(onClick = { viewModel.celebrationDone(c.id) })
            .background(Brush.radialGradient(listOf(Gold.copy(alpha = if (c.skipped) 0f else 0.30f), Color(0xCC050B1F)))),
        contentAlignment = Alignment.Center,
    ) {
        val variant = c.variant
        if (c.skipped || variant == null) {
            Text(stringResource(R.string.arcade_stage_skipped), style = LogicMixTokens.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(LogicMixTokens.spacingL))
            return@Box
        }
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val imgW = minOf(maxWidth * 0.94f, maxHeight * 0.5f * variant.aspect)
            val imgH = imgW / variant.aspect
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(
                    Modifier
                        .size(imgW, imgH)
                        .onGloballyPositioned { imageRect = it.boundsInRoot() }
                        .graphicsLayer { val k = 0.15f + 0.85f * pop.value; scaleX = k; scaleY = k; alpha = pop.value.coerceIn(0f, 1f) },
                ) {
                    Image(painterResource(variant.drawable()), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    val r = variant.ribbon
                    FitText(
                        stringResource(R.string.victory_level_complete),
                        Modifier.offset(imgW * r.left, imgH * r.top).size(imgW * (r.right - r.left), imgH * (r.bottom - r.top)),
                    )
                }
                Column(Modifier.padding(top = LogicMixTokens.spacingS).graphicsLayer { alpha = pop.value }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(LogicMixTokens.spacingS)) {
                    Text("+${points.value.toInt()}", style = LogicMixTokens.typography.headlineMedium, color = Color.White)
                    if (c.bonus > 0) Text(stringResource(R.string.arcade_bonus, c.bonus), style = LogicMixTokens.typography.titleMedium, color = GoldSoft)
                    if (c.levelUp) {
                        Text(stringResource(R.string.arcade_level_up), style = LogicMixTokens.typography.headlineMedium, color = Gold, textAlign = TextAlign.Center)
                        Text(stringResource(R.string.arcade_level_n, c.level), style = LogicMixTokens.typography.titleMedium, color = Color.White)
                    }
                    c.achievements.forEach {
                        Text(stringResource(R.string.arcade_achievement_unlocked, stringResource(it.nameRes())), style = LogicMixTokens.typography.labelMedium, color = GoldSoft, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        if (particles.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                val rect = imageRect ?: return@Canvas
                drawParticles(particles, variant, rect.translate(-origin), progress.value * totalSeconds, totalSeconds, starPath)
            }
        }
    }
}

@Composable
private fun FitText(text: String, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val base = LogicMixTokens.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val sizePx = remember(text, w, h, base) { fitFontPx(measurer, text, base, w, h, density) }
        Text(
            text,
            style = base.copy(fontSize = with(density) { sizePx.toSp() }, shadow = Shadow(Color(0xCC000000), Offset(0f, 3f), 6f)),
            color = Color.White,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

/** Largest font (px) at which [text] fits in at most two lines inside w x h; shrinks 8 % per step. */
private fun fitFontPx(measurer: TextMeasurer, text: String, base: TextStyle, w: Int, h: Int, density: Density): Float {
    var px = h * 0.7f
    val min = with(density) { 9.sp.toPx() }
    while (px > min) {
        val r = measurer.measure(text, base.copy(fontSize = with(density) { px.toSp() }), maxLines = 2, constraints = Constraints(maxWidth = w))
        if (!r.hasVisualOverflow && r.size.height <= h && r.size.width <= w) return px
        px *= 0.92f
    }
    return min
}

private fun VictoryVariant.drawable(): Int = when (this) {
    VictoryVariant.RED -> R.drawable.victory_fanfare_red
    VictoryVariant.PURPLE -> R.drawable.victory_fanfare_purple
    VictoryVariant.BLUE -> R.drawable.victory_fanfare_blue
}

/** [emitter] 0 = left horn, 1 = right horn. Speeds are in image widths per second so the burst scales with the artwork. */
private class Particle(val emitter: Int, val angle: Float, val speed: Float, val delay: Float, val kind: Int, val size: Float, val color: Color, val spin: Float, val spinSpeed: Float)

private fun buildParticles(seed: Int, perHorn: Int): List<Particle> {
    val r = Random(seed)
    val colors = listOf(Gold, GoldSoft, Color.White, Color(0xFFFF8A65), Color(0xFF63B3ED), Color(0xFFB794F4))
    return List(perHorn * 2) { i ->
        Particle(
            emitter = i % 2,
            angle = Math.toRadians(-85.0 + r.nextDouble() * 60.0).toFloat(), // upward and outwards from the horn mouth
            speed = 0.35f + r.nextFloat() * 0.55f,
            delay = 0.25f + r.nextFloat() * 0.5f,
            kind = r.nextInt(3),
            size = 0.008f + r.nextFloat() * 0.012f,
            color = colors[r.nextInt(colors.size)],
            spin = r.nextFloat() * 360f,
            spinSpeed = (r.nextFloat() - 0.5f) * 900f,
        )
    }
}

private fun unitStar(): Path = Path().apply {
    for (i in 0 until 10) {
        val a = Math.PI / 5 * i - Math.PI / 2
        val rad = if (i % 2 == 0) 1f else 0.45f
        val x = (cos(a) * rad).toFloat(); val y = (sin(a) * rad).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.drawParticles(list: List<Particle>, v: VictoryVariant, rect: Rect, t: Float, total: Float, star: Path) {
    val w = rect.width
    val g = w * GRAVITY
    list.forEach { p ->
        val tau = t - p.delay
        if (tau <= 0f) return@forEach
        val horn = if (p.emitter == 0) v.leftHorn else v.rightHorn
        val dir = if (p.emitter == 0) 1f else -1f
        val vx = dir * cos(p.angle) * p.speed * w // horns face up and inwards: left shoots right, right shoots left
        val vy = sin(p.angle) * p.speed * w
        val drag = (1f - exp(-1.6f * tau)) / 1.6f
        val x = rect.left + horn.x * w + vx * drag
        val y = rect.top + horn.y * rect.height + vy * drag + 0.5f * g * tau * tau
        val alpha = ((total - t) / 0.6f).coerceIn(0f, 1f)
        val s = p.size * w
        val c = p.color.copy(alpha = alpha)
        val deg = p.spin + p.spinSpeed * tau
        translate(x, y) {
            rotate(deg, Offset.Zero) {
                when (p.kind) {
                    0 -> drawRect(c, Offset(-s, -s / 2), Size(s * 2, s))
                    1 -> drawCircle(c, s * 0.6f)
                    else -> scale(s * 1.4f, s * 1.4f, Offset.Zero) { drawPath(star, c) }
                }
            }
        }
    }
}

private fun ArcadeAchievement.nameRes(): Int = when (this) {
    ArcadeAchievement.STREAK_3 -> R.string.arcade_ach_streak_3
    ArcadeAchievement.STREAK_5 -> R.string.arcade_ach_streak_5
    ArcadeAchievement.STREAK_10 -> R.string.arcade_ach_streak_10
    ArcadeAchievement.FIRST_CYCLE -> R.string.arcade_ach_first_cycle
    ArcadeAchievement.FULL_SET_CYCLE -> R.string.arcade_ach_full_set
    ArcadeAchievement.NO_HINTS -> R.string.arcade_ach_no_hints
    ArcadeAchievement.NEW_RECORD -> R.string.arcade_ach_record
}
