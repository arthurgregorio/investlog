#!/usr/bin/env bash
# Build both Docker images for InvestLog:
#   - server: Spring Boot Cloud Native Buildpacks image (via Gradle)
#   - client: Vue SPA served by nginx (via docker build)
#
# Usage: ./build.sh
set -euo pipefail

cd "$(dirname "$0")"

# Load .env if present (for image tags); fall back to defaults otherwise.
if [ -f .env ]; then
    set -a
    # shellcheck disable=SC1091
    . ./.env
    set +a
fi

SERVER_IMAGE_TAG="${SERVER_IMAGE_TAG:-v0.1.0}"
CLIENT_IMAGE_TAG="${CLIENT_IMAGE_TAG:-v0.1.0}"

echo "==> Building server image (Gradle bootBuildImage) -> investlog/server:${SERVER_IMAGE_TAG}"
( cd server && ./gradlew bootBuildImage --imageName="investlog/server:${SERVER_IMAGE_TAG}" )

echo "==> Building client image (docker build) -> investlog/client:${CLIENT_IMAGE_TAG}"
docker build -t "investlog/client:${CLIENT_IMAGE_TAG}" ./client

echo
echo "Done. Images:"
echo "  investlog/server:${SERVER_IMAGE_TAG}"
echo "  investlog/client:${CLIENT_IMAGE_TAG}"
echo
echo "Next: docker compose up -d   (then open http://localhost:${WEB_PORT:-8081})"
