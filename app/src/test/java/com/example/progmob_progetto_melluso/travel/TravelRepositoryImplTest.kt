package com.example.progmob_progetto_melluso.travel

import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.dao.TravelDao
import com.example.progmob_progetto_melluso.data.dao.TravelPathPointDao
import com.example.progmob_progetto_melluso.data.dao.TravelPoiVisitDao
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import com.example.progmob_progetto_melluso.data.entities.TravelPoiVisitEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TravelRepositoryImplTest {

    /** Small deterministic thresholds so tests don't need real distances. */
    private val config = TravelConfig(
        minDistanceMeters = 10.0,
        minElapsedMillis = 1_000L,
        maxAccuracyMeters = 25f,
        tripEndDistanceMeters = 100.0
    )

    /**
     * A mocked PoiDao whose visit detection finds no POIs (auto-visit is a
     * no-op). Calls the suspend allPoisOnce(), so it must be created from a
     * coroutine (all construction sites live inside runTest).
     */
    private suspend fun poiDao(): PoiDao = mock<PoiDao>().apply {
        whenever(allPoisOnce()).thenReturn(emptyList())
    }

    private fun visitDao(): TravelPoiVisitDao = mock()

    private fun activeTravel(id: Long = 1L) = TravelEntity(
        id = id,
        startUtcMillis = 0L,
        endUtcMillis = null,
        travelTimeZoneId = "UTC",
        title = "Pinerolo Marittima"
    )
    /**
     * Verifica che venga creato un nuovo viaggio quando non ce n'è uno attivo.
     * In questo test simuliamo l'inserimento di un viaggio che riceve l'ID 7.
     */
    @Test
    fun startTravel_createsNewTravelWhenNoneActive() = runTest {
        val travelDao = mock<TravelDao>()
        val pathPointDao = mock<TravelPathPointDao>()
        whenever(travelDao.activeTravelOnce()).thenReturn(null)
        whenever(travelDao.insert(any())).thenReturn(7L)
        whenever(pathPointDao.pointsForTravelOnce(7L)).thenReturn(emptyList())

        val repository = TravelRepositoryImpl(
            travelDao = travelDao,
            pathPointDao = pathPointDao,
            poiDao = poiDao(), travelPoiVisitDao = visitDao(), config = config,
            timeZoneIdProvider = { "Europe/Rome" },
            clock = { 1_000L }
        )

        assertEquals(7L, repository.startTravel())
        verify(travelDao).insert(
            argThat {
                endUtcMillis == null &&
                    startUtcMillis == 1_000L &&
                    travelTimeZoneId == "Europe/Rome"
            }
        )
    }

    /**
     * Verifica che startTravel restituisca l'ID del viaggio esistente se ce n'è uno già attivo,
     * senza crearne uno nuovo nel database.
     */
    @Test
    fun startTravel_reusesActiveTravel() = runTest {
        val travelDao = mock<TravelDao>()
        val pathPointDao = mock<TravelPathPointDao>()
        val existing = activeTravel(3L)
        whenever(travelDao.activeTravelOnce()).thenReturn(existing)
        whenever(pathPointDao.pointsForTravelOnce(3L)).thenReturn(emptyList())

        val repository = TravelRepositoryImpl(travelDao, pathPointDao, poiDao(), visitDao(), config)

        assertEquals(3L, repository.startTravel())
        verify(travelDao, never()).insert(any())
    }

    /**
     * Verifica che stopTravel chiuda correttamente il viaggio attivo impostando la data di fine.
     */
    @Test
    fun stopTravel_closesActiveTravel() = runTest {
        val travelDao = mock<TravelDao>()
        val pathPointDao = mock<TravelPathPointDao>()
        val existing = activeTravel(1L)
        whenever(travelDao.activeTravelOnce()).thenReturn(existing)

        val repository = TravelRepositoryImpl(
            travelDao = travelDao,
            pathPointDao = pathPointDao,
            poiDao = poiDao(), travelPoiVisitDao = visitDao(), config = config,
            clock = { 42L }
        )

        assertTrue(repository.stopTravel())
        verify(travelDao).update(existing.copy(endUtcMillis = 42L))
    }

    /**
     * Verifica che stopTravel non faccia nulla (e restituisca false) se non c'è alcun viaggio attivo.
     */
    @Test
    fun stopTravel_doesNothingWhenNoActiveTravel() = runTest {
        val travelDao = mock<TravelDao>()
        val pathPointDao = mock<TravelPathPointDao>()
        whenever(travelDao.activeTravelOnce()).thenReturn(null)

        val repository = TravelRepositoryImpl(travelDao, pathPointDao, poiDao(), visitDao(), config)

        assertFalse(repository.stopTravel())
        verify(travelDao, never()).update(any())
    }

    /**
     * Verifica che la registrazione di una posizione faccia partire automaticamente un viaggio
     * se non ce n'è uno attivo, registrando il primo punto del percorso.
     */
    @Test
    fun recordLocation_autoStartsAndRecordsFirstPoint() = runTest {
        val travelDao = mock<TravelDao>()
        val pathPointDao = mock<TravelPathPointDao>()
        whenever(travelDao.activeTravelOnce()).thenReturn(null)
        whenever(travelDao.insert(any())).thenReturn(1L)
        whenever(pathPointDao.pointsForTravelOnce(1L)).thenReturn(emptyList())

        val repository = TravelRepositoryImpl(travelDao, pathPointDao, poiDao(), visitDao(), config)

        val result = repository.recordLocation(LocationSample(45.0, 7.0, 100L, 5f))

        assertEquals(LocationRecordResult.Recorded(1L, false), result)
        verify(pathPointDao).insert(
            argThat { travelId == 1L && latitude == 45.0 && longitude == 7.0 }
        )
    }

    /**
     * Verifica che i punti troppo vicini o troppo ravvicinati nel tempo rispetto all'ultimo
     * punto registrato vengano ignorati per risparmiare energia e spazio.
     */
    @Test
    fun recordLocation_skipsPointBelowThresholds() = runTest {
        val travelDao = mock<TravelDao>()
        val pathPointDao = mock<TravelPathPointDao>()
        whenever(travelDao.activeTravelOnce()).thenReturn(activeTravel(1L))
        whenever(pathPointDao.pointsForTravelOnce(1L)).thenReturn(emptyList())

        val repository = TravelRepositoryImpl(travelDao, pathPointDao, poiDao(), visitDao(), config)

        repository.recordLocation(LocationSample(0.0, 0.0, 0L))
        // ~5m away and 5s later: below both thresholds, must be skipped.
        val result = repository.recordLocation(LocationSample(0.000045, 0.0, 5_000L))

        assertEquals(LocationRecordResult.Skipped, result)
        verify(pathPointDao, times(1)).insert(any())
    }

    /**
     * Verifica che i filtri spaziale e temporale siano combinati con semantica
     * AND (spec §7.2): uno spostamento sufficiente ma troppo ravvicinato nel
     * tempo rispetto all'ultimo punto NON viene registrato.
     */
    @Test
    fun recordLocation_skipsBeforeTemporalThreshold() = runTest {
        val travelDao = mock<TravelDao>()
        val pathPointDao = mock<TravelPathPointDao>()
        whenever(travelDao.activeTravelOnce()).thenReturn(activeTravel(1L))
        whenever(pathPointDao.pointsForTravelOnce(1L)).thenReturn(emptyList())

        val repository = TravelRepositoryImpl(travelDao, pathPointDao, poiDao(), visitDao(), config)

        repository.recordLocation(LocationSample(0.0, 0.0, 0L))
        // ~55 m away (beyond the spatial threshold) but only 500 ms later:
        // the temporal threshold is not met, must be skipped.
        val result = repository.recordLocation(LocationSample(0.0005, 0.0, 500L))

        assertEquals(LocationRecordResult.Skipped, result)
        verify(pathPointDao, times(1)).insert(any())
    }

    /**
     * Verifica che un punto venga registrato quando supera ENTRAMBE le soglie,
     * spaziale e temporale.
     */
    @Test
    fun recordLocation_recordsWhenBothThresholdsMet() = runTest {
        val travelDao = mock<TravelDao>()
        val pathPointDao = mock<TravelPathPointDao>()
        whenever(travelDao.activeTravelOnce()).thenReturn(activeTravel(1L))
        whenever(pathPointDao.pointsForTravelOnce(1L)).thenReturn(emptyList())

        val repository = TravelRepositoryImpl(travelDao, pathPointDao, poiDao(), visitDao(), config)

        repository.recordLocation(LocationSample(0.0, 0.0, 0L))
        // ~55 m away and 2 s later: both thresholds met, must be recorded.
        val result = repository.recordLocation(LocationSample(0.0005, 0.0, 2_000L))

        assertTrue(result is LocationRecordResult.Recorded)
        verify(pathPointDao, times(2)).insert(any())
    }

    /**
     * Verifica che un fix con accuratezza scadente venga scartato quando esiste
     * già un punto precedente: è rumore GPS e sporcherebbe il percorso.
     */
    @Test
    fun recordLocation_skipsInaccurateFix() = runTest {
        val travelDao = mock<TravelDao>()
        val pathPointDao = mock<TravelPathPointDao>()
        whenever(travelDao.activeTravelOnce()).thenReturn(activeTravel(1L))
        whenever(pathPointDao.pointsForTravelOnce(1L)).thenReturn(emptyList())

        val repository = TravelRepositoryImpl(travelDao, pathPointDao, poiDao(), visitDao(), config)

        repository.recordLocation(LocationSample(0.0, 0.0, 0L))
        // ~55 m away (inside the vacation radius, beyond the spatial
        // threshold) but with a reported error above the maximum accepted
        // accuracy: must be skipped.
        val result = repository.recordLocation(LocationSample(0.0005, 0.0, 5_000L, accuracyMeters = 60f))

        assertEquals(LocationRecordResult.Skipped, result)
        verify(pathPointDao, times(1)).insert(any())
    }

    /**
     * Verifica che il primo punto di un viaggio venga accettato anche se la
     * sua accuratezza è scadente: senza di esso non esisterebbe mai l'ancora.
     */
    @Test
    fun recordLocation_acceptsFirstPointEvenIfInaccurate() = runTest {
        val travelDao = mock<TravelDao>()
        val pathPointDao = mock<TravelPathPointDao>()
        whenever(travelDao.activeTravelOnce()).thenReturn(activeTravel(1L))
        whenever(pathPointDao.pointsForTravelOnce(1L)).thenReturn(emptyList())

        val repository = TravelRepositoryImpl(travelDao, pathPointDao, poiDao(), visitDao(), config)

        val result = repository.recordLocation(LocationSample(0.0, 0.0, 0L, accuracyMeters = 90f))

        assertTrue(result is LocationRecordResult.Recorded)
    }

    /**
     * Verifica che lo jitter GPS da fermo non venga registrato nemmeno se lo
     * spostamento apparente supera la soglia spaziale minima: la soglia effettiva
     * cresce con l'errore di accuratezza medio dei due fix.
     */
    @Test
    fun recordLocation_skipsJitterWithinAccuracyBuffer() = runTest {
        val travelDao = mock<TravelDao>()
        val pathPointDao = mock<TravelPathPointDao>()
        whenever(travelDao.activeTravelOnce()).thenReturn(activeTravel(1L))
        whenever(pathPointDao.pointsForTravelOnce(1L)).thenReturn(emptyList())

        val repository = TravelRepositoryImpl(travelDao, pathPointDao, poiDao(), visitDao(), config)

        // Both fixes report ±20 m of error: the effective threshold is the
        // average buffer (20 m), above the 15 m apparent displacement.
        repository.recordLocation(LocationSample(0.0, 0.0, 0L, accuracyMeters = 20f))
        val result = repository.recordLocation(
            LocationSample(0.000135, 0.0, 30_000L, accuracyMeters = 20f)
        )

        assertEquals(LocationRecordResult.Skipped, result)
        verify(pathPointDao, times(1)).insert(any())
    }

    /**
     * Verifica che se l'utente è fermo, il punto non venga registrato anche se è passato
     * molto tempo (evita di registrare rumore GPS mentre si è fermi).
     */
    @Test
    fun recordLocation_skipsWhenStationary() = runTest {
        val travelDao = mock<TravelDao>()
        val pathPointDao = mock<TravelPathPointDao>()
        whenever(travelDao.activeTravelOnce()).thenReturn(activeTravel(1L))
        whenever(pathPointDao.pointsForTravelOnce(1L)).thenReturn(emptyList())

        val repository = TravelRepositoryImpl(travelDao, pathPointDao, poiDao(), visitDao(), config)

        repository.recordLocation(LocationSample(0.0, 0.0, 0L))
        // No movement even after the time threshold: must be skipped.
        val result = repository.recordLocation(LocationSample(0.0, 0.0, 15_000L))

        assertEquals(LocationRecordResult.Skipped, result)
        verify(pathPointDao, times(1)).insert(any())
    }

    /**
     * Verifica che finché l'utente si muove all'interno di un raggio definito (vacation area),
     * il viaggio rimanga unico anche se il percorso totale è lungo.
     */
    @Test
    fun recordLocation_doesNotSegmentWhenMovingWithinRadius() = runTest {
        val travelDao = mock<TravelDao>()
        val pathPointDao = mock<TravelPathPointDao>()
        whenever(travelDao.activeTravelOnce()).thenReturn(activeTravel(1L))
        whenever(pathPointDao.pointsForTravelOnce(1L)).thenReturn(emptyList())

        val repository = TravelRepositoryImpl(travelDao, pathPointDao, poiDao(), visitDao(), config)

        repository.recordLocation(LocationSample(0.0, 0.0, 0L))
        // 44m east, still within the 100m radius.
        repository.recordLocation(LocationSample(0.0004, 0.0, 1_000L))
        // Back to the start.
        repository.recordLocation(LocationSample(0.0, 0.0, 2_000L))
        // East again: the travelled path exceeds 100m in total, but the user
        // never leaves the radius, so the trip must NOT be segmented.
        val result = repository.recordLocation(LocationSample(0.0004, 0.0, 3_000L))

        assertEquals(LocationRecordResult.Recorded(1L, false), result)
        verify(travelDao, never()).update(any())
        verify(pathPointDao, times(4)).insert(any())
    }

    /**
     * Verifica che l'allontanamento oltre il raggio dall'ancora NON chiuda più
     * il viaggio in silenzio: la fine della vacanza viene segnalata alla UI
     * (TravelStaleness) che chiede all'utente prima di chiudere, quindi il
     * punto viene registrato nello stesso viaggio che resta attivo.
     */
    @Test
    fun recordLocation_keepsTravelOpenWhenLeavingAnchorRadius() = runTest {
        val travelDao = mock<TravelDao>()
        val pathPointDao = mock<TravelPathPointDao>()
        val existing = activeTravel(1L)
        whenever(travelDao.activeTravelOnce()).thenReturn(existing)
        whenever(pathPointDao.pointsForTravelOnce(1L)).thenReturn(emptyList())

        val repository = TravelRepositoryImpl(travelDao, pathPointDao, poiDao(), visitDao(), config)

        repository.recordLocation(LocationSample(0.0, 0.0, 0L))
        // around 150m from the anchor: beyond the radius, but the trip is NOT
        // closed behind the user's back — the point is recorded normally.
        val result = repository.recordLocation(LocationSample(0.0, 0.00135, 1_000L))

        assertEquals(LocationRecordResult.Recorded(1L, false), result)
        verify(travelDao, never()).update(any())
        verify(pathPointDao).insert(argThat { travelId == 1L && longitude == 0.00135 })
    }

    /**
     * Verifica che il rilevamento automatico della visita registri ogni POI UNA
     * sola volta per viaggio, anche passandoci più volte: la seconda passata
     * vicino allo stesso POI non crea una visita duplicata nel diario.
     */
    @Test
    fun recordLocation_detectsEachPoiVisitOnlyOnce() = runTest {
        val travelDao = mock<TravelDao>()
        val pathPointDao = mock<TravelPathPointDao>()
        whenever(travelDao.activeTravelOnce()).thenReturn(activeTravel(1L))
        whenever(pathPointDao.pointsForTravelOnce(1L)).thenReturn(emptyList())

        val poi = PoiEntity(
            id = 7L,
            lat = 45.0,
            lon = 7.0,
            title = "P",
            description = null,
            category = null,
            createdByUserId = null
        )
        val poiDao = mock<PoiDao>()
        whenever(poiDao.allPoisOnce()).thenReturn(listOf(poi))

        val existingVisit = TravelPoiVisitEntity(id = 1L, travelId = 1L, poiId = 7L, visitedAtUtcMillis = 0L)
        val visitDao = mock<TravelPoiVisitDao>()
        // First detection: no visit yet -> insert. Second detection: already
        // recorded -> no insert.
        whenever(visitDao.visitForTravelAndPoi(any(), any())).thenReturn(null, existingVisit)

        val repository = TravelRepositoryImpl(travelDao, pathPointDao, poiDao, visitDao, config)

        // Pass near the POI twice in the same trip (both points are recorded).
        repository.recordLocation(LocationSample(45.0, 7.0, 0L, 5f))
        repository.recordLocation(LocationSample(45.0002, 7.0, 2_000L, 5f))

        // The visit is inserted exactly once, not once per pass.
        verify(visitDao, times(1)).insert(any())
    }

    /**
     * Verifica che, anche dopo un riavvio (ancora ricaricata dai dati salvati),
     * un campione oltre il raggio dall'ancora NON chiuda il viaggio in
     * silenzio: il punto resta nel viaggio attivo e la chiusura spetta alla UI.
     */
    @Test
    fun recordLocation_keepsTravelOpenUsingStoredAnchor() = runTest {
        val travelDao = mock<TravelDao>()
        val pathPointDao = mock<TravelPathPointDao>()
        val existing = activeTravel(1L)
        val storedPoints = listOf(
            // Anchor: the vacation start.
            TravelPathPointEntity(id = 1L, travelId = 1L, latitude = 0.0, longitude = 0.0, recordedAtUtcMillis = 0L),
            // Still inside the radius (ex. 95m).
            TravelPathPointEntity(id = 2L, travelId = 1L, latitude = 0.0, longitude = 0.00085, recordedAtUtcMillis = 1_000L)
        )
        whenever(travelDao.activeTravelOnce()).thenReturn(existing)
        whenever(pathPointDao.pointsForTravelOnce(1L)).thenReturn(storedPoints)

        // A fresh repository rebuilds the anchor from storage; a sample is 111m
        // from the anchor: beyond the radius, but the trip stays open.
        val repository = TravelRepositoryImpl(travelDao, pathPointDao, poiDao(), visitDao(), config)
        val result = repository.recordLocation(LocationSample(0.0, 0.0010, 2_000L))

        assertEquals(LocationRecordResult.Recorded(1L, false), result)
        verify(travelDao, never()).update(any())
        verify(pathPointDao).insert(argThat { travelId == 1L })
    }
}
