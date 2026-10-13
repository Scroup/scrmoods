package com.scrmoods.app

/** What kind of answer a tracking point takes. */
enum class PointType(val xmlName: String, val title: String) {
    SCALE("scale", "Scale (choose one option)"),
    YESNO("yesno", "Yes / No"),
    NUMBER("number", "Number (slider)"),
    TEXT("text", "Text");

    companion object {
        fun fromXml(s: String?): PointType = entries.firstOrNull { it.xmlName == s } ?: YESNO
    }
}

/** Which way is good for a tracking point. Drives the green / red colouring in Trends. */
enum class Better(val xmlName: String) {
    HIGHER("higher"), LOWER("lower"), NONE("none"),

    /** Best in the middle of the range, worse towards either end (the mood scale: 5 is balanced). */
    MIDDLE("middle");

    companion object {
        fun fromXml(s: String?): Better? = entries.firstOrNull { it.xmlName == s }
    }
}

/** [hidden] sections are switched off in Settings: not shown in the journal, their past answers are kept. */
data class Section(val id: String, val title: String, val hidden: Boolean = false)

data class TrackingPoint(
    val id: String,
    val sectionId: String,
    val type: PointType,
    val label: String,
    /** For SCALE: option labels from lowest (value 0) to highest. */
    val options: List<String> = emptyList(),
    /** For NUMBER. */
    val min: Float = 0f,
    val max: Float = 10f,
    val step: Float = 1f,
    val unit: String = "",
    /** Removed points are hidden from the check-in but their past data is kept. */
    val archived: Boolean = false,
    /** Daily reminder times as "HH:mm" (used for medications). */
    val reminders: List<String> = emptyList(),
    /** For scales the last option is "higher"; for yes/no, Yes is "higher". */
    val better: Better = Better.NONE,
)

data class TimedNote(val time: String, val text: String)

/**
 * One day of answers. Values are stored as text:
 *  SCALE  -> option index ("0", "1", ...)
 *  YESNO  -> "0" (No) or "1" (Yes)
 *  NUMBER -> number ("7.5")
 *  TEXT   -> the text
 */
data class DayEntry(
    val date: String, // yyyy-MM-dd
    val values: Map<String, String> = emptyMap(),
    val note: String = "",
    val timedNotes: List<TimedNote> = emptyList(),
) {
    val isEmpty: Boolean get() = values.isEmpty() && note.isBlank() && timedNotes.isEmpty()
}

data class AppData(
    val sections: List<Section>,
    val points: List<TrackingPoint>,
    val entries: Map<String, DayEntry>,
)

object Sections {
    const val DAILY = "daily"
    const val HEALTH = "health"
    const val SOCIAL = "social"
    const val CUSTOM = "custom"
    const val MEDS = "meds"
    const val BIPOLAR = "bipolar"
    const val WATER = "water"
}

/**
 * The water tracker: cups of 200 ml drunk today. The daily goal (in cups) is kept as the point's maximum,
 * so it travels with the data file and Trends can tell how close each day came to it.
 */
object Water {
    const val POINT = "water_cups"
    const val ML_PER_CUP = 200
    const val DEFAULT_GOAL = 8

    /** A common rule of thumb: about 30 ml per kg of body weight a day, in whole cups. */
    fun recommendedCups(weightKg: Float): Int = Math.round(weightKg * 30f / ML_PER_CUP).coerceIn(4, 20)

    fun litres(cups: Float): String = String.format(java.util.Locale.US, "%.1f", cups * ML_PER_CUP / 1000f)
}

/** Ids of the mood scale (0 severe depression, 5 balanced, 10 mania) and its "mixed features" flag. */
object MoodPoints {
    const val SCALE = "bipolar_mood"
    /** The second number on a mixed day (e.g. 2 and 8). */
    const val SCALE2 = "bipolar_mood2"
    const val MIXED = "bipolar_mixed"
}

object Defaults {
    private val freq = listOf("Not at all", "Somewhat", "Fairly", "Very")
    private val level = listOf("Not at all", "Low", "Medium", "High")

