# Keycloak — realm `shopify-catalog` (LOT2-08)

Export de realm **reproductible et versionné**, pas une suite de clics dans un conteneur jetable.
Produit contre le Keycloak jetable de `LOT0-05`/D22 (`keycloak-lot0`, `start-dev`, base en mémoire),
à côté du realm `zickat` — jamais dedans. Ce fichier documente ce qui a été construit, comment il a
été vérifié, et ce qui reste à décider par `LOT2-09` (D20, montage du Keycloak durable).

## Ce que le fichier contient

`realm-shopify-catalog.json` — un realm avec :

- **Deux clients pré-enregistrés** (D22 : le pré-enregistrement est la voie recommandée par la spec
  MCP courante, pas un repli — voir `architecture.md` D22 et `progress.md`, entrée « Q1 »).
- **Val et Antoine**, comptes créés sans mot de passe (`credentials: []`,
  `requiredActions: ["UPDATE_PASSWORD"]`) — voir « Comptes opérateurs » plus bas.
- **Durée de jeton d'accès 3600s (D21)**, refresh token **avec rotation obligatoire**
  (`revokeRefreshToken: true`, `refreshTokenMaxReuse: 0`) — Anthropic l'exige explicitement pour les
  clients publics (voir « Sources »).
- **`registrationAllowed: false`** au niveau realm (auto-inscription des *utilisateurs* — n'a aucun
  rapport avec le DCR des *clients*, qui reste géré par la politique `Trusted Hosts` par défaut de
  Keycloak, vérifiée verrouillée ci-dessous).

## Les deux clients, et pourquoi deux et pas un

**Source normative** : `claude.com/docs/connectors/building/authentication`, consultée le 2026-08-07
(la page a bougé depuis la Q1 de la veille — `support.claude.com` redirige maintenant vers
`claude.com/docs/connectors/building`). Trois faits qui gouvernent tout ce fichier :

1. **Les surfaces hébergées (claude.ai web, Desktop, mobile, Cowork) partagent un unique callback** :
   `https://claude.ai/api/mcp/auth_callback` (et `https://claude.com/api/mcp/auth_callback`, à
   allowlister aussi — Anthropic prévient que la bascule est possible). **Un seul client suffit pour
   les quatre.**
2. **Claude Code est un client natif à part**, boucle OAuth locale (RFC 8252), jamais les
   identifiants Anthropic partagés. Redirect en loopback, port éphémère, chemin `/callback`.
3. **Claude inclut systématiquement un `code_challenge` PKCE `S256`**, quel que soit le mécanisme
   d'enregistrement. Les deux clients l'exigent (`pkce.code.challenge.method: S256`) — vérifié
   ci-dessous que ça mord (rejet sans PKCE, rejet PKCE incorrect).

