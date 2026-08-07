package com.zickat.shopifymcpserver.api.mcp

import arrow.core.flatMap
import arrow.core.raise.either
import com.zickat.shopifymcpserver.audit.exposed_interface.AuditExposedService
import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.ToolAccessControl
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.shared_kernel.UserContext
import com.zickat.shopifymcpserver.shared_kernel.orThrow
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import org.springframework.stereotype.Component

/**
 * `LOT0-08` — le câblage bout en bout que la tâche demande, en un seul endroit réutilisable par
 * tout outil `@McpTool` : résolution d'accès (`LOT0-06`), autorisation par rôle (`LOT0-06`),
 * exécution de l'outil, écriture d'audit AVANT la réponse (`LOT0-07`, fail-closed — voir la KDoc
 * d'[AuditExposedService], jamais réinterprété ici).
 *
 * Composition exacte de `schema.md` §2 :
 * ```
 * principal (jeton déjà validé) → identity.resolve → tenancy.resolveAccess → authorizeCall → action → audit
 * ```
 *
 * **`identityId` est résolu une première fois AVANT l'appel d'audit**, séparément de la résolution
 * d'accès : `AuditLogUseCase.execute` a besoin de `identityId` **avant** de savoir si l'accès sera
 * autorisé (schema.md — un appel refusé est quand même journalisé, avec l'identité de qui l'a
 * tenté). `AccessExposedService.resolveAccess` refait cette résolution en interne
 * (`IdentityUseCase.findOrCreate` est idempotent, `LOT0-06`) : un aller-retour Mongo de plus par
 * appel, accepté pour ce lot — pas de cache introduit ici, contrainte de `AccessResolutionUseCase`
 * (KDoc, « ne jamais ajouter de cache… sans remonter au Tech Lead »).
 */
@Component
class AuthenticatedToolPipeline(
    private val identityExposedService: IdentityExposedService,
    private val accessExposedService: AccessExposedService,
    private val auditExposedService: AuditExposedService,
) {
    /**
     * @param toolInput représentation déjà réduite à des chaînes de l'entrée métier de l'outil —
     *   seule son empreinte SHA-256 est journalisée (voir `ToolInputDigest`), jamais ces valeurs.
     * @param action reçoit le [TenantContext]/[UserContext] réels, construits par
     *   `AccessResolutionUseCase` — jamais reconstruits ici (`TenantUserContextConstructionTest`).
     * @throws com.zickat.shopifymcpserver.shared_kernel.UseCaseErrorException si l'accès est
     *   refusé, l'écriture d'audit échoue (fail-closed), ou [action] échoue — le framework MCP
     *   catche cette exception à la frontière de l'outil et la restitue en `isError: true`.
     */
    fun <T> run(
        toolName: String,
        useCase: ToolUseCase,
        storeId: String,
        toolInput: Map<String, String>,
        action: (TenantContext, UserContext) -> T,
    ): T {
        val principalResult = currentJwtPrincipal()
        val identityResult = principalResult.flatMap { identityExposedService.resolve(it.issuer, it.subject) }
        val identityIdForAudit = identityResult.getOrNull()

        val result = auditExposedService.execute(
            identityId = identityIdForAudit,
            storeId = storeId,
            toolName = toolName,
            isMutation = useCase.kind == UseCaseKind.MUTATION,
            toolInput = toolInput,
        ) {
            either {
                val principal = principalResult.bind()
                identityResult.bind()
                val (tenant, user) = accessExposedService.resolveAccess(principal.issuer, principal.subject, storeId).bind()
                ToolAccessControl.authorizeCall(user.role, useCase).bind()
                action(tenant, user)
            }
        }

        return result.orThrow()
    }
}
