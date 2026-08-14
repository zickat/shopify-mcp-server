@file:NamedInterface("exposed_interface")

package com.zickat.shopifymcpserver.tenancy.exposed_interface

import org.springframework.modulith.NamedInterface

fun AccessExposedService.slugFor(identityId: String, storeId: String): String =
    listGrantedStores(identityId).fold({ emptyList() }, { it })
        .firstOrNull { it.storeId == storeId }
        ?.slug
        ?: storeId
