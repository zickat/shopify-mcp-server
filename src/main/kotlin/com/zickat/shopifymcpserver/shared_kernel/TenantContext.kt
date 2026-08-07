package com.zickat.shopifymcpserver.shared_kernel

/**
 * Identifie la boutique porteuse d'une requête — backend.md §Multi-tenancy : toute requête MongoDB
 * filtre obligatoirement sur `storeId`, jamais accepté en paramètre HTTP. schema.md §2, invariant
 * 1 : « la boutique n'est jamais un paramètre d'outil » — elle vient exclusivement d'ici.
 *
 * **Un seul point de construction dans tout le dépôt** :
 * [com.zickat.shopifymcpserver.tenancy.domain.AccessResolutionUseCase], et seulement après
 * vérification d'un grant actif. Garanti structurellement par `TenantUserContextConstructionTest`
 * (`shared_kernel`, `LOT0-06`) — pas seulement par convention : un futur `TenantContext(...)`
 * construit ailleurs (par ex. depuis un `storeId` lu dans le corps JSON d'un outil) ferait échouer
 * ce test.
 */
data class TenantContext(val storeId: String)
