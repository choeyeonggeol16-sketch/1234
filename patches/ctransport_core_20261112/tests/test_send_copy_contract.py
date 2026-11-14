from pathlib import Path
import tempfile,subprocess
r=Path(__file__).resolve().parents[1];core=r/'src/l1j/server/server/model/network/core'
with tempfile.TemporaryDirectory(prefix='send-copy-contract-') as out:
 args=['java','-m','jdk.compiler/com.sun.tools.javac.Main','--enable-preview','-source','21','-target','21','-encoding','UTF-8','-d',out,str(core/'transport/CSocket.java'),str(core/'execution/CIOObject.java')]+[str(p)for p in sorted((r/'tests/send-copy-contract').glob('*.java'))]
 subprocess.run(args,check=True,timeout=30)
 subprocess.run(['java','--enable-preview','-cp',out,'l1j.server.server.model.network.core.transport.SendCopyContract'],check=True,timeout=30)
