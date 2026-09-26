param([string]$TreePath = "$PSScriptRoot/../backend/target/dependency-tree.json")
$ErrorActionPreference = "Stop"
$tree = Get-Content -LiteralPath $TreePath -Raw | ConvertFrom-Json
$nodes = [Collections.Generic.List[object]]::new()
function Visit($node) {
    $nodes.Add($node)
    foreach ($child in $node.children) { Visit $child }
}
Visit $tree
$packages = @($nodes | Where-Object groupId -ne "com.patientmanagement" |
    Sort-Object groupId, artifactId, version -Unique)
$queries = @($packages | ForEach-Object {
    @{ package = @{ ecosystem = "Maven"; name = ($_.groupId + ":" + $_.artifactId) }; version = $_.version }
})
# Only public dependency coordinates leave the machine, never source or configuration.
$result = Invoke-RestMethod -Uri "https://api.osv.dev/v1/querybatch" -Method Post -ContentType "application/json" -Body (
    @{ queries = $queries } | ConvertTo-Json -Depth 8)
if ($result.results.Count -ne $packages.Count) { throw "Incomplete vulnerability response" }
$findings = 0
for ($i = 0; $i -lt $packages.Count; $i++) {
    if ($result.results[$i].vulns) {
        $findings++
        Write-Output ("{0} {1}: {2}" -f $queries[$i].package.name, $packages[$i].version,
            ($result.results[$i].vulns.id -join ", "))
    }
}
Write-Output "Maven packages scanned: $($packages.Count); affected packages: $findings"
if ($findings -gt 0) { exit 1 }
