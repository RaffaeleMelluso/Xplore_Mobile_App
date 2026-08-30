package com.example.progmob_progetto_melluso.ui.trend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.progmob_progetto_melluso.trend.PoiStats
import com.example.progmob_progetto_melluso.trend.TrendPeriod
import com.example.progmob_progetto_melluso.trend.TrendRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the trends screen.
 *
 * How it works: it combines the two filter choices (period and scope) into a
 * single [StateFlow]: every change re-subscribes the repository rankings for
 * the selected window, and a new vote anywhere in the app re-emits the state
 * automatically because the repository flows are reactive. All asynchrony
 * lives in [viewModelScope]; the UI only collects the state and forwards the
 * user's filter/vote intents.
 *
 * @param trendRepository the repository exposing the rankings and the votes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrendViewModel(
    private val trendRepository: TrendRepository
) : ViewModel() {

    // The two filter choices, kept here (not in the repository) because they
    // are per-screen presentation preferences.
    private val selectedPeriod = MutableStateFlow(TrendPeriod.WEEK)
    private val selectedScope = MutableStateFlow(TrendScope.ATTRACTIONS)

    // Which item is opened in the detail screen (attraction id / city name).
    private val selectedPoiId = MutableStateFlow<Long?>(null)
    private val selectedCityName = MutableStateFlow<String?>(null)

    /**
     * Combined trends state: every filter change produces a new snapshot.
     *
     * How it works: both rankings (attractions and cities) are collected in
     * parallel so switching scope is instant; `WhileSubscribed` stops the
     * aggregation flows when the tab is not visible, saving work.
     */
    val uiState: StateFlow<TrendUiState> =
        combine(selectedPeriod, selectedScope) { period, scope -> period to scope }
            .flatMapLatest { (period, scope) ->
                combine(
                    trendRepository.observePoiTrends(period),
                    trendRepository.observeCityTrends(period)
                ) { pois, cities ->
                    TrendUiState(
                        period = period,
                        scope = scope,
                        poiTrends = pois,
                        cityTrends = cities,
                        isLoading = false
                    )
                }
            }
            .combine(selectedPoiId) { state, poiId -> state.copy(selectedPoiId = poiId) }
            .combine(selectedCityName) { state, city -> state.copy(selectedCityName = city) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = TrendUiState()
            )

    /**
     * All-time stats of the opened attraction (score and vote count over ALL
     * votes), reactive: the detail screen shows them while it is open.
     */
    val poiStats: StateFlow<PoiStats?> = selectedPoiId.flatMapLatest { id ->
        if (id == null) flowOf(null) else trendRepository.observePoiStats(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null
    )

    init {
        // Populate the demo POIs and votes on first launch (no-op when done).
        viewModelScope.launch { trendRepository.ensureTrendSeed() }
    }

    /** Selects the aggregation window (day/week/month/year). */
    fun selectPeriod(period: TrendPeriod) {
        selectedPeriod.value = period
    }

    /** Selects the ranking scope (single attractions or cities). */
    fun selectScope(scope: TrendScope) {
        selectedScope.value = scope
    }

    /** Opens the detail screen of an attraction (row tap). */
    fun openPoi(poiId: Long) {
        selectedPoiId.value = poiId
    }

    /** Opens the detail screen of a city (row tap). */
    fun openCity(cityName: String) {
        selectedCityName.value = cityName
    }

    /** Closes the detail screens, back to the ranking list. */
    fun closeDetail() {
        selectedPoiId.value = null
        selectedCityName.value = null
    }

    /** Closes only the attraction detail (returns to the city detail). */
    fun closePoiDetail() {
        selectedPoiId.value = null
    }

    // NOTE: there is intentionally no votePoi here. The trends page is a
    // read-only ranking: voting from the ranking would let users vote places
    // they never visited. Votes happen where the visit is real (map POI card,
    // journal visits) through the shared PoiVoteRepository.

    companion object {
        /** Delay before the upstream flows stop after the UI stops collecting. */
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
