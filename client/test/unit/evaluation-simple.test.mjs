import test from 'node:test';import assert from 'node:assert/strict';
import {evaluationConfig,resultSummary,scenarioResults,caseOutcome,caseExplanation} from '../../agent/evaluation-view.mjs';
test('quick and stability presets produce six and eighteen matched cases',()=>{
 const draft={models:['baseline'],scenarios:['forest','shared','competition','information','fault','battle'],starter:'MUDKIP'};
 assert.equal(evaluationConfig(draft,'quick').count,6);const stable=evaluationConfig(draft,'stable');assert.equal(stable.count,18);assert.deepEqual(stable.params.seeds,[11,23,37]);assert.equal(stable.params.repetitions,1);
});
test('custom parameters enforce integer bounds and 24 cases',()=>{
 const draft={models:['baseline'],scenarios:['forest'],starter:'TREECKO',seeds:'11,23',repetitions:2,decisions:100,seconds:180,prices:'{}'};
 assert.equal(evaluationConfig(draft,'custom').count,4);assert.throws(()=>evaluationConfig({...draft,repetitions:1.5},'custom'));assert.throws(()=>evaluationConfig({...draft,scenarios:['forest','shared','competition','information','fault','battle'],repetitions:3},'custom'));
});
test('pending and cancellation are not failures, battle completion is not victory',()=>{
 const rows=[{scenario:'forest',status:'COMPLETED'},{scenario:'shared',status:'RUNNING'},{scenario:'fault',status:'CANCELLED'},{scenario:'battle',status:'COMPLETED',metrics:{battleWin:0,battleResult:'RIGHT_WON'}}];
 assert.deepEqual(resultSummary(rows),{total:4,passed:1,failed:1,cancelled:1,pending:1});assert.equal(caseOutcome(rows[3]).label,'未获胜');
});
test('group results by scenario and model, retain failure evidence without inventing causes',()=>{
 const rows=[{scenario:'competition',model:'baseline',status:'COMPLETED'},{scenario:'competition',model:'baseline',status:'WALL_TIME_LIMIT'},{scenario:'competition',model:'other',status:'FAILED'}];
 const grouped=scenarioResults(rows);assert.equal(grouped.length,2);assert.equal(grouped[0].passed,1);assert.equal(grouped[0].total,2);assert.equal(grouped[0].failures.length,1);
 assert.match(caseExplanation({status:'FAILED'}),/待排查/);assert.match(caseExplanation({status:'WALL_TIME_LIMIT',budgets:{wallMillis:180000}}),/180/);
});

test('public paid tests require confirmation and bounded cases, free defaults remain unchanged',()=>{
 const limits={maxPaidCases:3,maxDecisions:30,maxSeconds:180};const draft={models:['deepseek-flash'],scenarios:['information'],starter:'TREECKO'};
 assert.throws(()=>evaluationConfig(draft,'quick',limits),/确认/);
 const result=evaluationConfig({...draft,paidConfirmed:true},'quick',limits);assert.equal(result.params.decisions,30);assert.equal(result.params.paidConfirmed,true);
 assert.equal(evaluationConfig({...draft,paidConfirmed:true},'stable',limits).count,3);
 assert.throws(()=>evaluationConfig({...draft,paidConfirmed:true,scenarios:['forest','shared','fault','battle']},'quick',limits),/3/);
 assert.throws(()=>evaluationConfig({...draft,paidConfirmed:true,seeds:'11',repetitions:1,decisions:31,seconds:180},'custom',limits),/30/);
 assert.equal(evaluationConfig({...draft,models:['baseline']},'quick',limits).params.decisions,100);
});
