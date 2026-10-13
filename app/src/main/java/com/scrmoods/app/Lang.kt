package com.scrmoods.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * The app's language (Settings → Language). English text is written in the code as is and also works as the
 * key for its translation, so anything without a translation, such as your own questions, shows exactly as written.
 */
object Lang {
    private const val PREFS = "look"

    class Option(val code: String, val name: String)

    val options = listOf(Option("en", "English"), Option("ru", "Русский"))

    var code by mutableStateOf("en")
        private set

    val isRussian: Boolean get() = code == "ru"

    /** Used for day and month names. */
    val locale: Locale get() = if (isRussian) Locale("ru") else Locale.ENGLISH

    fun init(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lang", null)
        code = saved ?: if (Locale.getDefault().language == "ru") "ru" else "en"
    }

    fun set(context: Context, newCode: String) {
        code = newCode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("lang", newCode).apply()
        QuickNoteWidget.refresh(context) // the widget's words follow the language
    }
}

/** Translates [en] into the chosen language. {0}, {1}… in the text are replaced by [args]. */
fun tr(en: String, vararg args: Any?): String {
    var s = if (Lang.isRussian) RuStrings.map[en] ?: en else en
    args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
    return s
}

/**
 * The right word for a count: trp(3, "day", "days") → "days" / "дня".
 * Russian has three forms (1 день, 2 дня, 5 дней), kept in [RuStrings.plurals] under the English plural.
 */
fun trp(n: Int, one: String, many: String): String {
    if (!Lang.isRussian) return if (n == 1) one else many
    val forms = RuStrings.plurals[many]?.split("|") ?: return tr(if (n == 1) one else many)
    val n10 = Math.abs(n) % 10
    val n100 = Math.abs(n) % 100
    return when {
        n10 == 1 && n100 != 11 -> forms[0]
        n10 in 2..4 && n100 !in 12..14 -> forms[1]
        else -> forms[2]
    }
}

/** A tracking point's question, translated when it's one of the built-in questions. */
val TrackingPoint.title: String get() = tr(label)

/** A section's title, translated when it's a built-in section. */
val Section.name: String get() = tr(title)

/** A stored answer as readable text in the app's language, e.g. "Positive", "Yes", "7.5 hrs". */
fun TrackingPoint.shown(value: String?): String {
    if (value == null) return ""
    return when (type) {
        PointType.SCALE -> tr(options.getOrNull(value.toIntOrNull() ?: -1) ?: value)
        PointType.YESNO -> tr(if (value == "1") "Yes" else "No")
        PointType.NUMBER -> formatNumber(value.toFloatOrNull() ?: 0f) + if (unit.isNotBlank()) " " + tr(unit) else ""
        PointType.TEXT -> value
    }
}
