# syntax=docker/dockerfile:1.7
# Build the SPA (npm cache mount; NPM_REGISTRY → internal mirror in the closed network) and serve it with NGINX.
FROM node:24.11.1-alpine AS build
ARG NPM_REGISTRY=https://registry.npmjs.org/
WORKDIR /web
COPY apps/web/package.json apps/web/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund --registry="$NPM_REGISTRY"
COPY apps/web/ ./
RUN npx vite build

FROM nginx:1.28.0-alpine
COPY infra/nginx/nginx.conf /etc/nginx/nginx.conf
COPY infra/nginx/security-headers.conf /etc/nginx/security-headers.conf
COPY --from=build /web/dist /usr/share/nginx/html
