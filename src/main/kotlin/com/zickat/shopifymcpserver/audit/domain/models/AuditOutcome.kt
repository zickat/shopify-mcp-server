package com.zickat.shopifymcpserver.audit.domain.models

/**
 * `schema.md` §3 : `outcome` ne prend que ces trois valeurs. Suit le patron de
 * `GrantRole`/`AccessRole` (`tenancy.domain.models.GrantRole`) : [wireValue] est la représentation
 * persistée dans [com.zickat.shopifymcpserver.audit.domain.models.AuditLog.outcome], qui reste
 * typé `String` (posé ainsi en `LOT0-03`, avant que ce type existe) — cet enum est la source de
 * vérité applicative, `AuditLog.outcome` sa projection câblée.
 *
 * `DENIED` couvre tout refus d'accès (`ForbiddenError` : grant absent, rôle insuffisant,
 * boutique archivée — `NotAuthorizedError` : identité non authentifiée). `ERROR` couvre tout le
 * reste d'un échec (`TechnicalError`, `NotFoundError` métier, etc.) — la distinction qui compte
 * pour l'attribution est « a-t-on refusé l'accès » vs « autre chose a échoué », pas le code HTTP
 * exact. Voir [com.zickat.shopifymcpserver.audit.domain.AuditLogUseCase] pour la classification.
 */
enum class AuditOutcome(val wireValue: String) {
    OK("ok"),
    DENIED("denied"),
    ERROR("error"),
}
