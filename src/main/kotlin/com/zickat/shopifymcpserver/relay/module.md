# `relay`

## Context

`relay` porte le manifeste des outils (`RelayManifest` — quel outil est natif, lequel est relayé vers le
process TypeScript legacy) et le pont d'appel vers ce process (`RelayDispatcher`, `RelayTsClient`).
C'est ce qui permet à `api` de dispatcher un appel d'outil sans savoir s'il est natif ou relayé. Module
socle (D50) : ni port catalogue ni outil MCP à descendre, seuls E1 et E5 le concernaient (BE-23).

## Use cases

| Use case | Signature |
|---|---|
| `RelayDispatcher.callRelayedTool` | `(toolName: String, toolInput: JsonElement, storeId: String, role: AccessRole): Either<UseCaseError, RelayToolOutcome>` |

`RelayManifest` n'est pas un `[Domain]UseCase` (pas de logique métier, une table de routage
immuable construite depuis `RelayProperties`) — câblé en `@Bean` malgré tout, comme
`ActiveStoreSelectionRegistry` dans `tenancy`.

## Ports (`domain/repositories/`)

**`RelayTsClient`**
- `invoke(request: RelayToolInvocationRequest): Either<UseCaseError, RelayToolOutcome>`

Implémentation : `spi/http/RelayHttpTsClient` (appel HTTP vers le process TS relayé, base URL
`relay.ts.base-url`).

## `exposed_interface`

**`RelayGateway`**
- `relayedTools(): List<RelayToolDescriptor>`
- `routeFor(toolName: String): ToolRoute?`
- `declaredToolNames(): Set<String>`
- `declaredNativeToolNames(): Set<String>`
- `invoke(toolName: String, toolInput: JsonElement, storeId: String, role: AccessRole): Either<UseCaseError, RelayToolOutcomeAcl>`

Consommé par `api` (`RoutedToolPipeline` : décide, pour chaque appel, s'il natif ou relayé) et par
`RelayManifestInvariantRunner`, `RelayEgressController` (`spi/web`).

## Événements

Aucun — `relay` ne publie ni ne consomme d'événement applicatif.

## Notes / specifics

**`RelayGatewayImpl` vivait dans `domain/` avec `@Service` — déplacement E5 prévu par la mesure
initiale de `BE-23` (un des deux cas nommés).** Déplacé vers `exposed_interface/`, à côté de son
interface, sans changement de contenu.

**`RelayDispatcher` portait `@Component` : câblé désormais depuis
`config/RelayDomainBeansConfiguration.kt`, qui reprend aussi le `@Bean relayManifest` qui vivait dans
`RelayConfiguration.kt` (module root, supprimé — il ne portait plus que `@ComponentScan`, interdit, et
ce seul `@Bean`).** `@EnableConfigurationProperties(RelayProperties::class)` migre avec lui.

**`RelayProperties.kt` reste au niveau racine du module** (pas de layer, simple porteuse de
configuration Spring Boot — hors `domain/`, non concernée par E1).
