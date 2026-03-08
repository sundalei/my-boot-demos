#!/bin/sh
# entrypoint.sh

# Exit immediately if a command exits with a non-zero status.
set -e

# Initialize JAVA_TOOL_OPTIONS to ensure it's not unset.
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS}"

# Check for each environment variable and convert it into a
# Java system property (e.g., MONGO_URI -> -Dmongo.uri=...)
# This allows any combination of the variables to be provided at runtime.

if [ -n "$NOTION_API_TOKEN" ]; then
	echo "✅ NOTION_API_TOKEN is set. Adding to Java system properties."
	export JAVA_TOOL_OPTIONS="-Dnotion.api.token=${NOTION_API_TOKEN} ${JAVA_TOOL_OPTIONS}"
fi

if [ -n "$NOTION_API_DATEBASE_ID" ]; then
	echo "✅ NOTION_API_DATEBASE_ID is set. Adding to Java system properties."
	# Note: Be cautious about logging or exposing secrets. Here we just confirm it's set.
	export JAVA_TOOL_OPTIONS="-Dnotion.api.database-id=${NOTION_API_DATEBASE_ID} ${JAVA_TOOL_OPTIONS}"
fi

if [ -n "$MONGO_URI" ]; then
	echo "✅ MONGO_URI is set. Adding to Java system properties."
	# Note: Be cautious about logging or exposing secrets. Here we just confirm it's set.
	export JAVA_TOOL_OPTIONS="-Dmongo.uri=${MONGO_URI} ${JAVA_TOOL_OPTIONS}"
fi

echo "🚀 Starting application..."

# Execute the command passed to this script (the CMD from the Dockerfile).
exec "$@"
