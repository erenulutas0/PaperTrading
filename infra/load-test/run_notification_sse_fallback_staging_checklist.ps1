param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$ReportsDir = "infra/load-test/reports",
  [int]$TimeoutSec = 30,
  [int]$DuplicateWindowSec = 5,
  [switch]$NoFail
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Net.Http

$httpClientHandler = [System.Net.Http.HttpClientHandler]::new()
$httpClient = [System.Net.Http.HttpClient]::new($httpClientHandler)
$httpClient.Timeout = [TimeSpan]::FromSeconds($TimeoutSec)

function Invoke-Request {
  param(
    [string]$Method,
    [string]$Url,
    [hashtable]$Headers = @{},
    [object]$Body = $null
  )

  try {
    $requestMessage = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::$Method, $Url)
    foreach ($key in $Headers.Keys) {
      [void]$requestMessage.Headers.TryAddWithoutValidation($key, [string]$Headers[$key])
    }

    if ($null -ne $Body) {
      $jsonBody = $Body | ConvertTo-Json -Depth 12
      $requestMessage.Content = [System.Net.Http.StringContent]::new($jsonBody, [System.Text.Encoding]::UTF8, "application/json")
    }

    $response = $httpClient.SendAsync($requestMessage).GetAwaiter().GetResult()
    $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $headerMap = @{}
    foreach ($header in $response.Headers) {
      $headerMap[$header.Key] = ($header.Value -join ",")
    }
    foreach ($header in $response.Content.Headers) {
      $headerMap[$header.Key] = ($header.Value -join ",")
    }

    return [pscustomobject]@{
      ok      = $response.IsSuccessStatusCode
      status  = [int]$response.StatusCode
      content = $content
      headers = $headerMap
      error   = ""
    }
  } catch {
    return [pscustomobject]@{
      ok      = $false
      status  = 0
      content = ""
      headers = @{}
      error   = $_.Exception.Message
    }
  }
}

function New-StepResult {
  param(
    [string]$Name,
    [bool]$Passed,
    [string]$Detail
  )

  return [pscustomobject]@{
    Name   = $Name
    Passed = $Passed
    Detail = $Detail
  }
}

function Assert-Condition {
  param(
    [System.Collections.Generic.List[object]]$Results,
    [string]$Name,
    [bool]$Condition,
    [string]$Detail
  )

  $Results.Add((New-StepResult -Name $Name -Passed $Condition -Detail $Detail)) | Out-Null
}

function Get-ObjectPropertyValue {
  param(
    [object]$Object,
    [string]$Name
  )

  if ($null -eq $Object) {
    return $null
  }

  $prop = $Object.PSObject.Properties[$Name]
  if ($null -eq $prop) {
    return $null
  }

  return $prop.Value
}

function Register-User {
  param(
    [string]$BaseUrl,
    [string]$Username,
    [string]$Email
  )

  $register = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/auth/register" -Body @{
    username = $Username
    email    = $Email
    password = "P@ssw0rd!123456"
  }

  $json = if ($register.content) { $register.content | ConvertFrom-Json } else { $null }
  return [pscustomobject]@{
    Response = $register
    Json     = $json
  }
}

