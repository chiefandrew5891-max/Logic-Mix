package io.github.chiefandrew5891max.logicmix.core.designsystem

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LogicCore = Color(0xFF9FE3FF)
private val LogicHalo = Color(0xFF2F8CFF)
private val MixCore = Color(0xFFFFD36B)
private val MixHalo = Color(0xFFFF8A1F)

private class NeonBitmaps(val srcW: Int, val srcH: Int, val logicCore: ImageBitmap, val logicHalo: ImageBitmap, val mixCore: ImageBitmap, val mixHalo: ImageBitmap)

private fun ByteArray.toWhiteMask(w: Int, h: Int): ImageBitmap {
    val pixels = IntArray(w * h) { i -> ((this[i].toInt() * 2).coerceIn(0, 255) shl 24) or 0xFFFFFF }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * Extra light drawn only on the letters of the logo. The letter shapes are read once from the PNG itself on a
 * background thread (a quarter-size coverage mask), so nothing is darkened and no rectangle is ever filled.
 * Must fill exactly the rectangle in which the PNG is drawn; the layer has no input handling.
 * [pulseTick] changes on every track change and gives MIX a short boost.
 */
@Composable
fun LogicMixNeonOverlay(drawableId: Int, pulseTick: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animated = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val engine = remember { NeonSignEngine(Random.Default) }
    var visible by remember { mutableStateOf(true) }
    var bitmaps by remember { mutableStateOf<NeonBitmaps?>(null) }
    val frame = remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        visible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val observer = LifecycleEventObserver { _, _ -> visible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(drawableId) {
        bitmaps = withContext(Dispatchers.Default) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, drawableId, bounds)
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            val src = BitmapFactory.decodeResource(context.resources, drawableId, options) ?: return@withContext null
            val px = IntArray(src.width * src.height)
            src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
            val m = NeonMasks.build(px, src.width, src.height)
            NeonBitmaps(
                bounds.outWidth, bounds.outHeight,
                m.logicCore.toWhiteMask(m.width, m.height), m.logicHalo.toWhiteMask(m.width, m.height),
                m.mixCore.toWhiteMask(m.width, m.height), m.mixHalo.toWhiteMask(m.width, m.height),
            )
        }
    }
    LaunchedEffect(pulseTick) { if (pulseTick > 0) engine.pulse() }
    LaunchedEffect(animated, visible, bitmaps != null) {
        if (bitmaps == null) return@LaunchedEffect
        if (!animated) {
            engine.advance(0f, false)
            frame.longValue++
            return@LaunchedEffect
        }
        if (!visible) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                engine.advance(dt, true)
                frame.longValue++
            }
        }
    }

    Canvas(modifier) {
        frame.longValue
        val b = bitmaps ?: return@Canvas
        // Same rectangle as Image(ContentScale.Fit): the masks are stretched from their own size onto it.
        val rect = FitRect.fit(size.width, size.height, b.srcW.toFloat(), b.srcH.toFloat())
        val split = rect.mapX(NeonSignGeometry.TUBE_LEFT)
        layer(rect, b.logicHalo, LogicHalo, engine.logic * 1.1f)
        layer(rect, b.logicCore, LogicCore, engine.logic * 0.9f)
        if (engine.tube == engine.mix) {
            layer(rect, b.mixHalo, MixHalo, engine.mix * 1.1f)
            layer(rect, b.mixCore, MixCore, engine.mix * 0.9f)
        } else {
            clipRect(right = split) { layer(rect, b.mixHalo, MixHalo, engine.mix * 1.1f); layer(rect, b.mixCore, MixCore, engine.mix * 0.9f) }
            clipRect(left = split) { layer(rect, b.mixHalo, MixHalo, engine.tube * 1.1f); layer(rect, b.mixCore, MixCore, engine.tube * 0.9f) }
        }
    }
}

private fun DrawScope.layer(rect: FitRect, image: ImageBitmap, color: Color, alpha: Float) {
    if (alpha <= 0.004f) return
    drawImage(
        image,
        srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
        dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
        alpha = alpha.coerceIn(0f, 1f), colorFilter = ColorFilter.tint(color, BlendMode.SrcIn), blendMode = BlendMode.Plus,
    )
}
