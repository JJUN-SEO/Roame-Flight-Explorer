package travel.roame.takehome.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import travel.roame.takehome.android.R
import travel.roame.takehome.domain.PointsFormat
import travel.roame.takehome.model.FilterKind
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.ExplorerStore

/**
 * One sheet per filter (UI_SPEC §2.4). Choices apply as they're made, so "Show N flights" shows the
 * real count and just closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(kind: FilterKind, state: ExplorerState, store: ExplorerStore, onDismiss: () -> Unit) {
    // Fully open, so "Show N flights" is on screen without dragging the sheet up.
    // K3: a start whose end hasn't been picked yet. "Any dates" clears it with the filter.
    val awaitingStart = rememberSaveable { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = 16.dp)) {
            // The same header as iOS's sheets: the filter's name, centred (D38).
            Text(kindName(kind), Modifier.fillMaxWidth().padding(bottom = 12.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            when (kind) {
                FilterKind.Dates -> DateRangeCalendar(state, store, awaitingStart, Modifier.height(440.dp))
                FilterKind.Cabin -> CabinBody(state, store)
                FilterKind.MaxPoints -> PointsBody(state, store)
                FilterKind.Programs -> ProgramsBody(state, store)
            }
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextLink(
                    stringResource(
                        when (kind) {
                            FilterKind.Dates -> R.string.any_dates
                            FilterKind.Cabin -> R.string.any_cabin
                            FilterKind.MaxPoints -> R.string.points_any
                            FilterKind.Programs -> R.string.clear
                        },
                    ),
                    onClick = {
                        store.clearFilter(kind)
                        awaitingStart.value = null
                    },
                )
                Spacer(Modifier.weight(1f))
                PrimaryButton(pluralStringResource(R.plurals.show_flights, state.resultCount, groupedCount(state.resultCount)), onDismiss)
            }
        }
    }
}

/** Single choice (F5), in rank order from the data. */
@Composable
private fun CabinBody(state: ExplorerState, store: ExplorerStore) {
    OptionRow(stringResource(R.string.any_cabin), selected = state.filters.cabin == null) { store.setCabin(null) }
    for (cabin in state.options.cabins) {
        OptionRow(cabin, selected = state.filters.cabin == cabin) { store.setCabin(cabin) }
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
        Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.titleMedium,
    )
    Slider(
        modifier = Modifier.padding(horizontal = 16.dp),
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
        OptionRow(program, selected = program in state.filters.programs) { store.toggleProgram(program) }
    }
}
