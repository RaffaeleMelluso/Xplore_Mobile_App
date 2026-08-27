package com.example.progmob_progetto_melluso.ui.trend

import com.example.progmob_progetto_melluso.trend.CityTrendItem
import com.example.progmob_progetto_melluso.trend.PoiTrendItem
import com.example.progmob_progetto_melluso.trend.TrendPeriod

/** What the trends page ranks: single attractions or whole cities. */
enum class TrendScope { ATTRACTIONS, CITIES }

/**
 * Immutable UI state for the trends screen.
 *
 * How it works: the Compose screen only collects this state and never mutates
 * it directly. The selected [period] and [scope] are chosen with the filter
 * chips and drive which ranking ([poiTrends] or [cityTrends]) is shown; both
 * lists are kept up to date so switching scope never flashes a loading state.
 *
 * @property period the selected aggregation window (day/week/month/year).
 * @property scope the selected ranking scope (attractions or cities).
 * @property poiTrends the attractions ranking for [period].
 * @property cityTrends the cities ranking for [period].
 * @property isLoading true until the first emission arrives.
 */
data class TrendUiState(
    val period: TrendPeriod = TrendPeriod.WEEK,
    val scope: TrendScope = TrendScope.ATTRACTIONS,
    val poiTrends: List<PoiTrendItem> = emptyList(),
    val cityTrends: List<CityTrendItem> = emptyList(),
    val isLoading: Boolean = true
)
