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
 * Home-screen widget. Usually: one tap opens the quick note window; the drop adds a cup of water and the bowl adds
 * a meal straight away, without opening anything. In the morning (until noon), while last night's sleep isn't logged,
 * it asks the hours (− / + / ✓) and then how it felt (Poor / OK / Good), then goes back to the quick note.
 * It takes the colours of the app's theme.
 */
class QuickNoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, views(context))
    }

    // The widget's buttons (+1 water, +1 meal, the morning sleep question) go to WidgetActionReceiver, which is not
    // exported: this provider has to be exported for the launcher, so it would otherwise take these actions from any app.
    companion object {
        internal const val PREFIX = "com.scrmoods.app.W_"
        const val ACTION_ADD_WATER = PREFIX + "ADD_WATER"
        const val ACTION_ADD_MEAL = PREFIX + "ADD_MEAL"
        const val ACTION_SLEEP_MINUS = PREFIX + "SLEEP_MINUS"
        const val ACTION_SLEEP_PLUS = PREFIX + "SLEEP_PLUS"
        const val ACTION_SLEEP_SAVE = PREFIX + "SLEEP_SAVE"
        const val ACTION_SLEEP_SKIP = PREFIX + "SLEEP_SKIP"
        const val ACTION_SLEEP_FACE = PREFIX + "SLEEP_FACE_"

        private fun broadcast(context: Context, action: String, code: Int) = PendingIntent.getBroadcast(
            context, code, Intent(context, WidgetActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun views(context: Context): RemoteViews {
            Themes.init(context)
            Lang.init(context)
            Repo.init(context)
            val pal = Themes.current
            val d = Repo.data
            val today = LocalDate.now().toString()
            fun openSheet(mode: String, code: Int) = PendingIntent.getActivity(
                context, code,
                Intent(context, QuickNoteActivity::class.java)
                    .putExtra(QuickNoteActivity.EXTRA_MODE, mode)
                    .setData(android.net.Uri.parse("scrmoods://widget/$mode"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val ask = MorningSleep.askNow(context, d)
            return RemoteViews(context.packageName, R.layout.widget_quick_note).apply {
                setInt(R.id.widget_bg, "setColorFilter", pal.page.toArgb())
                setOnClickPendingIntent(R.id.widget_root, openSheet("note", 0))
                fun show(id: Int, on: Boolean) = setViewVisibility(id, if (on) View.VISIBLE else View.GONE)
                fun roundButton(frame: Int, dot: Int, text: Int, action: String, code: Int, strong: Boolean = false) {
                    setInt(dot, "setColorFilter", (if (strong) pal.accent else pal.accentWash).toArgb())
                    setTextColor(text, (if (strong) pal.onAccent else pal.ink).toArgb())
                    setOnClickPendingIntent(frame, broadcast(context, action, code))
                }

                val morning = ask != MorningSleep.Ask.NONE
                show(R.id.widget_buttons, !morning)
                show(R.id.widget_sleep_row, morning)

                if (morning) {
                    // ===== the morning sleep question =====
                    setInt(R.id.widget_dot, "setColorFilter", pal.accent.toArgb())
                    setInt(R.id.widget_icon, "setColorFilter", pal.onAccent.toArgb())
                    setImageViewResource(R.id.widget_icon, R.drawable.ic_moon)
                    setTextColor(R.id.widget_title, pal.ink.toArgb())
                    setTextColor(R.id.widget_sub, pal.inkSoft.toArgb())
                    show(R.id.widget_sleep_hours, ask == MorningSleep.Ask.HOURS)
                    show(R.id.widget_sleep_faces, ask == MorningSleep.Ask.QUALITY)
                    setTextColor(R.id.widget_sleep_skip, pal.inkSoft.toArgb())
                    setOnClickPendingIntent(R.id.widget_sleep_skip, broadcast(context, ACTION_SLEEP_SKIP, 14))
                    setContentDescription(R.id.widget_sleep_skip, tr("Not now"))
                    if (ask == MorningSleep.Ask.HOURS) {
                        setTextViewText(R.id.widget_title, tr("How long did you sleep?"))
                        setTextViewText(R.id.widget_sub, tr("{0} h", formatNumber(MorningSleep.draft(context, d))))
                        setTextColor(R.id.widget_sub, pal.accent.toArgb())
                        roundButton(R.id.widget_sleep_minus, R.id.widget_sleep_minus_dot, R.id.widget_sleep_minus_text, ACTION_SLEEP_MINUS, 11)
                        roundButton(R.id.widget_sleep_plus, R.id.widget_sleep_plus_dot, R.id.widget_sleep_plus_text, ACTION_SLEEP_PLUS, 12)
                        roundButton(R.id.widget_sleep_save, R.id.widget_sleep_save_dot, R.id.widget_sleep_save_text, ACTION_SLEEP_SAVE, 13, strong = true)
                    } else {
                        setTextViewText(R.id.widget_title, tr("How did it feel?"))
                        setTextViewText(R.id.widget_sub, tr("Last night: {0} h", d.entries[today]?.values?.get(Sleep.HOURS) ?: "?"))
                        val faces = listOf(R.id.widget_face_0, R.id.widget_face_1, R.id.widget_face_2)
                        faces.forEachIndexed { i, id ->
                            setInt(id, "setColorFilter", answerColor(i / 2f).toArgb())
                            setContentDescription(id, tr(Sleep.QUALITY_OPTIONS[i]))
                            setOnClickPendingIntent(id, broadcast(context, ACTION_SLEEP_FACE + i, 20 + i))
                        }
                    }
                    return@apply
                }

                // ===== usual: quick note · to-do · +1 water · +1 meal, with a label or count under each =====
                fun button(dot: Int, icon: Int, label: Int, text: String, strong: Boolean = false) {
                    setInt(dot, "setColorFilter", (if (strong) pal.accent else pal.accentWash).toArgb())
                    setInt(icon, "setColorFilter", (if (strong) pal.onAccent else pal.accent).toArgb())
                    setTextViewText(label, text)
                    setTextColor(label, (if (strong) pal.accent else pal.inkSoft).toArgb())
                }
                fun count(id: String) = d.entries[today]?.values?.get(id)?.toFloatOrNull()?.toInt() ?: 0

                button(R.id.widget_note_dot, R.id.widget_note_icon, R.id.widget_note_label, tr("Note"), strong = true)
                setContentDescription(R.id.widget_btn_note, tr("Quick note"))
                setOnClickPendingIntent(R.id.widget_btn_note, openSheet("note", 0))

                val open = Todos.openCount(d.todos)
                button(R.id.widget_todo_dot, R.id.widget_todo_icon, R.id.widget_todo_count, if (open == 0) tr("To-do") else tr("{0} to do", open))
                setContentDescription(R.id.widget_btn_todo, tr("To-do"))
                setOnClickPendingIntent(R.id.widget_btn_todo, openSheet("todo", 3))

                val water = waterPoint(d)?.takeIf { d.sections.firstOrNull { it.id == Sections.WATER }?.hidden == false }
                show(R.id.widget_water, water != null)
                if (water != null) {
                    button(R.id.widget_water_dot, R.id.widget_water_drop, R.id.widget_water_count, "${count(water.id)}/${water.goalCups}")
                    setInt(R.id.widget_water_plus, "setColorFilter", pal.onAccent.toArgb())
                    setContentDescription(R.id.widget_water, tr("Add a cup of water"))
                    setOnClickPendingIntent(R.id.widget_water, broadcast(context, ACTION_ADD_WATER, 1))
                }

                val meals = mealsPoint(d)?.takeIf { d.sections.firstOrNull { it.id == Sections.MEALS }?.hidden == false }
                show(R.id.widget_meal, meals != null)
                if (meals != null) {
                    button(R.id.widget_meal_dot, R.id.widget_meal_bowl, R.id.widget_meal_count, "${count(meals.id)}/${meals.goalMeals}")
                    setInt(R.id.widget_meal_plus, "setColorFilter", pal.onAccent.toArgb())
                    setContentDescription(R.id.widget_meal, tr("Add a meal"))
                    setOnClickPendingIntent(R.id.widget_meal, broadcast(context, ACTION_ADD_MEAL, 2))
                }
            }
        }

        /** Redraws placed widgets, e.g. after the theme changes or something it shows was logged. */
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

/**
 * The small window the widget opens: a quick note (a line added to today's note with the time),
 * or the to-do list.
 */
class QuickNoteActivity : ComponentActivity() {
    companion object {
        const val EXTRA_MODE = "mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(applicationContext)
        Themes.init(applicationContext)
        Lang.init(applicationContext)
        Haptics.init(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val todo = intent?.getStringExtra(EXTRA_MODE) == "todo"
        setContent {
            ScrmoodsTheme {
                if (todo) TodoSheet(onDone = { finish() }) else QuickNoteSheet(onDone = { finish() })
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }
}

/** The to-do list on its own, from the widget: add tasks, tick them off, close. */
@Composable
private fun TodoSheet(onDone: () -> Unit) {
    val focus = remember { FocusRequester() }
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(380, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) }
    LaunchedEffect(Unit) { delay(120); try { focus.requestFocus() } catch (_: Exception) {} }
    fun close() { Repo.flush(); onDone() }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = rise.value }
            .background(Color(0xFF2A1C17).copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { close() } }
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
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
        ) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(tr("To-do"), fontSize = 14.sp, fontStyle = FontStyle.Italic, color = Accent)
                Text(tr("What needs doing?"), fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.padding(top = 2.dp))
                TodoList(0.dp, focus)
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                LinkButton(tr("Close")) { close() }
            }
        }
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

    fun close() { Repo.flush(); onDone() } // water, meal and pill taps are saved as they happen

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
    val data = Repo.data
    fun on(section: String) = data.sections.firstOrNull { it.id == section }?.hidden == false
    val values = data.entries[today]?.values ?: emptyMap()
    val water = waterPoint(data)?.takeIf { on(Sections.WATER) }
    val meals = mealsPoint(data)?.takeIf { on(Sections.MEALS) }
    val meds = if (on(Sections.MEDS)) data.points.filter { it.isPillMed && !it.archived } else emptyList()

    /** A thin line and a small heading between the parts of the sheet. */
    @Composable
    fun Part(title: String) {
        Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(1.dp).background(Rule))
        Text(title, fontSize = 14.sp, fontStyle = FontStyle.Italic, color = Accent, modifier = Modifier.padding(top = 12.dp))
    }

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
          // everything but the buttons scrolls, so water, meals and medications below the note can be reached with the keyboard open
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
                Part(tr("Water today"))
                WaterTracker(water, values[water.id], today, 0.dp)
            }
            if (meals != null) {
                Part(tr("Meals today"))
                MealsTracker(meals, snacksPoint(data), values, today, 0.dp)
            }
            if (data.plan.enabled) {
                // the planner: today's doses by time of day
                if (Plan.intakes(data.plan, today).isNotEmpty()) {
                    Part(tr("Medications today"))
                    PlanTodayList()
                }
            } else if (meds.isNotEmpty()) {
                Part(tr("Medications today"))
                for (p in meds) MedRow(p, values[p.id], 6.dp) { Repo.setValue(today, p.id, it) }
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

/**
 * Handles taps on the widget's buttons. Not exported (see AndroidManifest.xml), so only the widget's own
 * PendingIntents can reach it: another app can't add cups or log sleep.
 */
class WidgetActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (!action.startsWith(QuickNoteWidget.PREFIX)) return
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                Repo.init(app)
                Lang.init(app)
                Haptics.init(app)
                Haptics.tap()
                val today = LocalDate.now().toString()
                fun toast(msg: String) = Handler(Looper.getMainLooper()).post { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() }
                when (action) {
                    QuickNoteWidget.ACTION_ADD_WATER -> waterPoint(Repo.data)?.let { p ->
                        val n = Repo.increment(today, p.id) // atomic: two quick taps count as two cups
                        toast(if (n == p.goalCups) tr("Goal reached. Well done!") else tr("+1 cup · {0} of {1}", n, p.goalCups))
                    }
                    QuickNoteWidget.ACTION_ADD_MEAL -> mealsPoint(Repo.data)?.let { p ->
                        val n = Repo.increment(today, p.id)
                        toast(if (n == p.goalMeals) tr("All your meals are in. Nicely done!") else tr("+1 meal · {0} of {1}", n, p.goalMeals))
                    }
                    QuickNoteWidget.ACTION_SLEEP_MINUS, QuickNoteWidget.ACTION_SLEEP_PLUS ->
                        MorningSleep.setDraft(app, MorningSleep.draft(app, Repo.data) + if (action == QuickNoteWidget.ACTION_SLEEP_PLUS) 0.5f else -0.5f)
                    QuickNoteWidget.ACTION_SLEEP_SAVE -> MorningSleep.saveHours(MorningSleep.draft(app, Repo.data))
                    QuickNoteWidget.ACTION_SLEEP_SKIP -> MorningSleep.skip(app)
                    else -> if (action.startsWith(QuickNoteWidget.ACTION_SLEEP_FACE)) {
                        MorningSleep.saveQuality(action.removePrefix(QuickNoteWidget.ACTION_SLEEP_FACE).toIntOrNull() ?: 1)
                        toast(tr("Thanks! Have a good day."))
                    }
                }
                Repo.flush()
                QuickNoteWidget.refresh(app)
            } catch (e: Exception) {
                // a bad saved value must not crash the app from the home screen
                android.util.Log.e("scrmoods", "Widget action $action failed", e)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
