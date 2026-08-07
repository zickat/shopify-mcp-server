package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

/** Résultat de [WhoAmITool.whoami] — jamais le jeton ni aucun secret, uniquement des identifiants métier. */
data class WhoAmIResult(val identityId: String, val storeId: String, val role: String)

/**
 * `LOT0-08` — l'outil trivial qui exerce la chaîne complète : ni un `ping` (qui ne prouverait que
 * le transport, déjà acquis par le PoC), ni un outil catalogue (aucun outil ne parle à Shopify
 * avant le lot 2). `whoami` touche réellement `TenantContext`/`UserContext`
 * (`AuthenticatedToolPipeline` → `AccessExposedService` → `AccessResolutionUseCase`, `LOT0-06`) et
 * l'audit (`LOT0-07`) — exactement ce que la tâche demande.
 *
 * **`READ`** : un `viewer` doit pouvoir vérifier qui il est et sur quelle boutique, aussi bien
 * qu'un `operator` — classification volontaire, pas l'omission par défaut de `ToolUseCase.kind`
 * (voir [TouchStoreTool] pour le pendant `MUTATION`).
 *
 * `storeId` est un paramètre explicite de cet outil transitoire — même statut que `list_stores`/
 * `use_store` (`architecture.md` §2.3, D8 : « survivent avec la même signature, bornés par les
 * grants »), pas une des futures **tools catalogue** que `schema.md` §2 interdit de paramétrer par
 * boutique : tant qu'aucune notion de « boutique active de session » n'existe (lot 2), c'est le
 * seul moyen pour l'appelant de dire quelle boutique il interroge — la boutique n'entre en scène
 * qu'après vérification du grant, jamais avant.
 */
@Service
class WhoAmITool(private val pipeline: AuthenticatedToolPipeline) {

    private object WhoAmIUseCase : ToolUseCase {
        override val kind = UseCaseKind.READ
    }

    @McpTool(
        name = "whoami",
        description = "Reports the caller's resolved identity and role on a given store — proves the " +
            "authentication and tenancy chain end to end. Touches no external system.",
    )
    fun whoami(
        @McpToolParam(description = "The store to check access for", required = true) storeId: String,
    ): WhoAmIResult =
        pipeline.run(
            toolName = "whoami",
            useCase = WhoAmIUseCase,
            storeId = storeId,
            toolInput = mapOf("storeId" to storeId),
        ) { tenant, user ->
            WhoAmIResult(identityId = user.identityId, storeId = tenant.storeId, role = user.role.name)
        }
}
