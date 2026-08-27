package com.example.progmob_progetto_melluso.ui.trend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.progmob_progetto_melluso.R
import com.example.progmob_progetto_melluso.core.AppContainer
import com.example.progmob_progetto_melluso.trend.CityTrendItem
import com.example.progmob_progetto_melluso.trend.PoiTrendItem
import com.example.progmob_progetto_melluso.trend.TrendPeriod
import com.example.progmob_progetto_melluso.ui.common.PoiVoteButtons

/** Label of a period shown in the filter chips. */
private val TrendPeriod.labelRes: Int
    get() = when (this) {
        TrendPeriod.DAY -> R.string.trends_period_day
        TrendPeriod.WEEK -> R.string.trends_period_week
        TrendPeriod.MONTH -> R.string.trends_period_month
        TrendPeriod.YEAR -> R.string.trends_period_year
    }

/**
 * Trends screen (spec §3.4): what other travelers are voting, ranked per
 * period (day/week/month/year) and per scope (single attractions or cities).
 *
 * How it works: the screen only collects [TrendUiState] and emits the user's
 * intents (select a filter, vote a POI). The rankings are computed from the
 * real community votes by [TrendViewModel], so voting from the journal or the
 * map re-ranks this page live, and voting here is the same vote everywhere.
 */
@Composable
fun TrendScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: TrendViewModel = viewModel(
        factory = remember(context) { TrendViewModelFactory(AppContainer.trendRepository(context)) }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.trends_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.trends_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        // Scope filter: single attractions or whole cities.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill(
                label = stringResource(R.string.trends_scope_attractions),
                selected = uiState.scope == TrendScope.ATTRACTIONS,
                onClick = { viewModel.selectScope(TrendScope.ATTRACTIONS) }
            )
            FilterPill(
                label = stringResource(R.string.trends_scope_cities),
                selected = uiState.scope == TrendScope.CITIES,
                onClick = { viewModel.selectScope(TrendScope.CITIES) }
            )
        }
        Spacer(Modifier.height(8.dp))

        // Period filter: votes received in the last day/week/month/year.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendPeriod.entries.forEach { period ->
                FilterPill(
                    label = stringResource(period.labelRes),
                    selected = uiState.period == period,
                    onClick = { viewModel.selectPeriod(period) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            when (uiState.scope) {
                TrendScope.ATTRACTIONS -> AttractionTrendList(
                    items = uiState.poiTrends,
                    onVote = { poiId, voteValue -> viewModel.votePoi(poiId, voteValue) }
                )
                TrendScope.CITIES -> CityTrendList(items = uiState.cityTrends)
            }
        }
    }
}

/**
 * Filter pill matching the app's visual language (same card surfaces and
 * primary fill as the map's raised button), instead of the default Material
 * chips that clash with the travel palette.
 */
@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/** Scrollable list of the ranked attractions, each votable. */
@Composable
private fun AttractionTrendList(
    items: List<PoiTrendItem>,
    onVote: (Long, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        TrendEmptyState(modifier = modifier)
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        itemsIndexed(items, key = { _, item -> item.poiId }) { index, item ->
            AttractionTrendRow(
                item = item,
                rank = index + 1,
                votesLabel = stringResource(R.string.trends_votes_count, item.windowVotes),
                onVote = onVote
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** One ranked attraction row: rank, name/category/city and the vote control. */
@Composable
private fun AttractionTrendRow(
    item: PoiTrendItem,
    rank: Int,
    votesLabel: String,
    onVote: (Long, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RankBadge(rank)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = listOfNotNull(item.category, item.city, votesLabel).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PoiVoteButtons(
                poiId = item.poiId,
                score = item.windowScore.toInt(),
                myVote = item.myVote,
                onVotePoi = onVote
            )
        }
    }
}

/** Scrollable list of the ranked cities. */
@Composable
private fun CityTrendList(
    items: List<CityTrendItem>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        TrendEmptyState(modifier = modifier)
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        itemsIndexed(items, key = { _, item -> item.cityName }) { index, item ->
            CityTrendRow(
                item = item,
                rank = index + 1,
                votesLabel = stringResource(R.string.trends_votes_count, item.windowVotes)
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** One ranked city row: rank, city name and the aggregated score. */
@Composable
private fun CityTrendRow(
    item: CityTrendItem,
    rank: Int,
    votesLabel: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RankBadge(rank)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.cityName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = votesLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.windowScore.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.trends_score_points),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The position of a row in the ranking. */
@Composable
private fun RankBadge(rank: Int) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/** Shown when no votes fell inside the selected period yet. */
@Composable
private fun TrendEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.trends_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
