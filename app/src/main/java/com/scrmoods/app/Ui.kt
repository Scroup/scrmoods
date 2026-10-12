package com.scrmoods.app

import android.content.Context
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

// ---------- themes: warm, soft palettes. Paper is the background, Page the lighter cards on it ----------
data class Palette(
    val id: String,
    val name: String,
    val paper: Color,
    val page: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val rule: Color,
    val accent: Color,
    val accentWash: Color,
    /** Text and icons drawn on the accent colour. */
    val onAccent: Color = Color.White,
    /** Dark background: the status bar gets light icons. */
    val dark: Boolean = false,
)

object Themes {
    /** Eight distinct, soft palettes; each has its own hue and a brighter accent. */
    val all = listOf(
        Palette("rosewood", "Rosewood", Color(0xFFF3E1DC), Color(0xFFFFF8F5), Color(0xFF3B2A25), Color(0xFF8A6B63),
            Color(0xFFC2A49B), Color(0xFFEBCFC6), Color(0xFFC0584A), Color(0xFFF9DCD3)),
        Palette("peach", "Peach", Color(0xFFFCE3CF), Color(0xFFFFF8F1), Color(0xFF43291C), Color(0xFF93684F),
            Color(0xFFCFA588), Color(0xFFF3D0B5), Color(0xFFDB6E35), Color(0xFFFFE0CB)),
        Palette("butter", "Butter", Color(0xFFFAEDC4), Color(0xFFFFFBEC), Color(0xFF3F3215), Color(0xFF85714A),
            Color(0xFFC4AE7A), Color(0xFFEEDDA8), Color(0xFFB57E0E), Color(0xFFFBEAB6)),
        Palette("matcha", "Matcha", Color(0xFFE3EDD8), Color(0xFFF9FCF4), Color(0xFF26331F), Color(0xFF5F7351),
            Color(0xFFA2B393), Color(0xFFCFDDBF), Color(0xFF5C8A3E), Color(0xFFDDEDCC)),
        Palette("lavender", "Lavender", Color(0xFFECE3F4), Color(0xFFFCF9FF), Color(0xFF33263F), Color(0xFF77648A),
            Color(0xFFB4A5C4), Color(0xFFDED0EC), Color(0xFF8A5BB8), Color(0xFFEBDCF7)),
        Palette("berry", "Berry", Color(0xFFF7DFE8), Color(0xFFFFF7FA), Color(0xFF3D2030), Color(0xFF8F5F74),
            Color(0xFFC99AAD), Color(0xFFEFC9D7), Color(0xFFC24A7C), Color(0xFFFBD9E6)),
        Palette("sepia", "Sepia", Color(0xFFEADCC6), Color(0xFFFAF4EA), Color(0xFF4A3428), Color(0xFF85695A),
            Color(0xFFBCA48C), Color(0xFFDECDB4), Color(0xFF8E5A3C), Color(0xFFEFDCC5)),
        Palette("night", "Night", Color(0xFF221A17), Color(0xFF2E2420), Color(0xFFF1E4DC), Color(0xFFBCA79C),
            Color(0xFF7E6B62), Color(0xFF44362F), Color(0xFFE39A7E), Color(0xFF4A3129),
            onAccent = Color(0xFF2A1C17), dark = true),
    )

    var current by mutableStateOf(all[0])
        private set

    private const val PREFS = "look"

    /** Quote of the day at the top of the journal (Settings switch). */
    var showQuote by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // themes from earlier versions map to their closest new one
        val saved = when (val id = prefs.getString("theme", null)) {
            "honey" -> "butter"; "sage" -> "matcha"; "cocoa" -> "sepia"; "blush" -> "berry"; else -> id
        }
        current = all.firstOrNull { it.id == saved } ?: all[0]
        showQuote = prefs.getBoolean("quote", true)
    }

    fun setShowQuote(context: Context, show: Boolean) {
        showQuote = show
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("quote", show).apply()
    }

    fun select(context: Context, p: Palette) {
        current = p
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("theme", p.id).apply()
    }
}

// Colour names used across the app; they follow the chosen theme.
val Paper: Color get() = Themes.current.paper           // screen background
val Page: Color get() = Themes.current.page             // cards, note page, dialogs
val Ink: Color get() = Themes.current.ink               // main text
val InkSoft: Color get() = Themes.current.inkSoft       // secondary text
val InkFaint: Color get() = Themes.current.inkFaint     // hints, disabled
val Rule: Color get() = Themes.current.rule             // hairlines, outlines, ruled lines
val Accent: Color get() = Themes.current.accent         // selections, headings, links
val AccentWash: Color get() = Themes.current.accentWash // light accent tint
val OnAccent: Color get() = Themes.current.onAccent     // text/icons on the accent colour
val Danger = Color(0xFFA8463F)

// Old names still used by some screens.
val Purple: Color get() = Accent
val Teal: Color get() = Accent
val LinkBlue: Color get() = Accent
val TextDark: Color get() = Ink
val TextGray: Color get() = InkSoft
val Border: Color get() = Rule

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
            onPrimary = OnAccent,
            onBackground = Ink,
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
    idleColor: Color = Paper,
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
                            .background(if (isSel) Accent else idleColor)
                            .border(1.dp, if (isSel) Accent else Rule, shape)
                            .clickable { onSelect(if (isSel) null else index) }
                            .padding(horizontal = 5.dp, vertical = 4.dp), // keeps text clear of the rounded edges
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (isSel) OnAccent else Ink,
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
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    horizontal: androidx.compose.ui.unit.Dp = 20.dp,
    top: androidx.compose.ui.unit.Dp = 28.dp,
) {
    Row(
        modifier.fillMaxWidth().padding(start = horizontal, end = horizontal, top = top, bottom = 2.dp),
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
