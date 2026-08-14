# `products`

## Context

`products` porte le cycle de vie éditorial des Product Shopify : recherche/scan du catalogue, lecture
du contenu brut et enrichi, détection des orphelins et des fiches à review, marquage bloqué,
publication/dépublication multi-type. C'est le septième et dernier module catalogue descendu (D50), et
le plus gros du dépôt — huit outils câblés (contre sept au maximum pour les modules précédents), deux
ports (D42), et quatre fichiers de logique métier réelle (`DeriveContentStatus`, `SizeConversion`,
`OriginGuard`, `BrandProfileParser`) qui ne migrent pas vers `spi/`, contrairement à tout ce qui
ressemblait à du JSON-parsing pur dans les six modules précédents.

## Use cases

| Use case | Signature |
|---|---|
| `SearchProductsUseCase` | `execute(storeId: String, query: String?, statusFilter: ProductStatusFilter): Either<UseCaseError, SearchProductsResult>` |
| `GetRawContentUseCase` | `execute(storeId: String, productId: String): Either<UseCaseError, GetRawContentResult>` |
| `GetEnrichedContentUseCase` | `execute(storeId: String, productId: String): Either<UseCaseError, GetEnrichedContentResult>` |
| `ListToReviewUseCase` | `execute(storeId: String, resourceType: ToReviewResourceType): Either<UseCaseError, ListToReviewResult>` |
| `ListOrphanProductsUseCase` | `execute(storeId: String): Either<UseCaseError, ListOrphanProductsResult>` |
| `MarkBlockedUseCase` | `execute(storeId: String, resourceId: String): Either<UseCaseError, MarkBlockedResult>` |
| `PublishResourceUseCase` | `execute(storeId: String, resourceId: String): Either<UseCaseError, PublishResourceResult>` |
| `UnpublishResourceUseCase` | `execute(storeId: String, resourceId: String): Either<UseCaseError, UnpublishResourceResult>` |
| `BrandProfileUseCase` | `getFor(storeId: String, brandProfileRef: String): Either<UseCaseError, BrandProfile>` — **non câblé, voir « Le port sans adaptateur » ci-dessous** |

## Ports (`domain/repositories/`)

**Deux ports (D42), pas un** — l'agrégat Product et l'agrégat BrandProfile ne partagent aucun cycle de
vie :

**`ProductRepository`** — huit méthodes, un port par agrégat malgré la diversité des huit use cases
câblés :
- `search(storeId, query): Either<UseCaseError, ProductScan>`
- `rawContent(storeId, productId): Either<UseCaseError, ProductRawSnapshot?>`
- `enrichedContent(storeId, productId): Either<UseCaseError, ProductEnrichedFetch?>`
- `listToReview(storeId, resourceType): Either<UseCaseError, List<ToReviewEntry>>`
- `listOrphans(storeId): Either<UseCaseError, OrphanScan>`
- `markBlocked(storeId, resourceId): Either<UseCaseError, ProductMetafieldWriteOutcome>`
- `publish(storeId, resourceId): Either<UseCaseError, ProductPublishOutcome>`
- `unpublish(storeId, resourceId): Either<UseCaseError, ProductUnpublishOutcome>`

Implémentation : `spi/shopify/ProductShopifyRepository`, sur `spi/shopify/ProductGraphQL` (requêtes +
parsing). `publish_resource`/`unpublish_resource` restent des méthodes de ce port malgré leur portée
multi-type de ressource — tranché par **D42**, non rouvert ici.

**`BrandProfileRepository`** — un seul méthode, `findRawProfile(brandProfileRef): Either<UseCaseError, String>`.
**Existe sans implémentation de production.** Voir « Le port sans adaptateur ».

## Le port sans adaptateur — `BrandProfileRepository`, enquête et décision

**Décidé pour ce chantier (2026-08-14, arbitrage CTO/Val) : lecture (a)** — ce chantier réaligne,
il n'ajoute pas de fonctionnalité. `BrandProfileRepository` reste tel quel, sans implémentation ;
`BrandProfileUseCase` reste non câblé (aucun `@Bean`). **Aucun des deux n'est supprimé** — un port sans
adaptateur n'est pas du code mort ici, c'est du travail suspendu (même précédent que les 7 outils
manquants de `collections`, voir `collections/module.md`).

**Ce que l'enquête a trouvé** (`git log --follow`, recherche dans `catalog-plugin-oauth-tenancy`) :

- `BrandProfileRepository.kt` et `BrandProfileUseCase.kt` naissent tous deux dans le **même commit**,
  `b1ee442` — *« feat(oauth-tenancy): LOT5-02 — brand profile + size conversion, ported and
  store-scoped »*. Un seul commit les touche depuis leur création.
