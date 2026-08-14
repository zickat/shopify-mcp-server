# `api`

## Context

`api` est le point d'entrée MCP du serveur : le pipeline d'authentification/autorisation/audit
(`AuthenticatedToolPipeline`), le routage natif/relayé (`RoutedToolPipeline`), et les deux seuls outils
qui n'appartiennent à aucun domaine métier (`list_stores`, `use_store`). Il n'a pas de `domain/` — ce
module socle n'avait rien à faire pour E1/E5 (D50/BE-23), seul le `module.md` (R11) était dû.

## Outils MCP exposés (`api/mcp/`)

| Nom | Description |
|---|---|
| `list_stores` | Liste les boutiques accordées à l'identité de l'appelant et nomme celle active pour la session, s'il y en a une. |
| `use_store` | Sélectionne la boutique sur laquelle agiront tous les appels d'outil suivants de cette session MCP. |

`RelayToolRegistrarConfiguration` enregistre dynamiquement, en plus, tous les outils relayés déclarés
dans le manifeste (`relay`) — pas des `@McpTool` statiques, un enregistrement programmatique au
démarrage.

## `exposed_interface`

**`AuthenticatedToolPipeline`** — orchestre, pour chaque appel d'outil : résolution d'identité (JWT →
`identity`), audit (`audit`), résolution d'accès (`tenancy`), autorisation par rôle
(`ToolAccessControl`). Trois variantes : `runForStore`, `runForIdentity`, `runForActiveStore`.

**`RoutedToolPipeline`** — au-dessus de `AuthenticatedToolPipeline`, décide si l'appel est natif ou
relayé (`relay.RelayGateway.routeFor`) avant de dispatcher.

Consommés par tous les modules qui exposent un outil MCP natif (`pages`, `products`, …) et par `relay`
(pour les outils relayés).

## Événements

Aucun — `api` ne publie ni ne consomme d'événement applicatif.

## Notes / specifics

**Pas de `domain/` dans ce module : `list_stores` et `use_store` sont des orchestrations pures
d'`exposed_interface` d'autres modules (`tenancy`), sans logique métier propre à `api`.** Rien à purifier
au sens E1 — les classes `api/mcp/*Tool.kt` gardent leurs annotations Spring (`@Service`, `@McpTool`),
ce qui est attendu : la couche `api/` n'est pas soumise à R1 (réservé à `domain/`).

**`D55` (renommer `api` en `tool_dispatch`) reste différée** — hors périmètre de `BE-23`, atteignable
seulement une fois E4 complet sur les seize modules.

**`ApiConfiguration.kt` (module root) porte encore `@ComponentScan`, non traité par cette tâche** :
`api` n'a pas de `domain/`, donc aucune règle ArchUnit (R1) ne l'atteint ici — contrairement à
`vault`/`identity`/`audit`/`tenancy`/`relay`/`shopify`, où retirer `@ComponentScan` faisait partie
d'E1. Signalé, pas corrigé : hors du mandat mesuré de `BE-23` (« rien à faire pour E1/E5 »).
