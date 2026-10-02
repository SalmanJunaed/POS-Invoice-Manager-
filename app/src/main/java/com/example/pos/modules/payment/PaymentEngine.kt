package com.example.pos.modules.payment

import com.example.pos.domain.FinancialCalculationEngine
import com.example.pos.domain.model.PaymentMethod
import com.example.pos.domain.model.PaymentRecord

/**
 * Payments module foundation.
 * Handles payment verification, tendered cash and change calculations, and MFS reference tagging.
 */
object PaymentEngine {

    fun calculateChangePaisa(tenderedPaisa: Long, totalDuePaisa: Long): Long {
        if (tenderedPaisa <= totalDuePaisa) return 0L
        return tenderedPaisa - totalDuePaisa
    }

    fun validatePayment(method: PaymentMethod, amountPaidPaisa: Long, reference: String?): Boolean {
        if (amountPaidPaisa <= 0L) return false
        return when (method) {
            PaymentMethod.MFS_BKASH, PaymentMethod.MFS_NAGAD -> !reference.isNullOrBlank()
            PaymentMethod.CARD -> !reference.isNullOrBlank()
            PaymentMethod.CASH, PaymentMethod.CREDIT -> true
        }
    }

    fun formatPaymentMethodDisplay(method: PaymentMethod, language: String): String {
        return when (method) {
            PaymentMethod.CASH -> if (language == "bn") "নগদ (Cash)" else "Cash"
            PaymentMethod.CARD -> if (language == "bn") "কার্ড (Card)" else "Card"
            PaymentMethod.MFS_BKASH -> "bKash"
            PaymentMethod.MFS_NAGAD -> "Nagad"
            PaymentMethod.CREDIT -> if (language == "bn") "বাকি (Credit)" else "Credit"
        }
    }
}
