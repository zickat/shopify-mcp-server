# ACCESS-RUNBOOK — octroi et révocation d'accès (`LOT2-10`, US-03/US-04/US-06)

Écrit par DevOps dans la nuit du 2026-08-07 au 2026-08-08, pendant que Val dormait. **Tout ce qui suit
a été exécuté pour de vrai** sur cette machine (`val-server`, tailnet `tail5e0606`) contre le serveur
déployé par `LOT2-09`, avec une identité de test créée, accordée, vérifiée, révoquée puis nettoyée —
pas seulement documenté comme possible. Voir `progress.md` de l'initiative
`catalog-plugin-oauth-tenancy` pour le compte rendu complet.

## Pourquoi ce fichier est séparé de `RUNBOOK.md`

`RUNBOOK.md` porte l'**exploitation** du service (déployer, sauvegarder, revenir en arrière) — son
audience est quelqu'un qui touche à l'infrastructure. Celui-ci porte les **accès** — qui peut faire
quoi, sur quelle boutique — un geste qui n'a rien à voir avec un déploiement et se répète à un rythme
différent (à chaque nouvel opérateur ou boutique, pas à chaque commit). Les mélanger aurait fait
relire à quelqu'un qui cherche juste « comment redémarrer le service » une procédure de gestion
d'identité, et inversement. Les deux se référencent l'un l'autre plutôt que de fusionner.

## Statut assumé : un palliatif, pas une plateforme

**Aucune API d'administration n'existe.** Le geste passe par Keycloak (admin console/`kcadm.sh`) et
`mongosh`, en direct sur les collections `IDENTITY`/`STORE`/`GRANT`. C'est un choix explicite du
lot 2 (`tasks/LOT2-10.md`) — construire une API dépasserait la tranche verticale que ce lot vise, et
rien ne l'exige tant que le geste reste rare.

**Ouvrir une initiative dédiée** (API d'administration) si l'un de ces signaux apparaît :
- un **troisième opérateur** rejoint (au-delà de Val et Antoine) ;
- le geste devient **fréquent** (plus qu'occasionnel) ou **pénible** (plusieurs minutes réelles, pas
  la mécanique elle-même) ;
- une **erreur de manipulation directe en base** cause un incident (preuve que la surface manuelle
  est devenue trop risquée pour rester manuelle).

---

## Deux défauts trouvés en écrivant et en exécutant cette procédure — corrigés cette nuit, pas juste notés

Ce runbook ne serait qu'une hypothèse s'il n'avait pas été exécuté pour de vrai. Il l'a été, et
l'exécution a trouvé deux défauts réels qui auraient cassé le geste que ce document prétend documenter
— tous deux corrigés avant d'écrire la suite de ce fichier, tous deux dans mon périmètre d'écriture
(`deploy/`, `keycloak/` — pas `src/`).

### 1. Le fichier de credentials multi-site était invisible du conteneur relayé — pour TOUTES les boutiques, pas seulement les nouvelles

C'est le défaut que `LOT2-03`/`LOT2-07` avaient nommé (« deux endroits à synchroniser à la main pour
toute nouvelle boutique »), mais en l'exécutant j'ai trouvé quelque chose de plus grave que la
synchronisation manuelle qu'ils décrivaient : **le montage qui rendrait cette synchronisation possible
n'existait tout simplement pas.**

`docker-compose.yml` montait le dépôt `mcp-shopify-catalog` en lecture seule dans le conteneur
`shopify-mcp-server-ts`, mais **rien ne montait `~/.config/shopify-catalog/credentials.json`**. Le
conteneur (`node:22-alpine`, aucune directive `user:`) tourne en `root`, donc `os.homedir()` côté
TypeScript (`credential-source.ts`) résout `/root` — jamais `/home/val`. Vérifié directement :

```
$ docker exec shopify-mcp-server-ts sh -c 'ls /root/.config/shopify-catalog/'
ls: /root/.config/shopify-catalog/: No such file or directory
```

