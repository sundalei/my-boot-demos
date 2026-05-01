#!/bin/sh
# entrypoint.sh

# Exit immediately if a command exits with a non-zero status.
set -e

# Log presence for debugging (Optional)

# Just start the app. Spring Boot will find the variables itself.
exec "$@"
