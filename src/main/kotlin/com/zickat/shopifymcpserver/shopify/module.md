# `shopify`

## Context

`shopify` porte l'accès générique à l'API Admin Shopify (GraphQL) : résolution du secret (`vault`),
échange et cache de l'access token, exécution de la requête. C'est le point de passage unique par
lequel tous les modules catalogue (`pages`, `products`, `seo`, …) atteignent Shopify — CATALOG_FAMILY
les y autorise (D50/R7). Module socle : ni port catalogue ni outil MCP propre, seuls E1 et E5 le
concernaient (BE-23).

## Use cases

| Use case | Signature |
|---|---|
| `ShopifyAdminGraphQLUseCase.executeGraphQL` | `(storeId: String, query: String, variables: JsonElement): Either<UseCaseError, JsonElement>` |

## Ports (`domain/repositories/`)

**`ShopifyAdminHttpClient`**
- `exchangeAccessToken(shopDomain: String, apiKey: String, apiSecret: String): Either<UseCaseError, ShopifyAccessToken>`
- `executeGraphQL(shopDomain: String, accessToken: String, query: String, variables: JsonElement): Either<UseCaseError, JsonElement>`

Implémentation : `spi/http/ShopifyAdminHttpOkHttpClient`.

## `exposed_interface`

**`ShopifyAdminGateway`**
- `executeGraphQL(storeId: String, query: String, variables: JsonElement): Either<UseCaseError, JsonElement>`

Consommé par tous les modules catalogue (via leur `spi/shopify/*Repository`) — c'est le seul point
d'accès à Shopify qu'ils voient.

`ShopifyResourceTypes`, `RichText`, `ShopifyUserErrors`, `ShopifyMetafields`, `ShopifyOnlineStorePublication`
sont des types de données partagés (GID, erreurs GraphQL, structures de metafields) — pas des ports,
rien à documenter au sens use case/implémentation.

## Événements

Aucun — `shopify` ne publie ni ne consomme d'événement applicatif.

## Notes / specifics

**`shopify/domain/` garde le droit de porter `kotlinx.serialization.json` (`ShopifyCredential`) —
exception nommée D43, non traitée comme une violation R2/E1 par cette tâche** : c'est le seul module où
le domaine parse directement un format de sérialisation, parce que le secret déchiffré par `vault`
arrive sous cette forme et que `shopify` est celui qui sait le structurer.

**`ShopifyAdminGatewayImpl` vivait dans `domain/` avec `@Service` — déplacement E5 prévu par la mesure
initiale de `BE-23` (le second des deux cas nommés).** Déplacé vers `exposed_interface/`, à côté de son
interface, sans changement de contenu. Une trentaine de tests de rejeu catalogue l'instancient
directement (le vrai gateway, câblé sur un fake HTTP) — seul l'import a changé, aucune assertion.

**`ShopifyAdminGraphQLUseCase` portait `@Component` : câblé désormais depuis
`config/ShopifyDomainBeansConfiguration.kt`.** `ShopifyConfiguration.kt` (module root) est supprimé —
il ne portait plus que `@ComponentScan`, interdit.
