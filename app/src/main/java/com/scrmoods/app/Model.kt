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
}

/** Ids of the mood scale (0 severe depression, 5 balanced, 10 mania) and its "mixed features" flag. */
object MoodPoints {
    const val SCALE = "bipolar_mood"
    const val MIXED = "bipolar_mixed"
}

object Defaults {
    private val freq = listOf("Not at all", "Somewhat", "Fairly", "Very")
    private val level = listOf("Not at all", "Low", "Medium", "High")

    val sections = listOf(
        Section(Sections.BIPOLAR, "Mood scale"),
        Section(Sections.DAILY, "Daily Check-in"),
        Section(Sections.HEALTH, "Health and Self Care"),
        Section(Sections.SOCIAL, "Mental and Social Stimulation"),
        Section(Sections.CUSTOM, "Custom tracking points"),
        Section(Sections.MEDS, "Medications/Supplements (check off those you took today)"),
    )

    val points = listOf(
        TrackingPoint(MoodPoints.SCALE, Sections.BIPOLAR, PointType.NUMBER, "Mood scale",
            min = 0f, max = 10f, step = 1f, better = Better.MIDDLE),
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
 * Makes sure built-in sections exist (in their usual order) and that the mood scale points exist,
 * so files saved by older versions get the new features.
 */
fun ensureBuiltIns(d: AppData): AppData {
    val sections = d.sections.toMutableList()
    Defaults.sections.forEachIndexed { i, def ->
        if (sections.none { it.id == def.id }) sections.add(i.coerceAtMost(sections.size), def)
    }
    val points = d.points.toMutableList()
    for (id in listOf(MoodPoints.MIXED, MoodPoints.SCALE)) { // added at the front, so the scale ends up first
        if (points.none { it.id == id }) points.add(0, Defaults.points.first { it.id == id })
    }
    return d.copy(sections = sections, points = points)
}
