# `menus`

## Context

`menus` porte la lecture des menus de navigation Shopify (header `main-menu`, footer, autres) avec
leur arbre d'items complet, jusqu'à 4 niveaux. C'est le quatrième module descendu (D50) — le plus
proche de `redirects` sur la partie câblée (1 outil, 1 use case), mais il porte une question que les
trois précédents n'avaient pas : quatre fichiers de domaine sans consommateur en production, du
travail commencé pour un lot de mutation de menus (`catalog-plugin-oauth-tenancy`, lot 3) et laissé en
jachère avant ce chantier. **Ce module reste dans `NOT_YET_REALIGNED` à l'issue de cette descente** —
voir « Pourquoi le cliquet ne sort pas » ci-dessous.

## Use cases

| Use case | Signature |
|---|---|
| `ListMenusUseCase` | `execute(storeId: String, query: String?, depth: Int?): Either<UseCaseError, ListMenusResult>` |

## Ports (`domain/repositories/`)

Un seul port, pour un seul agrégat — l'arbre de menu (D42) :

**`MenusRepository`**
- `list(storeId: String, query: String?): Either<UseCaseError, MenuListing>`

Implémentation : `spi/shopify/MenusShopifyRepository`, sur `spi/shopify/MenusGraphQL`. La requête
GraphQL, la pagination par curseur et le parsing JSON → `MenuNode`/`MenuItemNode` (fonctions
`normalizeItems`/`normalizeItem`, avant nommées `MenuTree.normalizeItems`) vivent entièrement dans
cette implémentation. Le paramètre `depth` de l'outil **n'entre pas dans le port** : la requête
GraphQL lit toujours 4 niveaux (`MENU_ITEMS_TREE`), `depth` ne pilote que l'affichage — voir « Notes /
specifics ».

## Outils MCP exposés (`api/mcp/`)

1 classe, 1 méthode `@McpTool` :

| Nom | Description |
|---|---|
| `list_menus` | Liste les menus de navigation avec leur arbre complet (profondeur d'affichage réglable, 4 niveaux toujours lus), avertit explicitement sur toute troncature (profondeur, plafond de lignes, pagination) et signale les menus portant des items au-delà de la profondeur lue (non modifiables par les outils d'écriture). |

## `exposed_interface`

Aucune — ce module n'expose rien vers les autres modules (D44 : zéro consommateur hors `api/mcp/`,
mesuré par recherche du package `menus.` en dehors de `menus/`). `MenusExposedService` et son
implémentation ont été supprimés (l'ACL catalogue disparaît une fois E4 fait, même mesure que
`redirects`).

## Événements

Aucun — `menus` ne publie ni ne consomme d'événement applicatif (mesuré : aucune référence à
`ApplicationEvent`/`ApplicationEventPublisher`/`@EventListener` dans le module).

## Pourquoi le cliquet ne sort pas — quatre fichiers de domaine, capital en attente du lot 3

Le module contient six fichiers dans `domain/` en plus de `ListMenusUseCase` et `MenuTree` :
`MenuTreeRenderer` (câblé, voir plus bas), et **quatre fichiers sans aucun appelant en dehors de leur
propre test** : `MenuDeletionGuards`, `MenuWriteDiff`, `MenuItemFactory`, `MenuTransforms` (ce dernier
via `MenuIntegrityGuards`, lui-même orphelin). **Reconfirmé par recherche fraîche à l'ouverture de
cette tâche** (BE-18) — aucun des quatre n'a de consommateur en dehors de `*Test.kt`, dans `src/main`
comme dans `src/test`.

