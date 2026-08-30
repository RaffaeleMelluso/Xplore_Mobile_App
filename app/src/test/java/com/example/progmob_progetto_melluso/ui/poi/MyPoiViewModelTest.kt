package com.example.progmob_progetto_melluso.ui.poi

import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.poi.PoiRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MyPoiViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun poi(id: Long = 1L) = PoiEntity(
        id = id,
        lat = 45.0,
        lon = 7.0,
        title = "Mio POI",
        description = null,
        category = "Museum",
        createdByUserId = "user-1",
        city = "Torino",
        imageUri = "file:///data/user/p.jpg",
        votes = 3
    )

    /**
     * Verifica che lo stato iniziale sia il placeholder di caricamento.
     */
    @Test
    fun uiState_startsLoading() {
        val viewModel = MyPoiViewModel(mock<PoiRepository>())
        assertTrue(viewModel.uiState.value.isLoading)
    }

    /**
     * Verifica che la lista contenga esattamente i POI dell'utente, così la
     * schermata può rimuovere solo quelli aggiunti da lui.
     */
    @Test
    fun uiState_listsTheUsersPois() = runTest(dispatcher) {
        val repository = mock<PoiRepository>()
        whenever(repository.observeMyPois()).thenReturn(flowOf(listOf(poi())))

        val viewModel = MyPoiViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(poi()), state.pois)
        assertTrue(!state.isLoading)
    }

    /**
     * Verifica che la rimozione passi dal repository (che controlla la
     * proprietà): la UI non decide mai cosa è rimovibile.
     */
    @Test
    fun removePoi_delegatesToTheRepository() = runTest(dispatcher) {
        val repository = mock<PoiRepository>()
        whenever(repository.observeMyPois()).thenReturn(flowOf(emptyList()))

        val viewModel = MyPoiViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.removePoi(3L)
        advanceUntilIdle()

        verify(repository).removeMyPoi(3L)
    }
}
