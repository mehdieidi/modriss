[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
  [Parameter(ParameterSetName = 'Project', Mandatory = $true)]
  [ValidateNotNullOrEmpty()]
  [string]$ProjectId,

  [Parameter(ParameterSetName = 'All', Mandatory = $true)]
  [switch]$All
)

$ErrorActionPreference = 'Stop'
$ownerLabel = 'io.modriss.test-environment'
$projectLabel = 'io.modriss.project-id'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  throw 'Docker CLI was not found. Start Docker Desktop and try again.'
}

function Invoke-DockerList {
  param([string[]]$Arguments)

  $result = @(& docker @Arguments 2>&1)
  if ($LASTEXITCODE -ne 0) {
    throw "Docker command failed: docker $($Arguments -join ' ')`n$($result -join "`n")"
  }
  return @($result | ForEach-Object { $_.ToString().Trim() } | Where-Object { $_ })
}

function Get-ProjectIdFromOwnedResource {
  param([string]$Type, [string]$Name)

  $json = Invoke-DockerList @($Type, 'inspect', $Name) | Out-String
  $resource = @($json | ConvertFrom-Json)[0]
  $labels = if ($Type -eq 'container') { $resource.Config.Labels } else { $resource.Labels }
  if ($labels.$ownerLabel -ne 'true') {
    throw "Refusing to use a resource without the MODRISS test-environment ownership label: $Name"
  }
  $id = [string]$labels.$projectLabel
  if ([string]::IsNullOrWhiteSpace($id)) {
    throw "Refusing to use a MODRISS test resource without a project ID label: $Name"
  }
  return $id
}

function Get-ProjectResources {
  param([string]$Id)

  $suffixBytes = [Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($Id))
  $suffix = [Convert]::ToHexString($suffixBytes).ToLowerInvariant().Substring(0, 24)
  $namespace = "modriss-floci-$suffix"
  $projectFilter = "label=$projectLabel=$Id"
  $ownerFilter = "label=$ownerLabel=true"
  $childFilter = "label=floci_namespace=$namespace"

  $mainContainers = @(Invoke-DockerList @('ps', '-aq', '--filter', $ownerFilter, '--filter', $projectFilter))
  $projectVolumes = @(Invoke-DockerList @('volume', 'ls', '-q', '--filter', $ownerFilter, '--filter', $projectFilter))
  $projectNetworks = @(Invoke-DockerList @('network', 'ls', '-q', '--filter', $ownerFilter, '--filter', $projectFilter))
  $childContainers = @(Invoke-DockerList @('ps', '-aq', '--filter', $childFilter))
  $childVolumes = @(Invoke-DockerList @('volume', 'ls', '-q', '--filter', $childFilter))
  $childNetworks = @(Invoke-DockerList @('network', 'ls', '-q', '--filter', $childFilter))

  foreach ($containerId in $mainContainers) {
    $details = Invoke-DockerList @('inspect', $containerId) | Out-String | ConvertFrom-Json
    $container = @($details)[0]
    if ($container.Config.Image -notlike 'floci/floci:*' -or
        $container.Name.TrimStart('/') -ne $namespace) {
      throw "Refusing to remove an unexpected container for project ${Id}: $($container.Name)"
    }
  }

  [pscustomobject]@{
    ProjectId = $Id
    Namespace = $namespace
    MainContainers = $mainContainers
    ChildContainers = $childContainers
    Volumes = @($projectVolumes + $childVolumes | Select-Object -Unique)
    Networks = @($projectNetworks + $childNetworks | Select-Object -Unique)
  }
}

if ($All) {
  $projectIds = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
  foreach ($type in @('container', 'volume', 'network')) {
    $names = if ($type -eq 'container') {
      Invoke-DockerList @('ps', '-aq', '--filter', "label=$ownerLabel=true")
    } else {
      Invoke-DockerList @($type, 'ls', '-q', '--filter', "label=$ownerLabel=true")
    }
    foreach ($name in $names) {
      [void]$projectIds.Add((Get-ProjectIdFromOwnedResource $type $name))
    }
  }
} else {
  $projectIds = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
  [void]$projectIds.Add($ProjectId)
}

foreach ($id in $projectIds) {
  $resources = Get-ProjectResources $id
  $items = @($resources.MainContainers + $resources.ChildContainers + $resources.Volumes + $resources.Networks)
  if ($items.Count -eq 0) {
    Write-Output "No dedicated Floci resources found for project $id."
    continue
  }

  Write-Output "Project $id — $($resources.MainContainers.Count) Floci container(s), $($resources.ChildContainers.Count) Floci child container(s), $($resources.Volumes.Count) volume(s), $($resources.Networks.Count) network(s)."
  if (-not $PSCmdlet.ShouldProcess("MODRISS Floci test environment for project $id", 'Remove its containers, child resources, networks, and data volumes')) {
    continue
  }

  foreach ($container in $resources.ChildContainers) {
    Invoke-DockerList @('rm', '-f', '-v', $container) | Out-Null
  }
  foreach ($container in $resources.MainContainers) {
    Invoke-DockerList @('rm', '-f', '-v', $container) | Out-Null
  }
  foreach ($volume in $resources.Volumes) {
    Invoke-DockerList @('volume', 'rm', $volume) | Out-Null
  }
  foreach ($network in $resources.Networks) {
    Invoke-DockerList @('network', 'rm', $network) | Out-Null
  }
  Write-Output "Removed the Floci test environment for project $id. Project models, artifacts, and MODRISS execution history were preserved."
}
