FROM node:22-alpine
WORKDIR /app
COPY package.json package-lock.json ./
COPY packages/js/glsp-server/package.json packages/js/glsp-server/package.json
RUN npm install --workspace @modless/glsp-server --omit=dev
COPY packages/js/glsp-server packages/js/glsp-server
RUN npm run build -w @modless/glsp-server
WORKDIR /app/packages/js/glsp-server
ENV MODLESS_GLSP_PORT=8081
ENV MODLESS_GLSP_PATH=modless
ENV MODLESS_BACKEND_URL=http://backend:8080
EXPOSE 8081
CMD ["node", "dist/main.js"]
