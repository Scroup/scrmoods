package com.scrmoods.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(applicationContext)
        Reminders.ensureChannel(applicationContext)
        Reminders.scheduleAll(applicationContext)
        setContent {
            ScrmoodsTheme { App() }
        }
    }
}

enum class Tab(val label: String) { CHECKIN("Journal"), CALENDAR("Calendar"), GRAPH("Trends"), DATA("Data") }

private fun Tab.icon(): ImageVector = when (this) {
    Tab.CHECKIN -> Icons.Filled.CheckCircle
    Tab.CALENDAR -> Icons.Filled.DateRange
    Tab.GRAPH -> BarChartIcon
    Tab.DATA -> Icons.AutoMirrored.Filled.List
}

/** Which point editor dialog is open: a new point for [sectionId], or editing [existing]. */
private data class EditorRequest(val sectionId: String, val existing: TrackingPoint?)

@Composable
fun App() {
    var tab by rememberSaveable { mutableStateOf(Tab.CHECKIN) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var manage by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    val context = LocalContext.current

    // Asked the first time you set a reminder (Android 13+ needs permission to show notifications).
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(
            context, "Notifications are off, so reminders can't appear. You can turn them on in Settings → Apps → scrmoods.",
            Toast.LENGTH_LONG,
        ).show()
    }
    fun askForNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!Reminders.canNotify(context)) {
            Toast.makeText(context, "Notifications are off for scrmoods. Turn them on in Settings to get reminders.", Toast.LENGTH_LONG).show()
        }
    }

    BackHandler(enabled = manage) { manage = false }
    BackHandler(enabled = !manage && tab != Tab.CHECKIN) { tab = Tab.CHECKIN }

    Scaffold(
        topBar = { TopBar(onSettings = { manage = true }) },
        bottomBar = {
            if (!manage) Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
                NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                    for (t in Tab.entries) {
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon(), t.label) },
                            label = { Text(t.label, fontWeight = if (tab == t) FontWeight.Medium else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent,
                                selectedTextColor = Accent,
                                unselectedIconColor = InkSoft,
                                unselectedTextColor = InkSoft,
                                indicatorColor = AccentWash,
                            ),
                        )
                    }
                }
            }
        },
        containerColor = Paper,
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (manage) {
                ManageScreen(
                    onBack = { manage = false },
                    onAdd = { editor = EditorRequest(it, null) },
                    onEdit = { editor = EditorRequest(it.sectionId, it) },
                )
            } else when (tab) {
                Tab.CHECKIN -> CheckInScreen(
                    date = date,
                    onDateChange = { date = if (it > LocalDate.now()) LocalDate.now() else it },
                    onAddPoint = { editor = EditorRequest(it, null) },
                    onManage = { manage = true },
                )
                Tab.CALENDAR -> CalendarScreen(date) { date = it; tab = Tab.CHECKIN }
                Tab.GRAPH -> GraphScreen()
                Tab.DATA -> DataScreen(onManage = { manage = true })
            }
        }
    }

    editor?.let { req ->
        PointEditorDialog(
            existing = req.existing,
            defaultSection = req.sectionId,
            onDismiss = { editor = null },
            onSave = { p ->
                if (req.existing == null) Repo.addPoint(p) else Repo.editPoint(p)
                editor = null
                if (p.reminders.isNotEmpty()) askForNotificationsIfNeeded()
            },
        )
    }

    Repo.loadError?.let { msg ->
        AlertDialog(
            onDismissRequest = { Repo.loadError = null },
            containerColor = Page,
            title = { Text("Problem loading data") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { Repo.loadError = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun TopBar(onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Paper).height(56.dp).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "scrmoods",
            color = Accent, fontSize = 21.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, "Tracking points", tint = InkSoft)
        }
    }
}
