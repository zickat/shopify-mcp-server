# `tool_dispatch`

## Context

`tool_dispatch` est le point d'entrée MCP du serveur : le pipeline d'authentification/autorisation/audit
(`AuthenticatedToolPipeline`), le routage natif/relayé (`RoutedToolPipeline`), et les deux seuls outils
qui n'appartiennent à aucun domaine métier (`list_stores`, `use_store`). Il n'a pas de `domain/` — ce
module socle n'avait rien à faire pour E1/E5 (D50/BE-23), seul le `module.md` (R11) était dû.

Le module s'appelait `api` jusqu'à `BE-26` (`D55`) — nom qui entrait en collision avec le nom de couche
`api/` que chaque module catalogue porte, ce qui empêchait toute règle ArchUnit de couche de distinguer
« la couche `api/` d'un module » de « le module `api` lui-même ». `D64` amende `D55` : les deux outils
ne descendent pas chez `tenancy` (ce sont les seuls clients de `tenancy.exposed_interface` qui forment
le plan de contrôle du dispatch, pas des outils métier de `tenancy`) — ils restent ici, renommés dans
la foulée.

## Outils MCP exposés (`mcp/`)

| Nom | Description |
|---|---|
| `list_stores` | Liste les boutiques accordées à l'identité de l'appelant et nomme celle active pour la session, s'il y en a une. |
| `use_store` | Sélectionne la boutique sur laquelle agiront tous les appels d'outil suivants de cette session MCP. |

`RelayToolRegistrarConfiguration` enregistre dynamiquement, en plus, tous les outils relayés déclarés
dans le manifeste (`relay`) — pas des `@McpTool` statiques, un enregistrement programmatique au
démarrage.

**Étape intermédiaire de `BE-26`** : à ce commit, ces classes vivent encore en `tool_dispatch/mcp/` et
non `tool_dispatch/api/mcp/` — le renommage du module (commit B) précède la création de la vraie couche
`api/` (commit C, qui complète `D55`). `R9` (`endsWith(".api.mcp")`) ne juge donc pas encore ce module à
ce commit précis ; elle le jugera dès le commit suivant.

## `exposed_interface`

**`AuthenticatedToolPipeline`** — orchestre, pour chaque appel d'outil : résolution d'identité (JWT →
`identity`), audit (`audit`), résolution d'accès (`tenancy`), autorisation par rôle
(`ToolAccessControl`). Trois variantes : `runForStore`, `runForIdentity`, `runForActiveStore`.

**`RoutedToolPipeline`** — au-dessus de `AuthenticatedToolPipeline`, décide si l'appel est natif ou
relayé (`relay.RelayGateway.routeFor`) avant de dispatcher.

Consommés par tous les modules qui exposent un outil MCP natif (`pages`, `products`, …) et par `relay`
(pour les outils relayés).

## Événements

Aucun — `tool_dispatch` ne publie ni ne consomme d'événement applicatif.

## Notes / specifics

**Pas de `domain/` dans ce module : `list_stores` et `use_store` sont des orchestrations pures
d'`exposed_interface` d'autres modules (`tenancy`), sans logique métier propre à `tool_dispatch`.** Rien
à purifier au sens E1 — les classes `mcp/*Tool.kt` gardent leurs annotations Spring (`@Service`,
`@McpTool`), ce qui est attendu : la couche `api/` (à naître au commit C) n'est pas soumise à R1
(réservé à `domain/`).

**`ToolDispatchConfiguration.kt` (module root, ex-`ApiConfiguration.kt`) porte encore `@ComponentScan`,
non traité par cette tâche** : `tool_dispatch` n'a pas de `domain/`, donc aucune règle ArchUnit (R1) ne
l'atteint ici — contrairement à `vault`/`identity`/`audit`/`tenancy`/`relay`/`shopify`, où retirer
`@ComponentScan` faisait partie d'E1. Signalé, pas corrigé : hors du mandat mesuré de `BE-23` (« rien à
faire pour E1/E5 »), et hors du mandat de `BE-26`, qui ne porte que sur `D55`/`D64`/`D65`/`D66`.

**`McpToolResults` a été renommé `ToolDispatchToolResults`** (`BE-26`, commit A′) — la convention du
dépôt depuis `BE-7` est `<Module>ToolResults`, et le module qui l'héberge s'appelle désormais
`tool_dispatch`. Sa méthode `storeActivated` appelle la copie `private` de `withBanner` posée dans le
même fichier (`D58` consigne 1, jamais appliquée jusqu'ici) ; `describeStores` n'en a pas — elle rend
l'état de **toutes** les boutiques accordées et n'a donc pas de boutique unique à bannerer (`D65`).
