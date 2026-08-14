# `pages`

## Context

`pages` porte les Shopify Pages natives (contenu HTML statique servi par le thème — pas un produit, pas
une commande) : leur cycle de vie complet — lister, créer, mettre à jour, publier/dépublier, supprimer —
et leurs `custom.*` metafields. C'est le module pilote du réalignement hexagonal (D50) : il a été choisi
parce qu'il porte, à lui seul, tous les écarts que le chantier devait résoudre (E1 à E4 et E6 au
complet), un `*Result` pur et six hybrides texte/données, sept outils couverts par cassette. Ce
`module.md` est le premier écrit dans ce dépôt (0/16 avant lui) : sa forme est le gabarit que les quinze
modules suivants copient (D50, point de méthode non négociable).

## Use cases

| Use case | Signature |
|---|---|
| `ListPagesUseCase` | `execute(storeId: String, query: String?): Either<UseCaseError, ListPagesResult>` |
| `CreatePageUseCase` | `execute(storeId: String, title: String, body: String, handle: String?, publish: Boolean?): Either<UseCaseError, CreatePageResult>` |
| `UpdatePageUseCase` | `execute(storeId: String, pageId: String, title: String?, body: String?, handle: String?): Either<UseCaseError, UpdatePageResult>` |
| `DeletePageUseCase` | `execute(storeId: String, pageId: String): Either<UseCaseError, DeletePageResult>` |
| `TogglePagePublishUseCase` | `execute(storeId: String, pageId: String, target: Boolean): Either<UseCaseError, TogglePagePublishResult>` |
| `GetPageMetafieldsUseCase` | `execute(storeId: String, pageId: String, keys: List<String>?): Either<UseCaseError, GetPageMetafieldsResult>` |
| `UpdatePageMetafieldsUseCase` | `execute(storeId: String, pageId: String, metafields: List<PageMetafieldInput>): Either<UseCaseError, UpdatePageMetafieldsResult>` |

## Ports (`domain/repositories/`)

Un seul port, pour un seul agrégat — la Page Shopify et ses metafields, qui ne vivent pas l'un sans
l'autre (D42) :

**`PageRepository`**
- `list(storeId: String, query: String?): Either<UseCaseError, PageListing>`
- `get(storeId: String, pageId: String): Either<UseCaseError, PageNative?>`
- `create(storeId: String, title: String, body: String, handle: String?, publish: Boolean?): Either<UseCaseError, PageWriteOutcome>`
- `update(storeId: String, pageId: String, patch: PagePatch): Either<UseCaseError, PageWriteOutcome>`
- `setPublished(storeId: String, pageId: String, target: Boolean): Either<UseCaseError, PageWriteOutcome>`
- `delete(storeId: String, pageId: String): Either<UseCaseError, PageDeleteOutcome>`
- `metafields(storeId: String, pageId: String): Either<UseCaseError, PageMetafieldsSnapshot?>`
- `setMetafields(storeId: String, pageId: String, metafields: List<PageMetafieldInput>): Either<UseCaseError, PageMetafieldsWriteOutcome>`

Implémentation : `spi/shopify/PageShopifyRepository`, sur `spi/shopify/PageGraphQL`.

## Outils MCP exposés (`api/mcp/`)

7 classes, 8 méthodes `@McpTool` (`TogglePagePublishTool` en porte deux, sur le même use case) :

| Nom | Description |
|---|---|
| `list_pages` | Liste les Pages de la boutique (id, titre, handle) ; filtrable par requête de recherche Admin. |
| `create_page` | Crée une Page (titre + corps HTML natif, handle optionnel) ; brouillon par défaut. |
| `update_page` | Met à jour partiellement une Page (titre, corps HTML, handle) ; seuls les champs fournis changent. |
| `delete_page` | Supprime définitivement une Page (action irréversible). |
| `publish_page` | Publie une Page en brouillon. |
| `unpublish_page` | Dépublie une Page sans la supprimer. |
| `get_page_metafields` | Lit les `custom.*` metafields déjà écrits sur une Page. |
| `update_page_metafields` | Écrit un ou plusieurs `custom.*` metafields sur une Page. |

## `exposed_interface`

Aucune — ce module n'expose rien vers les autres modules (D44 : l'ACL catalogue disparaît une fois E4
fait ; F1 mesuré — zéro consommateur hors `api/mcp/`).

## Événements

Aucun — `pages` ne publie ni ne consomme d'événement applicatif.

## Notes / specifics

**`PageRepository` : un port par agrégat, pas par intention (D42).** Sept use cases, un seul port. La
tentation naturelle est un port par méthode de use case ; c'est le mauvais découpage — ça duplique le
use case en interface sans ajouter de frontière. Le bon critère est la ressource racine Shopify : tout
ce qui ne survit pas sans la Page (ici, ses metafields) reste sur le même port.

**Les types de sortie du port sont des `sealed interface`, jamais des data class à champs nullables.**
`PageWriteOutcome`, `PageDeleteOutcome`, `PageMetafieldsWriteOutcome` forcent l'appelant à traiter
`Success`/`Failed` explicitement — un champ nullable aurait laissé un état ambigu passer le compilateur.

**`RoutedToolPipeline`/`AuthenticatedToolPipeline` ont dû être rendus atteignables depuis `api/mcp/`
(BE-4) avant que ce module puisse les utiliser.** Un futur module qui descend son rendu vers `<module>/api/mcp/`
aura le même besoin — ce n'est pas à redécouvrir, la portée d'atteignabilité déjà posée par BE-4 couvre
tout `api/mcp/`.

**Ordre de commit qui a fonctionné, à répéter** : purifier le domaine — retirer le framework, retyper la
sortie des use cases, faire disparaître l'ACL (BE-5) — **avant** de descendre le rendu vers
`<module>/api/mcp/` (BE-7). Et avant de toucher au rendu, auditer les tests de rejeu (BE-6, D47) pour
repérer ceux qui assertent sur `result.text` plutôt que sur le `CallToolResult` rendu : ce sont eux qui
casseraient silencieusement si on descend le code avant l'assertion.

**Les tests de rejeu (`*CassetteReplayTest`) assertent sur le `CallToolResult` rendu, pas sur une
chaîne intermédiaire.** C'est ce qui les a rendus insensibles au déplacement du rendu de `domain/` vers
`api/mcp/` : les 7 outils ont changé d'implémentation sans qu'une seule assertion de cassette ne bouge.

**`errorResult`, `invalidGidType`, `withBanner` sont dupliqués en `private`/local dans
`pages/api/mcp/PageToolResults.kt`, volontairement (D58) : ce sont des helpers de rendu MCP pur, et le
risque de dérive de format entre modules se couvre par une règle ArchUnit (R13, sur le bandeau
`Boutique : `), pas par du code partagé.** `slugFor` en revanche est **encore** dupliqué au même
endroit à ce stade (une extension `AccessExposedService.slugFor` locale à `pages`, identique au
caractère près à ses copies dans les autres modules) — D58 a tranché qu'il doit remonter dans
`tenancy/exposed_interface/`, parce qu'il consulte `AccessExposedService` et porte une politique de
tenancy, pas un format. **Ce déplacement est un lot préalable distinct**, pas fait par ce module :
un helper qui touche à l'`exposed_interface` d'un autre module n'est pas un helper de rendu, il ne se
duplique pas — il remonte. Un futur module qui descend son rendu doit reproduire cette distinction dès
le départ plutôt que de dupliquer `slugFor` une fois de plus.
