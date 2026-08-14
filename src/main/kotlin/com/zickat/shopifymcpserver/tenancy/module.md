# `tenancy`

## Context

`tenancy` porte les boutiques (`Store`), les octrois d'accès (`Grant` : qui a le droit d'agir sur
quelle boutique, avec quel rôle, jusqu'à quand) et la sélection de boutique active par session
(`use_store`). C'est le module qui répond à « cette identité a-t-elle le droit d'agir ici ? ». Module
socle (D50) : ni port catalogue ni outil MCP à descendre, seuls E1 et E5 le concernaient (BE-23).

## Use cases

| Use case | Signature |
|---|---|
| `AccessResolutionUseCase.resolve` | `(issuer: String, subject: String, storeId: String): Either<UseCaseError, AccessContext>` |
| `AccessResolutionUseCase.listGrantedStores` | `(identityId: String): Either<UseCaseError, List<Store>>` |
| `ActiveStoreSelectionRegistry.activeStoreFor` | `(identityId: String, sessionId: String): StoreId?` |
| `ActiveStoreSelectionRegistry.select` | `(identityId: String, sessionId: String, storeId: StoreId): Unit` |

`ActiveStoreSelectionRegistry` n'est pas un `[Domain]UseCase` au sens du nommage (pas de logique
métier à proprement parler, juste un registre en mémoire par session) — câblé en `@Bean` comme un use
case malgré tout, car il porte de l'état mutable et ne doit exister qu'en une seule instance.

## Ports (`domain/repositories/`)

**`GrantRepository`**
- `save(grant: Grant): Either<UseCaseError, Grant>`
- `findById(id: GrantId): Either<UseCaseError, Grant>`
- `findActiveByIdentityAndStore(identityId: String, storeId: StoreId): Either<UseCaseError, Grant>`
- `findAllActiveByIdentity(identityId: String): Either<UseCaseError, List<Grant>>`

**`StoreRepository`**
- `save(store: Store): Either<UseCaseError, Store>`
- `findById(id: StoreId): Either<UseCaseError, Store>`
- `findBySlug(slug: String): Either<UseCaseError, Store>`

Implémentations : `spi/mongo/GrantMongoRepository`, `spi/mongo/StoreMongoRepository`.

## `exposed_interface`

**`AccessExposedService`**
- `resolveAccess(issuer: String, subject: String, storeId: String): Either<UseCaseError, Pair<TenantContext, UserContext>>`
- `listGrantedStores(identityId: String): Either<UseCaseError, List<GrantedStore>>`

**`ActiveStoreExposedService`**
- `activeStoreFor(identityId: String, sessionId: String): String?`
- `select(identityId: String, sessionId: String, storeId: String): Unit`

**`StoreExposedService`**
- `exists(storeId: String): Boolean`
- `existsAndNotArchived(storeId: String): Boolean`
- `resolveStoreIdBySlug(slug: String): Either<UseCaseError, String>`

**`AccessExposedService.slugFor`** — extension `@file:NamedInterface`, posée par `BE-11` : traduit un
`storeId` en slug lisible pour l'affichage. Reste ici (pas déplacée par `BE-23`).

Consommés par `api` (`AuthenticatedToolPipeline`, `RoutedToolPipeline`) et par `vault`/`shopify`
(`StoreExposedService`, pour vérifier qu'une boutique existe avant d'y attacher un secret).

## Événements

Aucun — `tenancy` ne publie ni ne consomme d'événement applicatif.

## Notes / specifics

**Trois implémentations d'`exposed_interface` vivaient hors de `exposed_interface/` — déplacement E5
dont seul un cas indirect (aucun) était nommé par la mesure initiale de `BE-23` (« 0 » pour ce
module).** `AccessExposedServiceImpl` et `ActiveStoreExposedServiceImpl` étaient dans `domain/` (avec
`@Service`, comptées dans les « 4 fichiers » E1 du module) ; `StoreExposedServiceImpl` était dans
`spi/mongo/` sans aucune dépendance Mongo réelle (juste `StoreRepository`, un port). Les trois sont
déplacées vers `exposed_interface/`, à côté de leur interface, sans changement de contenu.

**`StoreSlug.kt` (`tenancy/exposed_interface/StoreSlug.kt`) n'est pas touché par cette tâche** — posé
par `BE-11`, avec son propre `@file:NamedInterface`, hors périmètre de `BE-23`.

**`AccessResolutionUseCase` et `ActiveStoreSelectionRegistry` portaient `@Component` et sont câblés
depuis `config/TenancyDomainBeansConfiguration.kt`.** Aucune génération d'identifiant local dans ce
module (contrairement à `vault`/`identity`/`audit`) — `Grant`/`Store` reçoivent leur `ObjectId` côté
`spi/mongo/` (`SeedGrantRunner`/`SeedStoresRunner`, hors `domain/`, non concernés par E1).
