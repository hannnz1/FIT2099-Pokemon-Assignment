"""Summarize synthetic benchmark evidence without pretending unknown usage or cost is zero."""
import json
from collections import Counter
from pathlib import Path

def read(path):
    return json.loads(path.read_text(encoding="utf-8"))

def matrix(folder, cap, version):
    reports=[read(p) for p in sorted((folder/"results").glob("*.json")) if p.name not in {"ai-budget.json","matrix.json"} and not p.name.endswith("-usage.json")]
    paid=[r for r in reports if r.get("model")=="deepseek-flash"]
    baseline=[r for r in reports if r.get("model")=="baseline"]
    meter=read(folder/"results"/"ai-budget.json") if (folder/"results"/"ai-budget.json").exists() else {}
    tokens=meter.get("actualInputTokens",0)+meter.get("actualOutputTokens",0) if meter else None
    latency=sorted(c["latencyMs"] for r in paid for c in r.get("providerCalls",[]) if isinstance(c.get("latencyMs"),(int,float)))
    quantile=lambda q:latency[min(len(latency)-1,int((len(latency)-1)*q))] if latency else None
    summary={"codeIdentity":version,"model":"deepseek-flash","syntheticOnly":True,"seeds":[11,23,37],"privateTokenCap":cap,
      "plannedPaidCases":18,"reportedPaidCases":len(paid),"allPlannedCasesReported":len(paid)==18,
      "completedPaidCases":sum(r.get("completed") is True for r in paid),"baselineCompleted":sum(r.get("completed") is True for r in baseline),
      "outcomes":dict(Counter(r.get("errorCode") or r.get("status") for r in paid)),
      "knownTokens":tokens,"protectedHttpSuccessfulCalls":meter.get("successfulCalls"),"protectedHttpFailedCalls":meter.get("failedCalls"),
      "budgetWithinCap":tokens is not None and tokens<=cap and meter.get("dayReserved",cap+1)<=cap,
      "providerLatencyP50Ms":quantile(.5),"providerLatencyP95Ms":quantile(.95),"latencyIncludesClientPacing":True,
      "cost":None,"costReason":"PRICE_NOT_CONFIGURED", "scenarios":[]}
    for name in ["forest","shared","competition","information","fault","battle"]:
        rows=[r for r in paid if r.get("scenario")==name]
        summary["scenarios"].append({"scenario":name,"cases":len(rows),"completed":sum(r.get("completed") is True for r in rows),
         "results":[{"seed":r.get("seed"),"status":r.get("status"),"reason":r.get("errorCode"),"tokens":r.get("metrics",{}).get("totalTokens"),"usageComplete":r.get("metrics",{}).get("usageComplete")} for r in rows]})
    summary["traceProviderLabels"]=sorted({str(t.get("provider")) for r in paid for t in r.get("trace",[]) if t.get("provider")})
    if "openai" in summary["traceProviderLabels"]:
        summary["traceProviderLabelWarning"]="Frozen adapter traces used openai; model and official wire identify DeepSeek. Subsequent implementation corrects provider attribution; original reports are unchanged."
    (folder/"summary.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding="utf-8")
    return summary

if __name__=="__main__":
    import sys
    print(json.dumps(matrix(Path(sys.argv[1]),int(sys.argv[2]),sys.argv[3]),ensure_ascii=False,indent=2))
