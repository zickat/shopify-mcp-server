# `redirects`

## Context

`redirects` porte la création d'Admin URL Redirects Shopify — le mécanisme qui évite qu'un chemin
public cassé (collection supprimée, guide réorganisé) tombe en 404. C'est le troisième module
descendu (D50) : le plus petit des seize, un seul outil, choisi pour vérifier que le gabarit posé par
`pages` et validé par `seo` tient sans le poids d'E6 ni d'un deuxième port.

## Use cases

| Use case | Signature |
|---|---|
| `CreateRedirectUseCase` | `execute(storeId: String, fromPath: String, toPath: String): Either<UseCaseError, CreateRedirectOutcome>` |

## Ports (`domain/repositories/`)

Un seul port, pour un seul agrégat — la redirection elle-même (D42) :

**`RedirectsRepository`**
- `create(storeId: String, fromPath: String, toPath: String): Either<UseCaseError, CreateRedirectOutcome>`

Implémentation : `spi/shopify/RedirectsShopifyRepository`, sur `spi/shopify/RedirectsGraphQL`.
L'interprétation de la réponse Shopify (aucune `userError` → créée, message « path has already been
taken » → déjà existante, tout autre `userError` → échec formaté) vit entièrement dans cette
implémentation — voir « Notes / specifics ».

## Outils MCP exposés (`api/mcp/`)

1 classe, 1 méthode `@McpTool` :

| Nom | Description |
|---|---|
| `create_redirect` | Crée une redirection Admin d'un ancien chemin public (from_path) vers un nouveau (to_path). Idempotent : recréer une redirection déjà existante pour ce from_path est un succès, sans mettre à jour sa cible. |

## `exposed_interface`

Aucune — ce module n'expose rien vers les autres modules (D44 : zéro consommateur hors `api/mcp/`,
mesuré par recherche du package `redirects.` en dehors de `redirects/`).

## Événements

Aucun — `redirects` ne publie ni ne consomme d'événement applicatif (mesuré : aucune référence à
`ApplicationEvent`/`ApplicationEventPublisher`/`@EventListener` dans le module).

## Notes / specifics

**`RedirectsRepository` : un port par agrégat, pas par intention (D42).** Un seul use case, un seul
port — pas de tentation de découpage ici, le module n'a qu'une seule opération.

**La garde d'entrée reste dans le use case, la traduction de la réponse Shopify descend entièrement
dans le repository — et c'est tout ce qui reste au-dessus du port.** Contrairement à `seo`
(`UpdateSeoUseCase`), `redirects` n'a pas de second motif de rester substantiel : il n'y a rien à
recalculer à partir de ce que l'appelant a fourni par opposition à ce que Shopify a renvoyé (l'outil
crée, il ne rapproche pas un état avant/après). Le use case se réduit donc à une garde en expression
conditionnelle (`fromPath`/`toPath` vides → `CreateRedirectOutcome.invalidInput(...)` sans appel
réseau, forme retenue sur `seo`) suivie d'une délégation directe au port :
- la garde existe pour **ne pas appeler le réseau** quand un chemin requis est vide — c'est la
  garantie de zéro appel Shopify sur une entrée invalide, une décision qui doit précéder l'appel ;
- il n'y a **aucun** calcul dépendant de ce que l'appelant a fourni : `CreateRedirectOutcome` ne
  distingue jamais une valeur demandée d'une valeur renvoyée, il n'y a qu'un seul chemin de
  succès (`Created`/`AlreadyExists`), pas de fusion à faire remonter.

La reconnaissance du message Shopify « path has already been taken » (regex insensible à la casse) et
le mapping des `userErrors` restants vers un texte formaté sont de l'interprétation de réponse
Shopify — transport, pas métier — et vivent dans `RedirectsShopifyRepository`, au même titre que le
dispatch NATIVE/METAFIELD de `SeoShopifyRepository`.

**`CreateRedirectOutcome` n'est pas un `sealed interface`** — c'est une `data class` à champ `status`
(enum) et deux champs nullables (`failureDetail`, `invalidField`), inchangée depuis avant ce
chantier ; seul son package a changé (`exposed_interface/model/` → `domain/models/`, en tant que type
de retour du port désormais que l'ACL est supprimée). Elle ne porte aucun champ `text` (E6 était déjà
soldé), ce qui suffit à R10 — mais elle reste l'exception au conseil « types de sortie du port en
`sealed interface` » posé par `pages/module.md` : une conversion en sealed interface est possible et
alignerait le module sur `PageWriteOutcome`/`SeoWriteOutcome`, mais n'a pas été faite ici (hors
mandat de cette descente, voir `progress.md` de `BE-17`).

**`errorResult`, `invalidRedirectInputMessage`, `withBanner` sont dupliqués en `private` dans
`redirects/api/mcp/RedirectsToolResults.kt` (D58)**, même justification que `pages` et `seo` : helpers
de rendu MCP pur, couverts par R13 plutôt que partagés. **`invalidGidType` et `slugFor` ne sont ni
l'un ni l'autre dupliqués ici** : `CreateRedirectTool` ne valide aucun gid (ses deux paramètres sont
des chemins, pas des identifiants Shopify — `invalidGidType` n'a jamais eu sa place dans ce module),
et `slugFor` est importé directement depuis `tenancy.exposed_interface`, comme sur `seo`.

**Aucun `*Result` de `redirects` ne porte de champ `text`** (mesuré par recherche dans le module) : E6
était déjà soldé avant ce chantier.
