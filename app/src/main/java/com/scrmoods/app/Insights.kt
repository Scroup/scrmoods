package com.scrmoods.app

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Looks for connections between what you track and how your days go, and for things that have been
 * off for a whole week. Pure Kotlin (no Android), so it can be tested on its own; the web app has the same logic.
 *
 * - [findings]: for every tracker, does it go together with better or harder days? Uses the correlation
 *   between the tracker and your outlook (or mood scale) plus the difference between "high" and "low" days,
 *   and only reports links that are clear enough in both, with at least a week of data.
 * - [alerts]: looks at the last 7 days for sustained problems (little water or sleep together with harder days,
 *   missed medications, several very high or very low mood days, or your strongest pattern pointing the wrong way).
 */
object Insights {

    /** A link between a tracker and your days. [diff] > 0: the tracker's "high" side went with better days. */
    class Finding(val point: TrackingPoint, val r: Float, val diff: Float, val n: Int, val sentence: String, val advice: String?)

    /** Something that has been off for about a week. */
    class Alert(val id: String, val title: String, val body: String, val advice: String, val urgent: Boolean = false)

    /** What "how your days went" is measured by: the outlook question if you use it, otherwise the mood scale. */
    class Target(val point: TrackingPoint, val name: String)

    /** Built-in questions that are themselves about mood, so they're compared with, not used as causes. */
    private val moodLike = setOf("outlook", "productive", "motivated", "lonely", "depressed", "anxious")

    private fun value(d: AppData, pid: String, day: LocalDate): Float? =
        d.entries[day.toString()]?.values?.get(pid)?.replace(',', '.')?.toFloatOrNull()

    private fun visible(d: AppData) = d.points.filter { p ->
        !p.archived && d.sections.firstOrNull { it.id == p.sectionId }?.hidden != true
    }

    fun target(d: AppData, days: List<LocalDate>): Target? {
        fun enough(p: TrackingPoint) = days.count { value(d, p.id, it) != null } >= 5
        val v = visible(d)
        v.firstOrNull { it.id == "outlook" && it.type == PointType.SCALE && enough(it) }?.let { return Target(it, tr("your outlook")) }
        v.firstOrNull { it.id == MoodPoints.SCALE && enough(it) }?.let { return Target(it, tr("your mood")) }
        return null
    }

    private fun goodnessOn(d: AppData, t: Target, day: LocalDate): Float? = value(d, t.point.id, day)?.let { t.point.goodness(it) }

