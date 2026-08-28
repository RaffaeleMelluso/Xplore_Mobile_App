package com.example.progmob_progetto_melluso.trend

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifica che ogni periodo apra la finestra di aggregazione corretta
 * (finestre scorrevoli: ultime 24 h, 7 giorni, 30 giorni, 365 giorni).
 */
class TrendPeriodTest {

    private val now = 1_000_000_000_000L

    @Test
    fun day_windowIsLast24Hours() {
        assertEquals(now - 24L * 3_600_000L, TrendPeriod.DAY.startUtcMillis(now))
    }

    @Test
    fun week_windowIsLast7Days() {
        assertEquals(now - 7L * 24L * 3_600_000L, TrendPeriod.WEEK.startUtcMillis(now))
    }

    @Test
    fun month_windowIsLast30Days() {
        assertEquals(now - 30L * 24L * 3_600_000L, TrendPeriod.MONTH.startUtcMillis(now))
    }

    @Test
    fun year_windowIsLast365Days() {
        assertEquals(now - 365L * 24L * 3_600_000L, TrendPeriod.YEAR.startUtcMillis(now))
    }
}
