package com.zickat.shopifymcpserver.shared_kernel

/**
 * Identifie l'identité (opérateur) porteuse d'une requête au sein d'un [TenantContext] donné, et
 * le rôle que son grant lui donne sur cette boutique — schema.md §6.
 *
 * Même garantie de construction que [TenantContext] : un seul point,
 * [com.zickat.shopifymcpserver.tenancy.domain.AccessResolutionUseCase] — voir
 * `TenantUserContextConstructionTest`.
 */
data class UserContext(val identityId: String, val role: AccessRole)
