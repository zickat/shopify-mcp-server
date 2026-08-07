package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService

fun AccessExposedService.slugFor(identityId: String, storeId: String): String =
    listGrantedStores(identityId).fold({ emptyList() }, { it })
        .firstOrNull { it.storeId == storeId }
        ?.slug
        ?: storeId
