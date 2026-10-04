#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
base=/home/ubuntu/pokemon-demo
bundle=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
[[ "$bundle" == "$base/agent-update-20261004" ]] || { echo 'Extract into /home/ubuntu/pokemon-demo/agent-update-20261004'; exit 1; }
cd "$bundle"
sha256sum -c SHA256SUMS
docker network inspect morris-demo_default >/dev/null
docker inspect pokemon-web-game >/dev/null
project=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' pokemon-web-game)
[[ -n "$project" && "$project" != '<no value>' ]] || project=pokemon-demo
docker compose -p "$project" -f "$base/compose.yaml" config --quiet
docker compose -p "$project" -f "$bundle/compose.yaml" config --quiet
docker build -t pokemon-agent-web:20261004 "$bundle"
# A temporary container verifies the new image before stopping the live game.
preview="pokemon-preview-$(date +%s)"
backup=""
changed=0
old_stopped=0
cleanup() {
  code=$?
  docker rm -f "$preview" >/dev/null 2>&1 || true
  if (( code != 0 )); then
    if (( changed )); then echo "Update failed. Run: sudo bash '$bundle/rollback.sh' '$backup'";
    elif (( old_stopped )); then docker start pokemon-web-game >/dev/null || true; fi
  fi
  exit "$code"
}
trap cleanup EXIT
docker run -d --name "$preview" --network morris-demo_default \
  -e PUBLIC_ORIGIN=https://pokemon.hanzhu-lab.online -e AGENT_BIND_ADDRESS=0.0.0.0 \
  -e AGENT_PORT=8080 -e AGENT_STORAGE=memory -e LLM_PROVIDER=disabled pokemon-agent-web:20261004 >/dev/null
preview_ip=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$preview")
ready=0
for attempt in $(seq 1 30); do
  if curl --silent --fail --max-time 3 -H 'Host: pokemon.hanzhu-lab.online' "http://$preview_ip:8080/growth/" >/dev/null; then ready=1; break; fi
  sleep 1
done
(( ready )) || { echo 'Preview failed; live game untouched.'; exit 1; }
docker rm -f "$preview" >/dev/null
stamp=$(date -u +%Y%m%dT%H%M%SZ)
backup="$base/backups/agent-update-$stamp"
mkdir -p "$backup"
cp "$base/compose.yaml" "$backup/compose.yaml"
printf '%s\n' "$project" > "$backup/project.txt"
old_tag="pokemon-web-backup:$stamp"
printf '%s\n' "$old_tag" > "$backup/image-tag.txt"
printf 'services:\n  game:\n    image: %s\n' "$old_tag" > "$backup/rollback-image.yaml"
docker stop pokemon-web-game >/dev/null
old_stopped=1
# Commit the stopped old container so writable-layer game data is also retained.
docker commit pokemon-web-game "$old_tag" > "$backup/image-id.txt"
mkdir -p "$base/agent-saves"
chown 10001:10001 "$base/agent-saves"
chmod 700 "$base/agent-saves"
cp "$bundle/compose.yaml" "$base/compose.yaml"
changed=1
docker compose -p "$project" -f "$base/compose.yaml" up -d --no-deps --force-recreate game
ready=0
for attempt in $(seq 1 30); do
  if curl --silent --fail --max-time 5 https://pokemon.hanzhu-lab.online/growth/ | grep -q 'player-dock'; then ready=1; break; fi
  sleep 1
done
(( ready )) || { echo 'Online verification failed; use rollback command above.'; exit 1; }
printf 'SUCCESS\nWebsite: https://pokemon.hanzhu-lab.online/growth/\nBackup: %s\nRollback: sudo bash %q %q\n' "$backup" "$bundle/rollback.sh" "$backup"
