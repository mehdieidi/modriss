FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --fetch-retries=5 --fetch-retry-factor=2 --fetch-retry-mintimeout=1000 --fetch-retry-maxtimeout=60000
COPY . .
ARG VITE_BACKEND_BASE_URL=
ARG VITE_APP_URL=
ARG VITE_DOCS_URL=
ENV VITE_BACKEND_BASE_URL=$VITE_BACKEND_BASE_URL
ENV VITE_APP_URL=$VITE_APP_URL
ENV VITE_DOCS_URL=$VITE_DOCS_URL
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8083
CMD ["nginx", "-g", "daemon off;"]