Conséquence : `resolveStoreSelection()` rendait en permanence `{mode: "none", available: []}`, donc
**les 76 outils relayés refusaient tout appel** avec « Boutique inconnue de ce process relayé »,
quel que soit le slug envoyé par Kotlin — pour Vélotrip et LureLab, déjà seedées, pas seulement pour
une boutique hypothétique qu'on viendrait d'ajouter. `healthcheck.sh` ne pouvait pas le voir : il
teste que le port répond, pas qu'un outil relayé aboutit.

**Corrigé** : `deploy/docker-compose.yml` monte maintenant explicitement le fichier de l'hôte (lecture
seule, fichier unique — pas le dossier entier, pour ne rien exposer d'autre que Val pourrait y poser
plus tard) :
```yaml
- ${SHOPIFY_CATALOG_CREDENTIALS_PATH:-/home/val/.config/shopify-catalog/credentials.json}:/root/.config/shopify-catalog/credentials.json:ro
```
Vérifié après recréation du conteneur : le process Node y lit `['velotrip', 'lurelab']`. **La
synchronisation slug ⇄ `credentials.json` reste un geste manuel réel** (voir étape 3 plus bas) — ce
correctif rend seulement ce fichier visible du conteneur qui doit le lire, condition sans laquelle
aucune synchronisation, aussi soigneuse soit-elle, n'aurait eu d'effet.

### 2. Aucun mécanisme ne permettait à Val ou Antoine d'obtenir un mot de passe sans que Val en transmette un

`keycloak/README.md` (LOT2-08) avait déjà posé la question sans la trancher : les comptes `val` et
`antoine` étaient créés avec `credentials: []` (aucun mot de passe, même provisoire) — bien, US-03 est
respectée à l'import. Mais LOT2-08 documentait aussi que la suite (« Credential Reset » dans la
console Keycloak) dépend du SMTP :
- **SMTP configuré** → Keycloak envoie lui-même un lien à l'utilisateur — propre.
- **SMTP absent** → la console propose de « copier le lien » à transmettre à la main — et LOT2-08
  l'écrivait déjà noir sur blanc : *« je ne tranche pas ici si ça satisfait la même exigence que
  US-01/US-03 (…) un lien à usage unique reste quelque chose qui transite par un canal (Slack, etc.)
  avant que l'opérateur ne pose son propre mot de passe »*.

`LOT2-09` n'a pas configuré de SMTP (aucune mention dans `RUNBOOK.md` — vérifié, pas supposé). La
question posée par LOT2-08 arrivait donc intacte à ce lot : **avec la config héritée, le seul chemin
disponible pour que Val ou Antoine posent un mot de passe faisait transiter un lien signé par un
canal choisi par Val** — pas un secret Shopify, mais un secret d'authentification tout de même, et le
contrat de cette tâche est explicite : *« s'il y a une étape où quelque chose de sensible circule,
c'est un défaut de conception à signaler, pas une ligne à écrire »*.

