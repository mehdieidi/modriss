FROM squidfunk/mkdocs-material:9.6.14 AS build
WORKDIR /docs
COPY mkdocs.yml ./
COPY docs docs
RUN mkdocs build --strict

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /docs/site /usr/share/nginx/html
EXPOSE 80
