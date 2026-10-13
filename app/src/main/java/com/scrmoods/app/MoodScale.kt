package com.scrmoods.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The 0–10 mood scale: 0 severe depression, 5 balanced, 10 mania. */
object MoodScale {
    data class Level(val name: String, val description: String)

    val levels = listOf(
        Level("Severe depression", "Constant thoughts of suicide, feeling there is no way out, barely able to move. Everything feels bleak and permanent."),
        Level("Severe depression", "Hopelessness and guilt, thoughts of suicide, very little movement. Doing anything feels impossible."),
        Level("Mild to moderate depression", "Slowed thinking, little appetite, wanting to be alone. Sleeping too much or too little; everything is a struggle."),
        Level("Mild to moderate depression", "Anxious or panicky, hard to concentrate, poor memory. Routine gives some comfort."),
        Level("Balanced", "Pulling back a little from people, concentration slightly below usual, a bit restless."),
        Level("Balanced", "Mood in balance, no signs of depression or mania."),
        Level("Balanced", "Good self-esteem, optimistic and sociable, clear decisions, getting things done on a little less sleep."),
        Level("Hypomania", "Very productive, doing everything to excess (calls, writing, smoking, caffeine), charming and talkative."),
        Level("Hypomania", "Inflated self-esteem, racing thoughts and fast speech, juggling many tasks without finishing them."),
        Level("Mania", "Losing touch with reality, hard to follow, no sleep, suspicious or vindictive, reckless behaviour."),
        Level("Mania", "Complete loss of judgement, extreme spending, grandiose or religious beliefs, hallucinations."),
    )

    /** Soft zone colours: deep rose → clay → sage → honey → coral. */
    fun zoneColor(v: Int): Color = when (v) {
        0, 1 -> Color(0xFF94504A)
        2, 3 -> Color(0xFFCF8A5A)
        4, 5, 6 -> Color(0xFF8DA47A)
        7, 8 -> Color(0xFFD5AE58)
        else -> Color(0xFFC6645A)
    }

    /** The level for a number, in the app's language. */
    fun level(v: Int) = levels[v.coerceIn(0, 10)].let { Level(tr(it.name), tr(it.description)) }
}

