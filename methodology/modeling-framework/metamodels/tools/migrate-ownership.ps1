# Re-parent legacy flat ownership in CIM/PIM XMI samples.
param(
  [Parameter(Mandatory = $true, Position = 0, ValueFromRemainingArguments = $true)]
  [string[]]$Paths,
  [ValidateSet("pim", "cim", "auto")]
  [string]$Level = "auto",
  [switch]$DryRun
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$pimScript = Join-Path $scriptDir "migrate-pim-ownership.py"
$cimScript = Join-Path $scriptDir "migrate-cim-ownership.py"

function Resolve-Level {
  param([string]$Path)
  if ($Level -ne "auto") {
    return $Level
  }
  $name = [System.IO.Path]::GetFileName($Path).ToLowerInvariant()
  if ($name -match "\.cim\.xmi$" -or $name -eq "cim.xmi") {
    return "cim"
  }
  if ($name -match "\.pim\.xmi$" -or $name -eq "pim.xmi") {
    return "pim"
  }
  throw "Cannot infer migration level for $Path. Pass -Level pim or -Level cim."
}

$grouped = @{}
foreach ($path in $Paths) {
  $resolved = Resolve-Level $path
  if (-not $grouped.ContainsKey($resolved)) {
    $grouped[$resolved] = @()
  }
  $grouped[$resolved] += $path
}

$exitCode = 0
foreach ($entry in $grouped.GetEnumerator()) {
  $script = if ($entry.Key -eq "cim") { $cimScript } else { $pimScript }
  if (-not (Test-Path $script)) {
    throw "Missing migration script: $script"
  }
  $args = @($script) + $entry.Value
  if ($DryRun) {
    $args += "--dry-run"
  }
  & python @args
  if ($LASTEXITCODE -ne 0) {
    $exitCode = $LASTEXITCODE
  }
}

exit $exitCode
