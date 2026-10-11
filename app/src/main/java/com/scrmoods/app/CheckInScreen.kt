package com.scrmoods.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val headerFmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
private val headerFmtYear = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())

@Composable
fun DateBar(date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    Column(Modifier.background(Color.White)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onDateChange(date.minusDays(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day", Modifier.size(36.dp), tint = TextDark)
            }
            Text(
                text = date.format(if (date.year == today.year) headerFmt else headerFmtYear),
                color = Teal,
                fontSize = 21.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clickable { onDateChange(today) },
            )
            IconButton(onClick = { onDateChange(date.plusDays(1)) }, enabled = date < today) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day", Modifier.size(36.dp),
                    tint = if (date < today) TextDark else Color(0xFFCCCCCC),
                )
            }
        }
        HorizontalDivider(color = Color(0xFFE0E0E0))
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

    Column(Modifier.fillMaxSize()) {
        DateBar(date, onDateChange)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            var shaded = false
            for (section in data.sections) {
                val points = data.points.filter { it.sectionId == section.id && !it.archived }
                val alwaysShown = section.id == Sections.CUSTOM || section.id == Sections.MEDS
                if (points.isEmpty() && !alwaysShown) continue
                val bg = if (shaded) SectionGray else Color.White
                shaded = !shaded
                Column(Modifier.fillMaxWidth().background(bg).padding(bottom = 12.dp)) {
                    SectionHeader(section.title)
                    for (p in points) {
                        PointInput(p, entry.values[p.id], key) { v -> Repo.setValue(key, p.id, v) }
                    }
                    when (section.id) {
                        Sections.CUSTOM -> LinkButton(
                            "Add custom tracking point",
                            Modifier.align(Alignment.CenterHorizontally),
                        ) { onAddPoint(Sections.CUSTOM) }
                        Sections.MEDS -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            LinkButton("Add medication") { onAddPoint(Sections.MEDS) }
                            LinkButton("Edit medications") { onManage() }
                        }
                    }
                }
            }

            NotesBlock(key, entry, if (shaded) SectionGray else Color.White, if (shaded) Color.White else SectionGray)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun PointInput(p: TrackingPoint, value: String?, dateKey: String, onChange: (String?) -> Unit) {
    val labelMod = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)
    when (p.type) {
        PointType.SCALE -> Column(Modifier.fillMaxWidth()) {
            Text(p.label, fontSize = 18.sp, color = TextGray, modifier = labelMod)
            Segmented(
                p.options,
                value?.toIntOrNull(),
                { onChange(it?.toString()) },
                Modifier.padding(horizontal = 12.dp),
            )
        }

        PointType.YESNO -> Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(p.label, fontSize = 18.sp, color = TextGray, modifier = Modifier.weight(1f).padding(end = 12.dp))
            Segmented(
                listOf("No", "Yes"),
                value?.toIntOrNull(),
                { onChange(it?.toString()) },
                Modifier.width(150.dp),
            )
        }

        PointType.NUMBER -> NumberInput(p, value, dateKey, onChange)

        PointType.TEXT -> Column(Modifier.fillMaxWidth()) {
            Text(p.label, fontSize = 18.sp, color = TextGray, modifier = labelMod)
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
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
    val shown = if (dragging || stored != null) formatNumber(sliderValue) else "–"
    val unit = if (p.unit.isNotBlank()) " (${p.unit})" else ""

    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${p.label}$unit: $shown", fontSize = 18.sp, color = TextGray, modifier = Modifier.weight(1f))
            if (stored != null) TextButton(onClick = { onChange(null) }) { Text("Clear", color = TextGray) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange(formatNumber(snap((stored ?: p.min) - step))) }) {
                Text("—", fontSize = 26.sp, color = TextDark)
            }
            val steps = (((p.max - p.min) / step).roundToInt() - 1).coerceAtLeast(0)
            Slider(
                value = sliderValue,
                onValueChange = { dragging = true; pos = snap(it) },
                onValueChangeFinished = { dragging = false; onChange(formatNumber(pos)) },
                valueRange = p.min..p.max,
                steps = if (steps <= 200) steps else 0,
                colors = SliderDefaults.colors(
                    thumbColor = Teal,
                    activeTrackColor = Teal,
                    inactiveTrackColor = Color(0xFFDDDDDD),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                onChange(formatNumber(snap(if (stored == null) p.min + step else stored + step)))
            }) {
                Text("+", fontSize = 30.sp, color = TextDark)
            }
        }
    }
}

@Composable
private fun NotesBlock(dateKey: String, entry: DayEntry, bg1: Color, bg2: Color) {
    var editNote by remember { mutableStateOf(false) }
    var addTimed by remember { mutableStateOf(false) }
    var deleteIndex by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxWidth().background(bg1).padding(bottom = 16.dp)) {
        Box(Modifier.fillMaxWidth()) {
            SectionHeader("Today's Note:")
            TextButton(onClick = { editNote = true }, modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 8.dp)) {
                Text("Edit", color = TextGray)
            }
        }
        Text(
            entry.note.ifBlank { "Tap Edit to write a note for this day." },
            color = if (entry.note.isBlank()) Color(0xFF999999) else TextDark,
            fontSize = 17.sp,
            modifier = Modifier.fillMaxWidth().clickable { editNote = true }.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    Column(Modifier.fillMaxWidth().background(bg2).padding(bottom = 12.dp)) {
        SectionHeader("Notes:")
        entry.timedNotes.forEachIndexed { i, n ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.Top) {
                Text(n.time.substringAfter(' '), color = Teal, fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp, end = 10.dp))
                Text(n.text, color = TextDark, fontSize = 17.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { deleteIndex = i }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, "Delete note", tint = Color(0xFFAAAAAA), modifier = Modifier.size(18.dp))
                }
            }
        }
        LinkButton("Add timestamped note", Modifier.align(Alignment.CenterHorizontally)) { addTimed = true }
    }

    if (editNote) TextInputDialog(
        title = "Today's Note",
        initial = entry.note,
        onDismiss = { editNote = false },
        onSave = { Repo.setNote(dateKey, it); editNote = false },
    )
    if (addTimed) TextInputDialog(
        title = "Timestamped note",
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
            title = "Delete note?",
            message = entry.timedNotes.getOrNull(idx)?.text ?: "",
            confirmText = "Delete",
            onDismiss = { deleteIndex = null },
            onConfirm = { Repo.removeTimedNote(dateKey, idx); deleteIndex = null },
        )
    }
}