**Ce ne sont pas des orphelins accidentels.** Leurs noms — garde de suppression, diff d'écriture après
mutation, fabrique d'item de menu, transformations d'arbre (insert/remove/reorder/update) — nomment
exactement les opérations qu'exigeraient les outils de mutation de menu du **lot 3** de l'initiative
`catalog-plugin-oauth-tenancy`, suspendue par ce chantier de réalignement. `BE-1` (sur `collections`)
avait mesuré 54 cassettes de production déjà enregistrées et orphelines de tout test, nommant
explicitement `add-menu-item`, `update-menu-item`, `remove-menu-item`, `delete-menu`,
`reorder-menu-items` — les mêmes outils. Val a tranché sur `collections` : ce lot sera repris. La même
lecture s'applique ici.

**Décision de cette tâche : ils restent dans `menus/domain/`, hors du port `MenusRepository`.** Les y
rattacher créerait un port avec des méthodes qu'aucun use case n'appelle — l'anti-pattern « port par
intention » que D42 proscrit. Ce n'est pas une dette à nettoyer, c'est du capital en attente de portage
— **ne pas proposer de « faire le ménage » ici sans réouvrir le lot 3.**

**Et c'est ce qui empêche `menus` de sortir de `NOT_YET_REALIGNED` : deux de ces quatre fichiers
violent R2 (`domainHasNoWireFormatDependency`).**

- `MenuWriteDiff.kt` importe `kotlinx.serialization.json.*` pour sérialiser un item en `JsonObject`
  (`serializeItem`), utilisé pour produire un plan de restauration humain (`diffAfterWrite`) en cas de
  perte silencieuse après une mutation Shopify. Ce n'est pas du parsing de réponse Shopify — c'est une
  sortie JSON pensée pour un humain — mais R2 ne distingue pas l'intention, seul le paquet importé
  compte.
- `MenuItemFactory.kt` importe `kotlinx.serialization.json.JsonElement`/`JsonObject`/`contentOrNull`/
  `jsonPrimitive` pour valider la forme brute d'un item de menu à créer (`validateCreateMenuItem`),
  avant que l'entrée outil (JSON) ne devienne un type de domaine (`NewMenuItemInput`).

Réécrire ces deux fichiers pour ne plus dépendre de `kotlinx.serialization.json` est un geste réel
(retyper leurs entrées/sorties), pas un déplacement de fichier — et ce n'est pas le mandat de cette
tâche : ces fichiers ne sont câblés à aucun use case, les toucher maintenant, c'est concevoir à l'aveugle
l'API du lot 3 avant que ses outils MCP existent. **Signalé au Tech Lead/CTO plutôt que contourné** :
`menus` reste dans `NOT_YET_REALIGNED` jusqu'à ce que le lot 3 soit repris (et purifie ces fichiers à
cette occasion, avec la connaissance réelle de ce que ses outils MCP exigent), ou jusqu'à ce qu'une
décision explicite tranche de les purifier hors mandat.

`MenuDeletionGuards.kt` et `MenuTransforms.kt`/`MenuIntegrityGuards.kt` ne portent en revanche aucune
dépendance à un framework ni à `kotlinx.serialization.json` — ils ne bloqueraient pas R1/R2 seuls.

## Notes / specifics

**`MenusRepository` : un port par agrégat, pas par intention (D42).** Un seul use case, un seul port —
même mesure que `redirects`.

