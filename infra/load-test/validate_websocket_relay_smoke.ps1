param(
  [string]$CoreApiDir = "services/core-api",
  [string]$BaseUrl = "http://localhost:8080",
  [string]$WsUrl = "",
  [int]$ServerPort = 18082,
  [int]$StartupTimeoutSec = 180,
  [int]$EventTimeoutSec = 45,
  [int]$MarketPriceTimeoutSec = 90,
  [string]$RelayHost = "localhost",
  [int]$RelayPort = 61613,
  [string]$RelayClientLogin = "guest",
  [string]$RelayClientPasscode = "guest",
  [string]$RelaySystemLogin = "guest",
  [string]$RelaySystemPasscode = "guest",
  [string]$RelayVirtualHost = "/",
  [string]$TradeSymbol = "BTCUSDT",
  [decimal]$TradeQuantity = 0.001,
  [string]$BrokerRestartCommand = "",
  [int]$BrokerRecoveryWaitSec = 20,
  [switch]$SkipAppStart,
  [switch]$PreserveAppAfterRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-FreeTcpPort {
  $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
  $listener.Start()
  $port = ($listener.LocalEndpoint).Port
  $listener.Stop()
  return $port
}

function Test-PortOpen {
  param([int]$Port)
  $client = [System.Net.Sockets.TcpClient]::new()
  try {
    $async = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
    $ok = $async.AsyncWaitHandle.WaitOne(200)
    if (-not $ok) {
      return $false
    }
    $client.EndConnect($async) | Out-Null
    return $true
  } catch {
    return $false
  } finally {
    $client.Close()
  }
}

function Wait-HttpHealthy {
  param(
    [string]$Url,
    [int]$TimeoutSec
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    try {
      $response = Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec 5
      if ($response.status -eq "UP") {
        return $true
      }
    } catch {
    }
    Start-Sleep -Seconds 2
  }
  return $false
}

function Get-WebSocketUrlFromBaseUrl {
  param([string]$HttpBaseUrl)

  $uri = [Uri]$HttpBaseUrl
  $wsScheme = if ($uri.Scheme -eq "https") { "wss" } else { "ws" }
  $path = if ($uri.AbsolutePath -and $uri.AbsolutePath -ne "/") { $uri.AbsolutePath.TrimEnd("/") } else { "" }
  return "${wsScheme}://$($uri.Authority)$path/ws"
}

function Invoke-JsonApi {
  param(
    [ValidateSet("GET", "POST", "PUT", "DELETE")]
    [string]$Method,
    [string]$Url,
    [hashtable]$Headers,
    [object]$Body
  )

  $request = @{
    Method      = $Method
    Uri         = $Url
    TimeoutSec  = 20
    ErrorAction = "Stop"
  }
  if ($Headers) {
    $request.Headers = $Headers
  }
  if ($null -ne $Body) {
    $request.ContentType = "application/json"
    $request.Body = ($Body | ConvertTo-Json -Depth 12 -Compress)
  }
  return Invoke-RestMethod @request
}

function Get-AuthHeaders {
  param([psobject]$User)

  if ($null -eq $User -or [string]::IsNullOrWhiteSpace($User.accessToken)) {
    throw "User accessToken is required for strict-mode websocket relay validation."
  }

  return @{
    Authorization = "Bearer $($User.accessToken)"
  }
}

function Wait-MarketPrice {
  param(
    [string]$ApiBaseUrl,
    [string]$Symbol,
    [int]$TimeoutSec
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    try {
      $prices = Invoke-JsonApi -Method GET -Url "$ApiBaseUrl/api/v1/market/prices" -Headers @{} -Body $null
      if ($prices -ne $null) {
        $property = $prices.PSObject.Properties | Where-Object { $_.Name -eq $Symbol } | Select-Object -First 1
        if ($property) {
          $value = [double]$property.Value
          if ($value -gt 0) {
            return $true
          }
        }
      }
    } catch {
    }
    Start-Sleep -Seconds 2
  }
  return $false
}

function New-StompFrameText {
  param(
    [string]$Command,
    [hashtable]$Headers,
    [string]$Body = ""
  )

  $builder = [System.Text.StringBuilder]::new()
  $null = $builder.Append($Command).Append("`n")
  foreach ($key in $Headers.Keys) {
    $null = $builder.Append($key).Append(":").Append([string]$Headers[$key]).Append("`n")
  }
  $null = $builder.Append("`n")
  if ($Body) {
    $null = $builder.Append($Body)
  }
  $null = $builder.Append("`0")
  return $builder.ToString()
}

function Parse-StompFrame {
  param([string]$RawFrame)

  $normalized = $RawFrame -replace "`r", ""
  $separatorIndex = $normalized.IndexOf("`n`n")
  if ($separatorIndex -lt 0) {
    $headerBlock = $normalized
    $body = ""
  } else {
    $headerBlock = $normalized.Substring(0, $separatorIndex)
    $body = $normalized.Substring($separatorIndex + 2)
  }

  $lines = $headerBlock -split "`n"
  $command = if ($lines.Length -gt 0) { $lines[0].Trim() } else { "" }
  $headers = @{}
  for ($i = 1; $i -lt $lines.Length; $i++) {
    $line = $lines[$i]
    if ([string]::IsNullOrWhiteSpace($line)) {
      continue
    }
    $parts = $line.Split(":", 2)
    if ($parts.Length -eq 2) {
      $headers[$parts[0]] = $parts[1]
    }
  }

  return [pscustomobject]@{
    Command = $command
    Headers = $headers
    Body    = $body
    Raw     = $normalized
  }
}

function Try-DequeueStompFrame {
  param([pscustomobject]$Session)

  while ($true) {
    $separatorIndex = $Session.Buffer.IndexOf([char]0)
    if ($separatorIndex -lt 0) {
      return $null
    }

    $raw = $Session.Buffer.Substring(0, $separatorIndex)
    $Session.Buffer = if ($separatorIndex + 1 -ge $Session.Buffer.Length) {
      ""
    } else {
      $Session.Buffer.Substring($separatorIndex + 1)
    }

    if ([string]::IsNullOrWhiteSpace($raw)) {
      continue
    }
    return Parse-StompFrame -RawFrame $raw
  }
}

function Send-WebSocketText {
  param(
    [System.Net.WebSockets.ClientWebSocket]$Socket,
    [string]$Text
  )

  $payload = [System.Text.Encoding]::UTF8.GetBytes($Text)
  $segment = [System.ArraySegment[byte]]::new($payload)
  $Socket.SendAsync(
    $segment,
    [System.Net.WebSockets.WebSocketMessageType]::Text,
    $true,
    [System.Threading.CancellationToken]::None
  ).GetAwaiter().GetResult()
}

function Send-StompFrame {
  param(
    [pscustomobject]$Session,
    [string]$Command,
    [hashtable]$Headers,
    [string]$Body = ""
  )

  $frameText = New-StompFrameText -Command $Command -Headers $Headers -Body $Body
  Send-WebSocketText -Socket $Session.Socket -Text $frameText
}

function Receive-StompFrame {
  param(
    [pscustomobject]$Session,
    [int]$TimeoutSec
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    $queued = Try-DequeueStompFrame -Session $Session
    if ($queued -ne $null) {
      return $queued
    }

    if ($Session.Socket.State -ne [System.Net.WebSockets.WebSocketState]::Open) {
      throw "WebSocket is not open. Current state: $($Session.Socket.State)"
    }

    $remainingMs = [Math]::Max(1, [int]($deadline - (Get-Date)).TotalMilliseconds)
    $pollMs = [Math]::Min($remainingMs, 2000)
    $cts = [System.Threading.CancellationTokenSource]::new($pollMs)
    $buffer = New-Object byte[] 8192
    $segment = [System.ArraySegment[byte]]::new($buffer)

    try {
      $result = $Session.Socket.ReceiveAsync($segment, $cts.Token).GetAwaiter().GetResult()
    } catch [System.OperationCanceledException] {
      continue
    } finally {
      $cts.Dispose()
    }

    if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
      throw "WebSocket closed by server: status=$($result.CloseStatus) description=$($result.CloseStatusDescription)"
    }

    if ($result.Count -gt 0) {
      $chunk = [System.Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count)
      $Session.Buffer += $chunk
    }
  }

  throw "Timed out waiting for STOMP frame after $TimeoutSec seconds."
}

