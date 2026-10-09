# Galaxy Explorer — frontend (React + Vite) servido por nginx.
#
# Multi-stage: Node so existe no build; o runtime e nginx com os arquivos estaticos.
# Tags fixas, conferidas no Docker Hub em 2026-10-08 (nginx 1.30 = linha stable).

# ── build ──────────────────────────────────────────────────────────────────────
FROM node:24.21.0-alpine3.24 AS build
WORKDIR /src

# Dependencias pelo lockfile (camada refeita so quando package*.json muda).
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
# "build" = typecheck + vite build -> dist/
RUN npm run build

# ── runtime ────────────────────────────────────────────────────────────────────
FROM nginx:1.30.5-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=6 \
    CMD wget -qO /dev/null http://127.0.0.1/ || exit 1
