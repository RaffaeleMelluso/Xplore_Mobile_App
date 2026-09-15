package com.example.progmob_progetto_melluso.ui.trend

import androidx.activity.compose.BackHandler
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
import com.example.progmob_progetto_melluso.ui.common.PoiImage

/** Label of a period shown in the filter chips. */
private val TrendPeriod.labelRes: Int
    get() = when (this) {
        TrendPeriod.DAY -> R.string.trends_period_day
        TrendPeriod.WEEK -> R.string.trends_period_week
        TrendPeriod.MONTH -> R.string.trends_period_month
        TrendPeriod.YEAR -> R.string.trends_period_year
    }

/**
 * Trends screen (the recommendations requirement): what other travelers are voting, ranked per
 * period (day/week/month/year) and per scope (single attractions or cities).
 *
 * How it works: the screen only collects [TrendUiState] and emits the user's
 * filter intents. The rankings are computed from the real community votes by
 * [TrendViewModel], so a vote cast where the visit is REAL (map card, journal
 * visit) re-ranks this page live. There is no voting here ON PURPOSE: from
 * the ranking a user could vote places they never visited.
 */
@Composable
fun TrendScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: TrendViewModel = viewModel(
        factory = remember(context) { TrendViewModelFactory(AppContainer.trendRepository(context)) }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val poiStats by viewModel.poiStats.collectAsStateWithLifecycle()

    // Which detail screen is open, resolved from the selection in the state:
    // an attraction (from the ranking or from a city) or a whole city.
    val selectedPoi = uiState.poiTrends.firstOrNull { it.poiId == uiState.selectedPoiId }
    val selectedCity = uiState.cityTrends.firstOrNull { it.cityName == uiState.selectedCityName }

    // System back closes the open detail and returns to where it was opened
    // from (city -> ranking, POI -> its city/ranking). Takes priority over the
    // Home tab-history handler while a detail is open.
    val detailOpen = selectedPoi != null || selectedCity != null
    if (detailOpen) {
        BackHandler {
            if (selectedPoi != null) viewModel.closePoiDetail() else viewModel.closeDetail()
        }
    }

    when {
        selectedPoi != null -> PoiTrendDetail(
            item = selectedPoi,
            stats = poiStats,
            periodLabel = stringResource(uiState.period.labelRes),
            onBack = viewModel::closePoiDetail
        )
        selectedCity != null -> CityTrendDetail(
            city = selectedCity,
            pois = uiState.poiTrends.filter { it.city == selectedCity.cityName },
            periodLabel = stringResource(uiState.period.labelRes),
            onBack = viewModel::closeDetail,
            onOpenPoi = viewModel::openPoi
        )
        else -> TrendsRanking(
            uiState = uiState,
            viewModel = viewModel,
            modifier = modifier
        )
    }
}

/**
 * The ranking list: filters on top, then the ranked attractions or cities.
 */
@Composable
private fun TrendsRanking(
    uiState: TrendUiState,
    viewModel: TrendViewModel,
    modifier: Modifier = Modifier
) {
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
                    onOpenPoi = viewModel::openPoi
                )
                TrendScope.CITIES -> CityTrendList(
                    items = uiState.cityTrends,
                    onOpenCity = viewModel::openCity
                )
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

/** Scrollable list of the ranked attractions (read-only ranking, tappable). */
@Composable
private fun AttractionTrendList(
    items: List<PoiTrendItem>,
    onOpenPoi: (Long) -> Unit,
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
                onClick = { onOpenPoi(item.poiId) }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * One ranked attraction row: rank, photo, name/category/city and the score.
 *
 * Read-only ON PURPOSE: from here a user could vote places they never
 * visited, so voting happens only where the visit is real (the map POI card
 * when you are near the place, and the journal visits). The score shown is
 * the same community score every other screen displays.
 */
@Composable
private fun AttractionTrendRow(
    item: PoiTrendItem,
    rank: Int,
    votesLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RankBadge(rank)
            // The photo slot is ALWAYS present (a themed placeholder when the
            // POI has no photo or the image cannot be fetched), so every title
            // in the ranking starts at the same x position — no misaligned
            // rows between POIs with and without a picture.
            Spacer(Modifier.width(8.dp))
            PoiImage(
                imageResName = item.imageResName,
                imageUri = item.imageUri,
                contentDescription = item.title,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = listOfNotNull(item.category, item.city).joinToString(" · "),
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
                    text = votesLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Scrollable list of the ranked cities (read-only ranking, tappable). */
@Composable
private fun CityTrendList(
    items: List<CityTrendItem>,
    onOpenCity: (String) -> Unit,
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
                votesLabel = stringResource(R.string.trends_votes_count, item.windowVotes),
                onClick = { onOpenCity(item.cityName) }
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
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
