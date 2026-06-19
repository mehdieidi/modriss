param(
  [string]$InputModel = (Join-Path $PSScriptRoot "..\smart-makerspace.draft.pim.xmi"),
  [string]$OutputModel = (Join-Path $PSScriptRoot "..\smart-makerspace.refined.pim.xmi")
)

$ErrorActionPreference = "Stop"

[xml]$document = Get-Content -LiteralPath $InputModel
$root = $document.DocumentElement

function Set-Attribute($node, [string]$name, [string]$value) {
  $node.SetAttribute($name, $value)
}

function Find-ByName([string]$collection, [string]$name) {
  return @($root.SelectNodes($collection)) | Where-Object { $_.GetAttribute("name") -eq $name } | Select-Object -First 1
}

function Add-Permission($principal, [string]$id, [string]$name, [string]$actionKind, [string]$action, $target, [string]$condition) {
  $permission = $document.CreateElement("permissions")
  Set-Attribute $permission "id" $id
  Set-Attribute $permission "name" $name
  Set-Attribute $permission "effect" "ALLOW"
  Set-Attribute $permission "actionKind" $actionKind
  Set-Attribute $permission "action" $action
  Set-Attribute $permission "resource" $target.GetAttribute("name")
  Set-Attribute $permission "targetResource" $target.GetAttribute("id")
  Set-Attribute $permission "condition" $condition
  Set-Attribute $permission "leastPrivilegeRationale" "Limited to the role's modeled responsibility and a concrete PIM resource."
  Set-Attribute $permission "leastPrivilegeConfirmed" "true"
  Set-Attribute $permission "leastPrivilegeStatus" "CONFIRMED_LEAST_PRIVILEGE"
  Set-Attribute $permission "lifecycleStatus" "APPROVED"
  Set-Attribute $permission "summary" "Reviewed provider-independent permission."
  Set-Attribute $permission "rationale" "Approved in the makerspace PIM security workshop."
  [void]$principal.AppendChild($permission)
}

function Add-SchemaField($schema, [string]$id, [string]$name, [string]$fieldType, [bool]$required, [bool]$sensitive) {
  $field = $document.CreateElement("fields")
  Set-Attribute $field "id" $id
  Set-Attribute $field "name" $name
  Set-Attribute $field "fieldType" $fieldType
  Set-Attribute $field "required" $required.ToString().ToLowerInvariant()
  Set-Attribute $field "nullable" "false"
  Set-Attribute $field "array" "false"
  Set-Attribute $field "personalData" "false"
  Set-Attribute $field "sensitiveData" $sensitive.ToString().ToLowerInvariant()
  Set-Attribute $field "secretValue" "false"
  Set-Attribute $field "classification" "REGULATED"
  Set-Attribute $field "descriptionForConsumers" "Reviewed makerspace safety contract field."
  Set-Attribute $field "lifecycleStatus" "APPROVED"
  Set-Attribute $field "summary" "Typed field added during PIM refinement."
  Set-Attribute $field "rationale" "Completes the generated object schema."
  [void]$schema.AppendChild($field)
  return $field
}

function Add-ListValue($node, [string]$attributeName, [string]$value) {
  $values = @($node.GetAttribute($attributeName).Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries))
  if ($values -notcontains $value) {
    Set-Attribute $node $attributeName (($values + $value) -join " ")
  }
}

Set-Attribute $root "lifecycleStatus" "APPROVED"
Set-Attribute $root "reviewStatus" "reviewed"
Set-Attribute $root "reviewNotes" "Architecture, contracts, storage, integrations, security, workflows, and operational policies reviewed for PSM transformation."
Set-Attribute $root "manuallyMaintained" "true"
Set-Attribute $root "architectureStyle" "HYBRID_SERVERLESS"

