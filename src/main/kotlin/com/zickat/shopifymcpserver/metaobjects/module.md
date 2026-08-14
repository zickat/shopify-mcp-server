# `metaobjects`

## Context

`metaobjects` porte le CRUD des instances Shopify Metaobject et de leurs définitions de type — la
ressource générique qui sert de socle à du contenu structuré non natif (FAQ, guides, thèmes de guide).
C'est le cinquième module descendu (D50) et le plus lourd depuis `pages` : il porte 5 des 10 `*Result`
avec `text` restants du dépôt entier (E6), une conversion de texte brut vers le Lexical JSON attendu
par les champs `rich_text_field` de Shopify, et une résolution de statut de référence
(`Metaobject.referencedBy`) partagée par trois des cinq outils.

## Use cases

| Use case | Signature |
|---|---|
| `ListMetaobjectsUseCase` | `execute(storeId: String, type: String?): Either<UseCaseError, ListMetaobjectsResult>` |
| `GetMetaobjectUseCase` | `execute(storeId: String, metaobjectId: String): Either<UseCaseError, GetMetaobjectResult>` |
| `CreateMetaobjectUseCase` | `execute(storeId: String, type: String, fields: List<MetaobjectFieldInput>): Either<UseCaseError, CreateMetaobjectResult>` |
| `UpdateMetaobjectUseCase` | `execute(storeId: String, metaobjectId: String, fields: List<MetaobjectFieldInput>): Either<UseCaseError, UpdateMetaobjectResult>` |
| `DeleteMetaobjectUseCase` | `execute(storeId: String, metaobjectId: String, confirmReferencedDeletion: Boolean): Either<UseCaseError, DeleteMetaobjectResult>` |

## Ports (`domain/repositories/`)

Un seul port, pour un seul agrégat — le metaobject, ses champs et ses références, qui ne vivent pas
l'un sans l'autre (D42) :

**`MetaobjectsRepository`**
- `listDefinitions(storeId: String): Either<UseCaseError, MetaobjectDefinitionListing>`
- `listInstances(storeId: String, type: String): Either<UseCaseError, MetaobjectInstanceListing>`
- `referenceStatus(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectReferenceStatus?>`
- `get(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectSnapshot?>`
- `create(storeId: String, type: String, fields: List<MetaobjectFieldInput>): Either<UseCaseError, MetaobjectWriteOutcome>`
- `getBeforeUpdate(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectSnapshot?>`
- `update(storeId: String, metaobjectId: String, type: String, fields: List<MetaobjectFieldInput>): Either<UseCaseError, MetaobjectWriteOutcome>`
- `getBeforeDelete(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectSnapshot?>`
- `delete(storeId: String, metaobjectId: String): Either<UseCaseError, MetaobjectDeleteOutcome>`

Implémentation : `spi/shopify/MetaobjectsShopifyRepository`, sur `spi/shopify/MetaobjectsGraphQL`
(requêtes + parsing) et `spi/shopify/MetaobjectsRichText` (résolution des types de champ déclarés côté
Shopify puis conversion texte brut → Lexical JSON pour les champs `rich_text_field`).

**Neuf méthodes, un seul agrégat — pourquoi pas moins.** `get`/`getBeforeUpdate`/`getBeforeDelete`
lisent la même forme (`id type fields { key value }`) mais sous trois noms d'opération GraphQL
distincts (`GetMetaobject`/`GetMetaobjectBeforeUpdate`/`GetMetaobjectBeforeDelete`, préexistants au
chantier) : les tests de rejeu comparent la requête **envoyée** à la requête **enregistrée dans la
cassette** (`CassetteEquivalence.assertShopifyAdminGraphQLRequestMatches`), donc fusionner ces trois
lectures en une seule méthode de port aurait changé le texte de requête émis par deux des trois
appelants — un risque de régression pour zéro gain fonctionnel. Idem `create`/`update` : deux mutations
distinctes malgré une mécanique de conversion de champs identique.

## Outils MCP exposés (`api/mcp/`)

5 classes, 5 méthodes `@McpTool` :

| Nom | Description |
|---|---|
| `list_metaobjects` | Sans `type` : liste les définitions de metaobject du store. Avec `type` : liste les instances de ce type, champs bruts et statut de référence. |
| `get_metaobject` | Retrouve une instance déjà identifiée par son id, même niveau d'information qu'une entrée de liste. |
| `create_metaobject` | Crée une instance d'un type déjà défini côté Shopify ; conversion automatique des champs `rich_text_field`. |
| `update_metaobject` | Met à jour partiellement les champs d'une instance existante ; conversion automatique des champs `rich_text_field`. |
| `delete_metaobject` | Supprime définitivement une instance ; refuse par défaut si elle est encore référencée, sauf `confirm_referenced_deletion: true`. |

## `exposed_interface`

Aucune — ce module n'expose rien vers les autres modules (D44 : zéro consommateur hors `api/mcp/`,
mesuré par recherche du package `metaobjects.` en dehors de `metaobjects/`).

## Événements

Aucun — `metaobjects` ne publie ni ne consomme d'événement applicatif (mesuré : aucune référence à
`ApplicationEvent`/`ApplicationEventPublisher`/`@EventListener` dans le module).

## Notes / specifics

