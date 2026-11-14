$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
& javac --enable-preview --release 21 -encoding UTF-8 -cp windows-classes -d windows-classes tests/StressMetrics.java tests/CTransportSplitWindowsTest.java
if ($LASTEXITCODE -ne 0) { throw 'Split-process test compile failed' }
& javac --enable-preview --release 21 -encoding UTF-8 -cp owner-classes -d owner-classes tests/StressMetrics.java tests/CUserSocketStabilityWindowsTest.java tests/CUserSocketSoakWindowsTest.java
if ($LASTEXITCODE -ne 0) { throw 'Soak test compile failed' }
$receive = Join-Path $root 'windows-classes'
$owners = Join-Path $root 'owner-classes'
Push-Location build
try {
    & java --enable-preview --enable-native-access=ALL-UNNAMED -Xms128m -Xmx768m -cp $receive l1j.server.server.model.network.core.transport.CTransportSplitWindowsTest 2>&1 | Tee-Object -FilePath ../logs/stability-split.log
    if ($LASTEXITCODE -ne 0) { throw 'Split-process stability failed' }
    & java --enable-preview --enable-native-access=ALL-UNNAMED -Xms128m -Xmx768m -cp $owners l1j.server.server.model.network.core.connection.CUserSocketSoakWindowsTest 180 2>&1 | Tee-Object -FilePath ../logs/stability-soak.log
    if ($LASTEXITCODE -ne 0) { throw 'Soak stability failed' }
} finally { Pop-Location }
