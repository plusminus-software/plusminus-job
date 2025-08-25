#!/bin/bash

set -euo pipefail

echo "Exporting inputs as env vars..."

if [[ -z "${GITHUB_EVENT_INPUTS_JSON:-}" ]]; then
  echo "Error: GITHUB_EVENT_INPUTS_JSON is not set"
  exit 1
fi

# Export each input as an uppercased env variable
while IFS= read -r key; do
  value=$(jq -r --arg k "$key" '.[$k]' <<< "$GITHUB_EVENT_INPUTS_JSON")
  env_key=$(echo "$key" | tr '[:lower:]' '[:upper:]' | sed 's/[^A-Z0-9_]/_/g')
  echo "Exporting $env_key=$value"
  echo "${env_key}=$value" >> "$GITHUB_ENV"
done < <(jq -r 'keys[]' <<< "$GITHUB_EVENT_INPUTS_JSON")