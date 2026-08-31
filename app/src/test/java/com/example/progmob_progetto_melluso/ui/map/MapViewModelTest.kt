package com.example.progmob_progetto_melluso.ui.map

import com.example.progmob_progetto_melluso.data.entities.GhostEntity
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.ghost.Ghost
import com.example.progmob_progetto_melluso.map.MapRepository
import com.example.progmob_progetto_melluso.poi.PoiRepository
import com.example.progmob_progetto_melluso.poi.PoiVoteRepository
import com.example.progmob_progetto_melluso.step.StepRepository
import com.example.progmob_progetto_melluso.travel.LocationSample
import com.example.progmob_progetto_melluso.travel.StaleReason
import com.example.progmob_progetto_melluso.travel.TravelConfig
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
import org.junit.Assert.assertNull
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

    /** Pedometer repository mock with the live flows stubbed (0 by default). */
    private fun stepRepository(): StepRepository {
        val repo = mock<StepRepository>()
        whenever(repo.observeActiveTravelSteps()).thenReturn(flowOf(0L))
        whenever(repo.observeActiveTravelDistance()).thenReturn(flowOf(0.0))
        return repo
    }

    /**
     * Verifica che lo stato iniziale sia il placeholder di caricamento.
     */
    @Test
    fun uiState_startsLoading() {
        val repository = mock<MapRepository>()
        whenever(repository.observeTravels()).thenReturn(flowOf(emptyList()))
        val viewModel = MapViewModel(
            repository, mock<PoiRepository>(), mock<PoiVoteRepository>(), stepRepository()
        )
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

        val viewModel = MapViewModel(repository, mock<PoiRepository>(), voteRepository, stepRepository())
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

        val viewModel = MapViewModel(repository, mock<PoiRepository>(), voteRepository, stepRepository())
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
        // The geofence-reset collector observes the travels; stub it so the
        // observeTravels() flow is not null.
        whenever(repository.observeTravels()).thenReturn(flowOf(emptyList()))
        val voteRepository = mock<PoiVoteRepository>()
        MapViewModel(repository, mock<PoiRepository>(), voteRepository, stepRepository())
        advanceUntilIdle()
        verify(repository).ensurePoiSeed()
        verify(voteRepository).ensureDemoVotes()
    }

    /**
     * Verifica che l'inserimento di un POI da parte dell'utente (spec §3.3,
     * demo senza controlli) passi dal repository del catalogo: il flusso
     * reattivo aggiorna mappa, ghost e trends senza altro cablaggio.
     */
    @Test
    fun addPoi_delegatesToPoiRepository() = runTest(dispatcher) {
        val repository = mock<MapRepository>()
        whenever(repository.observeCurrentLocation()).thenReturn(flowOf(LocationSample(45.0, 7.0, 100L, 5f)))
        whenever(repository.observeActivePath()).thenReturn(flowOf(emptyList()))
        whenever(repository.observePois()).thenReturn(flowOf(emptyList()))
        whenever(repository.observeGhosts()).thenReturn(flowOf(emptyList()))
        whenever(repository.observeTravels()).thenReturn(flowOf(emptyList()))
        whenever(repository.observeTravelCities()).thenReturn(flowOf(emptyList()))
        val voteRepository = mock<PoiVoteRepository>()
        whenever(voteRepository.observeMyVotes()).thenReturn(flowOf(emptyList()))
        val poiRepository = mock<PoiRepository>()

        val viewModel = MapViewModel(repository, poiRepository, voteRepository, stepRepository())
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val newPoi = PoiEntity(lat = 35.71, lon = 139.79, title = "Senso-ji", description = null, category = null, createdByUserId = null)
        viewModel.addPoi(newPoi)
        advanceUntilIdle()

        verify(poiRepository).addPoi(newPoi)
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

        val viewModel = MapViewModel(repository, mock<PoiRepository>(), voteRepository, stepRepository())
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

        val viewModel = MapViewModel(repository, mock<PoiRepository>(), voteRepository, stepRepository())
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
     * Verifica che un viaggio attivo "in stallo" (l'utente è lontano
     * dall'ancora di partenza) venga segnalato nello stato con il motivo, e
     * che "tieni il viaggio" / "chiudi il viaggio" si comportino come atteso:
     * il primo sopprime il prompt, il secondo chiude il viaggio attivo.
     */
    @Test
    fun staleTravel_flaggedFarFromAnchor_keepAndEndBehave() = runTest(dispatcher) {
        val repository = mock<MapRepository>()
        val anchor = TravelPathPointEntity(
            id = 1L, travelId = 1L, latitude = 45.0, longitude = 7.0, recordedAtUtcMillis = 100L
        )
        val active = TravelEntity(
            id = 1L, startUtcMillis = 100L, endUtcMillis = null, travelTimeZoneId = "UTC", title = null
        )
        whenever(repository.observeCurrentLocation())
            .thenReturn(flowOf(LocationSample(45.6, 7.0, 200L, 5f))) // ~66 km from the anchor
        whenever(repository.observeActivePath()).thenReturn(flowOf(listOf(anchor)))
        whenever(repository.observePois()).thenReturn(flowOf(emptyList()))
        whenever(repository.observeGhosts()).thenReturn(flowOf(emptyList()))
        whenever(repository.observeTravels()).thenReturn(flowOf(listOf(active)))
        whenever(repository.observeTravelCities()).thenReturn(flowOf(emptyList()))
        val voteRepository = mock<PoiVoteRepository>()
        whenever(voteRepository.observeMyVotes()).thenReturn(flowOf(emptyList()))

        // Fixed clock right after the trip start: the trip is recent, so the
        // staleness must come from the DISTANCE trigger, not the age one.
        val viewModel = MapViewModel(
            repository, mock<PoiRepository>(), voteRepository, stepRepository(), TravelConfig(), { 200L }
        )
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertEquals(StaleReason.TOO_FAR, viewModel.uiState.value.staleTravel?.reason)

        // "Keep it": the prompt is suppressed for this trip.
        viewModel.keepStaleTravel()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.staleTravel)

        // "End trip": the active travel is closed through the repository.
        viewModel.endStaleTravel()
        advanceUntilIdle()
        verify(repository).stopTravel()
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

        val viewModel = MapViewModel(repository, mock<PoiRepository>(), voteRepository, stepRepository())
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
