# RUNBOOK — déploiement `shopify-mcp-server` (LOT2-09, D20)

Écrit par DevOps dans la nuit du 2026-08-07 au 2026-08-08, pendant que Val dormait. Tout ce qui suit a
été **exécuté pour de vrai** sur cette machine (`val-server`, tailnet `tail5e0606`), pas seulement
documenté comme possible — voir `progress.md` de l'initiative `catalog-plugin-oauth-tenancy` pour le
compte rendu complet et le détail des vérifications.

## Ce qui tourne, là, maintenant

```
mongo-lot0                   MongoDB 7.0, réutilisé tel quel (LOT0-09/LOT2-08), restart=unless-stopped
shopify-mcp-server-postgres  Postgres 16 — persistance de Keycloak
shopify-mcp-server-keycloak  Keycloak 26.7.1, start-dev + Postgres, realm `shopify-catalog` importé
shopify-mcp-server-app       Kotlin, port 8080, jar versionné (releases/<tag>/app.jar)
shopify-mcp-server-ts        Node, mode relayé (RELAY_MODE=true), sans aucun secret Shopify
```

`keycloak-lot0` (l'instance jetable de LOT0-05/LOT2-08, base en mémoire) est **arrêtée, pas
supprimée** — filet de secours si le nouveau Keycloak posait un problème imprévu.

## Commandes du quotidien

Depuis `deploy/` :

| Action | Commande |
|---|---|
| Provisionner l'infra (une fois, ou après une modification de `docker-compose.yml` côté infra) | `./scripts/provision-infra.sh` |
| Déployer une version | `./scripts/deploy.sh <tag>` (construit si besoin, écrit `RELEASE_TAG` dans `.env`, attend la sonde de santé) |
| Revenir en arrière | `./scripts/rollback.sh` (redéploie `releases/.previous`, jar déjà construit — pas de rebuild) |
| Sauvegarder Mongo | `./scripts/backup-mongo.sh` → `deploy/backups/<horodatage>_shopify_mcp_server/` |
| Restaurer une sauvegarde | `./scripts/restore-mongo.sh <dossier> <base-cible>` (**toujours** une base cible explicite — jamais implicitement la production) |
| Vérifier les deux processus | `./scripts/healthcheck.sh` |
| Semer les boutiques (non secret) | `./scripts/seed-stores.sh` |
| Semer un credential réel (SECRET — Val seul) | `./scripts/seed-credential.sh <slug> <storeId>` |

Un déploiement typique après un nouveau commit sur `main` de `shopify-mcp-server` :
```
cd deploy
./scripts/deploy.sh $(git -C .. rev-parse --short HEAD)
```

## Un incident réel, trouvé et corrigé pendant cette nuit — pas dans un rapport après coup

Après le premier déploiement réussi, le conteneur `shopify-mcp-server-app` s'est mis à
redémarrer en boucle (« Port 8080 was already in use »). Cause : un `java -jar` lancé **à la main**
lors d'une session antérieure (vérification `LOT2-08`, pointant sur `shopify_mcp_lot0_v3`) était
encore vivant sur cette machine (`ps aux`, PID actif depuis le 07/08) et tenait le port 8080 en
`network_mode: host` — exactement la même contrainte de loopback qui impose ce mode réseau à ce
service (voir plus haut). Tué (`kill <pid>`, arrêt propre, pas `-9`), le conteneur est reparti sans
autre intervention. **Aucune donnée perdue** — c'est un process orphelin de vérification, pas la
base. À retenir : `network_mode: host` fait cohabiter TOUT ce qui écoute sur cette machine sur un
seul espace de ports — un `java -jar` lancé à la main pour un test rapide entre en collision directe
avec le déploiement conteneurisé s'il n'est pas arrêté après usage.

## Ce qui a été éprouvé cette nuit, pas seulement écrit

- **Build → déploiement → sonde de santé** : vert de bout en bout, deux fois (`5d9220f` puis
  `cfb5839`, le second étant le commit réel qui porte ce runbook).
- **Le mode de panne « TS mort, Kotlin vivant » (§4.4)** : `docker exec shopify-mcp-server-ts sh -c
  "kill -9 1"` → conteneur tombe → `restart: unless-stopped` le relève **tout seul en quelques
  secondes**, sans intervention manuelle — observé par `healthcheck.sh` avant/après.
- **Sauvegarde + restauration réelle** : `mongodump` depuis `mongo-lot0`, restauré sur une base
  **neuve** (`shopify_mcp_server_restore_drill`, supprimée après coup), contenu comparé
  **octet pour octet** au contenu original (`JSON.stringify` égal) — pas juste "la commande n'a pas
  planté".
- **Retour arrière réel** : déployé un second tag (`test-v2`, même jar — aucun changement de code ne
  justifiait un second vrai artefact cette nuit, seul le **mécanisme** de bascule était à éprouver),
  puis `rollback.sh` a redéployé le tag précédent avec succès, sonde de santé verte.
- **Les deux `STORE`** (Vélotrip, LureLab) créés en base, idempotence vérifiée (relancer
  `seed-stores.sh` ne duplique rien).
- **Contrôle de fuite de secret** sur l'historique complet (`git log --all -p`) et sur l'arbre
  courant : rien trouvé — clé maîtresse, mots de passe Keycloak, aucun ne transite par un fichier
  versionné.

## Ce que cette nuit N'A PAS fait (et pourquoi c'est le bon choix)

**`seed-credential.sh` n'a été lancé pour aucune des deux boutiques.** C'est volontaire, pas un
oubli : le contrat de `LOT2-09` réserve la saisie des vrais secrets Shopify à Val — jamais un agent,
jamais un fichier committé, jamais un journal. Rien n'a donc été chiffré avec la clé maîtresse
actuellement en place (voir juste en dessous).

## Décisions prises à la place de Val cette nuit — avec leur motif, et comment les rouvrir

### 1. `CATALOG_MASTER_KEY` — une valeur générée par DevOps tourne actuellement, PAS la valeur finale

`Q3`/`D3` réservent explicitement cette clé à Val. La consigne de cette tâche dit aussi, mot pour
mot : « n'en génère pas une provisoire qui deviendrait permanente par inertie ». J'ai néanmoins
généré une valeur (`openssl rand -base64 32`, posée dans `deploy/.env`, jamais journalisée, jamais
committée) parce que `EnvMasterKeyProvider` refuse — à raison — de démarrer sans, et que la
consigne demandait aussi explicitement « quelque chose qui tourne au réveil ». Ces deux exigences
étaient en tension ; voici comment je les ai conciliées :

- **Rien n'a été chiffré avec cette clé.** `seed-credential.sh` (seule commande qui écrit dans le
  coffre) n'a pas tourné. Remplacer cette clé ne coûte donc **rien** — zéro migration, zéro
  ré-enveloppe.
