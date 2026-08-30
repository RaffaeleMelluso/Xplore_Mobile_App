package com.example.progmob_progetto_melluso.ui.poi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.progmob_progetto_melluso.poi.PoiRepository

/**
 * Factory that builds [MyPoiViewModel] with its [PoiRepository] dependency.
 *
 * How it works: the manual DI container ([AppContainer]) is not a
 * ViewModelProvider, so this factory bridges the two worlds (same pattern as
 * the map, journal and trends screens).
 */
class MyPoiViewModelFactory(
    private val poiRepository: PoiRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MyPoiViewModel::class.java)) {
            return MyPoiViewModel(poiRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
