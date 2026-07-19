# Live assistant eval matrix. Run from the repository root after `docker compose up -d backend`.
# Uses provider credentials already loaded by docker compose from .env; this script never prints them.

param(
  [string]$BaseUrl = "http://127.0.0.1:8080",
  [int]$TimeoutSeconds = 420,
  [string]$ReportPath = "docs/internal/ai/live-eval-gate-report.md",
  [string]$FixturePath = "packages/java/platform-assistant/src/test/resources/assistant-durable-eval-fixtures.json"
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
      # The provider health contributor can be temporarily degraded while the application and
      # durable worker are ready; the gate itself verifies provider calls explicitly.
      $params = @{ Method = "GET"; Uri = "$BaseUrl/actuator/health/readiness"; TimeoutSec = 5 }
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
  $rootTurnId = $TurnId
  $automaticContinuations = 0
  do {
    Start-Sleep -Seconds 3
    $turn = Invoke-Api -Method GET -Path "/api/chatbot/turns/$TurnId" -Token $Token
    if (@("SUCCEEDED", "PARTIAL", "NEEDS_INPUT", "NEEDS_CONFIRMATION", "CONFLICTED", "CANCELLED", "TIMED_OUT", "FAILED") -contains [string]$turn.state) {
      # A partial checkpoint may have already queued a fresh durable slice. Follow it so this
      # gate evaluates the completed incremental request, not an intermediate canvas state.
      $next = @($turn.continuations | Where-Object { $_.state -in @("QUEUED", "RUNNING") } | Select-Object -First 1)
      if ($turn.state -eq "PARTIAL" -and $next.Count -gt 0) {
        $TurnId = $next[0].turnId
        $automaticContinuations++
        continue
      }
      return [pscustomobject]@{
        Turn = $turn
        ElapsedSeconds = [int]((Get-Date) - $started).TotalSeconds
        RootTurnId = $rootTurnId
        AutomaticContinuations = $automaticContinuations
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
    CoveragePercent = $turn.coveragePercent
    Provenance = $turn.provenance
    Continuations = $result.AutomaticContinuations
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
    CoveragePercent = $turn.coveragePercent
    Provenance = $turn.provenance
    Continuations = $edit.AutomaticContinuations
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

# Every versioned fixture must enter here.  Scenarios which need an existing revision first create
# an isolated base model, so one failure cannot contaminate the rest of the matrix.
function Run-FixtureScenario {
  param([string]$Token, [string]$ProjectId, $Fixture, [string]$Story)
  switch ([string]$Fixture.id) {
    "source-to-cim-pantry" { return Run-Scenario -Token $Token -ProjectId $ProjectId -Name $Fixture.id -Level "cim" -Prompt "Create a complete CIM model from the attached user stories. Ground each modeled element in the source where possible and mark assumptions explicitly." -AttachmentName "community-pantry-user-stories.md" -AttachmentContent $Story }
    "create-cim-library" { return Run-Scenario -Token $Token -ProjectId $ProjectId -Name $Fixture.id -Level "cim" -Prompt "Create a compact complete CIM library model with actors, goals, capabilities, concepts, policies, and borrowing relationships." }
    "create-pim-serverless" { return Run-Scenario -Token $Token -ProjectId $ProjectId -Name $Fixture.id -Level "pim" -Prompt "Create a complete PIM for serverless order processing with HTTP API, commands, events, persistent data, payment integration, observability, and security." }
    "edit-existing-pim-add-pattern" { return Run-EditScenario -Token $Token -ProjectId $ProjectId }
    "answer-only" { return Run-Scenario -Token $Token -ProjectId $ProjectId -Name $Fixture.id -Level "cim" -Prompt "Explain what a CIM model contains. Do not create, edit, or delete a model." }
    "create-cim" { return Run-Scenario -Token $Token -ProjectId $ProjectId -Name $Fixture.id -Level "cim" -Prompt "Create a compact CIM for an appointment booking service with actors, goals, concepts, and relationships." }
    "edit-pim" { $result = Run-EditScenario -Token $Token -ProjectId $ProjectId; $result.Scenario = $Fixture.id; return $result }
    "malformed-repair" { return Run-Scenario -Token $Token -ProjectId $ProjectId -Name $Fixture.id -Level "cim" -Prompt "Create a small valid CIM. If a proposed model action is rejected, repair it within the same turn and then save one valid checkpoint." }
    "attachment-provenance-cim" { return Run-Scenario -Token $Token -ProjectId $ProjectId -Name $Fixture.id -Level "cim" -Prompt "Create a CIM from the attached labelled requirements. Include source-grounded evidence for every R id." -AttachmentName "labelled-pantry.md" -AttachmentContent "R1 A visitor requests an appointment. R2 A coordinator approves requests. R3 Approval reserves inventory and sends notification." }
    "labelled-requirements-pim" { return Run-Scenario -Token $Token -ProjectId $ProjectId -Name $Fixture.id -Level "pim" -Prompt "Create a PIM from every attached labelled requirement. Include requirement evidence for every R id." -AttachmentName "labelled-order.md" -AttachmentContent "R1 Accept HTTP order commands. R2 Persist idempotency keys. R3 Publish order-created events. R4 Invoke payment provider." }
    "automatic-multi-slice" { return Run-Scenario -Token $Token -ProjectId $ProjectId -Name $Fixture.id -Level "cim" -Prompt "Create this model in at least two validated coherent slices. After the first checkpoint set turnComplete=false, then automatically continue from the persisted checkpoint and complete it." -AttachmentName "sliced-requirements.md" -AttachmentContent "R1 Visitors request appointments. R2 Coordinators approve appointments. R3 Approval reserves inventory. R4 Volunteers prepare daily pickup lists." }
    "delete-confirmation" {
      $base = Run-Scenario -Token $Token -ProjectId $ProjectId -Name "delete-base" -Level "cim" -Prompt "Create a small CIM with a disposable goal named ObsoleteGoal."
      if (-not $base.ModelId) { return $base }
      $pending = Send-Turn -Token $Token -SessionId $base.SessionId -ModelId $base.ModelId -Revision $base.Revision -Message "Delete the goal named ObsoleteGoal from this model."
      $turn = $pending.Turn
      return [pscustomobject]@{ Scenario=$Fixture.id; Level="CIM"; State=$turn.state; Seconds=$pending.ElapsedSeconds; ProviderCalls=$turn.providerCalls; Checkpoints=$turn.checkpointCount; SavedElements=$turn.savedElementCount; StructuralNodes=$base.StructuralNodes; Elements=$base.Elements; Relationships=$base.Relationships; ValidationValid=$base.ValidationValid; ModelId=$turn.modelId; Revision=$turn.revision; Message=$turn.finalMessage; SessionId=$base.SessionId; Provenance=$turn.provenance }
    }
    "stale-revision" {
      $base = Run-Scenario -Token $Token -ProjectId $ProjectId -Name "stale-base" -Level "pim" -Prompt "Create a small valid PIM with one API component."
      if (-not $base.ModelId) { return $base }
      $stale = Send-Turn -Token $Token -SessionId $base.SessionId -ModelId $base.ModelId -Revision ([long]$base.Revision + 1000) -Message "Add a cache component."
      $turn = $stale.Turn
      return [pscustomobject]@{ Scenario=$Fixture.id; Level="PIM"; State=$turn.state; Seconds=$stale.ElapsedSeconds; ProviderCalls=$turn.providerCalls; Checkpoints=$turn.checkpointCount; SavedElements=$turn.savedElementCount; StructuralNodes=$base.StructuralNodes; Elements=$base.Elements; Relationships=$base.Relationships; ValidationValid=$base.ValidationValid; ModelId=$turn.modelId; Revision=$turn.revision; Message=$turn.finalMessage; SessionId=$base.SessionId; Provenance=$turn.provenance }
    }
    "cancellation" {
      $session = Invoke-Api -Method POST -Path "/api/chatbot/sessions" -Body @{ projectId=$ProjectId; modelType="CIM"; modelName="cancel"; forceNew=$true } -Token $Token
      $accepted = Invoke-Api -Method POST -Path "/api/chatbot/sessions/$($session.sessionId)/messages" -Body @{ idempotencyKey=[guid]::NewGuid().ToString(); message="Create a detailed CIM for a national logistics platform."; selectedElementIds=@() } -Token $Token
      Invoke-Api -Method POST -Path "/api/chatbot/turns/$($accepted.turnId)/cancel" -Token $Token | Out-Null
      $cancelled = Wait-Turn -Token $Token -TurnId $accepted.turnId
      $turn = $cancelled.Turn
      return [pscustomobject]@{ Scenario=$Fixture.id; Level="CIM"; State=$turn.state; Seconds=$cancelled.ElapsedSeconds; ProviderCalls=$turn.providerCalls; Checkpoints=$turn.checkpointCount; SavedElements=$turn.savedElementCount; StructuralNodes=0; Elements=0; Relationships=0; ValidationValid=$true; ModelId=$turn.modelId; Revision=$turn.revision; Message=$turn.finalMessage; SessionId=$session.sessionId; Provenance=$turn.provenance }
    }
    default { throw "No live implementation exists for fixture '$($Fixture.id)'." }
  }
}

function Test-ScenarioGate {
  param($Result, $Fixture)
  $failures = @()
  $scenarioId = [string]$Result.Scenario
  $assertions = @($Fixture.assertions)
  if ($assertions -contains "no_mutation") {
    if ($Result.State -ne "SUCCEEDED" -or [int]$Result.SavedElements -ne 0 -or [int]$Result.Checkpoints -ne 0) { $failures += "answer mutated model or did not succeed" }
  } elseif ($assertions -contains "conflict") {
    if ($Result.State -ne "CONFLICTED" -or [int]$Result.SavedElements -ne 0) { $failures += "expected stale-revision conflict without mutation, got $($Result.State)" }
  } elseif ($assertions -contains "cancelled") {
    if ($Result.State -ne "CANCELLED" -or [int]$Result.Checkpoints -ne 0) { $failures += "expected cancellation without checkpoint, got $($Result.State)" }
  } elseif ($assertions -contains "needs_confirmation") {
    if ($Result.State -ne "NEEDS_CONFIRMATION" -or [int]$Result.SavedElements -ne 0) { $failures += "expected confirmation before deletion, got $($Result.State)" }
  } else {
    if (@("SUCCEEDED", "PARTIAL") -notcontains [string]$Result.State) { $failures += "state=$($Result.State)" }
    if ($Result.ValidationValid -ne $true) { $failures += "structural validation failed" }
    if ([int]$Result.SavedElements -lt 1) { $failures += "no model elements were saved" }
  }
  if (($assertions -contains "provenance") -and (Count-Array $Result.Provenance) -lt 1) { $failures += "missing provenance" }
  if (($assertions -contains "labelled_requirement_recall") -and [int]$Result.CoveragePercent -lt 100) { $failures += "requirement recall below 100%" }
  if (($assertions -contains "automatic_continuation") -and [int]$Result.Continuations -lt 1) { $failures += "automatic continuation was not created" }
  if ($null -eq $Fixture) { $failures += "missing versioned fixture" }
  $complex = $Fixture.route -eq "COMPLEX_EDIT"
  $callBudget = if ($complex) { 4 } elseif ($Fixture.route -eq "EXPLANATION") { 1 } else { 2 }
  if ([int]$Result.ProviderCalls -gt $callBudget) {
    $failures += "provider calls $($Result.ProviderCalls) exceed budget $callBudget"
  }
  if ([int]$Result.Seconds -gt 420) { $failures += "latency $($Result.Seconds)s exceeds 420s" }
  [pscustomobject]@{
    Scenario = $Result.Scenario
    Passed = $failures.Count -eq 0
    Failures = ($failures -join "; ")
  }
}

Wait-Backend

if (-not (Test-Path $FixturePath)) { throw "Assistant eval fixture matrix not found: $FixturePath" }
$fixtureMatrix = Get-Content -Raw $FixturePath | ConvertFrom-Json

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

$results = @($fixtureMatrix | ForEach-Object { Run-FixtureScenario -Token $token -ProjectId $project.id -Fixture $_ -Story $story })

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
# Windows PowerShell 5 does not support utf8NoBOM; UTF8 is portable across the developer and
# CI shells used for this live gate.
$lines | Set-Content -Path $ReportPath -Encoding UTF8
Write-Host "Report written: $ReportPath"

$gate = @($results | ForEach-Object {
  $result = $_
  $fixture = @($fixtureMatrix | Where-Object { $_.id -eq $result.Scenario }) | Select-Object -First 1
  Test-ScenarioGate $result $fixture
})
$gate | Format-Table Scenario,Passed,Failures -AutoSize
$failed = @($gate | Where-Object { -not $_.Passed })
if ($failed.Count -gt 0) {
  throw "Assistant live evaluation gate failed: " + (($failed | ForEach-Object { "$($_.Scenario): $($_.Failures)" }) -join " | ")
}
Write-Host "Assistant live evaluation gate passed."
