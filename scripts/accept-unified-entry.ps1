param([string]$BaseUrl='http://127.0.0.1:8088',[string]$ReportDir='outputs/unified-entry-acceptance')
$ErrorActionPreference='Stop'
if($BaseUrl -notmatch '^http://127\.0\.0\.1:[0-9]{1,5}$'){throw 'Requires local server.'}
New-Item -ItemType Directory -Force $ReportDir | Out-Null
$taskReport=@{status='NOT_PASSED';usesModel=$false;checkedAtUtc=[DateTime]::UtcNow.ToString('o')}
$taskReport | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 "$ReportDir/report.json"
$taskSession=Invoke-RestMethod "$BaseUrl/api/quest/session" -SessionVariable taskWebSession
$taskHeaders=@{Origin=$BaseUrl;'X-CSRF-Token'=$taskSession.csrfToken}
function Open-Room([string]$mode) {
    $room=Invoke-RestMethod "$BaseUrl/api/$mode/rooms" -Method Post -WebSession $taskWebSession -Headers $taskHeaders -ContentType 'application/json' -Body '{}'
    return "$BaseUrl/api/$mode/rooms/$($room.roomId)"
}
function Get-View([string]$url) { Invoke-RestMethod "$url/snapshot" -WebSession $taskWebSession }
function Send-Command([string]$url,[string]$kind,$parameters) {
    $view=Get-View $url
    $body=@{requestId=[Guid]::NewGuid().ToString();taskId=$view.taskId;expectedRevision=$view.revision;command=$kind;params=$parameters} | ConvertTo-Json -Depth 10 -Compress
    Invoke-RestMethod "$url/commands" -Method Post -WebSession $taskWebSession -Headers $taskHeaders -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) | Out-Null
}
$quest=Open-Room 'quest'
Send-Command $quest 'MANUAL' @{action='wait';arguments=@{}}
Send-Command $quest 'SWITCH_MODE' @{mode='training'}
$training=Open-Room 'training'
Send-Command $training 'MANUAL' @{action='move';arguments=@{direction='E'}}
Send-Command $training 'MANUAL' @{action='capture';arguments=@{targetId='wild-treecko'}}
$battle=Get-View $training
if([int]$battle.world.turn -ne 2 -or @($battle.captured).Count -ne 1 -or $battle.captured[0] -ne 'wild-treecko'){throw 'Actual capture failed.'}
Send-Command $training 'SWITCH_MODE' @{mode='quest'}
$null=Open-Room 'quest'
$original=Get-View $quest
if([int]$original.world.turn -ne 1 -or [int]$original.world.coins -ne 5){throw 'Quest state was not preserved.'}
$taskReport.status='PASSED';$taskReport.quest=@{turn=[int]$original.world.turn;coins=[int]$original.world.coins};$taskReport.training=@{turn=[int]$battle.world.turn;captured=@($battle.captured)}
$taskReport | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 "$ReportDir/report.json"
Write-Output 'PASSED: unified entry, separate worlds and actual capture; no model calls. Restart is covered separately.'