**`MenuTreeRenderer` reste dans `domain/` — décision explicite, pas un défaut.** Il rend du texte
français (arbre numéroté, bandeaux, avertissements de troncature) : à lecture seule de sa forme, il
ressemblerait à un candidat de sortie vers `api/mcp/`, au même titre que les `*ToolResults.kt` d'E6.
**Mais `MenuTreeRenderer.deriveMenuItemType` — dérivation du type d'item de menu (`COLLECTION`,
`PRODUCT`, `HTTP`…) à partir d'un gid ou d'une URL — est appelé par `MenuItemFactory` (orphelin, lot
3), et c'est un choix métier, pas du rendu : classer une ressource externe, pas la mettre en forme
pour un humain.** Si `MenuTreeRenderer` descendait vers `api/mcp/`, `MenuItemFactory` — domaine par
construction, destiné à rester domaine quand le lot 3 le câblera — ne pourrait plus l'appeler sans
violer R5 (le domaine ne dépend ni de `api/` ni de `spi/`). Un même objet sert donc aujourd'hui un
usage câblé (rendu, via `ListMenusUseCase`) et un usage en jachère (classification, via
`MenuItemFactory`) : le seul découpage honnête serait de scinder `MenuTreeRenderer` en une partie
rendu et une partie classification, ce que cette tâche n'a pas mandat de faire (hors E3/E4, et
`MAX_REEMITTABLE_DEPTH`, définie dans `MenuIntegrityGuards.kt`, est déjà partagée entre le rendu et les
gardes d'intégrité du lot 3 — un découpage propre demande de revoir les deux ensemble). **Ni domaine
« par choix métier » à 100 %, ni SPI (il ne touche jamais le format Shopify — il opère sur des
`MenuNode`/`MenuItemNode` déjà parsés) : domaine par nécessité de cohabitation avec le lot 3, à
rouvrir avec lui.**

**Le paramètre `depth` de l'outil ne fait pas partie du port.** La requête GraphQL
(`spi/shopify/MenusGraphQL.MENU_ITEMS_TREE`) lit systématiquement 4 niveaux ; `depth` (1 à 4, défaut 3)
ne pilote que ce que `MenuTreeRenderer` affiche. Documenté ainsi dans la description `@McpToolParam`
de l'outil depuis avant ce chantier (« Purely cosmetic »). C'est ce qui confirme, indépendamment de la
question précédente, que le clamp `(depth ?: 3).coerceIn(1, 4)` fait partie de ce qui reste dans
`ListMenusUseCase` : il ne dépend d'aucun échange réseau, seulement de ce que l'appelant a fourni.

**Ce qui reste dans `ListMenusUseCase` après purification, et pourquoi (les deux questions du
gabarit) :**
- *Qu'est-ce qui dépend de ce que l'appelant a fourni ?* `hadQuery` (`searchQuery != null`, calculé
  sur la requête telle que fournie, trimée) et `displayDepth` (le clamp du paramètre `depth`) — les
  deux ne peuvent pas être reconstruits depuis la seule réponse Shopify.
- *Qu'est-ce qui existe pour éviter un appel réseau ?* Rien — comme `redirects`, `list_menus` appelle
  toujours le gateway, y compris avec une requête vide ou blanche (transformée en `null`). Il n'y a pas
  de no-op à court-circuiter : lister est toujours un appel légitime.

**`E6` était déjà soldé mécaniquement pour `menus` avant ce chantier** (aucun `*Result` ne porte de
champ nommé `text` — `ListMenusResult.blocks` est un `List<String>`, ce que R10 ne détecte pas par
construction, puisqu'il ne vérifie que le nom et le type exact du champ). **Signalé, pas retypé : ce
n'était pas le mandat de cette tâche**, et retyper `ListMenusResult` aurait nécessité de faire
descendre `MenuTreeRenderer` hors du domaine, ce que la note précédente exclut pour cette tâche.
`blocks` reste donc une liste de blocs déjà rendus, produite dans le use case — une exception de fait à
la lettre de la règle « un use case ne rend jamais de chaîne destinée à l'affichage », déjà actée par
le chantier avant cette tâche et non rouverte ici.

**`errorResult` et `withBanner` sont dupliqués en `private` dans `menus/api/mcp/MenuToolResults.kt`
(D58)**, même justification que `redirects` : helpers de rendu MCP pur, couverts par R13 plutôt que
partagés. **`invalidGidType` n'est pas dupliqué ici** : `ListMenusTool` ne valide aucun gid en entrée
(ses deux paramètres sont une requête de recherche et une profondeur d'affichage). **`slugFor` est
importé directement depuis `tenancy.exposed_interface`**, comme sur `seo`/`redirects` — jamais sa
propre copie.
