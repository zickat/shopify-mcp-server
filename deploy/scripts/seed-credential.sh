#!/usr/bin/env bash
# LOT2-09 — écrit le STORE_CREDENTIAL réel d'une boutique. RÉSERVÉ À VAL : jamais un agent, jamais un
# fichier committé, jamais un journal (contrat LOT2-09). Prompts interactifs masqués — voir
# com.zickat.shopifymcpserver.vault.SeedCredentialRunner pour le détail exact de ce qui est demandé.
#
# Usage : scripts/seed-credential.sh <velotrip|lurelab> <storeId>
#   <storeId> : l'id affiché par scripts/seed-stores.sh (une fois, la première fois qu'il tourne).
#   Si tu ne l'as pas sous la main : docker exec mongo-lot0 mongosh --quiet shopify_mcp_server \
#     --eval "db.stores.find({}, {slug:1, shopDomain:1}).forEach(printjson)"
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."   # -> deploy/

SLUG="${1:?Usage : seed-credential.sh <velotrip|lurelab> <storeId>}"
STORE_ID="${2:?Usage : seed-credential.sh <velotrip|lurelab> <storeId>}"

if [ ! -f .env ]; then
  echo "deploy/.env absent." >&2
  exit 1
fi
# shellcheck disable=SC2046
export $(grep -E '^(RELEASE_TAG|CATALOG_MASTER_KEY)=' .env | xargs -d '\n')
if [ -z "${CATALOG_MASTER_KEY:-}" ]; then
  echo "CATALOG_MASTER_KEY absente de deploy/.env — la générer d'abord (openssl rand -base64 32), voir RUNBOOK.md." >&2
  exit 1
fi
JAR="releases/$RELEASE_TAG/app.jar"
if [ ! -f "$JAR" ]; then
  echo "Introuvable : $JAR (RELEASE_TAG='$RELEASE_TAG' dans .env) — déployer d'abord (scripts/deploy.sh)." >&2
  exit 1
fi

java -jar "$JAR" --spring.profiles.active=seed --seed.command=credential --seed.slug="$SLUG" --seed.storeId="$STORE_ID"
