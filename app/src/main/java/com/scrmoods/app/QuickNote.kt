package com.scrmoods.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Home-screen widget: one tap opens the quick note window; the drop on the right adds a cup of water
 * straight away, without opening anything. It takes the colours of the app's theme.
 */
class QuickNoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, views(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ADD_WATER) return super.onReceive(context, intent)
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                Repo.init(app)
                Lang.init(app)
                Haptics.init(app)
                val p = waterPoint(Repo.data)
                if (p != null) {
                    val today = LocalDate.now().toString()
                    val n = (Repo.data.entries[today]?.values?.get(p.id)?.toFloatOrNull()?.toInt() ?: 0) + 1
                    Repo.setValue(today, p.id, n.toString())
                    Repo.flush()
                    refresh(app)
                    Haptics.tap()
                    val msg = if (n == p.goalCups) tr("Goal reached. Well done!")
                    else tr("+1 cup · {0} of {1}", n, p.goalCups)
                    Handler(Looper.getMainLooper()).post { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_ADD_WATER = "com.scrmoods.app.ADD_WATER"

        private fun views(context: Context): RemoteViews {
            Themes.init(context)
            Lang.init(context)
            Repo.init(context)
            val pal = Themes.current
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, QuickNoteActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return RemoteViews(context.packageName, R.layout.widget_quick_note).apply {
                setInt(R.id.widget_bg, "setColorFilter", pal.page.toArgb())
                setInt(R.id.widget_dot, "setColorFilter", pal.accent.toArgb())
                setInt(R.id.widget_icon, "setColorFilter", pal.onAccent.toArgb())
                setTextColor(R.id.widget_title, pal.ink.toArgb())
                setTextColor(R.id.widget_sub, pal.inkSoft.toArgb())
                setTextViewText(R.id.widget_title, tr("Quick note"))
                setTextViewText(R.id.widget_sub, tr("How are you right now?"))
                setOnClickPendingIntent(R.id.widget_root, open)

                // the water drop: shown while the water tracker is on
                val water = waterPoint(Repo.data)
                val waterOn = water != null && Repo.data.sections.firstOrNull { it.id == Sections.WATER }?.hidden == false
                setViewVisibility(R.id.widget_water, if (waterOn) View.VISIBLE else View.GONE)
                if (water != null && waterOn) {
                    val drunk = Repo.data.entries[LocalDate.now().toString()]?.values?.get(water.id)?.toFloatOrNull()?.toInt() ?: 0
                    setTextViewText(R.id.widget_water_count, "$drunk/${water.goalCups}")
                    setTextColor(R.id.widget_water_count, pal.accent.toArgb())
                    setInt(R.id.widget_water_dot, "setColorFilter", pal.accentWash.toArgb())
                    setInt(R.id.widget_water_drop, "setColorFilter", pal.accent.toArgb())
                    setInt(R.id.widget_water_plus, "setColorFilter", pal.onAccent.toArgb())
                    setContentDescription(R.id.widget_water, tr("Add a cup of water"))
                    val add = PendingIntent.getBroadcast(
                        context, 1,
                        Intent(context, QuickNoteWidget::class.java).setAction(ACTION_ADD_WATER),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    setOnClickPendingIntent(R.id.widget_water, add)
                }
            }
        }

        /** Redraws placed widgets, e.g. after the theme changes. */
        fun refresh(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(context, QuickNoteWidget::class.java))
                if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context))
            } catch (_: Exception) {
            }
        }
    }
}

/** The small window the widget opens: write a line, and it's added to today's note with the time. */
class QuickNoteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(applicationContext)
        Themes.init(applicationContext)
        Lang.init(applicationContext)
        Haptics.init(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ScrmoodsTheme {
                QuickNoteSheet(onDone = { finish() })
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }
}

@Composable
private fun QuickNoteSheet(onDone: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val time = remember { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }
    // the card slides up into place
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        rise.animateTo(1f, tween(380, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
    }
    LaunchedEffect(Unit) {
        delay(120)
        try { focus.requestFocus() } catch (_: Exception) {}
    }

    fun close() { Repo.flush(); onDone() } // water taps are saved as they happen

    fun save() {
        val t = text.trim()
        if (t.isNotEmpty()) {
            Haptics.tap()
            Repo.addQuickNote(LocalDate.now().toString(), time, t)
            Toast.makeText(context, tr("Added to today's note"), Toast.LENGTH_SHORT).show()
        }
        close()
    }
    val today = LocalDate.now().toString()
    val water = waterPoint(Repo.data)?.takeIf { Repo.data.sections.firstOrNull { it.id == Sections.WATER }?.hidden == false }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = rise.value }
            .background(Color(0xFF2A1C17).copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { close() } } // tap outside to close
            .imePadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .padding(10.dp)
                .fillMaxWidth()
                .graphicsLayer { translationY = (1f - rise.value) * 60.dp.toPx() }
                .clip(RoundedCornerShape(24.dp))
                .background(Page)
                .pointerInput(Unit) { detectTapGestures { } } // taps on the card stay on the card
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
        ) {
          // everything but the buttons scrolls, so the water cups below the note can be reached with the keyboard open
          Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Text(tr("Quick note") + " · $time", fontSize = 14.sp, fontStyle = FontStyle.Italic, color = Accent)
            Text(
                tr("How are you right now?"),
                fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Ink,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                tr("A feeling, a thought, what just happened. It goes into today's note."),
                fontSize = 13.5.sp, lineHeight = 18.sp, fontStyle = FontStyle.Italic, color = InkSoft,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(tr("Right now I feel…"), fontStyle = FontStyle.Italic, color = InkFaint) },
                textStyle = TextStyle(fontFamily = Lora, fontSize = 16.sp, lineHeight = 23.sp, color = Ink),
                colors = journalFieldColors(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).focusRequester(focus),
            )
            if (water != null) {
                Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(1.dp).background(Rule))
                Text(
                    tr("Water today"), fontSize = 14.sp, fontStyle = FontStyle.Italic, color = Accent,
                    modifier = Modifier.padding(top = 12.dp),
                )
                WaterTracker(water, Repo.data.entries[today]?.values?.get(water.id), today, 0.dp)
            }
          }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinkButton(tr("Close")) { close() }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { save() },
                    enabled = text.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent, contentColor = OnAccent,
                        disabledContainerColor = AccentWash, disabledContentColor = InkFaint,
                    ),
                ) { Text(tr("Save to today"), fontSize = 15.sp) }
            }
        }
    }
}
