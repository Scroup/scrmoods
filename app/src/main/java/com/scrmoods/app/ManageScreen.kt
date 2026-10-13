package com.scrmoods.app

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ManageScreen(
    onBack: () -> Unit,
    onAdd: (sectionId: String) -> Unit,
    onEdit: (TrackingPoint) -> Unit,
) {
    val data = Repo.data
    var confirmDelete by remember { mutableStateOf<TrackingPoint?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(
            Modifier.fillMaxWidth().background(Paper).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back"), tint = TextDark) }
            Text(tr("Tracking points"), fontSize = 23.sp, fontWeight = FontWeight.Medium, color = Ink)
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                tr("Add your own questions, edit or reorder them, or remove the ones you don't need. Removing a point only hides it from the check-in, so its past answers stay in your data."),
                color = TextGray, fontSize = 15.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp), fontStyle = FontStyle.Italic, lineHeight = 21.sp,
            )
            for (section in data.sections) {
                if (section.id == Sections.BIPOLAR) continue // switched on/off in Settings
                val points = data.points.filter { it.sectionId == section.id && !it.archived }
                SectionHeader(section.name)
                points.forEachIndexed { i, p ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).clickable { onEdit(p) }) {
                            Text(p.title, fontSize = 15.sp, lineHeight = 20.sp, color = Ink)
                            Text(typeSummary(p), fontSize = 13.sp, color = InkSoft)
                            if (p.reminders.isNotEmpty()) Text(Reminders.describe(p.reminders), fontSize = 13.sp, fontStyle = FontStyle.Italic, color = Accent)
                        }
                        SmallIcon(Icons.Filled.KeyboardArrowUp, tr("Move up"), enabled = i > 0) { Repo.move(p.id, -1) }
                        SmallIcon(Icons.Filled.KeyboardArrowDown, tr("Move down"), enabled = i < points.size - 1) { Repo.move(p.id, 1) }
                        SmallIcon(Icons.Filled.Edit, tr("Edit")) { onEdit(p) }
                        SmallIcon(Icons.Filled.Delete, tr("Remove")) { Repo.setArchived(p.id, true) }
                    }
                }
                if (points.isEmpty()) {
                    Text(tr("Nothing here yet."), color = InkFaint, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp), fontStyle = FontStyle.Italic)
                }
                LinkButton(
                    if (section.id == Sections.MEDS) tr("+ Add medication") else tr("+ Add tracking point"),
                    Modifier.padding(start = 8.dp),
                ) { onAdd(section.id) }
            }

            val removed = data.points.filter { it.archived && it.sectionId != Sections.BIPOLAR }
            if (removed.isNotEmpty()) {
                SectionHeader(tr("Removed tracking points"))
                Text(
                    tr("Hidden from the check-in. Restore brings a point back; Delete erases it and all its past answers."),
                    color = InkSoft, fontSize = 14.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                for (p in removed) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.title, fontSize = 16.sp, color = TextGray)
                            Text(sectionTitle(p.sectionId), fontSize = 13.sp, color = InkFaint)
                        }
                        SmallIcon(Icons.Filled.Refresh, tr("Restore")) { Repo.setArchived(p.id, false) }
                        SmallIcon(Icons.Filled.Delete, tr("Delete forever"), tint = Danger) { confirmDelete = p }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp), color = Rule)
            TextButton(onClick = { confirmReset = true }, modifier = Modifier.padding(8.dp)) {
                Text(tr("Restore all built-in questions"), color = InkSoft, fontStyle = FontStyle.Italic)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    confirmDelete?.let { p ->
        ConfirmDialog(
            title = tr("Delete forever?"),
            message = tr("“{0}” and every answer you gave for it will be erased. This can't be undone.", p.title),
            confirmText = tr("Delete"),
            onDismiss = { confirmDelete = null },
            onConfirm = { Repo.deleteForever(p.id); confirmDelete = null },
        )
    }
    if (confirmReset) ConfirmDialog(
        title = tr("Restore built-in questions?"),
        message = tr("Any removed or deleted built-in questions come back. Your own points and all answers are kept."),
        confirmText = tr("Restore"),
        onDismiss = { confirmReset = false },
        onConfirm = { Repo.resetSetupToDefaults(); confirmReset = false },
    )
}

