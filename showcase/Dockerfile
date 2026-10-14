FROM nginx:stable-alpine

COPY deployment/nginx.conf /etc/nginx/conf.d/default.conf
COPY showcase/*.html showcase/*.css showcase/*.mjs /usr/share/nginx/html/
