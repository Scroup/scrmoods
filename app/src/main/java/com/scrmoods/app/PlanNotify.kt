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
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate
import java.time.ZoneId

/** Asks the app to open the planner (e.g. from a notification). */
object PlannerNav {
    var request by androidx.compose.runtime.mutableIntStateOf(0)
    const val EXTRA = "open_planner"
}

/**
 * The planner's reminders: one notification per time of day listing what to take, with "Took them all" and
 * "Snooze 15 min"; once more after 30 minutes if nothing was ticked; and a quiet note the evening before a dose
 * changes or on a course's last day.
 */
object PlanNotifier {
    private const val CHANNEL_ID = "med_plan"
    const val ACTION_FIRE = "com.scrmoods.app.PLAN_FIRE"
    const val ACTION_AGAIN = "com.scrmoods.app.PLAN_AGAIN"
    const val ACTION_ALL = "com.scrmoods.app.PLAN_ALL"
    const val ACTION_SNOOZE = "com.scrmoods.app.PLAN_SNOOZE"
    const val EXTRA_SLOT = "slot"
    private const val SNOOZE_MIN = 15L
    private const val AGAIN_MIN = 30L

    private fun intent(c: Context, action: String, slot: Slot) = Intent(c, PlanReceiver::class.java)
        .setAction(action)
        .setData(Uri.parse("scrmoods://plan/$action/${slot.code}"))
        .putExtra(EXTRA_SLOT, slot.code)

    private fun pending(c: Context, action: String, slot: Slot, code: Int) =
        PendingIntent.getBroadcast(c, code + slot.ordinal, intent(c, action, slot), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun noteId(slot: Slot) = 4300 + slot.ordinal
    private const val INFO_ID = 4310

    /** Sets the next alarm for each time of day (or clears them when the planner or its reminders are off). */
    fun schedule(context: Context) {
        val c = context.applicationContext
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val plan = Repo.data.plan
        for (s in Slot.entries) {
            am.cancel(pending(c, ACTION_FIRE, s, 7200))
            if (!plan.enabled || plan.meds.none { it.remind && !it.archived }) continue
            val at = Reminders.nextOccurrence(plan.time(s)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            Reminders.setAlarm(c, at, pending(c, ACTION_FIRE, s, 7200))
        }
    }

    fun later(c: Context, slot: Slot, minutes: Long) =
        Reminders.setAlarm(c, System.currentTimeMillis() + minutes * 60_000, pending(c, ACTION_AGAIN, slot, 7210))

    private fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, tr("Medication planner"), NotificationManager.IMPORTANCE_HIGH)
        ch.description = tr("What to take in the morning, afternoon and evening")
        nm.createNotificationChannel(ch)
    }

