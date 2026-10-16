package com.scrmoods.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The meals point and the snacks point (or null when missing or removed). */
fun mealsPoint(d: AppData = Repo.data): TrackingPoint? = d.points.firstOrNull { it.id == Meals.MEALS && !it.archived }
fun snacksPoint(d: AppData = Repo.data): TrackingPoint? = d.points.firstOrNull { it.id == Meals.SNACKS && !it.archived }

/** The meals a day goal (kept as the meals point's maximum). */
val TrackingPoint.goalMeals: Int get() = max.toInt().coerceIn(1, 8)

/**
 * A row of little icons to tap: tap an empty one to fill up to it, tap the last filled one to take it back.
 * Used for meals (bowls) and snacks (cookies).
 */
@Composable
fun TapRow(count: Int, filled: Int, size: Dp, spacing: Dp = 4.dp, icon: @Composable (Boolean) -> Unit, onSet: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
        for (i in 0 until count) {
            val pop = rememberPop()
            Box(
                Modifier
                    .then(pop.modifier)
                    .size(size)
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        Haptics.tap()
                        val next = if (i + 1 == filled) i else i + 1
                        pop.fire(stars = next > filled, color = Accent)
                        onSet(next)
                    },
            ) { icon(i < filled) }
        }
    }
}

/** A little bowl with a happy face; when eaten, filled, with a heap of food and a wisp of steam. */
@Composable
fun Bowl(full: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    val accent = Accent
    val light = Accent.copy(alpha = 0.38f).compositeOver(Page)
    val face = OnAccent
    val line = if (full) Accent else InkFaint
    Canvas(modifier) {
        val s = size.width / 32f
        fun o(x: Float, y: Float) = Offset(x * s, y * s)
        val body = Path().apply {
            moveTo(4f * s, 15f * s); lineTo(28f * s, 15f * s)
            quadraticTo(28f * s, 27f * s, 16f * s, 27f * s); quadraticTo(4f * s, 27f * s, 4f * s, 15f * s); close()
        }
        if (full) {
            val heap = Path().apply { moveTo(7f * s, 15f * s); quadraticTo(16f * s, 6.5f * s, 25f * s, 15f * s); close() }
            drawPath(heap, light)
            drawPath(heap, accent, style = Stroke(1.4f * s, join = StrokeJoin.Round))
            for (x in listOf(12f, 20f)) {
                val steam = Path().apply { moveTo(x * s, 7.5f * s); quadraticTo((x - 1.6f) * s, 5.5f * s, x * s, 3.2f * s) }
                drawPath(steam, accent.copy(alpha = 0.55f), style = Stroke(1.4f * s, cap = StrokeCap.Round))
            }
            drawPath(body, accent.copy(alpha = 0.9f))
            drawCircle(face, radius = 1.15f * s, center = o(12.6f, 19.6f))
            drawCircle(face, radius = 1.15f * s, center = o(19.4f, 19.6f))
            val smile = Path().apply { moveTo(14f * s, 22.2f * s); quadraticTo(16f * s, 24f * s, 18f * s, 22.2f * s) }
            drawPath(smile, face, style = Stroke(1.3f * s, cap = StrokeCap.Round))
        }
        drawPath(body, line, style = Stroke(1.7f * s, join = StrokeJoin.Round))
        drawLine(line, o(12f, 29.5f), o(20f, 29.5f), strokeWidth = 1.7f * s, cap = StrokeCap.Round)
    }
}

/** A chocolate-chip cookie for a snack. */
@Composable
fun Cookie(full: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    val accent = Accent
    val light = Accent.copy(alpha = 0.32f).compositeOver(Page)
    val line = if (full) Accent else InkFaint
    Canvas(modifier) {
        val s = size.width / 32f
        val c = Offset(16f * s, 16f * s)
        if (full) drawCircle(light, radius = 11f * s, center = c)
        drawCircle(line, radius = 11f * s, center = c, style = Stroke(1.7f * s))
        val chips = listOf(12f to 12f, 19.5f to 12.5f, 14.5f to 19f, 20.5f to 19.5f, 10.5f to 16.5f)
        for ((x, y) in chips) drawCircle(if (full) accent else InkFaint.copy(alpha = 0.6f), radius = (if (full) 1.6f else 1.1f) * s, center = Offset(x * s, y * s))
    }
}

/** The meals card in the journal: a bowl per meal of the day and a few snack cookies. */
@Composable
fun MealsTracker(meals: TrackingPoint, snacks: TrackingPoint?, values: Map<String, String>, dateKey: String, gutter: Dp) {
    val goal = meals.goalMeals
    val eaten = (values[meals.id]?.toFloatOrNull()?.toInt() ?: 0).coerceIn(0, 20) // drawn one bowl each
    Column(Modifier.fillMaxWidth().padding(horizontal = gutter).padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Question(tr("Meals"))
                Text(tr("{0} of {1}", eaten, goal), fontSize = 13.sp, fontStyle = FontStyle.Italic, color = if (eaten >= goal) Accent else InkSoft)
            }
            TapRow(maxOf(goal, eaten), eaten, if (goal > 4) 34.dp else 40.dp, icon = { Bowl(it) }) { n ->
                Repo.setValue(dateKey, meals.id, if (n <= 0) "0" else n.toString())
            }
        }
        if (snacks != null) {
            val had = values[snacks.id]?.toFloatOrNull()?.toInt() ?: 0
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Question(tr("Snacks"))
                    Text(if (had == 0) tr("none yet") else "$had", fontSize = 13.sp, fontStyle = FontStyle.Italic, color = InkSoft)
                }
                // always one more empty cookie to tap (up to 8)
                TapRow(minOf(8, maxOf(snacks.max.toInt(), had + 1)), had, 34.dp, icon = { Cookie(it) }) { n ->
                    Repo.setValue(dateKey, snacks.id, if (n <= 0) "0" else n.toString())
                }
            }
        }
        Text(
            when {
                eaten >= goal -> tr("All your meals are in. Nicely done!")
                eaten == 0 -> tr("Tap a bowl after each meal.")
                else -> tr("{0} more to go.", "${goal - eaten} " + trp(goal - eaten, "meal", "meals"))
            },
            fontSize = 13.5.sp, fontStyle = FontStyle.Italic, color = if (eaten >= goal) Accent else InkSoft,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** Settings → Meals: the switch and the meals a day goal. */
@Composable
fun MealsSettings(settingSwitch: @Composable (String, String, Boolean, (Boolean) -> Unit) -> Unit) {
    val section = Repo.data.sections.firstOrNull { it.id == Sections.MEALS } ?: return
    val p = mealsPoint() ?: return
    settingSwitch(
        tr("Meal tracker"),
        tr("Tap a bowl after each meal and a cookie for each snack, to see that you eat enough to get through the day."),
        !section.hidden,
    ) { Repo.setSectionHidden(Sections.MEALS, !it) }
    if (section.hidden) return
    val goal = p.goalMeals
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tr("Meals a day"), fontSize = 16.sp, color = Ink)
            Text("$goal " + trp(goal, "meal", "meals"), fontSize = 13.sp, fontStyle = FontStyle.Italic, color = InkSoft)
        }
        RoundButton(enabled = goal > 1, onClick = { Haptics.tap(); Repo.editPoint(p.copy(max = (goal - 1).toFloat())) }) { Text("−", fontSize = 22.sp, color = Ink) }
        Box(Modifier.width(8.dp))
        RoundButton(enabled = goal < 8, onClick = { Haptics.tap(); Repo.editPoint(p.copy(max = (goal + 1).toFloat())) }) { Text("+", fontSize = 22.sp, color = Ink) }
    }
}
