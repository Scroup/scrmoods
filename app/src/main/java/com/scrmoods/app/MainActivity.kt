package com.scrmoods.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(applicationContext)
        setContent {
            ScrmoodsTheme { App() }
        }
    }
}

enum class Tab(val label: String) { CHECKIN("Check-in"), CALENDAR("Calendar"), GRAPH("Graph"), DATA("Data") }

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

    BackHandler(enabled = manage) { manage = false }
    BackHandler(enabled = !manage && tab != Tab.CHECKIN) { tab = Tab.CHECKIN }

    Scaffold(
        topBar = { TopBar(onSettings = { manage = true }) },
        bottomBar = {
            if (!manage) NavigationBar(containerColor = Purple) {
                for (t in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon(), t.label) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            unselectedIconColor = Color.White.copy(alpha = 0.75f),
                            unselectedTextColor = Color.White.copy(alpha = 0.75f),
                            indicatorColor = Teal.copy(alpha = 0.55f),
                        ),
                    )
                }
            }
        },
        containerColor = Color.White,
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
            },
        )
    }

    Repo.loadError?.let { msg ->
        AlertDialog(
            onDismissRequest = { Repo.loadError = null },
            title = { Text("Problem loading data") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { Repo.loadError = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun TopBar(onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Purple).height(64.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(BarChartIcon, null, tint = Purple, modifier = Modifier.size(26.dp))
        }
        Box(Modifier.width(16.dp))
        Text(
            "scrmoods",
            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, "Tracking points", tint = Color.White)
        }
    }
}
