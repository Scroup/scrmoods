package com.scrmoods.app

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/*
 * The medication planner's screens: the day (a strip of days whose circles fill as doses are ticked, then the doses
 * by time of day), the list of courses, and the editor (stages with a dose, dates and times of day).
 */

private val dayFmt get() = DateTimeFormatter.ofPattern("d MMMM", Lang.locale)
private val shortFmt get() = DateTimeFormatter.ofPattern("d MMM", Lang.locale)
private val longFmt get() = DateTimeFormatter.ofPattern("d MMM yyyy", Lang.locale)

fun slotName(s: Slot) = tr(s.title)

/** Whether the planner shows as a line in the journal / its own tab. */
val MedPlan.asRow get() = enabled && placement != "tab"
val MedPlan.asTab get() = enabled && placement == "tab"

/** The whole planner: day → courses → editor. [onBack] is null when it's a tab of its own. */
@Composable
fun PlannerScreen(startDay: String = LocalDate.now().toString(), onBack: (() -> Unit)?) {
    var page by rememberSaveable { mutableStateOf("day") }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var editSlot by rememberSaveable { mutableStateOf<String?>(null) }
    var day by rememberSaveable(startDay) { mutableStateOf(startDay) }
    BackHandler(enabled = page != "day") { page = if (page == "edit") "courses" else "day" }
    when (page) {
        "courses" -> PlanCourses(
            onBack = { page = "day" },
            onEdit = { editId = it; editSlot = null; page = "edit" },
        )
        "edit" -> PlanEditor(editId, editSlot?.let { Slot.of(it) }, onDone = { page = "courses" })
        else -> PlanDay(
            day = day, onDay = { day = it }, onBack = onBack,
            onCourses = { page = "courses" },
            onAdd = { slot -> editId = null; editSlot = slot?.code; page = "edit" },
        )
    }
}

@Composable
private fun PlanHeader(title: String, onBack: (() -> Unit)?, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) RoundButton(onClick = onBack) { Text("←", fontSize = 18.sp, color = Ink) } else Spacer(Modifier.width(40.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        if (action != null) action() else Spacer(Modifier.width(40.dp))
    }
}

/** A day's circle: filled from the bottom by the share of doses ticked. */
@Composable
fun FillCircle(fraction: Float, size: Dp, ring: Boolean = false) {
    Box(
        Modifier
            .size(size)
            .then(if (ring) Modifier.border(1.5.dp, Accent, CircleShape).padding(3.dp) else Modifier)
            .clip(CircleShape)
            .background(Rule),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (fraction > 0f) Box(Modifier.fillMaxWidth().fillMaxHeight(fraction.coerceIn(0f, 1f)).background(Accent))
    }
}

