package com.scrmoods.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/** One screen of the one-question-at-a-time check-in. */
private sealed class Step {
    class Point(val p: TrackingPoint, val section: String) : Step()
    class Mood(val section: String) : Step()
    object Note : Step()
}

/** Every visible question in journal order, then the day's note. The mixed-features extras stay on the journal page. */
private fun buildSteps(d: AppData): List<Step> {
    val steps = mutableListOf<Step>()
    for (s in d.sections) {
        if (s.hidden) continue
        if (s.id == Sections.BIPOLAR) {
            if (d.points.any { it.id == MoodPoints.SCALE }) steps += Step.Mood(s.name)
            continue
        }
        for (p in d.points) if (p.sectionId == s.id && !p.archived) steps += Step.Point(p, s.name)
    }
    steps += Step.Note
    return steps
}

private fun Step.answered(e: DayEntry): Boolean = when (this) {
    is Step.Point -> !e.values[p.id].isNullOrBlank()
    is Step.Mood -> !e.values[MoodPoints.SCALE].isNullOrBlank()
    Step.Note -> e.note.isNotBlank()
}

private val Swoosh = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * The quick check-in: one question on the screen at a time, tap an answer and the next one slides in.
 * "Skip" in the corner moves on without answering. It ends with the note and the good-night celebration.
 */
@Composable
fun QuickCheckIn(date: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val steps = remember(date) { buildSteps(Repo.data) }
    var index by remember(date) {
        val e = Repo.entry(date)
        mutableIntStateOf(steps.indexOfFirst { !it.answered(e) }.let { if (it < 0) steps.lastIndex else it })
    }
    var forward by remember { mutableStateOf(true) }
    var advancing by remember { mutableStateOf(false) } // ignore taps while the next question is on its way
    val entry = Repo.entry(date)

    fun go(to: Int) {
        forward = to >= index
        index = to.coerceIn(0, steps.lastIndex)
        advancing = false
    }
    fun next() = go(index + 1)
    fun answeredThenNext() {
        if (advancing) return
        advancing = true
        scope.launch { delay(420); next() }
    }
    fun finish(note: String) {
        Repo.setNote(date, note)
        onClose()
        if (GoodNight.enabled && GoodNight.finished != date) GoodNight.celebrate(context, date)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .pointerInput(Unit) { detectTapGestures { } }, // keeps taps off the journal underneath
    ) {
        // top: close, progress, skip
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Haptics.tap(); onClose() }) { Icon(Icons.Filled.Close, tr("Close"), tint = InkSoft) }
            val progress by animateFloatAsState((index + 1f) / steps.size, tween(400), label = "progress")
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Rule)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(3.dp)).background(Accent))
            }
            if (steps[index] !is Step.Note) {
                Text(
                    tr("Skip"),
                    fontSize = 14.sp, fontStyle = FontStyle.Italic, color = InkSoft,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { Haptics.tap(); next() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            } else Spacer(Modifier.width(16.dp))
        }
        Text(
            tr("{0} of {1}", index + 1, steps.size),
            fontSize = 12.5.sp, fontStyle = FontStyle.Italic, color = InkFaint,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        // the question, sliding in
        key(index) {
            val a = remember { Animatable(0f) }
            LaunchedEffect(Unit) { a.animateTo(1f, tween(420, easing = Swoosh)) }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = a.value
                        translationX = (1f - a.value) * (if (forward) 48 else -48).dp.toPx()
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                when (val s = steps[index]) {
                    is Step.Mood -> MoodStep(s.section, entry.values[MoodPoints.SCALE]?.toFloatOrNull()?.toInt()) { n ->
                        Repo.setValue(date, MoodPoints.SCALE, n.toString())
                        if (entry.values[MoodPoints.MIXED] != "1") Repo.setValue(date, MoodPoints.SCALE2, null)
                        if (!advancing) { advancing = true; scope.launch { delay(900); next() } }
                    }
                    is Step.Point -> when (s.p.type) {
                        PointType.SCALE, PointType.YESNO -> ChoiceStep(s, entry.values[s.p.id]) { v ->
                            Repo.setValue(date, s.p.id, v)
                            answeredThenNext()
                        }
                        PointType.NUMBER -> NumberStep(s, date, entry.values[s.p.id]) { v ->
                            Repo.setValue(date, s.p.id, v)
                            next()
                        }
                        PointType.TEXT -> TextStep(s, entry.values[s.p.id]) { v ->
                            Repo.setValue(date, s.p.id, v.ifBlank { null })
                            next()
                        }
                    }
                    Step.Note -> NoteStep(entry.note, finishedBefore = GoodNight.finished == date) { finish(it) }
                }
            }
        }

        // bottom: back
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (index > 0) LinkButton("‹ " + tr("Previous")) { go(index - 1) }
        }
    }
}

