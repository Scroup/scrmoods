package com.scrmoods.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import java.time.LocalDate
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The good-night moment: tap "Finish the day" under the note and the journal fills with
 * twinkling confetti, a short lullaby plays, and the app wishes you good night.
 */
object GoodNight {
    private const val PREFS = "look"

    /** Settings switches. */
    var enabled by mutableStateOf(true)
        private set
    var melody by mutableStateOf(true)
        private set

    /** The date being celebrated while the overlay is on screen. */
    var showing by mutableStateOf<String?>(null)

    /** The last day you tapped "Finish the day" for (the button then shows it's done). */
    var finished by mutableStateOf<String?>(null)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean("goodnight", true)
        melody = prefs.getBoolean("goodnight_melody", true)
        finished = prefs.getString("goodnight_last", null)
    }

    fun setEnabled(context: Context, on: Boolean) {
        enabled = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("goodnight", on).apply()
    }

    fun setMelody(context: Context, on: Boolean) {
        melody = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("goodnight_melody", on).apply()
    }

    /**
     * True when every visible question has an answer and the note isn't empty.
     * The mixed-features switch and the second mood number are optional, so they don't count.
     */
    fun isComplete(d: AppData, e: DayEntry): Boolean {
        if (e.note.isBlank()) return false
        for (s in d.sections) {
            if (s.hidden) continue
            for (p in d.points) {
                if (p.sectionId != s.id || p.archived) continue
                if (p.id == MoodPoints.SCALE2 || p.id == MoodPoints.MIXED) continue
                if (e.values[p.id].isNullOrBlank()) return false
            }
        }
        return true
    }

    /** Shows the overlay and plays the melody. [remember] marks the day as finished. */
    fun celebrate(context: Context, date: String, remember: Boolean = true) {
        if (remember) {
            finished = date
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("goodnight_last", date).apply()
        }
        showing = date
        if (melody) Lullaby.play()
    }

    private val wishes = listOf(
        "Today is written down. You can let it rest now.",
        "Thank you for checking in with yourself today.",
        "Whatever today was, it's safely kept. Sleep softly.",
        "You showed up for yourself today. Rest well.",
        "Tomorrow is a fresh page. Sweet dreams.",
        "Breathe out slowly. The day is done.",
        "Be as gentle with yourself tonight as you'd be with a friend.",
    )

    fun wishFor(date: String): String {
        val day = runCatching { LocalDate.parse(date).toEpochDay() }.getOrDefault(0L)
        return wishes[Math.floorMod(day, wishes.size.toLong()).toInt()]
    }
}

/** A short music-box lullaby, synthesised on the phone (no sound files needed). */
object Lullaby {
    private const val RATE = 22050
    private const val BEAT = 0.42

    // start (beats), MIDI note, length (beats), loudness, soft pad?
    private class Note(val start: Double, val midi: Int, val len: Double, val gain: Double, val pad: Boolean = false)

    private val notes = listOf(
        // melody
        Note(0.0, 67, 1.0, 0.55), Note(1.0, 72, 1.0, 0.55), Note(2.0, 76, 1.0, 0.55), Note(3.0, 79, 1.5, 0.6),
        Note(4.5, 81, 0.5, 0.45), Note(5.0, 79, 1.0, 0.5), Note(6.0, 76, 1.0, 0.5), Note(7.0, 74, 2.0, 0.55),
        Note(9.0, 76, 1.0, 0.5), Note(10.0, 72, 1.0, 0.5), Note(11.0, 74, 1.0, 0.5), Note(12.0, 72, 4.0, 0.6),
        // soft chords underneath: C, A minor, F, G, C
        Note(0.0, 48, 4.0, 0.22, true), Note(0.0, 55, 4.0, 0.16, true),
        Note(4.0, 45, 3.0, 0.22, true), Note(4.0, 52, 3.0, 0.16, true),
        Note(7.0, 41, 2.0, 0.22, true), Note(7.0, 57, 2.0, 0.14, true),
        Note(9.0, 43, 3.0, 0.22, true), Note(9.0, 50, 3.0, 0.16, true),
        Note(12.0, 48, 4.0, 0.22, true), Note(12.0, 55, 4.0, 0.16, true), Note(12.0, 64, 4.0, 0.12, true),
    )

    /** The lullaby as 16-bit mono samples. */
    fun render(): ShortArray {
        val total = (RATE * (16 * BEAT + 2.2)).toInt()
        val out = DoubleArray(total)
        for (n in notes) {
            val f = 440.0 * 2.0.pow((n.midi - 69) / 12.0)
            val start = (n.start * BEAT * RATE).toInt()
            val tau = if (n.pad) 2.4 else 1.0
            val attack = if (n.pad) 0.09 else 0.006
            val held = n.len * BEAT
            val length = ((held + tau * 2.5) * RATE).toInt()
            for (i in 0 until length) {
                val idx = start + i
                if (idx >= total) break
                val t = i.toDouble() / RATE
                var env = (1 - exp(-t / attack)) * exp(-t / tau)
                if (t > held) env *= exp(-(t - held) / 0.5) // let go of the note gently
                val w = 2 * PI * f * t
                val s = sin(w) + 0.28 * sin(2 * w) * exp(-t / 0.35) + 0.08 * sin(3 * w) * exp(-t / 0.15)
                out[idx] += s * env * n.gain
            }
        }
        // a soft echo for a little space
        val d = (0.31 * RATE).toInt()
        for (i in d until total) out[i] += out[i - d] * 0.27
        var peak = 1e-9
        for (v in out) peak = maxOf(peak, abs(v))
        val fade = (0.6 * RATE).toInt()
        return ShortArray(total) { i ->
            var v = out[i] / peak * 0.42
            if (i > total - fade) v *= (total - i).toDouble() / fade
            (v * Short.MAX_VALUE).toInt().toShort()
        }
    }

