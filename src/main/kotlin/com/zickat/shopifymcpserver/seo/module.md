# `seo`

## Context

`seo` porte les métadonnées SEO — titre et description — de quatre types de ressources Shopify :
Product, Collection, Article et Page. Deux mécanismes de stockage selon le type : natif (Product,
Collection, champ `seo { title description }` via `productUpdate`/`collectionUpdate`) ou metafield
(Article, Page, `global.title_tag`/`global.description_tag`). C'est le deuxième module descendu (D50) :
le plus léger des seize, avec E6 déjà soldé avant même le début du chantier, choisi pour valider le
gabarit posé par `pages` sur un deuxième cas.

## Use cases

| Use case | Signature |
|---|---|
| `GetSeoUseCase` | `execute(storeId: String, resourceType: SeoResourceType, resourceId: String): Either<UseCaseError, GetSeoResult>` |
| `UpdateSeoUseCase` | `execute(storeId: String, resourceType: SeoResourceType, resourceId: String, seoTitle: String?, seoDescription: String?): Either<UseCaseError, UpdateSeoResult>` |

## Ports (`domain/repositories/`)

Un seul port, pour un seul agrégat — la paire (titre, meta title, meta description) d'une ressource,
quel que soit son mécanisme de stockage (D42) :

**`SeoRepository`**
- `get(storeId: String, resourceType: SeoResourceType, resourceId: String): Either<UseCaseError, SeoSnapshot?>`
- `update(storeId: String, resourceType: SeoResourceType, resourceId: String, seoTitle: String?, seoDescription: String?): Either<UseCaseError, SeoWriteOutcome>`

Implémentation : `spi/shopify/SeoShopifyRepository`, sur `spi/shopify/SeoGraphQL`. Le dispatch
NATIVE/METAFIELD (choix de la requête, du champ de mutation, du parsing de la réponse) vit entièrement
dans cette implémentation — voir « Notes / specifics ».

## Outils MCP exposés (`api/mcp/`)

2 classes, 2 méthodes `@McpTool` :

| Nom | Description |
|---|---|
| `get_seo` | Lit le meta title et la meta description actuellement définis sur un product, collection, article ou page. |
| `update_seo` | Met à jour le meta title et/ou la meta description d'un product, collection, article ou page ; un champ omis conserve sa valeur (fusion, jamais effacement) ; les deux omis = no-op. |

## `exposed_interface`

Aucune — ce module n'expose rien vers les autres modules (D44 : zéro consommateur hors `api/mcp/`,
mesuré par recherche du package `seo.` en dehors de `seo/`).

## Événements

Aucun — `seo` ne publie ni ne consomme d'événement applicatif (mesuré : aucune référence à
`ApplicationEvent`/`ApplicationEventPublisher`/`@EventListener` dans le module).

## Notes / specifics

**`SeoRepository` : un port par agrégat, pas par intention (D42).** Deux use cases, un seul port —
`get` et `update` portent sur la même paire de valeurs d'une même ressource, qui ne se dissocie pas
selon l'opération.

**`GetSeoUseCase` s'est effondré presque comme `ListPagesUseCase` ; `UpdateSeoUseCase` non, et c'est
mesuré, pas supposé.** Le dispatch de mécanisme (`SeoMechanism.NATIVE`/`METAFIELD` — quelle requête,
quel champ de mutation, comment parser la réponse) est descendu entièrement dans
`SeoShopifyRepository` : c'était du transport (comment parler à Shopify pour cette ressource), pas du
métier. `UpdateSeoUseCase` reste substantiel pour deux raisons qui, elles, sont du métier et doivent
rester au-dessus du repository :
- la garde `NoOp` (`seoTitle == null && seoDescription == null`) court-circuite avant tout appel à
  `seoRepository.update` — c'est la garantie de zéro appel réseau quand rien n'est à écrire, une
  décision qui doit précéder l'appel, pas être découverte après ;
- `titleModified`/`descriptionModified` valent `seoTitle != null`/`seoDescription != null` — c'est-à-
  dire ce que **l'appelant a fourni**, pas ce que Shopify a renvoyé dans `SeoWriteOutcome.Updated`
  (`finalMetaTitle`/`finalMetaDescription`, qui peut être la valeur inchangée). Un repository ne peut
  pas porter cette distinction : il ne connaît que le résultat final, pas l'intention d'origine.

**`errorResult`, `invalidGidType`, `withBanner` sont dupliqués en `private` dans
`seo/api/mcp/SeoToolResults.kt` (D58)**, même justification que `pages` : helpers de rendu MCP pur,
couverts par R13 plutôt que partagés. **`slugFor` n'est en revanche pas dupliqué** : `GetSeoTool` et
`UpdateSeoTool` l'importent directement de `tenancy.exposed_interface` — ce module n'a jamais eu sa
propre copie à faire remonter.

**Aucun `*Result` de `seo` ne porte de champ `text`** (mesuré par recherche dans le module) : E6 était
déjà soldé avant ce chantier, `GetSeoResult` et `UpdateSeoResult` n'ont jamais rendu de chaîne — le
rendu vit exclusivement dans `SeoToolResults`.
