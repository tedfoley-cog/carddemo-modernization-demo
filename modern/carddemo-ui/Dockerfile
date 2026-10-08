# syntax=docker/dockerfile:1
FROM node:20-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# API_URL is the internal Cloud Run URL of carddemo-online; nginx proxies /api so the browser stays same-origin.
ENV PORT=8080 API_URL=http://localhost:8081
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 8080
