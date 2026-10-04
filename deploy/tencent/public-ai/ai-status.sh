#!/bin/bash
set -euo pipefail
python3 - "$@" <<'PY'
import json,sys,datetime
from pathlib import Path
from zoneinfo import ZoneInfo
p=Path('/home/ubuntu/pokemon-demo/agent-saves/ai-budget.json')
if not p.exists():
    print('尚无模型调用记录。');sys.exit(0)
s=json.loads(p.read_text())
today=datetime.datetime.now(ZoneInfo('Australia/Sydney')).date().isoformat()
d=s['dayReserved'] if s['day']==today else 0
m=s['monthReserved'] if s['month']==today[:7] else 0
print('计费周期：Australia/Sydney；全站共享额度')
print('今日 Token 预留：',d,'/ 500000')
print('本月 Token 预留：',m,'/ 2000000')
print('累计请求 / 成功 / 失败：',s['calls'],s['successfulCalls'],s['failedCalls'])
print('已知实际输入 / 输出 / 缓存 Token：',s['actualInputTokens'],s['actualOutputTokens'],s['cachedInputTokens'])
print('成功请求按已验证 usage 结算；超时、重启后的未知费用保留全部预留。实际账单以 DeepSeek 控制台为准。')
if len(sys.argv)==4:
    rates=list(map(float,sys.argv[1:]))
    if any(v<0 for v in rates):raise ValueError('价格不可为负')
    input_rate,cached_rate,output_rate=rates
    cost=(max(0,s['actualInputTokens']-s['cachedInputTokens'])*input_rate+s['cachedInputTokens']*cached_rate+s['actualOutputTokens']*output_rate)/1000000
    print('按提供的每百万 Token 单价估算已知费用：',round(cost,6),'（单位与提供单价一致，不含未知请求）')
else:
    print('估算费用：bash ai-status.sh 输入单价 缓存单价 输出单价（每百万 Token）；不要填密钥。')
PY
