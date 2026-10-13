package com.scrmoods.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Daily medication reminders.
 *
 * Each reminder time of each (not removed) tracking point gets its own exact alarm. When it fires,
 * [ReminderReceiver] shows a notification with "Taken" and "Snooze" buttons and schedules the
 * same time for the next day. [BootReceiver] puts all alarms back after a restart or app update.
 */
object Reminders {
    const val CHANNEL_ID = "med_reminders"
    private const val PREFS = "reminders"
    private const val KEY_SCHEDULED = "scheduled"
    const val ACTION_FIRE = "com.scrmoods.app.REMINDER"
    const val ACTION_TAKEN = "com.scrmoods.app.TAKEN"
    const val ACTION_SNOOZE = "com.scrmoods.app.SNOOZE"
    const val EXTRA_POINT = "point"
    const val EXTRA_TIME = "time"
    const val EXTRA_SNOOZED = "snoozed"
    const val SNOOZE_MINUTES = 15L

    private val TIME_RE = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    fun isValidTime(t: String) = TIME_RE.matches(t)

    /** Stable id used for both the alarm and the notification of one point + time. */
    fun idFor(pointId: String, time: String) = "$pointId|$time".hashCode()

    private fun fireIntent(context: Context, pointId: String, time: String, snoozed: Boolean): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            // A distinct data URI keeps every reminder's PendingIntent separate.
            data = Uri.parse("scrmoods://reminder/${Uri.encode(pointId)}/$time" + if (snoozed) "/snooze" else "")
            putExtra(EXTRA_POINT, pointId)
            putExtra(EXTRA_TIME, time)
            putExtra(EXTRA_SNOOZED, snoozed)
        }
        return PendingIntent.getBroadcast(
            context, idFor(pointId, time) + if (snoozed) 1 else 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** The next moment (today or tomorrow) the given "HH:mm" time comes round. */
    private fun nextOccurrence(time: String, after: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val t = LocalTime.parse(time)
        val today = LocalDate.now().atTime(t)
        return if (today.isAfter(after.plusSeconds(5))) today else today.plusDays(1)
    }

    private fun setAlarm(context: Context, atMillis: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
    }

    private fun schedule(context: Context, pointId: String, time: String) {
        val at = nextOccurrence(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setAlarm(context, at, fireIntent(context, pointId, time, snoozed = false))
    }

    fun snooze(context: Context, pointId: String, time: String) {
        val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000
        setAlarm(context, at, fireIntent(context, pointId, time, snoozed = true))
    }

    /** Cancels every reminder set before and sets one alarm per current reminder time. */
    fun scheduleAll(context: Context) {
        val ctx = context.applicationContext
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (key in prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()) {
            val pointId = key.substringBeforeLast('|')
            val time = key.substringAfterLast('|')
            am.cancel(fireIntent(ctx, pointId, time, snoozed = false))
        }
        val keys = mutableSetOf<String>()
        for (p in Repo.data.points) {
            if (p.archived) continue
            for (t in p.reminders.filter(::isValidTime).distinct()) {
                schedule(ctx, p.id, t)
                keys += "${p.id}|$t"
            }
        }
        prefs.edit().putStringSet(KEY_SCHEDULED, keys).apply()
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, tr("Medication reminders"), NotificationManager.IMPORTANCE_HIGH)
        ch.description = tr("Reminders to take your medications and supplements")
        nm.createNotificationChannel(ch)
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun show(context: Context, p: TrackingPoint, time: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val id = idFor(p.id, time)
        fun action(act: String): PendingIntent {
            val i = Intent(context, ReminderReceiver::class.java).apply {
                action = act
                data = Uri.parse("scrmoods://action/$act/${Uri.encode(p.id)}/$time")
                putExtra(EXTRA_POINT, p.id)
                putExtra(EXTRA_TIME, time)
            }
            return PendingIntent.getBroadcast(context, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFA0594F.toInt())
            .setContentTitle(tr("Time to take your medication"))
            .setContentText(p.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(p.title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, tr("Taken"), action(ACTION_TAKEN))
            .addAction(0, tr("Snooze {0} min", SNOOZE_MINUTES), action(ACTION_SNOOZE))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    fun dismiss(context: Context, pointId: String, time: String) {
        NotificationManagerCompat.from(context).cancel(idFor(pointId, time))
    }

    /** "Reminder at 08:00" / "Reminders at 08:00 and 21:00" */
    fun describe(times: List<String>): String = when (times.size) {
        0 -> ""
        1 -> tr("Reminder at {0}", times[0])
        else -> tr("Reminders at {0} and {1}", times.dropLast(1).joinToString(", "), times.last())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pointId = intent.getStringExtra(Reminders.EXTRA_POINT) ?: return
        val time = intent.getStringExtra(Reminders.EXTRA_TIME) ?: return
        val pending = goAsync()
        Thread {
            try {
                Repo.init(context.applicationContext)
                Lang.init(context.applicationContext)
                val today = LocalDate.now().toString()
                val p = Repo.data.points.firstOrNull { it.id == pointId }
                when (intent.action) {
                    Reminders.ACTION_FIRE -> {
                        val snoozed = intent.getBooleanExtra(Reminders.EXTRA_SNOOZED, false)
                        // Daily alarms set up their own next day; snoozes don't.
                        if (!snoozed) Reminders.scheduleAll(context)
                        val stillWanted = p != null && !p.archived && time in p.reminders
                        val alreadyTaken = p != null && dosesTaken(Repo.data.entries[today]?.values?.get(pointId)) >= p.doses
                        if (stillWanted && !alreadyTaken) Reminders.show(context, p!!, time)
                    }
                    Reminders.ACTION_TAKEN -> {
                        // one more dose taken (a once-a-day medication simply becomes "taken")
                        val n = p?.doses ?: 1
                        val had = dosesTaken(Repo.data.entries[today]?.values?.get(pointId))
                        Repo.setValue(today, pointId, doseValue(minOf(had + 1, n), n))
                        Repo.flush()
                        Reminders.dismiss(context, pointId, time)
                    }
                    Reminders.ACTION_SNOOZE -> {
                        Reminders.snooze(context, pointId, time)
                        Reminders.dismiss(context, pointId, time)
                    }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** Puts the alarms back after the phone restarts, the app is updated, or the clock/time zone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                Repo.init(context.applicationContext)
                Lang.init(context.applicationContext)
                Reminders.scheduleAll(context)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
