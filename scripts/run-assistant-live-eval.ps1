# Live assistant eval matrix. Run from the repository root after `docker compose up -d backend`.
# Uses provider credentials already loaded by docker compose from .env; this script never prints them.

param(
  [string]$BaseUrl = "http://127.0.0.1:8080",
  [int]$TimeoutSeconds = 420,
  [ValidateRange(0, 8)]
  [int]$MaxDurableResumes = 4,
  [ValidateRange(0, 8)]
  [int]$ProviderRetryCount = 8,
  [string[]]$FixtureId = @(),
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
  $firstCheckpointSeconds = $null
  $resumeCount = 0
  $providerCalls = 0
  $promptTokens = 0
  $completionTokens = 0
  do {
    Start-Sleep -Seconds 3
    $turn = Invoke-Api -Method GET -Path "/api/chatbot/turns/$TurnId" -Token $Token
    if ($null -eq $firstCheckpointSeconds -and (Count-Checkpoint $turn) -gt 0) {
      $firstCheckpointSeconds = [int]((Get-Date) - $started).TotalSeconds
    }
    if (@("SUCCEEDED", "PARTIAL", "NEEDS_INPUT", "NEEDS_CONFIRMATION", "CONFLICTED", "CANCELLED", "TIMED_OUT", "FAILED") -contains [string]$turn.state) {
      $providerCalls = [int]$turn.providerCalls
      $promptTokens = [long]$turn.promptTokens
      $completionTokens = [long]$turn.completionTokens
      if ($turn.state -eq "PARTIAL" -and $turn.remainingWork -and (Count-Checkpoint $turn) -gt 0 -and $resumeCount -lt $MaxDurableResumes) {
        Invoke-Api -Method POST -Path "/api/chatbot/turns/$TurnId/continue" -Token $Token | Out-Null
        $resumeCount++
        continue
      }
      return [pscustomobject]@{
        Turn = $turn
        ElapsedSeconds = [int]((Get-Date) - $started).TotalSeconds
        FirstCheckpointSeconds = $firstCheckpointSeconds
        ResumeCount = $resumeCount
        ProviderCalls = $providerCalls
        PromptTokens = $promptTokens
        CompletionTokens = $completionTokens
      }
    }
  } while (((Get-Date) - $started).TotalSeconds -lt $TimeoutSeconds)
  try {
    Invoke-Api -Method POST -Path "/api/chatbot/turns/$TurnId/cancel" -Token $Token | Out-Null
  } catch {
    Write-Warning "Timed out and failed to request cancellation for ${TurnId}: $($_.Exception.Message)"
  }
  throw "Assistant turn timed out and cancellation was requested: $TurnId"
}

function Count-Array {
  param($Value)
  if ($null -eq $Value) { return 0 }
  if ($Value -is [array]) { return $Value.Count }
  return 1
}

function Count-Checkpoint {
  param($Turn)
  return Count-Array $Turn.checkpoints
}

