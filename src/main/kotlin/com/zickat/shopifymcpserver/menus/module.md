# `menus`

## Context

`menus` porte la lecture et l'écriture des menus de navigation Shopify (header `main-menu`, footer,
autres) avec leur arbre d'items complet, jusqu'à 4 niveaux. Quatrième module descendu à l'exemption
zéro (D50), il a été le dernier à en sortir : `LOT3-06` (initiative `catalog-plugin-oauth-tenancy`,
lot 3, repris après suspension) a câblé les 7 outils de mutation dont le capital dormait dans
`domain/` depuis la descente initiale — voir « Comment le cliquet est sorti » ci-dessous.

## Use cases

| Use case | Signature |
|---|---|
| `ListMenusUseCase` | `execute(storeId: String, query: String?, depth: Int?): Either<UseCaseError, ListMenusResult>` |
| `AddMenuItemUseCase` | `execute(storeId, menuId, title, resourceId?, url?, parentItemId?, position?): Either<UseCaseError, AddMenuItemResult>` |
| `RemoveMenuItemUseCase` | `execute(storeId, menuId, itemId, withChildren): Either<UseCaseError, RemoveMenuItemResult>` |
| `ReorderMenuItemsUseCase` | `execute(storeId, menuId, parentItemId?, orderedItemIds): Either<UseCaseError, ReorderMenuItemsResult>` |
| `UpdateMenuItemUseCase` | `execute(storeId, menuId, itemId, title?, resourceId?, url?): Either<UseCaseError, UpdateMenuItemResult>` |
| `UpdateMenuUseCase` | `execute(storeId, menuId, title?, handle?, confirmHandleChange): Either<UseCaseError, UpdateMenuResult>` |
| `CreateMenuUseCase` | `execute(storeId, handle, title, items: List<CreateMenuItemInput>): Either<UseCaseError, CreateMenuResult>` |
| `DeleteMenuUseCase` | `execute(storeId, menuId, confirmNonEmptyDeletion): Either<UseCaseError, DeleteMenuResult>` |

Les cinq outils qui éditent un menu **existant** (`AddMenuItemUseCase`, `RemoveMenuItemUseCase`,
`ReorderMenuItemsUseCase`, `UpdateMenuItemUseCase`, `UpdateMenuUseCase`) ne parlent jamais directement
au port : ils passent tous par `MenuRewriteEngine` (classe nue, sans `@Bean` propre autre que
son injection — câblée dans `MenusDomainBeansConfiguration`), l'unique primitive read-modify-write du
module. `CreateMenuUseCase` et `DeleteMenuUseCase` sont les deux seuls à parler au port directement :
rien à relire pour créer, et `delete_menu` a besoin de son propre enchaînement fetch → garde-fous →
mutation, distinct du cycle de réécriture.

### `MenuRewriteEngine` — le moteur read-modify-write

Séquence, portée à l'identique de `menus.ts:503-514` (l'original TypeScript) :

