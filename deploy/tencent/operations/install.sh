#!/usr/bin/env bash
set -Eeuo pipefail
base=/home/ubuntu/pokemon-demo
source_dir=$(cd -- "$(dirname -- "$0")" && pwd)
mkdir -p "$base/operations"
install -m 700 "$source_dir/backup.sh" "$base/operations/backup.sh"
install -m 700 "$source_dir/monitor.py" "$base/operations/monitor.py"
cat > /etc/cron.d/pokemon-game-operations <<'CRON'
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
17 2 * * * root /home/ubuntu/pokemon-demo/operations/backup.sh >> /home/ubuntu/pokemon-demo/operations/backup.log 2>&1
*/5 * * * * root python3 /home/ubuntu/pokemon-demo/operations/monitor.py >> /home/ubuntu/pokemon-demo/operations/monitor.log 2>&1
CRON
chmod 644 /etc/cron.d/pokemon-game-operations
printf 'Game-only backup and local alert journal installed; offsite delivery requires a configured destination.\n'