function Start-SseListenerJob {
  param(
    [string]$StreamUrl,
    [int]$TimeoutSec
  )

  return Start-Job -ScriptBlock {
    param($InnerStreamUrl, $InnerTimeoutSec)

    Set-StrictMode -Version Latest
    $ErrorActionPreference = "Stop"
    Add-Type -AssemblyName System.Net.Http

    try {
      $handler = [System.Net.Http.HttpClientHandler]::new()
      $client = [System.Net.Http.HttpClient]::new($handler)
      $client.Timeout = [System.Threading.Timeout]::InfiniteTimeSpan

      $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get, $InnerStreamUrl)
      $response = $client.SendAsync(
        $request,
        [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead
      ).GetAwaiter().GetResult()

      [pscustomobject]@{
        type   = "meta"
        status = [int]$response.StatusCode
        at     = (Get-Date).ToString("o")
      }

      $stream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
      $reader = [System.IO.StreamReader]::new($stream)
      $eventName = ""
      $dataLines = New-Object 'System.Collections.Generic.List[string]'
      $deadline = (Get-Date).AddSeconds($InnerTimeoutSec)

      while ((Get-Date) -lt $deadline) {
        $readTask = $reader.ReadLineAsync()
        if (-not $readTask.Wait(1000)) {
          continue
        }

        $line = $readTask.Result
        if ($null -eq $line) {
          break
        }

        if ([string]::IsNullOrWhiteSpace($line)) {
          if (-not [string]::IsNullOrWhiteSpace($eventName) -or $dataLines.Count -gt 0) {
            [pscustomobject]@{
              type  = "event"
              event = $eventName
              data  = ($dataLines -join "`n")
              at    = (Get-Date).ToString("o")
            }
          }
          $eventName = ""
          $dataLines.Clear()
          continue
        }

        if ($line.StartsWith("event:")) {
          $eventName = $line.Substring(6).Trim()
          continue
        }

        if ($line.StartsWith("data:")) {
          $dataLines.Add($line.Substring(5).TrimStart()) | Out-Null
        }
      }
    } catch {
      [pscustomobject]@{
        type    = "error"
        message = $_.Exception.Message
        at      = (Get-Date).ToString("o")
      }
    }
  } -ArgumentList $StreamUrl, $TimeoutSec
}

function Sync-JobOutput {
  param(
    [System.Management.Automation.Job]$Job,
    [System.Collections.Generic.List[object]]$OutputSink,
    [ref]$SeenCount
  )

  $items = @(Receive-Job -Job $Job -Keep)
  for ($i = $SeenCount.Value; $i -lt $items.Count; $i++) {
    $OutputSink.Add($items[$i]) | Out-Null
  }
  $SeenCount.Value = $items.Count
}

function Wait-ForListenerEvent {
  param(
    [System.Management.Automation.Job]$Job,
    [System.Collections.Generic.List[object]]$OutputSink,
    [ref]$SeenCount,
    [scriptblock]$Matcher,
    [int]$TimeoutSec
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    Sync-JobOutput -Job $Job -OutputSink $OutputSink -SeenCount $SeenCount

    foreach ($item in $OutputSink) {
      if ($item.type -eq "error") {
        throw "SSE listener failed: $($item.message)"
      }
    }

    foreach ($item in $OutputSink) {
      $matched = $false
      try {
        $matched = [bool](& $Matcher $item)
      } catch {
        $matched = $false
      }
      if ($matched) {
        return $item
      }
    }

    Start-Sleep -Milliseconds 200
  }

  return $null
}

