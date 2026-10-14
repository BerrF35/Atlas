package com.berrf35.atlas.core.common

sealed interface AtlasResult<out T> {
    data class Ok<T>(val value: T) : AtlasResult<T>
    data class Err(val error: AtlasError) : AtlasResult<Nothing>

    val isOk: Boolean get() = this is Ok
    val isErr: Boolean get() = this is Err

    fun getOrNull(): T? = when (this) {
        is Ok -> value
        is Err -> null
    }

    fun getOrThrow(): T = when (this) {
        is Ok -> value
        is Err -> error("AtlasResult was Err: ${error.code} - ${error.message}")
    }
}

enum class Recoverability {
    RECOVERABLE,
    USER_ACTION_REQUIRED,
    FATAL
}

sealed interface AtlasError {
    val code: String
    val message: String
    val recoverability: Recoverability

    data class PermissionMissing(
        val permission: String,
        override val message: String = "Required permission $permission is missing",
        override val recoverability: Recoverability = Recoverability.USER_ACTION_REQUIRED
    ) : AtlasError {
        override val code: String = "PERMISSION_MISSING"
    }

    data class ProviderDisabled(
        val provider: String,
        override val message: String = "Location provider $provider is disabled",
        override val recoverability: Recoverability = Recoverability.USER_ACTION_REQUIRED
    ) : AtlasError {
        override val code: String = "PROVIDER_DISABLED"
    }

    data class ForegroundStartDenied(
        override val message: String = "Foreground service start was denied by the OS",
        override val recoverability: Recoverability = Recoverability.USER_ACTION_REQUIRED
    ) : AtlasError {
        override val code: String = "FOREGROUND_START_DENIED"
    }

    data class StorageFull(
        override val message: String = "Device storage is full",
        override val recoverability: Recoverability = Recoverability.USER_ACTION_REQUIRED
    ) : AtlasError {
        override val code: String = "STORAGE_FULL"
    }

    data class NotFound(
        val entity: String,
        val id: String,
        override val message: String = "$entity with id $id was not found",
        override val recoverability: Recoverability = Recoverability.RECOVERABLE
    ) : AtlasError {
        override val code: String = "NOT_FOUND"
    }

    data class InvalidInput(
        override val message: String,
        override val recoverability: Recoverability = Recoverability.RECOVERABLE
    ) : AtlasError {
        override val code: String = "INVALID_INPUT"
    }

    data class ExportFailed(
        override val message: String,
        override val recoverability: Recoverability = Recoverability.RECOVERABLE
    ) : AtlasError {
        override val code: String = "EXPORT_FAILED"
    }

    data class Cancelled(
        override val message: String = "Operation was cancelled",
        override val recoverability: Recoverability = Recoverability.RECOVERABLE
    ) : AtlasError {
        override val code: String = "CANCELLED"
    }

    data class Generic(
        override val code: String = "GENERIC_ERROR",
        override val message: String,
        override val recoverability: Recoverability = Recoverability.RECOVERABLE
    ) : AtlasError
}
