package com.zickat.shopifymcpserver.audit.domain

import arrow.core.Either
import arrow.core.left
import com.zickat.shopifymcpserver.audit.domain.models.AuditLog
import com.zickat.shopifymcpserver.audit.domain.models.AuditLogId
import com.zickat.shopifymcpserver.audit.domain.models.AuditOutcome
import com.zickat.shopifymcpserver.audit.domain.repositories.AuditLogRepository
import com.zickat.shopifymcpserver.shared_kernel.ForbiddenError
import com.zickat.shopifymcpserver.shared_kernel.NotAuthorizedError
import com.zickat.shopifymcpserver.shared_kernel.TechnicalError
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import kotlin.time.Clock
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AuditLogUseCase(
    private val repository: AuditLogRepository,
    private val clock: Clock = Clock.System,
) {
    private val log = LoggerFactory.getLogger(AuditLogUseCase::class.java)

    fun <T> execute(
        identityId: String?,
        storeId: String,
        toolName: String,
        isMutation: Boolean,
        toolInput: Map<String, String>,
        action: () -> Either<UseCaseError, T>,
    ): Either<UseCaseError, T> {
        val actionResult = action()
        val (outcome, denialReason) = classify(actionResult)

        val entry = AuditLog(
            id = AuditLogId(ObjectId().toHexString()),
            identityId = identityId,
            storeId = storeId,
            toolName = toolName,
            isMutation = isMutation,
            outcome = outcome.wireValue,
            denialReason = denialReason,
            toolInputDigest = ToolInputDigest.of(toolInput),
            occurredAt = clock.now(),
        )

        return when (repository.append(entry)) {
            is Either.Left -> {
                log.error(
                    "Audit write failed for tool '{}' on store {} (business outcome was '{}') — " +
                        "failing the whole request closed, this outcome is not honored",
                    toolName,
                    storeId,
                    outcome.wireValue,
                )
                TechnicalError("auditLog.write.failed", mapOf("toolName" to toolName)).left()
            }
            is Either.Right -> actionResult
        }
    }

    private fun <T> classify(result: Either<UseCaseError, T>): Pair<AuditOutcome, String?> = result.fold(
        ifLeft = { error ->
            when (error) {
                is ForbiddenError -> AuditOutcome.DENIED to error.messageKey
                is NotAuthorizedError -> AuditOutcome.DENIED to error.messageKey
                else -> AuditOutcome.ERROR to null
            }
        },
        ifRight = { AuditOutcome.OK to null },
    )
}