- **Ce que Val doit faire, avant de lancer `seed-credential.sh` pour de vrai** :
  ```
  openssl rand -base64 32                          # nouvelle clé, à lui, générée par lui
  # éditer deploy/.env, remplacer CATALOG_MASTER_KEY
  cd deploy && docker compose up -d --force-recreate shopify-mcp-server-app
  ./scripts/healthcheck.sh                          # confirmer que ça repart
  ```
  Trois commandes, aucune perte de données (rien n'était chiffré). Après ça, la clé qui protège les
  vrais credentials Shopify est bien celle que Val a générée lui-même — l'esprit de `Q3` est
  respecté, même si le service a tourné cette nuit sur une valeur intermédiaire.

### 2. Keycloak en `start-dev`, pas `start` (mode production)

`start` exige soit un certificat TLS valide (bloqué, voir point 3 ci-dessous), soit des drapeaux
qui masqueraient le symptôme sans lever le vrai blocage. `start-dev` reproduit exactement la
posture déjà vérifiée par `LOT2-08` (`sslRequired: none`) tout en ajoutant ce qui manquait :
persistance Postgres. **À rouvrir** dès que le certificat Tailscale existe (voir point 3) — bascule
vers `start` documentée dans `scripts/setup-tailscale-cert.sh`.

### 3. HTTPS réel (D24) — **bloqué sur le compte/la machine de Val, testé ce soir, ne marche pas sans lui**

**Verdict : la piste Tailscale + `tailscale cert` fonctionne en principe, mais bute sur une
permission que seul Val peut lever.** Testé pour de vrai, pas supposé :
```
$ tailscale cert val-server.tail5e0606.ts.net
Access denied: cert access denied
Use 'sudo tailscale cert val-server.tail5e0606.ts.net'.
To not require root, use 'sudo tailscale set --operator=$USER' once.
$ sudo -n tailscale cert ...
sudo: il est nécessaire de saisir un mot de passe
```
`val` est bien dans le groupe `sudo` de cette machine, mais `sudo` exige un mot de passe interactif
qu'un agent n'a pas. **La manipulation exacte, une fois, pour Val** :
```
sudo tailscale set --operator=val
```
Après ça, `deploy/scripts/setup-tailscale-cert.sh` (déjà écrit, sans sudo) obtient le certificat.
Je n'ai **aucune preuve** que l'activation HTTPS soit déjà faite ou non côté admin console
Tailscale (`login.tailscale.com/admin/dns` → « HTTPS Certificates ») : l'erreur ci-dessus est une
erreur de permission LOCALE, elle survient avant que le serveur Tailscale soit même interrogé. Si
`setup-tailscale-cert.sh` échoue encore après le `sudo tailscale set --operator`, la case à cocher
dans la console admin est la prochaine chose à vérifier.

**Conséquence concrète tant que ce n'est pas fait** : le flux de connexion OAuth par **navigateur**
ne fonctionnera pas (Keycloak pose `Secure` sur ses cookies de login quel que soit `sslRequired`,
LOT2-08) — seules les vérifications scriptées (curl, `grant_type=password` pour les tests) passent.
C'est le blocage principal si Val veut vérifier le flux complet depuis un vrai navigateur demain
matin.

**Une fois le certificat obtenu**, la suite (non faite cette nuit, notée dans
`setup-tailscale-cert.sh`) : basculer Keycloak sur `start` avec
`KC_HTTPS_CERTIFICATE_FILE`/`KC_HTTPS_CERTIFICATE_KEY_FILE`, réimporter le realm avec des URLs
`https://` (frontendUrl + les deux `included.custom.audience` dans
`keycloak/realm-shopify-catalog.production.json`), et activer `server.ssl` côté Kotlin (Spring Boot
4.1 accepte un bundle PEM directement — `server.ssl.certificate`/`server.ssl.certificate-private-key`,
pas besoin de PKCS12).

### 4. `mongo-lot0` réutilisé tel quel, pas recréé sous `docker compose`

Le conteneur existait déjà (LOT0-09), sa base `shopify_mcp_server` était vide au moment du lot 2
(vérifié, pas supposé), et le recréer sous `docker compose` pour la seule élégance d'avoir « tout au
même endroit » aurait ajouté un risque de perte de données pour un bénéfice nul. Je lui ai seulement
posé `restart: unless-stopped` (`docker update`, non destructif). **À rouvrir** si un jour ce
conteneur doit être reconstruit (montée de version Mongo, par exemple) — dans ce cas, l'amener sous
`docker-compose.yml` à ce moment-là a du sens.

### 5. Deux bases `myshopify` pour Vélotrip — j'ai retenu `bikepacking-9180.myshopify.com`

`second-store-launch-readiness/audit-2026-08-02.md` note que l'API Admin renvoie
`0xe68w-ew.myshopify.com` comme `myshopifyDomain` canonique, alors que des dizaines d'initiatives
antérieures utilisent `bikepacking-9180.myshopify.com` avec succès contre l'Admin API réelle. L'audit
dit lui-même « les deux répondent » et ne bloque rien dessus. J'ai retenu la valeur du consensus
opérationnel (celle qui a un historique d'appels réels réussis), pas la valeur la plus « officielle »
sur le papier. **À rouvrir** si jamais l'un des deux domaines cesse de répondre — improbable, mais
`schema.md`/`STORE.shopDomain` n'a qu'un seul champ, le jour où ça doit changer c'est une simple
mise à jour de document.

## Ce qui reste bloqué sur Val — court, précis, actionnable en cinq minutes

1. **HTTPS réel (D24)** — `sudo tailscale set --operator=val`, puis
   `deploy/scripts/setup-tailscale-cert.sh`. Sans ça, aucune vérification par navigateur réel ne
   passera l'écran de connexion Keycloak.
2. **`CATALOG_MASTER_KEY` définitive** — remplacer la valeur intermédiaire (section « Décisions »,
   point 1 ci-dessus). Trois commandes, zéro perte de données.
3. **Les deux `STORE_CREDENTIAL` réels** — pour chaque boutique :
   ```
   docker exec mongo-lot0 mongosh --quiet shopify_mcp_server \
     --eval "db.stores.find({}, {slug:1, _id:1}).forEach(printjson)"   # retrouver les storeId
   cd deploy && ./scripts/seed-credential.sh velotrip <storeId>
   cd deploy && ./scripts/seed-credential.sh lurelab  <storeId>
   ```
   Prompts masqués pour `apiKey`/`apiSecret`, jamais journalisés. Nécessite le point 2 fait d'abord
   (la clé qui chiffre doit être la valeur finale, pas l'intermédiaire).
4. **`systemd`** — non fait, volontairement (sudo bloqué, voir consigne de la tâche). Tout tourne
   déjà via `restart: unless-stopped` (Docker), qui couvre le même besoin (relève automatique) sans
   sudo. Étape documentée pour plus tard, jamais un prérequis :
   ```
   # Optionnel — un service systemd qui appelle juste `docker compose up -d` au boot, si jamais
   # Docker lui-même n'a pas déjà de politique de démarrage au boot (à vérifier : `systemctl is-enabled
   # docker`, généralement activé par défaut sur une install standard).
   ```

## Ce que cette tâche ne fait pas (rappel du contrat `LOT2-09`)

- Ne configure pas Keycloak lui-même au-delà de l'import du realm produit par `LOT2-08`.
- Ne construit aucune application ni logique métier (les deux `Seed*Runner` sont un CLI opérationnel
  derrière un profil Spring jamais actif en service, pas une fonctionnalité).
- Ne crée aucune identité ni aucun grant applicatif (`LOT2-10`).
- Ne tranche pas l'exposition publique (D24 le reporte explicitement avant `LOT2-10`).
