param(
    [string]$JarPath = (Join-Path (Split-Path $PSScriptRoot -Parent) 'pokemon-openai-acceptance.jar'),
    [string]$OutputDirectory = (Join-Path (Split-Path $PSScriptRoot -Parent) 'acceptance-results')
)
$ErrorActionPreference='Stop'
$OutputEncoding=[Text.UTF8Encoding]::new($false)
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
[ordered]@{provider='openai';status='NOT_PASSED';completed=$false;checkedAtUtc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputDirectory 'openai-acceptance.json') -Encoding UTF8
if([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY)){throw 'OPENAI_API_KEY is missing. Configure it locally; do not paste it into chat.'}
if(!(Test-Path -LiteralPath $JarPath)){throw 'Acceptance JAR was not found.'}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$taskPreviousSave=$env:AGENT_SAVE_DIR
$taskPreviousStorage=$env:AGENT_STORAGE
try {
    $env:AGENT_STORAGE='file'
    $env:AGENT_SAVE_DIR=Join-Path ([IO.Path]::GetTempPath()) ('pokemon-openai-acceptance-'+[Guid]::NewGuid().ToString('N'))
    $taskGoal='帮我收集3个树果交给博士，不要花金币，不要主动战斗，只在任务区域活动，在第40回合之前完成。'
    $taskQuest=@($taskGoal,'y','n','n','n','n','n') | & java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp $JarPath game.agent.demo.OpenAiAgentConsole 2>&1
    $taskQuestExit=$LASTEXITCODE
    $taskQuestText=$taskQuest -join "`n"
    $taskQuest | Set-Content -LiteralPath (Join-Path $OutputDirectory 'openai-quest.log') -Encoding UTF8
    if($taskQuestExit -ne 0 -or $taskQuestText -notmatch 'QUEST_COMPLETED' -or $taskQuestText -notmatch '实际交付 3 个树果；剩余金币 5' -or $taskQuestText -notmatch 'NO_SPENDING' -or $taskQuestText -notmatch 'NO_ACTIVE_BATTLE' -or $taskQuestText -notmatch 'QUEST_AREA' -or $taskQuestText -notmatch 'DEADLINE' -or $taskQuestText -notmatch 'AREA_RESTRICTED' -or $taskQuestText -notmatch '允许区域：QUEST_AREA；截止回合：40。'){
        throw 'Live OpenAI quest acceptance failed; inspect openai-quest.log. No scripted fallback was used.'
    }
    $taskBattle=& java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp $JarPath game.agent.combat.BattleTrainingConsole --live --new --no-battle 2>&1
    $taskBattleExit=$LASTEXITCODE
    $taskBattleText=$taskBattle -join "`n"
    $taskBattle | Set-Content -LiteralPath (Join-Path $OutputDirectory 'openai-capture.log') -Encoding UTF8
    if($taskBattleExit -ne 0 -or $taskBattleText -notmatch 'LIVE_RESULT COMPLETED captured=1' -or $taskBattleText -notmatch 'SUCCESS:CAPTURED'){
        throw 'Live OpenAI capture acceptance failed; inspect openai-capture.log. No scripted fallback was used.'
    }
    $taskModel=if([string]::IsNullOrWhiteSpace($env:OPENAI_MODEL)){'gpt-6-luna'}else{$env:OPENAI_MODEL}
    $taskReport=[ordered]@{provider='openai';status='PASSED';model=$taskModel;credentialSource='local OPENAI_API_KEY environment variable';endpoint='https://api.openai.com/v1/responses';checkedAtUtc=[DateTime]::UtcNow.ToString('o');live=$true;scriptedFallback=$false;quest=[ordered]@{completed=$true;delivered=3;coins=5;constraints=@('NO_SPENDING','NO_ACTIVE_BATTLE','AREA_RESTRICTED','DEADLINE');areaId='QUEST_AREA';deadlineTurn=40};capture=[ordered]@{completed=$true;captured=1;noActiveBattle=$true};quotaAndTimeout='covered by deterministic integration tests, not induced on the live account'}
    $taskReport | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'openai-acceptance.json') -Encoding UTF8
    Write-Output 'OPENAI_LIVE_ACCEPTANCE_PASSED: quest delivered=3 coins=5; captured=1; no scripted fallback.'
} finally {
    $env:AGENT_SAVE_DIR=$taskPreviousSave
    $env:AGENT_STORAGE=$taskPreviousStorage
}
