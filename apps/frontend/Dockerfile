FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY . /usr/share/nginx/html
COPY docker-entrypoint.sh /docker-entrypoint.d/40-backend-config.sh
RUN chmod +x /docker-entrypoint.d/40-backend-config.sh \
    && rm /usr/share/nginx/html/Dockerfile /usr/share/nginx/html/nginx.conf /usr/share/nginx/html/docker-entrypoint.sh
EXPOSE 8082
