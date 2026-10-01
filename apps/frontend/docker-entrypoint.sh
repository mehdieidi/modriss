#!/bin/sh
set -eu
backend_url=${MODRISS_FRONTEND_BACKEND_BASE_URL:-}
docs_url=${MODRISS_FRONTEND_DOCS_URL:-}
# Values become JavaScript strings. Reject script syntax and control characters.
for url in "$backend_url" "$docs_url"; do
    case "$url" in
        *[!a-zA-Z0-9:/?\&=._~%#@+,\;-]*)
            echo "Invalid frontend runtime URL" >&2
            exit 1
            ;;
    esac
done
printf 'window.MODRISS_BACKEND_BASE_URL = "%s";\nwindow.MODRISS_DOCS_URL = "%s";\n' \
    "$backend_url" "$docs_url" > /usr/share/nginx/html/backend-config.js
