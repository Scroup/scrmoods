package com.scrmoods.app

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream

/**
 * Reads and writes everything (tracking point setup + all days) as one XML file:
 *
 * <scrmoods version="1">
 *   <sections>
 *     <section id="daily" title="Daily Check-in"/>
 *   </sections>
 *   <points>
 *     <point id="outlook" section="daily" type="scale" label="How was your overall outlook today?" archived="false">
 *       <option>Negative</option><option>Neutral</option><option>Positive</option>
 *     </point>
 *     <point id="med_x" section="meds" type="yesno" label="Vitamin D" archived="false">
 *       <reminder time="08:00"/>
 *     </point>
 *     <point id="sleep_hours" section="health" type="number" label="Last night's sleep" min="0" max="16" step="0.5" unit="hrs" archived="false"/>
 *   </points>
 *   <entries>
 *     <entry date="2026-10-11">
 *       <value point="outlook" text="Positive">2</value>
 *       <value point="sleep_hours" text="7.5 hrs">7.5</value>
 *       <note>Today's note</note>
 *       <timednote time="2026-10-11 23:52">A timestamped note</timednote>
 *     </entry>
 *   </entries>
 * </scrmoods>
 */
object XmlStore {

    fun write(data: AppData, out: OutputStream) {
        val s = Xml.newSerializer()
        s.setOutput(out, "UTF-8")
        s.startDocument("UTF-8", true)
        s.text("\n")
        s.startTag(null, "scrmoods").attribute(null, "version", "1")

        nl(s, 1); s.startTag(null, "sections")
        for (sec in data.sections) {
            nl(s, 2)
            s.startTag(null, "section")
            s.attribute(null, "id", sec.id)
            s.attribute(null, "title", sec.title)
            if (sec.hidden) s.attribute(null, "hidden", "true")
            s.endTag(null, "section")
        }
        nl(s, 1); s.endTag(null, "sections")

        nl(s, 1); s.startTag(null, "points")
        for (p in data.points) {
            nl(s, 2)
            s.startTag(null, "point")
            s.attribute(null, "id", p.id)
            s.attribute(null, "section", p.sectionId)
            s.attribute(null, "type", p.type.xmlName)
            s.attribute(null, "label", p.label)
            if (p.type == PointType.NUMBER) {
                s.attribute(null, "min", p.min.toString())
                s.attribute(null, "max", p.max.toString())
                s.attribute(null, "step", p.step.toString())
                s.attribute(null, "unit", p.unit)
            }
            s.attribute(null, "archived", p.archived.toString())
            s.attribute(null, "better", p.better.xmlName)
            for (o in p.options) {
                nl(s, 3)
                s.startTag(null, "option").text(o).endTag(null, "option")
            }
            for (r in p.reminders) {
                nl(s, 3)
                s.startTag(null, "reminder").attribute(null, "time", r).endTag(null, "reminder")
            }
            if (p.options.isNotEmpty() || p.reminders.isNotEmpty()) nl(s, 2)
            s.endTag(null, "point")
        }
        nl(s, 1); s.endTag(null, "points")

        writePlan(s, data.plan)

        if (data.todos.isNotEmpty()) {
            nl(s, 1); s.startTag(null, "todos")
            for (t in data.todos) {
                nl(s, 2)
                s.startTag(null, "todo").attribute(null, "id", t.id).attribute(null, "created", t.created)
                if (t.doneOn != null) s.attribute(null, "done", t.doneOn)
                s.text(t.text).endTag(null, "todo")
            }
            nl(s, 1); s.endTag(null, "todos")
        }

        val pointsById = data.points.associateBy { it.id }
        nl(s, 1); s.startTag(null, "entries")
        for (e in data.entries.values.sortedBy { it.date }) {
            if (e.isEmpty) continue
            nl(s, 2)
            s.startTag(null, "entry").attribute(null, "date", e.date)
            for ((pid, v) in e.values) {
                nl(s, 3)
                s.startTag(null, "value")
                s.attribute(null, "point", pid)
                pointsById[pid]?.let { s.attribute(null, "text", it.display(v)) }
                s.text(v)
                s.endTag(null, "value")
            }
            if (e.note.isNotBlank()) {
                nl(s, 3); s.startTag(null, "note").text(e.note).endTag(null, "note")
            }
            for (t in e.timedNotes) {
                nl(s, 3)
                s.startTag(null, "timednote").attribute(null, "time", t.time).text(t.text).endTag(null, "timednote")
            }
            nl(s, 2)
            s.endTag(null, "entry")
        }
        nl(s, 1); s.endTag(null, "entries")
        s.text("\n")
        s.endTag(null, "scrmoods")
        s.text("\n")
        s.endDocument()
        s.flush()
    }

