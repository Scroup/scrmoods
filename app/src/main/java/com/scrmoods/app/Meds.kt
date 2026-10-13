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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One little capsule, two-tone when taken, outlined when not. */
@Composable
fun Pill(taken: Boolean, modifier: Modifier = Modifier) {
    val accent = Accent
    val light = Accent.copy(alpha = 0.38f).compositeOver(Page)
    val line = if (taken) Accent else InkFaint
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cw = w * 0.86f
        val ch = cw * 0.44f
        val left = (w - cw) / 2f
        val top = (h - ch) / 2f
        val r = CornerRadius(ch / 2f)
        val stroke = 1.6.dp.toPx()
        rotate(-35f) {
            if (taken) {
                clipRect(left, top - 1f, w / 2f, top + ch + 1f) { drawRoundRect(accent, Offset(left, top), Size(cw, ch), r) }
                clipRect(w / 2f, top - 1f, left + cw, top + ch + 1f) { drawRoundRect(light, Offset(left, top), Size(cw, ch), r) }
                drawLine(
                    Color.White.copy(alpha = 0.7f), Offset(left + ch * 0.5f, top + ch * 0.3f), Offset(w / 2f - ch * 0.25f, top + ch * 0.3f),
                    strokeWidth = ch * 0.16f, cap = StrokeCap.Round,
                )
            }
            drawRoundRect(line, Offset(left, top), Size(cw, ch), r, style = Stroke(stroke))
            drawLine(line, Offset(w / 2f, top), Offset(w / 2f, top + ch), strokeWidth = stroke)
        }
    }
}

/** A row of pills: tap an empty one to take up to it, tap the last taken one to take it back. */
@Composable
fun PillRow(p: TrackingPoint, value: String?, size: Dp, spacing: Dp = 6.dp, onChange: (String) -> Unit) {
    val n = p.doses
    val taken = dosesTaken(value).coerceAtMost(n)
    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
        for (i in 0 until n) {
            val pop = rememberPop()
            Box(
                Modifier
                    .then(pop.modifier)
                    .size(size)
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        Haptics.tap()
                        val next = if (i + 1 == taken) i else i + 1
                        pop.fire(stars = next > taken, color = Accent)
                        onChange(doseValue(next, n))
                    },
            ) { Pill(i < taken, Modifier.fillMaxSize()) }
        }
    }
}

/** A medication in the journal: its name, reminder times, and a pill for each dose. */
@Composable
fun MedRow(p: TrackingPoint, value: String?, gutter: Dp, onChange: (String?) -> Unit) {
    val n = p.doses
    val taken = dosesTaken(value).coerceAtMost(n)
    Row(
        Modifier.fillMaxWidth().padding(start = gutter, end = gutter - 6.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Question(p.title)
            val sub = listOfNotNull(
                if (n > 1) tr("{0} of {1} taken", taken, n) else if (value == "1") tr("Taken") else null,
                Reminders.describe(p.reminders).takeIf { it.isNotEmpty() },
            ).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = Accent, modifier = Modifier.padding(top = 2.dp))
        }
        PillRow(p, value, if (n > 4) 30.dp else 38.dp, spacing = 2.dp) { onChange(it) }
    }
}
