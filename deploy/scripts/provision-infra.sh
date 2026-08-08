#!/usr/bin/env bash
# LOT2-09 — provisionne l'infrastructure (Postgres + Keycloak durable), et ajuste MongoDB sans le
# recréer. Idempotent : relançable sans effet destructeur.
#
# Ce script NE monte PAS l'application (voir deploy.sh) — il prépare ce dont elle a besoin.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."   # -> deploy/

if [ ! -f .env ]; then
  echo "deploy/.env absent — copier .env.example et remplir KC_BOOTSTRAP_ADMIN_PASSWORD/KC_DB_PASSWORD (openssl rand -base64 24 chacune) avant de continuer." >&2
  exit 1
fi

echo "== MongoDB (mongo-lot0) — politique de redémarrage, sans toucher aux données =="
if docker inspect mongo-lot0 >/dev/null 2>&1; then
  docker update --restart unless-stopped mongo-lot0 >/dev/null
  echo "  mongo-lot0 : restart=unless-stopped posé."
else
  echo "  mongo-lot0 introuvable — ce script suppose qu'il existe déjà (LOT0-09/LOT2-08)." >&2
  echo "  Si ce n'est plus le cas, lancer : docker run -d --name mongo-lot0 --restart unless-stopped -p 27017:27017 -v mongo-lot0-data:/data/db mongo:7.0" >&2
  exit 1
fi

echo "== Keycloak jetable (keycloak-lot0) — arrêt, PAS de suppression =="
if docker inspect keycloak-lot0 >/dev/null 2>&1; then
  if [ "$(docker inspect -f '{{.State.Running}}' keycloak-lot0)" = "true" ]; then
    docker stop keycloak-lot0 >/dev/null
    echo "  keycloak-lot0 arrêté (conservé, non supprimé — filet de secours, base en mémoire de toute façon perdue à l'arrêt)."
  else
    echo "  keycloak-lot0 déjà arrêté."
  fi
else
  echo "  keycloak-lot0 introuvable — rien à arrêter."
fi

echo "== Postgres + Keycloak durable (docker compose) =="
docker compose up -d postgres keycloak

echo "== Attente que Keycloak réponde =="
for i in $(seq 1 30); do
  if curl -sf http://localhost:8081/realms/shopify-catalog/.well-known/openid-configuration >/dev/null 2>&1; then
    echo "  Keycloak prêt, realm 'shopify-catalog' importé et joignable."
    exit 0
  fi
  sleep 2
done
echo "  Keycloak ne répond toujours pas après 60s — voir 'docker compose logs keycloak'." >&2
exit 1
