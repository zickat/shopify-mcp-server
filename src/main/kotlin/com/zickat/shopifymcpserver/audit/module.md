# `audit`

## Context

`audit` journalise chaque appel d'outil (nom, mutation ou lecture, identité, boutique, verdict,
condensé des paramètres) en échouant fermé : si l'écriture d'audit échoue, l'appel entier échoue, même
si l'action métier avait réussi (voir Notes). Module socle (D50) : ni port catalogue ni outil MCP à
descendre, seuls E1 et E5 le concernaient (BE-23).

## Use cases

| Use case | Signature |
|---|---|
| `AuditLogUseCase.execute` | `<T> (identityId: String?, storeId: String, toolName: String, isMutation: Boolean, toolInput: Map<String, String>, action: () -> Either<UseCaseError, T>): Either<UseCaseError, T>` |

## Ports (`domain/repositories/`)

**`AuditLogRepository`**
- `append(entry: AuditLog): Either<UseCaseError, AuditLog>`
- `findByStore(storeId: String): Either<UseCaseError, List<AuditLog>>`
- `findByIdentity(identityId: String): Either<UseCaseError, List<AuditLog>>`

Implémentation : `spi/mongo/AuditLogMongoRepository`.

## `exposed_interface`

**`AuditExposedService`**
- `<T> execute(identityId: String?, storeId: String, toolName: String, isMutation: Boolean, toolInput: Map<String, String>, action: () -> Either<UseCaseError, T>): Either<UseCaseError, T>`

Consommé par `api` (`AuthenticatedToolPipeline`) : chaque appel d'outil passe par ce point avant de
s'exécuter.

## Événements

Aucun — `audit` ne publie ni ne consomme d'événement applicatif.

## Notes / specifics

**`AuditLogUseCase.execute` échoue fermé sur une panne d'écriture d'audit (`log.error` +
`TechnicalError`), y compris quand `action()` avait réussi.** Un appel dont la trace ne peut pas être
écrite n'est pas honoré — le verdict métier n'est jamais renvoyé sans sa preuve.

**Génération d'identifiant sans `org.bson.types.ObjectId` en `domain/` (BE-23) : `newObjectIdHex()`,
partagé avec `vault` et `identity`** — voir `vault/module.md` pour le détail.

**`AuditExposedServiceImpl` vivait dans `domain/` avec `@Service` — déplacement E5 non nommé par la
mesure initiale de `BE-23` (qui n'en comptait que deux, `RelayGatewayImpl`/`ShopifyAdminGatewayImpl`).**
Déplacé vers `exposed_interface/`, à côté de son interface, sans changement de contenu.