@Composable
private fun PlanDay(day: String, onDay: (String) -> Unit, onBack: (() -> Unit)?, onCourses: () -> Unit, onAdd: (Slot?) -> Unit) {
    val d = Repo.data
    val today = LocalDate.now()
    val sel = LocalDate.parse(day)
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        PlanHeader(tr("Medications"), onBack) {
            RoundButton(onClick = onCourses) { Text("☰", fontSize = 17.sp, color = Accent) }
        }
        Text(
            if (sel == today) tr("Today, {0}", sel.format(dayFmt))
            else sel.dayOfWeek.getDisplayName(TextStyle.FULL, Lang.locale).replaceFirstChar { it.uppercase() } + ", " + sel.format(dayFmt),
            fontSize = 23.sp, fontWeight = FontWeight.Medium, color = Ink, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
        )

        // the strip of days, scrolled so the chosen day sits in the middle
        Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
        val days = remember(today) { (-60..30).map { today.plusDays(it.toLong()) } }
        val start = days.indexOf(sel).coerceAtLeast(0)
        val list = rememberLazyListState(initialFirstVisibleItemIndex = (start - 3).coerceAtLeast(0))
        LazyRow(state = list, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(days) { dd ->
                val k = dd.toString()
                val (taken, total) = Plan.progress(d, k)
                val chosen = dd == sel
                Column(
                    Modifier
                        .width(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { Haptics.tap(); onDay(k) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val letter = dd.dayOfWeek.getDisplayName(TextStyle.NARROW_STANDALONE, Lang.locale)
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(if (chosen) Ink else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(letter, fontSize = 14.sp, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (chosen) Paper else if (dd > today) InkFaint else InkSoft)
                    }
                    Spacer(Modifier.height(6.dp))
                    FillCircle(if (total == 0) 0f else taken.toFloat() / total, 42.dp, ring = dd == today)
                    Text("${dd.dayOfMonth}", fontSize = 11.sp, color = InkFaint, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        val (taken, total) = Plan.progress(d, day)
        Text(
            if (total == 0) tr("Nothing planned for this day.") else tr("{0} of {1} taken", taken, total),
            fontSize = 13.sp, fontStyle = FontStyle.Italic, color = InkSoft, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )

        if (d.plan.meds.none { !it.archived }) {
            Text(
                tr("Add your medications with their dose, dates and times of day. They'll show here, with a reminder at each time."),
                fontSize = 15.sp, lineHeight = 22.sp, color = InkSoft, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp),
            )
        }

        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(tr("Intake"), fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
            if (total > 0 && sel <= today) Text(tr("tap when taken"), fontSize = 13.sp, fontStyle = FontStyle.Italic, color = InkSoft)
        }
        val intakes = Plan.intakes(d.plan, day)
        for (slot in Slot.entries) {
            val mine = intakes.filter { it.slot == slot }
            if (mine.isEmpty()) continue
            SlotCard(slot, mine, day, canTick = sel <= today)
        }
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = { Haptics.tap(); onAdd(null) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("+ Add medication"), fontSize = 16.sp) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PlanCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp).fillMaxWidth().clip(shape).background(Page).border(1.dp, Rule, shape)
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
        content = content,
    )
}

@Composable
private fun SlotCard(slot: Slot, intakes: List<Intake>, day: String, canTick: Boolean) {
    PlanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Repo.data.plan.time(slot), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text("  " + slotName(slot).lowercase(Lang.locale), fontSize = 14.sp, fontStyle = FontStyle.Italic, color = InkSoft, modifier = Modifier.weight(1f))
        }
        for (i in intakes) IntakeRow(i, day, canTick)
    }
}

/** One dose: the medication's coloured button (tap to tick), its name, a note and the dose. */
@Composable
fun IntakeRow(i: Intake, day: String, canTick: Boolean = true) {
    val taken = Plan.taken(Repo.data, day, i)
    val color = Color(i.med.color)
    val pop = rememberPop()
    val next = i.med.stages.firstOrNull { it.from > day }
    val sub = when {
        taken -> tr("taken")
        next != null && i.stage.to != null -> tr("from {0} — {1}", LocalDate.parse(next.from).format(shortFmt), Plan.dose(next, i.med))
        i.stage.to != null -> tr("until {0}", LocalDate.parse(i.stage.to).format(shortFmt))
        else -> ""
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        // a small colour mark, the name, the dose, and the tick button on the right
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(i.med.name, fontSize = 17.sp, color = Ink)
            if (sub.isNotEmpty()) Text(sub, fontSize = 12.5.sp, color = InkSoft)
        }
        Text(Plan.dose(i.stage, i.med), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = InkSoft, modifier = Modifier.padding(start = 8.dp, end = 12.dp))
        Box(
            Modifier
                .then(pop.modifier)
                .size(44.dp)
                .clip(CircleShape)
                .background(if (taken) color else color.copy(alpha = 0.2f))
                .then(if (taken) Modifier else Modifier.border(3.dp, color, CircleShape))
                .clickable(enabled = canTick) {
                    Haptics.tap()
                    pop.fire(stars = !taken, color = color)
                    Repo.tick(day, i, !taken)
                },
            contentAlignment = Alignment.Center,
        ) {
            if (taken) Text("✓", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            else Box(Modifier.size(17.dp).clip(CircleShape).background(Color.White))
        }
    }
}

/** Today's doses grouped by time of day, for the quick note window. */
@Composable
fun PlanTodayList() {
    val today = LocalDate.now().toString()
    val intakes = Plan.intakes(Repo.data.plan, today)
    for (slot in Slot.entries) {
        val mine = intakes.filter { it.slot == slot }
        if (mine.isEmpty()) continue
        Text(Repo.data.plan.time(slot) + " · " + slotName(slot).lowercase(Lang.locale), fontSize = 13.sp, color = InkSoft, modifier = Modifier.padding(top = 8.dp))
        for (i in mine) IntakeRow(i, today)
    }
}

/** The line in the journal that opens the planner: a filling circle, "Medications · 2 of 5" and what's next. */
@Composable
fun PlanRow(date: LocalDate, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val d = Repo.data
    val k = date.toString()
    val (taken, total) = Plan.progress(d, k)
    val next = if (date == LocalDate.now()) Plan.next(d, k) else null
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .padding(start = 10.dp, end = 10.dp, top = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Page)
            .border(1.dp, Rule, shape)
            .clickable { Haptics.tap(); onOpen() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FillCircle(if (total == 0) 0f else taken.toFloat() / total, 40.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                if (total == 0) tr("Medications") else tr("Medications · {0} of {1}", taken, total),
                fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Ink,
            )
            Text(
                when {
                    total == 0 -> tr("Nothing planned for this day.")
                    taken == total -> tr("All taken. Well done!")
                    next != null -> tr("Next at {0} — {1}, {2}", d.plan.time(next.slot), next.med.name, Plan.dose(next.stage, next.med))
                    else -> tr("{0} not ticked", total - taken)
                },
                fontSize = 13.sp, color = if (total > 0 && taken == total) Accent else InkSoft,
            )
        }
        Text("›", fontSize = 24.sp, color = Accent)
    }
}

