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

    fun level(v: Int) = levels[v.coerceIn(0, 10)]
}

/** The mood scale card at the top of the journal. */
@Composable
fun MoodScaleCard(
    value: String?,
    mixed: String?,
    gutter: Dp,
    onChange: (String?) -> Unit,
    onMixedChange: (String?) -> Unit,
) {
    val v = value?.toFloatOrNull()?.toInt()?.coerceIn(0, 10)

    Column(Modifier.fillMaxWidth().padding(horizontal = gutter)) {
        Text(
            "Where is your mood today?",
            fontSize = 15.5.sp, lineHeight = 21.sp, color = Ink,
            modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
        )

        // The chosen state, tinted by its zone
        val tone = if (v != null) MoodScale.zoneColor(v) else Rule
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (v != null) tone.copy(alpha = 0.2f) else Paper)
                .border(1.dp, if (v != null) tone.copy(alpha = 0.55f) else Rule, RoundedCornerShape(16.dp))
                .padding(14.dp)
                .heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (v == null) {
                Text(
                    "Tap a number below: 0 is severe depression, 5 balanced, 10 mania.",
                    fontSize = 14.sp, fontStyle = FontStyle.Italic, color = InkSoft, lineHeight = 19.sp,
                )
            } else {
                Text(
                    v.toString(),
                    fontSize = 40.sp, fontWeight = FontWeight.Medium, color = tone,
                    modifier = Modifier.width(52.dp),
                    textAlign = TextAlign.Center,
                )
                Column(Modifier.padding(start = 10.dp)) {
                    Text(MoodScale.level(v).name, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Ink)
                    Text(
                        MoodScale.level(v).description,
                        fontSize = 13.5.sp, lineHeight = 18.sp, fontStyle = FontStyle.Italic, color = InkSoft,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        // 0–10 dots; tap the chosen one again to clear it
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (n in 0..10) {
                val sel = v == n
                val inRange = v != null && ((v < 5 && n in v..5) || (v > 5 && n in 5..v))
                val c = MoodScale.zoneColor(n)
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(
                            when {
                                sel -> c
                                inRange -> c.copy(alpha = 0.25f)
                                else -> Paper
                            },
                        )
                        .border(1.dp, if (sel) c else c.copy(alpha = 0.45f), CircleShape)
                        .clickable { onChange(if (sel) null else n.toString()) },
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
            Text("Depression", fontSize = 12.sp, fontStyle = FontStyle.Italic, color = InkSoft, modifier = Modifier.weight(1f))
            Text("Balanced", fontSize = 12.sp, fontStyle = FontStyle.Italic, color = InkSoft, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("Mania", fontSize = 12.sp, fontStyle = FontStyle.Italic, color = InkSoft, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }

        // Mixed features: depressive and high symptoms at the same time
        val isMixed = mixed == "1"
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isMixed) "✓ Mixed features" else "Mixed features",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                color = if (isMixed) Color.White else Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isMixed) Accent else Paper)
                    .border(1.dp, if (isMixed) Accent else Rule, RoundedCornerShape(50))
                    .clickable { onMixedChange(if (isMixed) null else "1") }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
            Text(
                "low and high at the same time",
                fontSize = 12.5.sp, fontStyle = FontStyle.Italic, color = InkFaint, lineHeight = 16.sp,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
        }

        // A gentle word at the extremes
        val support = when (v) {
            0, 1 -> "If you're thinking about suicide, please reach out now: call a crisis line or emergency services, or tell someone you trust. You don't have to carry this alone."
            9, 10 -> "This is a lot. Consider contacting your doctor or someone you trust today."
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
