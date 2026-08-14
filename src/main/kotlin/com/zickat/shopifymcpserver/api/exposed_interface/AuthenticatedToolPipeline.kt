package com.zickat.shopifymcpserver.api.exposed_interface

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.raise.either
import com.zickat.shopifymcpserver.api.mcp.currentJwtPrincipal
import com.zickat.shopifymcpserver.audit.exposed_interface.AuditExposedService
import com.zickat.shopifymcpserver.identity.exposed_interface.IdentityExposedService
import com.zickat.shopifymcpserver.shared_kernel.ForbiddenError
import com.zickat.shopifymcpserver.shared_kernel.TenantContext
import com.zickat.shopifymcpserver.shared_kernel.ToolAccessControl
import com.zickat.shopifymcpserver.shared_kernel.ToolUseCase
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseKind
import com.zickat.shopifymcpserver.shared_kernel.UserContext
import com.zickat.shopifymcpserver.shared_kernel.orThrow
import com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService
import com.zickat.shopifymcpserver.tenancy.exposed_interface.ActiveStoreExposedService
import org.springframework.modulith.NamedInterface
import org.springframework.stereotype.Component

@NamedInterface("exposed_interface")
@Component
class AuthenticatedToolPipeline(
    private val identityExposedService: IdentityExposedService,
    private val accessExposedService: AccessExposedService,
    private val auditExposedService: AuditExposedService,
    private val activeStoreExposedService: ActiveStoreExposedService,
) {

    fun <T> runForStore(
        toolName: String,
        useCase: ToolUseCase,
        storeId: String,
        toolInput: Map<String, String>,
        operatorLabel: String,
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
                val identityId = identityResult.bind()
                val (tenant, user) = accessExposedService.resolveAccess(principal.issuer, principal.subject, storeId)
                    .mapLeft { enrichWithGrantedStores(it, identityId, operatorLabel) }
                    .bind()
                ToolAccessControl.authorizeCall(user.role, useCase).bind()
                action(tenant, user)
            }
        }

        return result.orThrow()
    }

    fun <T> runForIdentity(
        toolName: String,
        useCase: ToolUseCase,
        toolInput: Map<String, String>,
        action: (String) -> T,
    ): T {
        val principalResult = currentJwtPrincipal()
        val identityResult = principalResult.flatMap { identityExposedService.resolve(it.issuer, it.subject) }
        val identityIdForAudit = identityResult.getOrNull()

        val result = auditExposedService.execute(
            identityId = identityIdForAudit,
            storeId = "",
            toolName = toolName,
            isMutation = useCase.kind == UseCaseKind.MUTATION,
            toolInput = toolInput,
        ) {
            either {
                principalResult.bind()
                val identityId = identityResult.bind()
                action(identityId)
            }
        }

        return result.orThrow()
    }

    fun <T> runForActiveStore(
        toolName: String,
        useCase: ToolUseCase,
        sessionId: String,
        toolInput: Map<String, String>,
        action: (TenantContext, UserContext) -> T,
    ): T {
        val principalResult = currentJwtPrincipal()
        val identityResult = principalResult.flatMap { identityExposedService.resolve(it.issuer, it.subject) }
        val identityIdForAudit = identityResult.getOrNull()
        val activeStoreIdForAudit = identityIdForAudit?.let { activeStoreExposedService.activeStoreFor(it, sessionId) }.orEmpty()

        val result = auditExposedService.execute(
            identityId = identityIdForAudit,
            storeId = activeStoreIdForAudit,
            toolName = toolName,
            isMutation = useCase.kind == UseCaseKind.MUTATION,
            toolInput = toolInput,
        ) {
            either {
                val principal = principalResult.bind()
                val identityId = identityResult.bind()
                val activeStoreId = activeStoreExposedService.activeStoreFor(identityId, sessionId)
                    ?: raise(noActiveSelection(identityId))
                val (tenant, user) = accessExposedService.resolveAccess(principal.issuer, principal.subject, activeStoreId)
                    .mapLeft { enrichWithGrantedStores(it, identityId) }
                    .bind()
                ToolAccessControl.authorizeCall(user.role, useCase).bind()
                action(tenant, user)
            }
        }

        return result.orThrow()
    }

    private fun noActiveSelection(identityId: String): UseCaseError =
        ForbiddenError("store.selection.missing", mapOf("grantedStores" to grantedStoreNames(identityId)))

    private fun enrichWithGrantedStores(error: UseCaseError, identityId: String, displayStoreId: String? = null): UseCaseError {
        if (error !is ForbiddenError || error.messageKey != "access.denied") return error
        val parameters = (error.parameters ?: emptyMap()) +
            ("grantedStores" to grantedStoreNames(identityId)) +
            (displayStoreId?.let { mapOf("storeId" to it) } ?: emptyMap())
        return ForbiddenError(error.messageKey, parameters)
    }

    private fun grantedStoreNames(identityId: String): String {
        val available = accessExposedService.listGrantedStores(identityId).getOrElse { emptyList() }
        return if (available.isEmpty()) "aucune" else available.joinToString(", ") { it.slug }
    }
}
