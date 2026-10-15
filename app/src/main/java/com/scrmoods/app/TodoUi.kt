package com.scrmoods.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Whether the to-do list shows on today's page (Settings → Little touches). */
object TodoPrefs {
    var enabled by mutableStateOf(true)
        private set

    fun init(c: Context) {
        enabled = c.getSharedPreferences("look", Context.MODE_PRIVATE).getBoolean("todo_on", true)
    }

    fun setEnabled(c: Context, on: Boolean) {
        enabled = on
        c.getSharedPreferences("look", Context.MODE_PRIVATE).edit().putBoolean("todo_on", on).apply()
    }
}

/**
 * The to-do list: a field to add a task, the open tasks and today's done ones (crossed out).
 * Tap a task to tick it off or bring it back; × deletes it.
 */
@Composable
fun TodoList(gutter: Dp, focus: FocusRequester? = null) {
    val today = LocalDate.now().toString()
    val all = Repo.data.todos
    val shown = Todos.shown(all, today)
    var text by remember { mutableStateOf("") }
    fun add() {
        if (text.isBlank()) return
        Haptics.tap()
        Repo.addTodo(text)
        text = ""
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = gutter)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(tr("Add a task…"), fontStyle = FontStyle.Italic, color = InkFaint) },
                singleLine = true,
                textStyle = TextStyle(fontFamily = Lora, fontSize = 16.sp, color = Ink),
                colors = journalFieldColors(),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f).then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
            )
            Box(Modifier.width(8.dp))
            RoundButton(enabled = text.isNotBlank(), onClick = { add() }) {
                Text("+", fontSize = 22.sp, color = if (text.isNotBlank()) Accent else Rule)
            }
        }
        if (shown.isEmpty()) Text(
            tr("Nothing to do yet. Add what's on your mind for today."),
            fontSize = 13.5.sp, fontStyle = FontStyle.Italic, color = InkSoft, modifier = Modifier.padding(top = 10.dp),
        )
        for (t in shown) TodoRow(t)
        if (shown.any { it.done }) LinkButton(tr("Clear done"), Modifier.padding(top = 2.dp)) { Repo.clearDoneTodos() }
    }
}

@Composable
private fun TodoRow(t: Todo) {
    val pop = rememberPop()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                Haptics.tap()
                if (!t.done) pop.fire(stars = true, color = Accent)
                Repo.toggleTodo(t.id)
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .then(pop.modifier)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (t.done) Accent else Page)
                .border(1.6.dp, if (t.done) Accent else InkFaint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (t.done) Text("✓", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnAccent)
        }
        Text(
            t.text,
            fontSize = 16.sp, lineHeight = 22.sp,
            color = if (t.done) InkFaint else Ink,
            textDecoration = if (t.done) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        Text(
            "×", fontSize = 18.sp, color = InkFaint,
            modifier = Modifier.clip(CircleShape).clickable { Haptics.tap(); Repo.deleteTodo(t.id) }.padding(horizontal = 10.dp),
        )
    }
}
