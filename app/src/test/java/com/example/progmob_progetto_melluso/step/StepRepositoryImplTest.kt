package com.example.progmob_progetto_melluso.step

import com.example.progmob_progetto_melluso.data.dao.StepCheckpointDao
import com.example.progmob_progetto_melluso.data.dao.StepDailySummaryDao
import com.example.progmob_progetto_melluso.data.dao.TravelDao
import com.example.progmob_progetto_melluso.data.entities.StepCheckpointEntity
import com.example.progmob_progetto_melluso.data.entities.TravelEntity
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for the pedometer repository: the cumulative sensor readings must be
 * turned into per-day deltas of the ACTIVE travel, surviving sensor resets
 * (device reboot) and day changes without double counting. A positive step
 * delta can even CREATE the trip (steps count before the first GPS fix).
 */
class StepRepositoryImplTest {

    private val activeTravel = TravelEntity(
        id = 7L,
        startUtcMillis = 0L,
        endUtcMillis = null,
        travelTimeZoneId = "UTC",
        title = null
    )

    private fun repository(
        travelDao: TravelDao,
        checkpointDao: StepCheckpointDao = mock(),
        summaryDao: StepDailySummaryDao = mock(),
        clock: () -> Long = { 1_000L }
    ) = StepRepositoryImpl(
        stepCheckpointDao = checkpointDao,
        stepDailySummaryDao = summaryDao,
        travelDao = travelDao,
        timeZoneIdProvider = { "UTC" },
        clock = clock,
        userIdProvider = { "u1" },
        strideLengthMeters = 0.75
    )

    /**
     * Verifica che la differenza tra due letture cumulative venga accumulata
     * nella sintesi giornaliera del viaggio attivo (distanza stimata dal passo
     * medio). La prima lettura del processo fissa solo il baseline (delta 0).
     */
    @Test
    fun onStepCount_accumulatesDeltaIntoDailySummary() = runTest {
        val travelDao = mock<TravelDao>()
        whenever(travelDao.activeTravelForUserOnce("u1")).thenReturn(activeTravel)
        val summaryDao = mock<StepDailySummaryDao>()
        whenever(summaryDao.summaryForTravelAndDateOnce(7L, "1970-01-01", "UTC")).thenReturn(null)
        val checkpointDao = mock<StepCheckpointDao>()
        whenever(checkpointDao.checkpointForTravelOnce(7L)).thenReturn(null)

        val repo = repository(travelDao, checkpointDao, summaryDao)
        repo.onStepCount(1_000L) // baseline: delta 0
        repo.onStepCount(1_240L) // delta 240

        verify(summaryDao, times(1)).upsert(any())
        verify(summaryDao).upsert(argThat { steps == 240L && distanceMeters == 240.0 * 0.75 })
        verify(checkpointDao, times(1)).upsert(any())
    }

    /**
     * Verifica che una lettura inferiore alla precedente (riavvio del device,
     * il contatore riparte da zero) NON produca passi negativi: si fa solo
     * rebaseline e non si scrive nulla di errato.
     */
    @Test
    fun onStepCount_sensorResetReBaselinesWithoutNegativeSteps() = runTest {
        val travelDao = mock<TravelDao>()
        whenever(travelDao.activeTravelForUserOnce("u1")).thenReturn(activeTravel)
        val summaryDao = mock<StepDailySummaryDao>()
        val checkpointDao = mock<StepCheckpointDao>()
        whenever(checkpointDao.checkpointForTravelOnce(7L)).thenReturn(
            StepCheckpointEntity(
                travelId = 7L,
                lastSensorValue = 5_000L,
                lastCheckpointUtcMillis = 100L,
                lastKnownLocalDate = "1970-01-01",
                lastKnownTimeZoneId = "UTC"
            )
        )

        val repo = repository(travelDao, checkpointDao, summaryDao)
        // Reboot: the counter restarts well below the previous checkpoint.
        repo.onStepCount(120L)

        verify(summaryDao, never()).upsert(any())
        verify(checkpointDao, never()).upsert(any())
    }

