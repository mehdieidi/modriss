#!/bin/sh
set -eu
backend_url=${MODRISS_FRONTEND_BACKEND_BASE_URL:-}
# The URL becomes a JavaScript string. Reject script syntax and control characters.
case "$backend_url" in
    *[!a-zA-Z0-9:/?\&=._~%#@+,\;-]*)
        echo "Invalid frontend runtime URL" >&2
        exit 1
        ;;
esac
printf 'window.MODRISS_BACKEND_BASE_URL = "%s";\n' \
    "$backend_url" > /usr/share/nginx/html/backend-config.js
