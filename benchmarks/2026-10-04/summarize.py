import json,csv,math,statistics,hashlib
from pathlib import Path
p=Path('outputs')
def load(n):return [json.loads(x) for x in (p/n).read_text(encoding='utf8').splitlines()]
def wilson(k,n):
 z=1.95996398454;d=1+z*z/n;c=(k/n+z*z/(2*n))/d;m=z*math.sqrt(k/n*(1-k/n)/n+z*z/(4*n*n))/d;return [max(0,c-m),min(1,c+m)]
def pct(v,q):return sorted(v)[max(0,math.ceil(len(v)*q)-1)] if v else None
def summarize(rows):
 calls=[c for r in rows for c in r['providerCalls']];m=[r['metrics'] for r in rows];k=sum(r['completed'] for r in rows);lat=[c['latencyMs'] for c in calls];tokens=[x['totalTokens'] for x in m];fault=sum(x['injectedFaults'] for x in m)
 return dict(cases=len(rows),completed=k,completionRate=k/len(rows),wilson95=wilson(k,len(rows)),actionSteps=sum(x['actionSteps'] for x in m),rejectedActions=sum(x['invalidActions'] for x in m),injectedFaults=fault,recoveredFaults=sum(x['recoveredFaults'] for x in m),providerCalls=len(calls),providerSuccess=sum(c['success'] for c in calls),providerLatencyMeanMs=statistics.mean(lat),providerLatencyP50Ms=pct(lat,.5),providerLatencyP95Ms=pct(lat,.95),totalTokens=sum(tokens) if all(t is not None for t in tokens) else None,taskWallMeanMs=statistics.mean(r['measuredWallMs'] for r in rows),taskWallP95Ms=pct([r['measuredWallMs'] for r in rows],.95))
base=load('resume-benchmark-baseline.jsonl');live=load('resume-benchmark-gpt-6-luna.jsonl');first=load('resume-benchmark-baseline-first.jsonl')
summary={'date':'2026-10-04','seedsBaseline':[11+12*i for i in range(20)],'seedsLive':[11,23,35],'model':'gpt-6-luna','provider':'openai','limits':{'decisions':100,'wallSeconds':180,'questWorldTurns':60},'baseline':summarize(base),'live':summarize(live),'liveByScenario':{s:summarize([r for r in live if r['scenario']==s]) for s in sorted(set(r['scenario'] for r in live))},'baselineReproducible':sum(x['initialFingerprint']==y['initialFingerprint'] and x['finalFingerprint']==y['finalFingerprint'] for x,y in zip(base,first)),'pairedInitialFingerprintMatch':sum(r['initialFingerprint']==next(b for b in base if b['scenario']==r['scenario'] and b['seed']==r['seed'])['initialFingerprint'] for r in live)}
(p/'resume-benchmark-summary.json').write_text(json.dumps(summary,indent=2),encoding='utf8')
with (p/'resume-benchmark-cases.csv').open('w',encoding='utf-8-sig',newline='') as f:
 w=csv.writer(f);w.writerow(['model','scenario','seed','status','actions','rejected','modelCalls','tokens','wallMs'])
 for r in base+live:w.writerow([r['model'],r['scenario'],r['seed'],r['status'],r['metrics']['actionSteps'],r['metrics']['invalidActions'],r['metrics']['providerCalls'],r['metrics']['totalTokens'],round(r['measuredWallMs'],2)])
print(json.dumps(summary,indent=2))
