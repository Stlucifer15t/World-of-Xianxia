package com.immortalworld.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.immortalworld.R
import com.immortalworld.core.GameSave
import kotlin.math.cos
import kotlin.math.sin

internal val RecordFont = FontFamily(Font(R.font.noto_sans))
internal val ScrollFont = FontFamily(Font(R.font.noto_serif))
internal val Jade = Color(0xFF9CD5BD)
internal val Gold = Color(0xFFDEC18A)
internal val Ink = Color(0xFF0C1919)

/** Static vector ornament: no timer, asset decode, animation or game-state mutation. */
@Composable
internal fun FormationSeal(modifier: Modifier = Modifier, progress: Float? = null) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val radius = size.minDimension * .43f
        drawCircle(Gold.copy(alpha = .24f), radius, style = Stroke(1.dp.toPx()))
        drawCircle(Jade.copy(alpha = .24f), radius * .72f, style = Stroke(1.dp.toPx()))
        for (i in 0 until 8) {
            val angle = i * Math.PI / 4
            fun point(r: Float, a: Double = angle) = center + Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat())
            drawLine(Gold.copy(alpha = .5f), point(radius * .87f), point(radius * 1.08f), 2.dp.toPx())
            drawLine(Jade.copy(alpha = .16f), point(radius * .72f), point(radius * .72f, angle + Math.PI * 3 / 4), 1.dp.toPx())
        }
        progress?.let {
            val r = radius * .91f
            drawArc(Gold, -90f, 360f * it.coerceIn(0f, 1f), false,
                topLeft = center - Offset(r, r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(3.dp.toPx()))
        }
        drawCircle(Jade.copy(alpha = .8f), radius * .08f)
    }
}

@Composable
internal fun SectionCard(title: String, summary: String, mark: String, tag: String = "", onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Gold.copy(alpha = .22f)),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = Jade.copy(alpha = .09f), shape = RoundedCornerShape(10.dp)) {
                Text(mark, Modifier.padding(12.dp), color = Gold, fontSize = 23.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", color = Jade, fontSize = 25.sp)
        }
    }
}

@Composable
internal fun RecordSection(title: String, summary: String, mark: String, initiallyOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedCard(onClick = { open = !open }, modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
            border = BorderStroke(1.dp, Gold.copy(alpha = .25f))) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(mark, color = Gold, fontSize = 24.sp)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (open) "−" else "+", color = Jade, fontSize = 22.sp)
            }
        }
        if (open) Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
internal fun RealmTablet(save: GameSave) {
    val p = save.player; val c = p.cultivation
    val progress = (c.progress / 100).toFloat().coerceIn(0f, 1f)
    Panel {
        Text("INNER WORLD · CULTIVATION RECORD", color = Gold, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FormationSeal(Modifier.size(76.dp), progress)
            Column(Modifier.weight(1f)) {
                Text(save.realms.first { it.id == c.realmId }.name, fontFamily = ScrollFont, style = MaterialTheme.typography.headlineSmall)
                Text("Stage ${c.substage}", color = Gold)
                Text(c.method, style = MaterialTheme.typography.bodySmall)
            }
        }
        Detail("Breakthrough progress", "${"%.1f".format(c.progress)}%")
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Breakthrough progress" }, color = Gold)
        Detail("Qi reserves", "${p.energy} / ${p.maxEnergy}")
        LinearProgressIndicator(progress = { if (p.maxEnergy > 0) p.energy.toFloat() / p.maxEnergy else 0f }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Qi reserves" })
        Detail("Spiritual root", p.spiritualRoot)
        Text(if (c.progress >= 100) "Foundation complete. A breakthrough is your choice." else "Refine qi to complete this stage. Breakthroughs are never automatic.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
