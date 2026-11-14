$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
& .\build\CTransport_SelfTest.exe
if ($LASTEXITCODE -ne 0) { throw 'Native Windows loopback failed' }
$core = 'src/l1j/server/server/model/network/core'
$pss = 'src/l1j/server/server/model/character/pss/window/PssWindowTransport.java'
New-Item -ItemType Directory -Force windows-classes | Out-Null
& javac --enable-preview --release 21 -encoding UTF-8 -d windows-classes "$core/broadcast/CAsyncSendConstBuffer.java" "$core/broadcast/CAsyncSendConstBufferExceptSelf.java" "$core/execution/CIOObject.java" "$core/config/CIOConfig.java" "$core/transport/CIOServer.java" "$core/transport/CSocket.java" "$core/transport/CIOServerEndpoint.java" "$core/transport/CIOPacketCodec.java" "$core/diagnostics/CPerfMon.java" $pss tests/windows-stubs/CUserSocket.java tests/CTransportWindowsSmokeTest.java tests/CUserSocketWindowsTest.java tests/CIOPacketCodecReference.java tests/CTransportSessionWindowsTest.java
if ($LASTEXITCODE -ne 0) { throw 'FFM Windows smoke compilation failed' }
$classes = Join-Path $root 'windows-classes'
Push-Location build
try {
    & java --enable-preview --enable-native-access=ALL-UNNAMED -cp $classes l1j.server.server.model.network.core.transport.CTransportWindowsSmokeTest
    if ($LASTEXITCODE -ne 0) { throw 'FFM/PSS Windows smoke failed' }
    & java --enable-preview --enable-native-access=ALL-UNNAMED -cp $classes l1j.server.server.model.network.core.transport.CUserSocketWindowsTest
    if ($LASTEXITCODE -ne 0) { throw 'L1 user-socket/codec/completion Windows test failed' }
    & java --enable-preview --enable-native-access=ALL-UNNAMED -cp $classes l1j.server.server.model.network.core.transport.CTransportSessionWindowsTest
    if ($LASTEXITCODE -ne 0) { throw 'Account/MAC/performance/concurrent session Windows test failed' }
} finally { Pop-Location }

