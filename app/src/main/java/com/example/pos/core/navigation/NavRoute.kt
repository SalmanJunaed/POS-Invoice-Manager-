package com.example.pos.core.navigation

/**
 * Type-safe navigation routes for the POS & Invoice Manager.
 */
sealed class NavRoute(val route: String) {
    object Auth : NavRoute("auth")
    object Billing : NavRoute("billing")
    object History : NavRoute("history")
    object Products : NavRoute("products")
    object Reports : NavRoute("reports")
    object Settings : NavRoute("settings")
    object InvoiceDetail : NavRoute("invoice_detail/{orderId}") {
        fun createRoute(orderId: String) = "invoice_detail/$orderId"
    }

    companion object {
        val bottomNavItems = listOf(Billing, History, Products, Reports, Settings)
    }
}