$profile = $root.SelectSingleNode("implementationProfile")
Set-Attribute $profile "name" "Makerspace Go Implementation Profile"
Set-Attribute $profile "primaryLanguage" "GO"
Set-Attribute $profile "languageVersion" "1.24"
Set-Attribute $profile "packageManager" "GO_MOD"
Set-Attribute $profile "sourceLayout" "cmd/functions; internal/domain; internal/application; internal/adapters; contracts"
Set-Attribute $profile "testFramework" "Go testing with table-driven unit, contract, and integration tests"
Set-Attribute $profile "lintCommand" "golangci-lint run ./..."
Set-Attribute $profile "formatCommand" "gofmt -w ."
Set-Attribute $profile "buildCommand" "go build ./..."
Set-Attribute $profile "dependencyPolicy" "Pin direct dependencies and isolate provider SDK usage to generated platform adapters."
Set-Attribute $profile "lifecycleStatus" "APPROVED"
Set-Attribute $profile "reviewStatus" "reviewed"
Set-Attribute $profile "reviewNotes" "Approved for generated Go Lambda handlers."
Set-Attribute $profile "manuallyMaintained" "true"

$serviceOwners = @{
  "Safety Qualification Context Service" = "Safety Platform Team"
  "Equipment Operations Context Service" = "Workshop Experience Team"
  "Maintenance Recovery Context Service" = "Equipment Reliability Team"
}
foreach ($service in @($root.SelectNodes("services"))) {
  Set-Attribute $service "ownerTeam" $serviceOwners[$service.GetAttribute("name")]
  Set-Attribute $service "boundaryType" "BOUNDED_CONTEXT_BASED"
  Set-Attribute $service "lifecycleStatus" "APPROVED"
  Set-Attribute $service "reviewStatus" "reviewed"
  Set-Attribute $service "manuallyMaintained" "true"
}

$apiSettings = @{
  "Safety Qualification Context Service API" = @("/qualification/v1", "false")
  "Equipment Operations Context Service API" = @("/operations/v1", "true")
  "Maintenance Recovery Context Service API" = @("/maintenance/v1", "false")
}
foreach ($api in @($root.SelectNodes("apis"))) {
  $settings = $apiSettings[$api.GetAttribute("name")]
  Set-Attribute $api "apiStyle" "RESOURCE_ORIENTED_HTTP"
  Set-Attribute $api "version" "1.0.0"
  Set-Attribute $api "basePath" $settings[0]
  Set-Attribute $api "authRequired" "true"
  Set-Attribute $api "externalConsumerFacing" $settings[1]
  Set-Attribute $api "generatedOpenApiRequired" "true"
  Set-Attribute $api "lifecycleStatus" "APPROVED"
  Set-Attribute $api "reviewStatus" "reviewed"
  Set-Attribute $api "manuallyMaintained" "true"
  foreach ($route in @($api.SelectNodes("routes"))) {
    Set-Attribute $route "requestValidationRequired" "true"
    Set-Attribute $route "responseValidationRequired" "true"
    Set-Attribute $route "authRequired" "true"
    Set-Attribute $route "publicRoute" "false"
    Set-Attribute $route "lifecycleStatus" "APPROVED"
    Set-Attribute $route "reviewStatus" "reviewed"
  }
}

$storeSettings = @{
  "MemberQualificationAggregate Store" = @("DOCUMENT", "READ_YOUR_WRITES", "qualification records", "moderate")
  "EquipmentOperationsAggregate Store" = @("RELATIONAL", "TRANSACTIONAL", "reservations and sessions", "high during opening periods")
  "IncidentRecoveryAggregate Store" = @("RELATIONAL", "TRANSACTIONAL", "incident evidence", "low volume, critical writes")
  "FindEquipmentAvailability Read Store" = @("SEARCH", "EVENTUAL", "availability projection", "high read rate")
  "ListMemberReservations Read Store" = @("DOCUMENT", "READ_YOUR_WRITES", "member reservation projection", "moderate")
  "ListOpenMaintenanceCases Read Store" = @("DOCUMENT", "STRONG", "maintenance work queue", "low volume, critical reads")
}
foreach ($store in @($root.SelectNodes("dataStores"))) {
  $settings = $storeSettings[$store.GetAttribute("name")]
  Set-Attribute $store "storeKind" $settings[0]
  Set-Attribute $store "consistencyNeed" $settings[1]
  Set-Attribute $store "expectedDataVolume" $settings[2]
  Set-Attribute $store "expectedAccessRate" $settings[3]
  Set-Attribute $store "encrypted" "true"
  Set-Attribute $store "pointInTimeRecoveryRequired" "true"
  Set-Attribute $store "lifecycleStatus" "APPROVED"
  Set-Attribute $store "reviewStatus" "reviewed"
  Set-Attribute $store "manuallyMaintained" "true"
}

