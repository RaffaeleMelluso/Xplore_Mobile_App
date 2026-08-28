package com.example.progmob_progetto_melluso.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.progmob_progetto_melluso.map.MapRepository
import com.example.progmob_progetto_melluso.poi.PoiRepository
import com.example.progmob_progetto_melluso.poi.PoiVoteRepository

/**
 * Factory that builds [MapViewModel] with its [MapRepository],
 * [PoiRepository] and [PoiVoteRepository] dependencies.
 *
 * How it works: the manual DI container ([AppContainer]) is not a
 * ViewModelProvider, so this factory bridges the two worlds.
 */
class MapViewModelFactory(
    private val mapRepository: MapRepository,
    private val poiRepository: PoiRepository,
    private val poiVoteRepository: PoiVoteRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            return MapViewModel(mapRepository, poiRepository, poiVoteRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
