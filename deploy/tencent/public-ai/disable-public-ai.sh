#!/bin/bash
set -euo pipefail
b=/home/ubuntu/pokemon-demo
p=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' pokemon-web-game)
cp "$b/compose.yaml" "$b/compose.before-ai-disable-$(date -u +%Y%m%dT%H%M%SZ).yaml"
sed -i 's/LLM_PROVIDER: deepseek/LLM_PROVIDER: disabled/' "$b/compose.yaml"
docker compose -p "$p" -f "$b/compose.yaml" up -d --no-deps --force-recreate game
echo '公开 AI 已关闭；保存队伍和额度账本。'
