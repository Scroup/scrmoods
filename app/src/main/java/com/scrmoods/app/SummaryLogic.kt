package com.scrmoods.app

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One page of the Summary: a small kicker, a big headline and a few plain sentences. */
data class SummaryPage(val kicker: String, val headline: String, val body: List<String>, val tone: SummaryTone)

enum class SummaryTone { NEUTRAL, GOOD, CARE, MOOD }

/**
 * Builds a retrospective in plain words from the tracked answers, Spotify-Wrapped style.
 * [days] is the period (oldest first); the same number of days before it is used for comparisons.
 * Pure Kotlin, so it runs anywhere and can be tested without a phone.
 */
object SummaryLogic {
    private val dayFmt get() = DateTimeFormatter.ofPattern("d MMMM", Lang.locale)

    /** "7 days" / "year" as said after "your last…" (in Russian: "последние 7 дней", "последний год"). */
    fun lastPeriod(label: String) = if (!Lang.isRussian) label else ruPeriod(label, "последние", "последний")

    /** The period before it (in Russian: "предыдущие 7 дней", "предыдущий год"). */
    private fun prevPeriod(label: String) = if (!Lang.isRussian) label else ruPeriod(label, "предыдущие", "предыдущий")

    private fun ruPeriod(label: String, many: String, one: String): String {
        val n = label.substringBefore(' ').toIntOrNull()
        return if (n != null) "$many $n ${trp(n, "day", "days")}" else "$one ${tr(label)}"
    }

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    /** A question in quotation marks, in the app's language. */
    private fun quoted(p: TrackingPoint) = tr("“{0}”", p.title.trimEnd(':'))

