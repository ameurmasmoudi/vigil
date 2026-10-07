#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

case "${1:-}" in
  api|worker) ;;
  *) echo "usage: $0 api|worker" >&2; exit 1 ;;
esac

set -a
source .env
set +a

cd "$1"
exec ./mvnw spring-boot:run "${@:2}"
