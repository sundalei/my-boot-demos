#!/bin/sh
# entrypoint.sh

# Exit immediately if a command exits with a non-zero status.
set -e

# Log presence for debugging (Optional)
[ -n "$SPRING_MONGODB_URI" ] && echo "✅ MongoDB URI is set."
[ -n "$NOTION_API_TOKEN" ] && echo "✅ Notion Token is set."
[ -n "$NOTION_API_DATABASE_ID" ] && echo "✅ Notion Database ID is set."
[ -n "$NOTION_API_SAVING_DATABASE_ID" ] && echo "✅ Notion Saving Database ID is set."

# Just start the app. Spring Boot will find the variables itself.
exec "$@"