# Compile the actual production CUserSocket separately from the FFM smoke
# fixture. Only game/account/actor/scheduler/handler/filter dependencies differ.
New-Item -ItemType Directory -Force owner-classes | Out-Null
$ownerSources = Get-Content tests/owner-production-sources.txt
$ownerFixtures = (Get-ChildItem tests/owner-fixtures/*.java).FullName
& javac --enable-preview --release 21 -encoding UTF-8 -d owner-classes $ownerSources $ownerFixtures tests/CIOPacketCodecReference.java tests/CUserSocketOwnerWindowsTest.java tests/CSharedBroadcastWindowsTest.java tests/CUserSocketSafeSendWindowsTest.java tests/PoolReclamationWindowsTest.java tests/PoolDiagnosticsTest.java tests/ReferenceDiagnosticsTest.java tests/ReferenceDiagnosticsWindowsTest.java
if ($LASTEXITCODE -ne 0) { throw 'Production user-socket owner compilation failed' }
$ownerClasses = Join-Path $root 'owner-classes'
Push-Location build
try {
    & java --enable-preview --enable-native-access=ALL-UNNAMED -cp $ownerClasses l1j.server.server.model.network.core.connection.CUserSocketOwnerWindowsTest
    if ($LASTEXITCODE -ne 0) { throw 'Production user-socket lease/close test failed' }
    & java --enable-preview --enable-native-access=ALL-UNNAMED -cp $ownerClasses l1j.server.server.model.network.core.connection.CSharedBroadcastWindowsTest
    if ($LASTEXITCODE -ne 0) { throw 'Shared broadcast FIFO/keys/lifetime Windows test failed' }
    & java --enable-preview --enable-native-access=ALL-UNNAMED -cp $ownerClasses l1j.server.server.model.network.core.connection.CUserSocketSafeSendWindowsTest
    if ($LASTEXITCODE -ne 0) { throw 'SafeSend and close reason Windows test failed' }
    & java --enable-preview --enable-native-access=ALL-UNNAMED -cp $ownerClasses l1j.server.server.model.network.core.connection.PoolReclamationWindowsTest
    if ($LASTEXITCODE -ne 0) { throw 'Native/Java pool reclamation test failed' }
    & java --enable-preview --enable-native-access=ALL-UNNAMED -cp $ownerClasses l1j.server.server.model.network.core.connection.ReferenceDiagnosticsWindowsTest
    if ($LASTEXITCODE -ne 0) { throw 'Native reference verifier Windows test failed' }
} finally { Pop-Location }

New-Item -ItemType Directory -Force copy-classes | Out-Null
$copyFixtures = (Get-ChildItem tests/send-copy-contract/*.java).FullName
& javac --enable-preview --release 21 -encoding UTF-8 -d copy-classes "$core/transport/CSocket.java" "$core/execution/CIOObject.java" $copyFixtures
if ($LASTEXITCODE -ne 0) { throw 'SEND copy/close contract compilation failed' }
& java --enable-preview -cp copy-classes l1j.server.server.model.network.core.transport.SendCopyContract
if ($LASTEXITCODE -ne 0) { throw 'SEND copy/close lease contract failed' }

New-Item -ItemType Directory -Force policy-classes | Out-Null
& javac --enable-preview --release 21 -encoding UTF-8 -cp owner-classes -d policy-classes "$core/connection/CUserSocketRegistry.java" "$core/connection/CUserSocketCloseTask.java" tests/usersocket/UserSocketContract.java
if ($LASTEXITCODE -ne 0) { throw 'L1 user-socket policy/registry compile failed' }
& java --enable-preview -cp 'policy-classes;owner-classes' l1j.server.server.model.network.core.connection.UserSocketContract
if ($LASTEXITCODE -ne 0) { throw 'L1 user-socket policy/registry test failed' }

& javac --release 21 -encoding UTF-8 -d windows-classes $pss tests/pss_window/TransportContract.java
if ($LASTEXITCODE -ne 0) { throw 'Independent PSS transport contract compilation failed' }
& java -cp windows-classes l1j.server.server.model.character.pss.window.TransportContract
if ($LASTEXITCODE -ne 0) { throw 'Independent PSS transport contract failed' }
& python tests/test_pss_independent.py
if ($LASTEXITCODE -ne 0) { throw 'Production PSS activation/pairing failed' }
& .\build\pss-client\PssClientLoadTest.exe
if ($LASTEXITCODE -ne 0) { throw 'Original standalone PSS client load failed' }

# Followup diagnostics and broadcast differential contracts.
@'
ctransport.macLimit=3
ctransport.loginTimeoutMs=1234
ctransport.diagnostics.maxConnections=100
'@ | Set-Content -Encoding UTF8 diagnostic-test.properties
& javac --enable-preview --release 21 -encoding UTF-8 -cp owner-classes -d owner-classes tests/NetworkDiagnosticsTest.java
if ($LASTEXITCODE -ne 0) { throw 'Diagnostics test compilation failed' }
& java --enable-preview '-Dctransport.config=diagnostic-test.properties' '-Dctransport.diagnostics.maxConnections=2' -cp owner-classes l1j.server.server.model.network.core.diagnostics.NetworkDiagnosticsTest
if ($LASTEXITCODE -ne 0) { throw 'Diagnostics/config test failed' }
& javac --enable-preview --release 21 -encoding UTF-8 -cp owner-classes -d owner-classes tests/ExtendedPacketDiagnosticsTest.java
if ($LASTEXITCODE -ne 0) { throw 'Extended diagnostics test compilation failed' }
& java --enable-preview '-Dctransport.diagnostics.enabled=true' -cp owner-classes l1j.server.server.model.network.core.diagnostics.ExtendedPacketDiagnosticsTest
if ($LASTEXITCODE -ne 0) { throw 'Extended command diagnostics test failed' }
& java --enable-preview '-Dctransport.diagnostics.enabled=false' -cp owner-classes l1j.server.server.model.network.core.diagnostics.ExtendedPacketDiagnosticsTest disabled
if ($LASTEXITCODE -ne 0) { throw 'Disabled extended diagnostics test failed' }
& java --enable-preview -cp owner-classes l1j.server.server.model.network.core.diagnostics.PoolDiagnosticsTest
if ($LASTEXITCODE -ne 0) { throw 'Pool diagnostic classification failed' }
& java --enable-preview -cp owner-classes l1j.server.server.model.network.core.diagnostics.ReferenceDiagnosticsTest
if ($LASTEXITCODE -ne 0) { throw 'Reference diagnostic rendering failed' }

# Production Swing window and display queue. Only game/DB bindings are fixtures.
New-Item -ItemType Directory -Force manager-classes | Out-Null
$managerFixtures = (Get-ChildItem tests/manager-fixtures/*.java).FullName
& javac --enable-preview --release 21 -encoding UTF-8 -cp owner-classes -d manager-classes src/l1j/server/swing/chocco.java src/l1j/server/swing/ManagerLogBuffer.java src/l1j/server/server/utils/SystemUtil.java $managerFixtures tests/ManagerWindowTest.java tests/WindowsUiMemory.java tests/ManagerLogBufferTest.java
if ($LASTEXITCODE -ne 0) { throw 'Production manager compilation failed' }
& java --enable-preview --enable-native-access=ALL-UNNAMED -Xmx192m '-Djava.awt.headless=false' -cp 'manager-classes;owner-classes' l1j.server.swing.ManagerWindowTest
if ($LASTEXITCODE -ne 0) { throw 'Actual Windows manager resize/memory test failed' }
New-Item -ItemType Directory -Force broadcast-classes | Out-Null
$broadcastFixtures = (Get-ChildItem tests/broadcast-followup/*.java).FullName
& javac --enable-preview --release 21 -encoding UTF-8 -d broadcast-classes "$core/broadcast/CTLMFilter.java" "$core/broadcast/CSharedBroadcast.java" src/l1j/server/server/Opcodes.java "$core/sc/packet/SC_MoveChar.java" "$core/sc/packet/SC_ChangeHeading.java" "$core/sc/packet/SC_RemoveObject.java" "$core/sc/packet/SC_DoActionGFX.java" "$core/sc/packet/SC_SkillSound.java" $broadcastFixtures
if ($LASTEXITCODE -ne 0) { throw 'Broadcast test compilation failed' }
& java --enable-preview -cp broadcast-classes l1j.server.server.model.network.core.broadcast.BroadcastFollowupTest
if ($LASTEXITCODE -ne 0) { throw 'Broadcast filters/bytes/cleanup failed' }
