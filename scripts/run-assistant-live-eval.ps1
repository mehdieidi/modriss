# Live assistant eval matrix. Run from the repository root after `docker compose up -d backend`.
# Uses provider credentials already loaded by docker compose from .env; this script never prints them.

param(
  [string]$BaseUrl = "http://127.0.0.1:8080",
  [int]$TimeoutSeconds = 420,
  [string]$ReportPath = "docs/internal/ai/live-eval-gate-report.md"
)

$ErrorActionPreference = "Stop"
Set-Location "$PSScriptRoot\.."
$SupportsNoProxy = (Get-Command Invoke-RestMethod).Parameters.ContainsKey("NoProxy")
if (-not $SupportsNoProxy) {
  [System.Net.WebRequest]::DefaultWebProxy = New-Object System.Net.WebProxy
}

function Invoke-Api {
  param(
    [string]$Method = "GET",
    [string]$Path,
    $Body = $null,
    [string]$Token = $null
  )
  $headers = @{}
  if ($Token) { $headers["X-Auth-Token"] = $Token }
  $params = @{
    Method = $Method
    Uri = "$BaseUrl$Path"
    Headers = $headers
    TimeoutSec = 120
  }
  if ($SupportsNoProxy) { $params.NoProxy = $true }
  if ($null -ne $Body) {
    $params.ContentType = "application/json"
    $params.Body = ($Body | ConvertTo-Json -Depth 60)
  }
  Invoke-RestMethod @params
}

function Wait-Backend {
  $deadline = (Get-Date).AddSeconds(90)
  do {
    try {
      $params = @{ Method = "GET"; Uri = "$BaseUrl/actuator/health"; TimeoutSec = 5 }
      if ($SupportsNoProxy) { $params.NoProxy = $true }
      $health = Invoke-RestMethod @params
      if ($health.status -eq "UP") { return }
    } catch {
      Start-Sleep -Seconds 2
    }
  } while ((Get-Date) -lt $deadline)
  throw "Backend did not become healthy at $BaseUrl."
}

function Wait-Turn {
  param([string]$Token, [string]$TurnId)
  $started = Get-Date
  do {
    Start-Sleep -Seconds 3
    $turn = Invoke-Api -Method GET -Path "/api/chatbot/turns/$TurnId" -Token $Token
    if (@("SUCCEEDED", "PARTIAL", "NEEDS_INPUT", "NEEDS_CONFIRMATION", "CONFLICTED", "CANCELLED", "TIMED_OUT", "FAILED") -contains [string]$turn.state) {
      return [pscustomobject]@{
        Turn = $turn
        ElapsedSeconds = [int]((Get-Date) - $started).TotalSeconds
      }
    }
  } while (((Get-Date) - $started).TotalSeconds -lt $TimeoutSeconds)
  throw "Assistant turn timed out: $TurnId"
}

function Count-Array {
  param($Value)
  if ($null -eq $Value) { return 0 }
  if ($Value -is [array]) { return $Value.Count }
  return 1
}

function Count-StructuralNodes {
  param($Value)
  if ($null -eq $Value) { return 0 }
  $count = 0
  if ($Value.PSObject.Properties["eClass"]) { $count++ }
  if ($Value -is [System.Collections.IDictionary] -or $Value.PSObject.Properties.Count -gt 0) {
    foreach ($property in $Value.PSObject.Properties) {
      $count += Count-StructuralNodes $property.Value
    }
  } elseif ($Value -is [System.Collections.IEnumerable] -and -not ($Value -is [string])) {
    foreach ($item in $Value) {
      $count += Count-StructuralNodes $item
    }
  }
  return $count
}

