#!/usr/bin/env python3
import json, subprocess, shutil, datetime, urllib.request, os
from zoneinfo import ZoneInfo
from pathlib import Path
base=Path('/home/ubuntu/pokemon-demo');ops=base/'operations';ops.mkdir(exist_ok=True)
now=datetime.datetime.now(datetime.timezone.utc);alerts=[];metrics={"at":now.isoformat()}
try:
 state=json.loads(subprocess.check_output(['docker','inspect','pokemon-web-game','--format','{{json .State}}'],text=True));metrics['running']=state['Running'];metrics['paused']=state['Paused']
 if not state['Running']:alerts.append('GAME_NOT_RUNNING')
 if state['Paused']:alerts.append('GAME_PAUSED')
except Exception:alerts.append('CONTAINER_CHECK_FAILED')
try:
 with urllib.request.urlopen('https://pokemon.hanzhu-lab.online/healthz',timeout=10) as r:
  metrics['httpStatus']=r.status;health=json.load(r);metrics.update(health)
  if health.get('storageErrors',0):alerts.append('GAME_STORAGE_ERROR')
  if health.get('modelQueue',0)>=12:alerts.append('MODEL_QUEUE_PRESSURE')
  if health.get('httpQueue',0)>=24:alerts.append('HTTP_QUEUE_PRESSURE')
except Exception:alerts.append('WEBSITE_UNREACHABLE')
free=shutil.disk_usage(base).free;metrics['diskFreeBytes']=free
if free<2*1024**3:alerts.append('DISK_SPACE_LOW')
backups=list((ops/'backups').glob('game-*.tar.gz'));age=(now.timestamp()-max(p.stat().st_mtime for p in backups)) if backups else None;metrics['backupAgeSeconds']=age
if age is None or age>26*3600:alerts.append('BACKUP_STALE')
p=base/'agent-saves'/'ai-budget.json'
if p.exists():
 try:
  b=json.loads(p.read_text());today=datetime.datetime.now(ZoneInfo('Australia/Sydney')).date().isoformat();day=b.get('dayReserved',0) if b.get('day')==today else 0;month=b.get('monthReserved',0) if b.get('month')==today[:7] else 0;metrics.update(dayReserved=day,monthReserved=month,failedProviderCalls=b.get('failedCalls'))
  if day>=int(os.getenv('AI_DAILY_TOKENS','500000'))*.9:alerts.append('DAILY_BUDGET_90_PERCENT')
  if month>=int(os.getenv('AI_MONTHLY_TOKENS','2000000'))*.9:alerts.append('MONTHLY_BUDGET_90_PERCENT')
 except Exception:alerts.append('BUDGET_LEDGER_UNREADABLE')
try:
 previous=json.loads((ops/'status.json').read_text()) if (ops/'status.json').exists() else {}
 if not isinstance(previous,dict):raise ValueError()
except (OSError,ValueError):
 previous={};alerts.append('MONITOR_STATUS_UNREADABLE')
def count(value):
 return value if isinstance(value,int) and value>=0 else 0
if count(metrics.get('failedProviderCalls'))-count(previous.get('failedProviderCalls'))>=5:alerts.append('PROVIDER_ERROR_SPIKE')
metrics['alerts']=alerts
temporary=ops/'status.json.part'
temporary.write_text(json.dumps(metrics,indent=2))
temporary.replace(ops/'status.json')
if previous.get('alerts')!=alerts:
 with (ops/'alerts.jsonl').open('a') as f:f.write(json.dumps(metrics)+'\n')
print(json.dumps(metrics))
