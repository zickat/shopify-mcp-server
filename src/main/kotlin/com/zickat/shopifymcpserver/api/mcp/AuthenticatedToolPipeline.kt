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

@Component
class AuthenticatedToolPipeline(
    private val identityExposedService: IdentityExposedService,
    private val accessExposedService: AccessExposedService,
    private val auditExposedService: AuditExposedService,
) {
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
