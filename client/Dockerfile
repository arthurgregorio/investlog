# syntax=docker/dockerfile:1

# ---- Stage 1: build the Vue SPA ----
FROM node:22-alpine AS build
WORKDIR /app

# Install dependencies first (better layer caching).
COPY package.json package-lock.json ./
RUN npm ci

# Build the static bundle into /app/dist.
COPY . .
RUN npm run build

# ---- Stage 2: serve with nginx ----
FROM nginx:alpine AS runtime

# SPA history fallback + reverse proxy for /private -> server:8080.
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Static assets.
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
