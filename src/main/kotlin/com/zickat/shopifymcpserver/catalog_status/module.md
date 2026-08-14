# `catalog_status`

## Context

`catalog_status` porte la recherche transverse de collections et d'articles (guides) du catalogue par
requête Shopify et/ou par statut de traitement du pipeline — l'entrée en matière pour identifier un lot
de ressources à traiter, jamais leur contenu. C'est le sixième module descendu (D50), et le seul dont la
fiche s'ouvrait bloquée : `architecture.md` (D44) classait l'ACL `CatalogStatusExposedService` parmi les
ACL **gardées** au motif d'une « vue transverse » avec de vrais consommateurs inter-modules. Mesuré à
l'ouverture de cette tâche (`BE-20`) : faux — le seul importateur de
`catalog_status.exposed_interface` dans tout le dépôt était `api/mcp/SearchResourcesTool.kt` et
`api/mcp/McpToolResults.kt`, c'est-à-dire l'outil MCP lui-même. Le CTO a tranché (`D60`) : l'ACL
disparaît, comme les six modules catalogue précédents. « Vue transverse » décrivait le **sujet** de
l'outil (chercher des collections *ou* des articles), pas ses arêtes — `catalog_status` n'a jamais eu de
consommateur hors de `api/mcp/`.

## Use cases

| Use case | Signature |
|---|---|
| `SearchResourcesUseCase` | `execute(storeId: String, resourceType: SearchResourceType, query: String?, statusFilter: SearchStatusFilter): Either<UseCaseError, SearchResourcesResult>` |

## Ports (`domain/repositories/`)

Un seul port, pour un seul agrégat — la recherche de ressources catalogue (D42) :

**`CatalogStatusRepository`**
- `search(storeId: String, resourceType: SearchResourceType, query: String?): Either<UseCaseError, CatalogStatusListing>`

Implémentation : `spi/shopify/CatalogStatusShopifyRepository`, sur `spi/shopify/CatalogStatusGraphQL`.
La requête GraphQL (choix du champ pluriel et de la clé de métachamp secondaire selon `resourceType`),
la pagination par curseur et le parsing JSON → `CatalogStatusResourceNode` vivent entièrement dans cette
implémentation, exactement le même partage que `menus`/`MenusShopifyRepository`+`MenusGraphQL`.

## Outils MCP exposés (`api/mcp/`)

1 classe, 1 méthode `@McpTool` :

| Nom | Description |
|---|---|
| `search_resources` | Recherche des collections ou des articles (guides) du catalogue par requête Shopify et/ou par statut de traitement du pipeline. Équivalent collection/article de `search_products`. Point d'entrée pour identifier un lot de ressources à traiter — ne retourne jamais de contenu. |

## `exposed_interface`

Aucune — ce module n'expose rien vers les autres modules (`D60` : zéro consommateur hors `api/mcp/`,
mesuré par recherche du package `catalog_status.` en dehors de `catalog_status/`).
`CatalogStatusExposedService` et son implémentation ont été supprimés, comme les six ACL catalogue
précédentes — voir « Le mot qui a fait tenir l'ACL un lot de trop » ci-dessous.

## Événements

Aucun — `catalog_status` ne publie ni ne consomme d'événement applicatif (mesuré : aucune référence à
`ApplicationEvent`/`ApplicationEventPublisher`/`@EventListener` dans le module).

## Le mot qui a fait tenir l'ACL un lot de trop

`F1` avait déjà mesuré, avant ce chantier, que le `domain/` de `catalog_status` n'importe aucune
`exposed_interface` catalogue — il appelle `ShopifyAdminGateway` directement, comme `pages` avant
réalignement. C'est resté vrai, et ce n'est pas ce qui a changé. Ce qui a changé, c'est l'autre axe :
personne n'avait mesuré, avant `BE-20`, qui **importe** `catalog_status`, seulement ce que
`catalog_status` importe. `D44` classait le module parmi les ACL gardées sur la foi du mot « vue
transverse » — une description exacte du **sujet** de l'outil (il traite collections *et* articles,
deux familles de ressources catalogue) mais pas de ses **arêtes réelles** dans le graphe de dépendance.
Les deux lectures se sont confondues jusqu'à cette tâche : `catalog_status` structurellement pesait déjà
comme `redirects` (1 outil, 1 use case, 2 importateurs — tous deux dans `api/mcp/`, tous deux internes à
l'outil lui-même) depuis le début du chantier.

## Notes / specifics

**`CatalogStatusRepository` : un port par agrégat, pas par intention (D42).** Un seul use case, un seul
port — même mesure que `redirects`/`menus`.

**Le pont `String → enum` (`SearchResourceType`, `SearchStatusFilter`) vivait déjà dans le Tool avant
cette tâche, pas dans l'ACL.** `parseResourceType`/`parseStatusFilter` sont des fonctions privées de
`SearchResourcesTool` depuis avant ce chantier — l'ACL supprimée ne portait aucune conversion
`String → enum` ni aucune autre traduction : `CatalogStatusExposedServiceImpl.searchResources` était une
délégation à une ligne vers `SearchResourcesUseCase.execute`, mêmes paramètres déjà typés, même retour.
Contrairement à `seo` (où l'ACL portait la conversion d'un `resource_type` polymorphe et où ce pont a dû
remonter dans le Tool au moment de la descente), il n'y avait ici rien à faire remonter : le pont était
déjà au bon endroit, et il ne cache aucune logique au-delà de la validation d'énumération (une valeur
non reconnue produit `null`, traité par le Tool via `CatalogStatusToolResults.invalidResourceType` —
inchangé).