function Wait-StompMessage {
  param(
    [pscustomobject]$Session,
    [int]$TimeoutSec,
    [scriptblock]$Matcher,
    [string]$Expectation
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    $remainingSec = [Math]::Max(1, [int][Math]::Ceiling(($deadline - (Get-Date)).TotalSeconds))
    $frame = Receive-StompFrame -Session $Session -TimeoutSec $remainingSec

    if ($frame.Command -eq "ERROR") {
      throw "Received STOMP ERROR frame while waiting for $($Expectation): $($frame.Body)"
    }
    if ($frame.Command -ne "MESSAGE") {
      continue
    }

    $isMatch = $false
    try {
      $isMatch = [bool](& $Matcher $frame)
    } catch {
      $isMatch = $false
    }
    if ($isMatch) {
      return $frame
    }
  }

  throw "Timed out waiting for $Expectation after $TimeoutSec seconds."
}

function New-StompSession {
  param(
    [string]$SocketUrl,
    [string]$AccessToken,
    [string]$HostHeader,
    [int]$ConnectTimeoutSec
  )

  $socket = [System.Net.WebSockets.ClientWebSocket]::new()
  $socket.Options.KeepAliveInterval = [TimeSpan]::FromSeconds(20)
  $cts = [System.Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds($ConnectTimeoutSec))
  try {
    $socket.ConnectAsync([Uri]$SocketUrl, $cts.Token).GetAwaiter().GetResult()
  } finally {
    $cts.Dispose()
  }

  $session = [pscustomobject]@{
    Socket = $socket
    Buffer = ""
  }

  $connectHeaders = @{
    "accept-version" = "1.2"
    "host"           = $HostHeader
    "heart-beat"     = "10000,10000"
    "Authorization"  = "Bearer $AccessToken"
  }
  Send-StompFrame -Session $session -Command "CONNECT" -Headers $connectHeaders
  $connectedFrame = Receive-StompFrame -Session $session -TimeoutSec $ConnectTimeoutSec
  if ($connectedFrame.Command -ne "CONNECTED") {
    throw "Expected CONNECTED frame but received '$($connectedFrame.Command)'."
  }
  return $session
}

