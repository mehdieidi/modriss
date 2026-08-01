param(
  [Parameter(Mandatory = $true)]
  [string]$StoryPath,
  [string]$BaseUrl = "http://127.0.0.1:8080/api",
  [int]$TimeoutSeconds = 600,
  [int]$MaxContinues = 2
)

$ErrorActionPreference = "Stop"
$SupportsNoProxy = (Get-Command Invoke-RestMethod).Parameters.ContainsKey("NoProxy")
if (-not $SupportsNoProxy) {
  [System.Net.WebRequest]::DefaultWebProxy = New-Object System.Net.WebProxy
}

function Invoke-Api {
  param(
    [string]$Method = "GET",
    [string]$Path,
    [hashtable]$Headers = @{},
    $Body = $null,
    [string]$ContentType = "application/json"
  )
  $params = @{
    Uri = "$BaseUrl$Path"
    Method = $Method
    Headers = $Headers
    TimeoutSec = 120
  }
  if ($SupportsNoProxy) { $params.NoProxy = $true }
  if ($null -ne $Body) {
    if ($Body -is [byte[]]) {
      $params.Body = $Body
      $params.ContentType = $ContentType
    } else {
      $params.Body = ($Body | ConvertTo-Json -Depth 60 -Compress)
      $params.ContentType = $ContentType
    }
  }
  Invoke-RestMethod @params
}

function Count-Items {
  param($Value)
  if ($null -eq $Value) { return 0 }
  if ($Value -is [array]) { return $Value.Count }
  return 1
}

function Get-Prop {
  param($Value, [string]$Name)
  if ($null -eq $Value) { return $null }
  $property = $Value.PSObject.Properties[$Name]
  if ($null -eq $property) { return $null }
  return $property.Value
}

