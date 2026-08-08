#!/usr/bin/env bash
# LOT2-09 — retour arrière en une commande (D20/§6.2). Redéploie le tag précédent (releases/.previous,
# écrit par deploy.sh), sur un jar DÉJÀ construit — pas de rebuild pendant un incident.
#
# Usage : scripts/rollback.sh
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."   # -> deploy/

if [ ! -f releases/.previous ]; then
  echo "releases/.previous absent — aucun déploiement antérieur connu à restaurer." >&2
  exit 1
fi

PREVIOUS_TAG="$(cat releases/.previous)"
if [ ! -f "releases/$PREVIOUS_TAG/app.jar" ]; then
  echo "releases/$PREVIOUS_TAG/app.jar introuvable (supprimé ?) — rollback impossible sans reconstruire." >&2
  exit 1
fi

echo "== Retour arrière vers $PREVIOUS_TAG =="
./scripts/deploy.sh "$PREVIOUS_TAG"
