package com.example.progmob_progetto_melluso.travel

import com.example.progmob_progetto_melluso.data.entities.TravelPathPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PathSegmenter]: verifies that the travel path is split into
 * separate segments only across real tracking interruptions, and stays
 * connected through legitimate long stops.
 */
class PathSegmenterTest {

    private fun point(lat: Double, lon: Double, timeMillis: Long) = TravelPathPointEntity(
        travelId = 1L,
        latitude = lat,
        longitude = lon,
        recordedAtUtcMillis = timeMillis
    )

    /** ~111 m east of (0,0). */
    private val farPoint = point(0.0, 0.001, 0L)

    @Test
    fun split_returnsEmptyForShortPaths() {
        assertEquals(emptyList<List<TravelPathPointEntity>>(), PathSegmenter.split(emptyList()))
        assertEquals(
            emptyList<List<TravelPathPointEntity>>(),
            PathSegmenter.split(listOf(point(0.0, 0.0, 0L)))
        )
    }

    @Test
    fun split_breaksAcrossInterruption() {
        // Walk, then a 10-minute gap during which the user moves ~1.1 km away
        // (app killed and reopened): the two parts must be separate segments.
        val points = listOf(
            point(0.0, 0.0, 0L),
            point(0.0004, 0.0, 30_000L),
            // 600 s later, ~1.1 km away.
            point(0.0, 0.010, 630_000L),
            point(0.0, 0.011, 650_000L)
        )

        val segments = PathSegmenter.split(points)

        assertEquals(2, segments.size)
        assertEquals(listOf(0L, 30_000L), segments[0].map { it.recordedAtUtcMillis })
        assertEquals(listOf(630_000L, 650_000L), segments[1].map { it.recordedAtUtcMillis })
    }

    @Test
    fun split_keepsConnectedThroughLongStop() {
        // A long stop (museum visit) with no movement, then the walk resumes
        // near the same spot: no interruption, one continuous segment.
        val points = listOf(
            point(0.0, 0.0, 0L),
            // 30 minutes later, only ~11 m away (still around the stop).
            point(0.0001, 0.0, 1_800_000L)
        )

        val segments = PathSegmenter.split(points)

        assertEquals(1, segments.size)
        assertEquals(2, segments[0].size)
    }

    @Test
    fun split_keepsConnectedOnQuickJump() {
        // A large distance covered in a short time (fast driving) is real
        // tracked movement: the path must stay connected even if the jump is big.
        val points = listOf(
            point(0.0, 0.0, 0L),
            // 20 s later, ~2.2 km away.
            farPoint.copy(recordedAtUtcMillis = 20_000L)
        )

        val segments = PathSegmenter.split(points)

        assertEquals(1, segments.size)
    }

    @Test
    fun distanceMeters_sumsContinuousGpsSegments() {
        val points = listOf(
            point(0.0, 0.0, 0L),
            point(0.0, 0.001, 20_000L)
        )

        val distance = PathSegmenter.distanceMeters(points)

        assertEquals(111.0, distance, 2.0)
    }

    @Test
    fun distanceMeters_ignoresUntrackedJump() {
        val points = listOf(
            point(0.0, 0.0, 0L),
            point(0.0, 0.001, 20_000L),
            point(0.0, 0.010, 630_000L),
            point(0.0, 0.011, 650_000L)
        )

        val distance = PathSegmenter.distanceMeters(points)

        // Only the two tracked ~111 m segments are counted; the ~1 km jump
        // during the interruption is deliberately excluded.
        assertTrue(distance in 215.0..230.0)
    }

    @Test
    fun split_dropsSinglePointTrailingSegments() {
        // Interruption followed by a single isolated point: that fragment has
        // no drawable line, so it is dropped instead of rendering a dot.
        val points = listOf(
            point(0.0, 0.0, 0L),
            point(0.0004, 0.0, 30_000L),
            // 10 minutes later, ~1.1 km away, single point before tracking dies again.
            point(0.0, 0.010, 630_000L)
        )

        val segments = PathSegmenter.split(points)

        assertEquals(1, segments.size)
        assertEquals(listOf(0L, 30_000L), segments[0].map { it.recordedAtUtcMillis })
    }
}
