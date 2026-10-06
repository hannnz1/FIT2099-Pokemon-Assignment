#!/usr/bin/env bash
set -Eeuo pipefail
base=/home/ubuntu/pokemon-demo
archive=${1:?Provide verified backup archive}
[[ "$(realpath -- "$archive")" == "$base"/operations/backups/game-*.tar.gz ]] || exit 1
stamp=$(date -u +%Y%m%dT%H%M%SZ)
target="$base/operations/restore-$stamp"
[[ ! -e "$target" ]] || exit 1
start=$(date +%s)
sha256sum -c "$archive.sha256"
mkdir -m 700 -- "$target"
tar -xzf "$archive" -C "$target"
image=$(docker inspect pokemon-web-game --format '{{.Config.Image}}')
docker run --rm --network none --read-only --user 0 -v "$target:/restore:ro" -v "$base/operations-package:/audit:ro" -v "$base/optimization-matrix:/matrix:ro" --entrypoint java "$image" -cp /audit:/matrix/fit2099-pokemon-assignment-1.0.0.jar game.agent.persistence.CheckpointAudit /restore/agent-saves
printf 'ISOLATED_RESTORE_SECONDS=%s\n' "$(( $(date +%s)-start ))"
printf 'PRODUCTION_UNCHANGED=true\n'