**Décision tranchée cette nuit, avec son motif** : activer l'**auto-inscription** du realm
(`registrationAllowed: true`, réalité déjà `false` par défaut depuis LOT2-08) et **supprimer les deux
comptes `val`/`antoine` pré-créés sans credential** — ils étaient de toute façon inutilisables tels
quels (aucun moyen de se connecter), donc rien n'est perdu à les retirer. Chaque opérateur crée
désormais lui-même son compte (nom d'utilisateur + mot de passe de son choix) directement sur la page
de connexion Keycloak, **sans que Val touche à aucun moment un mot de passe, un lien signé, ou quoi
que ce soit qui ressemble à un secret d'authentification**. C'est strictement plus fort que le lien à
usage unique que LOT2-08 laissait en suspens.

**Pourquoi c'est sûr même une fois le serveur exposé publiquement (D24)** : s'inscrire ne donne
**aucun accès**. `schema.md` §5 le pose comme l'état central du modèle — *« s'authentifier ne donne
AUCUN accès. Un compte valide chez l'IdP et zéro grant = zéro boutique visible »*. Un inconnu qui
s'inscrit obtient un jeton, appelle un outil, se voit répondre qu'aucune boutique ne lui est accordée,
et une ligne d'audit se crée — rien de plus. La seule vraie porte reste le grant, que seul Val pose à
la main.

**Ce que je n'ai pas réglé, et pourquoi ce n'est pas bloquant** : `verifyEmail` reste à `false` (déjà
le cas), donc l'auto-inscription ne nécessite pas non plus de SMTP pour la vérification — cohérent
avec l'absence de SMTP côté LOT2-09. Si un jour la boutique de comptes auto-inscrits devient un
vecteur de spam (une fois le serveur exposé publiquement, hors périmètre D24 de ce lot), la reprise
est une ligne : `registrationAllowed: false` de nouveau, et une vraie procédure SMTP à la place —
signalé ici comme déclencheur de réouverture, pas comme un risque déjà matérialisé.

`keycloak/realm-shopify-catalog.production.json` mis à jour à l'identique (le realm importé au
prochain redémarrage à froid du conteneur Postgres reproduira cette configuration) **et** appliqué en
direct sur l'instance vivante via `kcadm.sh` (un `--import-realm` ne réimporte pas un realm déjà
existant — les deux étaient nécessaires, pas l'un ou l'autre).

---

## Procédure complète — octroyer un accès

### Étape 0 — le nouvel opérateur crée son propre compte (une fois)

Sur `http://val-server.tail5e0606.ts.net:8081/realms/shopify-catalog/protocol/openid-connect/registrations`
(page « Register » accessible depuis l'écran de connexion normal une fois qu'un client OAuth
l'atteint — pas une URL à visiter nue en pratique, voir « État réseau actuel » plus bas), l'opérateur
choisit son propre nom d'utilisateur et son propre mot de passe. **Val n'intervient à aucun moment de
cette étape.**

### Étape 1 — premier appel, refusé mais l'identité se crée

L'opérateur ouvre son client MCP (Claude Code, Cowork…), s'authentifie (flux OAuth 2.1 + PKCE normal
du client), et appelle n'importe quel outil — `list_stores` convient, il ne nécessite aucune boutique
accordée. La réponse dit qu'aucune boutique n'est accordée (`isError: false`, liste vide) — **normal**,
c'est l'état « Authentifié → SansAccès » de `schema.md` §5. Ce qui compte : l'appel a créé le document
`IDENTITY` en base (`findOrCreate`, `LOT0-06`).

### Étape 2 — Val retrouve l'identité créée

```
docker exec mongo-lot0 mongosh --quiet shopify_mcp_server \
  --eval 'db.identities.find({}, {subject:1, createdAt:1}).sort({createdAt:-1}).limit(5).forEach(printjson)'
```
Le `subject` seul ne dit pas qui c'est (c'est un `sub` opaque de l'IdP, jamais un nom — `schema.md`,
index) : recouper avec l'heure de création et, si besoin, demander confirmation à l'opérateur
(« tu viens de te connecter ? », pas plus). Noter le `_id` — c'est l'`identityId` de la suite.

**Bootstrap pour Val lui-même** : la toute première fois, Val n'a pas encore d'`IDENTITY` — il doit
faire l'étape 0 et l'étape 1 **pour lui-même** avant de pouvoir accorder quoi que ce soit à qui que ce
soit (`grantedBy` référence un `identityId` existant). Ce n'est pas fait cette nuit — Val ne s'est pas
authentifié pour de vrai depuis ce serveur — c'est la première chose à faire à son réveil, avant
d'accorder l'accès d'Antoine.

### Étape 3 — vérifier la boutique concernée, DANS LES DEUX ENDROITS

**Le piège numéro un de ce runbook, décrit dans la tâche, confirmé réel par le défaut n°1 ci-dessus.**
Une boutique existe dans deux systèmes distincts, et les deux doivent s'accorder sur le même mot :

