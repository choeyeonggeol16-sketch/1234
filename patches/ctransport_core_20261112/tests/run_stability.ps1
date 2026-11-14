$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
New-Item -ItemType Directory -Force logs | Out-Null
& javac --enable-preview --release 21 -encoding UTF-8 -cp windows-classes -d windows-classes tests/StressMetrics.java tests/CTransportLoadWindowsTest.java
if ($LASTEXITCODE -ne 0) { throw 'Receive stability compile failed' }
& javac --enable-preview --release 21 -encoding UTF-8 -cp owner-classes -d owner-classes tests/StressMetrics.java tests/CUserSocketStabilityWindowsTest.java
if ($LASTEXITCODE -ne 0) { throw 'Production Java stability compile failed' }
$receive = Join-Path $root 'windows-classes'
$owners = Join-Path $root 'owner-classes'
Push-Location build
try {
    & java --enable-preview --enable-native-access=ALL-UNNAMED -Xms128m -Xmx768m -cp $receive l1j.server.server.model.network.core.transport.CTransportLoadWindowsTest 2>&1 | Tee-Object -FilePath ../logs/stability-rx.log
    if ($LASTEXITCODE -ne 0) { throw 'Receive stability failed' }
    & java --enable-preview --enable-native-access=ALL-UNNAMED -Xms128m -Xmx768m -cp $owners l1j.server.server.model.network.core.connection.CUserSocketStabilityWindowsTest 2>&1 | Tee-Object -FilePath ../logs/stability-java.log
    if ($LASTEXITCODE -ne 0) { throw 'Production Java stability failed' }
} finally { Pop-Location }
