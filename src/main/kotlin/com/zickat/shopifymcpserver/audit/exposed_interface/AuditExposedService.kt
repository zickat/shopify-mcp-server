package com.zickat.shopifymcpserver.audit.exposed_interface

import arrow.core.Either
import com.zickat.shopifymcpserver.shared_kernel.UseCaseError
import org.springframework.modulith.NamedInterface

/**
 * API publique du module `audit` vers les autres modules — `LOT0-08`.
 *
 * Expose exactement le contrat de
 * [com.zickat.shopifymcpserver.audit.domain.AuditLogUseCase.execute] (voir sa KDoc : « `LOT0-08`
 * composera dans `action` le pipeline réel d'un appel d'outil »), sans y ajouter ni retrancher
 * quoi que ce soit — même raison que [com.zickat.shopifymcpserver.tenancy.exposed_interface.AccessExposedService] :
 * `AuditLogUseCase` vit dans `audit.domain`, un sous-package non exposé par défaut, et le module
 * `api` a besoin d'appeler exactement ce use case (seul point d'écriture de l'audit, fail-closed)
 * pour câbler un outil MCP réel. La signature générique (`action: () -> Either<UseCaseError, T>`)
 * ne fait transiter aucun type interne à `audit` à travers cette frontière.
 */
@NamedInterface("exposed_interface")
interface AuditExposedService {
    fun <T> execute(
        identityId: String?,
        storeId: String,
        toolName: String,
        isMutation: Boolean,
        toolInput: Map<String, String>,
        action: () -> Either<UseCaseError, T>,
    ): Either<UseCaseError, T>
}
