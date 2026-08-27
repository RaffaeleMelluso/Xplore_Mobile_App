package com.example.progmob_progetto_melluso.ui.trend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.progmob_progetto_melluso.trend.TrendPeriod
import com.example.progmob_progetto_melluso.trend.TrendRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = TrendUiState()
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

    /** Votes a POI up/down through the shared vote engine. */
    fun votePoi(poiId: Long, voteValue: Int) {
        viewModelScope.launch { trendRepository.votePoi(poiId, voteValue) }
    }

    companion object {
        /** Delay before the upstream flows stop after the UI stops collecting. */
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