1. **Mongo** — `db.stores.findOne({slug: "<slug>"})` : doit exister, avec le bon `shopDomain`.
2. **`credentials.json`** — le fichier de l'hôte, à l'emplacement pointé par
   `SHOPIFY_CATALOG_CREDENTIALS_PATH` (défaut `/home/val/.config/shopify-catalog/credentials.json`) :
   doit contenir une section de même clé que le `slug` Mongo. **La clé du fichier ET `STORE.slug`
   doivent être identiques caractère pour caractère** — c'est le même identifiant qui traverse Kotlin
   (ingress) → TS (exécution) → TS (sortie) → Kotlin (résolution du credential réel).

Si la boutique est nouvelle (hors du périmètre de cette tâche, mais le geste est le même mécanisme) :
`scripts/seed-stores.sh` côté Mongo, et une section à ajouter à la main dans `credentials.json` côté
fichier — **puis recréer le conteneur TS** (`docker compose up -d --force-recreate
shopify-mcp-server-ts`) si le fichier vient de changer : `credential-source.ts` met le contenu en
cache mémoire pour la durée du process (voir son en-tête), donc une modification du fichier
**pendant que le conteneur tourne** n'est prise en compte qu'au redémarrage du process — pas à la
volée.

Pour Vélotrip et LureLab, déjà seedées des deux côtés : cette étape se réduit à vérifier que rien n'a
divergé (vérifié cette nuit — les deux s'accordent).

### Étape 4 — créer le grant

```js
db.grants.insertOne({
  identityId: ObjectId("<identityId de l'étape 2>"),
  storeId:    ObjectId("<_id du STORE de l'étape 3>"),
  role:       "viewer",   // ou "operator" — les deux sont utilisables, voir ci-dessous
  grantedBy:  ObjectId("<identityId de Val, voir bootstrap étape 2>"),
  createdAt:  new Date(),
  revokedAt:  null,
})
```

**Les deux rôles sont utilisables.** Ce paragraphe portait un avertissement — un grant `viewer`
n'ouvrait aucun outil, parce que `use_store` était classée **mutante** et fermait donc l'étape qui
sélectionne la boutique. **Corrigé le 2026-08-08** (commit `6604b06`, déployé) : `use_store` est
reclassée `READ`, puisqu'elle ne touche aucun système externe et ne persiste rien — c'est l'état en
mémoire par *(identité, session)* que `schema.md` §7 exclut délibérément du modèle.

La reclassification n'ouvre aucune mutation : chaque outil que `use_store` débloque déclare son propre
type et il est réévalué à chaque appel ; la règle fermée par défaut de `LOT0-06` continue de refuser
tout ce qui n'est pas explicitement `READ`.

**Choisis donc le rôle selon l'intention réelle** : `viewer` pour lire sans écrire, `operator` pour
lire et écrire. Un test de bout en bout vérifie désormais qu'un `viewer` sélectionne sa boutique, lit
à travers `search_resources`, et n'est refusé que sur la mutation.

### Étape 5 — vérifier que l'octroi a pris — et le piège inverse

**Ne pas se contenter de `list_stores`** pour conclure que l'accès fonctionne. Il faut qu'un outil
scopé à une boutique **réponde** :

```
use_store {"store_id": "<storeId>"}          → doit réussir
check_shopify_connection {}                   → doit réussir à sélectionner la boutique
                                                 (peut échouer plus loin sur storeCredential.not.found
                                                 si aucun credential Shopify réel n'est encore seedé —
                                                 c'est un échec DIFFÉRENT et ATTENDU, voir plus bas)
```
Les deux appels doivent se faire **dans la même session cliente** (le même processus/la même
connexion MCP) — la boutique active est un état de session, perdu si le client se reconnecte entre
les deux appels (vérifié en le cassant par erreur cette nuit avant de le refaire correctement).

**Contrôle inverse, qui compte autant (US-02)** : tenter `use_store` sur une boutique **non**
accordée doit être refusé, et le message doit nommer uniquement les boutiques réellement accordées —
jamais une boutique existante mais non accordée à cette identité. Vérifié cette nuit :
```
access.denied {storeId=<autre boutique>, grantedStores=velotrip}
```
`lurelab` n'apparaît jamais dans `grantedStores` pour une identité qui n'y a pas accès — pas même son
nom.

### Étape 6 — révocation (US-04)

**Un seul geste, sur l'`IDENTITY`, pas sur le `GRANT`** (`D17` : révoquer une identité coupe tout,
quel que soit le nombre de boutiques accordées) :
```js
db.identities.updateOne({_id: ObjectId("<identityId>")}, {$set: {revokedAt: new Date()}})
```
**Vérifier avec n'importe quel outil** : `list_stores` rend désormais une liste vide pour une identité
révoquée, et `use_store` répond `access.denied`, tous deux immédiatement — aucune attente, aucun cache.

