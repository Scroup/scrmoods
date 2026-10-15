package com.scrmoods.app

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * The medication planner (for people who take many medications). Pure Kotlin, so it can be tested on its own;
 * the web app has the same logic.
 *
 * A medication has stages that follow each other: a dose (¼ steps for tablets), from a date to a date (or open-ended),
 * and the times of day it's taken (morning / afternoon / evening, each with one time for all medications).
 * Each intake taken is stored on that day's page as the value "plan.<med>.<slot>" = "1".
 *
 * In the data file:
 *   <medplan enabled="true" placement="row" morning="08:00" noon="14:00" evening="21:00" repeat="true">
 *     <med id="pm_x" name="Magnesium B6" unit="tab" color="#B85C4E" remind="true" archived="false">
 *       <stage dose="1.5" from="2026-10-15" to="2026-10-24" slots="m,e"/>
 *     </med>
 *   </medplan>
 */

enum class Slot(val code: String, val title: String, val defaultTime: String) {
    MORNING("m", "Morning", "08:00"),
    NOON("n", "Afternoon", "14:00"),
    EVENING("e", "Evening", "21:00");

    companion object {
        fun of(code: String): Slot? = entries.firstOrNull { it.code == code }
    }
}

data class PlanStage(
    val dose: Float,
    val from: String, // yyyy-MM-dd
    val to: String?, // null: no end date
    val slots: Set<Slot>,
) {
    fun covers(day: String) = day >= from && (to == null || day <= to)

    /** Days in the stage (null when open-ended). */
    fun days(): Long? = to?.let { ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(it)) + 1 }
}

data class PlanMed(
    val id: String,
    val name: String,
    val unit: String = "tab",
    val color: Long = MedPlanColors.first(),
    val stages: List<PlanStage> = emptyList(),
    val remind: Boolean = true,
    val archived: Boolean = false,
) {
    fun stageOn(day: String): PlanStage? = stages.firstOrNull { it.covers(day) }
    val start: String? get() = stages.minOfOrNull { it.from }
    /** The last day, or null when a stage has no end. */
    val end: String? get() = if (stages.any { it.to == null }) null else stages.mapNotNull { it.to }.maxOrNull()
    fun finishedBy(day: String) = end != null && end!! < day
}

data class MedPlan(
    val enabled: Boolean = false,
    /** "row": one line in the journal that opens the planner; "tab": its own tab at the bottom. */
    val placement: String = "row",
    val times: Map<Slot, String> = Slot.entries.associateWith { it.defaultTime },
    /** Remind again 30 minutes later when nothing was ticked. */
    val repeat: Boolean = true,
    val meds: List<PlanMed> = emptyList(),
) {
    fun time(s: Slot) = times[s] ?: s.defaultTime
}

/** Colours to tell medications apart (soft, so they sit well with every theme). */
val MedPlanColors = listOf(0xFFB85C4EL, 0xFFC9963AL, 0xFF7E9A62L, 0xFF8A6A9CL, 0xFF4F8A8BL, 0xFFC27795L)

/** One dose to take: a medication, its stage on that day and the time of day. */
data class Intake(val med: PlanMed, val stage: PlanStage, val slot: Slot) {
    val key: String get() = Plan.key(med.id, slot)
}

object Plan {
    /** Units a dose can be in (stored codes) and how they're written. */
    val UNITS = listOf("tab", "caps", "drops", "ml", "mg", "IU")
    fun unitLabel(unit: String): String = when (unit) {
        "tab" -> tr("tab.")
        "caps" -> tr("caps.")
        "drops" -> tr("drops")
        "ml" -> tr("ml")
        "mg" -> tr("mg")
        "IU" -> tr("IU")
        else -> unit
    }

    /** How much − / + changes the dose. */
    fun step(unit: String) = when (unit) { "tab" -> 0.25f; "ml" -> 0.5f; "mg", "IU" -> 50f; else -> 1f }

