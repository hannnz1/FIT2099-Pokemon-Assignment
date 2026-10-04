param([string]$BaseUrl='http://127.0.0.1:8091',[string]$ReportDir='outputs/browser-battle-acceptance',[ValidateSet('/api/training','/api/agent')][string]$ApiBase='/api/training')
$ErrorActionPreference='Stop'
if($BaseUrl -notmatch '^http://127\.0\.0\.1:[0-9]{1,5}$'){throw 'Acceptance requires the local battle server.'}
New-Item -ItemType Directory -Force $ReportDir | Out-Null
$taskReport=@{status='NOT_PASSED';provider='openai';scriptedFallback=$false;checkedAtUtc=[DateTime]::UtcNow.ToString('o')}
$taskReport | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 "$ReportDir/report.json"
$taskSession=Invoke-RestMethod "$BaseUrl$ApiBase/session" -SessionVariable taskWebSession
$taskHeaders=@{Origin=$BaseUrl;'X-CSRF-Token'=$taskSession.csrfToken}
$taskRoom=Invoke-RestMethod "$BaseUrl$ApiBase/rooms" -Method Post -WebSession $taskWebSession -Headers $taskHeaders -ContentType 'application/json' -Body '{}'
$taskRoomUrl="$BaseUrl$ApiBase/rooms/$($taskRoom.roomId)"
function Get-BattleView { Invoke-RestMethod "$taskRoomUrl/snapshot" -WebSession $taskWebSession }
function Send-BattleCommand([string]$kind,$parameters) {
    $view=Get-BattleView
    $body=@{requestId=[Guid]::NewGuid().ToString();taskId=$view.taskId;expectedRevision=$view.revision;command=$kind;params=$parameters} | ConvertTo-Json -Depth 10 -Compress
    Invoke-RestMethod "$taskRoomUrl/commands" -Method Post -WebSession $taskWebSession -Headers $taskHeaders -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) | Out-Null
}
function Wait-BattleStatus([string]$wanted) {
    $until=[DateTime]::UtcNow.AddSeconds(125)
    do {$view=Get-BattleView;if($view.status -eq $wanted){return $view};if($view.status -in @('ERROR','FAILED','CANCELLED','STORAGE_ERROR','PROVIDER_UNAVAILABLE')){throw "Battle stopped: $($view.status) $($view.errorCode)"};Start-Sleep -Milliseconds 500}while([DateTime]::UtcNow -lt $until)
    throw "Timed out waiting for $wanted"
}
$initial=Get-BattleView
if($initial.mode -ne 'BATTLE_TRAINING' -or $initial.provider -ne 'openai' -or -not $initial.aiAvailable){throw 'Requires a configured real OpenAI battle server.'}
Send-BattleCommand 'PARSE' @{text='捕捉木守宫，不要主动战斗'}
$ready=Wait-BattleStatus 'READY'
if($ready.intent.goal -ne 'CAPTURE' -or $ready.intent.targetId -ne 'wild-treecko' -or -not $ready.intent.noBattle -or [int]$ready.world.turn -ne 0){throw 'Wrong confirmation or pre-confirmation world mutation.'}
Send-BattleCommand 'CONFIRM' @{}
$capture=Wait-BattleStatus 'COMPLETED'
if(-not $capture.goalComplete -or @($capture.captured).Count -ne 1 -or $capture.captured[0] -ne 'wild-treecko' -or [int]$capture.world.actorHp -ne 1000){throw 'Actual peaceful capture not proven.'}
$capture | ConvertTo-Json -Depth 30 | Set-Content -Encoding UTF8 "$ReportDir/capture.json"
Send-BattleCommand 'RESET' @{}
Send-BattleCommand 'PARSE' @{text='击败火稚鸡'}
$ready=Wait-BattleStatus 'READY'
if($ready.intent.goal -ne 'DEFEAT' -or $ready.intent.targetId -ne 'wild-torchic' -or $ready.intent.noBattle){throw 'Wrong defeat confirmation.'}
Send-BattleCommand 'CONFIRM' @{}
$defeat=Wait-BattleStatus 'COMPLETED'
$target=$defeat.targets | Where-Object id -eq 'wild-torchic'
if(-not $defeat.goalComplete -or $target.state -ne 'DEFEATED' -or [int]$target.hp -ne 0 -or @($defeat.captured).Count -ne 0){throw 'Actual defeat not proven.'}
$defeat | ConvertTo-Json -Depth 30 | Set-Content -Encoding UTF8 "$ReportDir/defeat.json"
$taskReport.status='PASSED';$taskReport.model=$defeat.model;$taskReport.capture=@{completed=$true;captured=1;noBattle=$true;turn=[int]$capture.world.turn};$taskReport.defeat=@{completed=$true;targetHp=0;actorHp=[int]$defeat.world.actorHp;turn=[int]$defeat.world.turn}
$taskReport | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 "$ReportDir/report.json"
Write-Output 'PASSED: real OpenAI capture and defeat verified against authoritative game state.'
