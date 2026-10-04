param([string]$BaseUrl='http://127.0.0.1:8088',[string]$ReportDir='outputs/pokemon-carry-acceptance')
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
$payload=New-Command $training 'TRANSFER' @{targetId='wild-treecko'}
Send-Payload $training $payload
Send-Payload $training $payload
$source=Get-View $training
if([int]$source.world.turn -ne 2 -or @($source.captured).Count -ne 0 -or @($source.collection).Count -ne 1 -or [int]$source.collection[0].hp -ne 100 -or -not $source.goalComplete){throw 'Unique ownership/HP/completion not proven.'}
Send-Command $training 'RESET' @{}
if(@((Get-View $training).collection).Count -ne 1){throw 'Training reset cleared collection.'}
$quest=Open-Room 'quest'
$destination=Get-View $quest
if([int]$destination.world.turn -ne 0 -or [int]$destination.world.coins -ne 5 -or @($destination.collection).Count -ne 1){throw 'Quest assets or collection wrong.'}
Send-Command $quest 'RESET' @{}
if(@((Get-View $quest).collection).Count -ne 1){throw 'Quest reset cleared collection.'}
$taskReport.status='PASSED';$taskReport.collection=@($destination.collection);$taskReport.source=@{turn=2;captured=0;goalComplete=$true};$taskReport.quest=@{turn=0;coins=5};$taskReport.duplicateAndBothResets='PASSED';$taskReport.restart='Covered separately by automated and browser verification'
$taskReport | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 "$ReportDir/report.json"
Write-Output 'PASSED: actual captured Pokemon carried once, duplicate receipt and scene resets preserve ownership; no model calls.'