**`ListMetaobjectsUseCase` bifurque entre deux vues du même agrégat, pas deux agrégats.** Sans `type` :
liste les *définitions* (le catalogue des types disponibles). Avec `type` : liste les *instances* d'un
type donné. Ce sont deux requêtes GraphQL différentes et deux formes de retour différentes
(`ListMetaobjectsResult` est un `sealed interface` à deux variantes, `Definitions`/`Instances`), mais
la ressource racine Shopify reste la même (`Metaobject`/`MetaobjectDefinition` sont la même famille
d'API, l'une décrivant l'autre) — même lecture que `pages` où metafields et Page restent un seul port
parce qu'ils ne vivent pas l'un sans l'autre. Deux méthodes de port (`listDefinitions`/`listInstances`)
plutôt qu'une seule avec un paramètre nullable, pour que chaque méthode retourne un type de listing
propre à sa forme (D42 : le critère est l'agrégat, pas la variante d'appel — ici les deux variantes
partagent l'agrégat mais pas la forme de sortie, donc deux méthodes typées valent mieux qu'une méthode
qui retournerait une union codée en champs nullables).

**`RichTextConversion.kt` et `MetaobjectReferences.kt` (noms d'avant la descente) ne portaient pas de
métier propre au module — ils sont descendus en `spi/shopify/`, contrairement à l'intuition initiale de
la fiche.** Lecture, pas nom de fichier (même piège que `seo`/`redirects`/`menus`) :
- La résolution des types de champ déclarés (`fieldDefinitions`) et la conversion texte brut → Lexical
  JSON n'existent que pour produire la valeur exacte que l'API Shopify attend sur un champ
  `rich_text_field` — la même bascule d'appartenance que `RichText`/`ShopifyMetafields` déjà côté
  `shopify/exposed_interface/` (D43). Si Shopify changeait de format de rich text, cette conversion
  changerait entièrement ; aucune règle métier de `metaobjects` n'en dépend. `spi/shopify/MetaobjectsRichText.kt`.
- `fetchMetaobjectReferences` exécute un appel GraphQL et parse sa réponse en `MetaobjectReferenceStatus`
  — E3 à la lettre. Descendu dans `MetaobjectsGraphQL.parseReferenceStatus` + `MetaobjectsShopifyRepository.referenceStatus`.
- **Ce qui reste domaine, à l'inverse** : `isOrphan(status): Boolean`
  (`metaobjects/domain/models/MetaobjectReferenceStatus.kt`) est un fait pur sur un type de domaine,
  invariant par langue — il pilote la décision de refus de `DeleteMetaobjectUseCase`. Et
  `MetaobjectReferenceStatus` lui-même (le sealed interface, avec ses trois branches et la liste des
  référenceurs) reste un modèle de domaine : c'est la donnée, pas sa formulation.
- `formatReferenceStatus`/`formatFields` rendaient du français — E6, descendus tels quels dans
  `metaobjects/api/mcp/MetaobjectToolResults.kt` (`private`, non testés isolément ailleurs que via les
  cinq fonctions de rendu publiques).

**Aucune forme hybride retenue.** La fiche envisageait qu'un des cinq `*Result` garde un champ `text?`
sur la seule branche succès (comme le nom de `products.CreateMetaobjectResult` le suggère côté
produit). Vérifié sur les cinq fichiers réels : chaque phrase se décompose intégralement en champs
typés déjà présents dans la réponse Shopify ou dans l'entrée de l'appelant (identifiant, type, liste de
`MetaobjectFieldValue`, `MetaobjectReferenceStatus?`, détail d'échec déjà formaté côté SPI comme sur
`pages`/`redirects`/`seo`). Aucun résidu textuel ne justifiait de garder `text?` quelque part — les
cinq sont retypés en forme pure.

**Ce qui reste dans chaque use case après purification, et pourquoi (les deux questions du gabarit) :**
- *Qu'est-ce qui dépend de ce que l'appelant a fourni ?* `CreateMetaobjectResult.fields` et
  `UpdateMetaobjectResult.fields` restent les champs **tels que fournis par l'appelant** (texte brut,
  avant conversion Lexical JSON) — pas ce que Shopify a stocké après conversion : c'est la confirmation
  lue par l'agent IA, elle doit rester lisible. `DeleteMetaobjectUseCase` : la décision de refus
  (`!orphan && !confirmReferencedDeletion`) dépend entièrement de l'argument `confirmReferencedDeletion`
  fourni par l'appelant croisé avec le fait Shopify (`referenceStatus`) — c'est la seule vraie décision
  métier du module, elle reste dans le use case.
- *Qu'est-ce qui existe pour éviter un appel réseau ?* Rien de nouveau introduit par cette descente :
  les cinq use cases appellent systématiquement le port, aucune garde d'entrée ne court-circuite un
  appel Shopify (contrairement à `seo`/`redirects`). La seule garde de séquencement est
  `DeleteMetaobjectUseCase`, qui évite l'appel de mutation (`delete`) — pas un appel de lecture — quand
  la décision de refus est prise après la lecture de `referenceStatus`.

**`errorResult`, `invalidGidType`, `withBanner` sont dupliqués en `private` dans
`metaobjects/api/mcp/MetaobjectToolResults.kt` (D58)**, même justification que les quatre modules
précédents. **`slugFor` est importé directement depuis `tenancy.exposed_interface`**, jamais dupliqué.

**Ce que les quatre gabarits (`pages`, `seo`, `redirects`, `menus`) ne couvraient pas ici** : aucun des
quatre n'avait de use case qui orchestre **plusieurs appels de port pour construire un seul résultat en
boucle** (`list_metaobjects` avec type : un appel de liste puis un appel de statut de référence *par
instance*). L'ordre et le nombre d'appels réseau sont préservés à l'identique par construction — le use
case reproduit exactement la boucle qui vivait avant dans le code mêlé domaine/transport, seule la
fabrication du texte français en sort.