    private fun writePlan(s: org.xmlpull.v1.XmlSerializer, plan: MedPlan) {
        if (!plan.enabled && plan.meds.isEmpty()) return
        nl(s, 1); s.startTag(null, "medplan")
        s.attribute(null, "enabled", plan.enabled.toString())
        s.attribute(null, "placement", plan.placement)
        s.attribute(null, "morning", plan.time(Slot.MORNING))
        s.attribute(null, "noon", plan.time(Slot.NOON))
        s.attribute(null, "evening", plan.time(Slot.EVENING))
        s.attribute(null, "repeat", plan.repeat.toString())
        for (m in plan.meds) {
            nl(s, 2)
            s.startTag(null, "med")
            s.attribute(null, "id", m.id)
            s.attribute(null, "name", m.name)
            s.attribute(null, "unit", m.unit)
            s.attribute(null, "color", String.format("#%06X", m.color and 0xFFFFFF))
            s.attribute(null, "remind", m.remind.toString())
            s.attribute(null, "archived", m.archived.toString())
            for (st in m.stages) {
                nl(s, 3)
                s.startTag(null, "stage")
                s.attribute(null, "dose", formatNumberPrecise(st.dose))
                s.attribute(null, "from", st.from)
                if (st.to != null) s.attribute(null, "to", st.to)
                s.attribute(null, "slots", Slot.entries.filter { it in st.slots }.joinToString(",") { it.code })
                s.endTag(null, "stage")
            }
            if (m.stages.isNotEmpty()) nl(s, 2)
            s.endTag(null, "med")
        }
        nl(s, 1); s.endTag(null, "medplan")
    }

    /** 0.25 stays "0.25" (formatNumber would round it to one decimal). */
    private fun formatNumberPrecise(f: Float): String =
        if (f == f.toLong().toFloat()) f.toLong().toString() else java.math.BigDecimal(f.toDouble()).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private fun nl(s: org.xmlpull.v1.XmlSerializer, depth: Int) {
        s.text("\n" + "  ".repeat(depth))
    }

    /** Largest file we read (a decade of daily entries is well under 5 MB). */
    const val MAX_BYTES = 20L * 1024 * 1024
    private const val MAX_DOSE = 1000f

    /** A real calendar date written as yyyy-MM-dd. */
    fun isIsoDate(s: String): Boolean =
        s.length == 10 && runCatching { java.time.LocalDate.parse(s) }.isSuccess