    fun key(medId: String, slot: Slot) = "plan.$medId.${slot.code}"
    fun isPlanKey(k: String) = k.startsWith("plan.")

    /** 1.5 → "1½", 0.25 → "¼", 2 → "2", 2.7 → "2.7". */
    fun amount(x: Float): String {
        val whole = x.toInt()
        val frac = Math.round((x - whole) * 100)
        val f = when (frac) { 0 -> ""; 25 -> "¼"; 50 -> "½"; 75 -> "¾"; else -> null }
        return when {
            f == null -> formatNumber(x)
            whole == 0 && f.isNotEmpty() -> f
            else -> "$whole$f"
        }
    }

    fun dose(stage: PlanStage, med: PlanMed) = amount(stage.dose) + " " + unitLabel(med.unit)

    /** Every dose planned for [day], by time of day, in the order the medications were added. */
    fun intakes(plan: MedPlan, day: String): List<Intake> =
        Slot.entries.flatMap { slot ->
            plan.meds.filter { !it.archived }.mapNotNull { m ->
                m.stageOn(day)?.takeIf { slot in it.slots }?.let { Intake(m, it, slot) }
            }
        }

    fun taken(d: AppData, day: String, i: Intake) = d.entries[day]?.values?.get(i.key) == "1"

    /** (taken, planned) for [day]. */
    fun progress(d: AppData, day: String): Pair<Int, Int> {
        val all = intakes(d.plan, day)
        return all.count { taken(d, day, it) } to all.size
    }

    /** The next not-yet-taken intake today at or after [nowSlot] (or the first one left). */
    fun next(d: AppData, day: String): Intake? = intakes(d.plan, day).firstOrNull { !taken(d, day, it) }

    /** Tablets (or units) a stage needs in all, when it has an end date. */
    fun total(stage: PlanStage): Float? = stage.days()?.let { it * stage.slots.size * stage.dose }

    /** The stage that starts the day after [day] for this medication, if any (a dose change tomorrow). */
    fun changeTomorrow(m: PlanMed, day: String): PlanStage? {
        val tomorrow = LocalDate.parse(day).plusDays(1).toString()
        return m.stages.firstOrNull { it.from == tomorrow }
    }

    /** Medications whose last dose is on [day]. */
    fun endsOn(plan: MedPlan, day: String) = plan.meds.filter { !it.archived && it.end == day }

    fun newId() = "pm_" + System.currentTimeMillis().toString(36)

    /**
     * When the planner is first switched on: today's simple medications become courses with no end date,
     * taken at the times of day that match their reminders (or morning / evening / afternoon by doses a day).
     */
    fun importSimple(d: AppData, today: String): List<PlanMed> {
        val have = d.plan.meds.map { it.name.lowercase() }.toSet()
        return d.points.filter { it.isPillMed && !it.archived && it.title.lowercase() !in have }.mapIndexed { i, p ->
            val fromReminders = p.reminders.mapNotNull { t ->
                val h = t.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
                when { h < 12 -> Slot.MORNING; h < 17 -> Slot.NOON; else -> Slot.EVENING }
            }.toSet()
            val slots = fromReminders.ifEmpty {
                when (p.doses) { 1 -> setOf(Slot.MORNING); 2 -> setOf(Slot.MORNING, Slot.EVENING); else -> Slot.entries.toSet() }
            }
            PlanMed(
                id = "pm_" + p.id, name = p.title, unit = "tab",
                color = MedPlanColors[i % MedPlanColors.size],
                stages = listOf(PlanStage(1f, today, null, slots)),
            )
        }
    }

    /** For the alerts: days this week (before today, checked in) on which some doses of a medication weren't ticked. */
    fun missedDays(d: AppData, m: PlanMed, week: List<LocalDate>, today: LocalDate): Int = week.count { day ->
        val k = day.toString()
        day < today && d.entries[k]?.values?.isNotEmpty() == true &&
            m.stageOn(k)?.let { st -> st.slots.any { d.entries[k]?.values?.get(key(m.id, it)) != "1" } } == true
    }
}
