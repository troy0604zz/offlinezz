#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "$0")/.." && pwd)"
maven="$project_root/offline/apache-maven-3.9.11/bin/mvn"
repository="$project_root/offline/maven-repository"
settings="$project_root/offline/maven-settings.xml"

if [[ ! -x "$maven" ]]; then
  echo "Bundled Maven is missing or not executable: $maven" >&2
  exit 1
fi
if [[ ! -d "$repository" ]]; then
  echo "Offline repository is missing: $repository" >&2
  exit 1
fi
if [[ ! -f "$settings" ]]; then
  echo "Offline Maven settings are missing: $settings" >&2
  exit 1
fi
if [[ $# -eq 0 ]]; then
  set -- clean test package
fi

cd "$project_root/server"
exec "$maven" --settings "$settings" --offline --no-transfer-progress "-Dmaven.repo.local=$repository" "$@"
