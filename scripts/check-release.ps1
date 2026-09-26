param([switch]$RequireTracked)
$ErrorActionPreference = "Stop"
$root = (Resolve-Path "$PSScriptRoot/..").Path
$manifest = Get-Content "$root/docs/release-files.txt" | Where-Object { $_ -and !$_.StartsWith("#") }
$missing = @($manifest | Where-Object { !(Test-Path -LiteralPath (Join-Path $root $_) -PathType Leaf) })
$tracked = @(& git -C $root ls-files)
$untracked = @($manifest | Where-Object { $_ -notin $tracked })
Write-Output "Release files: $($manifest.Count); missing: $($missing.Count); not tracked: $($untracked.Count)"
if ($missing.Count) { $missing | Write-Output; throw "Required release files are missing" }
if ($RequireTracked -and $untracked.Count) { $untracked | Write-Output; throw "Required release files are not tracked" }
if ($untracked.Count) { Write-Output "Working directory is complete; clean checkout is NOT reproducible until the release list is included." }
