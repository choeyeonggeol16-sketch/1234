$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
New-Item -ItemType Directory -Force diagnostics-baseline-classes | Out-Null
& javac --enable-preview --release 21 -encoding UTF-8 -cp owner-classes -d diagnostics-baseline-classes research/performance-20261114/CNetworkDiagnostics.java tests/NetworkDiagnosticsPerformanceTest.java
if ($LASTEXITCODE -ne 0) { throw 'Baseline compile failed' }
& javac --enable-preview --release 21 -encoding UTF-8 -cp owner-classes -d owner-classes tests/NetworkDiagnosticsPerformanceTest.java tests/CTransportPerformanceWindowsTest.java
if ($LASTEXITCODE -ne 0) { throw 'Candidate compile failed' }
$candidate = Join-Path $root 'owner-classes'
$baseline = (Join-Path $root 'diagnostics-baseline-classes') + ';' + $candidate
Push-Location build
try {
    # ABBA limits ordering/JIT bias; same host, compiler, DLL and frame verifier.
    foreach ($label in @('baseline-a','candidate-a','candidate-b','baseline-b')) {
        $classes = if ($label.StartsWith('baseline')) { $baseline } else { $candidate }
        & java --enable-preview --enable-native-access=ALL-UNNAMED -Xms128m -Xmx768m -cp $classes l1j.server.server.model.network.core.diagnostics.NetworkDiagnosticsPerformanceTest $label 2>&1 | Tee-Object -FilePath "../logs/stability-perf-$label.log"
        if ($LASTEXITCODE -ne 0) { throw "Diagnostics comparison failed: $label" }
        & java --enable-preview --enable-native-access=ALL-UNNAMED -Xms128m -Xmx768m -cp $classes l1j.server.server.model.network.core.connection.CTransportPerformanceWindowsTest $label 2>&1 | Tee-Object -FilePath "../logs/stability-wireperf-$label.log"
        if ($LASTEXITCODE -ne 0) { throw "Wire comparison failed: $label" }
    }
} finally { Pop-Location }
