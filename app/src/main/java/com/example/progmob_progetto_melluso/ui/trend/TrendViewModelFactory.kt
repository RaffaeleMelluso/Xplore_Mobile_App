package com.example.progmob_progetto_melluso.ui.trend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.progmob_progetto_melluso.trend.TrendRepository

/**
 * Factory that builds [TrendViewModel] with its [TrendRepository] dependency.
 *
 * How it works: the manual DI container ([AppContainer]) is not a
 * ViewModelProvider, so this factory bridges the two worlds (same pattern as
 * the map and journal screens).
 */
class TrendViewModelFactory(
    private val trendRepository: TrendRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrendViewModel::class.java)) {
            return TrendViewModel(trendRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
