from pathlib import Path
import argparse,subprocess,tempfile
p=argparse.ArgumentParser();p.add_argument('--source-root',type=Path,required=True);a=p.parse_args()
here=Path(__file__).parent;core=a.source_root/'src/l1j/server/server/model/network/core'
with tempfile.TemporaryDirectory(prefix='extended-trace-gap-') as tmp:
 subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--enable-preview','-source','21','-target','21','-encoding','UTF-8','-d',tmp,str(core/'CIOConfig.java'),str(core/'CNetworkDiagnostics.java'),str(here/'TraceIdentityGap.java')],check=True)
 subprocess.run(['java','--enable-preview','-Dctransport.diagnostics.enabled=true','-cp',tmp,'l1j.server.server.model.network.core.TraceIdentityGap'],check=True)
