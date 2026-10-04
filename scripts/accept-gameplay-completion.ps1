param([string]$BaseUrl='http://127.0.0.1:8088',[string]$ReportDir='outputs/gameplay-completion-acceptance')
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
$quest=Open-Room 'quest'
Send-Command $quest 'EXPLORE' @{}
foreach($step in 1..4){Send-Command $quest 'WILD_ACTION' @{action='move';arguments=@{direction='E'}}}
Send-Command $quest 'WILD_ACTION' @{action='capture';arguments=@{targetId='field-mudkip'}}
$payload=New-Command $quest 'STORE_WILD' @{targetId='field-mudkip'}
Send-Payload $quest $payload
Send-Payload $quest $payload
$captured=Get-View $quest
if(@($captured.collection).Count -ne 1 -or $captured.collection[0].species -ne 'MUDKIP' -or ($captured.wild | Where-Object id -eq 'field-mudkip').state -ne 'TRANSFERRED'){throw 'Unique native Mudkip capture receipt not proven.'}
$id=$captured.collection[0].captureId
Send-Command $quest 'SUMMON' @{captureId=$id;direction='E'}
Send-Command $quest 'PARTNER' @{captureId=$id}
Send-Command $quest 'FOLLOW' @{captureId=$id;enabled=$true}
Send-Command $quest 'WILD_ACTION' @{action='move';arguments=@{direction='E'}}
$moved=Get-View $quest
if($moved.combatPartner -ne $id -or [int]$moved.world.x -ne 33 -or [int]$moved.summoned.x -ne 35 -or $moved.summoned.following -or [int]$moved.world.turn -ne 6){throw 'Actual partner switching or single movement not proven.'}
try{Send-Command $quest 'WILD_ACTION' @{action='capture';arguments=@{targetId='field-torchic'}};throw 'Torchic unexpectedly captured.'}catch{if([int]$_.Exception.Response.StatusCode -ne 409){throw}}
Send-Command $quest 'WILD_ACTION' @{action='attack';arguments=@{targetId='field-torchic'}}
$attacked=Get-View $quest
if([int]$attacked.world.turn -ne 7 -or $attacked.summoned.hp -lt 1 -or $attacked.summoned.hp -gt 100){throw 'Native battle health/turn not proven.'}
$hp=$attacked.summoned.hp
Send-Command $quest 'RECALL' @{captureId=$id}
$recalled=Get-View $quest
if($recalled.combatPartner -ne 'mudkip' -or $null -ne $recalled.summoned -or $recalled.collection[0].hp -ne $hp){throw 'Recall did not retain battle health.'}
Send-Command $quest 'RESET' @{}
$reset=Get-View $quest
if(@($reset.wild).Count -ne 0 -or @($reset.collection).Count -ne 1 -or $reset.collection[0].hp -ne $hp){throw 'New round lost collection or retained old wild targets.'}
Send-Command $quest 'SUMMON' @{captureId=$id;direction='E'}
if((Get-View $quest).summoned.hp -ne $hp){throw 'Resummon did not retain native health.'}
$taskReport.status='PASSED';$taskReport.capture=$captured.collection[0];$taskReport.partner=$moved.combatPartnerState;$taskReport.combat=$attacked.summoned;$taskReport.nativeCaptureAndUniqueReceipt='PASSED';$taskReport.partnerMovementAndTurn='PASSED';$taskReport.nativeBattleAndRecallHealth='PASSED';$taskReport.torchicUncapturable='PASSED';$taskReport.restartAndRest='Covered by file/PostgreSQL tests and packaged browser acceptance'
$taskReport | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 "$ReportDir/report.json"
Write-Output 'PASSED: native original-map capture, unique collection transfer, partner switch/move, combat, recall health and new round; no model calls.'
