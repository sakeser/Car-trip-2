package com.cartrip.analyzer.record

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Live, in-memory state shared between the recording service and the UI. */
object RecordingState {

    data class Live(
        val recording: Boolean = false,
        val tripId: Long = 0,
        val startTime: Long = 0,
        val elapsedS: Long = 0,
        val distanceM: Double = 0.0,
        val speedKmh: Double = 0.0,
        val maxSpeedKmh: Double = 0.0,
        val hardBrake: Int = 0,
        val hardAccel: Int = 0,
        val hardCorner: Int = 0,
        val gpsFixes: Int = 0,
        val completedTripId: Long? = null
    )

    private val _state = MutableStateFlow(Live())
    val state: StateFlow<Live> = _state

    fun update(block: (Live) -> Live) {
        _state.update(block)
    }

    fun reset() {
        val completed = _state.value.completedTripId
        _state.value = Live(completedTripId = completed)
    }

    fun completeTrip(id: Long) {
        _state.update { Live(completedTripId = id) }
    }

    fun consumeCompletedTrip() {
        _state.update { it.copy(completedTripId = null) }
    }
}