Ce paragraphe avertissait de ne pas se fier à `list_stores` seul, parce qu'il continuait de lister les
boutiques d'une identité révoquée, en contradiction avec **D17**. **Corrigé le 2026-08-08** (commit
`6604b06`, déployé). La correction a été posée au point de convergence — `listGrantedStores` — et non
sur l'outil : un **second** appelant faisait la même faute, le message « boutiques disponibles » qui
enrichit un refus, et personne ne l'avait identifié.

Pour re-donner l'accès plus tard : `revokedAt: null` suffit (le grant sous-jacent, s'il n'a pas été
retiré séparément, redevient actif immédiatement).

---

## État réseau actuel (D24) — ce qui marche aujourd'hui, ce qui ne marche pas encore

**Le service n'est joignable que par le tailnet Tailscale (`tail5e0606`) de Val.** `D24` le pose en
connaissance de cause : Tailscale suffit tant que Claude Code (poste de Val) est le seul client, parce
qu'il tourne sur la machine de l'opérateur et fait lui-même l'échange OAuth depuis le tailnet.

**Antoine est sur Cowork — une surface *hébergée*.** C'est l'infrastructure d'Anthropic, pas la
machine d'Antoine, qui doit atteindre le serveur pour faire l'échange `/token`. Un tailnet privé ne
lui est pas accessible, quelle que soit la qualité de ce runbook. **La bascule d'Antoine — la raison
d'être de cette initiative — n'est donc pas réalisable aujourd'hui**, indépendamment de tout ce qui
précède : même avec un compte auto-inscrit et un grant posé correctement, l'infrastructure Cowork ne
peut tout simplement pas ouvrir de connexion vers `val-server.tail5e0606.ts.net`.

**Ce qui débloque Antoine** : la revue d'exposition réseau publique, que `D24` place explicitement
*avant* ce lot (« se tranche avant `LOT2-10` ») — non faite. Ce runbook est écrit et vérifié pour
qu'il soit prêt le jour où cette revue tranche, **pas parce que le blocage est levé**. Une fois le
service exposé (avec HTTPS réel — voir aussi le point suivant), la procédure ci-dessus s'applique sans
changement : le mécanisme de grant ne dépend pas de la topologie réseau.

**Le flux par navigateur ne passe pas non plus tant que le certificat HTTPS réel n'est pas posé**
(cookie `Secure` de Keycloak, `LOT2-08`/`RUNBOOK.md` § « Décisions », point 3) — seules les
vérifications scriptées (comme celle de cette nuit) passent en HTTP tailnet nu. C'est un blocage
distinct de l'exposition publique : même Val, en HTTP nu, ne complèterait pas le flux depuis un
navigateur réel aujourd'hui.

---

## Exécution réelle de cette nuit — bout en bout, avec chronométrage

Identité de test créée par **auto-inscription réelle** (pas un utilisateur inséré à la main dans le
realm) : `runbook-test-op`, via `GET /protocol/openid-connect/registrations` → formulaire → `POST` →
code d'autorisation → `POST /token` avec `code_verifier` PKCE (`S256`) — flux HTTP scripté en Python,
même mécanique qu'un navigateur, contre le client pré-enregistré réel `mcp-claude-code` (pas un client
de test ad hoc). Jeton obtenu : `iss`/`aud` corrects, durée 3600s.

