package com.example.progmob_progetto_melluso.ui.trend

import com.example.progmob_progetto_melluso.trend.CityTrendItem
import com.example.progmob_progetto_melluso.trend.PoiTrendItem
import com.example.progmob_progetto_melluso.trend.TrendPeriod
import com.example.progmob_progetto_melluso.trend.TrendRepository
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TrendViewModelTest {

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
        val viewModel = TrendViewModel(mock<TrendRepository>())
        assertTrue(viewModel.uiState.value.isLoading)
    }

    /**
     * Verifica che il ViewModel combini le due scelte di filtro (periodo e
     * ambito) con i ranking del repository in un unico stato immutabile.
     */
    @Test
    fun uiState_reflectsPeriodAndScopeSelection() = runTest(dispatcher) {
        val repository = mock<TrendRepository>()
        whenever(repository.observePoiTrends(TrendPeriod.WEEK))
            .thenReturn(flowOf(listOf(poiItem())))
        whenever(repository.observeCityTrends(TrendPeriod.WEEK))
            .thenReturn(flowOf(listOf(cityItem())))
        whenever(repository.observePoiTrends(TrendPeriod.DAY))
            .thenReturn(flowOf(emptyList()))
        whenever(repository.observeCityTrends(TrendPeriod.DAY))
            .thenReturn(flowOf(emptyList()))

        val viewModel = TrendViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertEquals(TrendPeriod.WEEK, viewModel.uiState.value.period)
        assertEquals(TrendScope.ATTRACTIONS, viewModel.uiState.value.scope)
        assertEquals(1, viewModel.uiState.value.poiTrends.size)
        assertEquals(1, viewModel.uiState.value.cityTrends.size)
        assertTrue(!viewModel.uiState.value.isLoading)

        viewModel.selectPeriod(TrendPeriod.DAY)
        advanceUntilIdle()

        assertEquals(TrendPeriod.DAY, viewModel.uiState.value.period)
        assertTrue(viewModel.uiState.value.poiTrends.isEmpty())

        viewModel.selectScope(TrendScope.CITIES)
        advanceUntilIdle()

        assertEquals(TrendScope.CITIES, viewModel.uiState.value.scope)
    }

    /**
     * Verifica che alla creazione venga avviato il seed dei dati demo
     * (POI + voti della community) se non ancora presente.
     */
    @Test
    fun init_seedsTrendData() = runTest(dispatcher) {
        val repository = mock<TrendRepository>()
        TrendViewModel(repository)
        advanceUntilIdle()
        verify(repository).ensureTrendSeed()
    }

    /**
     * Verifica che il voto dalla pagina trends passi dal motore di voto
     * condiviso (stesso voto del journal e della mappa).
     */
    @Test
    fun votePoi_delegatesToRepository() = runTest(dispatcher) {
        val repository = mock<TrendRepository>()
        whenever(repository.observePoiTrends(any<TrendPeriod>())).thenReturn(flowOf(emptyList()))
        whenever(repository.observeCityTrends(any<TrendPeriod>())).thenReturn(flowOf(emptyList()))

        val viewModel = TrendViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.votePoi(3L, -1)
        advanceUntilIdle()

        verify(repository).votePoi(3L, -1)
    }

    /** Builds a minimal ranked attraction for the tests. */
    private fun poiItem() = PoiTrendItem(
        poiId = 1L,
        title = "Mole Antonelliana",
        category = "Museum",
        city = "Torino",
        description = null,
        windowScore = 12L,
        windowVotes = 8L,
        myVote = 1
    )

    /** Builds a minimal ranked city for the tests. */
    private fun cityItem() = CityTrendItem(cityName = "Torino", windowScore = 42L, windowVotes = 20L)
}
