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
                        fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, color = Ink,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Filled.ArrowDropDown, null, tint = Accent)
                }
                Text("Tap to look at another tracking point", fontSize = 14.sp, fontStyle = FontStyle.Italic, color = InkSoft)
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
            Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp), minHeight = 38, idleColor = Page,
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

        // Overview of everything in the same period, laid out like the journal (removed points left out).
        SectionHeader("Everything, last ${ranges[rangeIdx].second}")
        Text(
            "Green is going well, red needs attention. Each square is a day" +
                (if (nDays > 31) " (or a few days averaged)" else "") + ".",
            fontSize = 13.5.sp, fontStyle = FontStyle.Italic, color = InkSoft, lineHeight = 19.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
        )
        for (section in data.sections) {
            val pts = chartable.filter { it.sectionId == section.id && !it.archived }
            if (pts.isEmpty()) continue
            Text(
                section.title, fontSize = 14.sp, fontStyle = FontStyle.Italic, color = Accent,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 2.dp),
            )
            for (p in pts) OverviewRow(p, days, stats(p, days)) { pointId = p.id }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Colours a value by how good it is (red → honey → green), or violet ink when the point has no direction. */
private fun moodColorFor(p: TrackingPoint, v: Float): Color =
    p.goodness(v)?.let { moodColor(it) } ?: Accent

private enum class Verdict { GOOD, MIXED, BAD, NONE }

private fun verdict(p: TrackingPoint, s: Stats?): Verdict {
    if (s == null) return Verdict.NONE
    val g = p.goodness(s.avg) ?: return Verdict.NONE
    return when {
        g >= 0.6f -> Verdict.GOOD
        g <= 0.4f -> Verdict.BAD
        else -> Verdict.MIXED
    }
}

/** Short tag text: the usual answer ("Positive"), the yes-rate ("Yes 80%") or the average ("7.5 hrs"). */
private fun tagText(p: TrackingPoint, s: Stats): String = when (p.type) {
    PointType.SCALE -> p.options.getOrNull(s.avg.roundToInt()) ?: formatNumber(s.avg)
    PointType.YESNO -> if (s.avg >= 0.5f) "Yes ${(s.avg * 100).roundToInt()}%" else "No ${((1 - s.avg) * 100).roundToInt()}%"
    PointType.NUMBER -> "%.1f".format(Locale.US, s.avg) + if (p.unit.isNotBlank()) " ${p.unit}" else ""
    PointType.TEXT -> ""
}

@Composable
private fun OverviewRow(p: TrackingPoint, days: List<LocalDate>, s: Stats?, onClick: () -> Unit) {
    val v = verdict(p, s)
    val tone = when (v) {
        Verdict.GOOD -> GoodColor
        Verdict.MIXED -> MidColor
        Verdict.BAD -> BadColor
        Verdict.NONE -> Accent
    }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(p.label, fontSize = 15.sp, lineHeight = 20.sp, color = Ink, modifier = Modifier.weight(1f).padding(end = 10.dp))
            if (s == null) {
                Text("no answers yet", fontSize = 13.5.sp, fontStyle = FontStyle.Italic, color = InkFaint)
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        tagText(p, s),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ink,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(tone.copy(alpha = if (v == Verdict.NONE) 0.14f else 0.32f))
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                    Text(
                        "${s.count} ${if (s.count == 1) "day" else "days"}",
                        fontSize = 12.5.sp, fontStyle = FontStyle.Italic, color = InkSoft,
                        modifier = Modifier.padding(top = 2.dp, end = 4.dp),
                    )
                }
            }
        }
        DayStrip(p, days, Modifier.padding(top = 8.dp))
        if (v == Verdict.BAD) {
            Text(
                "Needs attention",
                fontSize = 13.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium, color = Danger,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Rule)
}

/** One small square per day (or per few days for long periods), coloured by how good the answer was. */
@Composable
private fun DayStrip(p: TrackingPoint, days: List<LocalDate>, modifier: Modifier = Modifier) {
    val perCell = (days.size + 30) / 31
    val cells = days.chunked(perCell.coerceAtLeast(1)).map { chunk ->
        val vals = chunk.mapNotNull { p.numeric(Repo.data.entries[it.toString()]?.values?.get(p.id)) }
        if (vals.isEmpty()) null else vals.average().toFloat()
    }
    Row(modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (c in cells) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (c == null) Rule.copy(alpha = 0.6f)
                        else p.goodness(c)?.let { moodColor(it) } ?: Accent.copy(alpha = 0.25f + 0.6f * p.fraction(c)),
                    ),
            )
        }
    }
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