function Inspect-Model {
  param([string]$Token, [string]$Level, [string]$ModelId)
  if (-not $ModelId) {
    return [pscustomobject]@{ StructuralNodes = 0; Elements = 0; Relationships = 0; ValidationValid = $null }
  }
  $model = Invoke-Api -Method GET -Path "/api/$($Level.ToLowerInvariant())/$ModelId" -Token $Token
  $validation = Invoke-Api -Method POST -Path "/api/$($Level.ToLowerInvariant())/$ModelId/validate" -Body @{} -Token $Token
  $visual = $model.model.diagram
  if ($null -eq $visual -or $null -eq $visual.elements) { $visual = $model.model.graph }
  [pscustomobject]@{
    StructuralNodes = Count-StructuralNodes $model.model
    Elements = Count-Array $visual.elements
    Relationships = Count-Array $visual.relationships
    ValidationValid = $validation.valid
  }
}

function Send-Turn {
  param(
    [string]$Token,
    [string]$SessionId,
    [string]$Message,
    [string]$ModelId = $null,
    [Nullable[long]]$Revision = $null,
    [string]$AttachmentName = $null,
    [string]$AttachmentContent = $null
  )
  $body = @{
    idempotencyKey = [guid]::NewGuid().ToString()
    message = $Message
    selectedElementIds = @()
  }
  if ($ModelId) { $body.modelId = $ModelId }
  if ($Revision -ne $null) {
    $body.revision = $Revision
    $body.expectedRevision = $Revision
  }
  if ($AttachmentContent) {
    $body.attachmentName = $AttachmentName
    $body.attachmentContent = $AttachmentContent
  }
  $accepted = Invoke-Api -Method POST -Path "/api/chatbot/sessions/$SessionId/messages" -Body $body -Token $Token
  Wait-Turn -Token $Token -TurnId $accepted.turnId
}

function Run-Scenario {
  param(
    [string]$Token,
    [string]$ProjectId,
    [string]$Name,
    [string]$Level,
    [string]$Prompt,
    [string]$AttachmentName = $null,
    [string]$AttachmentContent = $null
  )
  $session = Invoke-Api -Method POST -Path "/api/chatbot/sessions" -Body @{
    projectId = $ProjectId
    modelType = $Level
    modelName = $Name
    forceNew = $true
  } -Token $Token
  $result = Send-Turn -Token $Token -SessionId $session.sessionId -Message $Prompt -AttachmentName $AttachmentName -AttachmentContent $AttachmentContent
  $turn = $result.Turn
  $inspection = Inspect-Model -Token $Token -Level $Level -ModelId $turn.modelId
  [pscustomobject]@{
    Scenario = $Name
    Level = $Level.ToUpperInvariant()
    State = $turn.state
    Seconds = $result.ElapsedSeconds
    ProviderCalls = $turn.providerCalls
    Checkpoints = $turn.checkpointCount
    SavedElements = $turn.savedElementCount
    StructuralNodes = $inspection.StructuralNodes
    Elements = $inspection.Elements
    Relationships = $inspection.Relationships
    ValidationValid = $inspection.ValidationValid
    ModelId = $turn.modelId
    Revision = $turn.revision
    Message = $turn.finalMessage
    SessionId = $session.sessionId
  }
}

function Run-EditScenario {
  param([string]$Token, [string]$ProjectId)
  $created = Run-Scenario -Token $Token -ProjectId $ProjectId -Name "edit-base-pim" -Level "pim" -Prompt "Create a compact PIM model for an order fulfillment serverless application with API, command handlers, data stores, events, and notification capability."
  if (-not $created.ModelId) { return $created }
  $edit = Send-Turn -Token $Token -SessionId $created.SessionId -ModelId $created.ModelId -Revision $created.Revision -Message "Edit this existing PIM model by adding an idempotency pattern for commands, including idempotency key storage, duplicate detection, and retry-safe event publishing."
  $turn = $edit.Turn
  $inspection = Inspect-Model -Token $Token -Level "pim" -ModelId $turn.modelId
  [pscustomobject]@{
    Scenario = "edit-existing-pim-add-pattern"
    Level = "PIM"
    State = $turn.state
    Seconds = $edit.ElapsedSeconds
    ProviderCalls = $turn.providerCalls
    Checkpoints = $turn.checkpointCount
    SavedElements = $turn.savedElementCount
    StructuralNodes = $inspection.StructuralNodes
    Elements = $inspection.Elements
    Relationships = $inspection.Relationships
    ValidationValid = $inspection.ValidationValid
    ModelId = $turn.modelId
    Revision = $turn.revision
    Message = $turn.finalMessage
    SessionId = $created.SessionId
  }
}