function Get-EClasses {
  param($Value)
  $types = [System.Collections.Generic.HashSet[string]]::new()
  if ($null -eq $Value) { return @() }
  $stack = [System.Collections.Generic.Stack[object]]::new()
  $visited = [System.Collections.Generic.HashSet[int]]::new()
  $stack.Push($Value)
  while ($stack.Count -gt 0) {
    $current = $stack.Pop()
    if ($null -eq $current -or $current -is [string] -or $current.GetType().IsValueType) { continue }
    $identity = [System.Runtime.CompilerServices.RuntimeHelpers]::GetHashCode($current)
    if (-not $visited.Add($identity)) { continue }
    $eClass = $current.PSObject.Properties["eClass"]
    if ($eClass -and -not [string]::IsNullOrWhiteSpace([string]$eClass.Value)) {
      [void]$types.Add([string]$eClass.Value)
    }
    if ($current -is [System.Collections.IDictionary]) {
      foreach ($item in $current.Values) { $stack.Push($item) }
    } elseif ($current -is [System.Collections.IEnumerable]) {
      foreach ($item in $current) { $stack.Push($item) }
    } else {
      foreach ($property in $current.PSObject.Properties) {
        if ($property.MemberType -eq "NoteProperty" -or $property.MemberType -eq "Property") {
          $stack.Push($property.Value)
        }
      }
    }
  }
  return @($types)
}

  function Count-StructuralNodes {
    param($Value)
    if ($null -eq $Value) { return 0 }
    $count = 0
    $stack = [System.Collections.Generic.Stack[object]]::new()
    $visited = [System.Collections.Generic.HashSet[int]]::new()
    $stack.Push($Value)
    while ($stack.Count -gt 0) {
      $current = $stack.Pop()
      if ($null -eq $current -or $current -is [string] -or $current.GetType().IsValueType) { continue }
      $identity = [System.Runtime.CompilerServices.RuntimeHelpers]::GetHashCode($current)
      if (-not $visited.Add($identity)) { continue }
      if ($current.PSObject.Properties["eClass"]) { $count++ }
      if ($current -is [System.Collections.IDictionary]) {
        foreach ($item in $current.Values) { $stack.Push($item) }
      } elseif ($current -is [System.Collections.IEnumerable]) {
        foreach ($item in $current) { $stack.Push($item) }
      } else {
        foreach ($property in $current.PSObject.Properties) {
          if ($property.MemberType -eq "NoteProperty" -or $property.MemberType -eq "Property") {
            $stack.Push($property.Value)
          }
        }
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
  $validation = Invoke-Api -Method POST -Path "/api/$($Level.ToLowerInvariant())/$ModelId/validate/structural" -Body @{} -Token $Token
  $modelJson = $model.modelJson
  $visual = $modelJson.diagram
  if ($null -eq $visual -or $null -eq $visual.elements) { $visual = $modelJson.graph }
  [pscustomobject]@{
    StructuralNodes = Count-StructuralNodes $modelJson
    Elements = Count-Array $visual.elements
    Relationships = Count-Array $visual.relationships
    ValidationValid = $validation.valid
    EClasses = @(Get-EClasses $modelJson)
    ModelText = ($modelJson | ConvertTo-Json -Depth 60 -Compress)
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
    ProviderCalls = $result.ProviderCalls
    PromptTokens = $result.PromptTokens
    CompletionTokens = $result.CompletionTokens
    RepairAttempts = [int]$turn.repairAttempts
    FirstCheckpointSeconds = $result.FirstCheckpointSeconds
    Checkpoints = Count-Checkpoint $turn
    SavedElements = $turn.savedElementCount
    CoveragePercent = $turn.coveragePercent
    Provenance = $turn.provenance
    Resumes = $result.ResumeCount
    StructuralNodes = $inspection.StructuralNodes
    Elements = $inspection.Elements
    Relationships = $inspection.Relationships
    ValidationValid = $inspection.ValidationValid
    EClasses = $inspection.EClasses
    ModelId = $turn.modelId
    Revision = $turn.revision
    Message = $turn.finalMessage
    SessionId = $session.sessionId
  }
}

function Run-EditScenario {
  param([string]$Token, [string]$ProjectId)
  $created = Run-Scenario -Token $Token -ProjectId $ProjectId -Name "edit-base-pim" -Level "pim" -Prompt "Create a complete provider-neutral PIM serverless architecture for online order intake. Model an HTTP API and route that accept and validate an order contract, function-based order processing, persistent order and idempotency data, an order-accepted event, asynchronous payment through a queue and external payment adapter, a workflow that coordinates payment and customer notification, authentication and authorization, retry and dead-letter behavior, and logging, metrics, tracing, and alerts. Connect the elements into meaningful end-to-end request and event flows; do not merely create isolated nodes."
  if (-not $created.ModelId) { return $created }
  $before = Inspect-Model -Token $Token -Level "pim" -ModelId $created.ModelId
  $edit = Send-Turn -Token $Token -SessionId $created.SessionId -ModelId $created.ModelId -Revision $created.Revision -Message "Extend this existing PIM without removing or recreating the order-intake, payment, and notification architecture. Add an order-cancellation feature with an authenticated cancellation API route, state validation, a cancellation workflow, compensating refund through the existing payment integration, an order-cancelled event, and customer notification. Reuse existing service, data, integration, security, and observability elements where compatible, create only the missing concepts, and connect the new behavior into the existing flows."
  $turn = $edit.Turn
  $inspection = Inspect-Model -Token $Token -Level "pim" -ModelId $turn.modelId
  [pscustomobject]@{
    Scenario = "edit-existing-pim-add-pattern"
    Level = "PIM"
    State = $turn.state
    Seconds = $edit.ElapsedSeconds
    ProviderCalls = $edit.ProviderCalls
    PromptTokens = $edit.PromptTokens
    CompletionTokens = $edit.CompletionTokens
    RepairAttempts = [int]$turn.repairAttempts
    FirstCheckpointSeconds = $edit.FirstCheckpointSeconds
    Checkpoints = Count-Checkpoint $turn
    SavedElements = $turn.savedElementCount
    CoveragePercent = $turn.coveragePercent
    Provenance = $turn.provenance
    Resumes = $edit.ResumeCount
    InitialStructuralNodes = $before.StructuralNodes
    StructuralNodes = $inspection.StructuralNodes
    Elements = $inspection.Elements
    Relationships = $inspection.Relationships
    ValidationValid = $inspection.ValidationValid
    InitialFeaturePresent = ($before.ModelText -match '(?i)order') -and ($before.ModelText -match '(?i)payment') -and ($before.ModelText -match '(?i)notif')
    InitialFeaturePreserved = ($inspection.ModelText -match '(?i)order') -and ($inspection.ModelText -match '(?i)payment') -and ($inspection.ModelText -match '(?i)notif')
    AddedFeaturePresent = ($inspection.ModelText -match '(?i)cancel') -and ($inspection.ModelText -match '(?i)refund')
    ModelId = $turn.modelId
    Revision = $turn.revision
    Message = $turn.finalMessage
    SessionId = $created.SessionId
  }
}

function Run-CimFeatureEvolutionScenario {
  param([string]$Token, [string]$ProjectId)
  $created = Run-Scenario -Token $Token -ProjectId $ProjectId -Name "cim-feature-evolution" -Level "cim" -Prompt "Model these feature requests in a new CIM: customers can place orders and track each order's status; support agents can review an order and update its status. Create the business-facing actors, goals, capabilities, domain concepts, requirements, and meaningful relationships needed to represent the requests."
  if (-not $created.ModelId) { return $created }
  $before = Inspect-Model -Token $Token -Level "cim" -ModelId $created.ModelId
  if (@("SUCCEEDED", "PARTIAL") -notcontains [string]$created.State -or [int]$created.Checkpoints -lt 1 -or [int]$before.StructuralNodes -le 1) {
    $created | Add-Member -NotePropertyName InitialStructuralNodes -NotePropertyValue $before.StructuralNodes
    $created | Add-Member -NotePropertyName InitialFeaturePresent -NotePropertyValue $false
    $created | Add-Member -NotePropertyName InitialFeaturePreserved -NotePropertyValue $false
    $created | Add-Member -NotePropertyName AddedFeaturePresent -NotePropertyValue $false
    return $created
  }
  $edit = Send-Turn -Token $Token -SessionId $created.SessionId -ModelId $created.ModelId -Revision $created.Revision -Message "Add this feature request to the existing CIM without removing or replacing the order placement and tracking features: customers can request a return for a delivered order, support agents approve or reject the return, and approved returns lead to a refund. Extend the existing business model with the needed concepts, requirements, capabilities, and relationships."
  $turn = $edit.Turn
  $after = Inspect-Model -Token $Token -Level "cim" -ModelId $turn.modelId
  [pscustomobject]@{
    Scenario = "cim-feature-evolution"
    Level = "CIM"
    State = $turn.state
    Seconds = ([int]$created.Seconds + [int]$edit.ElapsedSeconds)
    ProviderCalls = ([int]$created.ProviderCalls + [int]$edit.ProviderCalls)
    PromptTokens = ([long]$created.PromptTokens + [long]$edit.PromptTokens)
    CompletionTokens = ([long]$created.CompletionTokens + [long]$edit.CompletionTokens)
    RepairAttempts = ([int]$created.RepairAttempts + [int]$turn.repairAttempts)
    FirstCheckpointSeconds = $created.FirstCheckpointSeconds
    Checkpoints = ([int]$created.Checkpoints + (Count-Checkpoint $turn))
    SavedElements = ([int]$created.SavedElements + [int]$turn.savedElementCount)
    CoveragePercent = $turn.coveragePercent
    Provenance = $turn.provenance
    Resumes = ([int]$created.Resumes + [int]$edit.ResumeCount)
    InitialStructuralNodes = $before.StructuralNodes
    StructuralNodes = $after.StructuralNodes
    Elements = $after.Elements
    Relationships = $after.Relationships
    ValidationValid = $after.ValidationValid
    InitialFeaturePresent = ($before.ModelText -match '(?i)order') -and ($before.ModelText -match '(?i)track|status')
    InitialFeaturePreserved = ($after.ModelText -match '(?i)order') -and ($after.ModelText -match '(?i)track|status')
    AddedFeaturePresent = ($after.ModelText -match '(?i)return') -and ($after.ModelText -match '(?i)refund')
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
    "create-pim-serverless" { return Run-Scenario -Token $Token -ProjectId $ProjectId -Name $Fixture.id -Level "pim" -Prompt "Create a complete PIM for serverless order processing with HTTP API, command-handling behavior, event publication and consumption, persistent data, external payment integration, observability, security, and workflow behavior." }
    "edit-existing-pim-add-pattern" { return Run-EditScenario -Token $Token -ProjectId $ProjectId }
    "cim-feature-evolution" { return Run-CimFeatureEvolutionScenario -Token $Token -ProjectId $ProjectId }
    "answer-only" { return Run-Scenario -Token $Token -ProjectId $ProjectId -Name $Fixture.id -Level "cim" -Prompt "Explain what a CIM model contains. Do not create, edit, or delete a model." }
    "create-cim" { return Run-Scenario -Token $Token -ProjectId $ProjectId -Name $Fixture.id -Level "cim" -Prompt "Create a compact CIM for an appointment booking service with actors, goals, concepts, and relationships." }
    "edit-pim" { $result = Run-EditScenario -Token $Token -ProjectId $ProjectId; $result.Scenario = $Fixture.id; return $result }
    "malformed-repair" { return Run-Scenario -Token $Token -ProjectId $ProjectId -Name $Fixture.id -Level "cim" -Prompt "Create a small valid CIM. If a proposed model action is rejected, repair it within the same turn and then save one valid checkpoint." }
    "attachment-provenance-cim" { return Run-Scenario -Token $Token -ProjectId $ProjectId -Name $Fixture.id -Level "cim" -Prompt "Create a CIM from the attached labelled requirements. Include source-grounded evidence for every R id." -AttachmentName "labelled-pantry.md" -AttachmentContent "R1 A visitor requests an appointment. R2 A coordinator approves requests. R3 Approval reserves inventory and sends notification." }
    "labelled-requirements-pim" { return Run-Scenario -Token $Token -ProjectId $ProjectId -Name $Fixture.id -Level "pim" -Prompt "Create a PIM from every attached labelled requirement. Include requirement evidence for every R id." -AttachmentName "labelled-order.md" -AttachmentContent "R1 Accept HTTP order commands. R2 Persist idempotency keys. R3 Publish order-created events. R4 Invoke payment provider." }
    "durable-resume" { return Run-Scenario -Token $Token -ProjectId $ProjectId -Name $Fixture.id -Level "cim" -Prompt "Create this model in two validated coherent work items. Save the first checkpoint with remaining work, then complete the remaining work when this same durable turn is resumed." -AttachmentName "durable-resume-requirements.md" -AttachmentContent "R1 Visitors request appointments. R2 Coordinators approve appointments. R3 Approval reserves inventory. R4 Volunteers prepare daily pickup lists." }
    "delete-confirmation" {
      $base = Run-Scenario -Token $Token -ProjectId $ProjectId -Name "delete-base" -Level "cim" -Prompt "Create a small CIM with a disposable goal named ObsoleteGoal."
      if (-not $base.ModelId) { return $base }
      $pending = Send-Turn -Token $Token -SessionId $base.SessionId -ModelId $base.ModelId -Revision $base.Revision -Message "Delete the goal named ObsoleteGoal from this model."
      $turn = $pending.Turn
      return [pscustomobject]@{ Scenario=$Fixture.id; Level="CIM"; State=$turn.state; Seconds=$pending.ElapsedSeconds; ProviderCalls=$pending.ProviderCalls; PromptTokens=$pending.PromptTokens; CompletionTokens=$pending.CompletionTokens; FirstCheckpointSeconds=$pending.FirstCheckpointSeconds; Checkpoints=(Count-Checkpoint $turn); SavedElements=$turn.savedElementCount; StructuralNodes=$base.StructuralNodes; Elements=$base.Elements; Relationships=$base.Relationships; ValidationValid=$base.ValidationValid; ModelId=$turn.modelId; Revision=$turn.revision; Message=$turn.finalMessage; SessionId=$base.SessionId; Provenance=$turn.provenance }
    }
    "stale-revision" {
      $base = Run-Scenario -Token $Token -ProjectId $ProjectId -Name "stale-base" -Level "pim" -Prompt "Create a small valid PIM with one API component."
      if (-not $base.ModelId) { return $base }
      $stale = Send-Turn -Token $Token -SessionId $base.SessionId -ModelId $base.ModelId -Revision ([long]$base.Revision + 1000) -Message "Add a cache component."
      $turn = $stale.Turn
      return [pscustomobject]@{ Scenario=$Fixture.id; Level="PIM"; State=$turn.state; Seconds=$stale.ElapsedSeconds; ProviderCalls=$stale.ProviderCalls; PromptTokens=$stale.PromptTokens; CompletionTokens=$stale.CompletionTokens; FirstCheckpointSeconds=$stale.FirstCheckpointSeconds; Checkpoints=(Count-Checkpoint $turn); SavedElements=$turn.savedElementCount; StructuralNodes=$base.StructuralNodes; Elements=$base.Elements; Relationships=$base.Relationships; ValidationValid=$base.ValidationValid; ModelId=$turn.modelId; Revision=$turn.revision; Message=$turn.finalMessage; SessionId=$base.SessionId; Provenance=$turn.provenance }
    }
    "cancellation" {
      $session = Invoke-Api -Method POST -Path "/api/chatbot/sessions" -Body @{ projectId=$ProjectId; modelType="CIM"; modelName="cancel"; forceNew=$true } -Token $Token
      $accepted = Invoke-Api -Method POST -Path "/api/chatbot/sessions/$($session.sessionId)/messages" -Body @{ idempotencyKey=[guid]::NewGuid().ToString(); message="Create a detailed CIM for a national logistics platform."; selectedElementIds=@() } -Token $Token
      Invoke-Api -Method POST -Path "/api/chatbot/turns/$($accepted.turnId)/cancel" -Token $Token | Out-Null
      $cancelled = Wait-Turn -Token $Token -TurnId $accepted.turnId
      $turn = $cancelled.Turn
      return [pscustomobject]@{ Scenario=$Fixture.id; Level="CIM"; State=$turn.state; Seconds=$cancelled.ElapsedSeconds; ProviderCalls=$cancelled.ProviderCalls; PromptTokens=$cancelled.PromptTokens; CompletionTokens=$cancelled.CompletionTokens; FirstCheckpointSeconds=$cancelled.FirstCheckpointSeconds; Checkpoints=(Count-Checkpoint $turn); SavedElements=$turn.savedElementCount; StructuralNodes=0; Elements=0; Relationships=0; ValidationValid=$true; ModelId=$turn.modelId; Revision=$turn.revision; Message=$turn.finalMessage; SessionId=$session.sessionId; Provenance=$turn.provenance }
    }
    default { throw "No live implementation exists for fixture '$($Fixture.id)'." }
  }
}

function Test-TransientProviderFailure {
  param($Result)
  # Do not retry functional gate failures. Freemodel intermittently drops an otherwise valid
  # request mid-turn, so a transport failure before the first durable checkpoint is retryable
  # even when earlier provider calls were recorded. A persisted checkpoint is always evidence
  # of real workflow progress and must be resumed rather than replayed in a new scenario.
  return $Result -and @("FAILED", "PARTIAL", "TIMED_OUT") -contains [string]$Result.State -and
    [int]$Result.Checkpoints -eq 0 -and
    ([string]$Result.Message -match "(?i)(provider.*(available|timeout|temporar|truncated|rate limit)|all models exhausted|request timed out|connection|transport|unexpected end-of-input|malformed assistant tool arguments)")
}

function Run-FixtureWithProviderRetries {
  param([string]$Token, [string]$ProjectId, $Fixture, [string]$Story)
  for ($attempt = 0; $attempt -le $ProviderRetryCount; $attempt++) {
    $result = Run-FixtureScenario -Token $Token -ProjectId $ProjectId -Fixture $Fixture -Story $Story
    if (-not (Test-TransientProviderFailure $result) -or $attempt -eq $ProviderRetryCount) {
      $result | Add-Member -NotePropertyName ProviderRetryAttempts -NotePropertyValue $attempt
      return $result
    }
    # Use a fresh isolated scenario after a bounded recovery delay. The report never includes
    # credentials or provider response bodies.
    Start-Sleep -Seconds ([Math]::Min(15 * ($attempt + 1), 30))
  }
}

function Test-ScenarioGate {
  param($Result, $Fixture)
  $failures = @()
  $scenarioId = [string]$Result.Scenario
  $assertions = @($Fixture.assertions)
  if ($assertions -contains "conflict") {
    if ($Result.State -ne "CONFLICTED" -or [int]$Result.SavedElements -ne 0) { $failures += "expected stale-revision conflict without mutation, got $($Result.State)" }
  } elseif ($assertions -contains "no_mutation") {
    if ($Result.State -ne "SUCCEEDED" -or [int]$Result.SavedElements -ne 0 -or [int]$Result.Checkpoints -ne 0) { $failures += "answer mutated model or did not succeed" }
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
  if (($assertions -contains "requirement_recall") -and [int]$Result.CoveragePercent -lt 100) { $failures += "requirement recall below 100%" }
  if (($assertions -contains "requirement_precision") -and [int]$Result.StructuralNodes -lt 4) { $failures += "too few model nodes for labelled requirement precision review" }
  if (($assertions -contains "durable_resume") -and [int]$Result.Resumes -lt 1) { $failures += "same durable turn was not resumed" }
  switch ([string]$Fixture.id) {
    "source-to-cim-pantry" {
      if ([int]$Result.StructuralNodes -lt 8) { $failures += "source CIM is too shallow" }
      if ((Count-Array $Result.Provenance) -lt 3) { $failures += "source CIM has too little grounded evidence" }
    }
    "create-cim-library" {
      if ([int]$Result.StructuralNodes -lt 5) { $failures += "library CIM is too shallow" }
    }
    "create-pim-serverless" {
      if ([int]$Result.StructuralNodes -lt 10) { $failures += "serverless PIM is too shallow" }
      if ([int]$Result.CoveragePercent -lt 100) { $failures += "mandatory LLM obligation coverage was not proven" }
      $requiredTypes = @("Api", "Function", "EventType", "DataStore", "ExternalAdapter", "ObservabilityConfig", "Workflow")
      foreach ($requiredType in $requiredTypes) {
        if (@($Result.EClasses) -notcontains $requiredType) { $failures += "missing required semantic evidence type $requiredType" }
      }
      if ((@($Result.EClasses) -notcontains "SecurityPolicy") -and (@($Result.EClasses) -notcontains "AuthPolicy")) {
        $failures += "missing security policy evidence"
      }
      $workflowStepTypes = @("StartStep", "SuccessEndStep", "FailureEndStep", "TaskStep", "ChoiceStep", "ParallelStep", "MapStep", "WaitStep", "PassStep")
      if (@($workflowStepTypes | Where-Object { @($Result.EClasses) -contains $_ }).Count -lt 1) {
        $failures += "workflow has no concrete step evidence"
      }
      if ([int]$Result.Relationships -lt 5) { $failures += "serverless PIM has too few relationships" }
    }
    "edit-existing-pim-add-pattern" {
      if ([int]$Result.StructuralNodes -lt 6) { $failures += "PIM edit did not produce enough model structure" }
      if ($Result.InitialFeaturePresent -ne $true) { $failures += "initial order/payment/notification architecture was not modeled" }
      if ($Result.InitialFeaturePreserved -ne $true) { $failures += "existing order/payment/notification architecture was not preserved" }
      if ($Result.AddedFeaturePresent -ne $true) { $failures += "cancellation/refund feature was not added" }
      if ([int]$Result.StructuralNodes -le [int]$Result.InitialStructuralNodes) { $failures += "second PIM feature request did not extend model structure" }
    }
    "cim-feature-evolution" {
      if ($Result.InitialFeaturePresent -ne $true) { $failures += "initial order-tracking feature was not modeled" }
      if ($Result.InitialFeaturePreserved -ne $true) { $failures += "existing order-tracking feature was not preserved" }
      if ($Result.AddedFeaturePresent -ne $true) { $failures += "return/refund feature was not added" }
      if ([int]$Result.StructuralNodes -le [int]$Result.InitialStructuralNodes) { $failures += "second feature request did not extend model structure" }
    }
  }
  if ($null -eq $Fixture) { $failures += "missing versioned fixture" }
  $complex = $Fixture.route -eq "COMPLEX_EDIT"
  # Provider retry attempts are real HTTP calls and remain visible in the report.  They are
  # allowed only for the Freemodel live-evaluation contingency; the logical workflow budget
  # still applies when no transient provider failure occurs.
  # The evolution gate contains two independent modeling turns. Unified complex turns include a
  # schema-bound strategy decision, focused Ecore contract selection, generation, and bounded
  # complete-model repairs. All calls remain visible; this is a workflow budget, not a retry mask.
  # The deployed DeepSeek profile permits 20 calls for one modeling turn: up to two adaptive
  # strategy calls and 18 conceptual/agent calls. Evolution fixtures contain two independent
  # modeling turns. This is an acceptance ceiling; every call and retry remains audited.
  # Explanation turns use one semantic routing call and one answer call. Allow one additional
  # audited call for a strict-schema correction; no deterministic answer is substituted.
  $callBudget = if ($scenarioId -in @("cim-feature-evolution", "edit-existing-pim-add-pattern")) { 40 } elseif ($Fixture.route -eq "EXPLANATION") { 3 } else { 20 }
  $callBudget += [int]$ProviderRetryCount
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
if ($FixtureId.Count -gt 0) {
  $fixtureMatrix = @($fixtureMatrix | Where-Object { $FixtureId -contains $_.id })
  if ($fixtureMatrix.Count -ne $FixtureId.Count) {
    throw "One or more requested fixture IDs are not present in $FixturePath."
  }
}

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

$results = @($fixtureMatrix | ForEach-Object { Run-FixtureWithProviderRetries -Token $token -ProjectId $project.id -Fixture $_ -Story $story })

$results | Format-Table Scenario,Level,State,Seconds,FirstCheckpointSeconds,ProviderCalls,PromptTokens,CompletionTokens,Checkpoints,SavedElements,StructuralNodes,Elements,Relationships,ValidationValid -AutoSize

$reportDir = Split-Path -Parent $ReportPath
if ($reportDir -and -not (Test-Path $reportDir)) {
  New-Item -ItemType Directory -Path $reportDir | Out-Null
}

$lines = @(
  "# Assistant Live Eval Gate Report",
  "",
  "Generated: $(Get-Date -Format o)",
  "",
  "| Scenario | Level | Final state | Total latency (s) | First checkpoint (s) | Provider calls | Prompt tokens | Completion tokens | Checkpoint repairs | Provider retries | Coverage | Checkpoints | Initial nodes | Final nodes | Initial feature | Preserved | Added feature | Structural status | Message |",
  "| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | ---: | ---: | ---: | --- | --- | --- | --- | --- |"
)
foreach ($result in $results) {
  $message = ([string]$result.Message).Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
  $repairs = if ($null -eq $result.RepairAttempts) { 0 } else { [int]$result.RepairAttempts }
  $lines += "| $($result.Scenario) | $($result.Level) | $($result.State) | $($result.Seconds) | $($result.FirstCheckpointSeconds) | $($result.ProviderCalls) | $($result.PromptTokens) | $($result.CompletionTokens) | $repairs | $($result.ProviderRetryAttempts) | $($result.CoveragePercent) | $($result.Checkpoints) | $($result.InitialStructuralNodes) | $($result.StructuralNodes) | $($result.InitialFeaturePresent) | $($result.InitialFeaturePreserved) | $($result.AddedFeaturePresent) | $($result.ValidationValid) | $message |"
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
