package com.example.progmob_progetto_melluso.service

import com.example.progmob_progetto_melluso.travel.LocationSample
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide bridge between the sensor layer and the UI layer.
 *
 * How it works: [TrackingService] publishes every location fix coming from the
 * fused location provider; the map layer collects [samples] to render the
 * current position and the ghosts. The travel repository does NOT read from
 * here: the service feeds it directly, so the path pipeline keeps working even
 * when the UI is not visible.
 *
 * `replay = 1` makes a new subscriber (e.g. the map screen after a process
 * restart) receive the latest fix immediately instead of waiting for the next
 * one, while `extraBufferCapacity` keeps the emission non-blocking.
 */
object LocationUpdates {

    private val _samples = MutableSharedFlow<LocationSample>(replay = 1, extraBufferCapacity = 1)

    /** Continuous stream of the latest location fixes. */
    val samples: SharedFlow<LocationSample> = _samples.asSharedFlow()

    /**
     * Publishes a new location fix to every current subscriber.
     *
     * @param sample the provider-agnostic location reading to broadcast.
     */
    fun publish(sample: LocationSample) {
        _samples.tryEmit(sample)
    }
}