    fun play() {
        thread(name = "lullaby") {
            try {
                val pcm = render()
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(pcm.size * 1000L / RATE + 300)
                track.release()
            } catch (_: Exception) {
                // no sound is fine; the rest of the celebration still shows
            }
        }
    }
}

private class Piece(
    val x: Float, val delay: Float, val speed: Float, val sway: Float, val swayRate: Float,
    val rot: Float, val spin: Float, val flip: Float, val color: Color,
    val w: Float, val h: Float, val phase: Float, val round: Boolean,
)

/** Full-screen confetti and the good-night card. Tap anywhere to close; it also fades out by itself. */
@Composable
fun GoodNightOverlay() {
    val date = GoodNight.showing ?: return
    val fade = remember(date) { Animatable(0f) }
    var closing by remember(date) { mutableStateOf(false) }
    var t by remember(date) { mutableFloatStateOf(0f) }

    val colors = listOf(
        Accent, AccentWash, Color(0xFFE8B04A), Color(0xFFF0C987), Color(0xFFE88B7A),
        Color(0xFFF2B8B0), Color(0xFF9DBE8F), Color(0xFFC9A6D9), Color(0xFFFFF3D6),
    )
    val pieces = remember(date) {
        val r = Random(date.hashCode())
        List(120) {
            Piece(
                x = r.nextFloat(), delay = r.nextFloat() * 4.5f, speed = 0.11f + r.nextFloat() * 0.13f,
                sway = 0.015f + r.nextFloat() * 0.03f, swayRate = 1.2f + r.nextFloat() * 1.6f,
                rot = r.nextFloat() * 360f, spin = -160f + r.nextFloat() * 320f, flip = 2f + r.nextFloat() * 4f,
                color = colors[r.nextInt(colors.size)],
                w = 14f + r.nextFloat() * 14f, h = 8f + r.nextFloat() * 8f,
                phase = r.nextFloat() * 6.28f, round = r.nextInt(4) == 0,
            )
        }
    }

    LaunchedEffect(date) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1e9f }
    }
    LaunchedEffect(date) {
        fade.animateTo(1f, tween(700))
        kotlinx.coroutines.delay(10_000)
        closing = true
    }
    LaunchedEffect(closing) {
        if (closing) {
            fade.animateTo(0f, tween(900))
            GoodNight.showing = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(Color(0xFF2A1C17).copy(alpha = 0.28f))
            .pointerInput(date) { detectTapGestures { closing = true } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // a soft glow that breathes behind the card
            val glow = 0.35f + 0.15f * sin(t * 1.6f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFE7B8).copy(alpha = glow), Color.Transparent),
                    center = center, radius = size.minDimension * 0.75f,
                ),
                radius = size.minDimension * 0.75f, center = center,
            )
            for (p in pieces) {
                val life = t - p.delay
                if (life < 0f) continue
                val y = (-0.04f + p.speed * life) * size.height
                if (y > size.height + 40f) continue
                val x = (p.x + sin(life * p.swayRate + p.phase) * p.sway) * size.width
                // gentle twinkle (about once a second, never a hard strobe)
                val twinkle = 0.5f + 0.5f * sin(life * 6.5f + p.phase)
                val c = p.color.copy(alpha = 0.45f + 0.55f * twinkle)
                val w = p.w.dp.toPx() / 2.2f
                val h = p.h.dp.toPx() / 2.2f
                if (p.round) {
                    drawCircle(c, radius = h * 0.6f, center = Offset(x, y))
                } else {
                    val flipW = w * abs(cos(life * p.flip + p.phase)).coerceAtLeast(0.15f)
                    rotate(p.rot + p.spin * life, pivot = Offset(x, y)) {
                        drawRect(c, topLeft = Offset(x - flipW / 2, y - h / 2), size = Size(flipW, h))
                    }
                }
            }
        }

        val pop = (fade.value * 0.08f + 0.92f)
        Column(
            Modifier
                .padding(horizontal = 32.dp)
                .widthIn(max = 380.dp)
                .graphicsLayer { scaleX = pop; scaleY = pop }
                .clip(RoundedCornerShape(26.dp))
                .background(Page)
                .padding(horizontal = 26.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("☾", fontSize = 42.sp, color = Accent)
            Text(
                "Good night",
                fontSize = 34.sp, fontWeight = FontWeight.Medium, color = Ink,
                modifier = Modifier.padding(top = 4.dp),
            )
            val complete = GoodNight.isComplete(Repo.data, Repo.data.entries[date] ?: DayEntry(date))
            Text(
                if (complete) "Today's journal is complete." else "Today is saved.",
                fontSize = 15.sp, fontStyle = FontStyle.Italic, color = Accent,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                GoodNight.wishFor(date),
                fontSize = 17.sp, lineHeight = 25.sp, color = Ink, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
            Text(
                "Tap anywhere to close",
                fontSize = 13.sp, fontStyle = FontStyle.Italic, color = InkFaint,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
    }
}
