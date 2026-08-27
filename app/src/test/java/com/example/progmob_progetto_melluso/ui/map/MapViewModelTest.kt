package com.example.progmob_progetto_melluso.ui.map

import com.example.progmob_progetto_melluso.data.entities.GhostEntity
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.ghost.Ghost
import com.example.progmob_progetto_melluso.map.MapRepository
import com.example.progmob_progetto_melluso.poi.PoiVoteRepository
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
        val viewModel = MapViewModel(mock<MapRepository>(), mock<PoiVoteRepository>())
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
        val ghost = Ghost(
            marker = GhostEntity(userId = "g1", lat = 45.01, lon = 7.01, timestamp = 100L),
            path = emptyList(),
            name = "G",
            description = "",
            isOnlineNow = true,
            city = "Torino"
        )
        whenever(repository.observeCurrentLocation()).thenReturn(flowOf(sample))
        whenever(repository.observeActivePath()).thenReturn(flowOf(listOf(point)))
        whenever(repository.observePois()).thenReturn(flowOf(listOf(poi)))
        whenever(repository.observeGhosts()).thenReturn(flowOf(listOf(ghost)))
        // The map filter combines travels + cities; stub them so the combined
        // state actually emits instead of blocking on a null stream.
        whenever(repository.observeTravels()).thenReturn(flowOf(emptyList()))
        whenever(repository.observeTravelCities()).thenReturn(flowOf(emptyList()))
        val voteRepository = mock<PoiVoteRepository>()
        whenever(voteRepository.observeMyVotes()).thenReturn(flowOf(emptyList()))

        val viewModel = MapViewModel(repository, voteRepository)
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
     * Verifica che il voto dalla card POI della mappa passi dal motore di
     * voto condiviso (stesso voto del journal e dei trends).
     */
    @Test
    fun votePoi_delegatesToVoteRepository() = runTest(dispatcher) {
        val repository = mock<MapRepository>()
        whenever(repository.observeCurrentLocation()).thenReturn(flowOf(LocationSample(45.0, 7.0, 100L, 5f)))
        whenever(repository.observeActivePath()).thenReturn(flowOf(emptyList()))
        whenever(repository.observePois()).thenReturn(flowOf(emptyList()))
        whenever(repository.observeGhosts()).thenReturn(flowOf(emptyList()))
        whenever(repository.observeTravels()).thenReturn(flowOf(emptyList()))
        whenever(repository.observeTravelCities()).thenReturn(flowOf(emptyList()))
        val voteRepository = mock<PoiVoteRepository>()
        whenever(voteRepository.observeMyVotes()).thenReturn(flowOf(emptyList()))

        val viewModel = MapViewModel(repository, voteRepository)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.votePoi(1L, 1)
        advanceUntilIdle()

        verify(voteRepository).votePoi(1L, 1)
    }

    /**
     * Verifica che alla creazione vengano avviati i seed: prima i POI demo,
     * poi i voti della community (gli stessi che i ghost mostrano), così mappa
     * e trends restano coerenti.
     */
    @Test
    fun init_seedsPoisAndDemoVotes() = runTest(dispatcher) {
        val repository = mock<MapRepository>()
        val voteRepository = mock<PoiVoteRepository>()
        MapViewModel(repository, voteRepository)
        advanceUntilIdle()
        verify(repository).ensurePoiSeed()
        verify(voteRepository).ensureDemoVotes()
    }

    /**
     * Verifica che hideGhost escluda dalla mappa solo l'utente indicato (tap
     * sul nome nel menù) e che il menù continui a vedere l'elenco completo.
     */
    @Test
    fun hideGhost_hidesOnlyThatTraveler() = runTest(dispatcher) {
        val repository = mock<MapRepository>()
        val user = LocationSample(45.0, 7.0, 100L, 5f)
        val ghostA = ghost("a", 45.001, 7.001)
        val ghostB = ghost("b", 45.002, 7.002)
        whenever(repository.observeCurrentLocation()).thenReturn(flowOf(user))
        whenever(repository.observeActivePath()).thenReturn(flowOf(emptyList()))
        whenever(repository.observePois()).thenReturn(flowOf(emptyList()))
        whenever(repository.observeGhosts()).thenReturn(flowOf(listOf(ghostA, ghostB)))
        whenever(repository.observeTravels()).thenReturn(flowOf(emptyList()))
        whenever(repository.observeTravelCities()).thenReturn(flowOf(emptyList()))
        val voteRepository = mock<PoiVoteRepository>()
        whenever(voteRepository.observeMyVotes()).thenReturn(flowOf(emptyList()))

        val viewModel = MapViewModel(repository, voteRepository)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.hideGhost("a")
        advanceUntilIdle()

        assertEquals("only the tapped traveler disappears", listOf(ghostB), viewModel.uiState.value.ghosts)
        assertEquals(setOf("a"), viewModel.uiState.value.hiddenGhostIds)
        // The menu still sees the full list (names + restore).
        assertEquals(listOf(ghostA, ghostB), viewModel.uiState.value.nearbyGhosts)
    }

    /**
     * Verifica che showGhost ripristini sulla mappa solo l'utente indicato.
     */
    @Test
    fun showGhost_restoresThatTraveler() = runTest(dispatcher) {
        val repository = mock<MapRepository>()
        val user = LocationSample(45.0, 7.0, 100L, 5f)
        val ghostA = ghost("a", 45.001, 7.001)
        val ghostB = ghost("b", 45.002, 7.002)
        whenever(repository.observeCurrentLocation()).thenReturn(flowOf(user))
        whenever(repository.observeActivePath()).thenReturn(flowOf(emptyList()))
        whenever(repository.observePois()).thenReturn(flowOf(emptyList()))
        whenever(repository.observeGhosts()).thenReturn(flowOf(listOf(ghostA, ghostB)))
        whenever(repository.observeTravels()).thenReturn(flowOf(emptyList()))
        whenever(repository.observeTravelCities()).thenReturn(flowOf(emptyList()))
        val voteRepository = mock<PoiVoteRepository>()
        whenever(voteRepository.observeMyVotes()).thenReturn(flowOf(emptyList()))

        val viewModel = MapViewModel(repository, voteRepository)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.hideGhost("a")
        viewModel.hideGhost("b")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.ghosts.isEmpty())

        viewModel.showGhost("a")
        advanceUntilIdle()

        assertEquals("only the restored traveler comes back", listOf(ghostA), viewModel.uiState.value.ghosts)
        assertEquals(setOf("b"), viewModel.uiState.value.hiddenGhostIds)
    }

    /**
     * Verifica che showAllGhosts ripristini subito tutti i fantasmi nascosti.
     */
    @Test
    fun showAllGhosts_restoresEveryHiddenGhost() = runTest(dispatcher) {
        val repository = mock<MapRepository>()
        val user = LocationSample(45.0, 7.0, 100L, 5f)
        val ghostA = ghost("a", 45.001, 7.001)
        val ghostB = ghost("b", 45.002, 7.002)
        whenever(repository.observeCurrentLocation()).thenReturn(flowOf(user))
        whenever(repository.observeActivePath()).thenReturn(flowOf(emptyList()))
        whenever(repository.observePois()).thenReturn(flowOf(emptyList()))
        whenever(repository.observeGhosts()).thenReturn(flowOf(listOf(ghostA, ghostB)))
        whenever(repository.observeTravels()).thenReturn(flowOf(emptyList()))
        whenever(repository.observeTravelCities()).thenReturn(flowOf(emptyList()))
        val voteRepository = mock<PoiVoteRepository>()
        whenever(voteRepository.observeMyVotes()).thenReturn(flowOf(emptyList()))

        val viewModel = MapViewModel(repository, voteRepository)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.hideGhost("a")
        viewModel.hideGhost("b")
        advanceUntilIdle()
        viewModel.showAllGhosts()
        advanceUntilIdle()

        assertEquals(listOf(ghostA, ghostB), viewModel.uiState.value.ghosts)
        assertTrue(viewModel.uiState.value.hiddenGhostIds.isEmpty())
    }

    /** Builds a minimal simulated traveler for the map tests. */
    private fun ghost(userId: String, lat: Double, lon: Double): Ghost =
        Ghost(
            marker = GhostEntity(userId = userId, lat = lat, lon = lon, timestamp = 100L),
            path = emptyList(),
            name = userId,
            description = "",
            isOnlineNow = true,
            city = "Torino"
        )
}
