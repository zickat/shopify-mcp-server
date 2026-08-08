#!/usr/bin/env bash
# LOT2-09 — sauvegarde MongoDB (identités, boutiques, grants, coffre CHIFFRÉ, audit — D20 : « le même
# hôte porte le service, la base, la clé maîtresse et le journal d'audit »). `mongodump` depuis le
# conteneur `mongo-lot0` lui-même (pas besoin d'installer les outils Mongo sur l'hôte), archive
# horodatée sous deploy/backups/, jamais committée (voir .gitignore).
#
# Le coffre est CHIFFRÉ EN BASE (AES-GCM, enveloppe — schema.md §4) : cette sauvegarde contient donc
# du ciphertext, jamais un secret Shopify en clair. Mais restaurer ne sert à rien sans la clé maîtresse
# (CATALOG_MASTER_KEY) : elle N'EST PAS dans ce dump (elle ne vit jamais en base) et doit être
# sauvegardée SÉPARÉMENT par Val (voir RUNBOOK.md — hors de ce qu'un agent peut faire).
#
# Usage : scripts/backup-mongo.sh [nom-de-base]
#   nom-de-base par défaut : shopify_mcp_server (la base de production).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."   # -> deploy/

DB="${1:-shopify_mcp_server}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_DIR="backups/${STAMP}_${DB}"
mkdir -p "$OUT_DIR"

echo "== mongodump ($DB) depuis mongo-lot0 -> $OUT_DIR =="
docker exec mongo-lot0 mongodump --db "$DB" --archive | gzip > "$OUT_DIR/dump.archive.gz"

SIZE="$(du -h "$OUT_DIR/dump.archive.gz" | cut -f1)"
echo "OK — $OUT_DIR/dump.archive.gz ($SIZE)"
echo "$DB" > "$OUT_DIR/SOURCE_DB"
date -u +%FT%TZ > "$OUT_DIR/TAKEN_AT"
