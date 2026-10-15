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
                loadError = tr("Could not read saved data ({0}). A copy was kept; starting fresh.", e.message)
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
    /** Answers the home-screen widget shows (today's cups and meals, last night's sleep). */
    private val WIDGET_POINTS = setOf(Water.POINT, Meals.MEALS, Sleep.HOURS, Sleep.QUALITY)


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
    fun setValue(date: String, pointId: String, value: String?) {
        updateEntry(date) { e ->
            val v = e.values.toMutableMap()
            if (value == null || value.isEmpty()) v.remove(pointId) else v[pointId] = value
            e.copy(values = v)
        }
        // the home-screen widget shows today's cups
        if (pointId in WIDGET_POINTS) appContext?.let { QuickNoteWidget.refresh(it) }
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

    // ---------- to-do list ----------

    private fun updateTodos(change: (List<Todo>) -> List<Todo>) {
        update { it.copy(todos = change(it.todos)) }
        appContext?.let { QuickNoteWidget.refresh(it) } // the widget shows how many are open
    }

    fun addTodo(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        updateTodos { it + Todo(Todos.newId(), t, java.time.LocalDate.now().toString()) }
    }

    /** Ticks a task off today, or brings it back. */
    fun toggleTodo(id: String) = updateTodos { list ->
        list.map { if (it.id == id) it.copy(doneOn = if (it.done) null else java.time.LocalDate.now().toString()) else it }
    }

    fun deleteTodo(id: String) = updateTodos { list -> list.filter { it.id != id } }

    fun clearDoneTodos() = updateTodos { list -> list.filter { !it.done } }

    // ---------- medication planner ----------

    fun updatePlan(change: (MedPlan) -> MedPlan) {
        update { it.copy(plan = change(it.plan)) }
        pointsChanged() // its reminders follow the plan
    }

    /** Adds or replaces a planned medication. */
    fun saveMed(m: PlanMed) = updatePlan { p ->
        p.copy(meds = if (p.meds.any { it.id == m.id }) p.meds.map { if (it.id == m.id) m else it } else p.meds + m)
    }

    /** Removes a planned medication; the doses already ticked stay in the days' records. */
    fun deleteMed(id: String) = updatePlan { p -> p.copy(meds = p.meds.filter { it.id != id }) }

    /**
     * Switches between the simple medication list and the planner. The first time the planner is switched on,
     * the simple medications are copied into it; the simple list is hidden while the planner is on.
     */
    fun setPlanEnabled(on: Boolean, today: String = java.time.LocalDate.now().toString()) {
        update { d ->
            val imported = if (on && d.plan.meds.isEmpty()) Plan.importSimple(d, today) else emptyList()
            d.copy(
                plan = d.plan.copy(enabled = on, meds = d.plan.meds + imported),
                sections = d.sections.map { if (it.id == Sections.MEDS) it.copy(hidden = on) else it },
            )
        }
        pointsChanged()
    }

    fun tick(date: String, i: Intake, on: Boolean) = setValue(date, i.key, if (on) "1" else null)

    // ---------- tracking points ----------

    fun addPoint(p: TrackingPoint) {
        update { it.copy(points = it.points + p) }
        pointsChanged()
    }

    fun editPoint(p: TrackingPoint) {
        update { d ->
            val old = d.points.firstOrNull { it.id == p.id }
            // a medication's doses a day changed: keep "all taken" meaning all taken
            val entries = if (old != null && old.isPillMed && p.isPillMed && old.doses != p.doses) {
                d.entries.mapValues { (_, e) ->
                    e.values[p.id]?.let { v -> e.copy(values = e.values + (p.id to remapDose(v, old.doses, p.doses))) } ?: e
                }
            } else d.entries
            d.copy(points = d.points.map { if (it.id == p.id) p else it }, entries = entries)
        }
        if (p.id in WIDGET_POINTS) appContext?.let { QuickNoteWidget.refresh(it) }
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

    fun setSectionHidden(id: String, hidden: Boolean) {
        update { d -> d.copy(sections = d.sections.map { if (it.id == id) it.copy(hidden = hidden) else it }) }
        if (id == Sections.WATER || id == Sections.MEALS || id == Sections.HEALTH) appContext?.let { QuickNoteWidget.refresh(it) }
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
