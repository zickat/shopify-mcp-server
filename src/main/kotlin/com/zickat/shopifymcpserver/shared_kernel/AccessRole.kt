package com.zickat.shopifymcpserver.shared_kernel

/**
 * Le rôle qu'un grant actif confère à une identité sur une boutique — schema.md §6. Deux rôles
 * seulement : `VIEWER` (lecture seule) et `OPERATOR` (lecture + mutation).
 *
 * Vit ici, dans `shared_kernel`, pas dans `tenancy` : [UserContext] le porte, et tout module futur
 * qui expose un [ToolUseCase] (catalogue, lot 2…) doit pouvoir lire le rôle courant sans dépendre
 * du module `tenancy` — la dépendance ne va que dans un sens (`tenancy` dépend de
 * `shared_kernel`), jamais l'inverse, sous peine de cycle Modulith. Le type "fil" propre au
 * schéma Mongo du grant (`GrantRole`, `tenancy.domain.models`, valeurs `viewer`/`operator`) se
 * convertit vers celui-ci à la frontière (`GrantRole.toAccessRole()`) ; il ne le remplace pas.
 * `GrantRole` reste la source de vérité du validateur Mongo, `AccessRole` est ce que les autres
 * modules sont autorisés à voir.
 */
enum class AccessRole { VIEWER, OPERATOR }