- `LOT5-02` (`catalog-plugin-oauth-tenancy/tasks/LOT5-02.md`) spécifie explicitement de **ne pas**
  câbler quoi que ce soit : *« Elle ne câble rien dans `enrich_product` (`LOT5-08`, bloquée sur feu
  vert). »* — la tâche qui a créé ce port l'a délibérément laissé sans adaptateur, dès l'origine.
- **`LOT5-08` (`catalog-plugin-oauth-tenancy/tasks/LOT5-08.md`), « Port natif `enrich_product` »**, est
  l'outil qui consommerait `BrandProfileUseCase`/`convertGarmentLetterSize` en production — décrit comme
  *« le plus gros outil du lot (≈ 533 lignes côté TS) »*, dépendant de `LOT5-07` (cassette,
  🔴 bloquée) et de `LOT5-02`.
- `catalog-plugin-oauth-tenancy` est **suspendue, lots 3 à 8 inclus** (`architecture.md` §D51 de ce
  chantier) — `LOT5-08` est dans cette plage, donc dans cette suspension.

**Fait, sans conclusion au-delà** : un lot spécifié et suspendu (`LOT5-08`) attend explicitement ce
port pour lui donner un adaptateur de production et un appelant. Ce n'est pas une garantie qu'il sera
repris tel quel — c'est ce que dit l'archéologie, à charge pour Val/CTO de trancher la suite du
portefeuille.

## Outils MCP exposés (`api/mcp/`)

8 classes, 8 méthodes `@McpTool` :

| Nom | Description |
|---|---|
| `search_products` | Recherche des produits par requête Shopify Admin et/ou statut de pipeline. |
| `get_raw_content` | Lit la fiche brute d'un produit (titre, description source, prix, images, options, variantes). |
| `get_enriched_content` | Lit l'état déjà écrit par le pipeline (contenu enrichi, statut, guides liés…). |
| `list_to_review` | Liste les ressources (product\|collection\|article) au statut `to_review`. |
| `list_orphan_products` | Liste les produits n'appartenant à aucune collection. |
| `mark_blocked` | Marque une ressource bloquée (`custom.content_status = "blocked"`). |
| `publish_resource` | Publie un produit : statut ACTIVE, retrait du marqueur pipeline, publication Online Store si nécessaire. |
| `unpublish_resource` | Retire un produit du canal Online Store sans changer son statut natif. |

## `exposed_interface`

Aucune — ce module n'expose rien vers les autres modules (D44 : zéro consommateur hors `api/mcp/`,
mesuré par recherche du package `products.` en dehors de `products/`).

## Événements

Aucun — `products` ne publie ni ne consomme d'événement applicatif.

## Notes / specifics

**Ce qui reste dans le domaine, et pourquoi — la réponse la plus significative de tout le chantier sur
ce point.** Quatre fichiers portent une logique métier réelle et ne descendent pas en `spi/` :
- **`DeriveContentStatus.kt`** — `deriveContentStatus(contentStatus, hasSummaryPoints)` et
  `deriveRelatedGuidesSource(rawSource)` : deux interprétations d'un fait Shopify brut en état
  pipeline/domaine, appelées depuis trois use cases (`SearchProductsUseCase`,
  `ListOrphanProductsUseCase`, `GetEnrichedContentUseCase`) — jamais depuis `spi/`, jamais depuis
  `api/`.
- **`SizeConversion.kt`** — table de conversion pointure CN→EU sourcée, et le refus assumé de
  convertir les tailles lettre (aucune extrapolation). Zéro appel réseau, zéro dépendance Shopify.
  Encore inerte (aucun appelant applicatif) — voir précédent `OriginGuard`/`LOT1-03`.
- **`OriginGuard.kt`** — garde-fou anti-mention d'origine dans les champs texte. Zéro réseau, zéro
  dépendance Shopify. Encore inerte, même statut.
- **`BrandProfileParser.kt`** — validation de forme d'un `brand.yaml`, portage fidèle depuis le TS
  (champs obligatoires vs optionnels). Zéro dépendance Shopify — utilise Jackson/YAML, pas
  `kotlinx.serialization.json` (R2 ne le concerne donc pas).

**Ce que chaque use case a gardé après la descente (les deux questions du gabarit) :**
- *Qu'est-ce qui dépend de ce que l'appelant a fourni ?* `SearchProductsUseCase.matches(statusFilter)` —
  le filtrage par statut pipeline (`UNTREATED`/`TO_REVIEW`/`BLOCKED`/`ALL`) s'applique en mémoire, après
  le fetch, sur les valeurs **brutes** (`contentStatusValue`, `hasSummaryPoints`) que `spi/` remonte
  sans les interpréter — parce que ce filtre varie avec l'argument de l'appelant, il ne peut pas être
  décidé par `spi/`, qui ne connaît pas `statusFilter`.