Mélanger les deux redirect URIs sur un seul client aurait élargi la surface de confusion (un jeton
destiné au flux hébergé redirigeable vers une boucle locale, ou l'inverse) sans bénéfice : ce sont deux
`client_id` séparés, c'est ce que fait cette configuration.

### Client 1 — `mcp-claude-hosted`

Pour claude.ai, Claude Desktop, Claude mobile, Cowork. **Procédure côté opérateur** : dans Claude,
« Add connector » → URL du serveur MCP → « Advanced settings » → coller `mcp-claude-hosted` dans
*OAuth Client ID* (le champ *Client Secret* reste vide : client public, PKCE). C'est exactement le
premier niveau de la spec MCP — « client pré-enregistré si connu » — appliqué par saisie manuelle,
puisque le CIMD de Keycloak reste expérimental (Q1, D22) et que le DCR est délibérément non utilisé.

- `redirectUris` : `https://claude.ai/api/mcp/auth_callback`, `https://claude.com/api/mcp/auth_callback`
- Public, PKCE S256, pas de secret, `directAccessGrantsEnabled: false` (aucun besoin — Claude ne fait
  jamais de resource-owner-password ; le désactiver réduit la surface par rapport au client de test
  `shopify-mcp-client` de `zickat`, qui l'avait laissé ouvert pour le `curl` manuel de `LOT0-08`)

### Client 2 — `mcp-claude-code`

Pour la CLI. **Procédure côté opérateur** (source : `code.claude.com/docs/en/mcp`, section
« Use pre-configured OAuth credentials ») :

```
claude mcp add-json shopify-catalog \
  '{"type":"http","url":"https://<url-publique-du-serveur>/mcp","oauth":{"clientId":"mcp-claude-code","callbackPort":8080}}'
```

(`--callback-port` peut être n'importe quel port libre côté opérateur — le client Keycloak accepte
tout port par préfixe, voir ci-dessous. Pas de `--client-secret` : client public.)

- `redirectUris` : `http://localhost:*`, `http://127.0.0.1:*` — préfixe Keycloak (`*` en fin de
  chaîne), qui absorbe *n'importe quel port choisi par l'opérateur* suivi de `/callback`. Ce n'est
  **pas** la correspondance « port ignoré » du CIMD de Claude Code (`http://localhost/callback`
  sans port) : en pré-enregistrement classique, Claude Code doit se voir passer un port fixe
  (`--callback-port`), et c'est ce port-là, avec `/callback`, que le préfixe absorbe.
- Mêmes réglages que le client 1 sinon.

## Ce qui a été vérifié, et comment

Vérifié contre le conteneur `keycloak-lot0` existant (realm `shopify-catalog` importé à côté de
`zickat`, jamais dedans), avec un utilisateur de test créé et **supprimé après coup** (`verify-tmp`,
absent du fichier exporté).

1. **Handshake OAuth 2.1 + PKCE complet, réel** — `GET /auth` → formulaire de connexion → `POST`
   identifiants → code d'autorisation → `POST /token` avec `code_verifier`. Pas de jeton forgé.
   Script : voir la trace dans `progress.md`, entrée datée de cette tâche.
2. **PKCE mord** : `POST /token` sans `code_verifier` → `400 invalid_grant, "PKCE code verifier not
   specified"`. Avec un `code_verifier` faux → `400 invalid_grant, "PKCE verification failed: Code
   mismatch"`.
3. **`redirect_uri` mord** : `GET /auth` avec une URI non enregistrée sur l'un ou l'autre client →
   `400 "Invalid parameter: redirect_uri"` avant même d'afficher le formulaire de connexion.
4. **Audience correcte** : le jeton obtenu porte `aud: "https://shopify-mcp-server.example.com/mcp"`
   (valeur placeholder, à remplacer par l'URL publique réelle du serveur — voir `.env.example`).
5. **Durée de vie D21** : `exp - iat = 3600` exactement sur le jeton obtenu.
6. **Rotation du refresh token** (exigée par Anthropic pour les clients publics) : un premier refresh
   réussit et renvoie un *nouveau* refresh token ; réutiliser l'ancien après rotation échoue en
   `400 invalid_grant`.
7. **`Trusted Hosts` reste verrouillée** : `POST /clients-registrations/openid-connect` anonyme →
   `403` sur le realm neuf, sans action supplémentaire (comportement par défaut de Keycloak à la
   création d'un realm, retrouvé identique à ce que Q1/Fait 2 avait établi sur `zickat`).
8. **Val/Antoine ne peuvent pas se connecter** : `grant_type=password` sur `val` échoue (aucun
   credential n'existe — `disableableCredentialTypes: []` côté admin), `requiredActions:
   ["UPDATE_PASSWORD"]` confirmé présent après import.

**Ce que je n'ai pas pu vérifier** : le rejet en 401 par le *resource server* Spring d'un jeton à
mauvaise audience (item 5 de la tâche, « configuration Spring… basculer vers ce Keycloak réel »).
J'ai bien produit les deux jetons nécessaires (audience correcte / audience
`https://wrong-audience.example.invalid/mcp` via un client de test, supprimé ensuite) mais au moment
de lancer une instance séparée du serveur pour les leur soumettre, **le `target/` du dépôt était en
cours de réécriture par le build Maven de l'autre développeur** (`LOT2-02`, en cours) — aucun jar
disponible, et je n'ai pas le droit de lancer `mvn` moi-même pour en reconstruire un. Le mécanisme de
validation d'audience lui-même (`AudienceValidator`) est du code déjà testé par `LOT0-05` ; ce qui
restait à prouver ici — que **ce realm-ci** produit un `aud` conforme à `MCP_EXPECTED_AUDIENCE` — l'a
été (point 4 ci-dessus). Le test bout-en-bout complet (jeton de ce realm → vrai serveur → 401/200)
reste à faire, par le prochain qui a la main libre sur `mvn`.

## Découverte non prévue, structurante pour `LOT2-09` : le flux navigateur casse en HTTP pur

En construisant la vérification n°1 ci-dessus, le premier essai a échoué : `POST` du formulaire de
connexion → **page d'erreur Keycloak, « Restart login cookie not found »**. Cause trouvée : Keycloak
26.7.1 pose `Secure` sur ses cookies de session de login (`AUTH_SESSION_ID`, `KC_AUTH_SESSION_HASH`,
`KC_RESTART`) **indépendamment du réglage `sslRequired` du realm** — reproduit à l'identique sur
`shopify-catalog` (`sslRequired: none`) et sur `zickat` (même réglage). Le journal de conteneur porte
l'avertissement correspondant depuis le tout début (`org.keycloak.cookie.DefaultCookieProvider`,
« Non-secure context detected; cookies are not secured, and will not be available in cross-origin
POST requests »).

**Conséquence concrète : sur une exposition HTTP pur, un vrai navigateur ne pourra pas non plus
envoyer ces cookies au retour du formulaire de connexion — le flux `/authorize` → login → `/token`
échoue à cette étape, quel que soit le client.** Aucune vérification antérieure de cette initiative
n'avait exercé ce chemin : `LOT0-08`, Q1/Fait 2 et la vérification de bout en bout du lot 0 utilisaient
toutes `grant_type=password` (échange direct identifiants → jeton), qui ne pose jamais de cookie et ne
traverse jamais le formulaire de connexion. **C'est le tout premier test de cette initiative à exercer
le flux navigateur réel — celui que Cowork, Claude Desktop et claude.ai utilisent tous — et il révèle
que ce chemin est cassé sur la configuration actuelle.** J'ai contourné le problème *pour mon test* en
portant les cookies moi-même sans l'attribut `Secure` (ce qui ne prouve que la validité de la
configuration réalm/clients, pas que l'exposition HTTP fonctionnera pour un vrai navigateur — un
navigateur, lui, n'a pas ce contournement).

Je n'ai pas trouvé, dans le temps de cette tâche, de drapeau serveur exposé par
`start-dev --help-all` pour désactiver ce comportement (les pistes glanées en recherche évoquent une
variable d'environnement `KC_HTTP_COOKIE_SECURE`, non confirmée sur cette version précise faute de
temps pour la tester isolément). **Je ne tranche pas cette question — elle revient à `LOT2-09` avec
D20** : soit HTTPS réel devient nécessaire dès le jour 1 pour le Keycloak de production (pas seulement
« souhaitable », mais **fonctionnellement requis pour que le flux de connexion marche dans un
navigateur**), soit il existe un réglage serveur pour lever cet attribut que je n'ai pas eu le temps
d'isoler et qu'il faudra chercher avant de conclure que HTTP suffit.

## La question HTTPS, posée et pas tranchée

Le conteneur actuel a `sslRequired: none` parce que l'accès passe par Tailscale (chiffré en
WireGuard, indépendant du HTTP/HTTPS vu du navigateur). Ce choix ne se transpose pas tel quel à une
mise en service, et la découverte ci-dessus le rend plus pressant qu'un simple choix de posture :

- **Si le serveur Keycloak doit rester joignable uniquement via Tailscale** (Val et Antoine sur le
  même réseau Tailscale), HTTP pourrait suffire pour *eux* — mais alors le comportement des cookies `Secure`
  documenté ci-dessus doit être résolu autrement (drapeau serveur à trouver, ou accepter que le
  formulaire de connexion ne marche pas et se rabattre sur un mécanisme sans navigateur — aucun n'est
  disponible pour Cowork/Claude Desktop/claude.ai, qui n'ont pas d'option ROPC).
- **Si les surfaces hébergées d'Anthropic (claude.ai, Desktop, mobile, Cowork) doivent pouvoir
  authentifier un opérateur**, il y a un fait plus large que HTTPS à trancher : d'après
  `claude.com/docs/connectors/building/authentication` (§ « Cross-host authorization servers »),
  **l'échange du code d'autorisation contre un jeton se fait depuis l'infrastructure d'Anthropic**
  (plage d'IP publiée `160.79.104.0/21`), pas depuis le navigateur de l'opérateur. **Le endpoint
  `/token` de Keycloak doit donc être joignable depuis l'internet public**, pas seulement depuis le
  Tailscale de Val — exactement le même fait que `architecture.md` §0.2 avait déjà établi pour le
  *resource server* MCP lui-même, mais jamais formulé pour l'*authorization server*. Cela ne concerne
  pas Claude Code (qui fait sa boucle OAuth localement, sur la machine de l'opérateur — Tailscale
  suffirait pour lui) mais concerne les trois autres surfaces.

Je pose ces deux faits, je ne tranche ni l'un ni l'autre : c'est une question pour `LOT2-09` et Val,
comme demandé.

## Comptes opérateurs — Val et Antoine

Créés avec `credentials: []` (aucun mot de passe défini, même temporaire) et `requiredActions:
["UPDATE_PASSWORD"]`. Confirmé après import : aucun type de credential disponible, connexion par mot
de passe impossible tant qu'aucun n'est défini. **Aucun secret ne leur est donc transmis par ce
fichier ni par la personne qui l'importe** (US-01/US-03).

**Procédure pour que chacun choisisse sa propre méthode**, sur le Keycloak durable de `LOT2-09` :
console admin → Users → `val` (ou `antoine`) → **Credential Reset**. Deux issues, selon que `LOT2-09`
configure ou non le SMTP du realm :

- **SMTP configuré** : Keycloak envoie lui-même le lien à l'adresse email de l'utilisateur — c'est la
  voie la plus propre, à privilégier.
- **SMTP absent** (le conteneur de vérification n'en a pas) : la console Keycloak propose de
  **copier le lien** au lieu de l'envoyer par email — un lien signé, à usage unique, à durée limitée.
  Ce n'est pas un mot de passe partagé, mais **je ne tranche pas ici si ça satisfait la même exigence
  que US-01/US-03** ("aucun secret ne leur est transmis") — un lien à usage unique reste quelque chose
  qui transite par un canal (Slack, etc.) avant que l'opérateur ne pose son propre mot de passe. À
  valider par Val/CEO avant que `LOT2-09` l'utilise en pratique ; SMTP réel lève la question.

## Ce qui reste à trancher ou à faire pour `LOT2-09` (D20)

1. **URL publique du Keycloak de production**, à figer et à poser en `frontendUrl` du realm — sans
   ça, l'`issuer` du jeton dépend de l'URL utilisée pour joindre le serveur (piège déjà rencontré sur
   `zickat`, voir `progress.md`, entrée D22 du Tech Lead). **Non inclus dans cet export** : je ne
   connais pas cette URL, et un placeholder faux serait pire qu'une absence.
2. **HTTPS** — posé ci-dessus, pas tranché.
3. **Persistance** — ce conteneur est en mémoire (`start-dev`, H2). Le realm de production doit
   survivre à un redémarrage : base externe (Postgres, cohérent avec le reste de la stack qui est déjà
   sur MongoDB pour l'app mais Keycloak ne supporte pas Mongo — Postgres est le choix standard
   documenté par Keycloak) + volume de sauvegarde.
4. **Sauvegarde** — le realm devient, avec `vault`, l'un des deux systèmes dont la perte coupe
   l'accès de tout le monde (D20 le nomme déjà pour l'hôte entier). Une procédure d'export/sauvegarde
   régulière du realm de production est à écrire, séparée de ce fichier (qui est un *template*
   versionné, pas un *backup* d'état vivant).
5. **`MCP_EXPECTED_AUDIENCE` réel** — remplacer le placeholder `https://shopify-mcp-server.example.com/mcp`
   dans les deux `protocolMappers` **et** dans `.env.example`/l'environnement du serveur, par la
   même valeur, exactement (piège déjà rencontré, voir tâche).
6. **Résoudre la question `Secure` cookie / HTTP** avant de décider que HTTP suffit pour Val/Antoine
   via Tailscale — sinon le formulaire de connexion ne marchera pour personne, Tailscale ou pas.

## Comment importer

```
TOKEN=$(curl -s -X POST "http://<keycloak>/realms/master/protocol/openid-connect/token" \
  -d "client_id=admin-cli" -d "username=admin" -d "password=<admin>" -d "grant_type=password" \
  | jq -r .access_token)

curl -X POST "http://<keycloak>/admin/realms" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  --data-binary @realm-shopify-catalog.json
```

**Piège rencontré et corrigé dans ce fichier** : Keycloak rejette l'import avec un message
**totalement opaque** — `{"errorMessage":"Database operation failed"}`, aucun détail — si le champ
`description` d'un client dépasse 255 caractères (colonne `CLIENT.DESCRIPTION`, `VARCHAR(255)`).
Aucune indication de taille dans l'erreur ; trouvé par bissection (réduire le JSON jusqu'à isoler le
client, puis le champ). À savoir si ce fichier est étendu plus tard : **toute description de client
doit rester sous 255 caractères**, le reste va dans ce README.

## Sources

- `claude.com/docs/connectors/building` et `.../authentication` (consultées 2026-08-07) — callback
  URLs, PKCE obligatoire, DCR/CIMD/pré-enregistrement, rotation du refresh token, plage d'IP sortante
  Anthropic.
- `code.claude.com/docs/en/mcp` (consultée 2026-08-07) — `claude mcp add-json`, `--client-id`,
  `--callback-port`, credentials pré-configurées côté Claude Code.
- `modelcontextprotocol.io/specification/2026-07-28/basic/authorization` — déjà sourcée par Q1
  (`progress.md`), pas re-consultée ici.
