# `collections`

## Context

`collections` porte le jugement de cohérence d'un regroupement de produits — la seule partie **sans
réseau** de l'ancien `collections.ts` (huit outils au total côté agent). Elle a été portée en premier,
exprès, par `LOT5-03` (`5538a41`, `catalog-plugin-oauth-tenancy`) précisément parce qu'elle était la
seule portable sans cassette. Le message du commit le dit lui-même : « the other 7 tools are all
mutations left for propose_collection (LOT5-12) ».

**Ce module est délibérément incomplet — pas du code mort.** Les 7 outils restants
(`propose_collection`, `publish_collection`, `unpublish_collection`, `update_collection_products`,
`update_collection_content`, `delete_collection`, `set_collection_hero_image`) sont spécifiés
outil par outil dans `LOT5-12` (`catalog-plugin-oauth-tenancy/tasks/LOT5-12.md`), un lot **suspendu par
ce refactor-ci** — sa seule dépendance technique, `LOT5-11`, est déjà levée. **`LOT5-12` sera repris** :
décision de portefeuille de Val, prise le 2026-08-14. **13 cassettes** les attendent déjà, enregistrées
sous un feu vert de mutation explicite de Val daté du 2026-08-13, sur la boutique de production
Velotrip, avec des résidus permanents assumés (`progress-archive-captures-mutation…` de
`catalog-plugin-oauth-tenancy`). Les refaire ne coûte pas du temps machine : ça coûte de rouvrir une
fenêtre de mutation en production. Ce module réaligné maintenant, `LOT5-12` créera son `api/mcp/` et son
`spi/shopify/` directement à la bonne place au lieu de les créer à l'ancienne puis de les redescendre.

## Use cases

| Use case | Signature |
|---|---|
| `CollectionCoherence.judgeCoherence` | `(products: List<ProductForSegmentation>): CollectionCoherenceVerdict` |

Ce n'est pas un `[Domain]UseCase` au sens de la convention (pas de suffixe `UseCase`, pas de
`Either<UseCaseError, T>`, pas câblé en `@Bean`) : c'est un jugement pur, sans effet de bord et sans
notion d'erreur métier — même famille que `products.domain.SizeConversion`, citée comme précédent par
`LOT5-03` lui-même. Rien à câbler : aucun port à injecter, donc aucune entrée dans un
`DomainBeansConfiguration`.

**Appelé uniquement par son test (`CollectionCoherenceTest`) à ce stade — c'est normal et attendu.**
`propose_collection` (`LOT5-12`) sera le premier appelant applicatif ; en attendant, la fonction n'a
personne d'autre à qui parler, elle n'est pas orpheline pour autant.

## Ports (`domain/repositories/`)

Aucun. `judgeCoherence` ne touche à aucune ressource externe — c'est justement pourquoi c'est la seule
partie de l'ancien `collections.ts` portée avant que le reste (7 outils, tous des mutations réseau) ne
le soit par `LOT5-12`.

## Outils MCP exposés (`api/mcp/`)

Aucun. Les 7 outils qui exposeraient ce module à l'agent IA (`propose_collection`,
`publish_collection`, `unpublish_collection`, `update_collection_products`,
`update_collection_content`, `delete_collection`, `set_collection_hero_image`) sont spécifiés dans
`LOT5-12`, pas encore portés.

## `exposed_interface`

Aucune — pas de consommateur hors module, et pas même de module à consommer : `judgeCoherence` n'est
appelée que par son propre test.

## Événements

Aucun — `collections` ne publie ni ne consomme d'événement applicatif.

## Notes / specifics

**Pourquoi ce module ne doit pas être « nettoyé ».** Un lecteur qui ne connaît pas l'archéologie git
verrait quatre fichiers (`CollectionCoherence`, `CollectionCoherenceVerdict`, `ProductForSegmentation`,
leur test) sans appelant applicatif et pourrait les prendre pour du code mort. Ce ne l'est pas : c'est
la fondation posée d'avance d'un lot spécifié et suspendu pour une raison — ce refactor — qui aura
disparu une fois qu'il sera terminé. Supprimer ces fichiers détruirait la seule partie déjà portée de
`LOT5-12` et rendrait orphelines les 13 cassettes qui attendent les 7 autres outils, sans qu'aucun
avantage ne le justifie : les garder ne coûte presque rien (ce fichier, une ligne de cliquet), les
supprimer coûte de rouvrir une fenêtre de mutation en production.

**Ce module ne viole que R11 avant ce lot.** `domain/` n'a aucun import hors de son propre paquet — le
commit `5538a41` le dit lui-même (« on nothing at all yet ») — donc R1 et R2 étaient déjà satisfaites.
Sans `exposed_interface/`, R6b et R8 sont sans objet ; sans `*Result` exposé, R10 est sans objet ; sans
`@McpTool`, R9 est sans objet. Seul l'absence de ce fichier violait R11.

**`CollectionsConfiguration.kt` a été supprimé, pas déplacé vers `config/`.** Les modules déjà
réalignés (`pages`, `seo`, `redirects`, `menus`, `metaobjects`, `catalog_status`) ne portent un
`config/[Module]DomainBeansConfiguration.kt` que parce qu'ils ont au moins un port à câbler en `@Bean` —
aucun d'eux n'a de fichier de configuration vide. `collections` n'a aucun port, donc rien à câbler :
créer un fichier de configuration à zéro `@Bean` aurait été le seul de tout le dépôt dans ce cas, une
incohérence plutôt qu'un alignement. L'ancien `@ComponentScan` du fichier supprimé ne scannait de toute
façon aucun composant (aucune classe `@Component`/`@Service`/`@Repository` dans le module, vérifié) :
un vestige du scaffold `LOT0-02`/`E1`/`D46`, pas un besoin réel. `LOT5-12` créera
`config/CollectionsDomainBeansConfiguration.kt` quand il posera un premier port à câbler.
