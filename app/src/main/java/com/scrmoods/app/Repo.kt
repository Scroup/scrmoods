package com.scrmoods.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors

/** Holds the app's data in memory and saves it to scrmoods.xml after every change. */
object Repo {
    private const val FILE_NAME = "scrmoods.xml"
    private val writer = Executors.newSingleThreadExecutor()
    private lateinit var file: File

    var data by mutableStateOf(Defaults.data())
        private set

    var loadError by mutableStateOf<String?>(null)

    val filePath: String get() = file.absolutePath

    private var appContext: Context? = null

    @Synchronized
    fun init(context: Context) {
        if (::file.isInitialized) return
        appContext = context.applicationContext
        file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            try {
                data = file.inputStream().use { XmlStore.read(it) }
            } catch (e: Exception) {
                // Keep a copy of the unreadable file instead of overwriting it.
                file.copyTo(File(context.filesDir, "scrmoods-unreadable-${System.currentTimeMillis()}.xml"), overwrite = true)
                loadError = "Could not read saved data (${e.message}). A copy was kept; starting fresh."
                data = Defaults.data()
            }
        } else {
            save()
        }
    }

    private fun update(change: (AppData) -> AppData) {
        data = change(data)
        save()
    }

    private fun save() {
        val snapshot = data
        val target = file
        writer.execute {
            try {
                val tmp = File(target.parentFile, target.name + ".tmp")
                tmp.outputStream().use { XmlStore.write(snapshot, it) }
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
            } catch (_: Exception) {
            }
        }
    }

    /** Waits until everything is written to disk (used by the notification "Taken" button). */
    fun flush() {
        writer.submit {}.get()
    }

    /** Medication reminders follow the tracking points, so re-set the alarms whenever points change. */
    private fun pointsChanged() {
        appContext?.let { Reminders.scheduleAll(it) }
    }

    // ---------- day values ----------

    fun entry(date: String): DayEntry = data.entries[date] ?: DayEntry(date)

    private fun updateEntry(date: String, change: (DayEntry) -> DayEntry) = update { d ->
        val e = change(d.entries[date] ?: DayEntry(date))
        val map = d.entries.toMutableMap()
        if (e.isEmpty) map.remove(date) else map[date] = e
        d.copy(entries = map)
    }

    /** value = null clears the answer. */
    fun setValue(date: String, pointId: String, value: String?) = updateEntry(date) { e ->
        val v = e.values.toMutableMap()
        if (value == null || value.isEmpty()) v.remove(pointId) else v[pointId] = value
        e.copy(values = v)
    }

    fun setNote(date: String, note: String) = updateEntry(date) { it.copy(note = note.trim()) }

    /** A quick note from the widget: a new line at the end of the day's note, e.g. "20:31 — feeling calmer". */
    fun addQuickNote(date: String, time: String, text: String) = updateEntry(date) {
        val line = "$time — ${text.trim()}"
        it.copy(note = if (it.note.isBlank()) line else it.note.trimEnd() + "\n" + line)
    }

    fun addTimedNote(date: String, time: String, text: String) = updateEntry(date) {
        it.copy(timedNotes = it.timedNotes + TimedNote(time, text.trim()))
    }

    fun removeTimedNote(date: String, index: Int) = updateEntry(date) {
        it.copy(timedNotes = it.timedNotes.filterIndexed { i, _ -> i != index })
    }

    // ---------- tracking points ----------

    fun addPoint(p: TrackingPoint) {
        update { it.copy(points = it.points + p) }
        pointsChanged()
    }

    fun editPoint(p: TrackingPoint) {
        update { d -> d.copy(points = d.points.map { if (it.id == p.id) p else it }) }
        pointsChanged()
    }

    fun setArchived(id: String, archived: Boolean) {
        update { d -> d.copy(points = d.points.map { if (it.id == id) it.copy(archived = archived) else it }) }
        pointsChanged()
    }

    /** Removes the point and every answer ever given for it. */
    fun deleteForever(id: String) {
        update { d ->
            d.copy(
                points = d.points.filter { it.id != id },
                entries = d.entries.mapValues { (_, e) -> e.copy(values = e.values - id) }
                    .filterValues { !it.isEmpty },
            )
        }
        pointsChanged()
    }

    /** Moves a point up (-1) or down (+1) among the active points of its section. */
    fun move(id: String, dir: Int) = update { d ->
        val p = d.points.firstOrNull { it.id == id } ?: return@update d
        val siblings = d.points.filter { it.sectionId == p.sectionId && !it.archived }
        val idx = siblings.indexOf(p)
        val other = siblings.getOrNull(idx + dir) ?: return@update d
        val list = d.points.toMutableList()
        val a = list.indexOf(p)
        val b = list.indexOf(other)
        list[a] = other
        list[b] = p
        d.copy(points = list)
    }

    fun setSectionHidden(id: String, hidden: Boolean) = update { d ->
        d.copy(sections = d.sections.map { if (it.id == id) it.copy(hidden = hidden) else it })
    }

    fun newId(prefix: String) = prefix + "_" + System.currentTimeMillis().toString(36)

    // ---------- import / export ----------

    fun export(out: OutputStream) = XmlStore.write(data, out)

    /** Replaces all data with the contents of an exported file. Throws if the file can't be read. */
    fun import(input: InputStream) {
        val d = XmlStore.read(input)
        data = d
        save()
        pointsChanged()
    }

    fun resetSetupToDefaults() {
        resetPoints()
        pointsChanged()
    }

    private fun resetPoints() = update { d ->
        // Keeps all answers; restores built-in questions that were removed.
        val ids = d.points.map { it.id }.toSet()
        val restored = d.points.map { p ->
            if (Defaults.points.any { it.id == p.id }) p.copy(archived = false) else p
        }
        d.copy(points = restored + Defaults.points.filter { it.id !in ids })
    }
}
