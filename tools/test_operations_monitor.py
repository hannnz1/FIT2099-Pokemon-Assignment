import unittest, tempfile, runpy, json, contextlib, io, datetime
from pathlib import Path
from unittest.mock import patch
from types import SimpleNamespace
class MonitorTests(unittest.TestCase):
 def run_monitor(self, health=None, state=None, budget=None, previous=None, disk=4*1024**3, backup=True):
  with tempfile.TemporaryDirectory() as tmp:
   base=Path(tmp);ops=base/'operations';ops.mkdir();(ops/'backups').mkdir();(base/'agent-saves').mkdir()
   if backup:(ops/'backups'/'game-fixture.tar.gz').write_bytes(b'fixture')
   if previous is not None:(ops/'status.json').write_text(previous)
   if budget is not None:(base/'agent-saves'/'ai-budget.json').write_text(json.dumps(budget))
   class Response:
    status=200
    def __enter__(self):
     data=io.StringIO(json.dumps(health or {}));data.status=200;return data
    def __exit__(self,*args):pass
   with patch('pathlib.Path',side_effect=lambda value:base if str(value)=='/home/ubuntu/pokemon-demo' else Path(value)),patch('subprocess.check_output',return_value=json.dumps(state or {'Running':True,'Paused':False})),patch('urllib.request.urlopen',return_value=Response()),patch('shutil.disk_usage',return_value=SimpleNamespace(free=disk)),contextlib.redirect_stdout(io.StringIO()):runpy.run_path('deploy/tencent/operations/monitor.py')
   return json.loads((ops/'status.json').read_text())
 def test_clean_status(self):self.assertEqual([],self.run_monitor()['alerts'])
 def test_unknown_usage_and_corrupt_previous_are_alerted_not_crash(self):
  d=self.run_monitor(budget={'failedCalls':None},previous='{broken');self.assertIn('MONITOR_STATUS_UNREADABLE',d['alerts'])
 def test_failure_storage_queue_disk_backup_alerts(self):
  d=self.run_monitor(health={'storageErrors':1,'modelQueue':12,'httpQueue':24},state={'Running':False,'Paused':True},disk=1,backup=False)
  self.assertTrue(set(['GAME_NOT_RUNNING','GAME_PAUSED','GAME_STORAGE_ERROR','MODEL_QUEUE_PRESSURE','HTTP_QUEUE_PRESSURE','DISK_SPACE_LOW','BACKUP_STALE']).issubset(d['alerts']))
 def test_budget_thresholds_and_provider_spike(self):
  from zoneinfo import ZoneInfo
  today=datetime.datetime.now(ZoneInfo('Australia/Sydney')).date().isoformat()
  d=self.run_monitor(budget={'day':today,'month':today[:7],'dayReserved':450000,'monthReserved':1800000,'failedCalls':8},previous=json.dumps({'failedProviderCalls':3,'alerts':[]}))
  self.assertTrue(set(['DAILY_BUDGET_90_PERCENT','MONTHLY_BUDGET_90_PERCENT','PROVIDER_ERROR_SPIKE']).issubset(d['alerts']))
 def test_expired_budget_day_does_not_alert_new_day(self):
  d=self.run_monitor(budget={'day':'2000-01-01','month':'2000-01','dayReserved':500000,'monthReserved':2000000,'failedCalls':None},previous=json.dumps({'failedProviderCalls':None,'alerts':[]}))
  self.assertNotIn('DAILY_BUDGET_90_PERCENT',d['alerts']);self.assertNotIn('MONTHLY_BUDGET_90_PERCENT',d['alerts']);self.assertNotIn('PROVIDER_ERROR_SPIKE',d['alerts'])
if __name__=='__main__':unittest.main()
