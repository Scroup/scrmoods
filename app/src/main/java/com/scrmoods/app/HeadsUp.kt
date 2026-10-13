package com.scrmoods.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/**
 * "A gentle heads-up": the first week-long alert from [Insights.alerts] that you haven't put away.
 * "Got it" hides that alert for a week; if it's still true after that, it comes back.
 */
object HeadsUp {
    private const val DAYS_HIDDEN = 7

    private fun prefs(context: Context) = context.getSharedPreferences("look", Context.MODE_PRIVATE)

    fun current(context: Context, d: AppData, today: LocalDate = LocalDate.now()): Insights.Alert? {
        val p = prefs(context)
        return Insights.alerts(d, today).firstOrNull { a ->
            val put = p.getLong("alert_" + a.id, Long.MIN_VALUE)
            put == Long.MIN_VALUE || today.toEpochDay() - put >= DAYS_HIDDEN
        }
    }

    fun dismiss(context: Context, a: Insights.Alert, today: LocalDate = LocalDate.now()) =
        prefs(context).edit().putLong("alert_" + a.id, today.toEpochDay()).apply()
}

/** The heads-up card, shown on today's journal page and in Trends. [onMore] adds a link (e.g. to the summary). */
@Composable
fun HeadsUpCard(modifier: Modifier = Modifier, moreLabel: String? = null, onMore: () -> Unit = {}) {
    val context = LocalContext.current
    var bump by remember { mutableIntStateOf(0) } // recompute after "Got it"
    val alert = remember(Repo.data, bump, Lang.code) { HeadsUp.current(context, Repo.data) } ?: return
    val shape = RoundedCornerShape(18.dp)
    val tint = (if (alert.urgent) BadColor.copy(alpha = 0.22f) else AccentWash).compositeOver(Page)
    Column(
        modifier
            .padding(start = 10.dp, end = 10.dp, top = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(tint)
            .border(1.dp, Rule, shape)
            .padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 4.dp),
    ) {
        Text(tr("A gentle heads-up"), fontSize = 13.sp, fontStyle = FontStyle.Italic, color = Accent)
        Text(alert.title, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.padding(top = 2.dp, end = 8.dp))
        Text(alert.body, fontSize = 15.sp, lineHeight = 21.sp, color = Ink, modifier = Modifier.padding(top = 6.dp, end = 8.dp))
        Text(alert.advice, fontSize = 14.sp, lineHeight = 20.sp, fontStyle = FontStyle.Italic, color = InkSoft, modifier = Modifier.padding(top = 6.dp, end = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (moreLabel != null) LinkButton(moreLabel) { onMore() }
            LinkButton(tr("Got it")) { Haptics.tap(); HeadsUp.dismiss(context, alert); bump++ }
        }
    }
}
