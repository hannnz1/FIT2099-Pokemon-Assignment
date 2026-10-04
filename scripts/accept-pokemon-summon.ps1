param([string]$BaseUrl='http://127.0.0.1:8088',[string]$ReportDir='outputs/pokemon-summon-acceptance')
$ErrorActionPreference='Stop'
if($BaseUrl -notmatch '^http://127\.0\.0\.1:[0-9]{1,5}$'){throw 'Requires local server.'}
New-Item -ItemType Directory -Force $ReportDir | Out-Null
$taskReport=@{status='NOT_PASSED';usesModel=$false;checkedAtUtc=[DateTime]::UtcNow.ToString('o')}
$taskReport | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 "$ReportDir/report.json"
$taskSession=Invoke-RestMethod "$BaseUrl/api/training/session" -SessionVariable taskWebSession
$taskHeaders=@{Origin=$BaseUrl;'X-CSRF-Token'=$taskSession.csrfToken}
function Open-Room([string]$mode) {
    $room=Invoke-RestMethod "$BaseUrl/api/$mode/rooms" -Method Post -WebSession $taskWebSession -Headers $taskHeaders -ContentType 'application/json' -Body '{}'
    return "$BaseUrl/api/$mode/rooms/$($room.roomId)"
}
function Get-View([string]$url) {Invoke-RestMethod "$url/snapshot" -WebSession $taskWebSession}
function New-Command([string]$url,[string]$kind,$parameters) {
    $view=Get-View $url
    return (@{requestId=[Guid]::NewGuid().ToString();taskId=$view.taskId;expectedRevision=$view.revision;command=$kind;params=$parameters} | ConvertTo-Json -Depth 10 -Compress)
}
function Send-Payload([string]$url,[string]$body) {Invoke-RestMethod "$url/commands" -Method Post -WebSession $taskWebSession -Headers $taskHeaders -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) | Out-Null}
function Send-Command([string]$url,[string]$kind,$parameters) {Send-Payload $url (New-Command $url $kind $parameters)}
$training=Open-Room 'training'
Send-Command $training 'MANUAL' @{action='move';arguments=@{direction='E'}}
Send-Command $training 'MANUAL' @{action='capture';arguments=@{targetId='wild-treecko'}}
Send-Command $training 'TRANSFER' @{targetId='wild-treecko'}
$quest=Open-Room 'quest'
$id=(Get-View $quest).collection[0].captureId
$payload=New-Command $quest 'SUMMON' @{captureId=$id;direction='E'}
Send-Payload $quest $payload
Send-Payload $quest $payload
$active=Get-View $quest
if($active.summoned.captureId -ne $id -or [int]$active.summoned.x -ne 30 -or [int]$active.summoned.y -ne 10 -or [int]$active.summoned.hp -ne 100 -or [int]$active.world.turn -ne 0 -or [int]$active.world.coins -ne 5 -or $active.collection[0].state -ne 'DEPLOYED'){throw 'Actual deployment or unchanged resources not proven.'}
$blocked=$false
try {Send-Command $quest 'MANUAL' @{action='move';arguments=@{direction='E'}}} catch {if([int]$_.Exception.Response.StatusCode -ne 409){throw};$blocked=$true}
if(-not $blocked -or [int](Get-View $quest).world.turn -ne 0){throw 'Actor occupancy not proven.'}
Send-Command $quest 'RECALL' @{captureId=$id}
$recalled=Get-View $quest
if($null -ne $recalled.summoned -or $recalled.collection[0].state -ne 'IN_BALL'){throw 'Recall not proven.'}
Send-Command $quest 'MANUAL' @{action='move';arguments=@{direction='E'}}
$moved=Get-View $quest
if([int]$moved.world.x -ne 30 -or [int]$moved.world.turn -ne 1){throw 'Tile not released.'}
Send-Command $quest 'SUMMON' @{captureId=$id;direction='E'}
Send-Command $quest 'RESET' @{}
$reset=Get-View $quest
if($null -ne $reset.summoned -or @($reset.collection).Count -ne 1 -or $reset.collection[0].state -ne 'IN_BALL'){throw 'Reset did not recall and preserve Pokemon.'}
$taskReport.status='PASSED';$taskReport.deployed=$active.summoned;$taskReport.resources=@{turn=0;coins=5};$taskReport.actualOccupancyAndRecall='PASSED';$taskReport.duplicateAndReset='PASSED';$taskReport.restart='Covered separately by file/PostgreSQL automated tests and packaged browser acceptance'
$taskReport | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 "$ReportDir/report.json"
Write-Output 'PASSED: actual summon, duplicate receipt, tile occupancy, recall, reset and resource preservation; no model calls.'
