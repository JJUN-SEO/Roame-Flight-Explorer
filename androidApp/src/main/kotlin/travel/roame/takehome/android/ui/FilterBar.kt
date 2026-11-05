package travel.roame.takehome.android.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import travel.roame.takehome.android.R
import travel.roame.takehome.domain.PointsFormat
import travel.roame.takehome.model.ActiveFilter
import travel.roame.takehome.model.FilterKind
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.ExplorerStore

/**
 * The active filters as chips, each with ✕ to clear it on its own (C2, C3), then "Clear all", which clears the filters
 * but keeps the search. Tapping a chip opens the Filters sheet. Nothing shows while no filter is active.
 */
@Composable
fun ActiveFilterBar(state: ExplorerState, store: ExplorerStore, onOpen: () -> Unit) {
    if (state.activeFilters.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (filter in state.activeFilters) {
            val name = kindName(filter.kind)
            val label = chipValue(filter, state)
            AppChip(
                label = label,
                active = true,
                description = stringResource(R.string.chip_active, name, label),
                onClick = onOpen,
                onClear = { store.clearFilter(filter.kind) },
                clearDescription = stringResource(R.string.clear_filter, name),
            )
        }
        TextLink(stringResource(R.string.clear_all), onClick = { state.activeFilters.forEach { store.clearFilter(it.kind) } })
    }
}

/** C1: "238 flights", at the top of the results sheet. */
@Composable
fun resultCountText(state: ExplorerState): String =
    pluralStringResource(R.plurals.result_count, state.resultCount, groupedCount(state.resultCount))

/** The active filters, listed under the filtered-empty message (UI_SPEC §4). */
@Composable
fun ActiveFilterList(state: ExplorerState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        for (filter in state.activeFilters) {
            Text(
                "${kindName(filter.kind)}: ${chipValue(filter, state)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun kindName(kind: FilterKind): String = stringResource(
    when (kind) {
        FilterKind.Dates -> R.string.filter_dates
        FilterKind.Cabin -> R.string.filter_cabin
        FilterKind.MaxPoints -> R.string.filter_points
        FilterKind.Programs -> R.string.filter_programs
    },
)

/** A null date bound shows as the data's own limit (F2). */
@Composable
private fun chipValue(filter: ActiveFilter, state: ExplorerState): String = when (filter) {
    is ActiveFilter.Dates -> dateRange(filter.start ?: state.options.minDate, filter.end ?: state.options.maxDate)
    is ActiveFilter.Cabin -> filter.cabin
    is ActiveFilter.MaxPoints -> stringResource(R.string.points_chip, PointsFormat.compact(filter.points))
    is ActiveFilter.Programs ->
        if (filter.programs.size == 1) filter.programs.single()
        else stringResource(R.string.programs_more, filter.programs.first(), filter.programs.size - 1)
}

@Composable
private fun dateRange(start: String?, end: String?): String =
    if (start == null || end == null) kindName(FilterKind.Dates)
    else stringResource(R.string.date_range, chipDate(start), chipDate(end))
