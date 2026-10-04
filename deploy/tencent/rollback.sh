#!/usr/bin/env bash
set -Eeuo pipefail
base=/home/ubuntu/pokemon-demo
backup=${1:?Provide backup directory printed by update.sh}
backup=$(realpath -- "$backup")
[[ "$backup" == "$base/backups/agent-update-"* ]] || { echo 'Unexpected backup path'; exit 1; }
test -f "$backup/compose.yaml"
test -f "$backup/rollback-image.yaml"
docker image inspect "$(cat "$backup/image-tag.txt")" >/dev/null
cp "$backup/compose.yaml" "$base/compose.yaml"
project=$(cat "$backup/project.txt")
docker compose -p "$project" -f "$base/compose.yaml" -f "$backup/rollback-image.yaml" up -d --no-deps --force-recreate game
echo 'Old game restored from backup image. New agent-saves have been retained.'
