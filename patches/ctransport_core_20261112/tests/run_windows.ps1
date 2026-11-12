$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
& .\build\CTransport_SelfTest.exe
if ($LASTEXITCODE -ne 0) { throw 'Native Windows loopback failed' }
$core = 'src/l1j/server/server/model/network/core'
$pss = 'src/l1j/server/server/model/character/pss/window/PssWindowTransport.java'
New-Item -ItemType Directory -Force windows-classes | Out-Null
& javac --enable-preview --release 21 -encoding UTF-8 -d windows-classes "$core/CTransportConfig.java" "$core/CTransportServer.java" "$core/CTransportSocket.java" "$core/CTransportEndpoint.java" $pss tests/windows-stubs/CIOUserSocket.java tests/CTransportWindowsSmokeTest.java
if ($LASTEXITCODE -ne 0) { throw 'FFM Windows smoke compilation failed' }
$classes = Join-Path $root 'windows-classes'
Push-Location build
try {
    & java --enable-preview --enable-native-access=ALL-UNNAMED -cp $classes l1j.server.server.model.network.core.CTransportWindowsSmokeTest
    if ($LASTEXITCODE -ne 0) { throw 'FFM/PSS Windows smoke failed' }
} finally { Pop-Location }
