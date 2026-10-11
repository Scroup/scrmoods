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

data class Section(val id: String, val title: String)

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
}

object Defaults {
    private val freq = listOf("Not at all", "Somewhat", "Fairly", "Very")
    private val level = listOf("Not at all", "Low", "Medium", "High")

    val sections = listOf(
        Section(Sections.DAILY, "Daily Check-in"),
        Section(Sections.HEALTH, "Health and Self Care"),
        Section(Sections.SOCIAL, "Mental and Social Stimulation"),
        Section(Sections.CUSTOM, "Custom tracking points"),
        Section(Sections.MEDS, "Medications/Supplements (check off those you took today)"),
    )

    val points = listOf(
        TrackingPoint("outlook", Sections.DAILY, PointType.SCALE, "How was your overall outlook today?",
            listOf("Negative", "Neutral", "Positive")),
        TrackingPoint("productive", Sections.DAILY, PointType.SCALE, "How productive were you today?", freq),
        TrackingPoint("motivated", Sections.DAILY, PointType.SCALE, "How motivated did you feel today?", freq),
        TrackingPoint("lonely", Sections.DAILY, PointType.SCALE, "Did you feel lonely today?", freq),
        TrackingPoint("depressed", Sections.DAILY, PointType.SCALE, "Did you feel depressed or hopeless today?", freq),
        TrackingPoint("anxious", Sections.DAILY, PointType.SCALE, "Did you feel anxious today?", freq),

        TrackingPoint("sleep_hours", Sections.HEALTH, PointType.NUMBER, "Last night's sleep",
            min = 0f, max = 16f, step = 0.5f, unit = "hrs"),
        TrackingPoint("restful_sleep", Sections.HEALTH, PointType.YESNO, "Did you have a restful night's sleep?"),
        TrackingPoint("nutrition", Sections.HEALTH, PointType.YESNO, "Have you met your nutrition needs today?"),
        TrackingPoint("outside", Sections.HEALTH, PointType.YESNO, "Did you spend time outside?"),
        TrackingPoint("exercise", Sections.HEALTH, PointType.YESNO, "Did you exercise today?"),
        TrackingPoint("hygiene", Sections.HEALTH, PointType.YESNO, "Did you tend to your personal hygiene today?"),

        TrackingPoint("friends_family", Sections.SOCIAL, PointType.YESNO, "Did you spend quality time engaging with friends or family?"),
        TrackingPoint("mindfulness", Sections.SOCIAL, PointType.YESNO, "Did you practice any kind of mindfulness or meditation?"),
        TrackingPoint("therapy", Sections.SOCIAL, PointType.YESNO, "Did you have talk therapy today?"),
        TrackingPoint("chores", Sections.SOCIAL, PointType.YESNO, "Did you accomplish any household chores or tasks?"),
        TrackingPoint("news_media", Sections.SOCIAL, PointType.SCALE, "News and media exposure:", level),
        TrackingPoint("social_media", Sections.SOCIAL, PointType.SCALE, "Social media exposure:", level),
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
