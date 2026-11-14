from pathlib import Path
import argparse,os,subprocess,tempfile
r=Path(__file__).resolve().parents[1];core=r/'src/l1j/server/server/model/network/core'
p=argparse.ArgumentParser();p.add_argument('--production-classes',type=Path);p.add_argument('--libs',type=Path);a=p.parse_args()
with tempfile.TemporaryDirectory(prefix='extended-diagnostics-') as tmp:
 subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--enable-preview','-source','21','-target','21','-encoding','UTF-8','-d',tmp,str(core/'config/CIOConfig.java'),str(core/'diagnostics/CNetworkDiagnostics.java'),str(r/'tests/ExtendedPacketDiagnosticsTest.java')],check=True)
 for enabled in [True,False]:
  subprocess.run(['java','--enable-preview','-Dctransport.diagnostics.enabled='+str(enabled).lower(),'-cp',tmp,'l1j.server.server.model.network.core.diagnostics.ExtendedPacketDiagnosticsTest',*([] if enabled else ['disabled'])],check=True)
if a.production_classes:
 assert a.libs is not None
 cp=os.pathsep.join([str(a.production_classes.resolve()),*[str(p.resolve()) for p in sorted(a.libs.glob('*.jar'))]])
 with tempfile.TemporaryDirectory(prefix='actual-extended-decoder-') as tmp:
  subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--enable-preview','-source','21','-target','21','-encoding','UTF-8','-cp',cp,'-d',tmp,str(r/'tests/ExtendedPacketDecoderTest.java')],check=True)
  subprocess.run(['java','--enable-preview','-cp',tmp+os.pathsep+cp,'l1j.server.server.model.network.core.connection.ExtendedPacketDecoderTest'],check=True)
