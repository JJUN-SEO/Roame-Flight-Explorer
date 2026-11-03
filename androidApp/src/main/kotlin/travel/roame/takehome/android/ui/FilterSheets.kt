package travel.roame.takehome.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import travel.roame.takehome.android.R
import travel.roame.takehome.domain.PointsFormat
import travel.roame.takehome.model.FilterKind
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.ExplorerStore
import kotlin.math.roundToInt

/**
 * One sheet per filter (UI_SPEC §2.4). Choices apply as they're made, so "Show N flights" shows the
 * real count and just closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(kind: FilterKind, state: ExplorerState, store: ExplorerStore, onDismiss: () -> Unit) {
    // Fully open, so "Show N flights" is on screen without dragging the sheet up.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(kindName(kind), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            when (kind) {
                FilterKind.Dates -> DatesBody(state, store)
                FilterKind.Cabin -> CabinBody(state, store)
                FilterKind.MaxPoints -> PointsBody(state, store)
                FilterKind.Programs -> ProgramsBody(state, store)
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { store.clearFilter(kind) }) {
                    Text(
                        stringResource(
                            when (kind) {
                                FilterKind.Dates -> R.string.any_dates
                                FilterKind.Cabin -> R.string.any_cabin
                                FilterKind.MaxPoints -> R.string.points_any
                                FilterKind.Programs -> R.string.clear
                            },
                        ),
                    )
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) {
                        Text(pluralStringResource(R.plurals.show_flights, state.resultCount, groupedCount(state.resultCount)))
                    }
                }
            }
        }
    }
}

/** Limited to the data's dates; the range picker keeps start before end. Converted with UTC (CLAUDE.md). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatesBody(state: ExplorerState, store: ExplorerStore) {
    val min = state.options.minDate ?: return
    val max = state.options.maxDate ?: return
    val minMillis = isoToUtcMillis(min)
    val maxMillis = isoToUtcMillis(max)
    val picker = rememberDateRangePickerState(
        initialSelectedStartDateMillis = state.filters.dateStart?.let(::isoToUtcMillis),
        initialSelectedEndDateMillis = state.filters.dateEnd?.let(::isoToUtcMillis),
        initialDisplayedMonthMillis = state.filters.dateStart?.let(::isoToUtcMillis) ?: minMillis,
        yearRange = min.take(4).toInt()..max.take(4).toInt(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in minMillis..maxMillis
        },
    )
    val start = picker.selectedStartDateMillis
    val end = picker.selectedEndDateMillis
    LaunchedEffect(start, end) {
        if (start != null) store.setDateRange(utcMillisToIso(start), end?.let(::utcMillisToIso))
    }
    DateRangePicker(state = picker, modifier = Modifier.height(460.dp), title = null)
}

/** Single choice (F5), in rank order from the data. */
@Composable
private fun CabinBody(state: ExplorerState, store: ExplorerStore) {
    OptionRow(stringResource(R.string.any_cabin), selected = state.filters.cabin == null) { store.setCabin(null) }
    for (cabin in state.options.cabins) {
        OptionRow(cabin, selected = state.filters.cabin == cabin) { store.setCabin(cabin) }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** From the data's lowest to highest points in steps of 500; the top means no limit (F4). */
@Composable
private fun PointsBody(state: ExplorerState, store: ExplorerStore) {
    val min = state.options.minPoints ?: return
    val max = state.options.maxPoints ?: return
    // Kept here while dragging, because the store's copy arrives a moment later.
    var value by remember { mutableFloatStateOf((state.filters.maxPoints ?: max).toFloat()) }
    val points = value.roundToInt()
    Text(
        if (points >= max) stringResource(R.string.points_any) else stringResource(R.string.points_up_to, PointsFormat.full(points)),
        style = MaterialTheme.typography.titleMedium,
    )
    Slider(
        value = value,
        onValueChange = {
            val snapped = (min + ((it - min) / 500f).roundToInt() * 500).coerceIn(min, max)
            value = snapped.toFloat()
            store.setMaxPoints(snapped)
        },
        valueRange = min.toFloat()..max.toFloat(),
    )
    LaunchedEffect(state.filters.maxPoints) { if (state.filters.maxPoints == null) value = max.toFloat() }
}

/** Any of the ticked programs (F6); none ticked means all. */
@Composable
private fun ProgramsBody(state: ExplorerState, store: ExplorerStore) {
    for (program in state.options.programs) {
        val checked = program in state.filters.programs
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { store.toggleProgram(program) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = { store.toggleProgram(program) })
            Text(program, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
