package com.scrmoods.app

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The little reward when you answer a question: the button squishes, springs back,
 * and a handful of stars pop out of it. Stars are drawn on a layer over the whole screen,
 * so they can fly past the edges of the cards.
 */
object Sparkles {
    private const val PREFS = "look"
    private const val LIFE_NS = 720_000_000L

    /** Settings switch. */
    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("sparkles", true)
    }

    fun setEnabled(context: Context, on: Boolean) {
        enabled = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("sparkles", on).apply()
    }

    internal class Star(val angle: Float, val dist: Float, val size: Float, val spin: Float, val color: Color, val dot: Boolean)
    internal class Burst(val center: Offset, val reach: Float, val start: Long, val stars: List<Star>)

    internal val bursts = mutableStateListOf<Burst>()

    /** Stars out of the point [center] (screen coordinates); [reach] is about the button's half-size. */
    fun burst(center: Offset, reach: Float, main: Color) {
        if (!enabled) return
        val gold = Color(0xFFE8A93A)
        val palette = listOf(main, main, gold, gold, main)
        val n = 9
        val start = Random.nextFloat() * 6.28f
        val stars = List(n) { i ->
            Star(
                angle = start + i * (2 * PI.toFloat() / n) + (Random.nextFloat() - 0.5f) * 0.5f,
                dist = 0.75f + Random.nextFloat() * 0.6f,
                size = 0.8f + Random.nextFloat() * 0.5f,
                spin = (Random.nextFloat() - 0.5f) * 540f,
                color = palette[Random.nextInt(palette.size)],
                dot = i % 3 == 2,
            )
        }
        bursts += Burst(center, reach, System.nanoTime(), stars)
    }

    internal fun prune(now: Long) {
        bursts.removeAll { now - it.start > LIFE_NS }
    }

    internal fun progress(b: Burst, now: Long) = ((now - b.start).toFloat() / LIFE_NS).coerceIn(0f, 1f)
}

/** Remembers one button's squish animation and where it sits on screen. */
class Pop internal constructor(
    internal val scale: Animatable<Float, AnimationVector1D>,
    private val scope: CoroutineScope,
) {
    private var coords: LayoutCoordinates? = null

    /** Put this on the button, before its clip/background, so the whole button squishes. */
    val modifier: Modifier = Modifier
        .onGloballyPositioned { coords = it }
        .graphicsLayer { val s = scale.value; scaleX = s; scaleY = s }

    /** Squish and spring back; with [stars], also pop stars in [color]. */
    fun fire(stars: Boolean, color: Color) {
        if (Sparkles.enabled) scope.launch {
            scale.animateTo(0.84f, tween(70))
            scale.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMediumLow))
        }
        if (stars) coords?.takeIf { it.isAttached }?.boundsInRoot()?.let { r ->
            Sparkles.burst(r.center, minOf(r.width, r.height) / 2f, color)
        }
    }
}

@Composable
fun rememberPop(): Pop {
    val scope = rememberCoroutineScope()
    return remember { Pop(Animatable(1f), scope) }
}

/** The screen-wide layer the stars are drawn on (touches pass straight through). */
@Composable
fun SparkleLayer() {
    if (Sparkles.bursts.isEmpty()) return
    var now by remember { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(Unit) {
        while (Sparkles.bursts.isNotEmpty()) {
            withFrameNanos { now = System.nanoTime() }
            Sparkles.prune(now)
        }
    }
    val star = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val base = 14.dp.toPx()
        val fly = 38.dp.toPx()
        for (b in Sparkles.bursts.toList()) {
            val p = Sparkles.progress(b, now)
            val ease = 1f - (1f - p).pow(3)          // fast out, gentle stop
            val fade = if (p < 0.5f) 1f else (1f - (p - 0.5f) / 0.5f).pow(1.2f) // fully visible, then fades
            for (s in b.stars) {
                val d = b.reach * 0.7f + fly * s.dist * ease
                val c = Offset(b.center.x + cos(s.angle) * d, b.center.y + sin(s.angle) * d)
                // grow quickly, then shrink away
                val grow = if (p < 0.2f) 0.2f + p / 0.2f * 0.9f else 1.1f - (p - 0.2f) * 0.75f
                val r = base * s.size * grow * 0.5f
                val color = s.color.copy(alpha = s.color.alpha * fade)
                if (s.dot) {
                    drawCircle(color, radius = r * 0.45f, center = c)
                } else {
                    rotate(s.spin * p, pivot = c) {
                        star.reset()
                        for (i in 0 until 10) {
                            val rad = if (i % 2 == 0) r else r * 0.45f
                            val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5
                            val x = c.x + cos(a) * rad
                            val y = c.y + sin(a) * rad
                            if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                        }
                        star.close()
                        drawPath(star, color)
                    }
                }
            }
        }
    }
}
