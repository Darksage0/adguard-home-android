package com.adguard.home.domain.model

sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class Error(val errorType: NetworkErrorType, val message: String? = null, val retryAfterSeconds: Int? = null) : NetworkResult<Nothing>
    data object Loading : NetworkResult<Nothing>
}

enum class NetworkErrorType {
    UNREACHABLE,
    UNAUTHORIZED,
    RATE_LIMITED,
    INVALID_RESPONSE,
    TLS_ERROR,
    SERVER_ERROR,
    UNKNOWN
}

inline fun <T, R> NetworkResult<T>.map(transform: (T) -> R): NetworkResult<R> {
    return when (this) {
        is NetworkResult.Success -> NetworkResult.Success(transform(data))
        is NetworkResult.Error -> this
        is NetworkResult.Loading -> NetworkResult.Loading
    }
}
