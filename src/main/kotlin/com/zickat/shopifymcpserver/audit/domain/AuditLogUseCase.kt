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

/**
 * Seul point d'écriture du journal d'audit qu'un appelant métier doit utiliser — `LOT0-07.md`.
 * Depuis l'arbitrage Q5 (une app Shopify par boutique, pas par boutique × opérateur), ce journal
 * est la **seule source au monde** qui répond à « qui a fait quoi » : Shopify ne voit plus que
 * l'app, jamais l'identité qui a agi derrière.
 *
 * `LOT0-08` composera dans [action] le pipeline réel d'un appel d'outil (résolution d'accès,
 * autorisation par rôle, puis l'outil lui-même) : cette classe ne connaît ni Shopify ni les outils
 * MCP, seulement le contrat « exécute, journalise, restitue ».
 */
@Component
class AuditLogUseCase(
    private val repository: AuditLogRepository,
    private val clock: Clock = Clock.System,
) {
    private val log = LoggerFactory.getLogger(AuditLogUseCase::class.java)

    /**
     * Exécute [action], journalise **systématiquement** son résultat, puis restitue ce résultat à
     * l'appelant.
     *
     * **« L'écriture d'audit précède la réponse »** (`schema.md` §2, invariant 3) : cette fonction
     * n'écrit l'entrée qu'après avoir exécuté [action], mais elle ne **rend la main** qu'une fois
     * cette écriture terminée. Tout ce que l'appelant fait ensuite du résultat — construire une
     * réponse HTTP, la sérialiser — s'exécute donc nécessairement APRÈS que la ligne d'audit soit
     * déjà en base. Un échec dans cette suite ne peut plus faire disparaître la trace d'une
     * mutation qui, elle, a bien eu lieu.
     *
     * **Fail-closed** (`LOT0-07.md` point 4, décision imposée par cette tâche, absente de
     * `architecture.md`/`schema.md`) : si l'écriture d'audit elle-même échoue, cette fonction
     * retourne [TechnicalError] — **quel qu'ait été le résultat de [action]**. L'action métier
     * n'est jamais considérée comme faite si elle n'a pas pu être journalisée : un effet de bord
     * déjà produit (ex. une mutation Shopify, à partir du lot 2) ne peut pas être annulé par ce
     * retour, mais le contrat retourné à l'appelant dit honnêtement qu'on ne peut pas garantir
     * l'attribution de ce qui vient de se passer — cohérent avec le refus par défaut déjà en place
     * pour les grants (`LOT0-06`). Une panne d'écriture d'audit n'est **pas** une décision locale à
     * contourner : voir `LOT0-07.md`, « ne pas réinterpréter ce choix en LOT0-08 ».
     *
     * @param identityId `null` si l'appelant n'est pas authentifié — un appel refusé avant
     *   authentification est quand même journalisé (`schema.md` §3).
     * @param toolInput représentation déjà réduite à des chaînes de l'entrée de l'outil ; seule
     *   son empreinte est journalisée, jamais ces valeurs elles-mêmes (voir [ToolInputDigest]).
     */
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
                // security.md : pas de PII/secret dans les logs — toolName/storeId/outcome sont
                // des identifiants métier, pas des données sensibles.
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

    /**
     * `DENIED` pour tout refus d'accès (`ForbiddenError`/`NotAuthorizedError`, avec leur
     * `messageKey` comme `denialReason`), `ERROR` pour tout le reste d'un échec, `OK` sinon.
     */
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
