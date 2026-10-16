package com.scrmoods.app

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DataScreen(onManage: () -> Unit) {
    val context = LocalContext.current
    val data = Repo.data
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    // file work runs off the main thread: the chosen file may live in Google Drive, and a slow read would freeze the app
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/xml")) { uri ->
        if (uri != null) {
            val snapshot = Repo.data
            scope.launch {
                val error = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { XmlStore.write(snapshot, it) } }.exceptionOrNull()
                }
                if (error == null) Toast.makeText(context, tr("Exported"), Toast.LENGTH_SHORT).show()
                else Toast.makeText(context, tr("Export failed: {0}", error.message), Toast.LENGTH_LONG).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        SectionHeader(tr("Language"))
        Segmented(
            Lang.options.map { it.name },
            Lang.options.indexOfFirst { it.code == Lang.code },
            { i -> if (i != null) Lang.set(context, Lang.options[i].code) },
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )

        SectionHeader(tr("Appearance"))
        ThemePicker()

        SectionHeader(tr("Journal sections"))
        val bipolar = data.sections.firstOrNull { it.id == Sections.BIPOLAR }
        if (bipolar != null) {
            Row(
                Modifier.fillMaxWidth().clickable { Haptics.tap(); Repo.setSectionHidden(Sections.BIPOLAR, !bipolar.hidden) }
                    .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(tr("Mood scale"), fontSize = 16.sp, color = Ink)
                    Text(
                        tr("Track depression and mania from 0 to 10 at the top of the journal. Hiding it keeps your past answers."),
                        fontSize = 13.sp, lineHeight = 18.sp, fontStyle = FontStyle.Italic, color = InkSoft,
                    )
                }
                Switch(
                    checked = !bipolar.hidden,
                    onCheckedChange = { Haptics.tap(); Repo.setSectionHidden(Sections.BIPOLAR, !it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Accent,
                        checkedThumbColor = OnAccent,
                        uncheckedTrackColor = Paper,
                        uncheckedThumbColor = InkFaint,
                        uncheckedBorderColor = Rule,
                    ),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().clickable { Haptics.tap(); Themes.setShowQuote(context, !Themes.showQuote) }
                .padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(tr("Quote of the day"), fontSize = 16.sp, color = Ink)
                Text(
                    tr("A quote or gentle reminder at the top of the journal, a new one each day."),
                    fontSize = 13.sp, lineHeight = 18.sp, fontStyle = FontStyle.Italic, color = InkSoft,
                )
            }
            Switch(
                checked = Themes.showQuote,
                onCheckedChange = { Haptics.tap(); Themes.setShowQuote(context, it) },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Accent,
                    checkedThumbColor = OnAccent,
                    uncheckedTrackColor = Paper,
                    uncheckedThumbColor = InkFaint,
                    uncheckedBorderColor = Rule,
                ),
            )
        }

        SectionHeader(tr("Medications"))
        MedsSettings { title, desc, checked, onChange -> SettingSwitch(title, desc, checked, onChange) }
        ExactAlarmHint()

        SectionHeader(tr("Water"))
        WaterSettings { title, desc, checked, onChange -> SettingSwitch(title, desc, checked, onChange) }

        SectionHeader(tr("Sleep"))
        MorningSleepSettings { title, desc, checked, onChange -> SettingSwitch(title, desc, checked, onChange) }

        SectionHeader(tr("Meals"))
        MealsSettings { title, desc, checked, onChange -> SettingSwitch(title, desc, checked, onChange) }

        SectionHeader(tr("Little touches"))
        SettingSwitch(tr("To-do list"), tr("A short list of tasks on today's page: at the top during the day, lower in the evening."), TodoPrefs.enabled) {
            TodoPrefs.setEnabled(context, it)
        }
        SettingSwitch(tr("Vibrate on tap"), tr("A soft click of vibration when you tap buttons and answers."), Haptics.enabled) {
            Haptics.setEnabled(context, it)
        }
        SettingSwitch(tr("Welcome when opening"), tr("A short greeting, then the journal rises into place."), Intro.enabled) {
            Intro.setEnabled(context, it)
        }
        SettingSwitch(tr("Stars when answering"), tr("Answer buttons squish when tapped and pop out little stars."), Sparkles.enabled) {
            Sparkles.setEnabled(context, it)
        }
        SettingSwitch(
            tr("Good night celebration"),
            tr("A “Finish the day” button under today's note: confetti, a good-night wish and a short lullaby."),
            GoodNight.enabled,
        ) { GoodNight.setEnabled(context, it) }
        if (GoodNight.enabled) {
            SettingSwitch(tr("Play the lullaby"), tr("Uses your media volume."), GoodNight.melody) { GoodNight.setMelody(context, it) }
            LinkButton(tr("Preview the celebration"), Modifier.padding(start = 8.dp)) {
                GoodNight.celebrate(context, LocalDate.now().toString(), remember = false)
            }
        }

        if (BuildConfig.SELF_UPDATE) UpdateSection() // the Google Play version is updated by Google Play

        SectionHeader(tr("Your journal"))
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
            val logged = data.entries.size
            val first = data.entries.keys.minOrNull()
            Text(tr("{0} {1} written", logged, trp(logged, "day", "days")), fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Ink)
            if (first != null) Text(tr("since {0}", first), fontSize = 16.sp, fontStyle = FontStyle.Italic, color = InkSoft)
            Text(tr("{0} tracking points in use", data.points.count { !it.archived }), fontSize = 16.sp, color = Ink, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                tr("Everything is stored in a single XML file on this phone:"),
                fontSize = 14.sp, color = TextGray,
            )
            Text(Repo.filePath, fontSize = 12.sp, color = InkFaint, fontFamily = FontFamily.Monospace)
            Text(
                tr("Export it to save a backup or to open it on your computer for statistics. Each answer is saved with its number and its readable text, e.g. {0}.", "<value point=\"outlook\" text=\"Positive\">2</value>"),
                fontSize = 14.sp, color = TextGray, modifier = Modifier.padding(top = 6.dp),
            )
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Button(
                onClick = { exportLauncher.launch("scrmoods-${LocalDate.now()}.xml") },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("Export data (XML)")) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("text/xml", "application/xml", "text/plain", "*/*")) },
                border = BorderStroke(1.dp, Rule),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("Import data from file…"), color = Accent) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onManage, border = BorderStroke(1.dp, Rule), contentPadding = PaddingValues(vertical = 14.dp), modifier = Modifier.fillMaxWidth()) {
                Text(tr("Manage tracking points"), color = Accent)
            }
        }

        Text(
            tr("Tip: export regularly. Uninstalling the app deletes its data on the phone."),
            fontSize = 14.sp, fontStyle = FontStyle.Italic, color = InkSoft, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(24.dp))
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = tr("Replace all data?"),
            message = tr("Importing replaces everything in the app (tracking points and all days) with the contents of the file. Export first if you want to keep a copy of the current data."),
            confirmText = tr("Import"),
            onDismiss = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                scope.launch {
                    val read = withContext(Dispatchers.IO) {
                        runCatching {
                            val input = context.contentResolver.openInputStream(uri) ?: error(tr("Couldn't open the file."))
                            input.use {
                                // a huge file would run out of memory: refuse it before parsing
                                val bytes = it.readNBytesCompat(XmlStore.MAX_BYTES + 1)
                                if (bytes.size > XmlStore.MAX_BYTES) error(tr("The file is too large."))
                                XmlStore.read(bytes.inputStream())
                            }
                        }
                    }
                    read.onSuccess { d ->
                        Repo.replaceAll(d)
                        Toast.makeText(context, tr("Imported {0} {1}", d.entries.size, trp(d.entries.size, "day", "days")), Toast.LENGTH_SHORT).show()
                    }.onFailure { e ->
                        Toast.makeText(context, tr("Import failed: {0}", e.message), Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }
}

/** A row of theme swatches: each shows the background, a card and the accent colour. */
@Composable
private fun ThemePicker() {
    val context = LocalContext.current
    val current = Themes.current
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
        for (row in Themes.all.chunked(4)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in row) {
                    val selected = t.id == current.id
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(t.paper)
                            .border(if (selected) 2.dp else 1.dp, if (selected) t.accent else t.rule, shape)
                            .clickable { Haptics.tap(); Themes.select(context, t) }
                            .padding(7.dp),
                    ) {
                        // a tiny journal page: a card with two lines and an accent pill
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.page)
                                .border(1.dp, t.rule, RoundedCornerShape(10.dp)).padding(8.dp),
                        ) {
                            Box(Modifier.fillMaxWidth(0.8f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(t.ink.copy(alpha = 0.75f)))
                            Spacer(Modifier.height(5.dp))
                            Box(Modifier.fillMaxWidth(0.55f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(t.inkSoft.copy(alpha = 0.6f)))
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(t.accent))
                                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(t.paper).border(1.dp, t.rule, RoundedCornerShape(5.dp)))
                            }
                        }
                        // one line, centred, same size on every card
                        Text(
                            tr(t.name),
                            fontSize = 12.sp,
                            fontStyle = if (selected) FontStyle.Normal else FontStyle.Italic,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            color = t.ink,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
                }
                // keep the last row's cards the same width as the others
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A setting with a title, a short explanation and a switch; tapping the row toggles it too. */
@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { Haptics.tap(); onChange(!checked) }
            .padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 16.sp, color = Ink)
            Text(description, fontSize = 13.sp, lineHeight = 18.sp, fontStyle = FontStyle.Italic, color = InkSoft)
        }
        Switch(
            checked = checked,
            onCheckedChange = { Haptics.tap(); onChange(it) },
            colors = SwitchDefaults.colors(
                checkedTrackColor = Accent,
                checkedThumbColor = OnAccent,
                uncheckedTrackColor = Paper,
                uncheckedThumbColor = InkFaint,
                uncheckedBorderColor = Rule,
            ),
        )
    }
}

/** Reads at most [limit] bytes (InputStream.readNBytes needs Android 13). */
private fun java.io.InputStream.readNBytesCompat(limit: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (total < limit) {
        val n = read(buf, 0, minOf(buf.size.toLong(), limit - total).toInt())
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}
