FROM node:22-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

ARG VITE_APP_VERSION=dev
ENV VITE_APP_VERSION=$VITE_APP_VERSION
RUN npm run build

FROM nginx:alpine AS runtime

# Defaults match the `server` container name on the compose.yaml / build-from-source
# bridge network; override BACKEND_HOST/BACKEND_PORT for deployments without that
# network, e.g. Railway's private-networking domain for the backend service.
ENV BACKEND_HOST=server
ENV BACKEND_PORT=8080

COPY nginx.conf.template /etc/nginx/templates/default.conf.template

COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