    private fun pearson(xs: List<Float>, ys: List<Float>): Float? {
        val n = xs.size
        if (n < 3) return null
        val mx = xs.average(); val my = ys.average()
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - mx; val dy = ys[i] - my
            sxy += dx * dy; sxx += dx * dx; syy += dy * dy
        }
        if (sxx < 1e-9 || syy < 1e-9) return null
        return (sxy / sqrt(sxx * syy)).toFloat()
    }

    /** Splits days into the tracker's "high" side and "low" side (yes/no, water goal, or above/below the median). */
    private fun isHigh(p: TrackingPoint, x: Float, median: Float, allAtTop: Boolean): Boolean = when {
        p.type == PointType.YESNO -> x >= 0.5f
        p.id == Water.POINT || p.isPillMed -> x >= p.max // water goal reached / every dose taken
        allAtTop -> x >= median
        else -> x > median
    }

    private fun strength(r: Float) = when {
        abs(r) >= 0.6f -> tr("a strong link")
        abs(r) >= 0.45f -> tr("a clear link")
        else -> tr("a slight link")
    }

    private fun quoted(p: TrackingPoint) = tr("“{0}”", p.title.trimEnd(':'))

    private fun sentence(p: TrackingPoint, better: Boolean, r: Float, t: Target): String {
        val s = strength(r)
        return when {
            p.id == Water.POINT ->
                if (better) tr("On days you reached your water goal, {0} was better ({1}).", t.name, s)
                else tr("On days you reached your water goal, {0} was lower ({1}).", t.name, s)
            p.id == "sleep_hours" ->
                if (better) tr("After longer nights, {0} was better ({1}).", t.name, s)
                else tr("After longer nights, {0} was lower ({1}).", t.name, s)
            p.isPillMed && p.type == PointType.NUMBER ->
                if (better) tr("On days you took every dose of {0}, {1} was better ({2}).", quoted(p), t.name, s)
                else tr("On days you took every dose of {0}, {1} was lower ({2}).", quoted(p), t.name, s)
            p.type == PointType.YESNO ->
                if (better) tr("On days you answered yes to {0}, {1} was better ({2}).", quoted(p), t.name, s)
                else tr("On days you answered yes to {0}, {1} was lower ({2}).", quoted(p), t.name, s)
            else ->
                if (better) tr("When {0} was higher, {1} was better ({2}).", quoted(p), t.name, s)
                else tr("When {0} was higher, {1} was lower ({2}).", quoted(p), t.name, s)
        }
    }

    /** What to do about a link, or null when there's nothing useful to say. */
    private fun advice(p: TrackingPoint, better: Boolean): String? {
        val usual = when (p.better) {
            Better.HIGHER -> better
            Better.LOWER -> !better
            else -> null
        }
        if (p.id == "sleep_hours") return if (better) tr("Aim for 7 or more hours: a regular bedtime helps.") else null
        if (usual == null) return null
        if (!usual) return tr("This goes against the usual direction, so it may be a coincidence or something else going on those days.")
        if (p.id == Water.POINT) return tr("Keep reaching your water goal ({0}).", "${p.goalCups} " + trp(p.goalCups, "cup", "cups"))
        if (p.sectionId == Sections.MEDS) return tr("Keep taking it as prescribed.")
        return when (p.id) {
            "exercise" -> tr("Even a short walk counts.")
            "outside" -> tr("A few minutes outside may help.")
            "friends_family" -> tr("Time with people you like seems to help.")
            "mindfulness" -> tr("A few minutes a day is enough.")
            "restful_sleep" -> tr("Protect your sleep: a calm evening routine helps.")
            "nutrition" -> tr("Regular meals seem to help.")
            "hygiene" -> tr("Small self-care steps matter.")
            "chores" -> tr("Small tasks done seem to lift the day.")
            "therapy" -> tr("Therapy days seem to help.")
            "social_media", "news_media" -> tr("Try a little less screen time and see how it feels.")
            else -> if (p.better == Better.LOWER) tr("Worth trying to keep it lower.") else tr("Worth keeping up.")
        }
    }

    /** Links between your trackers and your days over [days], strongest first. */
    fun findings(d: AppData, days: List<LocalDate>, max: Int = 4): List<Finding> {
        val t = target(d, days) ?: return emptyList()
        val g = days.mapNotNull { day -> goodnessOn(d, t, day)?.let { day to it } }.toMap()
        val out = mutableListOf<Finding>()
        for (p in visible(d)) {
            if (p.id == t.point.id || p.type == PointType.TEXT) continue
            if (p.sectionId == Sections.BIPOLAR || p.id in moodLike) continue
            val pairs = days.mapNotNull { day -> val x = value(d, p.id, day); val y = g[day]; if (x != null && y != null) x to y else null }
            if (pairs.size < 7) continue
            val r = pearson(pairs.map { it.first }, pairs.map { it.second }) ?: continue
            val sorted = pairs.map { it.first }.sorted()
            val median = sorted[sorted.size / 2]
            val allAtTop = pairs.none { it.first > median }
            val hi = pairs.filter { isHigh(p, it.first, median, allAtTop) }.map { it.second }
            val lo = pairs.filterNot { isHigh(p, it.first, median, allAtTop) }.map { it.second }
            if (hi.size < 3 || lo.size < 3) continue
            val diff = (hi.average() - lo.average()).toFloat()
            if (abs(r) < 0.3f || abs(diff) < 0.15f || (r > 0) != (diff > 0)) continue
            out += Finding(p, r, diff, pairs.size, sentence(p, diff > 0, r, t), advice(p, diff > 0))
        }
        return out.sortedByDescending { abs(it.r) * sqrt(it.n.toFloat()) }.take(max)
    }

    /** Things that have been off in the 7 days up to [today], most important first. */
    fun alerts(d: AppData, today: LocalDate): List<Alert> {
        val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val month = (29 downTo 0).map { today.minusDays(it.toLong()) }
        val v = visible(d)
        val t = target(d, month)
        val weekG = if (t == null) emptyList() else week.mapNotNull { goodnessOn(d, t, it) }
        val harder = weekG.size >= 3 && weekG.average() <= 0.45
        val out = mutableListOf<Alert>()

        // very high or very low mood days (the mood scale)
        if (v.any { it.id == MoodPoints.SCALE }) {
            val high = week.count { day ->
                (value(d, MoodPoints.SCALE, day) ?: 0f) >= 8f ||
                    (value(d, MoodPoints.MIXED, day) == 1f && (value(d, MoodPoints.SCALE2, day) ?: 0f) >= 8f)
            }
            val low = week.count { (value(d, MoodPoints.SCALE, it) ?: 99f) <= 2f }
            if (high >= 3) out += Alert(
                "mood_high", tr("Several high days this week"),
                tr("Your mood scale was at 8 or above on {0} {1} in the last week.", high, trp(high, "day", "days")),
                tr("High stretches can build up quickly. Consider contacting your doctor, and protect your sleep."), urgent = true,
            )
            if (low >= 3) out += Alert(
                "mood_low", tr("A heavy week"),
                tr("Your mood scale was at 2 or below on {0} {1} in the last week.", low, trp(low, "day", "days")),
                tr("You don't have to carry this alone: consider reaching out to your doctor or someone you trust."), urgent = true,
            )
        }

        val found = findings(d, month, max = 20)

        // water: below the goal most of the week, together with harder days
        val water = v.firstOrNull { it.id == Water.POINT }
        if (water != null && harder) {
            val cups = week.mapNotNull { value(d, water.id, it) }
            val under = cups.count { it < water.max }
            if (cups.size >= 4 && under >= maxOf(4, cups.size - 1)) {
                val avg = cups.average().toFloat()
                val linked = found.firstOrNull { it.point.id == Water.POINT && it.diff > 0 }
                out += Alert(
                    "water_low", tr("Low water, harder days"),
                    tr(
                        "This week you drank about {0} a day against a goal of {1}, and your days were harder than usual.",
                        "${formatNumber(avg)} " + trp(Math.round(avg), "cup", "cups"), "${water.goalCups} " + trp(water.goalCups, "cup", "cups"),
                    ),
                    tr("Try to reach your goal: keep a glass in sight and refill it after each cup.") +
                        if (linked != null) " " + tr("In your own data, days with enough water were better days.") else "",
                )
            }
        }

        // sleep: short nights together with harder days
        val sleep = v.firstOrNull { it.id == "sleep_hours" }
        if (sleep != null && harder) {
            val nights = week.mapNotNull { value(d, sleep.id, it) }
            if (nights.size >= 4 && nights.average() < 6.0) {
                out += Alert(
                    "sleep_low", tr("Short nights, harder days"),
                    tr("This week you slept about {0} h a night, and your days were harder.", String.format(Locale.US, "%.1f", nights.average())),
                    tr("Try a calm, regular bedtime. Sleeping much less than usual can also be an early sign of a mood shift."),
                )
            }
        }

        // medications not (fully) taken: on finished days you checked in, since you started logging the medication
        val missed = v.filter { it.isPillMed }.map { p ->
            val first = d.entries.keys.filter { d.entries[it]?.values?.containsKey(p.id) == true }.minOrNull()
            p to week.filter { day ->
                day < today && first != null && day.toString() >= first && d.entries[day.toString()]?.values?.isNotEmpty() == true
            }.count { day -> ((value(d, p.id, day) ?: 0f).toInt()) < p.doses }
        }.filter { it.second >= 3 }
        if (missed.isNotEmpty()) out += Alert(
            "meds_missed", tr("Missed medications"),
            missed.joinToString(" ") { (p, n) -> tr("{0} wasn't fully taken on {1} {2} this week.", p.title, n, trp(n, "day", "days")) },
            tr("If it's hard to keep up, reminders can help, and so can talking to your doctor."),
        )

        // your strongest other pattern, pointing the wrong way most of this week
        if (harder) {
            val strong = found.firstOrNull { f ->
                abs(f.r) >= 0.45f && f.point.id != Water.POINT && f.point.id != "sleep_hours" && f.point.sectionId != Sections.MEDS
            }
            if (strong != null) {
                val p = strong.point
                val xs = month.mapNotNull { value(d, p.id, it) }.sorted()
                if (xs.isNotEmpty()) {
                    val median = xs[xs.size / 2]
                    val allAtTop = xs.none { it > median }
                    val weekX = week.mapNotNull { value(d, p.id, it) }
                    // the side that went with harder days
                    val wrong = weekX.count { isHigh(p, it, median, allAtTop) != (strong.diff > 0) }
                    if (weekX.size >= 4 && wrong >= weekX.size - 1) out += Alert(
                        "pattern_" + p.id, tr("A pattern to watch"),
                        strong.sentence + " " + tr("This week it's been on the harder side most days."),
                        strong.advice ?: tr("Worth keeping an eye on."),
                    )
                }
            }
        }
        return out.sortedByDescending { it.urgent }.take(3)
    }
}
