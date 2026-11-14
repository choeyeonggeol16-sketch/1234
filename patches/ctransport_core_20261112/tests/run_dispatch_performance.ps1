$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
New-Item -ItemType Directory -Force dispatch-baseline-classes | Out-Null
& javac --enable-preview --release 21 -encoding UTF-8 -cp owner-classes -d dispatch-baseline-classes research/dispatch-performance-20261114/CUserSocket.java
if ($LASTEXITCODE -ne 0) { throw 'Dispatch baseline compile failed' }
& javac --enable-preview --release 21 -encoding UTF-8 -cp owner-classes -d owner-classes tests/CUserSocketReceivePerformanceTest.java
if ($LASTEXITCODE -ne 0) { throw 'Production receive performance compile failed' }
$candidate = Join-Path $root 'owner-classes'
$baseline = (Join-Path $root 'dispatch-baseline-classes') + ';' + $candidate
Push-Location build
try {
    foreach ($label in @('baseline-a','candidate-a','candidate-b','baseline-b')) {
        $classes = if ($label.StartsWith('baseline')) { $baseline } else { $candidate }
        & java --enable-preview --enable-native-access=ALL-UNNAMED -Xms128m -Xmx768m -cp $classes l1j.server.server.model.network.core.connection.CUserSocketReceivePerformanceTest $label 2>&1 | Tee-Object -FilePath "../logs/stability-rxowner-$label.log"
        if ($LASTEXITCODE -ne 0) { throw "Production receive comparison failed: $label" }
    }
} finally { Pop-Location }
