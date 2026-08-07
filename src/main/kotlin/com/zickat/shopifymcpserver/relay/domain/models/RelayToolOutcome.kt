package com.zickat.shopifymcpserver.relay.domain.models

data class RelayToolOutcome(
    val content: List<RelayContentBlock>,
    val isError: Boolean,
)

data class RelayContentBlock(val text: String)
