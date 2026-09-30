package com.rjbiermann.giffyviewer.core.model

/**
 * Immutable UiState contract: every screen state is one of these.
 * `error` carries a user-presentable message; retry re-triggers the same load.
 */
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>

    data object Loading : UiState<Nothing>

    data class Ready<T>(
        val data: T,
    ) : UiState<T>

    data class Error(
        val message: String,
        val retryable: Boolean = true,
    ) : UiState<Nothing>
}
