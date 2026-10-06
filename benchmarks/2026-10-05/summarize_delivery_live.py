import json, math, pathlib, csv
root=pathlib.Path(__file__).resolve().parent.parent
cases=[json.loads(s) for s in (root/'outputs/delivery-live-20261005-gpt-6-luna.jsonl').read_text(encoding='utf-8').splitlines()]
old=[json.loads(s) for s in (root/'outputs/resume-benchmark-gpt-6-luna.jsonl').read_text(encoding='utf-8').splitlines()]
assert len(cases)==15 and len({(c['scenario'],c['seed']) for c in cases})==15
previous={(c['scenario'],c['seed']):c for c in old}
assert all(c['initialFingerprint']==previous[c['scenario'],c['seed']]['initialFingerprint'] for c in cases)
calls=[p for c in cases for p in c['providerCalls']]
latencies=sorted(p['latencyMs'] for p in calls)
success=sum(c['completed'] for c in cases);n=len(cases);z=1.96;fraction=success/n
center=(fraction+z*z/(2*n))/(1+z*z/n);half=z*math.sqrt(fraction*(1-fraction)/n+z*z/(4*n*n))/(1+z*z/n)
rejections=[t for c in cases for t in c['trace'] if t['actionStatus']=='REJECTED']
summary={'validation':'LIVE_OPENAI_LOCAL_PROJECT','model':'gpt-6-luna','executionPolicyVersion':'berry-delivery-recovery-20261005',
 'cases':n,'completed':success,'completionRate':fraction,'completion95Wilson':[center-half,center+half],
 'matchedInitialFingerprints':15,'providerCalls':len(calls),'successfulProviderCalls':sum(p['success'] for p in calls),
 'tokens':sum(c['metrics']['totalTokens'] for c in cases),'meanProviderLatencyMs':sum(latencies)/len(latencies),
 'p95ProviderLatencyMs':latencies[math.ceil(len(latencies)*.95)-1],
 'rejectedActionCodes':[t['actionResult'] for t in rejections],
 'oldCompleted':sum(c['completed'] for c in old),'oldProviderCalls':sum(c['metrics']['providerCalls'] for c in old),
 'oldTokens':sum(c['metrics']['totalTokens'] for c in old),
 'deepSeekLive':'NOT_RUN_LOCAL_KEY_MISSING','deployment':'LOCAL_ONLY_WEBSITE_UNCHANGED'}
out=root/'outputs/delivery-live-summary-20261005.json';out.write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
with (root/'outputs/delivery-live-cases-20261005.csv').open('w',encoding='utf-8',newline='') as f:
 writer=csv.writer(f);writer.writerow(['scenario','seed','status','worldTurns','providerCalls','tokens','initialFingerprint'])
 for c in cases:writer.writerow([c['scenario'],c['seed'],c['status'],c['metrics']['worldTurns'],c['metrics']['providerCalls'],c['metrics']['totalTokens'],c['initialFingerprint']])
print(json.dumps(summary,ensure_ascii=False,indent=2))
