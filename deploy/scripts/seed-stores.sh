#!/usr/bin/env bash
# LOT2-09 — crée les documents STORE de Vélotrip et LureLab (slug/shopDomain, RIEN de secret).
# Idempotent. Voir com.zickat.shopifymcpserver.tenancy.SeedStoresRunner pour le détail.
#
# Tourne HORS docker (java -jar directement sur l'hôte, pas dans le conteneur app) : le profil "seed"
# doit pouvoir se connecter à Mongo et sortir seul, sans tourner à côté du service qui sert du trafic.
# MONGODB_URI par défaut de l'app pointe déjà sur localhost:27017 (mongo-lot0) — inutile de le répéter.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."   # -> deploy/

if [ ! -f .env ]; then
  echo "deploy/.env absent." >&2
  exit 1
fi
RELEASE_TAG="$(grep -E '^RELEASE_TAG=' .env | cut -d= -f2-)"
JAR="releases/$RELEASE_TAG/app.jar"
if [ ! -f "$JAR" ]; then
  echo "Introuvable : $JAR (RELEASE_TAG='$RELEASE_TAG' dans .env) — déployer d'abord (scripts/deploy.sh)." >&2
  exit 1
fi

java -jar "$JAR" --spring.profiles.active=seed --seed.command=stores
