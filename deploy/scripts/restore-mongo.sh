#!/usr/bin/env bash
# LOT2-09 — restaure une sauvegarde prise par backup-mongo.sh. RESTAURE TOUJOURS SUR UNE BASE CIBLE
# EXPLICITE, jamais implicitement sur la base de production — c'est ce qui permet la répétition sans
# risque (le test de restauration de ce lot restaure sur une base neuve, voir RUNBOOK.md) ET une vraie
# restauration en cas d'incident (cible = shopify_mcp_server, en connaissance de cause).
#
# Usage : scripts/restore-mongo.sh <dossier-de-sauvegarde> <base-cible>
#   Exemple (drill, ne touche jamais la production) :
#     scripts/restore-mongo.sh backups/20260808T020000Z_shopify_mcp_server shopify_mcp_server_restore_drill
#   Exemple (restauration réelle après incident) :
#     scripts/restore-mongo.sh backups/<le-plus-récent> shopify_mcp_server
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."   # -> deploy/

BACKUP_DIR="${1:?Usage : restore-mongo.sh <dossier-de-sauvegarde> <base-cible>}"
TARGET_DB="${2:?Usage : restore-mongo.sh <dossier-de-sauvegarde> <base-cible>}"
ARCHIVE="$BACKUP_DIR/dump.archive.gz"

if [ ! -f "$ARCHIVE" ]; then
  echo "Introuvable : $ARCHIVE" >&2
  exit 1
fi
SOURCE_DB="$(cat "$BACKUP_DIR/SOURCE_DB" 2>/dev/null || echo '?')"

echo "== mongorestore : $ARCHIVE (source: $SOURCE_DB) -> base '$TARGET_DB' sur mongo-lot0 =="
gunzip -c "$ARCHIVE" | docker exec -i mongo-lot0 mongorestore --archive --nsFrom="${SOURCE_DB}.*" --nsTo="${TARGET_DB}.*"

echo "== Vérification — comptage par collection sur '$TARGET_DB' =="
docker exec mongo-lot0 mongosh --quiet "$TARGET_DB" --eval "
db.getCollectionNames().sort().forEach(c => print(c + ': ' + db[c].countDocuments()));
"
echo ""
echo "Rappel : le coffre restauré (storeCredential.ciphertext) reste ILLISIBLE sans CATALOG_MASTER_KEY"
echo "— cette clé ne vit jamais en base, elle n'est donc jamais dans cette sauvegarde ni cette restauration."