    val sections = listOf(
        Section(Sections.BIPOLAR, "Mood scale"),
        Section(Sections.DAILY, "Daily Check-in"),
        Section(Sections.HEALTH, "Health and Self Care"),
        Section(Sections.WATER, "Water"),
        Section(Sections.SOCIAL, "Mental and Social Stimulation"),
        Section(Sections.CUSTOM, "Custom tracking points"),
        Section(Sections.MEDS, "Medications/Supplements (check off those you took today)"),
    )

    val points = listOf(
        TrackingPoint(MoodPoints.SCALE, Sections.BIPOLAR, PointType.NUMBER, "Mood scale",
            min = 0f, max = 10f, step = 1f, better = Better.MIDDLE),
        TrackingPoint(MoodPoints.SCALE2, Sections.BIPOLAR, PointType.NUMBER, "Mood scale, second number on mixed days",
            min = 0f, max = 10f, step = 1f, better = Better.NONE),
        TrackingPoint(MoodPoints.MIXED, Sections.BIPOLAR, PointType.YESNO, "Mixed features", better = Better.NONE),
        TrackingPoint("outlook", Sections.DAILY, PointType.SCALE, "How was your overall outlook today?",
            listOf("Negative", "Neutral", "Positive"), better = Better.HIGHER),
        TrackingPoint("productive", Sections.DAILY, PointType.SCALE, "How productive were you today?", freq, better = Better.HIGHER),
        TrackingPoint("motivated", Sections.DAILY, PointType.SCALE, "How motivated did you feel today?", freq, better = Better.HIGHER),
        TrackingPoint("lonely", Sections.DAILY, PointType.SCALE, "Did you feel lonely today?", freq, better = Better.LOWER),
        TrackingPoint("depressed", Sections.DAILY, PointType.SCALE, "Did you feel depressed or hopeless today?", freq, better = Better.LOWER),
        TrackingPoint("anxious", Sections.DAILY, PointType.SCALE, "Did you feel anxious today?", freq, better = Better.LOWER),

        TrackingPoint("sleep_hours", Sections.HEALTH, PointType.NUMBER, "Last night's sleep",
            min = 0f, max = 16f, step = 0.5f, unit = "hrs"),
        TrackingPoint("restful_sleep", Sections.HEALTH, PointType.YESNO, "Did you have a restful night's sleep?", better = Better.HIGHER),
        TrackingPoint("nutrition", Sections.HEALTH, PointType.YESNO, "Have you met your nutrition needs today?", better = Better.HIGHER),
        TrackingPoint("outside", Sections.HEALTH, PointType.YESNO, "Did you spend time outside?", better = Better.HIGHER),
        TrackingPoint("exercise", Sections.HEALTH, PointType.YESNO, "Did you exercise today?", better = Better.HIGHER),
        TrackingPoint("hygiene", Sections.HEALTH, PointType.YESNO, "Did you tend to your personal hygiene today?", better = Better.HIGHER),

        TrackingPoint("friends_family", Sections.SOCIAL, PointType.YESNO, "Did you spend quality time engaging with friends or family?", better = Better.HIGHER),
        TrackingPoint("mindfulness", Sections.SOCIAL, PointType.YESNO, "Did you practice any kind of mindfulness or meditation?", better = Better.HIGHER),
        TrackingPoint("therapy", Sections.SOCIAL, PointType.YESNO, "Did you have talk therapy today?", better = Better.HIGHER),
        TrackingPoint("chores", Sections.SOCIAL, PointType.YESNO, "Did you accomplish any household chores or tasks?", better = Better.HIGHER),
        TrackingPoint("news_media", Sections.SOCIAL, PointType.SCALE, "News and media exposure:", level, better = Better.LOWER),
        TrackingPoint("social_media", Sections.SOCIAL, PointType.SCALE, "Social media exposure:", level, better = Better.LOWER),

        TrackingPoint(Water.POINT, Sections.WATER, PointType.NUMBER, "Water",
            min = 0f, max = Water.DEFAULT_GOAL.toFloat(), step = 1f, unit = "cups", better = Better.HIGHER),
    )

    fun data() = AppData(sections, points, emptyMap())
}

