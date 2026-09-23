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
 *
 * Every reading states WHEN it was taken, because the repository must date the
 * steps with the instant the sensor reports and not with the moment the event
 * is processed (see SensorTime): the day-bucketing cases below are written on
 * that value, not on "now".
 */
class StepRepositoryImplTest {

    /** A reading taken 1 s after the epoch: the reference time of most cases. */
    private val readingTime = 1_000L

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
        summaryDao: StepDailySummaryDao = mock()
    ) = StepRepositoryImpl(
        stepCheckpointDao = checkpointDao,
        stepDailySummaryDao = summaryDao,
        travelDao = travelDao,
        timeZoneIdProvider = { "UTC" },
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
        repo.onStepCount(1_000L, readingTime) // baseline: delta 0
        repo.onStepCount(1_240L, readingTime + 1_000L) // delta 240

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
        repo.onStepCount(120L, readingTime)

        verify(summaryDao, never()).upsert(any())
        verify(checkpointDao, never()).upsert(any())
    }

    /**
     * Verifica che i passi NON creino più un viaggio da soli: senza viaggio
     * attivo la lettura fissa solo il baseline e nessun passo viene scritto
     * (la creazione del viaggio è manuale o dell'auto-start della mappa).
     */
    @Test
    fun onStepCount_doesNotCreateTravelWhenNoneActive() = runTest {
        val travelDao = mock<TravelDao>()
        whenever(travelDao.activeTravelForUserOnce("u1")).thenReturn(null)
        val summaryDao = mock<StepDailySummaryDao>()
        val checkpointDao = mock<StepCheckpointDao>()

        val repo = repository(travelDao, checkpointDao, summaryDao)
        repo.onStepCount(1_000L, readingTime) // baseline, no trip
        repo.onStepCount(1_030L, readingTime + 1_000L) // positive delta but STILL no trip

        verify(travelDao, never()).insert(any())
        verify(checkpointDao, never()).upsert(any())
        verify(summaryDao, never()).upsert(any())
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
        repo.onStepCount(1_000L, readingTime) // first trip baseline
        repo.onStepCount(1_100L, readingTime + 1_000L) // first trip: +100
        repo.onStepCount(5_000L, readingTime + 2_000L) // new trip baseline, not +3,900
        repo.onStepCount(5_050L, readingTime + 3_000L) // new trip: +50

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

        val repo = repository(travelDao, checkpointDao, summaryDao)

        repo.onStepCount(100L, readingTime) // baseline, day 1
        repo.onStepCount(160L, readingTime + 60_000L) // day 1, delta 60

        repo.onStepCount(200L, 86_500_000L) // 1970-01-02, delta 40

        verify(summaryDao, times(2)).upsert(any())
        verify(summaryDao).upsert(argThat { localDate == "1970-01-01" && steps == 60L })
        verify(summaryDao).upsert(argThat { localDate == "1970-01-02" && steps == 40L })
    }

    /**
     * Verifica che il timestamp salvato sia quello della LETTURA e non quello
     * dell'elaborazione: senza questa distinzione una consegna in ritardo (main
     * thread occupato, device in doze) daterebbe i passi più tardi di quanto
     * sono stati camminati.
     */
    @Test
    fun onStepCount_stampsTheReadingTime_notTheProcessingTime() = runTest {
        val travelDao = mock<TravelDao>()
        whenever(travelDao.activeTravelForUserOnce("u1")).thenReturn(activeTravel)
        val summaryDao = mock<StepDailySummaryDao>()
        whenever(summaryDao.summaryForTravelAndDateOnce(any(), any(), any())).thenReturn(null)
        val checkpointDao = mock<StepCheckpointDao>()
        whenever(checkpointDao.checkpointForTravelOnce(7L)).thenReturn(null)

        // The reading was taken 60 s after the epoch, and processed "later".
        val takenAt = 60_000L
        val repo = repository(travelDao, checkpointDao, summaryDao)
        repo.onStepCount(100L, takenAt)
        repo.onStepCount(150L, takenAt + 1_000L)

        verify(summaryDao).upsert(argThat { updatedAtUtcMillis == takenAt + 1_000L && steps == 50L })
        verify(checkpointDao).upsert(argThat { lastCheckpointUtcMillis == takenAt + 1_000L })
    }

    /**
     * Verifica che i passi restino nel giorno in cui sono stati CAMMINATI: una
     * lettura presa prima della mezzanotte ma consegnata dopo deve restare nel
     * giorno precedente, altrimenti il totale di entrambi i giorni sarebbe
     * sbagliato.
     */
    @Test
    fun onStepCount_lateReadingStaysInTheDayItWasWalked() = runTest {
        val travelDao = mock<TravelDao>()
        whenever(travelDao.activeTravelForUserOnce("u1")).thenReturn(activeTravel)
        val summaryDao = mock<StepDailySummaryDao>()
        whenever(summaryDao.summaryForTravelAndDateOnce(7L, "1970-01-01", "UTC")).thenReturn(null)
        val checkpointDao = mock<StepCheckpointDao>()
        whenever(checkpointDao.checkpointForTravelOnce(7L)).thenReturn(null)

        // 23:59:30 of day 1: walked before midnight, delivered right after it.
        val justBeforeMidnight = 86_370_000L
        val repo = repository(travelDao, checkpointDao, summaryDao)
        repo.onStepCount(100L, justBeforeMidnight - 30_000L)
        repo.onStepCount(180L, justBeforeMidnight)

        verify(summaryDao).upsert(argThat { localDate == "1970-01-01" && steps == 80L })
    }
}