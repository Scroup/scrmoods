package com.scrmoods.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val ranges = listOf(7 to "7 days", 30 to "30 days", 90 to "90 days", 365 to "1 year")

/** Numeric value used for charts and averages; Yes = 1, No = 0, scale = option index. */
private fun TrackingPoint.numeric(v: String?): Float? = when (type) {
    PointType.TEXT -> null
    else -> v?.replace(',', '.')?.toFloatOrNull()
}

private fun TrackingPoint.yRange(): Pair<Float, Float> = when (type) {
    PointType.SCALE -> 0f to (options.size - 1).coerceAtLeast(1).toFloat()
    PointType.YESNO -> 0f to 1f
    PointType.NUMBER -> min to max
    PointType.TEXT -> 0f to 1f
}

private data class Stats(val count: Int, val avg: Float, val min: Float, val max: Float, val values: List<Float>)

private fun stats(p: TrackingPoint, days: List<LocalDate>): Stats? {
    val vals = days.mapNotNull { p.numeric(Repo.data.entries[it.toString()]?.values?.get(p.id)) }
    if (vals.isEmpty()) return null
    return Stats(vals.size, vals.average().toFloat(), vals.min(), vals.max(), vals)
}

private fun summary(p: TrackingPoint, s: Stats?): String {
    if (s == null) return "no answers"
    return when (p.type) {
        PointType.YESNO -> "Yes on ${(s.avg * 100).roundToInt()}% of ${s.count} days"
        PointType.SCALE -> {
            val near = p.options.getOrNull(s.avg.roundToInt()) ?: ""
            "avg ${"%.1f".format(Locale.US, s.avg)} ($near), ${s.count} days"
        }
        PointType.NUMBER -> "avg ${"%.1f".format(Locale.US, s.avg)}${if (p.unit.isNotBlank()) " " + p.unit else ""}, ${s.count} days"
        PointType.TEXT -> ""
    }
}

@Composable
fun GraphScreen() {
    val data = Repo.data
    val chartable = data.points.filter { it.type != PointType.TEXT }
    var pointId by remember { mutableStateOf(chartable.firstOrNull { it.id == "outlook" }?.id ?: chartable.firstOrNull()?.id) }
    var rangeIdx by remember { mutableIntStateOf(1) }
    var menuOpen by remember { mutableStateOf(false) }

    val point = chartable.firstOrNull { it.id == pointId } ?: chartable.firstOrNull()
    val today = LocalDate.now()
    val nDays = ranges[rangeIdx].first
    val days = (nDays - 1 downTo 0).map { today.minusDays(it.toLong()) }

    if (point == null) {
        Text("No tracking points to chart yet.", modifier = Modifier.padding(16.dp), color = TextGray)
        return
    }
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {

        // The chosen tracking point is the page heading; tap it to pick another.
        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { menuOpen = true }
                    .padding(vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        point.label + if (point.archived) " (removed)" else "",
                        fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Medium, color = Ink,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Filled.ArrowDropDown, null, tint = Accent)
                }
                Text("Tap to look at another tracking point", fontSize = 15.sp, fontStyle = FontStyle.Italic, color = InkSoft)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                for (p in chartable) {
                    DropdownMenuItem(
                        text = { Text(p.label + if (p.archived) " (removed)" else "", maxLines = 2, color = Ink) },
                        onClick = { pointId = p.id; menuOpen = false },
                    )
                }
            }
        }

        Segmented(
            ranges.map { it.second }, rangeIdx, { if (it != null) rangeIdx = it },
            Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp), minHeight = 38,
        )

        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Page)
                .border(1.dp, Rule, RoundedCornerShape(16.dp))
                .padding(top = 10.dp, bottom = 8.dp),
        ) {
            Chart(point, days)

        val dfmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        Row(Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp)) {
            Text(days.first().format(dfmt), fontSize = 12.sp, fontStyle = FontStyle.Italic, color = InkSoft, modifier = Modifier.weight(1f))
            Text(days.last().format(dfmt), fontSize = 12.sp, fontStyle = FontStyle.Italic, color = InkSoft)
        }
        }

        // Stats for the chosen point
        val s = stats(point, days)
        SectionHeader("In numbers")
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 10.dp)) {
            StatLine("Days answered", "${s?.count ?: 0} of $nDays")
            if (s != null) when (point.type) {
                PointType.YESNO -> {
                    val yes = s.values.count { it >= 0.5f }
                    StatLine("Yes", "$yes days (${(yes * 100f / s.count).roundToInt()}%)")
                    StatLine("No", "${s.count - yes} days")
                }
                PointType.SCALE -> {
                    StatLine("Average", "%.2f".format(Locale.US, s.avg) + "  → " + (point.options.getOrNull(s.avg.roundToInt()) ?: ""))
                    Spacer(Modifier.height(8.dp))
                    point.options.forEachIndexed { i, o ->
                        val c = s.values.count { it.roundToInt() == i }
                        DistBar(o, c, s.count, moodColorFor(point, i.toFloat()))
                    }
                }
                PointType.NUMBER -> {
                    val u = if (point.unit.isNotBlank()) " ${point.unit}" else ""
                    StatLine("Average", "%.1f".format(Locale.US, s.avg) + u)
                    StatLine("Lowest", formatNumber(s.min) + u)
                    StatLine("Highest", formatNumber(s.max) + u)
                }
                PointType.TEXT -> {}
            }
        }

        // Overview of everything in the same period
        SectionHeader("Everything, last ${ranges[rangeIdx].second}")
        for (p in chartable.filter { !it.archived }) {
            Row(
                Modifier.fillMaxWidth().clickable { pointId = p.id }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(p.label, fontSize = 15.5.sp, lineHeight = 21.sp, color = Ink, modifier = Modifier.weight(1f).padding(end = 8.dp))
                Text(summary(p, stats(p, days)), fontSize = 14.sp, fontStyle = FontStyle.Italic, color = InkSoft, modifier = Modifier.widthIn(max = 170.dp))
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Rule)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Colours a value by its position in the point's range (low = red, high = green). */
private fun moodColorFor(p: TrackingPoint, v: Float): Color {
    val (lo, hi) = p.yRange()
    return if (p.type == PointType.NUMBER) Teal else moodColor(((v - lo) / (hi - lo)).coerceIn(0f, 1f))
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 16.sp, color = TextGray, modifier = Modifier.weight(1f))
        Text(value, fontSize = 16.sp, color = TextDark, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DistBar(label: String, count: Int, total: Int, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = TextDark, modifier = Modifier.width(100.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Paper)) {
            if (count > 0) Box(
                Modifier.fillMaxHeight().fillMaxWidth(count.toFloat() / total).clip(RoundedCornerShape(4.dp)).background(color),
            )
        }
        Text("$count", fontSize = 14.sp, color = TextGray, modifier = Modifier.width(36.dp).padding(start = 8.dp))
    }
}