function Wait-Turn {
  param([string]$Token, [string]$TurnId)
  $started = Get-Date
  $continues = 0
  do {
    Start-Sleep -Seconds 3
    $turn = Invoke-Api -Method GET -Path "/chatbot/turns/$TurnId" -Headers @{ "X-Auth-Token" = $Token }
    if (@("SUCCEEDED", "PARTIAL", "NEEDS_INPUT", "NEEDS_CONFIRMATION", "CONFLICTED", "CANCELLED", "TIMED_OUT", "FAILED") -contains [string]$turn.state) {
      if ($turn.state -eq "PARTIAL" -and $turn.remainingWork -and $continues -lt $MaxContinues) {
        Invoke-Api -Method POST -Path "/chatbot/turns/$TurnId/continue" -Headers @{ "X-Auth-Token" = $Token } | Out-Null
        $continues++
        continue
      }
      $turn | Add-Member -NotePropertyName elapsedSeconds -NotePropertyValue ([int]((Get-Date) - $started).TotalSeconds)
      $turn | Add-Member -NotePropertyName continues -NotePropertyValue $continues
      return $turn
    }
  } while (((Get-Date) - $started).TotalSeconds -lt $TimeoutSeconds)
  try {
    Invoke-Api -Method POST -Path "/chatbot/turns/$TurnId/cancel" -Headers @{ "X-Auth-Token" = $Token } | Out-Null
  } catch {
    Write-Warning "Timed out and failed to request cancellation for ${TurnId}: $($_.Exception.Message)"
  }
  throw "Assistant turn timed out and cancellation was requested: $TurnId"
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

function Resolve-TurnId {
  param($Accepted)
  foreach ($name in @("turnId", "id")) {
    $value = Get-Prop $Accepted $name
    if ($value) { return [string]$value }
  }
  foreach ($name in @("turn", "activeTurn", "activity")) {
    $nested = Get-Prop $Accepted $name
    foreach ($nestedName in @("turnId", "id")) {
      $value = Get-Prop $nested $nestedName
      if ($value) { return [string]$value }
    }
  }
  throw "Message response did not include a turn id: $($Accepted | ConvertTo-Json -Depth 20 -Compress)"
}

function Resolve-ModelPayload {
  param($Response)
  if ($null -eq $Response) { return $null }
  foreach ($name in @("model", "content", "snapshot")) {
    $value = Get-Prop $Response $name
    if ($null -ne $value) { return $value }
  }
  return $Response
}

$resolvedStoryPath = (Resolve-Path $StoryPath).Path
$storyName = [System.IO.Path]::GetFileName($resolvedStoryPath)
$email = "live-cim-" + [guid]::NewGuid().ToString("N").Substring(0, 8) + "@example.com"
$auth = Invoke-Api -Method POST -Path "/auth/register" -Body @{
  email = $email
  password = "Live-Test-123!"
  displayName = "Live CIM Tester"
}
$headers = @{ "X-Auth-Token" = $auth.token }

$project = Invoke-Api -Method POST -Path "/projects" -Headers $headers -Body @{
  name = "Live CIM Source Test $storyName"
  description = "Automated user-story-to-CIM test"
}

$model = Invoke-Api -Method POST -Path "/cim" -Headers $headers -Body @{
  projectId = $project.id
  name = "CIM from $storyName"
  model = @{ eClass = "CIMModel"; name = "CIM from $storyName" }
}

$session = Invoke-Api -Method POST -Path "/chatbot/sessions" -Headers $headers -Body @{
  projectId = $project.id
  modelType = "CIM"
  modelName = "CIM from $storyName"
  forceNew = $true
}

$boundary = [guid]::NewGuid().ToString()
$fileBytes = [System.IO.File]::ReadAllBytes($resolvedStoryPath)
$enc = [System.Text.Encoding]::UTF8
$bodyLines = @(
  "--$boundary",
  "Content-Disposition: form-data; name=`"file`"; filename=`"$storyName`"",
  "Content-Type: text/markdown",
  "",
  $enc.GetString($fileBytes),
  "--$boundary--",
  ""
) -join "`r`n"
$attachment = Invoke-Api -Method POST -Path "/chatbot/sessions/$($session.sessionId)/attachments" -Headers $headers `
  -Body ($enc.GetBytes($bodyLines)) -ContentType "multipart/form-data; boundary=$boundary"

$accepted = Invoke-Api -Method POST -Path "/chatbot/sessions/$($session.sessionId)/messages" -Headers $headers -Body @{
  idempotencyKey = [guid]::NewGuid().ToString()
  message = "Create a complete CIM model from the attached user story document. Build the model directly on the canvas with nodes and edges grounded in the file."
  modelId = $model.id
  revision = $model.revision
  expectedRevision = $model.revision
  activeView = "CIM"
  selectedElementIds = @()
  attachmentIds = @($attachment.id)
  unsavedDraftPatch = $null
}

$acceptedTurnId = Resolve-TurnId $accepted
$turn = Wait-Turn -Token $auth.token -TurnId $acceptedTurnId
$turnId = Get-Prop $turn "turnId"
$modelId = Get-Prop $turn "modelId"
$updated = if ($modelId) { Invoke-Api -Path "/cim/$modelId" -Headers $headers } else { $null }
$structuralValidation = if ($modelId) { Invoke-Api -Method POST -Path "/cim/$modelId/validate/structural" -Headers $headers -Body @{} } else { $null }
$modelPayload = Resolve-ModelPayload $updated

[pscustomobject]@{
  story = $storyName
  turnId = $turnId
  state = $turn.state
  checkpoints = Count-Items $turn.checkpoints
  savedElementCount = $turn.savedElementCount
  coveragePercent = $turn.coveragePercent
  providerCalls = $turn.providerCalls
  repairAttempts = $turn.repairAttempts
  continues = $turn.continues
  elapsedSeconds = $turn.elapsedSeconds
  structuralValidationValid = if ($structuralValidation) { Get-Prop $structuralValidation "valid" } else { $null }
  structuralValidationIssues = if ($structuralValidation) { Get-Prop $structuralValidation "issues" } else { $null }
  structuralNodes = Count-StructuralNodes $modelPayload
  modelId = $modelId
  revision = $turn.revision
  remainingWork = $turn.remainingWork
  finalMessage = $turn.finalMessage
} | ConvertTo-Json -Depth 10
