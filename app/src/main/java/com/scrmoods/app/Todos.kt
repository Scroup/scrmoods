package com.scrmoods.app

/*
 * A simple to-do list, separate from the days: write a task, tick it off (it's crossed out), clear the done ones.
 * Done tasks stay visible on the day they were done, then quietly drop out of the list (they stay in the file
 * until cleared). In the data file:
 *   <todos>
 *     <todo id="td_x" created="2026-10-15" done="2026-10-15">Call the pharmacy</todo>
 *   </todos>
 */
data class Todo(
    val id: String,
    val text: String,
    val created: String, // yyyy-MM-dd
    val doneOn: String? = null, // the day it was ticked off, or null while open
) {
    val done: Boolean get() = doneOn != null
}

object Todos {
    fun newId() = "td_" + System.currentTimeMillis().toString(36)

    /** What the list shows on [today]: open tasks (oldest first), then the ones ticked off today. */
    fun shown(all: List<Todo>, today: String): List<Todo> =
        all.filter { !it.done } + all.filter { it.doneOn == today }

    fun openCount(all: List<Todo>) = all.count { !it.done }
}
