package com.example.pos.core.error

/**
 * Functional Result monad for clean, exception-free error propagation.
 */
sealed class PosResult<out T> {
    data class Success<out T>(val data: T) : PosResult<T>()
    data class Error(val error: PosError) : PosResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    inline fun <R> map(transform: (T) -> R): PosResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> Error(error)
    }

    inline fun onSuccess(action: (T) -> Unit): PosResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (PosError) -> Unit): PosResult<T> {
        if (this is Error) action(error)
        return this
    }
}