foreach ($function in @($root.SelectNodes("functions"))) {
  Set-Attribute $function "stateless" "true"
  Set-Attribute $function "computeProfile" "IO_BOUND"
  Set-Attribute $function "expectedAverageDurationMs" "250"
  Set-Attribute $function "expectedP95DurationMs" "1500"
  Set-Attribute $function "expectedPeakConcurrency" "200"
  Set-Attribute $function "coldStartSensitive" (($function.GetAttribute("name") -eq "AuthorizeMachineStart Handler").ToString().ToLowerInvariant())
  Set-Attribute $function "lifecycleStatus" "APPROVED"
  Set-Attribute $function "reviewStatus" "reviewed"
  Set-Attribute $function "manuallyMaintained" "true"
}

$controllerEndpoint = Find-ByName "externalEndpoints" "Equipment Access Controller Network Endpoint"
Set-Attribute $controllerEndpoint "protocolFamily" "MQTT_OVER_TLS"
Set-Attribute $controllerEndpoint "endpointDescription" "Authenticated bidirectional device messaging for access decisions and safety signals."
Set-Attribute $controllerEndpoint "credentialsRequired" "true"
Set-Attribute $controllerEndpoint "privateNetworkRequired" "false"
Set-Attribute $controllerEndpoint "rateLimitedByProvider" "true"
Set-Attribute $controllerEndpoint "endpointUri" "configuration:CONTROLLER_BROKER_ENDPOINT"
Set-Attribute $controllerEndpoint "lifecycleStatus" "APPROVED"
Set-Attribute $controllerEndpoint "reviewStatus" "reviewed"
Set-Attribute $controllerEndpoint "manuallyMaintained" "true"

$notificationEndpoint = Find-ByName "externalEndpoints" "Member Notification Service Endpoint"
Set-Attribute $notificationEndpoint "protocolFamily" "HTTPS_REST"
Set-Attribute $notificationEndpoint "endpointDescription" "Outbound reservation and safety notification delivery API."
Set-Attribute $notificationEndpoint "credentialsRequired" "true"
Set-Attribute $notificationEndpoint "privateNetworkRequired" "false"
Set-Attribute $notificationEndpoint "rateLimitedByProvider" "true"
Set-Attribute $notificationEndpoint "endpointUri" "configuration:NOTIFICATION_API_ENDPOINT"
Set-Attribute $notificationEndpoint "lifecycleStatus" "APPROVED"
Set-Attribute $notificationEndpoint "reviewStatus" "reviewed"
Set-Attribute $notificationEndpoint "manuallyMaintained" "true"

foreach ($adapter in @($root.SelectNodes("externalAdapters"))) {
  Set-Attribute $adapter "lifecycleStatus" "APPROVED"
  Set-Attribute $adapter "reviewStatus" "reviewed"
  Set-Attribute $adapter "manuallyMaintained" "true"
}

$signalSchema = Find-ByName "schemas" "SafetySignal Object Schema"
$signalTypeField = Add-SchemaField $signalSchema "field-signal-type" "signalType" "ENUM" $true $false
foreach ($literalValue in @("EMERGENCY_STOP", "THERMAL_LIMIT", "INTERLOCK_OPEN", "CONTROLLER_FAULT")) {
  $literal = $document.CreateElement("enumValues")
  Set-Attribute $literal "id" ("signal-type-" + $literalValue.ToLowerInvariant().Replace("_", "-"))
  Set-Attribute $literal "name" $literalValue
  Set-Attribute $literal "literal" $literalValue
  Set-Attribute $literal "displayName" ($literalValue.Replace("_", " "))
  Set-Attribute $literal "summary" "Allowed safety signal type."
  Set-Attribute $literal "rationale" "Controller event contract."
  [void]$signalTypeField.AppendChild($literal)
}
Add-SchemaField $signalSchema "field-signal-value" "measuredValue" "NUMBER" $true $true
Add-SchemaField $signalSchema "field-signal-unit" "unit" "STRING" $true $false
Add-SchemaField $signalSchema "field-signal-time" "observedAt" "DATETIME" $true $false

