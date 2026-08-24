package com.example.progmob_progetto_melluso.ui.map

import com.example.progmob_progetto_melluso.data.entities.GhostEntity
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.map.MapRepository
import com.example.progmob_progetto_melluso.travel.LocationSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Verifica che lo stato iniziale sia il placeholder di caricamento.
     */
    @Test
    fun uiState_startsLoading() {
        val viewModel = MapViewModel(mock<MapRepository>())
        assertTrue(viewModel.uiState.value.isLoading)
    }

    /**
     * Verifica che il ViewModel combini tutti e quattro i flussi del repository
     * in un unico stato immutabile.
     */
    @Test
    fun uiState_combinesAllStreams() = runTest(dispatcher) {
        val repository = mock<MapRepository>()
        val sample = LocationSample(45.0, 7.0, 100L, 5f)
        val point = TravelPathPointEntity(id = 1L, travelId = 1L, latitude = 45.0, longitude = 7.0, recordedAtUtcMillis = 100L)
        val poi = PoiEntity(lat = 45.1, lon = 7.1, title = "P", description = null, category = null, createdByUserId = null)
        val ghost = GhostEntity(userId = "g1", lat = 45.01, lon = 7.01, timestamp = 100L)
        whenever(repository.observeCurrentLocation()).thenReturn(flowOf(sample))
        whenever(repository.observeActivePath()).thenReturn(flowOf(listOf(point)))
        whenever(repository.observePois()).thenReturn(flowOf(listOf(poi)))
        whenever(repository.observeGhosts()).thenReturn(flowOf(listOf(ghost)))

        val viewModel = MapViewModel(repository)
        // Subscribe so the WhileSubscribed StateFlow starts collecting upstream.
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(sample, state.userLocation)
        assertEquals(listOf(point), state.pathPoints)
        assertEquals(listOf(poi), state.pois)
        assertEquals(listOf(ghost), state.ghosts)
        assertFalse(state.isLoading)
    }

    /**
     * Verifica che alla creazione venga avviato il seed dei POI demo.
     */
    @Test
    fun init_seedsPois() = runTest(dispatcher) {
        val repository = mock<MapRepository>()
        MapViewModel(repository)
        advanceUntilIdle()
        verify(repository).ensurePoiSeed()
    }
}
