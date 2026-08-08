#!/usr/bin/env bash
# LOT2-09 — sonde de santé sur les DEUX processus (architecture.md §4.4 : « sinon la panne se
# présente comme "tout est cassé sans raison" »). Volontairement une sonde EXTERNE, pas un
# HealthIndicator Spring supplémentaire : aucune des deux sondes ne demande de toucher au code de
# `shopify-mcp-server` (périmètre DevOps), et distinguer les deux processus est justement ce qui
# manque si on ne regarde que /actuator/health (qui ne voit que le processus Kotlin).
#
# Usage : scripts/healthcheck.sh [--quiet]
#   Sans --quiet : affiche l'état de chaque processus. Avec --quiet : silencieux, juste le code de
#   sortie (utilisé par deploy.sh en boucle d'attente).
# Code de sortie : 0 si les deux processus répondent, 1 sinon.
set -uo pipefail
QUIET=0
[ "${1:-}" = "--quiet" ] && QUIET=1

log() { [ "$QUIET" = "0" ] && echo "$@"; }

FAILED=0

# --- Kotlin : /actuator/health (couvre aussi l'indicateur Mongo, LOT0-02/LOT0-03) ---
KOTLIN_BODY="$(curl -sf --max-time 3 http://127.0.0.1:8080/actuator/health 2>/dev/null)"
if [ -n "$KOTLIN_BODY" ] && echo "$KOTLIN_BODY" | grep -q '"status":"UP"'; then
  log "Kotlin (port 8080)      : UP"
else
  log "Kotlin (port 8080)      : DOWN — pas de réponse UP sur /actuator/health"
  FAILED=1
fi

# --- TS relayé : pas d'endpoint GET (relay-server.ts ne répond qu'à POST /relay/invoke), donc une
# requête invalide qui obtient TOUT DE MÊME une réponse HTTP (même 400/404) prouve que le processus
# écoute — une absence de connexion (curl code 7/28) prouve qu'il ne répond pas. Distinction
# volontaire : on ne teste pas "l'outil marche", seulement "le processus est vivant sur son port".
TS_HTTP_CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 -X POST http://127.0.0.1:8765/relay/invoke -H 'Content-Type: application/json' -d '{}' 2>/dev/null)"
if [ -n "$TS_HTTP_CODE" ] && [ "$TS_HTTP_CODE" != "000" ]; then
  log "TS relayé (port 8765)   : UP (HTTP $TS_HTTP_CODE sur une requête invalide, attendu — signe que le processus écoute)"
else
  log "TS relayé (port 8765)   : DOWN — aucune réponse HTTP (processus arrêté ou port fermé)"
  FAILED=1
fi

if [ "$FAILED" = "1" ]; then
  log ""
  log "Mode de panne « TS mort, Kotlin vivant » (§4.4) : auth OK, outils NATIFS (list_stores, use_store,"
  log "create_redirect, search_resources) répondent, les 76 outils RELAIS échouent un par un — ce n'est"
  log "jamais un incident commercial (Shopify sert les boutiques indépendamment de ce serveur)."
fi

exit $FAILED
