package com.example.pos.core.error

/**
 * Sealed hierarchy for application errors across the POS architecture.
 * Ensures robust error handling without unhandled runtime crashes.
 */
sealed class PosError(val message: String, val cause: Throwable? = null) {

    // Database & Persistence Errors
    class DatabaseError(message: String, cause: Throwable? = null) : PosError(message, cause)
    class RecordNotFound(entity: String, id: String) : PosError("$entity with id '$id' was not found.")
    class DuplicateRecord(entity: String, id: String) : PosError("$entity with id '$id' already exists.")

    // Validation & Calculation Errors
    class ValidationError(message: String) : PosError(message)
    class InvalidQuantity(message: String = "Quantity must be a positive decimal.") : PosError(message)
    class InvalidPrice(message: String = "Price must be a valid positive amount.") : PosError(message)

    // Hardware / Bluetooth Printer Errors
    class PrinterNotConnected(message: String = "Thermal printer is not connected.") : PosError(message)
    class PrinterCommunicationError(message: String, cause: Throwable? = null) : PosError(message, cause)
    class BluetoothPermissionDenied : PosError("Bluetooth permissions are required for thermal printing.")

    // Authentication Errors
    class InvalidCredentials : PosError("Invalid phone number or password.")
    class UserAlreadyExists : PosError("An operator with this phone number already exists.")
    class Unauthenticated : PosError("No active operator session found.")

    // XLSX Sync & Import/Export Errors
    class SyncConflictError(message: String) : PosError(message)
    class CorruptedFileError(message: String, cause: Throwable? = null) : PosError(message, cause)

    // General / Unknown Errors
    class UnexpectedError(message: String, cause: Throwable? = null) : PosError(message, cause)
}