private fun sectionTitle(id: String) = Repo.data.sections.firstOrNull { it.id == id }?.name ?: id

private fun typeSummary(p: TrackingPoint): String = when (p.type) {
    PointType.SCALE -> p.options.joinToString(" · ") { tr(it) }
    PointType.YESNO -> tr("No") + " · " + tr("Yes")
    PointType.NUMBER -> tr("Number {0}–{1}", formatNumber(p.min), formatNumber(p.max)) + if (p.unit.isNotBlank()) " ${tr(p.unit)}" else ""
    PointType.TEXT -> tr("Text")
}

@Composable
private fun SmallIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    enabled: Boolean = true,
    tint: Color = TextGray,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(icon, desc, tint = if (enabled) tint else Rule, modifier = Modifier.size(22.dp))
    }
}

/** Dialog to add a new tracking point (existing == null) or edit one. */
@Composable
fun PointEditorDialog(
    existing: TrackingPoint?,
    defaultSection: String,
    onDismiss: () -> Unit,
    onSave: (TrackingPoint) -> Unit,
) {
    val sections = Repo.data.sections.filter { it.id != Sections.BIPOLAR }
    // built-in questions show in the app's language; left unchanged, they keep their original wording
    var label by remember { mutableStateOf(existing?.title ?: "") }
    var sectionId by remember { mutableStateOf(existing?.sectionId ?: defaultSection) }
    var type by remember {
        mutableStateOf(existing?.type ?: if (defaultSection == Sections.MEDS) PointType.YESNO else PointType.SCALE)
    }
    var options by remember {
        mutableStateOf(existing?.options?.joinToString("\n") { tr(it) } ?: listOf("Not at all", "Somewhat", "Fairly", "Very").joinToString("\n") { tr(it) })
    }
    var min by remember { mutableStateOf(formatNumber(existing?.min ?: 0f)) }
    var max by remember { mutableStateOf(formatNumber(existing?.max ?: 10f)) }
    var step by remember { mutableStateOf(formatNumber(existing?.step ?: 1f)) }
    var unit by remember { mutableStateOf(existing?.unit?.let { tr(it) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var reminders by remember { mutableStateOf(existing?.reminders ?: emptyList()) }
    var better by remember { mutableStateOf(existing?.better ?: Better.HIGHER) }
    val context = LocalContext.current
    val isMed = sectionId == Sections.MEDS

    fun build(): TrackingPoint? {
        if (label.isBlank()) { error = tr("Please enter a question or name."); return null }
        val orig = existing?.options ?: emptyList()
        val opts = options.lines().map { it.trim() }.filter { it.isNotEmpty() }
            .mapIndexed { i, o -> orig.getOrNull(i)?.takeIf { tr(it) == o } ?: o }
        if (type == PointType.SCALE && opts.size < 2) { error = tr("A scale needs at least 2 options (one per line)."); return null }
        val mn = min.replace(',', '.').toFloatOrNull()
        val mx = max.replace(',', '.').toFloatOrNull()
        val st = step.replace(',', '.').toFloatOrNull()
        if (type == PointType.NUMBER && (mn == null || mx == null || st == null || mx <= mn || st <= 0f)) {
            error = tr("Check the numbers: maximum must be above minimum and step above 0."); return null
        }
        val base = existing ?: TrackingPoint(
            id = Repo.newId(if (isMed) "med" else "custom"),
            sectionId = sectionId, type = type, label = "",
        )
        return base.copy(
            label = if (existing != null && label.trim() == existing.title) existing.label else label.trim(),
            sectionId = sectionId,
            type = type,
            options = if (type == PointType.SCALE) opts else emptyList(),
            min = mn ?: 0f, max = mx ?: 10f, step = st ?: 1f,
            unit = if (type != PointType.NUMBER) "" else if (existing != null && unit.trim() == tr(existing.unit)) existing.unit else unit.trim(),
            reminders = if (type == PointType.YESNO) reminders.distinct().sorted() else emptyList(),
            better = if (type == PointType.TEXT) Better.NONE else better,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    existing != null -> tr("Edit tracking point")
                    isMed -> tr("Add medication")
                    else -> tr("Add tracking point")
                },
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it; error = null },
                    label = { Text(if (isMed) tr("Name and dose") else tr("Question")) },
                    placeholder = { Text(if (isMed) tr("e.g. Vitamin D, 2000 IU (AM)") else tr("e.g. Did you drink enough water?")) },
                    colors = journalFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Text(tr("Section"), fontSize = 14.sp, color = TextGray)
                for (s in sections) {
                    RadioRow(s.name, sectionId == s.id) { sectionId = s.id }
                }

                Spacer(Modifier.height(8.dp))
                Text(tr("Answer type"), fontSize = 14.sp, color = TextGray)
                if (existing == null) {
                    for (t in PointType.entries) RadioRow(tr(t.title), type == t) { type = t; error = null }
                } else {
                    Text(
                        tr("{0} (can't be changed once created, so old answers stay valid)", tr(type.title)),
                        fontSize = 14.sp, color = TextDark, modifier = Modifier.padding(vertical = 6.dp),
                    )
                }

                if (type == PointType.SCALE) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = options,
                        onValueChange = { options = it; error = null },
                        label = { Text(tr("Options, one per line (lowest first)")) },
                        minLines = 3,
                        colors = journalFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (existing != null) Text(
                        tr("Answers are saved by position, so keep the same number and order of options."),
                        fontSize = 12.sp, color = InkSoft,
                    )
                }
                if (type == PointType.NUMBER) {
                    Spacer(Modifier.height(8.dp))
                    Row {
                        NumField(tr("Min"), min, Modifier.weight(1f)) { min = it; error = null }
                        Spacer(Modifier.width(8.dp))
                        NumField(tr("Max"), max, Modifier.weight(1f)) { max = it; error = null }
                        Spacer(Modifier.width(8.dp))
                        NumField(tr("Step"), step, Modifier.weight(1f)) { step = it; error = null }
                    }
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text(tr("Unit (optional, e.g. hrs, cups)")) },
                        singleLine = true,
                        colors = journalFieldColors(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                if (type != PointType.TEXT) {
                    Spacer(Modifier.height(12.dp))
                    Text(tr("Which is better?"), fontSize = 14.sp, color = TextGray)
                    val labels = when (type) {
                        PointType.YESNO -> listOf("Yes is good", "No is good", "Neither")
                        PointType.SCALE -> listOf("Later options are better", "Earlier options are better", "Neither")
                        else -> listOf("Higher is better", "Lower is better", "Neither")
                    }
                    listOf(Better.HIGHER, Better.LOWER, Better.NONE).forEachIndexed { i, b ->
                        RadioRow(tr(labels[i]), better == b) { better = b }
                    }
                    Text(
                        tr("Used to colour Trends green when it's going well and red when it needs attention."),
                        fontSize = 12.5.sp, fontStyle = FontStyle.Italic, color = InkFaint, lineHeight = 17.sp,
                    )
                }
                if (type == PointType.YESNO) {
                    Spacer(Modifier.height(14.dp))
                    Text(tr("Remind me"), fontSize = 14.sp, color = TextGray)
                    Text(
                        if (isMed) tr("A notification at each time, every day. Tap Taken in it to tick this off.")
                        else tr("A notification at each time, every day."),
                        fontSize = 12.5.sp, fontStyle = FontStyle.Italic, color = InkFaint, lineHeight = 17.sp,
                    )
                    for (t in reminders) {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Accent, modifier = Modifier.weight(1f))
                            IconButton(onClick = { reminders = reminders - t }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Close, tr("Remove reminder"), tint = InkFaint, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    LinkButton(tr("+ Add a time"), Modifier.padding(top = 2.dp)) {
                        TimePickerDialog(
                            context,
                            { _, h, m -> reminders = (reminders + String.format(java.util.Locale.US, "%02d:%02d", h, m)).distinct().sorted() },
                            8, 0, DateFormat.is24HourFormat(context),
                        ).show()
                    }
                }
                error?.let { Text(it, color = Danger, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        containerColor = Page,
        shape = RoundedCornerShape(22.dp),
        confirmButton = { TextButton(onClick = { build()?.let(onSave) }) { Text(tr("Save"), color = Accent, fontWeight = FontWeight.Medium) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel"), color = InkSoft) } },
    )
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = InkFaint))
        Text(text, fontSize = 15.sp, color = TextDark)
    }
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = journalFieldColors(),
        modifier = modifier,
    )
}
