# The admin dashboard: build with Node, serve the result with nginx.

# ---------- 1. Build ----------
FROM node:24-alpine AS build
WORKDIR /app
# Railway passes the service variable VITE_API_URL here; Vite bakes it into the JavaScript.
ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN test -n "$VITE_API_URL" || (echo "VITE_API_URL is not set" && exit 1)
RUN npm run build

# ---------- 2. Serve ----------
FROM nginx:1.29-alpine
COPY --from=build /app/dist /usr/share/nginx/html
# Files in /etc/nginx/templates/*.template get ${VARIABLES} replaced at startup (PORT, VITE_API_URL).
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY security-headers.inc.template /etc/nginx/templates/security-headers.inc.template
# Turns VITE_API_URL (https://...) into VITE_API_WS_URL (wss://...) for the CSP, before the templates are filled in.
COPY --chmod=755 api-ws-url.envsh /docker-entrypoint.d/15-api-ws-url.envsh
ENV PORT=8080
EXPOSE 8080
