package travel.roame.takehome.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import travel.roame.takehome.android.R
import travel.roame.takehome.domain.PointsFormat
import travel.roame.takehome.model.ActiveFilter
import travel.roame.takehome.model.FilterKind
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.ExplorerStore

/** One chip per filter, filled when active, with ✕ to clear it on its own (C2, C3, UI_SPEC §1). */
@Composable
fun FilterBar(state: ExplorerState, store: ExplorerStore, onOpen: (FilterKind) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (kind in FilterKind.entries) {
            val active = state.activeFilters.firstOrNull { it.kind == kind }
            val name = kindName(kind)
            val label = active?.let { chipValue(it, state) } ?: defaultChipLabel(kind, state)
            val description = if (active != null) stringResource(R.string.chip_active, name, label) else name
            FilterChip(
                selected = active != null,
                onClick = { onOpen(kind) },
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.semantics { contentDescription = description },
                trailingIcon = if (active != null) {
                    {
                        val clear = stringResource(R.string.clear_filter, name)
                        Text(
                            "✕",
                            Modifier.clickable { store.clearFilter(kind) }.padding(4.dp)
                                .semantics { contentDescription = clear },
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

/** "238 flights · 2 filters" and Reset, which is hidden when there's nothing to reset (C1, C2, C4). [trailing] goes at the end. */
@Composable
fun ResultSummary(state: ExplorerState, onReset: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        val flights = pluralStringResource(R.plurals.result_count, state.resultCount, groupedCount(state.resultCount))
        val filters = state.activeFilters.size
        Text(
            if (filters == 0) flights else "$flights · ${pluralStringResource(R.plurals.filter_count, filters, filters)}",
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.hasAnythingToReset) TextButton(onClick = onReset) { Text(stringResource(R.string.reset)) }
        trailing()
    }
}

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

/** Dates show the data's range by default, so the starting state is visible (F2, FILT-10). */
@Composable
private fun defaultChipLabel(kind: FilterKind, state: ExplorerState): String =
    if (kind == FilterKind.Dates) dateRange(state.options.minDate, state.options.maxDate) else kindName(kind)

@Composable
private fun dateRange(start: String?, end: String?): String =
    if (start == null || end == null) kindName(FilterKind.Dates)
    else stringResource(R.string.date_range, chipDate(start), chipDate(end))