@Composable
private fun Chart(p: TrackingPoint, days: List<LocalDate>) {
    val (lo, hi) = p.yRange()
    val labels: List<String> = when (p.type) {
        PointType.SCALE -> p.options.reversed()
        PointType.YESNO -> listOf("Yes", "No")
        else -> listOf(formatNumber(hi), formatNumber((lo + hi) / 2), formatNumber(lo))
    }
    val values: List<Float?> = days.map { p.numeric(Repo.data.entries[it.toString()]?.values?.get(p.id)) }
    val chartHeight = 220.dp
    val inset = 10.dp

    Row(Modifier.fillMaxWidth().height(chartHeight).padding(end = 16.dp)) {
        Column(
            Modifier.width(72.dp).fillMaxHeight().padding(start = 8.dp, end = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            for (l in labels) Text(l, fontSize = 11.sp, color = TextGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val top = inset.toPx()
            val bottom = size.height - inset.toPx()
            val h = bottom - top
            fun y(v: Float) = bottom - (v - lo) / (hi - lo) * h
            val n = days.size
            val slot = size.width / n
            fun x(i: Int) = slot * i + slot / 2

            // grid
            val gridCount = labels.size
            for (g in 0 until gridCount) {
                val gy = top + h * g / (gridCount - 1).coerceAtLeast(1)
                drawLine(Rule, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
            }

            if (p.type == PointType.YESNO) {
                val barW = (slot * 0.7f).coerceAtMost(24.dp.toPx())
                values.forEachIndexed { i, v ->
                    if (v == null) return@forEachIndexed
                    if (v >= 0.5f) {
                        drawRect(GoodColor, Offset(x(i) - barW / 2, y(1f)), androidx.compose.ui.geometry.Size(barW, bottom - y(1f)))
                    } else {
                        drawRect(BadColor, Offset(x(i) - barW / 2, bottom - 4.dp.toPx()), androidx.compose.ui.geometry.Size(barW, 4.dp.toPx()))
                    }
                }
            } else {
                val path = Path()
                var started = false
                values.forEachIndexed { i, v ->
                    if (v == null) return@forEachIndexed
                    val px = x(i); val py = y(v.coerceIn(lo, hi))
                    if (!started) { path.moveTo(px, py); started = true } else path.lineTo(px, py)
                }
                drawPath(path, Accent.copy(alpha = 0.55f), style = Stroke(width = 2.dp.toPx()))
                val r = if (n > 120) 2.5.dp.toPx() else 4.dp.toPx()
                values.forEachIndexed { i, v ->
                    if (v == null) return@forEachIndexed
                    drawCircle(moodColorFor(p, v), r, Offset(x(i), y(v.coerceIn(lo, hi))))
                }
            }
        }
    }
}