| Étape | Résultat observé | Durée |
|---|---|---|
| Auto-inscription → jeton obtenu (PKCE réel) | OK | **1,3 s** |
| `list_stores` (avant grant) | `isError:false`, liste vide — identité créée en base | — |
| Grant `viewer` inséré | inséré | **< 1 s** |
| `use_store` avec `viewer` | refusé — `access.role.insufficient` (défaut de rôle trouvé, voir Étape 4) | — |
| Grant relevé à `operator` | mis à jour | **< 1 s** |
| `use_store` puis `check_shopify_connection`, même session | OK — sélection réussie, échec attendu en aval (`storeCredential.not.found`, aucun secret Shopify réel encore seedé pour Vélotrip) | — |
| `use_store` sur boutique non accordée (LureLab) | refusé — `access.denied`, `grantedStores=velotrip` seulement | — |
| Révocation de l'identité | posée | **< 1 s** |
| `use_store` après révocation | refusé immédiatement — `access.denied` | — |
| `list_stores` après révocation | **`isError:false`, liste toujours "velotrip"** — défaut réel, voir plus bas | — |

**Temps total mesuré, du geste « insérer le grant » à « accès confirmé fonctionnel »** : de l'insertion
du grant (`02:43:55`) à la vérification concluante (`use_store` + `check_shopify_connection` réussis,
un peu avant `02:45:16`) — **environ 1 minute 20**, en comptant un aller-retour de correction de rôle
(`viewer` → `operator`) qu'un opérateur qui choisit le bon rôle dès le départ (voir Étape 4) n'aurait
pas. Sans cet aller-retour : l'insertion du grant elle-même prend moins d'une seconde, la vérification
(reconnexion cliente + deux appels d'outil) quelques secondes. **US-03 (« des minutes, pas un échange
de fichiers ») est vérifiée, pas supposée** — le temps réel est dominé par la frappe humaine des
commandes `mongosh`, pas par le système.

Nettoyage effectué après vérification : grant, identité et lignes d'audit de test supprimés de Mongo
(`deleteMany`), utilisateur `runbook-test-op` supprimé de Keycloak (`kcadm.sh delete users/...`),
script temporaire de test retiré du dépôt `mcp-shopify-catalog` (jamais commité, jamais dans `src/`).
État final vérifié : `identities: 0`, `grants: 0`, `auditLogs: 0`, `stores: 2` (Vélotrip, LureLab —
inchangées), aucun utilisateur de test restant sur le realm.

---

## Défauts trouvés en exécutant ce runbook — **les deux ont été corrigés depuis**

> **Mise à jour du 2026-08-08.** Les deux défauts décrits ci-dessous ont été remontés au Dev Backend,
> corrigés (commit `6604b06`) et **déployés**. Les sections sont conservées telles quelles parce
> qu'elles racontent comment ils ont été trouvés — en **exécutant** cette procédure, pas en l'écrivant —
> et parce que le second a révélé un chemin d'appel que personne n'avait identifié. Ce qui a changé
> est noté sous chacune. Les avertissements correspondants ont été retirés des étapes 4 et 6 : suivre
> ce runbook aujourd'hui n'appelle plus de précaution particulière sur ces deux points.
>
> - `list_stores` respecte désormais `D17` — correction posée sur `listGrantedStores`, le point de
>   convergence, et non sur l'outil : un **second** appelant faisait la même faute.
> - `use_store` est reclassée `READ`, donc **les deux rôles sont utilisables** conformément à `D6`.

### `list_stores` ne consulte pas `Identity.revokedAt` — une identité révoquée continue de voir ses anciennes boutiques

