package com.zickat.shopifymcpserver.collections.domain.models

sealed interface CollectionCoherenceVerdict {
    data object Coherent : CollectionCoherenceVerdict
    data class Incoherent(val reason: String) : CollectionCoherenceVerdict
}