    private fun openIntent(c: Context) = PendingIntent.getActivity(
        c, 7230,
        Intent(c, MainActivity::class.java).putExtra(PlannerNav.EXTRA, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** What's still to take at this time of day today (only medications with reminders on). */
    fun due(slot: Slot): List<Intake> {
        val d = Repo.data
        val today = LocalDate.now().toString()
        return Plan.intakes(d.plan, today).filter { it.slot == slot && it.med.remind && !Plan.taken(d, today, it) }
    }

    /** Shows (or clears) the notification for [slot]; [again] = the 30-minute repeat or a snooze. */
    fun show(c: Context, slot: Slot, again: Boolean) {
        val d = Repo.data
        if (!d.plan.enabled || !Reminders.canNotify(c)) return
        ensureChannel(c)
        val nm = NotificationManagerCompat.from(c)
        val due = due(slot)
        if (due.isNotEmpty()) {
            val lines = due.joinToString("\n") { it.med.name + " — " + Plan.dose(it.stage, it.med) }
            val n = NotificationCompat.Builder(c, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFFA0594F.toInt())
                .setContentTitle(tr("{0} · time for your medications", slotName(slot)))
                .setContentText(lines.replace("\n", ", "))
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(openIntent(c))
                .setAutoCancel(true)
                .addAction(0, tr("Took them all"), pending(c, ACTION_ALL, slot, 7220))
                .addAction(0, tr("Snooze {0} min", SNOOZE_MIN), pending(c, ACTION_SNOOZE, slot, 7240))
                .build()
            try { nm.notify(noteId(slot), n) } catch (_: SecurityException) {}
            if (!again && d.plan.repeat) later(c, slot, AGAIN_MIN)
        } else nm.cancel(noteId(slot))

        // the evening before a dose changes, and on a course's last day: a quiet note
        if (slot == Slot.EVENING && !again) {
            val today = LocalDate.now().toString()
            val info = d.plan.meds.filter { !it.archived }.mapNotNull { m ->
                Plan.changeTomorrow(m, today)?.let { st ->
                    tr("From tomorrow: {0} — {1}, {2}", m.name, Plan.dose(st, m), Slot.entries.filter { it in st.slots }.joinToString(", ") { slotName(it).lowercase(Lang.locale) })
                }
            } + Plan.endsOn(d.plan, today).map { tr("Today is the last day of {0}.", it.name) }
            if (info.isNotEmpty()) {
                val text = info.joinToString("\n")
                val n = NotificationCompat.Builder(c, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(0xFFA0594F.toInt())
                    .setContentTitle(tr("Medication plan"))
                    .setContentText(text.replace("\n", " "))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setContentIntent(openIntent(c))
                    .setAutoCancel(true)
                    .build()
                try { nm.notify(INFO_ID, n) } catch (_: SecurityException) {}
            }
        }
    }

    fun cancel(c: Context, slot: Slot) = NotificationManagerCompat.from(c).cancel(noteId(slot))
}

class PlanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val slot = Slot.of(intent.getStringExtra(PlanNotifier.EXTRA_SLOT) ?: "") ?: return
        val pending = goAsync()
        Thread {
            try {
                Repo.init(app)
                Lang.init(app)
                when (intent.action) {
                    PlanNotifier.ACTION_FIRE -> { PlanNotifier.schedule(app); PlanNotifier.show(app, slot, again = false) }
                    PlanNotifier.ACTION_AGAIN -> PlanNotifier.show(app, slot, again = true)
                    PlanNotifier.ACTION_ALL -> {
                        val today = LocalDate.now().toString()
                        PlanNotifier.due(slot).forEach { Repo.tick(today, it, true) }
                        Repo.flush()
                        PlanNotifier.cancel(app, slot)
                    }
                    PlanNotifier.ACTION_SNOOZE -> { PlanNotifier.later(app, slot, 15); PlanNotifier.cancel(app, slot) }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** Settings → Medications: simple list or planner, where the planner lives, the times of day, the repeat. */
@Composable
fun MedsSettings(settingSwitch: @Composable (String, String, Boolean, (Boolean) -> Unit) -> Unit) {
    val context = LocalContext.current
    val plan = Repo.data.plan
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Choice(tr("Simple list"), tr("Tick your medications in the journal, as before"), !plan.enabled) { Repo.setPlanEnabled(false) }
    Choice(
        tr("Planner"),
        tr("Courses with dates, ¼ and ½ doses, dose changes in stages and reminders. For people who take many medications."),
        plan.enabled,
    ) {
        Repo.setPlanEnabled(true)
        if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (!plan.enabled) return
    Text(tr("Where to open the planner"), fontSize = 14.sp, color = InkSoft, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 2.dp))
    Choice(tr("A line in the journal"), tr("Under the mood scale; tap it to open"), plan.placement != "tab") { Repo.updatePlan { it.copy(placement = "row") } }
    Choice(tr("Its own tab at the bottom"), tr("A fifth button next to the Journal"), plan.placement == "tab") { Repo.updatePlan { it.copy(placement = "tab") } }
    Text(tr("Times of day"), fontSize = 14.sp, color = InkSoft, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 2.dp))
    for (s in Slot.entries) {
        Row(
            Modifier.fillMaxWidth().clickable {
                val (h, m) = plan.time(s).split(":").map { it.toInt() }
                TimePickerDialog(context, { _, hh, mm ->
                    val t = String.format(java.util.Locale.US, "%02d:%02d", hh, mm)
                    Repo.updatePlan { p -> p.copy(times = p.times + (s to t)) }
                }, h, m, DateFormat.is24HourFormat(context)).show()
            }.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(slotName(s), fontSize = 16.sp, color = Ink, modifier = Modifier.weight(1f))
            Text(plan.time(s), fontSize = 16.sp, color = Accent)
            Text("  ›", fontSize = 16.sp, color = InkFaint)
        }
    }
    settingSwitch(tr("Remind me again"), tr("30 minutes later, if nothing was ticked"), plan.repeat) { on -> Repo.updatePlan { it.copy(repeat = on) } }
}

@Composable
private fun Choice(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { Haptics.tap(); onClick() }.padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.padding(top = 2.dp).size(20.dp).clip(CircleShape)
                .border(if (selected) 6.dp else 2.dp, if (selected) Accent else InkFaint, CircleShape),
        )
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, fontSize = 16.sp, color = Ink)
            Text(description, fontSize = 13.sp, lineHeight = 18.sp, fontStyle = FontStyle.Italic, color = InkSoft)
        }
    }
}

/**
 * When reminders are set but Android doesn't allow exact alarms (the Google Play version on Android 14+ until the
 * person allows "Alarms & reminders"), a line that opens that setting. Reminders still come without it, a bit late.
 */
@Composable
fun ExactAlarmHint() {
    val context = LocalContext.current
    var bump by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) bump++ }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }
    val d = Repo.data
    val wanted = (d.plan.enabled && d.plan.meds.any { it.remind }) || d.points.any { it.reminders.isNotEmpty() && !it.archived } || SleepNotifier.enabled(context)
    if (bump < 0 || !wanted || Reminders.canExact(context)) return
    Column(
        Modifier.fillMaxWidth().clickable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(tr("Allow exact reminders"), fontSize = 16.sp, color = Accent)
        Text(tr("Without this, Android may deliver reminders a few minutes late. Tap and turn on “Alarms & reminders” for scrmoods."), fontSize = 13.sp, lineHeight = 18.sp, fontStyle = FontStyle.Italic, color = InkSoft)
    }
}
