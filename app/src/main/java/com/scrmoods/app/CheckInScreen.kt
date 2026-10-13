package com.scrmoods.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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


/** The day as a journal page heading: a large weekday, the date in italic, and arrows to turn the page. */
@Composable
fun JournalDateHeader(date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                date.format(DateTimeFormatter.ofPattern("EEEE", Lang.locale)).replaceFirstChar { it.uppercase() },
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.3).sp,
                color = Ink,
                lineHeight = 38.sp,
            )
            Text(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Lang.locale)), fontSize = 15.5.sp, fontStyle = FontStyle.Italic, color = InkSoft)
            if (date != today) {
                Text(
                    tr("Back to today"),
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
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, tr("Previous day"), tint = Ink)
            }
            RoundButton(enabled = date < today, onClick = { onDateChange(date.plusDays(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, tr("Next day"), tint = if (date < today) Ink else Rule)
            }
        }
    }
}

/** The day's quote, set like an epigraph: a large opening mark, the words in italic, the author below. */
@Composable
private fun QuoteOfTheDay(q: Quote) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 24.dp, top = 10.dp, bottom = 4.dp)) {
        Text("“", fontSize = 46.sp, lineHeight = 46.sp, color = Accent.copy(alpha = 0.55f), modifier = Modifier.width(26.dp))
        Column(Modifier.padding(top = 6.dp)) {
            Text(tr(q.text), fontSize = 16.sp, lineHeight = 23.sp, fontStyle = FontStyle.Italic, color = Ink)
            Text(
                q.author?.let { tr(it) } ?: tr("A reminder for today"),
                fontSize = 13.sp, fontStyle = FontStyle.Italic, color = Accent,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Space between a journal page's edge and its content. */
private val Gutter = 14.dp

/** One rounded "page" holding a section of the journal. */
@Composable
private fun JournalCard(
    bottomPadding: androidx.compose.ui.unit.Dp = 16.dp,
    order: Int = 0, // when it rises in during the opening animation
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .reveal(order)
            .padding(start = 10.dp, end = 10.dp, top = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Page)
            .border(1.dp, Rule, shape)
            .padding(bottom = bottomPadding),
        content = content,
    )
}

@Composable
fun CheckInScreen(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onAddPoint: (sectionId: String) -> Unit,
    onManage: () -> Unit,
    onQuick: () -> Unit = {},
) {
    val data = Repo.data
    val key = date.toString()
    val entry = data.entries[key] ?: DayEntry(key)
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        Box(Modifier.reveal(0)) { JournalDateHeader(date, onDateChange) }
        if (Themes.showQuote) Box(Modifier.reveal(1)) { QuoteOfTheDay(Quotes.forDate(date)) }
        QuickCheckInButton(Modifier.reveal(2), onClick = onQuick)
        var order = 3 // cards rise in one after another when the app opens

        for (section in data.sections) {
            if (section.hidden) continue
            if (section.id == Sections.BIPOLAR) {
                JournalCard(order = order++) {
                    SectionHeader(section.name, horizontal = Gutter, top = 16.dp)
                    MoodScaleCard(
                        value = entry.values[MoodPoints.SCALE],
                        value2 = entry.values[MoodPoints.SCALE2],
                        mixed = entry.values[MoodPoints.MIXED],
                        gutter = Gutter,
                        onChange = { a, b ->
                            Repo.setValue(key, MoodPoints.SCALE, a?.toString())
                            Repo.setValue(key, MoodPoints.SCALE2, b?.toString())
                        },
                        onMixedChange = { Repo.setValue(key, MoodPoints.MIXED, it) },
                    )
                }
                continue
            }
            val points = data.points.filter { it.sectionId == section.id && !it.archived }
            val alwaysShown = section.id == Sections.CUSTOM || section.id == Sections.MEDS
            if (points.isEmpty() && !alwaysShown) continue
            JournalCard(order = order++) {
                SectionHeader(section.name, horizontal = Gutter, top = 16.dp)
                points.forEachIndexed { i, p ->
                    if (i > 0) QuestionDivider()
                    PointInput(p, entry.values[p.id], key) { v -> Repo.setValue(key, p.id, v) }
                }
                when (section.id) {
                    Sections.CUSTOM -> LinkButton(tr("+ Add custom tracking point"), Modifier.padding(start = Gutter - 12.dp, top = 6.dp)) {
                        onAddPoint(Sections.CUSTOM)
                    }
                    Sections.MEDS -> Row(Modifier.padding(start = Gutter - 12.dp, top = 6.dp)) {
                        LinkButton(tr("+ Add medication")) { onAddPoint(Sections.MEDS) }
                        LinkButton(tr("Edit medications")) { onManage() }
                    }
                }
            }
        }

        JournalCard(bottomPadding = 4.dp, order = order) {
            SectionHeader(tr("Today's note"), horizontal = Gutter, top = 16.dp)
            RuledNote(key, entry.note, onFinish = if (GoodNight.enabled) ({ GoodNight.celebrate(context, key) }) else null)
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun PointInput(p: TrackingPoint, value: String?, dateKey: String, onChange: (String?) -> Unit) {
    when (p.type) {
        // Answer rows reach 4dp past the question text on each side, for more room in the buttons.
        PointType.SCALE -> Column(Modifier.fillMaxWidth().padding(horizontal = Gutter - 4.dp)) {
            Question(p.title, Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 10.dp))
            Segmented(p.options, value?.toIntOrNull(), { onChange(it?.toString()) }, playful = true)
        }

        PointType.YESNO -> YesNoRow(p.title, value, onChange, Reminders.describe(p.reminders))

        PointType.NUMBER -> NumberInput(p, value, dateKey, onChange)

        PointType.TEXT -> Column(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
            Question(p.title, Modifier.padding(top = 16.dp, bottom = 10.dp))
            // Typing is saved shortly after you stop, not on every key press.
            var text by remember(dateKey, p.id) { mutableStateOf(value ?: "") }
            LaunchedEffect(dateKey, text) {
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

/** A yes/no question: the question on top, the No / Yes buttons right underneath it, like every other question. */
@Composable
private fun YesNoRow(label: String, value: String?, onChange: (String?) -> Unit, note: String = "") {
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter - 4.dp)) {
        Question(label, Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = if (note.isEmpty()) 10.dp else 0.dp))
        if (note.isNotEmpty()) Box(Modifier.padding(start = 4.dp, bottom = 10.dp)) { ReminderNote(note) }
        Segmented(listOf("No", "Yes"), value?.toIntOrNull(), { onChange(it?.toString()) }, playful = true)
    }
}

/** A faint line between questions, so it's clear which answers belong to which question. */
@Composable
private fun QuestionDivider() {
    Box(
        Modifier
            .padding(start = Gutter, end = Gutter, top = 18.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Rule.copy(alpha = 0.7f)),
    )
}

@Composable
private fun ReminderNote(text: String) {
    Text(text, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = Accent, modifier = Modifier.padding(top = 2.dp))
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

    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(p.title, fontSize = QuestionSize, lineHeight = QuestionLine, color = Ink, modifier = Modifier.weight(1f).padding(bottom = 3.dp))
            if (hasValue) {
                Text(formatNumber(sliderValue), fontSize = 21.sp, fontWeight = FontWeight.Medium, color = Accent)
                if (p.unit.isNotBlank()) Text(
                    " " + tr(p.unit), fontSize = 14.5.sp, fontStyle = FontStyle.Italic, color = InkSoft,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            } else {
                Text(tr("not set"), fontSize = 14.5.sp, fontStyle = FontStyle.Italic, color = InkFaint, modifier = Modifier.padding(bottom = 3.dp))
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
                tr("Clear"),
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

/** What's typed in one day's note, kept per day so one day's text can never be saved into another day. */
private class NoteDraft(initial: String) {
    var text by mutableStateOf(initial)
    var known by mutableStateOf(initial) // the note as this field last saved or saw it
    var dirty = false                    // typed since the last save
}

/** The day's note, written straight onto ruled notebook lines. Saved shortly after you stop typing. */
@Composable
private fun RuledNote(dateKey: String, saved: String, onFinish: (() -> Unit)? = null) {
    val focus = LocalFocusManager.current
    val draft = remember(dateKey) { NoteDraft(saved) }
    // If the stored note changed from outside (a quick note from the widget), show the new version.
    if (saved != draft.known) {
        draft.text = saved
        draft.known = saved
        draft.dirty = false
    }
    fun save() {
        draft.known = draft.text.trim()
        draft.dirty = false
        Repo.setNote(dateKey, draft.text)
    }
    // Leaving the day (another date, another tab): save it if something was typed and not saved yet.
    // Uses this day's own draft and date, captured when the day was opened.
    DisposableEffect(dateKey) {
        val day = dateKey
        val d = draft
        onDispose {
            if (d.dirty) {
                d.dirty = false
                Repo.setNote(day, d.text)
            }
        }
    }
    LaunchedEffect(dateKey, draft.text) {
        if (draft.dirty) {
            delay(600)
            save()
        }
    }
    val lineHeight = 28.sp
    val padTop = 10.dp
    BasicTextField(
        value = draft.text,
        onValueChange = { draft.text = it; draft.dirty = true },
        textStyle = TextStyle(fontFamily = Lora, fontSize = 15.5.sp, lineHeight = lineHeight, color = Ink),
        cursorBrush = SolidColor(Accent),
        modifier = Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .drawBehind {
                val lh = lineHeight.toPx()
                var y = padTop.toPx() + lh - lh * 0.16f
                while (y < size.height - 6.dp.toPx()) {
                    drawLine(Rule, Offset(Gutter.toPx(), y), Offset(size.width - Gutter.toPx(), y), strokeWidth = 1.dp.toPx())
                    y += lh
                }
            }
            .heightIn(min = 160.dp)
            .padding(start = Gutter + 2.dp, end = Gutter + 2.dp, top = padTop, bottom = 12.dp),
        decorationBox = { inner ->
            Box {
                if (draft.text.isEmpty()) Text(
                    tr("Write about your day…"),
                    style = TextStyle(fontFamily = Lora, fontSize = 15.5.sp, lineHeight = lineHeight, fontStyle = FontStyle.Italic, color = InkFaint),
                )
                inner()
            }
        },
    )
    if (onFinish != null) {
        val done = GoodNight.finished == dateKey
        FinishDayButton(done = done) {
            save()                        // save right away, don't wait for the pause in typing
            focus.clearFocus()            // closes the keyboard
            if (!done) onFinish()         // the good night only comes once a day
        }
    }
}

/** Under the note: ends the day with the good-night celebration. After that it shows the day is done (tapping it then just saves). */
@Composable
private fun FinishDayButton(done: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .padding(start = Gutter, end = Gutter, top = 4.dp, bottom = 14.dp)
            .fillMaxWidth()
            .clip(shape)
            .then(if (done) Modifier.border(1.dp, Rule, shape) else Modifier.background(Accent))
            .clickable { Haptics.tap(); onClick() }
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (done) tr("Day finished") + "  ✓" else tr("Finish the day") + "  ☾",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontStyle = if (done) FontStyle.Italic else FontStyle.Normal,
            color = if (done) Accent else OnAccent,
        )
    }
}