    /**
     * Verifica che un delta positivo possa CREARE da solo il viaggio attivo
     * (niente GPS ancora): i passi non vanno mai persi, contano app open o in
     * background, offline o online.
     */
    @Test
    fun onStepCount_createsTravelWhenNoneActive() = runTest {
        val travelDao = mock<TravelDao>()
        whenever(travelDao.activeTravelForUserOnce("u1")).thenReturn(null)
        val summaryDao = mock<StepDailySummaryDao>()
        // The created travel gets id 0 from the mocked insert (a Long, not null).
        whenever(travelDao.insert(any())).thenReturn(0L)
        whenever(summaryDao.summaryForTravelAndDateOnce(0L, "1970-01-01", "UTC")).thenReturn(null)
        val checkpointDao = mock<StepCheckpointDao>()

        val repo = repository(travelDao, checkpointDao, summaryDao)
        repo.onStepCount(1_000L) // baseline
        repo.onStepCount(1_030L) // delta 30 -> creates the trip and records

        verify(travelDao).insert(any())
        verify(checkpointDao, times(1)).upsert(any())
        verify(summaryDao).upsert(argThat { travelId == 0L && steps == 30L })
    }

    /**
     * Verifica che fermare un viaggio e iniziarne un altro non trasferisca nel
     * nuovo viaggio il delta del contatore cumulativo del sensore.
     */
    @Test
    fun onStepCount_newTravelStartsWithCleanBaseline() = runTest {
        val travelDao = mock<TravelDao>()
        val newTravel = activeTravel.copy(id = 8L)
        whenever(travelDao.activeTravelForUserOnce("u1")).thenReturn(
            activeTravel,
            activeTravel,
            newTravel,
            newTravel
        )
        val summaryDao = mock<StepDailySummaryDao>()
        whenever(summaryDao.summaryForTravelAndDateOnce(any(), any(), any())).thenReturn(null)
        val checkpointDao = mock<StepCheckpointDao>()
        whenever(checkpointDao.checkpointForTravelOnce(8L)).thenReturn(null)

        val repo = repository(travelDao, checkpointDao, summaryDao)
        repo.onStepCount(1_000L) // first trip baseline
        repo.onStepCount(1_100L) // first trip: +100
        repo.onStepCount(5_000L) // new trip baseline, not +3,900
        repo.onStepCount(5_050L) // new trip: +50

        verify(summaryDao, times(2)).upsert(any())
        verify(summaryDao).upsert(argThat { travelId == 7L && steps == 100L })
        verify(summaryDao).upsert(argThat { travelId == 8L && steps == 50L })
    }

    /**
     * Verifica che un cambio di giorno crei un nuovo bucket: la sintesi del
     * giorno precedente resta intatta e il totale del viaggio resta esatto.
     */
    @Test
    fun onStepCount_dayChangeCreatesNewBucket() = runTest {
        val travelDao = mock<TravelDao>()
        whenever(travelDao.activeTravelForUserOnce("u1")).thenReturn(activeTravel)
        val summaryDao = mock<StepDailySummaryDao>()
        whenever(summaryDao.summaryForTravelAndDateOnce(7L, "1970-01-01", "UTC")).thenReturn(null)
        whenever(summaryDao.summaryForTravelAndDateOnce(7L, "1970-01-02", "UTC")).thenReturn(null)
        val checkpointDao = mock<StepCheckpointDao>()
        whenever(checkpointDao.checkpointForTravelOnce(7L)).thenReturn(null)

        var now = 1_000L // 1970-01-01
        val repo = repository(travelDao, checkpointDao, summaryDao, clock = { now })

        repo.onStepCount(100L) // baseline
        repo.onStepCount(160L) // day 1, delta 60

        now = 86_500_000L // 1970-01-02
        repo.onStepCount(200L) // day 2, delta 40

        verify(summaryDao, times(2)).upsert(any())
        verify(summaryDao).upsert(argThat { localDate == "1970-01-01" && steps == 60L })
        verify(summaryDao).upsert(argThat { localDate == "1970-01-02" && steps == 40L })
    }
}