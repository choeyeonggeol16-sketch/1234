$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
& .\build\CTransport_SelfTest.exe
if ($LASTEXITCODE -ne 0) { throw 'Native Windows loopback failed' }
$core = 'src/l1j/server/server/model/network/core'
$pss = 'src/l1j/server/server/model/character/pss/window/PssWindowTransport.java'
New-Item -ItemType Directory -Force windows-classes | Out-Null
& javac --enable-preview --release 21 -encoding UTF-8 -d windows-classes "$core/CTransportTask.java" "$core/CTransportConfig.java" "$core/CTransportServer.java" "$core/CTransportSocket.java" "$core/CTransportEndpoint.java" "$core/CIOPacketCodec.java" $pss tests/windows-stubs/CIOUserSocket.java tests/CTransportWindowsSmokeTest.java tests/CUserSocketWindowsTest.java tests/CIOPacketCodecReference.java
if ($LASTEXITCODE -ne 0) { throw 'FFM Windows smoke compilation failed' }
$classes = Join-Path $root 'windows-classes'
Push-Location build
try {
    & java --enable-preview --enable-native-access=ALL-UNNAMED -cp $classes l1j.server.server.model.network.core.CTransportWindowsSmokeTest
    if ($LASTEXITCODE -ne 0) { throw 'FFM/PSS Windows smoke failed' }
    & java --enable-preview --enable-native-access=ALL-UNNAMED -cp $classes l1j.server.server.model.network.core.CUserSocketWindowsTest
    if ($LASTEXITCODE -ne 0) { throw 'L1 user-socket/codec/completion Windows test failed' }
} finally { Pop-Location }

# Compile the actual production CIOUserSocket separately from the FFM smoke
# fixture. Only game/account/actor/scheduler/handler/filter dependencies differ.
New-Item -ItemType Directory -Force owner-classes | Out-Null
$ownerSources = Get-Content tests/owner-production-sources.txt
$ownerFixtures = (Get-ChildItem tests/owner-fixtures/*.java).FullName
& javac --enable-preview --release 21 -encoding UTF-8 -d owner-classes $ownerSources $ownerFixtures tests/CIOPacketCodecReference.java tests/CUserSocketOwnerWindowsTest.java
if ($LASTEXITCODE -ne 0) { throw 'Production user-socket owner compilation failed' }
$ownerClasses = Join-Path $root 'owner-classes'
Push-Location build
try {
    & java --enable-preview --enable-native-access=ALL-UNNAMED -cp $ownerClasses l1j.server.server.model.network.core.CUserSocketOwnerWindowsTest
    if ($LASTEXITCODE -ne 0) { throw 'Production user-socket lease/close test failed' }
} finally { Pop-Location }

& javac --release 21 -encoding UTF-8 -d windows-classes "$core/CUserSocketRegistry.java" "$core/CUserSocketDispatch.java" "$core/CUserSocketCloseTask.java" src/l1j/server/server/Opcodes.java src/l1j/server/server/model/network/utill/ConnectionState.java tests/usersocket/UserSocketContract.java
if ($LASTEXITCODE -ne 0) { throw 'L1 user-socket policy/registry compile failed' }
& java -cp windows-classes l1j.server.server.model.network.core.UserSocketContract
if ($LASTEXITCODE -ne 0) { throw 'L1 user-socket policy/registry test failed' }

& javac --release 21 -encoding UTF-8 -d windows-classes $pss tests/pss_window/TransportContract.java
if ($LASTEXITCODE -ne 0) { throw 'Independent PSS transport contract compilation failed' }
& java -cp windows-classes l1j.server.server.model.character.pss.window.TransportContract
if ($LASTEXITCODE -ne 0) { throw 'Independent PSS transport contract failed' }
& python tests/test_pss_independent.py
if ($LASTEXITCODE -ne 0) { throw 'Production PSS activation/pairing failed' }
& .\build\pss-client\PssClientLoadTest.exe
if ($LASTEXITCODE -ne 0) { throw 'Original standalone PSS client load failed' }
