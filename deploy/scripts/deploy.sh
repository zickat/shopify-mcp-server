#!/usr/bin/env bash
# LOT2-09 — déploie une version applicative (jar Kotlin déjà construit par build-release.sh) et
# (re)lance le processus TS relayé sur l'état courant du dépôt B. Écrit RELEASE_TAG dans deploy/.env
# (seule façon supportée de le changer — jamais à la main, voir docker-compose.yml) et garde la trace
# du tag précédent pour rollback.sh.
#
# Usage : scripts/deploy.sh <tag>
#   (scripts/build-release.sh <tag> d'abord si releases/<tag>/app.jar n'existe pas encore.)
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."   # -> deploy/

TAG="${1:?Usage : deploy.sh <tag>}"

if [ ! -f "releases/$TAG/app.jar" ]; then
  echo "releases/$TAG/app.jar absent — construction d'abord."
  ./scripts/build-release.sh "$TAG"
fi

if [ ! -f .env ]; then
  echo "deploy/.env absent — voir .env.example." >&2
  exit 1
fi

# Trace du tag précédent, pour rollback.sh — jamais écrasée par un déploiement qui échoue avant la fin.
CURRENT_TAG="$(grep -E '^RELEASE_TAG=' .env | cut -d= -f2- || true)"
if [ -n "$CURRENT_TAG" ] && [ "$CURRENT_TAG" != "$TAG" ]; then
  echo "$CURRENT_TAG" > releases/.previous
  echo "Tag précédent ($CURRENT_TAG) noté dans releases/.previous pour un rollback éventuel."
fi

# Remplace la ligne RELEASE_TAG=... dans .env (une seule ligne, jamais dupliquée).
if grep -qE '^RELEASE_TAG=' .env; then
  sed -i "s/^RELEASE_TAG=.*/RELEASE_TAG=$TAG/" .env
else
  echo "RELEASE_TAG=$TAG" >> .env
fi

echo "== Déploiement de $TAG =="
docker compose up -d --force-recreate shopify-mcp-server-app shopify-mcp-server-ts

echo "== Attente de santé (jusqu'à 90s) =="
for i in $(seq 1 30); do
  if ./scripts/healthcheck.sh --quiet; then
    echo "OK — $TAG en service."
    exit 0
  fi
  sleep 3
done
echo "Le déploiement de $TAG ne passe pas la sonde de santé après 90s — voir 'docker compose logs'." >&2
exit 1
