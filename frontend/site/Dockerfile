# The public website: plain HTML/CSS/JS served by nginx (no build tool needed).
FROM nginx:1.29-alpine

# Railway passes these service variables at build time; they are written into the files below.
ARG VITE_API_URL
ARG VORNX_APP_URL
ARG VORNX_PARTNER_URL

COPY index.html /usr/share/nginx/html/index.html
COPY assets /usr/share/nginx/html/assets
COPY img /usr/share/nginx/html/img

WORKDIR /usr/share/nginx/html
RUN test -n "$VITE_API_URL" || (echo "VITE_API_URL is not set" && exit 1) \
 && sed -i "s#__VORNX_API_URL__#${VITE_API_URL}#g" assets/site.js \
 && sed -i "s#__VORNX_APP_URL__#${VORNX_APP_URL}#g; s#__VORNX_PARTNER_URL__#${VORNX_PARTNER_URL}#g" index.html \
 # Version the CSS/JS links with their content hash, so browsers may cache them for a year
 # and still get a new deploy right away.
 && V=$(cat assets/site.css assets/site.js | md5sum | cut -c1-10) \
 && sed -i "s#/assets/site.css#/assets/site.css?v=$V#; s#/assets/site.js#/assets/site.js?v=$V#" index.html

# Files in /etc/nginx/templates/*.template get ${VARIABLES} replaced at startup (PORT, VITE_API_URL).
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY security-headers.inc.template /etc/nginx/templates/security-headers.inc.template
ENV PORT=8080
EXPOSE 8080
