package com.cartrip.analyzer.cloud

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

object CloudState {
    data class S(
        val email: String? = null,
        val syncing: Boolean = false,
        val lastMessage: String? = null
    )

    private val _state = MutableStateFlow(S())
    val state: StateFlow<S> = _state

    fun set(block: (S) -> S) {
        _state.update(block)
    }
}
