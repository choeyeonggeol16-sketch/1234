param([switch]$CompareExperiment)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
New-Item -ItemType Directory -Force dispatch-baseline-classes | Out-Null
if ($CompareExperiment) {
    & javac --enable-preview --release 21 -encoding UTF-8 -cp owner-classes -d dispatch-baseline-classes research/dispatch-performance-20261114/CUserSocket.java
    if ($LASTEXITCODE -ne 0) { throw 'Dispatch baseline compile failed' }
    New-Item -ItemType Directory -Force dispatch-experiment-classes | Out-Null
    & javac --enable-preview --release 21 -encoding UTF-8 -cp owner-classes -d dispatch-experiment-classes research/dispatch-performance-20261114/rejected-candidate/CUserSocket.java
    if ($LASTEXITCODE -ne 0) { throw 'Rejected experiment compile failed' }
}
& javac --enable-preview --release 21 -encoding UTF-8 -cp owner-classes -d owner-classes tests/StressMetrics.java tests/CUserSocketReceivePerformanceTest.java
if ($LASTEXITCODE -ne 0) { throw 'Production receive performance compile failed' }
$candidate = Join-Path $root 'owner-classes'
$baseline = (Join-Path $root 'dispatch-baseline-classes') + ';' + $candidate
$experiment = (Join-Path $root 'dispatch-experiment-classes') + ';' + $candidate
Push-Location build
try {
    $labels = if ($CompareExperiment) { @('baseline-a','candidate-a','candidate-b','baseline-b') } else { @('shipping') }
    foreach ($label in $labels) {
        $classes = if ($label.StartsWith('baseline')) { $baseline } elseif ($label.StartsWith('candidate')) { $experiment } else { $candidate }
        & java --enable-preview --enable-native-access=ALL-UNNAMED -Xms128m -Xmx768m -cp $classes l1j.server.server.model.network.core.connection.CUserSocketReceivePerformanceTest $label 2>&1 | Tee-Object -FilePath "../logs/stability-rxowner-$label.log"
        if ($LASTEXITCODE -ne 0) { throw "Production receive comparison failed: $label" }
    }
} finally { Pop-Location }