    fun read(input: InputStream): AppData {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(input, "UTF-8")

        var sawRoot = false
        val sections = mutableListOf<Section>()
        val points = mutableListOf<TrackingPoint>()
        val entries = linkedMapOf<String, DayEntry>()

        var curPoint: TrackingPoint? = null
        var curOptions = mutableListOf<String>()
        var curReminders = mutableListOf<String>()
        var curEntry: DayEntry? = null
        var curValues = linkedMapOf<String, String>()
        var curTimed = mutableListOf<TimedNote>()
        var plan = MedPlan()
        val todos = mutableListOf<Todo>()
        val planMeds = mutableListOf<PlanMed>()
        var curMed: PlanMed? = null
        var curStages = mutableListOf<PlanStage>()

        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "scrmoods" -> sawRoot = true
                    "section" -> sections += Section(
                        p.getAttributeValue(null, "id") ?: "",
                        p.getAttributeValue(null, "title") ?: "",
                        p.getAttributeValue(null, "hidden") == "true",
                    )
                    "point" -> {
                        curPoint = TrackingPoint(
                            id = p.getAttributeValue(null, "id") ?: "",
                            sectionId = p.getAttributeValue(null, "section") ?: Sections.CUSTOM,
                            type = PointType.fromXml(p.getAttributeValue(null, "type")),
                            label = p.getAttributeValue(null, "label") ?: "",
                            min = p.getAttributeValue(null, "min")?.toFloatOrNull()?.takeIf { it.isFinite() } ?: 0f,
                            max = p.getAttributeValue(null, "max")?.toFloatOrNull()?.takeIf { it.isFinite() } ?: 10f,
                            step = p.getAttributeValue(null, "step")?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: 1f,
                            unit = p.getAttributeValue(null, "unit") ?: "",
                            archived = p.getAttributeValue(null, "archived") == "true",
                        )
                        curPoint = curPoint.copy(
                            better = Better.fromXml(p.getAttributeValue(null, "better"))
                                ?: defaultBetter(curPoint.id, curPoint.type),
                        )
                        curOptions = mutableListOf()
                        curReminders = mutableListOf()
                    }
                    "option" -> curOptions += p.nextText()
                    "todo" -> {
                        val id = p.getAttributeValue(null, "id") ?: Todos.newId()
                        val created = p.getAttributeValue(null, "created") ?: ""
                        val done = p.getAttributeValue(null, "done")
                        val text = p.nextText()
                        if (text.isNotBlank()) todos += Todo(id, text, created, done)
                    }
                    "medplan" -> plan = MedPlan(
                        enabled = p.getAttributeValue(null, "enabled") == "true",
                        placement = if (p.getAttributeValue(null, "placement") == "tab") "tab" else "row",
                        times = Slot.entries.associateWith { s ->
                            p.getAttributeValue(null, when (s) { Slot.MORNING -> "morning"; Slot.NOON -> "noon"; Slot.EVENING -> "evening" })
                                ?.takeIf(Reminders::isValidTime) ?: s.defaultTime
                        },
                        repeat = p.getAttributeValue(null, "repeat") != "false",
                    )
                    "med" -> {
                        curMed = PlanMed(
                            id = p.getAttributeValue(null, "id") ?: Plan.newId(),
                            name = p.getAttributeValue(null, "name") ?: "",
                            unit = p.getAttributeValue(null, "unit") ?: "tab",
                            color = p.getAttributeValue(null, "color")?.removePrefix("#")?.toLongOrNull(16)?.let { it or 0xFF000000L } ?: MedPlanColors.first(),
                            remind = p.getAttributeValue(null, "remind") != "false",
                            archived = p.getAttributeValue(null, "archived") == "true",
                        )
                        curStages = mutableListOf()
                    }
                    "stage" -> {
                        // dates are parsed all over the planner and its alarms: a bad one would crash them, so drop it here
                        val from = p.getAttributeValue(null, "from")?.takeIf(::isIsoDate)
                        val to = p.getAttributeValue(null, "to")?.takeIf(::isIsoDate)
                        val dose = p.getAttributeValue(null, "dose")?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f && it <= MAX_DOSE }
                        val slots = (p.getAttributeValue(null, "slots") ?: "").split(",").mapNotNull { Slot.of(it.trim()) }.toSet()
                        if (from != null && dose != null && (to == null || to >= from)) curStages += PlanStage(dose, from, to, slots)
                    }
                    "reminder" -> p.getAttributeValue(null, "time")?.let { if (Reminders.isValidTime(it)) curReminders += it }
                    "entry" -> {
                        // a day that isn't a real yyyy-MM-dd date is skipped (the calendar and summaries parse it)
                        curEntry = DayEntry(p.getAttributeValue(null, "date")?.takeIf(::isIsoDate) ?: "")
                        curValues = linkedMapOf()
                        curTimed = mutableListOf()
                    }
                    "value" -> {
                        val pid = p.getAttributeValue(null, "point") ?: ""
                        val v = p.nextText()
                        if (pid.isNotEmpty()) curValues[pid] = v
                    }
                    "note" -> curEntry = curEntry?.copy(note = p.nextText())
                    "timednote" -> {
                        val time = p.getAttributeValue(null, "time") ?: ""
                        curTimed += TimedNote(time, p.nextText())
                    }
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "med" -> {
                        curMed?.let { planMeds += it.copy(stages = curStages.sortedBy { s -> s.from }) }
                        curMed = null
                    }
                    "point" -> {
                        curPoint?.let { if (it.id.isNotEmpty()) points += it.copy(options = curOptions.toList(), reminders = curReminders.toList()) }
                        curPoint = null
                    }
                    "entry" -> {
                        curEntry?.let {
                            if (it.date.isNotEmpty()) entries[it.date] =
                                it.copy(values = curValues.toMap(), timedNotes = curTimed.toList())
                        }
                        curEntry = null
                    }
                }
            }
            event = p.next()
        }
        if (!sawRoot) throw IllegalArgumentException("This is not a scrmoods file")

        return ensureBuiltIns(AppData(sections, points, entries, plan.copy(meds = planMeds.toList()), todos.toList()))
    }
}
