#!/usr/bin/env bash
# LOT2-09 (D24) — obtient un certificat Let's Encrypt réel pour ce nœud Tailscale, sur son nom
# `*.ts.net`. RÉSERVÉ À VAL : `tailscale cert` exige root (ou le statut "operator", posé UNE FOIS par
# une commande qui exige elle-même sudo) — un agent de cette startup n'a pas de mot de passe sudo sur
# cette machine et ne peut pas franchir cette étape (voir RUNBOOK.md, « Ce qui reste bloqué sur Val »).
#
# Vérifié cette nuit, sans sudo (échec attendu, documente EXACTEMENT où ça bute) :
#   $ tailscale cert val-server.tail5e0606.ts.net
#   Access denied: cert access denied
#   Use 'sudo tailscale cert val-server.tail5e0606.ts.net'.
#   To not require root, use 'sudo tailscale set --operator=$USER' once.
#
# Usage (Val, une fois) :
#   sudo tailscale set --operator=val        # une seule fois, permanent ensuite
#   deploy/scripts/setup-tailscale-cert.sh    # celui-ci, sans sudo à partir de là
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."   # -> deploy/

DOMAIN="val-server.tail5e0606.ts.net"
mkdir -p certs

echo "== tailscale cert $DOMAIN =="
tailscale cert --cert-file "certs/$DOMAIN.crt" --key-file "certs/$DOMAIN.key" "$DOMAIN"

echo ""
echo "OK — certs/$DOMAIN.crt et certs/$DOMAIN.key obtenus."
echo ""
echo "Ce que ce script NE fait PAS encore (suite à écrire, hors périmètre d'une nuit sans validation) :"
echo "  - basculer Keycloak de 'start-dev' vers 'start' avec ces fichiers (KC_HTTPS_CERTIFICATE_FILE/"
echo "    KC_HTTPS_CERTIFICATE_KEY_FILE), et réimporter le realm avec des URLs https:// (frontendUrl,"
echo "    included.custom.audience) au lieu de http://."
echo "  - activer server.ssl côté Kotlin (Spring Boot 4.1 accepte un bundle PEM directement, pas besoin"
echo "    de PKCS12 : server.ssl.certificate / server.ssl.certificate-private-key)."
echo "  - renouveler ce certificat périodiquement (tailscale cert ne le fait pas tout seul — cron ou"
echo "    relance manuelle avant expiration)."
echo "Voir RUNBOOK.md pour le détail."
