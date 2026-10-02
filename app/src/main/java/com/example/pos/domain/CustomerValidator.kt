package com.example.pos.domain

/**
 * Validates and sanitizes customer_info according to the approved PRD:
 * - Primary alphanumeric field supporting:
 *   - Numbers (e.g. "01700000000")
 *   - English letters (e.g. "ABC-123", "CUSTOMER-001")
 *   - Bangla text (e.g. "করিম আহমেদ", "গাড়ী-১২৩৪")
 *   - Mixed alphanumeric values (e.g. "ঢাকা মেট্রো গ-১১-২২৩৩", "CAR-882")
 * - Max length reasonable for single-line invoice header (e.g. 100 chars)
 * - Safe sanitization to prevent control characters while preserving unicode letters, digits, and common separators (- / . space , #)
 */
object CustomerValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val sanitizedValue: String,
        val errorMessage: String? = null
    )

    fun validate(input: String?): ValidationResult {
        if (input.isNullOrBlank()) {
            return ValidationResult(
                isValid = true,
                sanitizedValue = "",
                errorMessage = null
            )
        }

        val trimmed = input.trim()
        if (trimmed.length > 100) {
            return ValidationResult(
                isValid = false,
                sanitizedValue = trimmed.take(100),
                errorMessage = "Customer identifier must not exceed 100 characters"
            )
        }

        // Allow English, digits, Bangla (U+0980 to U+09FF includes consonants, vowels, matras, hasant, nukta),
        // spaces, and standard punctuation delimiters (- / . , # : _)
        // Also supports Unicode general categories Letter, Number, Mark (combining vowel diacritics)
        val validPattern = Regex("^[\\p{L}\\p{M}\\p{N}\\s\\-_/.,#:]+$")
        return if (validPattern.matches(trimmed)) {
            ValidationResult(
                isValid = true,
                sanitizedValue = trimmed,
                errorMessage = null
            )
        } else {
            // Filter out control or unsafe characters while preserving valid letters, marks, digits, spaces, and separators
            val sanitized = trimmed.filter { ch ->
                ch.isLetterOrDigit() || ch.isWhitespace() || ch in "-_/.,#:" ||
                        Character.getType(ch).toByte() in listOf(
                            Character.NON_SPACING_MARK,
                            Character.COMBINING_SPACING_MARK,
                            Character.ENCLOSING_MARK
                        )
            }
            ValidationResult(
                isValid = sanitized.isNotBlank(),
                sanitizedValue = sanitized,
                errorMessage = if (sanitized.isBlank()) "Contains invalid characters" else null
            )
        }
    }

    /**
     * Resolves display customer info with default fallback if empty.
     */
    fun resolveForInvoice(input: String, defaultPhone: String = "01700000000"): String {
        val result = validate(input)
        return if (result.sanitizedValue.isNotBlank()) {
            result.sanitizedValue
        } else {
            defaultPhone
        }
    }
}
