#!/usr/bin/env bash
set -Eeuo pipefail
base=${POKEMON_BASE:-/home/ubuntu/pokemon-demo}
container=${POKEMON_CONTAINER:-pokemon-web-game}
base=$(realpath -- "$base")
[[ "$base" == /home/ubuntu/pokemon-demo ]] || { echo 'Unexpected game directory'; exit 1; }
[[ "$container" == pokemon-web-game ]] || exit 1
umask 077
mkdir -p "$base/operations/backups"
exec 9>"$base/operations/backup.lock"
flock -n 9 || exit 0
stamp=$(date -u +%Y%m%dT%H%M%SZ)
archive="$base/operations/backups/game-$stamp.tar.gz"
paused=false
finish(){ if $paused; then docker unpause "$container" >/dev/null || true; fi; }
trap finish EXIT
docker inspect "$container" --format '{{.Config.Image}}' >"$base/operations/image-tag.txt"
docker pause "$container" >/dev/null;paused=true
tar -czf "$archive.part" -C "$base" compose.yaml agent-saves -C "$base/operations" image-tag.txt
tar -tzf "$archive.part" >/dev/null
mv -- "$archive.part" "$archive"
docker unpause "$container" >/dev/null;paused=false
sha256sum "$archive" >"$archive.sha256"
# Retention: 28 daily snapshots. Only matching regular archives inside the verified game backup directory.
find "$base/operations/backups" -maxdepth 1 -type f -name 'game-*.tar.gz*' -mtime +28 -delete
printf 'Backup verified: %s\n' "$archive"