@Composable
private fun Kicker(text: String) {
    Text(text, fontSize = 15.sp, fontStyle = FontStyle.Italic, color = Accent)
}

@Composable
private fun BigQuestion(text: String) {
    Text(
        text, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium, color = Ink,
        modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
    )
}

/** Answers stacked as big pills; tapping one picks it (with the star pop) and moves on. */
@Composable
private fun ChoiceStep(s: Step.Point, value: String?, onPick: (String) -> Unit) {
    Kicker(s.section)
    BigQuestion(s.p.title)
    val options: List<Pair<Int, String>> =
        if (s.p.type == PointType.YESNO) listOf(1 to "Yes", 0 to "No") else s.p.options.mapIndexed { i, o -> i to o }
    val selected = value?.toIntOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((v, label) in options) {
            val sel = v == selected
            // red for the bad end, green for the good end (following the question's "Which is better?")
            val c = s.p.goodness(v.toFloat())?.let { answerColor(it) } ?: Accent
            val pop = rememberPop()
            val shape = RoundedCornerShape(50)
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .then(pop.modifier)
                    .clip(shape)
                    .background(if (sel) c else c.copy(alpha = 0.13f).compositeOver(Page))
                    .border(if (sel) 1.dp else 1.5.dp, if (sel) c else c.copy(alpha = 0.55f), shape)
                    .clickable {
                        Haptics.tap()
                        pop.fire(stars = true, color = c)
                        onPick(v.toString())
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tr(label), fontSize = 18.sp, textAlign = TextAlign.Center,
                    fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                    color = if (sel) Color.White else Ink,
                )
            }
        }
    }
}

private val AnswerBad = Color(0xFFC9584B)
private val AnswerMid = Color(0xFFD9A040)
private val AnswerGood = Color(0xFF5F9E55)

/** 0 = bad (red), 0.5 = in between (amber), 1 = good (green). */
private fun answerColor(g: Float): Color =
    if (g < 0.5f) lerp(AnswerBad, AnswerMid, g * 2f) else lerp(AnswerMid, AnswerGood, (g - 0.5f) * 2f)

