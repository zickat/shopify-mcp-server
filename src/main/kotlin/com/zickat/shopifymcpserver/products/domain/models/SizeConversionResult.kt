package com.zickat.shopifymcpserver.products.domain.models

sealed interface SizeConversionResult {
    data class Covered(val euSize: Double, val sourceNote: String) : SizeConversionResult
    data class Blocked(val reason: String) : SizeConversionResult
}
