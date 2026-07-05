# Live API smoke: user story attachment -> CIM model (mirrors frontend chat flow)
$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8080/api"
$storyPath = "d:\Repositories\My\modless\mde\samples\document-to-cim\community-clinic-user-stories.md"
$timeoutSec = 600

function Invoke-Api {
  param(
    [string]$Method = "GET",
    [string]$Path,
    [hashtable]$Headers = @{},
    $Body = $null,
    [string]$ContentType = "application/json"
  )
  $uri = "$base$Path"
  $params = @{
    Uri = $uri
    Method = $Method
    Headers = $Headers
    TimeoutSec = $timeoutSec
  }
  if ($null -ne $Body) {
    if ($Body -is [byte[]]) {
      $params.Body = $Body
      $params.ContentType = $ContentType
    } else {
      $params.Body = ($Body | ConvertTo-Json -Depth 20 -Compress)
      $params.ContentType = $ContentType
    }
  }
  return Invoke-RestMethod @params
}

$email = "live-cim-" + [guid]::NewGuid().ToString("N").Substring(0, 8) + "@example.com"
$password = "Live-Test-123!"
Write-Host "Registering $email ..."
$auth = Invoke-Api -Method POST -Path "/auth/register" -Body @{
  email = $email
  password = $password
  displayName = "Live CIM Tester"
}
$token = $auth.token
$headers = @{ "X-Auth-Token" = $token }

Write-Host "Creating project ..."
$project = Invoke-Api -Method POST -Path "/projects" -Headers $headers -Body @{
  name = "Live CIM Source Test"
  description = "Automated user-story-to-CIM test"
}
$projectId = $project.id

Write-Host "Creating CIM model ..."
$model = Invoke-Api -Method POST -Path "/cim" -Headers $headers -Body @{
  projectId = $projectId
  name = "Clinic Appointments CIM"
  model = @{ eClass = "CIMModel"; name = "Clinic Appointments CIM" }
}
$modelId = $model.id
$revision = $model.revision

Write-Host "Starting assistant session ..."
$session = Invoke-Api -Method POST -Path "/chatbot/sessions" -Headers $headers -Body @{
  projectId = $projectId
  modelType = "CIM"
  modelName = "Clinic Appointments CIM"
  forceNew = $true
}
$sessionId = $session.sessionId

Write-Host "Uploading user story attachment ..."
$boundary = [guid]::NewGuid().ToString()
$fileBytes = [System.IO.File]::ReadAllBytes($storyPath)
$fileName = [System.IO.Path]::GetFileName($storyPath)
$enc = [System.Text.Encoding]::UTF8
$bodyLines = @(
  "--$boundary",
  "Content-Disposition: form-data; name=`"file`"; filename=`"$fileName`"",
  "Content-Type: text/markdown",
  "",
  $enc.GetString($fileBytes),
  "--$boundary--",
  ""
) -join "`r`n"
$uploadBytes = $enc.GetBytes($bodyLines)
$attachment = Invoke-Api -Method POST -Path "/chatbot/sessions/$sessionId/attachments" -Headers $headers `
  -Body $uploadBytes -ContentType "multipart/form-data; boundary=$boundary"
$attachmentId = $attachment.id
Write-Host "Attachment id: $attachmentId"

$idempotencyKey = [guid]::NewGuid().ToString()
Write-Host "Sending modeling message (timeout ${timeoutSec}s) ..."
$started = Get-Date
$response = Invoke-Api -Method POST -Path "/chatbot/sessions/$sessionId/messages" -Headers $headers -Body @{
  idempotencyKey = $idempotencyKey
  message = "Create a complete CIM model from the attached requirements document."
  modelId = $modelId
  revision = $revision
  activeView = "CIM"
  selectedElementIds = @()
  attachmentIds = @($attachmentId)
  unsavedDraftPatch = $null
}
$elapsed = (Get-Date) - $started
Write-Host "Elapsed: $($elapsed.TotalSeconds.ToString('F1'))s"
Write-Host "Workflow state: $($response.workflowState)"
Write-Host "Assistant message:"
Write-Host $response.assistantMessage
if ($response.proposal) {
  Write-Host "Proposal operations: $($response.proposal.operations.Count)"
}
if ($response.modelId) {
  $updated = Invoke-Api -Path "/cim/$($response.modelId)" -Headers $headers
  $elementCount = ($updated.model | ConvertTo-Json -Depth 30).Length
  Write-Host "Updated model payload chars: $elementCount revision: $($updated.revision)"
}