/** The 0–10 mood scale as two rows of big circles, with the chosen state described underneath. */
@Composable
private fun MoodStep(section: String, value: Int?, onPick: (Int) -> Unit) {
    Kicker(section)
    BigQuestion(tr("Where is your mood today?"))
    for (row in listOf(0..5, 6..10)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            for (n in row) {
                val c = MoodScale.zoneColor(n)
                val sel = n == value
                val pop = rememberPop()
                Box(
                    Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 54.dp)
                        .aspectRatio(1f)
                        .then(pop.modifier)
                        .clip(CircleShape)
                        .background(if (sel) c else Page)
                        .border(1.5.dp, if (sel) c else c.copy(alpha = 0.55f), CircleShape)
                        .clickable { Haptics.tap(); pop.fire(stars = true, color = c); onPick(n) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(n.toString(), fontSize = 17.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = if (sel) Color.White else Ink)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text(tr("0 depression"), fontSize = 12.5.sp, fontStyle = FontStyle.Italic, color = InkSoft, modifier = Modifier.weight(1f))
        Text(tr("5 balanced"), fontSize = 12.5.sp, fontStyle = FontStyle.Italic, color = InkSoft, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text(tr("10 mania"), fontSize = 12.5.sp, fontStyle = FontStyle.Italic, color = InkSoft, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
    // the chosen state, in the same fixed-height space so nothing jumps
    Box(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(top = 18.dp), contentAlignment = Alignment.TopCenter) {
        if (value != null) {
            val level = MoodScale.level(value)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$value · ${level.name}", fontSize = 19.sp, fontWeight = FontWeight.Medium, color = MoodScale.zoneColor(value))
                Text(
                    level.description, fontSize = 14.5.sp, lineHeight = 20.sp, fontStyle = FontStyle.Italic, color = InkSoft,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** A number, e.g. hours of sleep: a big value with − / + and a slider, then Next. */
@Composable
private fun NumberStep(s: Step.Point, date: String, value: String?, onDone: (String) -> Unit) {
    val isWater = s.p.id == Water.POINT
    // water can go past the goal, so its slider reaches further
    val p = if (isWater) s.p.copy(max = maxOf(s.p.max * 2f, 12f)) else s.p
    val step = if (p.step > 0f) p.step else 1f
    fun snap(f: Float): Float = (p.min + ((f - p.min) / step).roundToInt() * step).coerceIn(p.min, p.max)
    // start from today's answer, else yesterday's, else the middle of the range
    val start = remember(p.id, date) {
        value?.toFloatOrNull()
            ?: (if (isWater) 0f else null)
            ?: runCatching { Repo.entry(LocalDate.parse(date).minusDays(1).toString()).values[p.id]?.toFloatOrNull() }.getOrNull()
            ?: snap((p.min + p.max) / 2f)
    }
    var v by remember(p.id, date) { mutableFloatStateOf(start) }

    Kicker(s.section)
    BigQuestion(p.title)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        Text(formatNumber(v), fontSize = 52.sp, fontWeight = FontWeight.Medium, color = Accent)
        if (p.unit.isNotBlank()) Text(" " + tr(p.unit), fontSize = 20.sp, fontStyle = FontStyle.Italic, color = InkSoft, modifier = Modifier.padding(bottom = 10.dp))
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(onClick = { v = snap(v - step) }) { Text("−", fontSize = 22.sp, color = Ink) }
        val steps = (((p.max - p.min) / step).roundToInt() - 1).coerceAtLeast(0)
        Slider(
            value = v,
            onValueChange = { v = snap(it) },
            valueRange = p.min..p.max,
            steps = if (steps <= 200) steps else 0,
            colors = SliderDefaults.colors(
                thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Rule,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        RoundButton(onClick = { v = snap(v + step) }) { Text("+", fontSize = 22.sp, color = Ink) }
    }
    NextButton(tr("Next"), Modifier.padding(top = 28.dp)) { onDone(formatNumber(v)) }
}

@Composable
private fun TextStep(s: Step.Point, value: String?, onDone: (String) -> Unit) {
    var text by remember(s.p.id) { mutableStateOf(value ?: "") }
    Kicker(s.section)
    BigQuestion(s.p.title)
    OutlinedTextField(
        value = text, onValueChange = { text = it },
        textStyle = TextStyle(fontFamily = Lora, fontSize = 16.sp, color = Ink),
        colors = journalFieldColors(), shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
    )
    NextButton(tr("Next"), Modifier.padding(top = 20.dp)) { onDone(text.trim()) }
}

/** Last page: the day's note, then the good-night celebration. */
@Composable
private fun NoteStep(saved: String, finishedBefore: Boolean, onFinish: (String) -> Unit) {
    val focus = LocalFocusManager.current
    var text by remember { mutableStateOf(saved) }
    Kicker(tr("Today's note"))
    BigQuestion(tr("Anything you'd like to remember about today?"))
    OutlinedTextField(
        value = text, onValueChange = { text = it },
        placeholder = { Text(tr("Write about your day…"), fontStyle = FontStyle.Italic, color = InkFaint) },
        textStyle = TextStyle(fontFamily = Lora, fontSize = 16.sp, lineHeight = 23.sp, color = Ink),
        colors = journalFieldColors(), shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
    )
    NextButton(if (finishedBefore) tr("Save and close") else tr("Finish the day") + "  ☾", Modifier.padding(top = 20.dp)) {
        focus.clearFocus()
        onFinish(text)
    }
}

@Composable
private fun NextButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = { Haptics.tap(); onClick() },
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
        shape = RoundedCornerShape(50),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text(text, fontSize = 17.sp) }
}

/** The button at the top of the journal that opens the quick check-in. */
@Composable
fun QuickCheckInButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .padding(start = 10.dp, end = 10.dp, top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Accent)
            .clickable { Haptics.tap(); onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tr("Quick check-in"), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = OnAccent)
            Text(
                tr("One question at a time, no distractions"),
                fontSize = 13.5.sp, fontStyle = FontStyle.Italic, color = OnAccent.copy(alpha = 0.85f),
            )
        }
        Box(
            Modifier.width(34.dp).aspectRatio(1f).clip(CircleShape).background(OnAccent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Text("→", fontSize = 17.sp, color = OnAccent) }
    }
}
