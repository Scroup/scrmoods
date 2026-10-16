package com.scrmoods.app

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The water point (or null when it's missing or removed). */
fun waterPoint(d: AppData = Repo.data): TrackingPoint? = d.points.firstOrNull { it.id == Water.POINT && !it.archived }

/** The daily goal in cups (kept as the water point's maximum). */
/** Same range as the goal setting (1–30), so a damaged file can't ask for a million drops. */
val TrackingPoint.goalCups: Int get() = max.toInt().coerceIn(1, 30)

/**
 * A row of cups to tap through the day: tap an empty cup to fill up to it, tap the last full cup to take it back.
 * "+" adds a cup past the goal, "−" takes one away.
 */
@Composable
fun WaterTracker(p: TrackingPoint, value: String?, dateKey: String, gutter: androidx.compose.ui.unit.Dp) {
    val drunk = (value?.toFloatOrNull()?.toInt() ?: 0).coerceIn(0, 60) // drawn one drop each
    val goal = p.goalCups
    fun set(n: Int) = Repo.setValue(dateKey, p.id, if (n <= 0) null else n.toString())

    Column(Modifier.fillMaxWidth().padding(horizontal = gutter).padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$drunk", fontSize = 30.sp, fontWeight = FontWeight.Medium, color = Accent)
                    Text(
                        " / $goal " + trp(goal, "cup", "cups"),
                        fontSize = 16.sp, fontStyle = FontStyle.Italic, color = InkSoft, modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
                Text(
                    tr("{0} L of {1} L", Water.litres(drunk.toFloat()), Water.litres(goal.toFloat())),
                    fontSize = 13.5.sp, fontStyle = FontStyle.Italic, color = InkSoft,
                )
            }
            RoundButton(enabled = drunk > 0, onClick = { set(drunk - 1) }) { Text("−", fontSize = 22.sp, color = if (drunk > 0) Ink else Rule) }
            Box(Modifier.width(8.dp))
            RoundButton(onClick = { set(drunk + 1) }) { Text("+", fontSize = 22.sp, color = Ink) }
        }

        // the drops (one per cup), all in one row: they shrink to fit when there are many
        val count = maxOf(goal, drunk)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until count) {
                val full = i < drunk
                val pop = rememberPop()
                Box(
                    Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 28.dp)
                        .aspectRatio(28f / 34f)
                        .then(pop.modifier)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            Haptics.tap()
                            val n = if (i + 1 == drunk) i else i + 1
                            pop.fire(stars = n > drunk, color = Accent)
                            set(n)
                        },
                ) { Drop(full, extra = i >= goal) }
            }
        }
        Text(
            when {
                drunk >= goal -> tr("Goal reached. Well done!")
                drunk == 0 -> tr("Tap a cup each time you drink a glass (200 ml).")
                else -> tr("{0} more to go.", "${goal - drunk} " + trp(goal - drunk, "cup", "cups"))
            },
            fontSize = 13.5.sp, fontStyle = FontStyle.Italic, color = if (drunk >= goal) Accent else InkSoft,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * One little water drop with a happy face, drawn on a 28 × 34 grid (the web app and the widget use the same shape).
 * Empty: just the outline. Drunk: filled with the accent colour, with a shine. Drops past the goal are a little lighter.
 */
@Composable
private fun Drop(full: Boolean, extra: Boolean) {
    val accent = Accent
    val face = OnAccent
    val line = if (full) Accent else InkFaint
    Canvas(Modifier.fillMaxSize()) {
        val sx = size.width / 28f
        val sy = size.height / 34f
        fun o(x: Float, y: Float) = Offset(x * sx, y * sy)
        val drop = Path().apply {
            moveTo(14f * sx, 2f * sy)
            cubicTo(17f * sx, 7f * sy, 24f * sx, 14f * sy, 24f * sx, 22f * sy)
            cubicTo(24f * sx, 27.5f * sy, 19.5f * sx, 32f * sy, 14f * sx, 32f * sy)
            cubicTo(8.5f * sx, 32f * sy, 4f * sx, 27.5f * sy, 4f * sx, 22f * sy)
            cubicTo(4f * sx, 14f * sy, 11f * sx, 7f * sy, 14f * sx, 2f * sy)
            close()
        }
        if (full) {
            drawPath(drop, accent.copy(alpha = if (extra) 0.5f else 0.85f))
            // shine
            val shine = Path().apply { moveTo(8.2f * sx, 17f * sy); quadraticTo(8.6f * sx, 13.5f * sy, 10.8f * sx, 10.8f * sy) }
            drawPath(shine, face.copy(alpha = 0.75f), style = Stroke(width = 1.6f * sx, cap = StrokeCap.Round))
            // a little face
            drawCircle(face.copy(alpha = 0.9f), radius = 1.15f * sx, center = o(11f, 22.5f))
            drawCircle(face.copy(alpha = 0.9f), radius = 1.15f * sx, center = o(17f, 22.5f))
            val smile = Path().apply { moveTo(11.8f * sx, 25.6f * sy); quadraticTo(14f * sx, 27.8f * sy, 16.2f * sx, 25.6f * sy) }
            drawPath(smile, face.copy(alpha = 0.9f), style = Stroke(width = 1.3f * sx, cap = StrokeCap.Round))
        }
        drawPath(drop, line, style = Stroke(width = 1.7f * sx, join = StrokeJoin.Round))
    }
}

/** Settings → Water: the switch, the daily goal and a weight-based suggestion. */
@Composable
fun WaterSettings(settingSwitch: @Composable (String, String, Boolean, (Boolean) -> Unit) -> Unit) {
    val context = LocalContext.current
    val section = Repo.data.sections.firstOrNull { it.id == Sections.WATER } ?: return
    val p = waterPoint() ?: return
    settingSwitch(
        tr("Water tracker"),
        tr("Tap a cup for every glass (200 ml) you drink. During the day it sits at the top of the journal."),
        !section.hidden,
    ) { Repo.setSectionHidden(Sections.WATER, !it) }
    if (section.hidden) return

    val goal = p.goalCups
    fun setGoal(n: Int) = Repo.editPoint(p.copy(max = n.coerceIn(1, 30).toFloat()))
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tr("Daily goal"), fontSize = 16.sp, color = Ink)
            Text(
                "$goal " + trp(goal, "cup", "cups") + " · " + tr("{0} L", Water.litres(goal.toFloat())),
                fontSize = 13.sp, fontStyle = FontStyle.Italic, color = InkSoft,
            )
        }
        RoundButton(enabled = goal > 1, onClick = { Haptics.tap(); setGoal(goal - 1) }) { Text("−", fontSize = 22.sp, color = Ink) }
        Box(Modifier.width(8.dp))
        RoundButton(onClick = { Haptics.tap(); setGoal(goal + 1) }) { Text("+", fontSize = 22.sp, color = Ink) }
    }

    val prefs = context.getSharedPreferences("look", Context.MODE_PRIVATE)
    var weight by remember { mutableStateOf(prefs.getString("water_weight", "") ?: "") }
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
        OutlinedTextField(
            value = weight,
            onValueChange = { v ->
                weight = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5)
                prefs.edit().putString("water_weight", weight).apply()
            },
            label = { Text(tr("Your weight, kg")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = journalFieldColors(),
            modifier = Modifier.width(180.dp),
        )
        val kg = weight.replace(',', '.').toFloatOrNull()?.takeIf { it in 25f..250f }
        if (kg != null) {
            val rec = Water.recommendedCups(kg)
            Text(
                tr("Suggested: about {0} {1} ({2} L) a day.", rec, trp(rec, "cup", "cups"), Water.litres(rec.toFloat())),
                fontSize = 14.sp, color = Ink, modifier = Modifier.padding(top = 8.dp),
            )
            if (rec != goal) LinkButton(tr("Use {0} {1} as my goal", rec, trp(rec, "cup", "cups")), Modifier.padding(start = 0.dp)) { setGoal(rec) }
        }
        Text(
            tr("A rule of thumb: about 30 ml per kg of body weight, counting all drinks. Heat and exercise mean more. If you take lithium or have heart or kidney problems, ask your doctor how much is right for you."),
            fontSize = 12.5.sp, lineHeight = 17.sp, fontStyle = FontStyle.Italic, color = InkFaint,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
