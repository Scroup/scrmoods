package com.scrmoods.app

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Full-screen, story-style summary. Tap the right of the screen for the next page, the left to go back. */
@Composable
fun SummaryDialog(pages: List<SummaryPage>, onClose: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val page = pages[index]
        val tint = when (page.tone) {
            SummaryTone.GOOD -> GoodColor.copy(alpha = 0.28f)
            SummaryTone.CARE -> BadColor.copy(alpha = 0.24f)
            SummaryTone.MOOD -> AccentWash
            SummaryTone.NEUTRAL -> Page
        }.compositeOver(Page)
        Box(
            Modifier
                .fillMaxSize()
                .background(tint)
                .pointerInput(pages.size) {
                    detectTapGestures { pos ->
                        if (pos.x < size.width / 3f) { if (index > 0) index-- }
                        else if (index < pages.size - 1) index++ else onClose()
                    }
                },
        ) {
            Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 28.dp)) {
                // progress, like stories
                Row(Modifier.fillMaxWidth().padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    pages.indices.forEach { i ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (i <= index) Ink.copy(alpha = 0.7f) else Ink.copy(alpha = 0.15f)),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("scrmoods summary"), fontSize = 14.sp, fontStyle = FontStyle.Italic, color = Accent, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, tr("Close summary"), tint = InkSoft) }
                }

                Crossfade(
                    targetState = index,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "summary page",
                ) { i ->
                    val p = pages[i]
                    Column(Modifier.fillMaxSize().padding(end = 12.dp), verticalArrangement = Arrangement.Center) {
                        Text(p.kicker, fontSize = 17.sp, fontStyle = FontStyle.Italic, color = Accent)
                        Text(
                            p.headline,
                            fontSize = 32.sp, lineHeight = 39.sp, fontWeight = FontWeight.Medium, color = Ink,
                            modifier = Modifier.padding(top = 10.dp, bottom = 18.dp),
                        )
                        for (line in p.body) {
                            Text(
                                line,
                                fontSize = 17.sp, lineHeight = 25.sp, color = if (line == tr("These are patterns in your answers, not proof of cause.") || line == tr("Small steps count.")) InkSoft else Ink,
                                fontStyle = if (line == tr("These are patterns in your answers, not proof of cause.") || line == tr("Small steps count.")) FontStyle.Italic else FontStyle.Normal,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                    }
                }

                Text(
                    if (index < pages.size - 1) tr("Tap to continue") else tr("Tap to close"),
                    fontSize = 13.sp, fontStyle = FontStyle.Italic, color = InkFaint,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

/** The button at the top of Trends that opens the summary. */
@Composable
fun SummaryButton(periodLabel: String, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Accent)
            .pointerInput(Unit) { detectTapGestures { Haptics.tap(); onClick() } }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tr("Summary"), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = OnAccent)
            Text(tr("How your last {0} went, in words", SummaryLogic.lastPeriod(periodLabel)), fontSize = 13.5.sp, fontStyle = FontStyle.Italic, color = OnAccent.copy(alpha = 0.85f))
        }
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(OnAccent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text("✦", fontSize = 16.sp, color = OnAccent)
        }
    }
}
