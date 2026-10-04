param([string]$BaseUrl='http://127.0.0.1:8088',[string]$ReportDir='outputs/pokemon-follow-acceptance')
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
Send-Command $quest 'SUMMON' @{captureId=$id;direction='E'}
if((Get-View $quest).summoned.following){throw 'Following should default off.'}
$payload=New-Command $quest 'FOLLOW' @{captureId=$id;enabled=$true}
Send-Payload $quest $payload
Send-Payload $quest $payload
$before=Get-View $quest
if(-not $before.summoned.following -or [int]$before.world.turn -ne 0){throw 'Toggle changed world turn or did not enable.'}
Send-Command $quest 'MANUAL' @{action='move';arguments=@{direction='W'}}
$followed=Get-View $quest
$distance=[Math]::Max([Math]::Abs([int]$followed.summoned.x-[int]$before.summoned.x),[Math]::Abs([int]$followed.summoned.y-[int]$before.summoned.y))
$gap=[Math]::Max([Math]::Abs([int]$followed.summoned.x-[int]$followed.world.x),[Math]::Abs([int]$followed.summoned.y-[int]$followed.world.y))
if($distance -ne 1 -or $gap -ne 1 -or [int]$followed.world.turn -ne 1 -or [int]$followed.world.coins -ne 5 -or [int]$followed.summoned.hp -ne 100 -or $followed.collection[0].x -ne $followed.summoned.x -or $followed.collection[0].y -ne $followed.summoned.y){throw 'Actual one-step follow or resources not proven.'}
Send-Command $quest 'FOLLOW' @{captureId=$id;enabled=$false}
Send-Command $quest 'MANUAL' @{action='move';arguments=@{direction='W'}}
$stopped=Get-View $quest
if($stopped.summoned.following -or $stopped.summoned.x -ne $followed.summoned.x -or $stopped.summoned.y -ne $followed.summoned.y -or [int]$stopped.world.turn -ne 2){throw 'Stop did not preserve actual position.'}
Send-Command $quest 'FOLLOW' @{captureId=$id;enabled=$true}
Send-Command $quest 'MANUAL' @{action='wait';arguments=@{}}
$waited=Get-View $quest
if([int]$waited.world.turn -ne 3 -or $waited.summoned.x -eq $stopped.summoned.x -and $waited.summoned.y -eq $stopped.summoned.y){throw 'World wait did not allow one follow step.'}
Send-Command $quest 'RESET' @{}
$reset=Get-View $quest
if($null -ne $reset.summoned -or @($reset.collection).Count -ne 1 -or $reset.collection[0].state -ne 'IN_BALL'){throw 'Reset did not recall follower and preserve ownership.'}
$taskReport.status='PASSED';$taskReport.followed=$followed.summoned;$taskReport.stopped=$stopped.summoned;$taskReport.waited=$waited.summoned;$taskReport.actualFollowAndToggle='PASSED';$taskReport.duplicateAndReset='PASSED';$taskReport.resources=@{turn=3;coins=5;hp=100};$taskReport.restart='Covered separately by real file/PostgreSQL tests and packaged browser acceptance'
$taskReport | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 "$ReportDir/report.json"
Write-Output 'PASSED: opt-in real one-step follow, stationary stop, world wait, duplicate toggle, reset and resource preservation; no model calls.'
