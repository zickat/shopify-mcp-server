#!/usr/bin/env bash
# LOT2-09 — construit un jar pour un tag de release donné et le range dans releases/<tag>/app.jar.
# Un jar par tag, construit UNE fois : le retour arrière (rollback.sh) redémarre sur un jar déjà là,
# jamais un rebuild pendant un incident (D20/§6.2 — « retour arrière en une commande »).
#
# Usage : scripts/build-release.sh [tag]
#   tag par défaut : le SHA court du HEAD courant du dépôt shopify-mcp-server (git rev-parse --short).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."   # -> deploy/
REPO_ROOT="$(cd .. && pwd)"

TAG="${1:-$(git -C "$REPO_ROOT" rev-parse --short HEAD)}"
DIRTY=""
if [ -n "$(git -C "$REPO_ROOT" status --porcelain)" ]; then
  DIRTY=" (ARBRE DE TRAVAIL SALE — pas seulement le commit $TAG)"
fi

RELEASE_DIR="releases/$TAG"
if [ -f "$RELEASE_DIR/app.jar" ]; then
  echo "releases/$TAG/app.jar existe déjà — rien à reconstruire (relancer avec un tag différent pour forcer)."
  exit 0
fi

echo "== Build Maven ($REPO_ROOT, tag=$TAG$DIRTY) =="
(cd "$REPO_ROOT" && mvn --batch-mode --no-transfer-progress -q clean package -DskipTests)

JAR="$(find "$REPO_ROOT/target" -maxdepth 1 -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | head -1)"
if [ -z "$JAR" ]; then
  echo "Aucun jar trouvé dans $REPO_ROOT/target après build." >&2
  exit 1
fi

mkdir -p "$RELEASE_DIR"
cp "$JAR" "$RELEASE_DIR/app.jar"
{
  echo "shopify-mcp-server: $(git -C "$REPO_ROOT" rev-parse HEAD)$DIRTY"
  echo "built-at: $(date -u +%FT%TZ)"
  if [ -n "${MCP_SHOPIFY_CATALOG_PATH:-}" ] && [ -d "${MCP_SHOPIFY_CATALOG_PATH}/.git" ]; then
    echo "mcp-shopify-catalog: $(git -C "$MCP_SHOPIFY_CATALOG_PATH" rev-parse HEAD)"
  fi
} > "$RELEASE_DIR/BUILD_INFO"

echo "== $RELEASE_DIR/app.jar prêt =="
cat "$RELEASE_DIR/BUILD_INFO"
