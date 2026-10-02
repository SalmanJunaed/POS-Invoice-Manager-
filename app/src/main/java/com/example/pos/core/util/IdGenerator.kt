package com.example.pos.core.util

import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Deterministic Transaction Identity & ID Generator.
 *
 * Distinguishes between:
 * 1. Globally Unique Internal ID (UUID / ULID): Guaranteed globally unique across all offline branch devices.
 * 2. Human-Readable Display Order ID: Clean format for receipts (e.g., "INV-10294" or "DHK01-260908-042").
 */
object IdGenerator {

    private val random = SecureRandom()
    private val dateFormat = SimpleDateFormat("yyMMdd", Locale.US)

    /**
     * Generates a collision-free global transaction ID for multi-branch XLSX sync.
     */
    fun generateGlobalTransactionId(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * Formats a human-readable display Order ID based on sequence and branch.
     */
    fun generateDisplayOrderId(sequence: Long, branchNumber: String = "01"): String {
        val datePart = dateFormat.format(Date())
        val seqPart = sequence.toString().padStart(4, '0')
        return "INV-$seqPart"
    }

    /**
     * Generates a branch-qualified display ID if branch code is known.
     */
    fun generateBranchQualifiedOrderId(branchCode: String, sequence: Long): String {
        val datePart = dateFormat.format(Date())
        val seqPart = sequence.toString().padStart(4, '0')
        return "$branchCode-$datePart-$seqPart"
    }
}
