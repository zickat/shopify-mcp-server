package com.zickat.shopifymcpserver.tenancy.domain.models

import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.UserContext

/**
 * Résultat d'une résolution d'accès réussie — schema.md §2, l'état « autorisé » du diagramme de
 * séquence. Regroupe le [TenantContext] et l'[UserContext] construits par
 * [com.zickat.shopifymcpserver.tenancy.domain.AccessResolutionUseCase] — jamais ailleurs, voir
 * `TenantUserContextConstructionTest`.
 */
data class AccessContext(val tenant: TenantContext, val user: UserContext)