function Close-StompSession {
  param([pscustomobject]$Session)
  if ($null -eq $Session -or $null -eq $Session.Socket) {
    return
  }

  try {
    if ($Session.Socket.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
      try {
        Send-StompFrame -Session $Session -Command "DISCONNECT" -Headers @{ receipt = "disconnect-$([Guid]::NewGuid())" }
      } catch {
      }
      $cts = [System.Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds(2))
      try {
        $Session.Socket.CloseAsync(
          [System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure,
          "relay-smoke-finished",
          $cts.Token
        ).GetAwaiter().GetResult()
      } catch {
      } finally {
        $cts.Dispose()
      }
    }
  } finally {
    try {
      $Session.Socket.Dispose()
    } catch {
    }
  }
}

function Shorten-Text {
  param(
    [string]$Value,
    [int]$MaxLength = 240
  )

  if ([string]::IsNullOrWhiteSpace($Value)) {
    return ""
  }
  $singleLine = ($Value -replace "`r", " " -replace "`n", " ").Trim()
  if ($singleLine.Length -le $MaxLength) {
    return $singleLine
  }
  return $singleLine.Substring(0, $MaxLength) + "..."
}

function New-Suffix {
  return ("{0}{1}" -f (Get-Date -Format "yyyyMMddHHmmss"), (Get-Random -Minimum 1000 -Maximum 9999))
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportsDir = "infra/load-test/reports"
$null = New-Item -ItemType Directory -Force -Path $reportsDir
$reportPath = Join-Path $reportsDir "websocket-relay-smoke-$timestamp.md"
$appLogPath = Join-Path $reportsDir "websocket-relay-app-$timestamp.log"
$appErrLogPath = Join-Path $reportsDir "websocket-relay-app-$timestamp.err.log"
$restartLogPath = Join-Path $reportsDir "websocket-relay-broker-restart-$timestamp.log"

$appProcess = $null
$appStartedByScript = $false
$session = $null
$validationStatus = "FAILED"
$notes = @()

$baselineNotificationReceived = $false
$baselineTournamentReceived = $false
$postRestartNotificationReceived = $null
$postRestartTournamentReceived = $null
$notificationSnippet = ""
$tournamentSnippet = ""
$postRestartNotificationSnippet = ""
$postRestartTournamentSnippet = ""
$restartExitCode = "n/a"

$requestedPort = $ServerPort
if (-not $SkipAppStart) {
  if ($ServerPort -le 0) {
    $ServerPort = Get-FreeTcpPort
  } elseif (Test-PortOpen -Port $ServerPort) {
    $ServerPort = Get-FreeTcpPort
    $notes += "Requested server port $requestedPort was in use; switched to $ServerPort."
  }
  $BaseUrl = "http://localhost:$ServerPort"
}

if ([string]::IsNullOrWhiteSpace($WsUrl)) {
  $WsUrl = Get-WebSocketUrlFromBaseUrl -HttpBaseUrl $BaseUrl
}

$healthUrl = "$BaseUrl/actuator/health"

try {
  if (-not $SkipAppStart) {
    $coreApiPath = (Resolve-Path $CoreApiDir).Path
    $appCommand = @(
      "`$env:SPRING_PROFILES_ACTIVE='staging'",
      "`$env:SERVER_PORT='$ServerPort'",
      "`$env:APP_WEBSOCKET_BROKER_MODE='RELAY'",
      "`$env:APP_WEBSOCKET_RELAY_HOST='$RelayHost'",
      "`$env:APP_WEBSOCKET_RELAY_PORT='$RelayPort'",
      "`$env:APP_WEBSOCKET_RELAY_CLIENT_LOGIN='$RelayClientLogin'",
      "`$env:APP_WEBSOCKET_RELAY_CLIENT_PASSCODE='$RelayClientPasscode'",
      "`$env:APP_WEBSOCKET_RELAY_SYSTEM_LOGIN='$RelaySystemLogin'",
      "`$env:APP_WEBSOCKET_RELAY_SYSTEM_PASSCODE='$RelaySystemPasscode'",
      "`$env:APP_WEBSOCKET_RELAY_VIRTUAL_HOST='$RelayVirtualHost'",
      "`$env:APP_SCHEDULING_ENABLED='false'",
      "`$env:MAVEN_OPTS='-Dmaven.repo.local=$coreApiPath\\.m2repo'",
      "mvn -q spring-boot:run"
    ) -join "; "

    $appProcess = Start-Process -FilePath "pwsh" `
      -ArgumentList "-NoLogo", "-NoProfile", "-Command", $appCommand `
      -WorkingDirectory $coreApiPath `
      -RedirectStandardOutput $appLogPath `
      -RedirectStandardError $appErrLogPath `
      -PassThru
    $appStartedByScript = $true

    if (-not (Wait-HttpHealthy -Url $healthUrl -TimeoutSec $StartupTimeoutSec)) {
      throw "Core API did not become healthy at $healthUrl within $StartupTimeoutSec seconds."
    }
  } else {
    if (-not (Wait-HttpHealthy -Url $healthUrl -TimeoutSec 25)) {
      throw "Core API is not reachable at $healthUrl. Start app or remove -SkipAppStart."
    }
  }

  if (-not (Wait-MarketPrice -ApiBaseUrl $BaseUrl -Symbol $TradeSymbol -TimeoutSec $MarketPriceTimeoutSec)) {
    throw "Market price for $TradeSymbol was not available within $MarketPriceTimeoutSec seconds."
  }

  $suffix = New-Suffix
  $receiver = Invoke-JsonApi -Method POST -Url "$BaseUrl/api/v1/auth/register" -Headers @{} -Body @{
    username = "relay_receiver_$suffix"
    email    = "relay_receiver_$suffix@example.com"
    password = "pass1234"
  }
  $actor = Invoke-JsonApi -Method POST -Url "$BaseUrl/api/v1/auth/register" -Headers @{} -Body @{
    username = "relay_actor_$suffix"
    email    = "relay_actor_$suffix@example.com"
    password = "pass1234"
  }

  $receiverId = [string]$receiver.id
  $actorId = [string]$actor.id

  $now = Get-Date
  $tournament = Invoke-JsonApi -Method POST -Url "$BaseUrl/api/v1/tournaments" -Headers @{} -Body @{
    name            = "Relay Smoke $suffix"
    description     = "WebSocket relay smoke/failover validation"
    startingBalance = 100000
    startsAt        = $now.AddMinutes(-1).ToString("yyyy-MM-ddTHH:mm:ss")
    endsAt          = $now.AddHours(2).ToString("yyyy-MM-ddTHH:mm:ss")
  }
  $tournamentId = [string]$tournament.id

  $joinResult = Invoke-JsonApi -Method POST -Url "$BaseUrl/api/v1/tournaments/$tournamentId/join" -Headers (Get-AuthHeaders -User $receiver) -Body $null
  $portfolioId = [string]$joinResult.portfolioId

  $hostHeader = if ([string]::IsNullOrWhiteSpace($RelayVirtualHost)) { "localhost" } else { $RelayVirtualHost }
  $session = New-StompSession -SocketUrl $WsUrl -AccessToken $receiver.accessToken -HostHeader $hostHeader -ConnectTimeoutSec 30

  Send-StompFrame -Session $session -Command "SUBSCRIBE" -Headers @{
    id          = "sub-notifications"
    destination = "/user/queue/notifications"
    ack         = "auto"
  }
  Send-StompFrame -Session $session -Command "SUBSCRIBE" -Headers @{
    id          = "sub-tournament"
    destination = "/topic/tournament/$tournamentId"
    ack         = "auto"
  }

  $null = Invoke-JsonApi -Method POST -Url "$BaseUrl/api/v1/users/$receiverId/follow" -Headers (Get-AuthHeaders -User $actor) -Body $null
  $notificationFrame = Wait-StompMessage -Session $session -TimeoutSec $EventTimeoutSec -Expectation "initial notification stream event" -Matcher {
    param($frame)
    $destination = [string]$frame.Headers["destination"]
    $body = [string]$frame.Body
    return $destination -eq "/user/queue/notifications" -or ($body -like '*"type":"FOLLOW"*')
  }
  $baselineNotificationReceived = $true
  $notificationSnippet = Shorten-Text -Value $notificationFrame.Body -MaxLength 320

  $null = Invoke-JsonApi -Method POST -Url "$BaseUrl/api/v1/trade/buy" -Headers @{} -Body @{
    portfolioId = $portfolioId
    symbol      = $TradeSymbol
    quantity    = [double]$TradeQuantity
    leverage    = 2
    side        = "LONG"
  }
  $tournamentFrame = Wait-StompMessage -Session $session -TimeoutSec $EventTimeoutSec -Expectation "initial tournament stream event" -Matcher {
    param($frame)
    $destination = [string]$frame.Headers["destination"]
    $body = [string]$frame.Body
    return $destination -eq "/topic/tournament/$tournamentId" -or ($body -like '*"type":"TRADE"*')
  }
  $baselineTournamentReceived = $true
  $tournamentSnippet = Shorten-Text -Value $tournamentFrame.Body -MaxLength 320

  if ([string]::IsNullOrWhiteSpace($BrokerRestartCommand)) {
    $postRestartNotificationReceived = "skipped"
    $postRestartTournamentReceived = "skipped"
  } else {
    $restartOutput = & "pwsh" -NoLogo -NoProfile -Command $BrokerRestartCommand 2>&1
    $restartExitCode = if ($null -ne $LASTEXITCODE) { [string]$LASTEXITCODE } else { if ($?) { "0" } else { "1" } }
    Set-Content -Path $restartLogPath -Value (($restartOutput | Out-String).Trim()) -Encoding UTF8
    if ($restartExitCode -ne "0") {
      throw "Broker restart command returned non-zero exit code: $restartExitCode"
    }

    Start-Sleep -Seconds $BrokerRecoveryWaitSec
    if (-not (Wait-HttpHealthy -Url $healthUrl -TimeoutSec 30)) {
      throw "Core API did not recover health after broker restart command."
    }

    Close-StompSession -Session $session
    $session = New-StompSession -SocketUrl $WsUrl -AccessToken $receiver.accessToken -HostHeader $hostHeader -ConnectTimeoutSec 30
    Send-StompFrame -Session $session -Command "SUBSCRIBE" -Headers @{
      id          = "sub-notifications-reconnect"
      destination = "/user/queue/notifications"
      ack         = "auto"
    }
    Send-StompFrame -Session $session -Command "SUBSCRIBE" -Headers @{
      id          = "sub-tournament-reconnect"
      destination = "/topic/tournament/$tournamentId"
      ack         = "auto"
    }

    try {
      $null = Invoke-JsonApi -Method DELETE -Url "$BaseUrl/api/v1/users/$receiverId/follow" -Headers (Get-AuthHeaders -User $actor) -Body $null
    } catch {
      $notes += "Cleanup unfollow failed before post-restart follow test: $($_.Exception.Message)"
    }
    $null = Invoke-JsonApi -Method POST -Url "$BaseUrl/api/v1/users/$receiverId/follow" -Headers (Get-AuthHeaders -User $actor) -Body $null

    $postNotificationFrame = Wait-StompMessage -Session $session -TimeoutSec $EventTimeoutSec -Expectation "post-restart notification stream event" -Matcher {
      param($frame)
      $destination = [string]$frame.Headers["destination"]
      $body = [string]$frame.Body
      return $destination -eq "/user/queue/notifications" -or ($body -like '*"type":"FOLLOW"*')
    }
    $postRestartNotificationReceived = $true
    $postRestartNotificationSnippet = Shorten-Text -Value $postNotificationFrame.Body -MaxLength 320

    $null = Invoke-JsonApi -Method POST -Url "$BaseUrl/api/v1/trade/buy" -Headers @{} -Body @{
      portfolioId = $portfolioId
      symbol      = $TradeSymbol
      quantity    = [double]$TradeQuantity
      leverage    = 2
      side        = "LONG"
    }
    $postTournamentFrame = Wait-StompMessage -Session $session -TimeoutSec $EventTimeoutSec -Expectation "post-restart tournament stream event" -Matcher {
      param($frame)
      $destination = [string]$frame.Headers["destination"]
      $body = [string]$frame.Body
      return $destination -eq "/topic/tournament/$tournamentId" -or ($body -like '*"type":"TRADE"*')
    }
    $postRestartTournamentReceived = $true
    $postRestartTournamentSnippet = Shorten-Text -Value $postTournamentFrame.Body -MaxLength 320
  }

  $validationStatus = "PASSED"
} catch {
  $notes += $_.Exception.Message
  if ($_.InvocationInfo -and $_.InvocationInfo.PositionMessage) {
    $notes += $_.InvocationInfo.PositionMessage
  }
  if ($_.ScriptStackTrace) {
    $notes += $_.ScriptStackTrace
  }
} finally {
  Close-StompSession -Session $session

  if ($appStartedByScript -and $appProcess) {
    try {
      if (-not $PreserveAppAfterRun -and -not $appProcess.HasExited) {
        Stop-Process -Id $appProcess.Id -Force
      }
    } catch {
    }
  }
}

$notesBlock = if ($notes.Count -eq 0) { "- none" } else { ($notes | ForEach-Object { "- $_" }) -join [Environment]::NewLine }
$startupMode = if ($SkipAppStart) { "reuse-existing-app" } else { "script-started-app" }
$appProcessId = if ($appStartedByScript -and $appProcess) { [string]$appProcess.Id } else { "n/a" }
$appLifecycle = if ($appStartedByScript) { $(if ($PreserveAppAfterRun) { "preserved" } else { "stopped-on-exit" }) } else { "n/a" }
$appLogValue = if ($appStartedByScript) { "$appLogPath (stderr: $appErrLogPath)" } else { "n/a (app not started by script)" }
$restartMode = if ([string]::IsNullOrWhiteSpace($BrokerRestartCommand)) { "disabled" } else { "enabled" }

$reportLines = @(
  "# WebSocket Relay Smoke Validation",
  "",
  "Generated at: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")",
  "",
  "## Scenario",
  "- Base URL: $BaseUrl",
  "- WebSocket URL: $WsUrl",
  "- Startup mode: $startupMode",
  "- Broker mode expectation: RELAY",
  "- Relay host/port: $($RelayHost):$($RelayPort)",
  "- Relay virtual host: $RelayVirtualHost",
  "- Trade symbol: $TradeSymbol",
  "- Restart drill: $restartMode",
  "- Restart wait seconds: $BrokerRecoveryWaitSec",
  "",
  "## Result",
  "- Validation: **$validationStatus**",
  "- Baseline notification message received: $baselineNotificationReceived",
  "- Baseline tournament message received: $baselineTournamentReceived",
  "- Post-restart notification message received: $postRestartNotificationReceived",
  "- Post-restart tournament message received: $postRestartTournamentReceived",
  "- Broker restart exit code: $restartExitCode",
  "- App process id: $appProcessId",
  "- App lifecycle: $appLifecycle",
  "- App log: $appLogValue",
  "- Broker restart log: $(if ($restartMode -eq "enabled") { $restartLogPath } else { "n/a" })",
  "",
  "## Message Samples",
  "- Notification sample: $(if ($notificationSnippet) { $notificationSnippet } else { "n/a" })",
  "- Tournament sample: $(if ($tournamentSnippet) { $tournamentSnippet } else { "n/a" })",
  "- Post-restart notification sample: $(if ($postRestartNotificationSnippet) { $postRestartNotificationSnippet } else { "n/a" })",
  "- Post-restart tournament sample: $(if ($postRestartTournamentSnippet) { $postRestartTournamentSnippet } else { "n/a" })",
  "",
  "## Notes",
  $notesBlock
)
$report = $reportLines -join [Environment]::NewLine
Set-Content -Path $reportPath -Value $report -Encoding UTF8

Write-Output "Relay smoke report created: $reportPath"
Write-Output "Relay smoke status: $validationStatus"

if ($validationStatus -ne "PASSED") {
  exit 1
}
