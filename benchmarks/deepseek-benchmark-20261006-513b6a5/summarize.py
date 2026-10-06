import json
import math
from collections import Counter
from pathlib import Path

root = Path(__file__).resolve().parent / 'results'
data = json.loads((root / 'matrix.json').read_text(encoding='utf-8'))
ledger = json.loads((root / 'ai-budget.json').read_text(encoding='utf-8'))
rows = data['cases']
paid = [r for r in rows if r['model'] == 'deepseek-flash']
baseline = [r for r in rows if r['model'] == 'baseline']
metered = []
case_usage = []
for row in paid:
    raw = json.loads((root / f"{row['scenario']}-{row['seed']}-deepseek-flash.json").read_text(encoding='utf-8'))
    calls = [c for c in raw['providerCalls'] if c['usage'].get('totalTokens', 0) > 0]
    metered.extend(calls)
    case_usage.append({'scenario': row['scenario'], 'seed': row['seed'],
                       'meteredCalls': len(calls), 'meteredTokens': sum(c['usage']['totalTokens'] for c in calls)})
latencies = sorted(c['latencyMs'] for c in metered)
assert len(metered) == ledger['calls']
assert sum(c['usage']['totalTokens'] for c in metered) == ledger['actualInputTokens'] + ledger['actualOutputTokens'] <= 300000
assert len(rows) == 36 and len(paid) == 18 and len(baseline) == 18
groups = {}
for scenario in ['shared', 'competition', 'fault', 'battle', 'forest', 'information']:
    group = [r for r in paid if r['scenario'] == scenario]
    groups[scenario] = {
        'reported': len(group), 'completed': sum(r['completed'] for r in group),
        'statuses': dict(Counter(r['status'] for r in group)),
        'errors': dict(Counter(r.get('errorCode') or 'NONE' for r in group)),
        'cases': [{k: r.get(k) for k in ['seed', 'status', 'errorCode', 'elapsedMs', 'metrics']} for r in group],
    }
summary = {
    'revision': data['codeRevision'], 'authorizedTokenCeiling': 300000,
    'plannedPaid': 18, 'paidReported': len(paid),
    'paidCompleted': sum(r['completed'] for r in paid),
    'baselineCompleted': sum(r['completed'] for r in baseline),
    'baselineReported': len(baseline), 'scenarios': groups,
    'paidOutcomes': dict(Counter(r.get('errorCode') or r['status'] for r in paid)),
    'paidCasesWithActualCalls': sum(c['meteredCalls'] > 0 for c in case_usage),
    'caseUsage': case_usage,
    'decisionLatencyIncludingPacingMs': {'mean': sum(latencies)/len(latencies), 'p95NearestRank': latencies[math.ceil(.95*len(latencies))-1]},
    'providerLedger': ledger,
    'actualTokens': ledger['actualInputTokens'] + ledger['actualOutputTokens'],
    'cost': None, 'costReason': 'PRICE_NOT_CONFIGURED',
    'note': 'Budget-blocked and timed-out cases are not passes; battle completion and victory are separate metrics.',
}
(root.parent / 'summary.json').write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding='utf-8')
print(json.dumps(summary, indent=2, ensure_ascii=False))
