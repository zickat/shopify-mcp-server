# `shared_kernel`

## Context

`shared_kernel` porte ce que tout module a le droit de tenir pour acquis : la hiérarchie d'erreurs
(`UseCaseError`), le mapping HTTP (`GlobalExceptionHandler`), les contextes de requête
(`TenantContext`, `UserContext`), le contrôle d'accès par rôle (`ToolAccessControl`), les types GID
Shopify (`GidTypes`), les migrations Mongo (`ChangeUnit`/`MigrationRunner`), et deux ou trois utilitaires
(`StripHtml`, `ObjectIds`, `PaginationLimits`). Module socle : ni port catalogue ni outil MCP, seul le
`module.md` (R11) était dû — sa nature même (franchissable en entier) l'exempte d'E1/E5.

## `exposed_interface`

**Aucune — et c'est la règle, pas une lacune (D54, exception nommée et permanente de R6).**
`shared_kernel` est franchissable en entier par tous les modules : toute classe publique y est déjà une
frontière, sans avoir besoin d'un dossier `exposed_interface/` séparé. Ne pas lui en inventer un.

## Notes / specifics

**`newObjectIdHex(): String` ajoutée à `ObjectIds.kt` par `BE-23`.** Trois modules socle
(`vault`, `identity`, `audit`) génèrent un identifiant local en `domain/` (pas d'ID Shopify externe à
réutiliser) et devaient encore produire un hexadécimal 24 caractères compatible `ObjectId` côté
persistance, sans faire dépendre leur `domain/` du type `org.bson.types.ObjectId` (interdit par R1).
`newObjectIdHex()` enveloppe `ObjectId().toHexString()` : le module appelant ne dépend, en bytecode, que
de `shared_kernel` — franchissable en entier, donc sans violation R1 même si l'implémentation, à
l'intérieur, touche bien à Mongo.

**Pas de `domain/` dans ce module — R1 ne s'y applique structurellement pas** (aucun de ses fichiers
n'est sous un sous-paquet nommé `domain`). C'est ce qui rend l'exception D54 cohérente : un module qui
n'a pas de frontière interne/externe à faire respecter n'a pas besoin qu'ArchUnit la vérifie.
