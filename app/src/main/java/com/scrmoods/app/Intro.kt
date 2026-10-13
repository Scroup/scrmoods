package com.scrmoods.app

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * Opening the app: a short welcome, then the journal's header and cards swoosh up from below,
 * fading in one after another.
 */
object Intro {
    private const val PREFS = "look"

    /** When the cards start rising (ms after the app opens), and the gap between one card and the next. */
    const val RISE_START = 950L
    const val STAGGER = 85L

    /** Settings switch. */
    var enabled by mutableStateOf(true)
        private set

    /** True while the opening animation is playing: elements composed now start hidden and rise in. */
    var playing by mutableStateOf(false)
    var showWelcome by mutableStateOf(false)

    fun init(context: Context, freshStart: Boolean) {
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("welcome", true)
        playing = freshStart && enabled
        showWelcome = playing
    }

    fun setEnabled(context: Context, on: Boolean) {
        enabled = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("welcome", on).apply()
    }

    fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
        in 5..11 -> tr("Good morning")
        in 12..16 -> tr("Good afternoon")
        in 17..22 -> tr("Good evening")
        else -> tr("Hello, night owl")
    }
}

private val Swoosh = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Rises from below and fades in during the opening animation; [order] sets when (0 first). */
fun Modifier.reveal(order: Int): Modifier = composed {
    val p = remember { Animatable(if (Intro.playing) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (p.value < 1f) {
            delay(Intro.RISE_START + order * Intro.STAGGER)
            p.animateTo(1f, tween(750, easing = Swoosh))
        }
    }
    graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 90.dp.toPx()
    }
}

/** The welcome card shown for a moment when the app opens. */
@Composable
fun WelcomeOverlay() {
    if (!Intro.showWelcome) return
    val fade = remember { Animatable(1f) }   // the paper cover
    val text = remember { Animatable(0f) }   // the greeting
    LaunchedEffect(Unit) {
        text.animateTo(1f, tween(450, easing = Swoosh))
        delay(Intro.RISE_START - 450 - 80)
        fade.animateTo(0f, tween(480))
        Intro.showWelcome = false
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(Paper),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    alpha = text.value
                    // drifts up into place, and keeps drifting up as it leaves
                    translationY = ((1f - text.value) * 18f - (1f - fade.value) * 30f).dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("✦", fontSize = 18.sp, color = Accent)
            Text(
                Intro.greeting(),
                fontSize = 34.sp, fontWeight = FontWeight.Medium, color = Ink, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                tr("Welcome back. Let's see how today is going."),
                fontSize = 16.sp, lineHeight = 22.sp, fontStyle = FontStyle.Italic, color = InkSoft, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
