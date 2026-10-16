package com.scrmoods.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.core.view.WindowCompat
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(PlannerNav.EXTRA, false)) PlannerNav.request++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(applicationContext)
        Themes.init(applicationContext)
        Lang.init(applicationContext)
        GoodNight.init(applicationContext)
        Haptics.init(applicationContext)
        TodoPrefs.init(applicationContext)
        Sparkles.init(applicationContext)
        Intro.init(applicationContext, freshStart = savedInstanceState == null)
        Reminders.ensureChannel(applicationContext)
        Reminders.scheduleAll(applicationContext)
        if (intent?.getBooleanExtra(PlannerNav.EXTRA, false) == true) PlannerNav.request++
        setContent {
            ScrmoodsTheme {
                // Status and navigation bars take the theme's paper colour.
                val bar = Paper.toArgb()
                val dark = Themes.current.dark
                SideEffect {
                    window.statusBarColor = bar
                    window.navigationBarColor = bar
                    // dark icons on light themes, light icons on Night
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    App()
                    WelcomeOverlay()
                    SparkleLayer()
                    GoodNightOverlay()
                }
            }
        }
    }
}

enum class Tab(val label: String) { CHECKIN("Journal"), MEDS("Meds"), CALENDAR("Calendar"), GRAPH("Trends"), DATA("Settings") }

private fun Tab.icon(): ImageVector = when (this) {
    Tab.CHECKIN -> Icons.Filled.CheckCircle
    Tab.CALENDAR -> Icons.Filled.DateRange
    Tab.MEDS -> PillIcon
    Tab.GRAPH -> BarChartIcon
    Tab.DATA -> Icons.Filled.Settings
}

/** Which point editor dialog is open: a new point for [sectionId], or editing [existing]. */
private data class EditorRequest(val sectionId: String, val existing: TrackingPoint?)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun App() {
    var tab by rememberSaveable { mutableStateOf(Tab.CHECKIN) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var manage by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    var quick by rememberSaveable { mutableStateOf<String?>(null) } // the day open in the quick check-in
    val context = LocalContext.current

    // Asked the first time you set a reminder (Android 13+ needs permission to show notifications).
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(
            context, tr("Notifications are off, so reminders can't appear. You can turn them on in Settings → Apps → scrmoods."),
            Toast.LENGTH_LONG,
        ).show()
    }
    fun askForNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!Reminders.canNotify(context)) {
            Toast.makeText(context, tr("Notifications are off for scrmoods. Turn them on in Settings to get reminders."), Toast.LENGTH_LONG).show()
        }
    }

    // the opening animation is over once the last card has risen
    LaunchedEffect(Unit) {
        if (Intro.playing) {
            kotlinx.coroutines.delay(Intro.RISE_START + 20 * Intro.STAGGER + 800)
            Intro.playing = false
        }
    }

    // the medication planner: opened from its line in the journal (a date), or its own tab
    var planner by rememberSaveable { mutableStateOf<String?>(null) }
    val plan = Repo.data.plan
    LaunchedEffect(plan.asTab) { if (!plan.asTab && tab == Tab.MEDS) tab = Tab.CHECKIN }
    LaunchedEffect(PlannerNav.request) {
        if (PlannerNav.request > 0 && plan.enabled) {
            manage = false
            if (plan.asTab) tab = Tab.MEDS else planner = LocalDate.now().toString()
        }
    }
    BackHandler(enabled = planner != null) { planner = null }
    BackHandler(enabled = manage) { manage = false }
    BackHandler(enabled = !manage && tab != Tab.CHECKIN) { tab = Tab.CHECKIN }

    Scaffold(
        topBar = { TopBar() },
        bottomBar = {
            if (!manage) Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
                NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                    for (t in Tab.entries.filter { it != Tab.MEDS || plan.asTab }) {
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { Haptics.tap(); tab = t; planner = null },
                            icon = { Icon(t.icon(), tr(t.label)) },
                            // one line always; a little smaller when there are five tabs
                            label = {
                                Text(
                                    tr(t.label), maxLines = 1, softWrap = false,
                                    fontSize = if (plan.asTab) 11.sp else 12.sp,
                                    fontWeight = if (tab == t) FontWeight.Medium else FontWeight.Normal,
                                )
                            },
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
        contentColor = Ink,
    ) { pad ->
        // the app draws edge to edge (Android 15+): keep content clear of the keyboard as well as the bars
        Box(Modifier.padding(pad).consumeWindowInsets(pad).imePadding().fillMaxSize()) {
            val openPlanner = planner
            if (openPlanner != null && !manage && tab == Tab.CHECKIN) {
                PlannerScreen(openPlanner) { planner = null }
            } else if (manage) {
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
                    onQuick = { quick = date.toString() },
                    onPlanner = { planner = it.toString() },
                )
                Tab.MEDS -> PlannerScreen(onBack = null)
                Tab.CALENDAR -> CalendarScreen(date) { date = it; tab = Tab.CHECKIN }
                Tab.GRAPH -> GraphScreen()
                Tab.DATA -> DataScreen(onManage = { manage = true })
            }
        }
    }

    quick?.let { d -> QuickCheckIn(d) { quick = null } }
    BackHandler(enabled = quick != null) { quick = null }

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
            title = { Text(tr("Problem loading data")) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { Repo.loadError = null }) { Text(tr("OK")) } },
        )
    }
    // writing to disk failed: say so (the next successful save clears it)
    Repo.saveError?.let { msg ->
        AlertDialog(
            onDismissRequest = { Repo.saveError = null },
            containerColor = Page,
            title = { Text(tr("Problem saving data")) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { Repo.saveError = null }) { Text(tr("OK")) } },
        )
    }
}

@Composable
private fun TopBar() {
    Row(
        Modifier.fillMaxWidth().background(Paper).statusBarsPadding().height(56.dp).padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "scrmoods",
            color = Accent, fontSize = 21.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}
