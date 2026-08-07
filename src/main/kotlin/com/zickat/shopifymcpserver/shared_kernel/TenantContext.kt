package com.zickat.shopifymcpserver.shared_kernel

/**
 * Identifie la boutique (tenant) porteuse d'une requête. backend.md §Multi-tenancy : toute requête
 * MongoDB filtre obligatoirement sur `tenantId`, jamais accepté en paramètre HTTP.
 *
 * Lot 0 : le type existe, sa résolution depuis un jeton OAuth arrive en LOT0-05/LOT0-06
 * (AuthenticationFilter / CredentialResolver — security.md).
 */
data class TenantContext(val tenantId: String)
