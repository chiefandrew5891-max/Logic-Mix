package com.akynex.logicmix.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Constraints
import com.akynex.logicmix.R
import kotlin.math.max
import kotlin.math.roundToInt

/** Scroll position of the screen's main scroll container, shared with the backdrop behind it. */
@Stable
class BackdropScroll {
    var state by mutableStateOf<ScrollState?>(null)
}

private val LocalBackdropScroll = staticCompositionLocalOf<BackdropScroll?> { null }

/** The one decoded backdrop bitmap kept for reuse across screens; replaced only when the theme changes. */
private object BackdropBitmapCache {
    private var id = 0
    private var bitmap: ImageBitmap? = null
    fun get(resources: android.content.res.Resources, resId: Int): ImageBitmap {
        bitmap?.let { if (id == resId) return it }
        return ImageBitmap.imageResource(resources, resId).also { id = resId; bitmap = it }
    }
}

/**
 * Drop-in replacement for `verticalScroll(rememberScrollState())` on a screen's main scroll container: scrolls the content
 * and reports its [ScrollState] to the enclosing [LogicMixScreen] so the backdrop can follow proportionally.
 */
@Composable
fun Modifier.backdropVerticalScroll(): Modifier {
    val scroll = rememberScrollState()
    val holder = LocalBackdropScroll.current
    DisposableEffect(holder, scroll) {
        holder?.state = scroll
        onDispose { if (holder?.state === scroll) holder.state = null }
    }
    return verticalScroll(scroll)
}

/**
 * Static premium backdrop (pre-rendered image, separate for dark and light themes) with scroll-linked parallax. The image
 * is scaled to cover the viewport; the scroll position is read only in the layer placement, so scrolling moves one
 * existing layer with a translation: no recomposition, no bitmap work, no timers. Without scrolling nothing is redrawn.
 */
@Composable
fun LogicMixBackdrop(modifier: Modifier = Modifier, scroll: BackdropScroll? = null) {
    val resId = if (LogicMixTokens.colors.isDark) R.drawable.premium_background_dark else R.drawable.premium_background_light
    val bitmap = BackdropBitmapCache.get(LocalContext.current.resources, resId)
    Image(
        bitmap = bitmap, contentDescription = null, contentScale = ContentScale.FillBounds,
        modifier = modifier.clipToBounds().layout { measurable, c ->
            val vw = c.maxWidth
            val vh = c.maxHeight
            val s = max(vw.toFloat() / bitmap.width, vh.toFloat() / bitmap.height)
            val bw = (bitmap.width * s).roundToInt()
            val bh = (bitmap.height * s).roundToInt()
            val p = measurable.measure(Constraints.fixed(bw, bh))
            layout(vw, vh) {
                p.placeWithLayer((vw - bw) / 2, 0) {
                    val st = scroll?.state
                    translationY = if (st == null) 0f else backdropOffsetY(bh.toFloat(), vh.toFloat(), backdropProgress(st.value, st.maxValue))
                }
            }
        },
    )
}

/** Full-screen container: the backdrop sits behind, [content] (with [modifier], e.g. system-bar padding) over it. */
@Composable
fun LogicMixScreen(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val holder = remember { BackdropScroll() }
    CompositionLocalProvider(LocalBackdropScroll provides holder) {
        Box(Modifier.fillMaxSize()) {
            LogicMixBackdrop(Modifier.fillMaxSize(), holder)
            Box(modifier.fillMaxSize(), content = content)
        }
    }
}
