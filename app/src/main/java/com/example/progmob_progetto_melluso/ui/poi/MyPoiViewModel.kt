package com.example.progmob_progetto_melluso.ui.poi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.poi.PoiRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Immutable state of the "my POIs" screen.
 *
 * @param pois the POIs created by the signed-in user, newest first.
 * @param isLoading true only until the first emission of the repository flow.
 */
data class MyPoiUiState(
    val pois: List<PoiEntity> = emptyList(),
    val isLoading: Boolean = false
)

/**
 * ViewModel for the "my POIs" screen.
 *
 * How it works: the list comes straight from the shared [PoiRepository] flow
 * filtered by the signed-in user ([PoiEntity.createdByUserId]), so it always
 * shows exactly the POIs the user added — and a removal re-emits the list
 * immediately. Removal goes through [PoiRepository.removeMyPoi], which deletes
 * only POIs owned by the user (their votes go with them, FK cascade), so the
 * demo community POIs can never be touched from here. All asynchrony lives in
 * [viewModelScope]; the UI only collects the state and forwards the intent.
 *
 * @param poiRepository the shared POI catalog (observeMyPois + removeMyPoi).
 */
class MyPoiViewModel(
    private val poiRepository: PoiRepository
) : ViewModel() {

    /** The user's POIs, reactively: every add/remove re-emits the list. */
    val uiState: StateFlow<MyPoiUiState> =
        poiRepository.observeMyPois()
            .map { pois -> MyPoiUiState(pois = pois, isLoading = false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = MyPoiUiState(isLoading = true)
            )

    /**
     * Removes one of the user's POIs (no effect on POIs owned by others).
     *
     * How it works: the repository checks the owner before deleting, so the
     * removal rule lives once in the data layer, not in the UI.
     */
    fun removePoi(poiId: Long) {
        viewModelScope.launch { poiRepository.removeMyPoi(poiId) }
    }

    companion object {
        /** Delay before the repository flow stops after the UI stops collecting. */
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