function Wait-ForApiCondition {
  param(
    [scriptblock]$Probe,
    [scriptblock]$Matcher,
    [int]$TimeoutSec
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    $result = & $Probe
    $matched = $false
    try {
      $matched = [bool](& $Matcher $result)
    } catch {
      $matched = $false
    }
    if ($matched) {
      return $result
    }
    Start-Sleep -Milliseconds 300
  }

  return & $Probe
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$null = New-Item -ItemType Directory -Force -Path $ReportsDir
$reportPath = Join-Path $ReportsDir "notification-sse-fallback-staging-checklist-$timestamp.md"

$results = New-Object 'System.Collections.Generic.List[object]'
$notes = New-Object 'System.Collections.Generic.List[string]'
$listenerJob = $null
$listenerOutput = New-Object 'System.Collections.Generic.List[object]'
$seenOutputCount = 0
$streamToken = ""
$firstNotification = $null

try {
  $health = Invoke-Request -Method "GET" -Url "$BaseUrl/actuator/health"
  Assert-Condition -Results $results -Name "Health" -Condition ($health.status -eq 200) -Detail "status=$($health.status)"

  $receiverRegistration = Register-User -BaseUrl $BaseUrl -Username "notif_receiver_$suffix" -Email "notif_receiver_$suffix@test.com"
  $receiverJson = $receiverRegistration.Json
  $receiverToken = [string](Get-ObjectPropertyValue -Object $receiverJson -Name "accessToken")
  $receiverId = [string](Get-ObjectPropertyValue -Object $receiverJson -Name "id")
  Assert-Condition -Results $results -Name "Register Receiver" -Condition ($receiverRegistration.Response.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($receiverToken)) -Detail "status=$($receiverRegistration.Response.status)"

  $actorRegistration = Register-User -BaseUrl $BaseUrl -Username "notif_actor_$suffix" -Email "notif_actor_$suffix@test.com"
  $actorJson = $actorRegistration.Json
  $actorToken = [string](Get-ObjectPropertyValue -Object $actorJson -Name "accessToken")
  $actorUsername = [string](Get-ObjectPropertyValue -Object $actorJson -Name "username")
  Assert-Condition -Results $results -Name "Register Actor" -Condition ($actorRegistration.Response.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($actorToken)) -Detail "status=$($actorRegistration.Response.status)"

  $tokenResponse = Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/notifications/stream-token" -Headers @{
    "Authorization" = "Bearer $receiverToken"
  }
  $tokenJson = if ($tokenResponse.content) { $tokenResponse.content | ConvertFrom-Json } else { $null }
  $streamToken = [string](Get-ObjectPropertyValue -Object $tokenJson -Name "streamToken")
  $streamTtl = Get-ObjectPropertyValue -Object $tokenJson -Name "expiresInSeconds"
  Assert-Condition -Results $results -Name "Issue Stream Token" -Condition ($tokenResponse.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($streamToken) -and $null -ne $streamTtl) -Detail "status=$($tokenResponse.status), ttl=$streamTtl"

  $streamUrl = "$BaseUrl/api/v1/notifications/stream?streamToken=$([System.Uri]::EscapeDataString($streamToken))"
  $listenerJob = Start-SseListenerJob -StreamUrl $streamUrl -TimeoutSec ($TimeoutSec + $DuplicateWindowSec + 10)

  $streamMeta = Wait-ForListenerEvent -Job $listenerJob -OutputSink $listenerOutput -SeenCount ([ref]$seenOutputCount) -Matcher {
    param($item)
    return $item.type -eq "meta"
  } -TimeoutSec 10
  Assert-Condition -Results $results -Name "Open SSE Stream" -Condition ($null -ne $streamMeta -and $streamMeta.status -eq 200) -Detail "status=$(if ($null -ne $streamMeta) { $streamMeta.status } else { '<none>' })"

  $connectedEvent = Wait-ForListenerEvent -Job $listenerJob -OutputSink $listenerOutput -SeenCount ([ref]$seenOutputCount) -Matcher {
    param($item)
    return $item.type -eq "event" -and $item.event -eq "connected"
  } -TimeoutSec 10
  Assert-Condition -Results $results -Name "Receive Connected Event" -Condition ($null -ne $connectedEvent) -Detail "$(if ($null -ne $connectedEvent) { $connectedEvent.at } else { '<missing>' })"

  $followResponse = Invoke-Request -Method "POST" -Url "$BaseUrl/api/v1/users/$receiverId/follow" -Headers @{
    "Authorization" = "Bearer $actorToken"
  }
  Assert-Condition -Results $results -Name "Trigger Follow Notification" -Condition ($followResponse.status -eq 200) -Detail "status=$($followResponse.status)"

  $notificationEvent = Wait-ForListenerEvent -Job $listenerJob -OutputSink $listenerOutput -SeenCount ([ref]$seenOutputCount) -Matcher {
    param($item)
    return $item.type -eq "event" -and $item.event -eq "notification"
  } -TimeoutSec $TimeoutSec
  if ($null -ne $notificationEvent -and -not [string]::IsNullOrWhiteSpace([string]$notificationEvent.data)) {
    $firstNotification = $notificationEvent.data | ConvertFrom-Json
  }
  $firstNotificationId = [string](Get-ObjectPropertyValue -Object $firstNotification -Name "id")
  $firstNotificationType = [string](Get-ObjectPropertyValue -Object $firstNotification -Name "type")
  $firstNotificationActor = [string](Get-ObjectPropertyValue -Object $firstNotification -Name "actorUsername")
  Assert-Condition -Results $results -Name "Receive Follow Notification Event" -Condition ($null -ne $firstNotification -and -not [string]::IsNullOrWhiteSpace($firstNotificationId) -and $firstNotificationType -eq "FOLLOW" -and $firstNotificationActor -eq $actorUsername) -Detail "id=$firstNotificationId, type=$firstNotificationType, actor=$firstNotificationActor"

  $receiverUnread = Wait-ForApiCondition -Probe {
    Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/notifications/unread-count" -Headers @{
      "Authorization" = "Bearer $receiverToken"
    }
  } -Matcher {
    param($response)
    if ($response.status -ne 200 -or [string]::IsNullOrWhiteSpace($response.content)) {
      return $false
    }
    $json = $response.content | ConvertFrom-Json
    return [int](Get-ObjectPropertyValue -Object $json -Name "count") -eq 1
  } -TimeoutSec 10
  $receiverUnreadJson = if ($receiverUnread.content) { $receiverUnread.content | ConvertFrom-Json } else { $null }
  $receiverUnreadCount = if ($null -ne $receiverUnreadJson) { [int](Get-ObjectPropertyValue -Object $receiverUnreadJson -Name "count") } else { -1 }
  Assert-Condition -Results $results -Name "Unread Count Is One" -Condition ($receiverUnread.status -eq 200 -and $receiverUnreadCount -eq 1) -Detail "status=$($receiverUnread.status), count=$receiverUnreadCount"

  $receiverNotifications = Wait-ForApiCondition -Probe {
    Invoke-Request -Method "GET" -Url "$BaseUrl/api/v1/notifications?size=5" -Headers @{
      "Authorization" = "Bearer $receiverToken"
    }
  } -Matcher {
    param($response)
    if ($response.status -ne 200 -or [string]::IsNullOrWhiteSpace($response.content)) {
      return $false
    }
    $json = $response.content | ConvertFrom-Json
    $content = @(Get-ObjectPropertyValue -Object $json -Name "content")
    foreach ($item in $content) {
      if ([string](Get-ObjectPropertyValue -Object $item -Name "id") -eq $firstNotificationId) {
        return $true
      }
    }
    return $false
  } -TimeoutSec 10
  $receiverNotificationsJson = if ($receiverNotifications.content) { $receiverNotifications.content | ConvertFrom-Json } else { $null }
  $notificationRows = @()
  if ($null -ne $receiverNotificationsJson) {
    $notificationRows = @(Get-ObjectPropertyValue -Object $receiverNotificationsJson -Name "content")
  }
  $matchingRows = @($notificationRows | Where-Object { [string](Get-ObjectPropertyValue -Object $_ -Name "id") -eq $firstNotificationId })
  $matchingRowType = if ($matchingRows.Count -ge 1) { [string](Get-ObjectPropertyValue -Object $matchingRows[0] -Name "type") } else { "" }
  Assert-Condition -Results $results -Name "Notification List Contains Event Once" -Condition ($receiverNotifications.status -eq 200 -and $matchingRows.Count -eq 1 -and $matchingRowType -eq "FOLLOW") -Detail "status=$($receiverNotifications.status), matches=$($matchingRows.Count), type=$matchingRowType"

  $duplicateDeadline = (Get-Date).AddSeconds($DuplicateWindowSec)
  while ((Get-Date) -lt $duplicateDeadline) {
    Sync-JobOutput -Job $listenerJob -OutputSink $listenerOutput -SeenCount ([ref]$seenOutputCount)
    Start-Sleep -Milliseconds 200
  }

  $notificationEvents = @($listenerOutput | Where-Object { $_.type -eq "event" -and $_.event -eq "notification" })
  $matchingNotificationEvents = @()
  foreach ($event in $notificationEvents) {
    if ([string]::IsNullOrWhiteSpace([string]$event.data)) {
      continue
    }
    try {
      $eventJson = $event.data | ConvertFrom-Json
      if ([string](Get-ObjectPropertyValue -Object $eventJson -Name "id") -eq $firstNotificationId) {
        $matchingNotificationEvents += $event
      }
    } catch {
    }
  }
  Assert-Condition -Results $results -Name "SSE Notification Not Duplicated" -Condition ($matchingNotificationEvents.Count -eq 1 -and $notificationEvents.Count -eq 1) -Detail "matching=$($matchingNotificationEvents.Count), total=$($notificationEvents.Count), windowSec=$DuplicateWindowSec"
} catch {
  $notes.Add($_.Exception.Message) | Out-Null
  if ($_.InvocationInfo -and $_.InvocationInfo.PositionMessage) {
    $notes.Add($_.InvocationInfo.PositionMessage) | Out-Null
  }
} finally {
  if ($null -ne $listenerJob) {
    try {
      Stop-Job -Job $listenerJob -ErrorAction SilentlyContinue | Out-Null
    } catch {
    }
    try {
      Remove-Job -Job $listenerJob -Force -ErrorAction SilentlyContinue | Out-Null
    } catch {
    }
  }
}