**La détection « enrichi » (`isEnriched`) décode le métachamp secondaire différemment selon
`resourceType`, et c'est resté dans le use case.** Une collection porte son texte d'introduction en
Lexical rich text (`RichText.toPlainText`), un article porte ses sections en tableau JSON de chaînes
(`RichText.parseStringArray`) — le choix entre les deux dépend de `resourceType`, fourni par l'appelant,
pas de ce que Shopify a renvoyé pour ce champ précis. C'est un calcul métier réel (pas du rendu) :
`isEnriched` pilote l'inclusion du résultat sous le filtre `UNTREATED` et le statut affiché
(`displayedStatus`) quand `contentStatus` est absent. Cette dépendance à `RichText`
(`shopify.exposed_interface`) est ce qui a exigé la sortie du bloc JSON brut hors du domaine (R2) : le
use case appelle `RichText` avec des `String?` déjà extraits, jamais avec un `JsonElement`.

**Le parsing JSON brut (extraction des valeurs de métachamp depuis la réponse GraphQL) est descendu en
`spi/shopify/CatalogStatusGraphQL.kt`, seul changement structurel forcé par R2.** Avant cette tâche,
`SearchResourcesUseCase` importait `kotlinx.serialization.json.*` directement dans `domain/` — toléré
tant que le module restait dans `NOT_YET_REALIGNED`, plus après sa sortie. Le use case ne reçoit
désormais que des `CatalogStatusResourceNode` déjà typés (`id`, `title`, `handle`,
`contentStatus: String?`, `summary: String?`, `secondarySignal: String?`) ; toute décision de filtrage
et de statut affiché reste dans `SearchResourcesUseCase.summaryOrNull`.

**Ce qui reste dans `SearchResourcesUseCase` après purification, et pourquoi (les deux questions du
gabarit) :**
- *Qu'est-ce qui dépend de ce que l'appelant a fourni ?* Le choix de décodage `RichText` selon
  `resourceType` et l'inclusion/le statut affiché selon `statusFilter` (les deux ci-dessus) — aucun des
  deux ne peut être reconstruit depuis la seule réponse Shopify, tous deux dépendent d'un paramètre
  fourni par l'appelant. Le trim de `query` (chaîne blanche → `null` avant l'appel au port) suit le même
  patron que `menus`/`ListMenusUseCase` : une normalisation de l'entrée, faite dans le use case, avant
  l'appel réseau.
- *Qu'est-ce qui existe pour éviter un appel réseau ?* Rien — comme `menus`/`redirects`, `search_resources`
  appelle toujours le port, y compris avec une requête vide (transformée en `null`). Il n'y a pas de
  no-op à court-circuiter : chercher est toujours un appel légitime. La validation qui pourrait
  ressembler à une garde (`resource_type` non reconnu) se fait **avant** d'atteindre le use case, dans le
  Tool (`parseResourceType` renvoie `null`, court-circuité par `CatalogStatusToolResults.invalidResourceType`)
  — elle n'a jamais fait partie du use case, ni avant ni après cette tâche.

**`errorResult`, `invalidResourceType`, `withBanner` sont dupliqués en `private` dans
`catalog_status/api/mcp/CatalogStatusToolResults.kt` (D58)**, même justification que les cinq modules
précédents : helpers de rendu MCP pur, couverts par R13 plutôt que partagés. **`invalidGidType` n'est
pas dupliqué ici** : `SearchResourcesTool` ne valide aucun gid en entrée (ses paramètres sont un type de
ressource, une requête et un filtre de statut — jamais un identifiant Shopify). **`slugFor` est importé
directement depuis `tenancy.exposed_interface`**, comme sur les cinq modules précédents.

**Aucun `*Result` de `catalog_status` ne porte de champ `text`** (mesuré par recherche dans le module) :
E6 était déjà soldé avant ce chantier, comme le notait déjà la fiche `BE-20`.

**Resserrement de R7 (`HexagonalArchitectureTest`), mandaté par `D60` dans le même commit que la
descente.** `catalog_status` sort de `CROSS_CUTTING_VIEW_MODULES` et entre dans `CATALOG_MODULES` : ce
privilège donnait au module le droit d'atteindre les domaines catalogue (`pages`, `products`, `seo`,
`menus`, `metaobjects`, `redirects`, `collections`) en plus de `shared_kernel`/`shopify` — mesuré par le
CTO, aucune classe ne l'exerçait. Après la descente, le module n'importe que `shared_kernel` (types
d'erreur) et `shopify.exposed_interface` (`ShopifyAdminGateway`, `RichText`), exactement l'ensemble
`CATALOG_FAMILY_ALLOWED` déjà applicable aux six autres modules catalogue.