    fun build(d: AppData, days: List<LocalDate>, periodLabel: String): List<SummaryPage> {
        if (days.isEmpty()) return emptyList()
        val lastP = lastPeriod(periodLabel)
        val prevP = prevPeriod(periodLabel)
        val prev = days.map { it.minusDays(days.size.toLong()) }
        fun v(pid: String, day: LocalDate): Float? = d.entries[day.toString()]?.values?.get(pid)?.replace(',', '.')?.toFloatOrNull()
        fun vals(p: TrackingPoint, ds: List<LocalDate>) = ds.mapNotNull { v(p.id, it) }
        val active = d.points.filter { !it.archived && d.sections.firstOrNull { s -> s.id == it.sectionId }?.hidden != true }
        val pages = mutableListOf<SummaryPage>()

        // 1. Overview
        val logged = days.filter { d.entries.containsKey(it.toString()) }
        if (logged.isEmpty()) {
            return listOf(
                SummaryPage(
                    cap(tr("Your last {0}", lastP)), tr("Nothing written yet in this period"),
                    listOf(tr("Check in for a few days and your summary will start to take shape.")), SummaryTone.NEUTRAL,
                ),
            )
        }
        var run = 0; var best = 0
        for (day in days) { if (d.entries.containsKey(day.toString())) { run++; best = maxOf(best, run) } else run = 0 }
        val notes = days.sumOf { day -> d.entries[day.toString()]?.let { (if (it.note.isNotBlank()) 1 else 0) + it.timedNotes.size } ?: 0 }
        pages += SummaryPage(
            cap(tr("Your last {0}", lastP)),
            tr("You checked in on {0} of {1} days", logged.size, days.size),
            listOfNotNull(
                if (best >= 3) tr("Your longest run was {0} {1} in a row.", best, trp(best, "day", "days")) else null,
                if (notes > 0) tr("You wrote {0} {1} along the way.", notes, trp(notes, "note", "notes")) else null,
                if (logged.size == days.size) tr("Not a single day missed.") else null,
            ),
            SummaryTone.NEUTRAL,
        )

        // The measure used for "how things went": outlook if tracked, otherwise the mood scale.
        val outlook = active.firstOrNull { it.id == "outlook" && it.type == PointType.SCALE && vals(it, days).isNotEmpty() }
        val moodScale = active.firstOrNull { it.id == MoodPoints.SCALE && vals(it, days).isNotEmpty() }
        val target = outlook ?: moodScale
        val targetName = if (outlook != null) tr("your outlook") else tr("your mood")
        fun goodnessOn(day: LocalDate): Float? = target?.let { t -> v(t.id, day)?.let { t.goodness(it) } }

        // 2. Outlook
        if (outlook != null) {
            val cur = vals(outlook, days)
            val counts = outlook.options.indices.map { i -> cur.count { it.roundToInt() == i } }
            val top = counts.indices.maxByOrNull { counts[it] } ?: 0
            val split = outlook.options.indices.reversed()
                .filter { counts[it] > 0 }
                .joinToString(", ") { tr("{0} on {1} {2}", tr(outlook.options[it]).lowercase(), counts[it], trp(counts[it], "day", "days")) }
            val body = mutableListOf(tr("You felt {0}.", split))
            compare(cur.map { outlook.goodness(it) ?: 0f }, vals(outlook, prev).map { outlook.goodness(it) ?: 0f }, prevP,
                better = tr("That's brighter than the {0} before.", prevP), worse = tr("A bit heavier than the {0} before. That happens.", prevP),
            )?.let { body += it }
            val g = cur.map { outlook.goodness(it) ?: 0.5f }.average()
            pages += SummaryPage(tr("Your outlook"), tr("Mostly {0}", tr(outlook.options[top])), body, if (g >= 0.6) SummaryTone.GOOD else if (g <= 0.4) SummaryTone.CARE else SummaryTone.MOOD)
        }

        // 3. Mood scale
        if (moodScale != null) {
            val cur = days.mapNotNull { v(MoodPoints.SCALE, it) }
            val mixedDays = days.count { v(MoodPoints.MIXED, it) == 1f }
            val balanced = days.count { day -> v(MoodPoints.SCALE, day)?.roundToInt() in 4..6 && v(MoodPoints.MIXED, day) != 1f }
            // on mixed days the second number counts towards the range too
            val both = cur + days.filter { v(MoodPoints.MIXED, it) == 1f }.mapNotNull { v(MoodPoints.SCALE2, it) }
            val lo = both.min().roundToInt(); val hi = both.max().roundToInt()
            val body = mutableListOf(
                if (lo == hi) tr("Every answer sat at {0}, {1}.", lo, MoodScale.level(lo).name.lowercase())
                else tr("Your range went from {0} ({1}) to {2} ({3}).", lo, MoodScale.level(lo).name.lowercase(), hi, MoodScale.level(hi).name.lowercase()),
            )
            if (mixedDays > 0) body += tr(
                "{0} {1} mixed, low and high at once.", mixedDays,
                if (Lang.isRussian) trp(mixedDays, "day", "days") else if (mixedDays == 1) "day was" else "days were",
            )
            val prevVals = prev.mapNotNull { v(MoodPoints.SCALE, it) }
            if (prevVals.size >= 3) {
                val curShare = balanced.toFloat() / cur.size
                val prevShare = prev.count { day -> v(MoodPoints.SCALE, day)?.roundToInt() in 4..6 && v(MoodPoints.MIXED, day) != 1f }.toFloat() / prevVals.size
                if (curShare - prevShare >= 0.1f) body += tr("More balanced days than the {0} before.", prevP)
                else if (prevShare - curShare >= 0.1f) body += tr("Fewer balanced days than the {0} before. Worth keeping an eye on.", prevP)
            }
            pages += SummaryPage(
                tr("Your mood scale"), tr("Balanced on {0} of {1} days", balanced, cur.size), body,
                if (balanced.toFloat() / cur.size >= 0.6f) SummaryTone.GOOD else SummaryTone.MOOD,
            )
        }

        // 4. Sleep
        val sleep = active.firstOrNull { it.id == "sleep_hours" }
        if (sleep != null) {
            val nights = days.mapNotNull { day -> v(sleep.id, day)?.let { day to it } }
            if (nights.isNotEmpty()) {
                val avg = nights.map { it.second }.average()
                val longest = nights.maxBy { it.second }
                val shortest = nights.minBy { it.second }
                val body = mutableListOf(
                    tr("Your longest night was {0} h on {1}, the shortest {2} h.", formatNumber(longest.second), longest.first.format(dayFmt), formatNumber(shortest.second)),
                )
                val prevAvg = vals(sleep, prev).takeIf { it.size >= 3 }?.average()
                if (prevAvg != null) {
                    val diff = avg - prevAvg
                    if (abs(diff) >= 0.3) body += tr("About {0} h {1} a night than the {2} before.", "%.1f".format(Locale.US, abs(diff)), tr(if (diff > 0) "more" else "less"), prevP)
                    else body += tr("About the same as the {0} before.", prevP)
                }
                pages += SummaryPage(
                    tr("Your sleep"), tr("{0} hours a night", "%.1f".format(Locale.US, avg)), body,
                    if (avg >= 7) SummaryTone.GOOD else if (avg < 6) SummaryTone.CARE else SummaryTone.MOOD,
                )
            }
        }

        // 5 & 6. What went well, and what needs care
        val judged = active.filter {
            it.type != PointType.TEXT && it.better != Better.NONE &&
                it.id != MoodPoints.SCALE && it.id != "outlook" && it.sectionId != Sections.BIPOLAR
        }.mapNotNull { p ->
            val cur = vals(p, days)
            if (cur.size < 2) null else Triple(p, cur, p.goodness(cur.average().toFloat()) ?: return@mapNotNull null)
        }
        fun describe(p: TrackingPoint, cur: List<Float>): String = when (p.type) {
            PointType.YESNO -> tr("{0} Yes on {1} of {2} days", quoted(p), cur.count { it >= 0.5f }, cur.size)
            PointType.SCALE -> tr("{0} mostly {1}", quoted(p), p.options.getOrNull(cur.average().roundToInt())?.let { tr(it).lowercase() } ?: "")
            else -> tr("{0} {1} on average", quoted(p), "%.1f".format(Locale.US, cur.average()) + if (p.unit.isNotBlank()) " " + tr(p.unit) else "")
        }
        val wins = judged.filter { it.third >= 0.6f }.sortedByDescending { it.third }.take(4)
        if (wins.isNotEmpty()) {
            pages += SummaryPage(tr("What went well"), if (wins.size == 1) tr("One thing that held up") else tr("{0} things that held up", wins.size), wins.map { describe(it.first, it.second) }, SummaryTone.GOOD)
        }
        val care = judged.filter { it.third <= 0.4f }.sortedBy { it.third }.take(3)
        if (care.isNotEmpty()) {
            pages += SummaryPage(
                tr("Worth some extra care"),
                if (care.size == 1) tr("One thing to be gentle with") else tr("{0} things to be gentle with", care.size),
                care.map { describe(it.first, it.second) } + tr("Small steps count."),
                SummaryTone.CARE,
            )
        }

        // 7. Patterns: what tends to go together with a better or harder day
        if (target != null) {
            val found = mutableListOf<Pair<Float, String>>()
            for (p in active.filter { it.type == PointType.YESNO && it.sectionId != Sections.BIPOLAR }) {
                val yes = days.filter { v(p.id, it) == 1f }.mapNotNull { goodnessOn(it) }
                val no = days.filter { v(p.id, it) == 0f }.mapNotNull { goodnessOn(it) }
                if (yes.size < 3 || no.size < 3) continue
                val diff = (yes.average() - no.average()).toFloat()
                if (abs(diff) < 0.12f) continue
                val q = quoted(p)
                found += diff to if (diff > 0) tr("On days you answered yes to {0}, {1} was noticeably better.", q, targetName)
                else tr("On days you answered yes to {0}, {1} tended to be lower.", q, targetName)
            }
            if (sleep != null) {
                val long = days.filter { (v(sleep.id, it) ?: -1f) >= 7f }.mapNotNull { goodnessOn(it) }
                val short = days.filter { (v(sleep.id, it) ?: 99f) < 7f }.mapNotNull { goodnessOn(it) }
                if (long.size >= 3 && short.size >= 3) {
                    val diff = (long.average() - short.average()).toFloat()
                    if (abs(diff) >= 0.12f) found += diff to if (diff > 0) tr("After 7 or more hours of sleep, {0} was better.", targetName)
                    else tr("Interestingly, {0} wasn't better after longer nights.", targetName)
                }
            }
            val picked = found.sortedByDescending { abs(it.first) }.take(3)
            if (picked.isNotEmpty()) {
                pages += SummaryPage(
                    tr("Patterns worth noticing"), tr("What goes with your better days"),
                    picked.map { it.second } + tr("These are patterns in your answers, not proof of cause."),
                    SummaryTone.MOOD,
                )
            }

            // 8. Brightest day
            val scored = days.mapNotNull { day -> goodnessOn(day)?.let { day to it } }
            val bestG = scored.maxOfOrNull { it.second }
            if (bestG != null && bestG >= 0.6f) {
                val candidates = scored.filter { it.second == bestG }.map { it.first }
                val day = candidates.firstOrNull { !d.entries[it.toString()]?.note.isNullOrBlank() } ?: candidates.last()
                val note = d.entries[day.toString()]?.note?.trim().orEmpty()
                val body = if (note.isNotEmpty()) listOf(tr("You wrote: “{0}”", if (note.length > 160) note.take(157).trimEnd() + "…" else note))
                else listOf(if (outlook != null) tr("Your outlook that day: {0}.", outlook.shown(v(outlook.id, day)?.roundToInt()?.toString())) else tr("One of your steadiest days."))
                pages += SummaryPage(tr("Your brightest day"), day.format(dayFmt), body, SummaryTone.GOOD)
            }
        }

        // 9. Closing
        pages += SummaryPage(
            tr("That's your {0}", lastP),
            tr("Thank you for showing up for yourself"),
            listOf(tr("Every check-in adds to the picture. See you tomorrow.")),
            SummaryTone.NEUTRAL,
        )
        return pages
    }

    /** A sentence comparing this period's goodness with the one before, or null if there's too little data or no real change. */
    private fun compare(cur: List<Float>, prev: List<Float>, prevP: String, better: String, worse: String): String? {
        if (cur.isEmpty() || prev.size < 3) return null
        val diff = cur.average() - prev.average()
        return when {
            diff >= 0.08 -> better
            diff <= -0.08 -> worse
            else -> tr("Much the same as the {0} before.", prevP)
        }
    }
}