1. relecture immédiate du menu (`MenusRepository.fetch`), payload déjà normalisé par le port ;
2. `precheck` optionnel (un seul appelant : `update_menu`, pour le refus de handle sur un menu par
   défaut — dépend d'un champ du menu relu, retombe donc après la lecture et avant tout écriture) ;
3. `MenuIntegrityGuards.assertNoUnreadableDepth` — refus en bloc si la sentinelle N5 a mordu ;
4. résolution du parent et des retraits déclarés, sur l'arbre relu ;
5. reconstruction immuable via `MenuTree.mapChildList` — le `transform` ne voit que sa fratrie ;
6. `assertNoSilentLoss` puis `assertDepthNotWorsened` — **avant** tout appel réseau ;
7. `MenusRepository.rewrite` (mutation `menuUpdate`), dont la réponse est comparée à l'arbre relu via
   `MenuWriteDiff.diffAfterWrite`.

Les quatre garde-fous (`MenuIntegrityGuards`) et les transformations d'arbre (`MenuTransforms`)
lèvent `MenuItemValidationError` — exception interne au module, **jamais laissée s'échapper hors du
moteur** : `MenuRewriteEngine` la rattrape à la frontière et la restitue comme un
`MenuRewriteOutcome.Failed(detail)`, `Right` d'un `Either` jamais `Left`. C'est un choix délibéré,
voir « Écart au principe "jamais d'exception dans le domaine" » plus bas.

## Ports (`domain/repositories/`)

Un seul port, pour un seul agrégat — l'arbre de menu (D42) :

**`MenusRepository`**
- `list(storeId, query): Either<UseCaseError, MenuListing>`
- `fetch(storeId, menuId): Either<UseCaseError, MenuNode?>`
- `rewrite(storeId, menuId, title, handle, items): Either<UseCaseError, MenuUpdateOutcome>`
- `create(storeId, handle, title, items): Either<UseCaseError, MenuCreateOutcome>`
- `delete(storeId, menuId): Either<UseCaseError, MenuDeleteOutcome>`

Implémentation : `spi/shopify/MenusShopifyRepository`, sur `spi/shopify/MenusGraphQL`. Les quatre
requêtes/mutations (`ListMenus`, `FetchMenu`, `UpdateMenu`/`menuUpdate`, `CreateMenu`/`menuCreate`,
`DeleteMenu`/`menuDelete`), la pagination par curseur, le parsing JSON → `MenuNode`/`MenuItemNode`
(`normalizeItems`/`normalizeItem`) et la sérialisation d'un `MenuItemNode` vers son entrée GraphQL
(`serializeItemForWrite`, réutilisée telle quelle par `menuUpdate` et `menuCreate`) vivent entièrement
dans cette implémentation — c'est la totalité du contact avec `kotlinx.serialization.json` du module.
Le paramètre `depth` de l'outil `list_menus` **n'entre pas dans le port** : la requête GraphQL lit
toujours 4 niveaux (`MENU_ITEMS_TREE`), `depth` ne pilote que l'affichage — voir « Notes / specifics ».

## Outils MCP exposés (`api/mcp/`)

8 classes, 8 méthodes `@McpTool` :

| Nom | Description | Kind |
|---|---|---|
| `list_menus` | Liste les menus avec leur arbre complet (profondeur d'affichage réglable, 4 niveaux toujours lus). | READ |
| `add_menu_item` | Ajoute un item à un menu existant, à n'importe quel niveau (borne N3 en création). | MUTATION |
| `remove_menu_item` | Retire un item à profondeur quelconque ; refuse par défaut s'il porte des sous-items. | MUTATION |
| `reorder_menu_items` | Réordonne une fratrie de menu selon une permutation exacte. | MUTATION |
| `update_menu_item` | Modifie un item en place (titre et/ou cible), préserve `item_id` et sous-arbre. | MUTATION |
| `update_menu` | Modifie le titre et/ou le handle du menu lui-même ; handle gardé (défaut jamais, sinon confirmation). | MUTATION |
| `create_menu` | Crée un menu (`menuCreate`), items de premier niveau uniquement en entrée. | MUTATION |
| `delete_menu` | Supprime un menu et tout son arbre (`menuDelete`, irréversible) ; deux garde-fous cumulatifs. | MUTATION |

## `exposed_interface`

Aucune — ce module n'expose rien vers les autres modules (D44 : zéro consommateur hors `api/mcp/`,
mesuré par recherche du package `menus.` en dehors de `menus/`).

## Événements

Aucun — `menus` ne publie ni ne consomme d'événement applicatif.

## Comment le cliquet est sorti — `LOT3-06`

À l'ouverture de `LOT3-06`, `menus` restait la dernière entrée de `DEFERRED_TO_OTHER_INITIATIVE`
(`"menus" to "lot 3 — oauth-tenancy, suspendu"`), pour deux fichiers de `domain/` violant R2
(`domainHasNoWireFormatDependency`) : `MenuWriteDiff.kt` (sérialisation JSON du plan de restauration
après une perte silencieuse) et `MenuItemFactory.kt` (validation de la forme JSON brute d'un item de
`create_menu`). Câbler les 7 outils a permis de trancher les deux, avec la connaissance réelle de ce
que ces outils exigent — ce que la note précédente de ce fichier disait explicitement attendre :

- **`MenuItemFactory`** — sa fonction `validateCreateMenuItem(raw: JsonElement)` n'a plus de raison
  d'exister : `create_menu` reçoit ses items via un DTO typé (`CreateMenuItemInput`, lié directement
  par Spring AI/Jackson depuis le schéma de l'outil), exactement comme `update_page_metafields` lie
  `PageMetafieldInput`. Aucun autre outil natif du dépôt ne réplique la garde `.strict()` de la
  version TS (refus explicite d'une clé `id` inconnue plutôt qu'ignorée en silence) — ce n'est donc
  pas une régression propre à `menus`, c'est l'absence d'un mécanisme équivalent dans tout le
  connecteur natif, signalée ici plutôt que résolue seule.
- **`MenuWriteDiff`** — sa fonction `serializeItem` (sortie JSON pensée pour un humain, plan de
  restauration en cas de perte silencieuse après écriture) est remplacée par un rendu texte via
  `MenuTreeRenderer.renderMenuTree`, déjà du domaine pur, déjà le rendu utilisé par `list_menus` et
  par l'instantané de `delete_menu`. Aucun test — cassette ou fake — n'exerçait le format JSON exact
  de cette sortie (D34 : les 7 cassettes de `LOT3-05` s'enregistrent sur un menu jetable, aucune perte
  silencieuse n'y a été capturée), donc ce changement de format est une décision prise dans le mandat
  de cette tâche, pas un comportement observable reconstitué.

Les deux fichiers ne dépendent plus de `kotlinx.serialization.json` : mesuré, `menus` ne viole plus
aucune des 7 règles filtrées par `EXEMPTED` (R1, R2, R6b, R8, R10, R11, R13). Le module a quitté
`DEFERRED_TO_OTHER_INITIATIVE`.

## Écart au principe « jamais d'exception dans le domaine »

`guidelines/backend.md` prescrit `Either.left()`, jamais une exception levée depuis le domaine.
`MenuTransforms`, `MenuTree.mapChildList` et `MenuIntegrityGuards` — écrits avant `LOT3-06`, pour le
capital en jachère que cette tâche a câblé — lèvent `MenuItemValidationError`, une exception interne
au module. `LOT3-06` n'a pas rouvert ce choix : il l'a border. `MenuRewriteEngine` est l'unique
frontière qui la rattrape (`try/catch` autour de la séquence de garde-fous), et aucun appelant en
dehors du moteur ne peut plus l'observer — les 7 use cases ne voient qu'un `Either` propre. Signalé au
Tech Lead/CTO plutôt que tranché seul : soit ce patron (exception interne + frontière unique de
capture) est une exception assumée à la règle pour ce genre de séquence de garde-fous imbriqués, soit
il doit être réécrit en `Either` de bout en bout — ce que cette tâche n'a pas fait, faute de mandat
pour redessiner une machinerie déjà en place et déjà testée.

## Notes / specifics

**`MenusRepository` : un port par agrégat, pas par intention (D42).** Cinq méthodes, un seul port —
même mesure que `redirects`/`pages`.

**`MenuTreeRenderer` reste dans `domain/`.** Il rend du texte français (arbre numéroté, bandeaux,
avertissements de troncature) et sert maintenant TROIS usages câblés : l'affichage de `list_menus`,
l'instantané de `delete_menu`, et le plan de restauration de `MenuWriteDiff.diffAfterWrite`. Sa
fonction `deriveMenuItemType` (dérivation du type d'item depuis un gid ou une URL) reste un choix
métier appelé par `MenuItemFactory.buildNewMenuItem` — classer une ressource externe, pas la mettre en
forme pour un humain.

**Le paramètre `depth` de l'outil ne fait pas partie du port.** La requête GraphQL
(`spi/shopify/MenusGraphQL.MENU_ITEMS_TREE`) lit systématiquement 4 niveaux ; `depth` (1 à 4, défaut 3)
ne pilote que ce que `MenuTreeRenderer` affiche.

**`errorResult`/`withBanner`/`withWarnings` sont dupliqués en `private` dans
`menus/api/mcp/MenuToolResults.kt` (D58)**, même justification que `redirects`/`pages` : helpers de
rendu MCP pur, couverts par R13 plutôt que partagés. **`invalidGidType` est ajouté par `LOT3-06`** —
les 7 nouveaux outils valident `menu_id`/`item_id`/`parent_item_id`/`ordered_item_ids` avant tout appel
réseau, contrairement à `list_menus` qui n'en avait pas besoin. **`slugFor` est importé directement
depuis `tenancy.exposed_interface`**, comme sur `seo`/`redirects`/`pages` — jamais sa propre copie.

**Résultats des 7 outils de mutation : `enum` + `data class` à champs nullables, pas `sealed
interface`.** Mesuré sur le gabarit indiqué par la tâche (`pages`, `metaobjects`) avant d'écrire :
`UpdatePageResult`, `DeleteMetaobjectResult`, etc. suivent tous ce patron (`enum class XxxOutcome` +
`data class XxxResult(val outcome: XxxOutcome, ...champs nullables, companion object { fun xxx(...) })`),
pas des `sealed interface`. `menus` suit le même patron pour rester cohérent avec le reste du dépôt —
écart assumé par rapport à la fiche de dispatch qui demandait des `sealed interface`, signalé plutôt
que tranché en silence.

**Modélisation des refus : `Either.Right` avec un `outcome` dédié, jamais `Either.Left`.** Un refus de
garde-fou (profondeur dépassée, item introuvable, permutation invalide, handle non confirmé…) porte un
texte français précis, déjà éprouvé par les cassettes `LOT3-05` — `Either.Left`/`UseCaseErrorException`
ne rend qu'une `messageKey` i18n, pas un texte dynamique déjà formé. Chaque refus devient donc une
valeur d'`outcome` (`INVALID_ITEM`, `REFUSED`, `NO_FIELD_PROVIDED`, `AMBIGUOUS_TARGET`,
`HANDLE_CHANGE_NOT_CONFIRMED`, `FAILED`…) rendue par `MenuToolResults`, jamais un `Left`. `Either.Left`
reste réservé aux erreurs techniques/infra propagées depuis le port (ex. `TechnicalError` sur une
réponse GraphQL malformée).

## Où vit cette dette

`NOT_YET_REALIGNED` et `DEFERRED_TO_OTHER_INITIATIVE` sont tous deux **vides** depuis `LOT3-06`. Le
dépôt est à zéro exemption.
