package com.example.progmob_progetto_melluso.trend

private const val HOUR_MILLIS = 3_600_000L

/**
 * Time window of the trends ranking: what happened in the last day, week,
 * month or year (rolling windows, the usual "trending" semantics).
 *
 * How it works: each period knows how far back its window starts from a given
 * "now", so the DAO aggregation can filter the votes by timestamp. The window
 * has no upper bound on purpose: new votes always fall inside it, so the
 * ranking updates live as soon as someone votes.
 */
enum class TrendPeriod(val windowMillis: Long) {

    /** Votes received in the last 24 hours. */
    DAY(24L * HOUR_MILLIS),

    /** Votes received in the last 7 days. */
    WEEK(7L * 24L * HOUR_MILLIS),

    /** Votes received in the last 30 days. */
    MONTH(30L * 24L * HOUR_MILLIS),

    /** Votes received in the last 365 days. */
    YEAR(365L * 24L * HOUR_MILLIS);

    /** Start of the window (exclusive lower bound) for the given "now". */
    fun startUtcMillis(nowMillis: Long): Long = nowMillis - windowMillis
}
