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
)

object Themes {
    val all = listOf(
        Palette("rosewood", "Rosewood", Color(0xFFEFE4DF), Color(0xFFFCF8F6), Color(0xFF3B2A25), Color(0xFF86706A),
            Color(0xFFB9A59D), Color(0xFFE3D3CC), Color(0xFFA0594F), Color(0xFFF0DCD5)),
        Palette("sepia", "Sepia", Color(0xFFEADFCF), Color(0xFFFBF6EE), Color(0xFF4F3933), Color(0xFF85695A),
            Color(0xFFBCA893), Color(0xFFE0D2BF), Color(0xFF945B46), Color(0xFFF0E0D0)),
        Palette("honey", "Honey", Color(0xFFF3E3C8), Color(0xFFFFF9EE), Color(0xFF4F3933), Color(0xFF8C6B4E),
            Color(0xFFC9AE86), Color(0xFFEBD8B6), Color(0xFFB0703F), Color(0xFFF8E6CB)),
        Palette("sage", "Sage", Color(0xFFE6E3D3), Color(0xFFFAF8F0), Color(0xFF45382C), Color(0xFF7C7563),
            Color(0xFFB3AE99), Color(0xFFDAD6C2), Color(0xFF6E7A52), Color(0xFFE6E9D6)),
        Palette("cocoa", "Cocoa", Color(0xFFE6D8CE), Color(0xFFF8F1EB), Color(0xFF4F3933), Color(0xFF7E5F52),
            Color(0xFFB39C8E), Color(0xFFDBC8BB), Color(0xFF6C4438), Color(0xFFEBDAD0)),
        Palette("blush", "Blush", Color(0xFFF1E1DA), Color(0xFFFDF7F4), Color(0xFF5A3F33), Color(0xFF957068),
            Color(0xFFC4A69D), Color(0xFFEBD5CC), Color(0xFFB0685C), Color(0xFFF5E1DA)),
    )

    var current by mutableStateOf(all[0])
        private set

    private const val PREFS = "look"

    /** Quote of the day at the top of the journal (Settings switch). */
    var showQuote by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        current = all.firstOrNull { it.id == prefs.getString("theme", null) } ?: all[0]
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