$failedResults = @($results | Where-Object { -not $_.Passed })
$allPassed = $failedResults.Count -eq 0

$lines = @()
$lines += "# Notification SSE Fallback Staging Checklist"
$lines += ""
$lines += "- Timestamp: $(Get-Date -Format s)"
$lines += "- Base URL: $BaseUrl"
$lines += "- Status: **$(if ($allPassed) { 'PASSED' } else { 'FAILED' })**"
$lines += "- Duplicate window seconds: $DuplicateWindowSec"
$lines += "- Stream token issued: $(if ([string]::IsNullOrWhiteSpace($streamToken)) { 'no' } else { 'yes' })"
$lines += "- First notification id: $(if ($null -ne $firstNotification) { [string](Get-ObjectPropertyValue -Object $firstNotification -Name 'id') } else { '<none>' })"
$lines += ""
$lines += "Coverage:"
$lines += "- bearer-authenticated stream-token issuance"
$lines += "- direct SSE subscription via signed stream token"
$lines += "- follow-triggered notification delivery over SSE"
$lines += "- unread-count and paged inbox continuity after fallback delivery"
$lines += "- short duplicate window to catch replay/double-delivery on the SSE path"
$lines += ""
$lines += "| Check | Result | Detail |"
$lines += "|---|---|---|"
foreach ($result in $results) {
  $detail = [string]$result.Detail
  $lines += "| $($result.Name) | $(if ($result.Passed) { 'PASS' } else { 'FAIL' }) | $($detail.Replace('|', '/')) |"
}
if ($notes.Count -gt 0) {
  $lines += ""
  $lines += "## Notes"
  foreach ($note in $notes) {
    $lines += "- $($note.Replace('|', '/'))"
  }
}

Set-Content -Path $reportPath -Value $lines -Encoding UTF8

Write-Host "Notification SSE fallback checklist report created: $reportPath"
Write-Host "Status: $(if ($allPassed) { 'PASSED' } else { 'FAILED' }) | FailedChecks: $($failedResults.Count)"

if (-not $allPassed -and -not $NoFail) {
  exit 1
}