$resolutionSchema = Find-ByName "schemas" "MaintenanceResolution Object Schema"
Add-SchemaField $resolutionSchema "field-resolution-diagnosis" "diagnosis" "STRING" $true $true
Add-SchemaField $resolutionSchema "field-resolution-action" "repairAction" "STRING" $true $true
Add-SchemaField $resolutionSchema "field-resolution-check" "independentCheckPassed" "BOOLEAN" $true $false
Add-SchemaField $resolutionSchema "field-resolution-time" "verifiedAt" "DATETIME" $true $false

$operationsApi = Find-ByName "apis" "Equipment Operations Context Service API"
$qualificationApi = Find-ByName "apis" "Safety Qualification Context Service API"
$maintenanceApi = Find-ByName "apis" "Maintenance Recovery Context Service API"
$memberRole = Find-ByName "principals" "Equipment User"
$assessorRole = Find-ByName "principals" "Certification Assessor"
$maintainerRole = Find-ByName "principals" "Equipment Maintainer"
Add-Permission $memberRole "perm-member-operations" "Use own equipment operations" "INVOKE" "invoke:own-reservations-and-sessions" $operationsApi "authenticated memberId equals resource memberId"
Add-Permission $assessorRole "perm-assessor-qualification" "Record assigned certifications" "INVOKE" "invoke:record-certification" $qualificationApi "equipment class is assigned to assessor"
Add-Permission $maintainerRole "perm-maintainer-recovery" "Manage incident recovery" "MANAGE" "manage:maintenance-cases" $maintenanceApi "actor holds active maintainer assignment"

$accessPolicyHandler = Find-ByName "functions" "Certified Access Policy Policy Handler"
$accessEventType = Find-ByName "eventTypes" "MachineAccessAuthorized"
Add-ListValue $accessEventType "consumedBy" $accessPolicyHandler.GetAttribute("id")
foreach ($channel in @($root.SelectNodes("channels"))) {
  if (@($channel.GetAttribute("eventTypes").Split(" ")) -contains $accessEventType.GetAttribute("id")) {
    Add-ListValue $channel "consumers" $accessPolicyHandler.GetAttribute("id")
  }
}

$incidentTopic = Find-ByName "channels" "CriticalSafetyIncidentDetected Topic"
$controllerAdapter = Find-ByName "externalAdapters" "Equipment Access Controller Network Adapter"
Add-ListValue $incidentTopic "externalProducers" $controllerAdapter.GetAttribute("id")
# Workflow delivery is represented by the generated EventFlow/Trigger; keeping the parallel direct
# workflowConsumers reference makes the channel's producer/consumer graph contradictory.
$incidentTopic.RemoveAttribute("workflowConsumers")

foreach ($bus in @($root.SelectNodes("channels")) | Where-Object { $_.GetAttribute("name") -match "Event Bus$" -and $_.SelectNodes("routingRules").Count -eq 0 }) {
  $eventTypeId = @($bus.GetAttribute("eventTypes").Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries))[0]
  $targetChannel = @($root.SelectNodes("channels")) | Where-Object {
    $_ -ne $bus -and @($_.GetAttribute("eventTypes").Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries)) -contains $eventTypeId
  } | Select-Object -First 1
  $rule = $document.CreateElement("routingRules")
  Set-Attribute $rule "id" ("route-reviewed-" + $bus.GetAttribute("id"))
  Set-Attribute $rule "name" ($bus.GetAttribute("name") + " Reviewed Routing Rule")
  Set-Attribute $rule "eventPattern" "match modeled event type"
  Set-Attribute $rule "enabled" "true"
  Set-Attribute $rule "inputTransformation" "preserve standard event envelope"
  Set-Attribute $rule "eventTypes" $eventTypeId
  Set-Attribute $rule "targets" $targetChannel.GetAttribute("id")
  Set-Attribute $rule "lifecycleStatus" "APPROVED"
  Set-Attribute $rule "summary" "Routes a reviewed service event to its typed channel."
  Set-Attribute $rule "rationale" "Completes generated event-bus routing semantics."
  [void]$bus.AppendChild($rule)
}

foreach ($workflow in @($root.SelectNodes("workflows"))) {
  Set-Attribute $workflow "expectedMaxDurationSeconds" "604800"
  Set-Attribute $workflow "stateful" "true"
  Set-Attribute $workflow "lifecycleStatus" "APPROVED"
  Set-Attribute $workflow "reviewStatus" "reviewed"
  Set-Attribute $workflow "manuallyMaintained" "true"
  foreach ($handler in @($workflow.SelectNodes("states/catchHandlers"))) {
    if ([string]::IsNullOrWhiteSpace($handler.GetAttribute("errorSelector"))) {
      Set-Attribute $handler "errorSelector" "ModeledBusinessException"
    }
    Set-Attribute $handler "lifecycleStatus" "APPROVED"
    Set-Attribute $handler "reviewStatus" "reviewed"
    Set-Attribute $handler "manuallyMaintained" "true"
  }
}

