package com.example.progmob_progetto_melluso.travel

/**
 * A single location reading produced by the sensor layer and consumed by the
 * travel repository.
 *
 * Kept free of Android location types so the repository stays unit-testable on
 * the JVM with plain JUnit/Mockito.
 *
 * @property latitude Latitude in degrees.
 * @property longitude Longitude in degrees.
 * @property recordedAtUtcMillis Fix time in UTC millis.
 * @property accuracyMeters Optional horizontal accuracy reported by the provider.
 */
data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val recordedAtUtcMillis: Long,
    val accuracyMeters: Float? = null
)