/** The mood scale card at the top of the journal. On mixed days two numbers can be picked. */
@Composable
fun MoodScaleCard(
    value: String?,
    value2: String?,
    mixed: String?,
    gutter: Dp,
    onChange: (Int?, Int?) -> Unit,
    onMixedChange: (String?) -> Unit,
) {
    val isMixed = mixed == "1"
    val a = value?.toFloatOrNull()?.toInt()?.coerceIn(0, 10)
    val b = if (isMixed) value2?.toFloatOrNull()?.toInt()?.coerceIn(0, 10) else null

    fun tap(n: Int) {
        if (!isMixed) { onChange(if (a == n) null else n, null); return }
        when {
            n == a -> onChange(b, null)   // remove the first; the second becomes the main one
            n == b -> onChange(a, null)
            a == null -> onChange(n, null)
            else -> onChange(a, n)        // set (or replace) the second number
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = gutter)) {
        Text(
            if (isMixed) tr("Pick two numbers: how low and how high you feel") else tr("Where is your mood today?"),
            fontSize = 15.5.sp, lineHeight = 21.sp, color = Ink,
            modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
        )

        // The chosen state(s): always the same height, centred like a page
        val shape = RoundedCornerShape(16.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(PanelHeight)
                .clip(shape)
                .background(Paper)
                .border(1.dp, Rule, shape),
        ) {
            val shown = listOfNotNull(a, b).sorted()
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    Text(
                        tr("Tap a number below: 0 is severe depression, 5 balanced, 10 mania."),
                        fontSize = 14.sp, fontStyle = FontStyle.Italic, color = InkSoft, lineHeight = 19.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                shown.forEachIndexed { i, v ->
                    StatePanel(v, compact = shown.size == 2, modifier = Modifier.weight(1f).fillMaxHeight())
                    if (i == 0 && shown.size == 2) Box(Modifier.width(1.dp).fillMaxHeight().background(Rule))
                }
            }
        }

        // 0–10 dots; tap a chosen one again to clear it
        val lo = listOfNotNull(a, b).minOrNull()
        val hi = listOfNotNull(a, b).maxOrNull()
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (n in 0..10) {
                val sel = n == a || n == b
                val inRange = when {
                    lo == null || hi == null -> false
                    b != null -> n in lo..hi
                    else -> (lo < 5 && n in lo..5) || (lo > 5 && n in 5..lo)
                }
                val c = MoodScale.zoneColor(n)
                val pop = rememberPop()
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .then(pop.modifier)
                        .clip(CircleShape)
                        .background(
                            when {
                                sel -> c
                                inRange -> c.copy(alpha = 0.25f)
                                else -> Paper
                            },
                        )
                        .border(1.dp, if (sel) c else c.copy(alpha = 0.45f), CircleShape)
                        .clickable { Haptics.tap(); pop.fire(stars = !sel, color = c); tap(n) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        n.toString(),
                        fontSize = 12.5.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (sel) Color.White else Ink,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(tr("Depression"), fontSize = 12.sp, fontStyle = FontStyle.Italic, color = InkSoft, modifier = Modifier.weight(1f))
            Text(tr("Balanced"), fontSize = 12.sp, fontStyle = FontStyle.Italic, color = InkSoft, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text(tr("Mania"), fontSize = 12.sp, fontStyle = FontStyle.Italic, color = InkSoft, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }

        // Mixed features: depressive and high symptoms at the same time
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            val mixedPop = rememberPop()
            Text(
                if (isMixed) "✓ " + tr("Mixed features") else tr("Mixed features"),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                color = if (isMixed) OnAccent else Accent,
                modifier = Modifier
                    .then(mixedPop.modifier)
                    .clip(RoundedCornerShape(50))
                    .background(if (isMixed) Accent else Paper)
                    .border(1.dp, if (isMixed) Accent else Rule, RoundedCornerShape(50))
                    .clickable {
                        Haptics.tap()
                        mixedPop.fire(stars = !isMixed, color = Accent)
                        if (isMixed) { onMixedChange(null); onChange(a, null) } else onMixedChange("1")
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
            Text(
                if (isMixed) tr("pick a low and a high number") else tr("low and high at the same time"),
                fontSize = 12.5.sp, fontStyle = FontStyle.Italic, color = InkFaint, lineHeight = 16.sp,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
        }

        // A gentle word at the extremes
        val values = listOfNotNull(a, b)
        val support = when {
            values.any { it <= 1 } -> tr("If you're thinking about suicide, please reach out now: call a crisis line or emergency services, or tell someone you trust. You don't have to carry this alone.")
            values.any { it >= 9 } -> tr("This is a lot. Consider contacting your doctor or someone you trust today.")
            else -> null
        }
        if (support != null) {
            Text(
                support,
                fontSize = 13.5.sp, lineHeight = 19.sp, color = Ink,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentWash)
                    .padding(12.dp),
            )
        }
    }
}

/** Fixed height, so the card doesn't jump as different descriptions are shown. */
private val PanelHeight = 196.dp

/** Number, state name and description, centred, tinted by the zone. */
@Composable
private fun StatePanel(v: Int, compact: Boolean, modifier: Modifier) {
    val tone = MoodScale.zoneColor(v)
    val level = MoodScale.level(v)
    Column(
        modifier
            .background(tone.copy(alpha = 0.2f))
            .padding(horizontal = if (compact) 10.dp else 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(v.toString(), fontSize = if (compact) 34.sp else 42.sp, lineHeight = if (compact) 38.sp else 46.sp, fontWeight = FontWeight.Medium, color = tone)
        Text(
            level.name,
            fontSize = if (compact) 14.sp else 18.sp, lineHeight = if (compact) 18.sp else 23.sp,
            fontWeight = FontWeight.Medium, color = Ink, textAlign = TextAlign.Center, maxLines = 2,
        )
        Text(
            level.description,
            fontSize = if (compact) 12.sp else 13.5.sp, lineHeight = if (compact) 16.sp else 18.sp,
            fontStyle = FontStyle.Italic, color = InkSoft, textAlign = TextAlign.Center,
            maxLines = if (compact) 5 else 4, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
