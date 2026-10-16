package com.scrmoods.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Last night's sleep, asked in the morning (until noon) in three places: the home-screen widget,
 * an optional notification you can reply to, and a card on today's journal page.
 * First the hours, then how it felt (Poor / OK / Good). Answering in one place answers everywhere.
 */
object MorningSleep {
    private const val PREFS = "morning_sleep"
    fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    enum class Ask { NONE, HOURS, QUALITY }

    fun isMorning(t: LocalTime = LocalTime.now()) = t.hour in Sleep.MORNING_START until Sleep.MORNING_END

    private fun visible(d: AppData, id: String) = d.points.firstOrNull { p ->
        p.id == id && !p.archived && d.sections.firstOrNull { it.id == p.sectionId }?.hidden != true
    }

    fun hoursPoint(d: AppData) = visible(d, Sleep.HOURS)
    fun qualityPoint(d: AppData) = visible(d, Sleep.QUALITY)?.takeIf { it.type == PointType.SCALE }

    /** What's still missing about last night (stored on today's page). */
    fun ask(d: AppData, today: LocalDate = LocalDate.now()): Ask {
        val v = d.entries[today.toString()]?.values ?: emptyMap()
        return when {
            hoursPoint(d) != null && v[Sleep.HOURS] == null -> Ask.HOURS
            qualityPoint(d) != null && v[Sleep.QUALITY] == null -> Ask.QUALITY
            else -> Ask.NONE
        }
    }

    /** What to ask right now: only in the morning, and not after "Not now" today. */
    fun askNow(c: Context, d: AppData): Ask = if (!isMorning() || skippedToday(c)) Ask.NONE else ask(d)

    fun skippedToday(c: Context) = prefs(c).getString("skipped", null) == LocalDate.now().toString()
    fun skip(c: Context) = prefs(c).edit().putString("skipped", LocalDate.now().toString()).apply()

    /** Where the hours start: your most recent night in the last two weeks, else 7.5. */
    fun usualHours(d: AppData, today: LocalDate = LocalDate.now()): Float =
        (1..14).firstNotNullOfOrNull { d.entries[today.minusDays(it.toLong()).toString()]?.values?.get(Sleep.HOURS)?.toFloatOrNull() } ?: 7.5f

    /** The hours being picked in the widget (kept between taps). */
    fun draft(c: Context, d: AppData): Float {
        val p = prefs(c)
        return if (p.getString("draft_day", null) == LocalDate.now().toString()) p.getFloat("draft", 7.5f) else usualHours(d)
    }

    fun setDraft(c: Context, h: Float) =
        prefs(c).edit().putString("draft_day", LocalDate.now().toString()).putFloat("draft", clamp(h)).apply()

    /** To the nearest half hour, 0–16. */
    fun clamp(h: Float) = (Math.round(h * 2f) / 2f).coerceIn(0f, 16f)

    /** Reads typed hours: "7.5", "7,5", "7:30", "7h30", "7 h", "7ч30". */
    fun parseHours(text: String): Float? {
        val s = text.trim().lowercase().replace(',', '.')
        Regex("^(\\d{1,2})\\s*[:hч]\\s*(\\d{1,2})\\s*(m|min|мин|м)?$").find(s)?.let {
            val h = it.groupValues[1].toInt(); val m = it.groupValues[2].toInt()
            return if (h <= 16 && m < 60) clamp(h + m / 60f) else null
        }
        Regex("^(\\d{1,2}(?:\\.\\d+)?)\\s*(h|hr|hrs|hours|ч|час|часа|часов)?$").find(s)?.let {
            val h = it.groupValues[1].toFloatOrNull() ?: return null
            return if (h in 0f..16f) clamp(h) else null
        }
        return null
    }

    fun saveHours(h: Float) = Repo.setValue(LocalDate.now().toString(), Sleep.HOURS, formatNumber(clamp(h)))
    fun saveQuality(i: Int) = Repo.setValue(LocalDate.now().toString(), Sleep.QUALITY, i.coerceIn(0, 2).toString())

    val faces = listOf(R.drawable.ic_face_poor, R.drawable.ic_face_ok, R.drawable.ic_face_good)
}

