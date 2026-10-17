package com.arianna.app.ui.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arianna.app.ui.components.SheetTitle
import com.arianna.app.ui.theme.Palette
import com.arianna.app.util.CalendarStats
import com.arianna.app.util.Dates
import java.time.YearMonth
import java.time.format.TextStyle

/** Días del año, racha actual, mejor racha y un gráfico de barras de los últimos meses. */
@Composable
fun CalendarStatsCard(stats: CalendarStats) {
    SheetTitle("Estadísticas")
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("En ${stats.year}", stats.daysThisYear, Modifier.weight(1f))
        StatTile("Racha actual", stats.currentStreak, Modifier.weight(1f))
        StatTile("Mejor racha", stats.bestStreak, Modifier.weight(1f))
    }
    Spacer(Modifier.height(18.dp))
    Text("DÍAS POR MES", style = MaterialTheme.typography.labelMedium, color = Palette.InkSoft)
    Spacer(Modifier.height(10.dp))
    MonthBars(stats)
}

@Composable
private fun StatTile(label: String, days: Int, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.LavenderSoft)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$days", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        Text(if (days == 1) "día" else "días", fontSize = 12.sp, color = Palette.InkSoft)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = Palette.Violet, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MonthBars(stats: CalendarStats) {
    val max = stats.maxMonth.coerceAtLeast(1)
    val current = YearMonth.now()
    Row(
        Modifier
            .fillMaxWidth()
            .height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        stats.months.forEach { (month, count) ->
            val name = month.month.getDisplayName(TextStyle.SHORT_STANDALONE, Dates.locale)
                .trimEnd('.')
                .replaceFirstChar { it.uppercase() }
            val isCurrent = month == current
            Column(
                Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = "${Dates.monthYear(month)}: $count días" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text("$count", fontSize = 12.sp, color = Palette.Ink, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((96f * count / max).coerceAtLeast(3f).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (isCurrent) Palette.Violet else Palette.Lavender),
                )
                Spacer(Modifier.height(6.dp))
                Text(name, fontSize = 12.sp, color = if (isCurrent) Palette.Violet else Palette.InkSoft)
            }
        }
    }
}
