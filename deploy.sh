#!/bin/bash
set -euo pipefail

PROJECT_DIR="$HOME/server/projects/vinayaga-eservice"
FRONTEND_DIR="$PROJECT_DIR/eservice-frontend"
WEB_DIR="/var/www/vinayaga-eservice"

echo "======================================"
echo " Vinayaga E-Service Deployment"
echo "======================================"

cd "$PROJECT_DIR"

echo "[1/7] Checking working tree..."
if [ -n "$(git status --porcelain)" ]; then
    echo "ERROR: Working tree is not clean."
    echo "Commit or remove local changes before deploying."
    git status
    exit 1
fi

echo "[2/7] Pulling latest code..."
git pull --ff-only origin develop

echo "[3/7] Building frontend..."
cd "$FRONTEND_DIR"
npm ci
npm run build

echo "[4/7] Publishing frontend..."
TEMP_WEB_DIR=$(mktemp -d)

cp -r dist/. "$TEMP_WEB_DIR"/

sudo rm -rf "$WEB_DIR"/*
sudo cp -r "$TEMP_WEB_DIR"/. "$WEB_DIR"/

rm -rf "$TEMP_WEB_DIR"

echo "[5/7] Building backend Docker image..."
cd "$PROJECT_DIR"
docker compose build vinayaga-eservice

echo "[6/7] Restarting backend..."
docker compose up -d vinayaga-eservice

echo "[7/7] Checking services..."
docker ps

echo ""
echo "======================================"
echo " Deployment completed successfully!"
echo "======================================"
