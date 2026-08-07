package com.zickat.shopifymcpserver.shared_kernel

/**
 * Hiérarchie d'erreurs du domaine — backend.md §Gestion des erreurs.
 *
 * Toutes les erreurs métier implémentent [UseCaseError]. Les use cases et repositories ne lèvent
 * jamais d'exception explicitement : ils retournent `Either<UseCaseError, T>` (Arrow-kt). La
 * traduction en réponse HTTP se fait à la frontière (voir [GlobalExceptionHandler]), pas dans le
 * domaine.
 *
 * Ces types vivent volontairement dans le package racine de shared_kernel (pas dans un
 * sous-package `error/`) : Spring Modulith expose par défaut les types du package racine d'un
 * module à tous les autres modules, et cantonne les sous-packages à l'usage interne. Cette
 * hiérarchie doit être visible de tous les modules — Kotlin ne supportant pas nativement les
 * annotations de package (`package-info.kt` échoue à la compilation, contrairement à Java), c'est
 * la façon la plus simple d'obtenir la même visibilité qu'un module `OPEN` sans recourir à un
 * fichier `package-info.java` séparé et à la compilation mixte Java/Kotlin que ça implique.
 */
interface UseCaseError

/** Erreur métier générique → 400 Bad Request. */
open class DomainError(
    open val messageKey: String,
    open val parameters: Map<String, String>? = mapOf(),
) : UseCaseError

/** Ressource introuvable → 404 Not Found. */
open class NotFoundError(
    messageKey: String,
    parameters: Map<String, String>? = mapOf(),
) : DomainError(messageKey, parameters)

/** Identité non authentifiée ou jeton invalide → 401 Unauthorized. */
open class NotAuthorizedError(
    messageKey: String,
    parameters: Map<String, String>? = mapOf(),
) : DomainError(messageKey, parameters)

/** Identité authentifiée mais sans droit sur la ressource → 403 Forbidden. */
open class ForbiddenError(
    messageKey: String,
    parameters: Map<String, String>? = mapOf(),
) : DomainError(messageKey, parameters)

/** Erreur technique (infra, dépendance externe) → 500 Internal Server Error. */
open class TechnicalError(
    messageKey: String,
    parameters: Map<String, String>? = mapOf(),
) : DomainError(messageKey, parameters)

/** Agrégat de plusieurs erreurs (ex. validation multi-champs). Prend le code HTTP de la pire d'entre elles. */
open class ManyUseCaseError(
    val errors: List<UseCaseError>,
) : UseCaseError