/** The list of courses, now or finished. */
@Composable
private fun PlanCourses(onBack: () -> Unit, onEdit: (String?) -> Unit) {
    val d = Repo.data
    val today = LocalDate.now().toString()
    var finished by rememberSaveable { mutableStateOf(false) }
    val meds = d.plan.meds.filter { !it.archived }
    val now = meds.filter { !it.finishedBy(today) }
    val done = meds.filter { it.finishedBy(today) }
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        PlanHeader(tr("Medication courses"), onBack)
        Segmented(
            listOf(tr("Current · {0}", now.size), tr("Finished · {0}", done.size)), if (finished) 1 else 0,
            { if (it != null) finished = it == 1 },
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp), minHeight = 38, idleColor = Page,
        )
        val shown = if (finished) done else now
        if (shown.isEmpty()) Text(
            if (finished) tr("No finished courses yet.") else tr("No medications yet."),
            fontSize = 15.sp, fontStyle = FontStyle.Italic, color = InkSoft, modifier = Modifier.padding(24.dp),
        )
        for (m in shown) PlanCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color(m.color)), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White))
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(m.name, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Ink)
                    Text(
                        trp(m.stages.size, "stage", "stages") .let { "${m.stages.size} $it" } + " · " +
                            (m.start?.let { LocalDate.parse(it).format(shortFmt) } ?: "") + " — " +
                            (m.end?.let { LocalDate.parse(it).format(shortFmt) } ?: tr("no end date")),
                        fontSize = 13.sp, fontStyle = FontStyle.Italic, color = InkSoft,
                    )
                }
                LinkButton(tr("Edit")) { onEdit(m.id) }
            }
            for (st in m.stages) {
                val current = st.covers(today)
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier.padding(top = 8.dp, end = 4.dp).fillMaxWidth().clip(shape)
                        .background(if (current) AccentWash else Page).border(1.dp, Rule, shape)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Text(
                        Plan.dose(st, m) + " · " + Slot.entries.filter { it in st.slots }.joinToString(", ") { slotName(it).lowercase(Lang.locale) },
                        fontSize = 15.sp, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal, color = Ink,
                    )
                    val dates = LocalDate.parse(st.from).format(shortFmt) + " — " + (st.to?.let { LocalDate.parse(it).format(shortFmt) } ?: tr("no end date"))
                    val extra = if (current && st.days() != null) {
                        val n = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(st.from), LocalDate.now()) + 1
                        " · " + tr("day {0} of {1}", n, st.days()!!)
                    } else if (current) " · " + tr("now") else ""
                    Text(dates + extra, fontSize = 13.sp, color = InkSoft)
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = { Haptics.tap(); onEdit(null) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("+ Add medication"), fontSize = 16.sp) }
        }
    }
}

/** A stage being edited. */
private class StageDraft(dose: Float, from: LocalDate, to: LocalDate?, slots: Set<Slot>) {
    var dose by mutableStateOf(dose)
    var from by mutableStateOf(from)
    var to by mutableStateOf(to)
    var slots by mutableStateOf(slots)
}