Wait-Backend

$email = "assistant-live-" + [guid]::NewGuid().ToString("N").Substring(0, 10) + "@example.test"
$auth = Invoke-Api -Method POST -Path "/api/auth/register" -Body @{
  email = $email
  password = "correct horse 2026"
  displayName = "Assistant Live Eval"
}
$token = $auth.token
$project = Invoke-Api -Method POST -Path "/api/projects" -Token $token -Body @{
  name = "Assistant Live Eval"
  description = "Agentic assistant live matrix"
}

$story = @"
# Community Pantry User Stories

As a visitor, I want to request a pantry appointment so that I can pick up food at an available time.
As a coordinator, I want to review requests, check household size, and approve or reject appointments.
As a volunteer, I want a daily pickup list with dietary notes and package status.
As a donor manager, I want to record incoming donations and update inventory.
When inventory is low, the system should alert coordinators before approving new appointments.
Every appointment approval should send a notification and reserve inventory until pickup or cancellation.
"@

$results = @()
$results += Run-Scenario -Token $token -ProjectId $project.id -Name "source-to-cim-pantry" -Level "cim" -Prompt "Create a complete CIM model from the attached user stories. Ground each modeled element in the source where possible and mark assumptions explicitly." -AttachmentName "community-pantry-user-stories.md" -AttachmentContent $story
$results += Run-Scenario -Token $token -ProjectId $project.id -Name "create-cim-library" -Level "cim" -Prompt "Create a compact but complete CIM model for a library management organization with actors, goals, capabilities, domain concepts, policies, and borrowing process."
$results += Run-Scenario -Token $token -ProjectId $project.id -Name "create-pim-serverless" -Level "pim" -Prompt "Create a full draft PIM model for a serverless order processing platform with HTTP API, command handlers, event-driven workflows, persistent data, external payment integration, observability, and security policies."
$results += Run-Scenario -Token $token -ProjectId $project.id -Name "create-psm-aws-serverless" -Level "psm" -Prompt "Create a full draft AWS PSM model for a serverless order processing platform using API Gateway, Lambda, DynamoDB, EventBridge, SQS dead-letter handling, IAM, alarms, and deployment outputs."
$results += Run-EditScenario -Token $token -ProjectId $project.id

$results | Format-Table Scenario,Level,State,Seconds,ProviderCalls,SavedElements,StructuralNodes,Elements,Relationships,ValidationValid -AutoSize

$reportDir = Split-Path -Parent $ReportPath
if ($reportDir -and -not (Test-Path $reportDir)) {
  New-Item -ItemType Directory -Path $reportDir | Out-Null
}

$lines = @(
  "# Assistant Live Eval Gate Report",
  "",
  "Generated: $(Get-Date -Format o)",
  "",
  "| Scenario | Level | State | Seconds | Provider calls | Saved elements | Structural nodes | Visual elements | Visual relationships | Valid | Message |",
  "| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |"
)
foreach ($result in $results) {
  $message = ([string]$result.Message).Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
  $lines += "| $($result.Scenario) | $($result.Level) | $($result.State) | $($result.Seconds) | $($result.ProviderCalls) | $($result.SavedElements) | $($result.StructuralNodes) | $($result.Elements) | $($result.Relationships) | $($result.ValidationValid) | $message |"
}
$lines | Set-Content -Path $ReportPath -Encoding utf8NoBOM
Write-Host "Report written: $ReportPath"