/** Readable text for a stored value, e.g. "Positive", "Yes", "7.5 hrs". */
fun TrackingPoint.display(value: String?): String {
    if (value == null) return ""
    return when (type) {
        PointType.SCALE -> options.getOrNull(value.toIntOrNull() ?: -1) ?: value
        PointType.YESNO -> if (value == "1") "Yes" else "No"
        PointType.NUMBER -> formatNumber(value.toFloatOrNull() ?: 0f) + if (unit.isNotBlank()) " $unit" else ""
        PointType.TEXT -> value
    }
}

fun formatNumber(f: Float): String =
    if (f == f.toLong().toFloat()) f.toLong().toString() else String.format(java.util.Locale.US, "%.1f", f)

/** Position of a value within the point's range: 0 = lowest option / No / minimum, 1 = highest. */
fun TrackingPoint.fraction(v: Float): Float = when (type) {
    PointType.SCALE -> if (options.size > 1) v / (options.size - 1) else 1f
    PointType.YESNO -> v
    PointType.NUMBER -> if (max > min) (v - min) / (max - min) else 0f
    PointType.TEXT -> 0f
}.coerceIn(0f, 1f)

/** How good a value is, from 0 (bad) to 1 (good); null when the point has no good or bad direction. */
fun TrackingPoint.goodness(v: Float): Float? = when (better) {
    Better.HIGHER -> fraction(v)
    Better.LOWER -> 1f - fraction(v)
    Better.MIDDLE -> 1f - kotlin.math.abs(fraction(v) - 0.5f) * 2f
    Better.NONE -> null
}

/** Good/bad direction for points saved before this setting existed. */
fun defaultBetter(id: String, type: PointType): Better =
    Defaults.points.firstOrNull { it.id == id }?.better
        ?: if (type == PointType.YESNO) Better.HIGHER else Better.NONE

/**
 * Makes sure built-in sections exist (in their usual order) and that the mood scale and water points exist,
 * so files saved by older versions get the new features.
 */
fun ensureBuiltIns(d: AppData): AppData {
    val sections = d.sections.toMutableList()
    Defaults.sections.forEachIndexed { i, def ->
        if (sections.none { it.id == def.id }) sections.add(i.coerceAtMost(sections.size), def)
    }
    val points = d.points.toMutableList()
    for (id in listOf(MoodPoints.MIXED, MoodPoints.SCALE2, MoodPoints.SCALE)) { // added at the front, so the scale ends up first
        if (points.none { it.id == id }) points.add(0, Defaults.points.first { it.id == id })
    }
    if (points.none { it.id == Water.POINT }) points.add(Defaults.points.first { it.id == Water.POINT })
    return d.copy(sections = sections, points = points)
}

/*
 * Medications can be taken several times a day (e.g. magnesium with each meal).
 * Once a day: a yes/no point ("1" taken, "0" not). Several times: a number point from 0 to the doses a day,
 * holding how many were taken. Both show as a row of little pills to tap.
 */

/** A medication shown as pills (yes/no or a dose count in the medications section). */
val TrackingPoint.isPillMed: Boolean
    get() = sectionId == Sections.MEDS && (type == PointType.YESNO || type == PointType.NUMBER)

/** How many times a day it's taken. */
val TrackingPoint.doses: Int get() = if (type == PointType.NUMBER) max.toInt().coerceIn(1, 12) else 1

/** How many doses a stored answer means. */
fun dosesTaken(value: String?): Int = value?.replace(',', '.')?.toFloatOrNull()?.toInt()?.coerceAtLeast(0) ?: 0

/** The same medication taken [n] times a day. */
fun TrackingPoint.withDoses(n: Int): TrackingPoint =
    if (n <= 1) copy(type = PointType.YESNO, min = 0f, max = 10f, step = 1f, unit = "", better = Better.HIGHER)
    else copy(type = PointType.NUMBER, min = 0f, max = n.toFloat(), step = 1f, unit = "doses", better = Better.HIGHER)

/** The stored answer for [taken] doses out of [doses]. */
fun doseValue(taken: Int, doses: Int): String = if (doses <= 1) (if (taken >= 1) "1" else "0") else taken.coerceIn(0, doses).toString()

/** Keeps old answers meaning the same when the doses a day change: "all taken" stays all taken. */
fun remapDose(value: String, from: Int, to: Int): String {
    val taken = dosesTaken(value)
    return if (taken >= from) doseValue(to, to) else doseValue(minOf(taken, to - 1).coerceAtLeast(0), to)
}
