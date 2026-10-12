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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable
fun DataScreen(onManage: () -> Unit) {
    val context = LocalContext.current
    val data = Repo.data
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/xml")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { Repo.export(it) }
                Toast.makeText(context, "Exported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        SectionHeader("Your journal")
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
            val logged = data.entries.size
            val first = data.entries.keys.minOrNull()
            Text("$logged ${if (logged == 1) "day" else "days"} written", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Ink)
            if (first != null) Text("since $first", fontSize = 16.sp, fontStyle = FontStyle.Italic, color = InkSoft)
            Text("${data.points.count { !it.archived }} tracking points in use", fontSize = 16.sp, color = Ink, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                "Everything is stored in a single XML file on this phone:",
                fontSize = 14.sp, color = TextGray,
            )
            Text(Repo.filePath, fontSize = 12.sp, color = InkFaint, fontFamily = FontFamily.Monospace)
            Text(
                "Export it to save a backup or to open it on your computer for statistics. " +
                    "Each answer is saved with its number and its readable text, e.g. " +
                    "<value point=\"outlook\" text=\"Positive\">2</value>.",
                fontSize = 14.sp, color = TextGray, modifier = Modifier.padding(top = 6.dp),
            )
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Button(
                onClick = { exportLauncher.launch("scrmoods-${LocalDate.now()}.xml") },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export data (XML)") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("text/xml", "application/xml", "text/plain", "*/*")) },
                border = BorderStroke(1.dp, Rule),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import data from file…", color = Accent) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onManage, border = BorderStroke(1.dp, Rule), contentPadding = PaddingValues(vertical = 14.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Manage tracking points", color = Accent)
            }
        }

        Text(
            "Tip: export regularly. Uninstalling the app deletes its data on the phone.",
            fontSize = 14.sp, fontStyle = FontStyle.Italic, color = InkSoft, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(24.dp))
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = "Replace all data?",
            message = "Importing replaces everything in the app (tracking points and all days) with the contents of the file. " +
                "Export first if you want to keep a copy of the current data.",
            confirmText = "Import",
            onDismiss = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                try {
                    context.contentResolver.openInputStream(uri)?.use { Repo.import(it) }
                    Toast.makeText(context, "Imported ${Repo.data.entries.size} days", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
        )
    }
}
