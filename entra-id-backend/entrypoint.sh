#!/bin/sh
# entrypoint.sh

# Exit immediately if a command exits with a non-zero status.
set -e

# Initialize JAVA_TOOL_OPTIONS to ensure it's not unset.
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS}"

# Check for each environment variable and convert it into a
# Java system property (e.g., MONGO_URI -> -Dmongo.uri=...)
# This allows any combination of the variables to be provided at runtime.

echo "🚀 Starting application..."

# Execute the command passed to this script (the CMD from the Dockerfile).
exec "$@"