/** Add or change a medication: name, form, colour, stages (dose, dates, times of day), reminders. */
@Composable
private fun PlanEditor(medId: String?, preSlot: Slot?, onDone: () -> Unit) {
    val context = LocalContext.current
    val existing = Repo.data.plan.meds.firstOrNull { it.id == medId }
    val today = LocalDate.now()
    fun freshStage() = StageDraft(1f, today, null, setOf(preSlot ?: Slot.MORNING))
    var name by remember(medId) { mutableStateOf(existing?.name ?: "") }
    var unit by remember(medId) { mutableStateOf(existing?.unit ?: "tab") }
    var color by remember(medId) { mutableStateOf(existing?.color ?: MedPlanColors[Repo.data.plan.meds.size % MedPlanColors.size]) }
    var remind by remember(medId) { mutableStateOf(existing?.remind ?: true) }
    val stages = remember(medId) {
        mutableStateListOf<StageDraft>().apply {
            if (existing != null) existing.stages.forEach { add(StageDraft(it.dose, LocalDate.parse(it.from), it.to?.let(LocalDate::parse), it.slots)) }
            else add(freshStage())
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun build(): PlanMed? {
        if (name.isBlank()) { error = tr("Please enter the name."); return null }
        if (stages.isEmpty()) { error = tr("Add at least one stage."); return null }
        stages.forEachIndexed { i, s ->
            if (s.dose <= 0f) { error = tr("Stage {0}: the dose must be above 0.", i + 1); return null }
            if (s.slots.isEmpty()) { error = tr("Stage {0}: choose morning, afternoon or evening.", i + 1); return null }
            if (s.to != null && s.to!! < s.from) { error = tr("Stage {0}: the end date is before the start.", i + 1); return null }
        }
        val sorted = stages.sortedBy { it.from }
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            if (prev.to == null || prev.to!! >= sorted[i].from) { error = tr("Stages overlap: each stage should end before the next one starts."); return null }
        }
        return PlanMed(
            id = existing?.id ?: Plan.newId(), name = name.trim(), unit = unit, color = color, remind = remind,
            stages = sorted.map { PlanStage(it.dose, it.from.toString(), it.to?.toString(), it.slots) },
        )
    }
    fun pickDate(initial: LocalDate, onPick: (LocalDate) -> Unit) =
        DatePickerDialog(context, { _, y, m, dd -> onPick(LocalDate.of(y, m + 1, dd)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        PlanHeader(if (existing == null) tr("New medication") else tr("Edit medication"), onDone)
        PlanCard {
            OutlinedTextField(
                value = name, onValueChange = { name = it; error = null },
                label = { Text(tr("Name")) }, singleLine = true, colors = journalFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
            )
            Text(tr("Form"), fontSize = 13.sp, color = InkSoft, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            Segmented(Plan.UNITS.map { Plan.unitLabel(it) }, Plan.UNITS.indexOf(unit).takeIf { it >= 0 }, { if (it != null) unit = Plan.UNITS[it] }, minHeight = 38, idleColor = Page)
            Text(tr("Colour"), fontSize = 13.sp, color = InkSoft, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                for (c in MedPlanColors) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .then(if (c == color) Modifier.border(2.dp, Ink, CircleShape).padding(4.dp).clip(CircleShape) else Modifier)
                            .background(Color(c))
                            .clickable { Haptics.tap(); color = c },
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
            Text(tr("Stages"), fontSize = 18.sp, fontStyle = FontStyle.Italic, color = Accent, modifier = Modifier.weight(1f))
            Text(tr("one after another"), fontSize = 13.sp, color = InkSoft)
        }
        stages.forEachIndexed { idx, s ->
            PlanCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Stage {0}", idx + 1), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                    if (stages.size > 1) Text("×", fontSize = 20.sp, color = InkSoft, modifier = Modifier.clip(CircleShape).clickable { stages.removeAt(idx) }.padding(horizontal = 12.dp))
                }
                Text(tr("Dose each time"), fontSize = 13.sp, color = InkSoft, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                val step = Plan.step(unit)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                    RoundButton(enabled = s.dose > step, onClick = { s.dose = (s.dose - step).coerceAtLeast(step) }) { Text("−", fontSize = 22.sp, color = Ink) }
                    if (unit == "mg" || unit == "IU" || unit == "ml") {
                        var text by remember(idx, unit) { mutableStateOf(formatNumber(s.dose)) }
                        OutlinedTextField(
                            value = if (text.toFloatOrNull() == s.dose) text else formatNumber(s.dose),
                            onValueChange = { v -> text = v.filter { c -> c.isDigit() || c == '.' || c == ',' }; text.replace(',', '.').toFloatOrNull()?.let { s.dose = it } },
                            singleLine = true, colors = journalFieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            suffix = { Text(Plan.unitLabel(unit)) },
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        )
                    } else Text(
                        Plan.amount(s.dose) + " " + Plan.unitLabel(unit), fontSize = 26.sp, fontWeight = FontWeight.Medium, color = Accent,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                    RoundButton(onClick = { s.dose += step }) { Text("+", fontSize = 22.sp, color = Ink) }
                }
                if (unit == "tab") Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp, end = 4.dp)) {
                    for (q in listOf(0.25f, 0.5f, 1f, 1.5f, 2f)) {
                        val on = s.dose == q
                        Text(
                            Plan.amount(q), fontSize = 16.sp, color = if (on) OnAccent else Ink, textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) Accent else Page)
                                .border(1.dp, if (on) Accent else Rule, RoundedCornerShape(50))
                                .clickable { Haptics.tap(); s.dose = q }.padding(vertical = 9.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp, end = 4.dp)) {
                    DateBox(tr("From"), s.from.format(longFmt), Modifier.weight(1f)) { pickDate(s.from) { s.from = it } }
                    DateBox(tr("To"), s.to?.format(longFmt) ?: tr("no end date"), Modifier.weight(1f)) { pickDate(s.to ?: s.from.plusDays(13)) { s.to = it } }
                }
                if (s.to != null) LinkButton(tr("No end date")) { s.to = null }
                Text(tr("When"), fontSize = 13.sp, color = InkSoft, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 4.dp)) {
                    for (slot in Slot.entries) {
                        val on = slot in s.slots
                        val shape = RoundedCornerShape(16.dp)
                        Column(
                            Modifier.weight(1f).clip(shape).background(if (on) AccentWash else Page)
                                .border(if (on) 1.5.dp else 1.dp, if (on) Accent else Rule, shape)
                                .clickable { Haptics.tap(); s.slots = if (on) s.slots - slot else s.slots + slot }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text((if (on) "✓ " else "") + slotName(slot), fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = Ink)
                            Text(Repo.data.plan.time(slot), fontSize = 12.sp, color = InkSoft)
                        }
                    }
                }
                val days = s.to?.let { java.time.temporal.ChronoUnit.DAYS.between(s.from, it) + 1 }
                if (days != null && days > 0 && s.slots.isNotEmpty()) Text(
                    tr("{0} {1} · {2} × {3} a day = {4} {5}", days, trp(days.toInt(), "day", "days"), Plan.amount(s.dose), s.slots.size, Plan.amount(days * s.slots.size * s.dose), Plan.unitLabel(unit)),
                    fontSize = 13.sp, fontStyle = FontStyle.Italic, color = InkSoft, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp)) {
            val shape = RoundedCornerShape(16.dp)
            Text(
                tr("+ Add a stage with another dose"), fontSize = 15.sp, color = Accent, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(shape).border(1.5.dp, Rule, shape).clickable {
                    Haptics.tap()
                    val last = stages.lastOrNull()
                    if (last == null) stages.add(freshStage()) else {
                        // the new stage starts the day after the last one ends (ending an open stage after two weeks)
                        if (last.to == null) last.to = maxOf(last.from, today).plusDays(13)
                        stages.add(StageDraft(last.dose, last.to!!.plusDays(1), null, last.slots))
                    }
                }.padding(vertical = 14.dp),
            )
        }
        PlanCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Remind me"), fontSize = 16.sp, color = Ink)
                    Text(tr("A notification at each time of day while the course runs"), fontSize = 13.sp, color = InkSoft)
                }
                Switch(
                    checked = remind, onCheckedChange = { remind = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = OnAccent, checkedTrackColor = Accent, uncheckedThumbColor = InkFaint, uncheckedTrackColor = Page, uncheckedBorderColor = Rule),
                )
            }
        }
        error?.let { Text(it, color = Danger, fontSize = 14.sp, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp)) }
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = { build()?.let { Haptics.tap(); Repo.saveMed(it); onDone() } },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("Save"), fontSize = 16.sp) }
            if (existing == null) LinkButton(tr("Save and add another medication"), Modifier.padding(top = 4.dp)) {
                build()?.let {
                    Repo.saveMed(it)
                    name = ""; error = null
                    color = MedPlanColors[Repo.data.plan.meds.size % MedPlanColors.size]
                    stages.clear(); stages.add(freshStage())
                }
            }
            if (existing != null) LinkButton(tr("Delete medication"), Modifier.padding(top = 4.dp)) { confirmDelete = true }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmDelete && existing != null) ConfirmDialog(
        title = tr("Delete {0}?", existing.name),
        message = tr("It disappears from the planner and reminders. The doses already ticked stay in your journal."),
        confirmText = tr("Delete"),
        onDismiss = { confirmDelete = false },
        onConfirm = { Repo.deleteMed(existing.id); confirmDelete = false; onDone() },
    )
}

@Composable
private fun DateBox(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier) {
        Text(label, fontSize = 13.sp, color = InkSoft, modifier = Modifier.padding(bottom = 4.dp))
        Text(
            value, fontSize = 15.sp, color = Ink,
            modifier = Modifier.fillMaxWidth().clip(shape).background(Color.White.copy(alpha = 0.5f)).border(1.dp, Rule, shape)
                .clickable { Haptics.tap(); onClick() }.padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}
