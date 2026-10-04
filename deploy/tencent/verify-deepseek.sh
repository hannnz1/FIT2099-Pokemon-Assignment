#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
base=/home/ubuntu/pokemon-demo
bundle=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
[[ "$bundle" == "$base/deepseek-update-20261004" ]] || { echo 'Unexpected bundle directory'; exit 1; }
test -s "$base/.env.deepseek"
grep -q '^DEEPSEEK_API_KEY=.' "$base/.env.deepseek"
[[ "$(stat -c %a "$base/.env.deepseek")" == 600 ]] || { echo 'Key file must have permission 600'; exit 1; }
cd "$bundle"
sha256sum -c SHA256SUMS
docker build -t pokemon-agent-web:deepseek-20261004 .
printf 'Isolated real AI berry acceptance; at most 24 requests. Production unchanged.\n'
# Passing the existing env file avoids reading keys into a command or build context.
# No public ports, production network alias, mounts or production save access.
printf '完成树果任务，不要花金币\ny\n' |
  docker run --rm -i --name pokemon-deepseek-acceptance \
    --memory 512m --cpus 0.75 \
    --env-file "$base/.env.deepseek" \
    -e DEEPSEEK_MODEL=deepseek-flash -e DEEPSEEK_TIMEOUT_MS=15000 \
    --entrypoint java pokemon-agent-web:deepseek-20261004 \
    -Xmx320m -cp /app/game.jar game.agent.demo.DeepSeekAgentConsole
echo 'Isolated acceptance passed. Public website AI is still unchanged.'