/** The morning card at the top of today's page: hours, then Poor / OK / Good. Gone once both are in, or at noon. */
@Composable
fun MorningSleepCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now by produceState(LocalTime.now()) { while (true) { delay(60_000); value = LocalTime.now() } }
    var bump by remember { mutableIntStateOf(0) }
    val d = Repo.data
    val ask = remember(d, bump, now.hour) { if (MorningSleep.isMorning(now)) MorningSleep.askNow(context, d) else MorningSleep.Ask.NONE }
    if (ask == MorningSleep.Ask.NONE) return
    var hours by remember { mutableFloatStateOf(MorningSleep.draft(context, d)) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .padding(start = 10.dp, end = 10.dp, top = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(AccentWash)
            .border(1.dp, Rule, shape)
            .padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 4.dp),
    ) {
        Text(tr("Good morning"), fontSize = 13.sp, fontStyle = FontStyle.Italic, color = Accent)
        if (ask == MorningSleep.Ask.HOURS) {
            Text(tr("How long did you sleep?"), fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(enabled = hours > 0f, onClick = { Haptics.tap(); hours = MorningSleep.clamp(hours - 0.5f) }) { Text("−", fontSize = 22.sp, color = Ink) }
                Text(
                    tr("{0} h", formatNumber(hours)), fontSize = 30.sp, fontWeight = FontWeight.Medium, color = Accent,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                RoundButton(enabled = hours < 16f, onClick = { Haptics.tap(); hours = MorningSleep.clamp(hours + 0.5f) }) { Text("+", fontSize = 22.sp, color = Ink) }
            }
            Button(
                onClick = { Haptics.tap(); MorningSleep.saveHours(hours); bump++ },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, end = 8.dp),
            ) { Text(tr("Save")) }
        } else {
            Text(tr("How did it feel?"), fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, end = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Sleep.QUALITY_OPTIONS.forEachIndexed { i, label -> FaceButton(i, tr(label)) { MorningSleep.saveQuality(i); bump++ } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            LinkButton(tr("Not now")) { MorningSleep.skip(context); QuickNoteWidget.refresh(context); bump++ }
        }
    }
}

/** A face (Poor / OK / Good) in red, amber or green, with its word under it. */
@Composable
fun FaceButton(i: Int, label: String, size: androidx.compose.ui.unit.Dp = 44.dp, onClick: () -> Unit) {
    val pop = rememberPop()
    val color = answerColor(i / 2f)
    Column(
        Modifier
            .then(pop.modifier)
            .clip(RoundedCornerShape(16.dp))
            .clickable { Haptics.tap(); pop.fire(stars = true, color = color); onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(MorningSleep.faces[i]), null, tint = color, modifier = Modifier.size(size))
        Text(label, fontSize = 14.sp, color = Ink, modifier = Modifier.padding(top = 4.dp))
    }
}

/** The optional morning notification: reply with the hours, then tap Poor / OK / Good. */
object SleepNotifier {
    private const val CHANNEL_ID = "sleep_morning"
    const val ACTION_FIRE = "com.scrmoods.app.SLEEP_FIRE"
    const val ACTION_REPLY = "com.scrmoods.app.SLEEP_REPLY"
    const val ACTION_FACE = "com.scrmoods.app.SLEEP_FACE"
    const val KEY_HOURS = "hours"
    const val EXTRA_FACE = "face"
    private const val NOTE_ID = 4242

    fun enabled(c: Context) = MorningSleep.prefs(c).getBoolean("notify", false)
    fun time(c: Context): String = MorningSleep.prefs(c).getString("notify_time", null)?.takeIf(Reminders::isValidTime) ?: "09:00"

    fun setEnabled(c: Context, on: Boolean) { MorningSleep.prefs(c).edit().putBoolean("notify", on).apply(); schedule(c) }
    fun setTime(c: Context, t: String) { MorningSleep.prefs(c).edit().putString("notify_time", t).apply(); schedule(c) }

    private fun receiver(c: Context, action: String) = Intent(c, SleepReceiver::class.java).setAction(action)

    private fun fireIntent(c: Context) =
        PendingIntent.getBroadcast(c, 7001, receiver(c, ACTION_FIRE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Sets (or clears) tomorrow's — or today's, if still ahead — alarm. */
    fun schedule(context: Context) {
        val c = context.applicationContext
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(fireIntent(c))
        if (!enabled(c)) return
        val at = Reminders.nextOccurrence(time(c)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        Reminders.setAlarm(c, at, fireIntent(c))
    }

    private fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, tr("Morning sleep question"), NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = tr("Asks in the morning how long you slept and how it felt")
        nm.createNotificationChannel(ch)
    }

    private fun base(c: Context, title: String, text: String): NotificationCompat.Builder {
        ensureChannel(c)
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(c, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFA0594F.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        // it goes away by itself at noon
        val noon = LocalTime.of(Sleep.MORNING_END, 0)
        val left = java.time.Duration.between(LocalTime.now(), noon).toMillis()
        if (left > 0) b.setTimeoutAfter(left)
        return b
    }

    private fun post(c: Context, b: NotificationCompat.Builder) {
        if (!Reminders.canNotify(c)) return
        try { NotificationManagerCompat.from(c).notify(NOTE_ID, b.build()) } catch (_: SecurityException) {}
    }

    fun showHours(c: Context, retry: Boolean = false) {
        val input = RemoteInput.Builder(KEY_HOURS).setLabel(tr("Hours, e.g. 7.5")).build()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val reply = PendingIntent.getBroadcast(c, 7002, receiver(c, ACTION_REPLY), flags)
        val action = NotificationCompat.Action.Builder(0, tr("Enter hours"), reply).addRemoteInput(input).build()
        post(
            c,
            base(
                c, tr("Good morning! How long did you sleep?"),
                if (retry) tr("Couldn't read that. Try something like 7.5 or 7:30.") else tr("Reply with the hours, e.g. 7.5 or 7:30."),
            ).addAction(action),
        )
    }

    fun showQuality(c: Context) {
        val b = base(c, tr("How did it feel?"), tr("Last night's sleep: {0} h", Repo.data.entries[LocalDate.now().toString()]?.values?.get(Sleep.HOURS) ?: "?"))
        Sleep.QUALITY_OPTIONS.forEachIndexed { i, label ->
            val pi = PendingIntent.getBroadcast(
                c, 7010 + i, receiver(c, ACTION_FACE).putExtra(EXTRA_FACE, i),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(0, tr(label), pi)
        }
        post(c, b)
    }

    fun cancel(c: Context) = NotificationManagerCompat.from(c).cancel(NOTE_ID)
}

class SleepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                Repo.init(app)
                Lang.init(app)
                when (intent.action) {
                    SleepNotifier.ACTION_FIRE -> {
                        SleepNotifier.schedule(app) // tomorrow's
                        when (MorningSleep.ask(Repo.data)) {
                            MorningSleep.Ask.HOURS -> SleepNotifier.showHours(app)
                            MorningSleep.Ask.QUALITY -> SleepNotifier.showQuality(app)
                            MorningSleep.Ask.NONE -> {}
                        }
                    }
                    SleepNotifier.ACTION_REPLY -> {
                        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(SleepNotifier.KEY_HOURS)?.toString().orEmpty()
                        val h = MorningSleep.parseHours(text)
                        if (h == null) SleepNotifier.showHours(app, retry = true)
                        else {
                            MorningSleep.saveHours(h)
                            Repo.flush()
                            if (MorningSleep.ask(Repo.data) == MorningSleep.Ask.QUALITY) SleepNotifier.showQuality(app) else SleepNotifier.cancel(app)
                        }
                    }
                    SleepNotifier.ACTION_FACE -> {
                        MorningSleep.saveQuality(intent.getIntExtra(SleepNotifier.EXTRA_FACE, 1))
                        Repo.flush()
                        SleepNotifier.cancel(app)
                    }
                }
            } catch (e: Exception) {
                // never let one bad value crash the app from a notification or alarm
                android.util.Log.e("scrmoods", "Receiver failed: ${intent.action}", e)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** Settings → Sleep: the morning notification and its time. */
@Composable
fun MorningSleepSettings(settingSwitch: @Composable (String, String, Boolean, (Boolean) -> Unit) -> Unit) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(SleepNotifier.enabled(context)) }
    var time by remember { mutableStateOf(SleepNotifier.time(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    settingSwitch(
        tr("Morning sleep question"),
        tr("A notification at {0} asking how long you slept: reply with the hours, then tap Poor, OK or Good. The widget and today's page ask too, until noon.", time),
        on,
    ) {
        on = it
        SleepNotifier.setEnabled(context, it)
        if (it && Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (on) Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LinkButton(tr("Change time ({0})", time)) {
            val (h, m) = time.split(":").map { it.toInt() }
            TimePickerDialog(context, { _, hh, mm ->
                time = String.format(java.util.Locale.US, "%02d:%02d", hh, mm)
                SleepNotifier.setTime(context, time)
            }, h, m, DateFormat.is24HourFormat(context)).show()
        }
        Spacer(Modifier.width(4.dp))
        LinkButton(tr("Try it now")) {
            when (MorningSleep.ask(Repo.data)) {
                MorningSleep.Ask.QUALITY -> SleepNotifier.showQuality(context)
                else -> SleepNotifier.showHours(context)
            }
        }
    }
}
