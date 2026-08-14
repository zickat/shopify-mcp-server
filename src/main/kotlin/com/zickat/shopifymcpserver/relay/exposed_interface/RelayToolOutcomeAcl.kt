package com.zickat.shopifymcpserver.relay.exposed_interface

data class RelayContentBlockAcl(val text: String)

data class RelayToolOutcomeAcl(val content: List<RelayContentBlockAcl>, val isError: Boolean)
