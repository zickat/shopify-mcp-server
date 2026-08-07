package com.zickat.shopifymcpserver.api.mcp

import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

/** Résultat de [TouchStoreTool.touchStore] — aucun état réel n'est modifié, voir la KDoc de la classe. */
data class TouchStoreResult(val storeId: String, val acknowledgedBy: String)

/**
 * `LOT0-08` — second outil trivial, **classifié `MUTATION`**, dont l'unique raison d'être est de
 * prouver en conditions réelles (transport HTTP réel, Mongo réel) le barrage que `whoami` seul ne
 * peut pas exercer : un `viewer` qui appelle directement un outil mutant est refusé côté serveur,
 * `tools/list` ne le lui montrant ou non n'y change rien (`ToolAccessControl.authorizeCall`,
 * `LOT0-06` — « le filtre au tools/list n'est pas le contrôle d'accès, il le double »).
 *
 * **Aucun effet réel** : ne touche ni Mongo (au-delà de l'audit, déjà couvert par le pipeline) ni
 * Shopify — « aucun outil catalogue n'existe encore » (`LOT0-08.md`, « ce que cette tâche ne
 * fait pas »). Le nom et jusqu'à l'existence même de cet outil n'ont aucune vocation à survivre au
 * lot 2 : dès qu'un vrai outil mutant existera (catalogue), celui-ci prendra sa place dans les
 * tests de ce barrage et `TouchStoreTool` pourra disparaître.
 *
 * `override val kind = UseCaseKind.MUTATION` est explicite plutôt que laissé au défaut fermé de
 * [ToolUseCase] — lisible pour quiconque relit ce fichier sans avoir à se souvenir de la doctrine
 * de classification.
 */
@Service
class TouchStoreTool(private val pipeline: AuthenticatedToolPipeline) {

    private object TouchStoreUseCase : ToolUseCase {
        override val kind = UseCaseKind.MUTATION
    }

    @McpTool(
        name = "touch_store",
        description = "Trivial mutation-classified tool that only proves role-based mutation gating " +
            "end to end (LOT0-08). No real side effect, never touches Shopify.",
    )
    fun touchStore(
        @McpToolParam(description = "The store this acknowledgement applies to", required = true) storeId: String,
    ): TouchStoreResult =
        pipeline.run(
            toolName = "touch_store",
            useCase = TouchStoreUseCase,
            storeId = storeId,
            toolInput = mapOf("storeId" to storeId),
        ) { tenant, user ->
            TouchStoreResult(storeId = tenant.storeId, acknowledgedBy = user.identityId)
        }
}
