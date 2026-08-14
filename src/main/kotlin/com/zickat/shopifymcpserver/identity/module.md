# `identity`

## Context

`identity` porte l'identité JWT résolue (issuer + subject) et sa traduction en `Identity` interne,
auto-créée au premier appel valide (`findOrCreate`) — la porte d'entrée avant toute résolution d'accès
(`tenancy`). Module socle (D50) : ni port catalogue ni outil MCP à descendre, seuls E1 et E5 le
concernaient (BE-23).

## Use cases

| Use case | Signature |
|---|---|
| `IdentityUseCase.findOrCreate` | `(issuer: String, subject: String, displayName: String): Either<UseCaseError, Identity>` |

## Ports (`domain/repositories/`)

**`IdentityRepository`**
- `save(identity: Identity): Either<UseCaseError, Identity>`
- `findById(id: IdentityId): Either<UseCaseError, Identity>`
- `findByIssuerAndSubject(issuer: String, subject: String): Either<UseCaseError, Identity>`

Implémentation : `spi/mongo/IdentityMongoRepository`.

## `exposed_interface`

**`IdentityExposedService`**
- `exists(identityId: String): Boolean`
- `isActive(identityId: String): Boolean`
- `resolve(issuer: String, subject: String): Either<UseCaseError, String>`

Consommé par `tenancy` (`AccessResolutionUseCase`) et par `api` (`AuthenticatedToolPipeline`), pour
transformer un JWT vérifié en identité interne avant toute résolution d'accès.

## Événements

Aucun — `identity` ne publie ni ne consomme d'événement applicatif.

## Notes / specifics

**Génération d'identifiant sans `org.bson.types.ObjectId` en `domain/` (BE-23) : `newObjectIdHex()`,
partagé avec `vault` et `audit`** — voir `vault/module.md` pour le détail du raisonnement (le domaine
doit produire un identifiant hexadécimal 24 caractères sans importer le type Mongo).

**`IdentityExposedServiceImpl` vivait dans `spi/mongo/` sans aucune dépendance Mongo réelle — il
n'utilise que `IdentityRepository` (port) et `IdentityUseCase`.** Déplacement E5 non prévu par la
mesure initiale de `BE-23` : déplacé vers `exposed_interface/`, à côté de son interface, sans
changement de contenu (garde `@Service`).