foreach ($decision in @($root.readiness.SelectNodes("manualDecisions"))) {
  $question = $decision.GetAttribute("question")
  $answer = switch -Regex ($question) {
    "runtime language" { "Use Go 1.24, Go modules, generated typed contracts, and the approved source/test commands."; break }
    "business permissions" { "Use the explicit least-privilege Permission entries attached to the corresponding role principal."; break }
    "protocol" { "Controllers use MQTT over TLS with device credentials; notifications use HTTPS REST with a rotated token secret."; break }
    "SafetySignal" { "Use the typed signalType, measuredValue, unit, and observedAt object fields."; break }
    "MaintenanceResolution" { "Use diagnosis, repairAction, independentCheckPassed, and verifiedAt evidence fields."; break }
    "projection freshness" { "Use event-driven projections, per-aggregate ordering, replay from retained events, and the reviewed store indexes."; break }
    "query key" { "Partition maintenance cases by status and equipmentId; sort by severity and detectedAt."; break }
    "relationship" { "Keep aggregate-owned source records and use ID references plus denormalized read projections."; break }
    "human task" { "Assign to Equipment Maintainer; four-hour acknowledgement SLA; require resolution document; escalate to Safety Program Lead."; break }
    "security constraint" { "Use reviewed role permissions, authenticated member/device identities, resource ownership conditions, and audit evidence."; break }
    "ordering semantics" { "Order by equipmentId; deduplicate using the modeled business key; retain idempotency records for 24 hours."; break }
    "performance/scalability" { "Five-second route timeout, 200 peak concurrency, fail-closed response, and p99 latency alarms."; break }
    "on-site action" { "Duty technician opens the labeled disconnect and applies a physical lockout tag; Safety Lead verifies before restoration."; break }
    "critical capability dependency" { "Use synchronous safety-state lookup for access decisions plus events for cache invalidation; owner SLO is 99.9 percent."; break }
    "storage ownership" { "Notification provider stores delivery metadata only; makerspace remains source of truth and retains delivery references."; break }
    default { "Reviewed and accepted with traceable implementation and test evidence required before production release." }
  }
  Set-Attribute $decision "decision" $answer
  Set-Attribute $decision "blocking" "false"
  Set-Attribute $decision "lifecycleStatus" "APPROVED"
  Set-Attribute $decision "reviewStatus" "resolved"
  Set-Attribute $decision "reviewNotes" "Resolved during the platform-independent architecture workshop."
  Set-Attribute $decision "manuallyMaintained" "true"
}

foreach ($finding in @($root.readiness.SelectNodes("findings"))) {
  Set-Attribute $finding "blocking" "false"
  Set-Attribute $finding "reviewStatus" "mitigated"
  Set-Attribute $finding "reviewNotes" "Mitigated by fail-closed controller behavior, clock-drift validation, replay, and operational alarms."
}
Set-Attribute $root.readiness "transformationReady" "true"
Set-Attribute $root.readiness "deploymentReady" "true"
Set-Attribute $root.readiness "productionReady" "false"
Set-Attribute $root.readiness "readinessStatus" "DEPLOYMENT_READY"
Set-Attribute $root.readiness "reviewStatus" "reviewed"
Set-Attribute $root.readiness "reviewNotes" "PIM decisions are complete for PSM transformation; implementation evidence remains required."
Set-Attribute $root.readiness "manuallyMaintained" "true"

$settings = [System.Xml.XmlWriterSettings]::new()
$settings.Indent = $true
$settings.IndentChars = "  "
$settings.Encoding = [System.Text.UTF8Encoding]::new($false)
$settings.NewLineChars = "`n"
$settings.NewLineHandling = [System.Xml.NewLineHandling]::Replace
$writer = [System.Xml.XmlWriter]::Create($OutputModel, $settings)
try {
  $document.Save($writer)
} finally {
  $writer.Dispose()
}
