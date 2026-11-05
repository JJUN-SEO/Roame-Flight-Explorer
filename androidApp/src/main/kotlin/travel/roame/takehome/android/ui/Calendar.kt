package travel.roame.takehome.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import travel.roame.takehome.android.R
import travel.roame.takehome.presentation.CalendarDay
import travel.roame.takehome.presentation.DateCalendar
import travel.roame.takehome.presentation.DateSelection
import travel.roame.takehome.presentation.DayMark
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.ExplorerStore

/**
 * The date range calendar (UI_SPEC §6), drawn the same as iOS's DateRangeCalendar. Which days show, which can be picked,
 * how they're marked and what a tap does all come from the shared DateCalendar (K1–K5, D41). [awaitingStart] holds a
 * start whose end hasn't been picked yet; the caller clears it with "Any dates". Each tap applies at once.
 */
@Composable
fun DateRangeCalendar(state: ExplorerState, store: ExplorerStore, awaitingStart: MutableState<String?>, modifier: Modifier = Modifier) {
    val min = state.options.minDate ?: return
    val max = state.options.maxDate ?: return
    val selection = awaitingStart.value?.let { DateSelection(it, null, awaitingEnd = true) } ?: DateCalendar.selectionOf(state.filters)
    val months = DateCalendar.months(min, max, selection)
    Column(modifier) {
        WeekdayHeader()
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            items(months, key = { it.year * 100 + it.month }) { month ->
                Text(
                    monthTitle(month.year, month.month),
                    Modifier.padding(top = 20.dp, bottom = 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                for (week in month.weeks) {
                    Row(Modifier.fillMaxWidth()) {
                        for (day in week.days) {
                            DayCell(day, Modifier.weight(1f)) {
                                val next = DateCalendar.tap(selection, day.iso, min, max)
                                awaitingStart.value = next.startIso.takeIf { next.awaitingEnd }
                                store.setDateRange(next.startIso, next.endIso)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** S M T W T F S, Sunday first like the weeks (K1). */
@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clearAndSetSemantics {}) {
        for (letter in listOf("S", "M", "T", "W", "T", "F", "S")) {
            Text(
                letter,
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/**
 * K4: a pink circle on the range's ends (or a lone start), and a light pink band joining them. The neighbouring months'
 * days stay empty.
 */
@Composable
private fun DayCell(day: CalendarDay, modifier: Modifier, onTap: () -> Unit) {
    Box(modifier.height(44.dp)) {
        if (!day.inMonth) return@Box
        val band = MaterialTheme.colorScheme.primaryContainer
        // The band runs under the whole cell between the ends, and under the inner half of each end.
        when (day.mark) {
            DayMark.Between -> Box(Modifier.fillMaxWidth().fillMaxHeight().padding(vertical = 2.dp).background(band))
            DayMark.Start -> Row(Modifier.fillMaxHeight().padding(vertical = 2.dp)) {
                Box(Modifier.weight(1f))
                Box(Modifier.weight(1f).fillMaxHeight().background(band))
            }
            DayMark.End -> Row(Modifier.fillMaxHeight().padding(vertical = 2.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight().background(band))
                Box(Modifier.weight(1f))
            }
            else -> Unit
        }
        val ends = day.mark == DayMark.Start || day.mark == DayMark.End || day.mark == DayMark.Single
        val description = when (day.mark) {
            DayMark.Start -> stringResource(R.string.calendar_start, cardDate(day.iso))
            DayMark.End -> stringResource(R.string.calendar_end, cardDate(day.iso))
            DayMark.Single -> stringResource(R.string.calendar_single, cardDate(day.iso))
            DayMark.Between -> stringResource(R.string.calendar_between, cardDate(day.iso))
            else -> cardDate(day.iso)
        }
        Box(
            Modifier.align(Alignment.Center).size(40.dp)
                .clip(CircleShape)
                .background(if (ends) MaterialTheme.colorScheme.primary else Color.Transparent)
                .then(if (day.isSelectable) Modifier.clickable(onClick = onTap) else Modifier)
                .clearAndSetSemantics {
                    contentDescription = description
                    if (day.isSelectable) role = Role.Button
                    selected = ends
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (ends) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    ends -> MaterialTheme.colorScheme.onPrimary
                    !day.isSelectable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