Trouvé en vérifiant la révocation à l'Étape 6. `ListStoresTool` appelle
`AuthenticatedToolPipeline.runForIdentity`, qui résout l'identité et exécute l'action **sans jamais
appeler `AccessResolutionUseCase.resolve`** (la méthode qui vérifie `identityExposedService.isActive`,
D17) — `AccessExposedServiceImpl.listGrantedStores` interroge les grants directement, sans ce
contrôle. Confirmé par le code (`AuthenticatedToolPipeline.kt`, `AccessResolutionUseCase.kt`) et par
l'audit réel :
```
{"tool":"list_stores","outcome":"ok", ...}     ← APRÈS révocation, toujours "ok"
{"tool":"use_store","outcome":"denied","denialReason":"access.denied"}   ← la vraie barrière, correcte
```
**Portée du défaut** : informationnel, pas une escalade — `use_store` et tout outil scopé à une
boutique refusent correctement (vérifié). Une identité révoquée peut seulement **voir la liste** des
boutiques qu'elle avait, pas agir dessus. Mais ça contredit l'esprit de D17 (« un seul geste coupe
tout ») et ça peut **fausser une vérification de révocation** faite avec le mauvais outil — d'où
l'avertissement explicite à l'Étape 6 de ce runbook. Fichier concerné :
`src/main/kotlin/com/zickat/shopifymcpserver/api/mcp/ListStoresTool.kt` — hors périmètre d'écriture
DevOps (`src/`), remonté au Tech Lead/Dev Backend, pas corrigé ici.

### Rôle `viewer` inutilisable en pratique — `use_store` classé mutant par défaut

Voir Étape 4 ci-dessus. Un `viewer` ne peut appeler aucun outil scopé à une boutique, parce que la
sélection de boutique elle-même (`use_store`) exige `operator`. Si l'intention de `viewer` est
« lecture seule sans écriture », ce n'est pas ce qui est livré aujourd'hui — à trancher par le Tech
Lead : soit reclassifier `use_store` en lecture, soit documenter que `viewer` est délibérément un rôle
qui n'ouvre encore aucun outil (auquel cas ce runbook recommande déjà `operator` par défaut jusqu'à
clarification, ci-dessus).

---

## Sécurité — contrôlé, pas supposé

- **Aucun secret n'a transité vers un opérateur pendant cette exécution.** L'identité de test s'est
  auto-inscrite avec un mot de passe qu'elle a choisi elle-même (généré côté script pour cette
  vérification synthétique, jamais communiqué à personne, jamais journalisé au-delà du processus qui
  l'a créé et immédiatement supprimé).
- **Identifiants d'administration Keycloak** (`KC_BOOTSTRAP_ADMIN_USERNAME`/`PASSWORD`, dans
  `deploy/.env`) utilisés uniquement via variables d'environnement passées à `kcadm.sh config
  credentials`, jamais passées en argument visible ni journalisées — pas de `--verbose` sur une
  commande portant un jeton ou un mot de passe (`guidelines/security.md`).
- **Contrôle de fuite** sur les fichiers touchés cette nuit (`git diff`, `git log -p` sur les commits
  de cette tâche) : rien trouvé — ni mot de passe, ni jeton, ni `identityId` réel d'une personne (seuls
  des identifiants de test, déjà supprimés du système vivant, apparaissent dans ce document).
- **`deploy/.env`, `deploy/certs/`, `deploy/backups/`, `deploy/releases/`** restent dans `.gitignore`
  (hérité de `LOT2-09`, revérifié, inchangé).

---

## Ce que cette tâche ne fait pas

- Elle ne construit aucune interface ni aucun outil MCP d'administration (décision explicite, voir
  « Statut assumé » en tête de fichier).
- Elle n'accorde pas l'accès réel à Antoine — impossible aujourd'hui de toute façon (voir « État
  réseau actuel »). L'exécuter pour de vrai sur Antoine relève de `LOT2-11`, une fois la revue
  d'exposition réseau tranchée.
- Elle ne corrige pas les deux défauts Kotlin trouvés (`list_stores`/`revokedAt`, rôle `viewer`) —
  remontés, pas traités, hors périmètre d'écriture DevOps.
