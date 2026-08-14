# `vault`

## Context

`vault` porte le secret `STORE_CREDENTIAL` de chaque boutique (`ShopifyCredential` — `apiKey`/`apiSecret`/
`shopDomain`) : chiffrement enveloppe (AES-GCM, AAD = `storeId` — D18), stockage, rotation et
déchiffrement à la demande. C'est un module socle (D50) : il n'a ni port catalogue ni outil MCP à
descendre — seuls E1 (retirer le framework de `domain/`) et E5 (replacer une implémentation
d'`exposed_interface` mal placée) le concernaient (BE-23).

## Use cases

| Use case | Signature |
|---|---|
| `StoreCredentialUseCase.store` | `(storeId: String, plaintext: ByteArray, scopesGranted: String): Either<UseCaseError, StoreCredentialId>` |
| `StoreCredentialUseCase.rotate` | `(id: StoreCredentialId, newPlaintext: ByteArray): Either<UseCaseError, StoreCredentialId>` |
| `StoreCredentialUseCase.reveal` | `(id: StoreCredentialId): Either<UseCaseError, ByteArray>` |

## Ports (`domain/repositories/`)

**`StoreCredentialRepository`**
- `save(credential: StoreCredential): Either<UseCaseError, StoreCredential>`
- `findById(id: StoreCredentialId): Either<UseCaseError, StoreCredential>`
- `findActiveByStore(storeId: String): Either<UseCaseError, StoreCredential>`

Implémentation : `spi/mongo/StoreCredentialMongoRepository`.

**`MasterKeyProvider`**
- `resolve(keyRef: String): Either<UseCaseError, ByteArray>`

Implémentation : `spi/env/EnvMasterKeyProvider` (clé maître lue depuis `CATALOG_MASTER_KEY`).

## `exposed_interface`

**`VaultExposedService`**
- `hasActiveCredential(storeId: String): Boolean`
- `resolveCredential(storeId: String): Either<UseCaseError, ByteArray>`

Consommé par `shopify` (`ShopifyAdminGraphQLUseCase.resolveCredential`, pour obtenir le secret dont il a
besoin avant d'échanger un access token).

## Événements

Aucun — `vault` ne publie ni ne consomme d'événement applicatif.

## Notes / specifics

**Le chiffrement (`EnvelopeCrypto`) reste un utilitaire pur dans `domain/crypto/`, sans dépendance
framework — rien à faire dessus pour E1.** Seul `StoreCredentialUseCase` portait la dette : `@Component`
(câblage par stéréotype) et `org.bson.types.ObjectId` (génération d'identifiant) importés directement
dans `domain/`.

**Génération d'identifiant sans `org.bson.types.ObjectId` en `domain/` (BE-23).** Le domaine doit encore
produire un identifiant hexadécimal 24 caractères — `spi/mongo/StoreCredentialEntity.fromDomain` fait
`ObjectId(domain.id.value)`, qui échoue sur toute autre forme. `shared_kernel.newObjectIdHex()` couvre ce
besoin : il enveloppe `ObjectId().toHexString()` sans que `domain/` n'importe le type — le module qui
appelle `newObjectIdHex()` ne dépend, en bytecode, que de `shared_kernel`, franchissable en entier
(D54). `identity` et `audit` partagent le même besoin et le même helper (BE-23).

**`VaultExposedServiceImpl` vivait dans `spi/mongo/` — déplacement E5 non prévu par la mesure initiale
de `BE-23` (qui n'en comptait que deux, `RelayGatewayImpl`/`ShopifyAdminGatewayImpl`).** Il ne dépend
que de `StoreCredentialRepository` (port) et `StoreCredentialUseCase` — aucune dépendance Mongo réelle,
juste un mauvais dossier. Déplacé vers `exposed_interface/`, à côté de son interface, sans changement de
contenu (garde `@Service`, R1 ne s'applique pas à cette couche).
