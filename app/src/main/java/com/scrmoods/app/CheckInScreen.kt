package com.scrmoods.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val weekdayFmt = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
private val longDateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())

/** The day as a journal page heading: a large weekday, the date in italic, and arrows to turn the page. */
@Composable
fun JournalDateHeader(date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                date.format(weekdayFmt).replaceFirstChar { it.uppercase() },
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.3).sp,
                color = Ink,
                lineHeight = 38.sp,
            )
            Text(date.format(longDateFmt), fontSize = 15.5.sp, fontStyle = FontStyle.Italic, color = InkSoft)
            if (date != today) {
                Text(
                    "Back to today",
                    fontSize = 15.sp,
                    fontStyle = FontStyle.Italic,
                    color = Accent,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { onDateChange(today) }
                        .padding(vertical = 4.dp),
                )
            }
        }
        Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundButton(onClick = { onDateChange(date.minusDays(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day", tint = Ink)
            }
            RoundButton(enabled = date < today, onClick = { onDateChange(date.plusDays(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day", tint = if (date < today) Ink else Rule)
            }
        }
    }
}

@Composable
fun CheckInScreen(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onAddPoint: (sectionId: String) -> Unit,
    onManage: () -> Unit,
) {
    val data = Repo.data
    val key = date.toString()
    val entry = data.entries[key] ?: DayEntry(key)

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        JournalDateHeader(date, onDateChange)

        for (section in data.sections) {
            val points = data.points.filter { it.sectionId == section.id && !it.archived }
            val alwaysShown = section.id == Sections.CUSTOM || section.id == Sections.MEDS
            if (points.isEmpty() && !alwaysShown) continue
            SectionHeader(section.title)
            for (p in points) {
                PointInput(p, entry.values[p.id], key) { v -> Repo.setValue(key, p.id, v) }
            }
            when (section.id) {
                Sections.CUSTOM -> LinkButton("+ Add custom tracking point", Modifier.padding(start = 8.dp, top = 6.dp)) {
                    onAddPoint(Sections.CUSTOM)
                }
                Sections.MEDS -> Row(Modifier.padding(start = 8.dp, top = 6.dp)) {
                    LinkButton("+ Add medication") { onAddPoint(Sections.MEDS) }
                    LinkButton("Edit medications") { onManage() }
                }
            }
        }

        SectionHeader("Today's note")
        RuledNote(key, entry.note)

        TimedNotes(key, entry)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun PointInput(p: TrackingPoint, value: String?, dateKey: String, onChange: (String?) -> Unit) {
    when (p.type) {
        PointType.SCALE -> Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Question(p.label, Modifier.padding(top = 20.dp, bottom = 10.dp))
            Segmented(p.options, value?.toIntOrNull(), { onChange(it?.toString()) })
        }

        PointType.YESNO -> YesNoRow(p.label, value, onChange)

        PointType.NUMBER -> NumberInput(p, value, dateKey, onChange)

        PointType.TEXT -> Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Question(p.label, Modifier.padding(top = 20.dp, bottom = 10.dp))
            // Typing is saved shortly after you stop, not on every key press.
            var text by remember(dateKey, p.id) { mutableStateOf(value ?: "") }
            LaunchedEffect(text) {
                if (text != (value ?: "")) {
                    delay(700)
                    onChange(text.ifBlank { null })
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                shape = RoundedCornerShape(14.dp),
                colors = journalFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val QuestionSize = 15.5.sp
private val QuestionLine = 21.sp

@Composable
private fun Question(label: String, modifier: Modifier) {
    Text(label, fontSize = QuestionSize, lineHeight = QuestionLine, color = Ink, modifier = modifier)
}

/**
 * A yes/no question. Short questions sit beside the No/Yes buttons; if a question would need
 * more than two lines there, it takes the full width instead, with the buttons underneath.
 */
@Composable
private fun YesNoRow(label: String, value: String?, onChange: (String?) -> Unit) {
    var stacked by remember(label) { mutableStateOf(false) }
    var decided by remember(label) { mutableStateOf(false) }
    val selected = value?.toIntOrNull()
    if (!stacked) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label, fontSize = QuestionSize, lineHeight = QuestionLine, color = Ink,
                onTextLayout = { if (it.lineCount > 2) stacked = true else decided = true },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
                    .drawWithContent { if (decided) drawContent() },
            )
            Segmented(listOf("No", "Yes"), selected, { onChange(it?.toString()) }, Modifier.width(116.dp), minHeight = 40)
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
            Text(label, fontSize = QuestionSize, lineHeight = QuestionLine, color = Ink)
            Segmented(
                listOf("No", "Yes"), selected, { onChange(it?.toString()) },
                Modifier.align(Alignment.End).padding(top = 8.dp).width(116.dp), minHeight = 40,
            )
        }
    }
}

@Composable
private fun NumberInput(p: TrackingPoint, value: String?, dateKey: String, onChange: (String?) -> Unit) {
    val stored = value?.toFloatOrNull()
    val step = if (p.step > 0f) p.step else 1f
    fun snap(f: Float): Float {
        val n = ((f - p.min) / step).roundToInt()
        return (p.min + n * step).coerceIn(p.min, p.max)
    }
    // Slider position while dragging; saved when the finger lifts.
    var dragging by remember(dateKey, p.id) { mutableStateOf(false) }
    var pos by remember(dateKey, p.id) { mutableFloatStateOf(stored ?: p.min) }
    val sliderValue = if (dragging) pos else (stored ?: p.min)
    val hasValue = dragging || stored != null

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(p.label, fontSize = QuestionSize, lineHeight = QuestionLine, color = Ink, modifier = Modifier.weight(1f).padding(bottom = 3.dp))
            if (hasValue) {
                Text(formatNumber(sliderValue), fontSize = 21.sp, fontWeight = FontWeight.Medium, color = Accent)
                if (p.unit.isNotBlank()) Text(
                    " " + p.unit, fontSize = 14.5.sp, fontStyle = FontStyle.Italic, color = InkSoft,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            } else {
                Text("not set", fontSize = 14.5.sp, fontStyle = FontStyle.Italic, color = InkFaint, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(onClick = { onChange(formatNumber(snap((stored ?: p.min) - step))) }) {
                Text("−", fontSize = 22.sp, color = Ink)
            }
            val steps = (((p.max - p.min) / step).roundToInt() - 1).coerceAtLeast(0)
            Slider(
                value = sliderValue,
                onValueChange = { dragging = true; pos = snap(it) },
                onValueChangeFinished = { dragging = false; onChange(formatNumber(pos)) },
                valueRange = p.min..p.max,
                steps = if (steps <= 200) steps else 0,
                colors = SliderDefaults.colors(
                    thumbColor = if (stored != null || dragging) Accent else InkFaint,
                    activeTrackColor = if (stored != null || dragging) Accent else Rule,
                    inactiveTrackColor = Rule,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            RoundButton(onClick = { onChange(formatNumber(snap(if (stored == null) p.min + step else stored + step))) }) {
                Text("+", fontSize = 22.sp, color = Ink)
            }
        }
        if (stored != null) {
            Text(
                "Clear",
                fontSize = 14.sp, fontStyle = FontStyle.Italic, color = InkSoft,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(50))
                    .clickable { onChange(null) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** The day's note, written straight onto ruled notebook lines. Saved shortly after you stop typing. */
@Composable
private fun RuledNote(dateKey: String, saved: String) {
    var text by remember(dateKey) { mutableStateOf(saved) }
    // Save anything still pending when you turn to another day.
    val latest by rememberUpdatedState(text)
    DisposableEffect(dateKey) {
        onDispose { if (latest.trim() != (Repo.data.entries[dateKey]?.note ?: "")) Repo.setNote(dateKey, latest) }
    }
    LaunchedEffect(dateKey, text) {
        if (text.trim() != saved) {
            delay(600)
            Repo.setNote(dateKey, text)
        }
    }
    val lineHeight = 28.sp
    val padTop = 10.dp
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        textStyle = TextStyle(fontFamily = Lora, fontSize = 15.5.sp, lineHeight = lineHeight, color = Ink),
        cursorBrush = SolidColor(Accent),
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Page)
            .border(1.dp, Rule, RoundedCornerShape(16.dp))
            .drawBehind {
                val lh = lineHeight.toPx()
                var y = padTop.toPx() + lh - lh * 0.16f
                while (y < size.height - 6.dp.toPx()) {
                    drawLine(Rule, Offset(16.dp.toPx(), y), Offset(size.width - 16.dp.toPx(), y), strokeWidth = 1.dp.toPx())
                    y += lh
                }
            }
            .heightIn(min = 160.dp)
            .padding(start = 18.dp, end = 18.dp, top = padTop, bottom = 12.dp),
        decorationBox = { inner ->
            Box {
                if (text.isEmpty()) Text(
                    "Write about your day…",
                    style = TextStyle(fontFamily = Lora, fontSize = 15.5.sp, lineHeight = lineHeight, fontStyle = FontStyle.Italic, color = InkFaint),
                )
                inner()
            }
        },
    )
}

@Composable
private fun TimedNotes(dateKey: String, entry: DayEntry) {
    var addTimed by remember { mutableStateOf(false) }
    var deleteIndex by remember { mutableStateOf<Int?>(null) }

    SectionHeader("Through the day")
    Column(Modifier.padding(top = 8.dp)) {
        entry.timedNotes.forEachIndexed { i, n ->
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.Top) {
                Text(
                    n.time.substringAfter(' '),
                    color = Accent, fontSize = 14.sp, fontStyle = FontStyle.Italic,
                    modifier = Modifier.width(52.dp).padding(top = 2.dp),
                )
                Text(n.text, color = Ink, fontSize = 15.5.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { deleteIndex = i }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, "Delete note", tint = InkFaint, modifier = Modifier.size(16.dp))
                }
            }
        }
        LinkButton("+ Add a timestamped note", Modifier.padding(start = 8.dp, top = 4.dp)) { addTimed = true }
    }

    if (addTimed) TextInputDialog(
        title = "Note for right now",
        initial = "",
        confirmText = "Add",
        onDismiss = { addTimed = false },
        onSave = {
            if (it.isNotBlank()) {
                val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                Repo.addTimedNote(dateKey, "$dateKey $time", it)
            }
            addTimed = false
        },
    )
    deleteIndex?.let { idx ->
        ConfirmDialog(
            title = "Delete this note?",
            message = entry.timedNotes.getOrNull(idx)?.text ?: "",
            confirmText = "Delete",
            onDismiss = { deleteIndex = null },
            onConfirm = { Repo.removeTimedNote(dateKey, idx); deleteIndex = null },
        )
    }
}
