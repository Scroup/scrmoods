package com.scrmoods.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Muted mood inks: dusty rose → honey → olive sage.
val BadColor = Color(0xFFC98378)
val MidColor = Color(0xFFD9B26A)
val GoodColor = Color(0xFF93A77F)

/** Red → orange → green for a 0..1 fraction. */
fun moodColor(f: Float): Color =
    if (f < 0.5f) lerp(BadColor, MidColor, f * 2) else lerp(MidColor, GoodColor, (f - 0.5f) * 2)

@Composable
fun CalendarScreen(selected: LocalDate, onPick: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val data = Repo.data
    val outlook = data.points.firstOrNull { it.id == "outlook" }
    val today = LocalDate.now()
    val monthFmt = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        // Month as a page heading: large month name, year in italic, arrows to turn the page.
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f).clickable { month = YearMonth.from(today) }) {
                Text(
                    month.format(DateTimeFormatter.ofPattern("LLLL", Locale.getDefault())).replaceFirstChar { it.uppercase() },
                    fontSize = 36.sp, fontWeight = FontWeight.Medium, color = Ink, lineHeight = 42.sp,
                )
                Text(month.year.toString(), fontSize = 17.sp, fontStyle = FontStyle.Italic, color = InkSoft)
            }
            Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", tint = Ink)
                }
                RoundButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", tint = Ink)
                }
            }
        }

        // Weekday names, Monday first.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
            for (i in 0 until 7) {
                val dow = DayOfWeek.MONDAY.plus(i.toLong())
                Text(
                    dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    color = InkSoft, fontSize = 13.sp, fontStyle = FontStyle.Italic,
                )
            }
        }

        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - 1 // Monday = 0
        val days = month.lengthOfMonth()
        val cells = ((offset + days + 6) / 7) * 7
        Column(Modifier.padding(horizontal = 8.dp)) {
            for (week in 0 until cells / 7) {
                Row(Modifier.fillMaxWidth()) {
                    for (d in 0 until 7) {
                        val dayNum = week * 7 + d - offset + 1
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                            if (dayNum in 1..days) {
                                val date = month.atDay(dayNum)
                                DayCell(date, date == selected, date == today, date > today, data.entries[date.toString()], outlook) {
                                    onPick(date)
                                }
                            }
                        }
                    }
                }
            }
        }

        val logged = (1..days).count { data.entries.containsKey(month.atDay(it).toString()) }
        Text(
            "Days logged this month: $logged",
            color = TextGray, fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (outlook != null && outlook.options.isNotEmpty()) {
            Text("Dot colour = overall outlook", color = TextGray, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                outlook.options.forEachIndexed { i, o ->
                    val f = if (outlook.options.size > 1) i.toFloat() / (outlook.options.size - 1) else 1f
                    Box(Modifier.size(12.dp).clip(CircleShape).background(moodColor(f)))
                    Text(o, fontSize = 14.sp, color = TextDark)
                }
                Box(Modifier.size(12.dp).clip(CircleShape).background(InkSoft))
                Text("Other entries", fontSize = 14.sp, color = TextDark)
            }
        }
        Text(
            "Tap a day to open its check-in.",
            color = InkFaint, fontSize = 14.sp, fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    entry: DayEntry?,
    outlook: TrackingPoint?,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    var m = Modifier.fillMaxSize().clip(shape)
    if (isSelected) m = m.background(Purple)
    if (isToday && !isSelected) m = m.border(1.5.dp, Teal, shape)
    if (!isFuture) m = m.clickable(onClick = onClick)

    Box(m, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                fontSize = 16.sp,
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    isSelected -> Color.White
                    isFuture -> InkFaint
                    else -> TextDark
                },
            )
            val dotColor: Color? = entry?.let { e ->
                val v = outlook?.let { e.values[it.id]?.toIntOrNull() }
                if (v != null && outlook != null && outlook.options.size > 1) moodColor(v.toFloat() / (outlook.options.size - 1))
                else if (isSelected) Color.White else InkSoft
            }
            Spacer(Modifier.height(3.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor ?: Color.Transparent))
        }
    }
}
