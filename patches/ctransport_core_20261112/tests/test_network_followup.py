from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
core=r/'src/l1j/server/server/model/network/core'
def javac(out,files):
 subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--enable-preview','-source','21','-target','21','-encoding','UTF-8','-d',str(out),*map(str,files)],check=True)
with tempfile.TemporaryDirectory(prefix='network-followup-') as temp:
 out=Path(temp);config=out/'network.properties'
 config.write_text('ctransport.macLimit=3\nctransport.loginTimeoutMs=1234\nctransport.diagnostics.maxConnections=100\n',encoding='utf-8')
 javac(out,[core/'config/CIOConfig.java',core/'diagnostics/CNetworkDiagnostics.java',r/'tests/NetworkDiagnosticsTest.java'])
 subprocess.run(['java','--enable-preview','-Dctransport.config='+str(config),'-Dctransport.diagnostics.maxConnections=2','-cp',str(out),'l1j.server.server.model.network.core.diagnostics.NetworkDiagnosticsTest'],check=True)

with tempfile.TemporaryDirectory(prefix='broadcast-followup-') as temp:
 out=Path(temp)
 files=[core/'broadcast/CTLMFilter.java',core/'broadcast/CSharedBroadcast.java',r/'src/l1j/server/server/Opcodes.java']
 files += [core/'sc/packet'/name for name in ['SC_MoveChar.java','SC_ChangeHeading.java','SC_RemoveObject.java','SC_DoActionGFX.java','SC_SkillSound.java']]
 files += sorted((r/'tests/broadcast-followup').glob('*.java'))
 javac(out,files)
 subprocess.run(['java','-cp',str(out),'l1j.server.server.model.network.core.broadcast.BroadcastFollowupTest'],check=True)
