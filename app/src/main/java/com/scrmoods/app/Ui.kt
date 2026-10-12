package com.scrmoods.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- palette: cocoa ink on blush paper, dusty rosewood accent ----------
val Paper = Color(0xFFF7F0EC)      // screen background
val Page = Color(0xFFFDF9F7)       // raised surfaces: chips, note page, dialogs
val Ink = Color(0xFF3B2A25)        // main text
val InkSoft = Color(0xFF8A736B)    // secondary text
val InkFaint = Color(0xFFBBA79F)   // hints, disabled
val Rule = Color(0xFFEADCD5)       // hairlines, outlines, ruled lines
val Accent = Color(0xFFA0594F)     // rosewood: selections, headings, links
val AccentWash = Color(0xFFF1DFD9) // light accent tint
val Danger = Color(0xFFA8463F)

// Old names still used by some screens.
val Purple = Accent
val Teal = Accent
val LinkBlue = Accent
val TextDark = Ink
val TextGray = InkSoft
val Border = Rule

// ---------- type: Lora, a calligraphic book serif (SIL Open Font License) ----------
val Lora = FontFamily(
    Font(R.font.lora_regular, FontWeight.Normal),
    Font(R.font.lora_medium, FontWeight.Medium),
    Font(R.font.lora_semibold, FontWeight.SemiBold),
    Font(R.font.lora_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.lora_medium_italic, FontWeight.Medium, FontStyle.Italic),
)

private fun Typography.withFamily(f: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = f)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

@Composable
fun ScrmoodsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            primaryContainer = AccentWash,
            secondary = Accent,
            secondaryContainer = AccentWash,
            background = Paper,
            surface = Page,
            surfaceContainerHigh = Page,
            surfaceContainer = Page,
            onSurface = Ink,
            onSurfaceVariant = InkSoft,
            outline = Rule,
        ),
        typography = Typography().withFamily(Lora),
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

/**
 * A row of rounded answer pills, e.g. "Not at all · Somewhat · Fairly · Very".
 * Labels always stay on one line: if any label doesn't fit, the text of the whole group shrinks
 * a little (down to [minFont]) so every pill keeps the same size.
 * Tapping the selected pill again clears the answer. Long lists wrap onto rows of up to 5.
 */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Int = 44,
    maxFont: Float = 14f,
    minFont: Float = 10.5f,
) {
    var fontSize by remember(options, maxFont) { mutableStateOf(maxFont) }
    var settled by remember(options, maxFont) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in options.withIndex().chunked(5)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((index, label) in row) {
                    val isSel = selected == index
                    val shape = RoundedCornerShape(50)
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = minHeight.dp)
                            .clip(shape)
                            .background(if (isSel) Accent else Page)
                            .border(1.dp, if (isSel) Accent else Rule, shape)
                            .clickable { onSelect(if (isSel) null else index) }
                            .padding(horizontal = 5.dp, vertical = 4.dp), // keeps text clear of the rounded edges
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (isSel) Color.White else Ink,
                            fontSize = fontSize.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            onTextLayout = { r ->
                                if (r.didOverflowWidth && fontSize > minFont) fontSize = (fontSize - 0.5f).coerceAtLeast(minFont)
                                else settled = true
                            },
                            // Hidden until the size settles, so the shrinking never flickers on screen.
                            modifier = Modifier.drawWithContent { if (settled) drawContent() },
                        )
                    }
                }
            }
        }
    }
}

/** Italic section title followed by a hairline, like a chapter heading in a notebook. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 19.sp,
            lineHeight = 24.sp,
            fontStyle = FontStyle.Italic,
            color = Accent,
        )
        Box(Modifier.weight(1f).padding(start = 14.dp).height(1.dp).background(Rule))
    }
}

/** Quiet text action in italic violet ink, e.g. "+ Add custom tracking point". */
@Composable
fun LinkButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        color = Accent,
        fontSize = 15.sp,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

/** Small round outlined button (date arrows, slider − / +). */
@Composable
fun RoundButton(enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Page)
            .border(1.dp, Rule, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun journalFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Rule,
    focusedLabelColor = Accent,
    cursorColor = Accent,
    focusedContainerColor = Page,
    unfocusedContainerColor = Page,
)

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
        containerColor = Page,
        shape = RoundedCornerShape(22.dp),
        title = { Text(title, fontWeight = FontWeight.Medium) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = if (label.isNotEmpty()) ({ Text(label) }) else null,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 4,
                colors = journalFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(confirmText, color = Accent, fontWeight = FontWeight.Medium) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = InkSoft) } },
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
        containerColor = Page,
        shape = RoundedCornerShape(22.dp),
        title = { Text(title, fontWeight = FontWeight.Medium) },
        text = { Text(message, color = InkSoft, lineHeight = 22.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText, color = Accent, fontWeight = FontWeight.Medium) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = InkSoft) } },
    )
}
