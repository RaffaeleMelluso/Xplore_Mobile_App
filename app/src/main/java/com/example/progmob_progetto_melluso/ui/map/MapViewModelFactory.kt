package com.example.progmob_progetto_melluso.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.progmob_progetto_melluso.map.MapRepository

/**
 * Factory that builds [MapViewModel] with its [MapRepository] dependency.
 *
 * How it works: the manual DI container ([AppContainer]) is not a
 * ViewModelProvider, so this factory bridges the two worlds.
 */
class MapViewModelFactory(
    private val mapRepository: MapRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            return MapViewModel(mapRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
