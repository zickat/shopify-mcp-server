package com.zickat.shopifymcpserver.shared_kernel

/**
 * Identifie l'utilisateur (opérateur) porteur d'une requête, au sein d'un [TenantContext] donné.
 *
 * Lot 0 : le type existe, sa résolution depuis un jeton OAuth arrive en LOT0-05/LOT0-06
 * (AuthenticationFilter / CredentialResolver — security.md).
 */
data class UserContext(val userId: String)
