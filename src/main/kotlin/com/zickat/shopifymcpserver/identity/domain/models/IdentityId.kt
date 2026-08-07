package com.zickat.shopifymcpserver.identity.domain.models

/** Wrapper de domaine autour de l'`ObjectId` Mongo — le domaine ne dépend jamais du driver Mongo. */
data class IdentityId(val value: String)