- *Qu'est-ce qui existe pour éviter un appel réseau ?* Rien dans ce module — contrairement à
  `seo`/`metaobjects`, aucun des huit use cases n'a de garde d'entrée qui court-circuite un appel
  Shopify sur la seule foi des arguments fournis. `PublishResourceUseCase`/`UnpublishResourceUseCase`
  ont un cas analogue (`wasNeverPublished`, `publicationsCount == 0` → no-op) mais la décision dépend
  d'un **fait lu depuis Shopify au milieu de la séquence**, pas des arguments d'entrée seuls — voir
  ci-dessous.

**`PublishResourceUseCase`/`UnpublishResourceUseCase` sont entièrement fondus dans `spi/` — écart
assumé par rapport à `DeleteMetaobjectUseCase` (`metaobjects`).** `DeleteMetaobjectUseCase` garde sa
décision de refus dans le domaine parce qu'elle croise un **argument de l'appelant**
(`confirmReferencedDeletion`) avec un fait Shopify. Ici, `wasNeverPublished` ne croise **aucun**
argument de l'appelant — c'est une pure conséquence de ce que Shopify répond, qui détermine si un
second appel réseau (`publishablePublish`) a lieu. Faute de décision à garder côté appelant, la
séquence entière (lecture d'état, activation, suppression du marqueur, publication conditionnelle) vit
dans `ProductShopifyRepository.publish`/`.unpublish`, qui retourne un seul type de sortie scellé
(`ProductPublishOutcome`/`ProductUnpublishOutcome`). Les deux use cases sont réduits à une traduction
outcome→Result, comme `pages`. **Ce choix n'a pas été tranché seul — signalé au Tech Lead, cf.
`progress.md`.**

**E6 — les cinq `*Result` retypés.** `GetEnrichedContentResult`/`GetRawContentResult` (hybrides,
traités en premier pour calibrer l'effort) gardent leur `outcome`/`FOUND`/`NOT_FOUND` et remplacent
`text: String?` par un instantané typé (`ProductEnrichedSnapshot`/`ProductRawSnapshot`).
`SearchProductsResult`/`ListOrphanProductsResult`/`ListToReviewResult` (purs, `data class` à un seul
champ `text` avant ce lot) sont retypés en listes d'entrées typées
(`List<ProductListingEntry>`/`List<ToReviewEntry>`) plus les indicateurs de troncature — plus aucun
n'a de champ nommé `text`. La totalité du rendu français (bandeaux, listes à puces, notes de
troncature, libellés « (aucun) »/« (vide) ») vit désormais dans
`products/api/mcp/ProductsToolResults.kt`. `MarkBlockedResult`/`PublishResourceResult`/
`UnpublishResourceResult` n'ont jamais porté de champ `text` — inchangés dans leur forme, seule leur
construction change (depuis les outcomes du port plutôt que depuis `ShopifyUserErrors.format(...)`
appelé en domaine).

**`stripHtml` (shared_kernel) est appelé au rendu, pas au fetch.** Le HTML brut d'un produit est montré
tel quel dans un champ (`Description actuelle`, pour un copier-coller direct dans `body_html`) et
dépouillé dans un autre (`Description source`, `Description originale fournisseur`, pour la lecture
humaine) — cette différence de traitement par champ est un choix d'affichage, donc elle vit dans
`ProductsToolResults`, pas dans `spi/` ni dans le domaine.

**`errorResult`, `invalidGidType`, `withBanner` sont dupliqués en `private` dans
`products/api/mcp/ProductsToolResults.kt` (D58)**, même justification que les six modules précédents.
**`slugFor` est importé directement depuis `tenancy.exposed_interface`**, jamais dupliqué.

**Nettoyage `return@either` (mandat complémentaire du Chief of Staff, en cours de ce lot).**
`BrandProfileUseCase.getFor` avait une garde de succès (cache hit) en tête de bloc `either {}` — sortie
du bloc en expression conditionnelle (`cacheByStoreId[storeId]?.right() ?: either { ... }`), même forme
que `seo` (`BE-15`). `PublishResourceUseCase`/`UnpublishResourceUseCase` avaient respectivement quatre
et trois `return@either` — **zéro restant**, pas par restructuration en `when`-valeur mais parce que la
séquence entière a fondu dans `spi/` (voir plus haut) : les deux use cases n'ont plus de bloc `either {}`
du tout. Les autres use cases du module (`SearchProductsUseCase`, `GetRawContentUseCase`,
`GetEnrichedContentUseCase`, `ListOrphanProductsUseCase`, `ListToReviewUseCase`, `MarkBlockedUseCase`)
n'en avaient aucun à l'origine.
