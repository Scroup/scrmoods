package com.scrmoods.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Purple = Color(0xFF38166B)
val Teal = Color(0xFF5FA7C3)
val LinkBlue = Color(0xFF4F9CBF)
val SectionGray = Color(0xFFF4F4F4)
val Border = Color(0xFFD9DCDF)
val TextGray = Color(0xFF555555)
val TextDark = Color(0xFF222222)

@Composable
fun ScrmoodsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Purple,
            onPrimary = Color.White,
            secondary = Teal,
            background = Color.White,
            surface = Color.White,
        ),
        content = content,
    )
}

/** Simple bar-chart icon (the built-in icon set has none). */
val BarChartIcon: ImageVector by lazy {
    ImageVector.Builder("BarChart", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 11f); lineTo(8f, 11f); lineTo(8f, 20f); lineTo(4f, 20f); close()
            moveTo(10f, 4f); lineTo(14f, 4f); lineTo(14f, 20f); lineTo(10f, 20f); close()
            moveTo(16f, 8f); lineTo(20f, 8f); lineTo(20f, 20f); lineTo(16f, 20f); close()
        }
    }.build()
}

/** A row of joined buttons like "Not at all | Somewhat | Fairly | Very". Tapping the selected one clears it. */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Int = 48,
) {
    // Long option lists wrap onto several rows of up to 5.
    val rows = options.withIndex().chunked(5)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in rows) {
            val shape = RoundedCornerShape(8.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(shape)
                    .border(1.dp, Border, shape)
                    .background(Color.White),
            ) {
                row.forEachIndexed { i, (index, label) ->
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Border))
                    val isSel = selected == index
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = minHeight.dp)
                            .background(if (isSel) Purple else Color.White)
                            .clickable { onSelect(if (isSel) null else index) }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (isSel) Color.White else TextDark,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = if (isSel) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp)) {
        Text(title, fontSize = 21.sp, color = TextDark, modifier = Modifier.padding(horizontal = 16.dp))
        HorizontalDivider(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), color = Color(0xFFE6E6E6))
    }
}

@Composable
fun LinkButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        color = LinkBlue,
        fontSize = 19.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    label: String = "",
    singleLine: Boolean = false,
    confirmText: String = "Save",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = if (label.isNotEmpty()) ({ Text(label) }) else null,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 4,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
