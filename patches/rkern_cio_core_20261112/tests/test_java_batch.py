from pathlib import Path
import argparse,subprocess,tempfile
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--src',type=Path,required=True);p.add_argument('--libs',type=Path,required=True);p.add_argument('--build',type=Path,required=True);p.add_argument('--native-dll',type=Path,default=root/'build/RKernCIOTransport.dll');args=p.parse_args()
args.build.mkdir(exist_ok=True,parents=True);(root/'logs').mkdir(exist_ok=True)
sources=sorted(args.src.resolve().rglob('*.java'));cp=':'.join(str(f) for f in sorted(args.libs.resolve().glob('*.jar')))
with tempfile.TemporaryDirectory(prefix='nc-batch-java-') as tmp:
 opts=['--enable-preview','-source','21','-target','21','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(args.build.resolve()),*[str(f) for f in sources]]
 argfile=Path(tmp)/'compile.args';argfile.write_text('\n'.join('"'+s.replace('\\','\\\\').replace('"','\\"')+'"' for s in opts))
 with (root/'logs/full-build.log').open('w') as log:
  subprocess.run(['java','-Xmx1400m','-m','jdk.compiler/com.sun.tools.javac.Main','@'+str(argfile)],stdout=log,stderr=subprocess.STDOUT,check=True,timeout=120)
  log.write(f'PASS full Java21 type compilation: {len(sources)} production sources; zero errors.\n')
 testcp=str(args.build.resolve())+':'+cp
 subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--enable-preview','-source','21','-target','21','-cp',testcp,'-d',str(args.build.resolve()),str(root/'tests/TransportConfigTest.java')],check=True,timeout=30)
 with (root/'logs/transport-config-test.log').open('w') as log:
  subprocess.run(['java','--enable-preview','-cp',testcp,'l1j.server.server.model.network.core.TransportConfigTest',str(args.native_dll.resolve())],stdout=log,stderr=subprocess.STDOUT,check=True,timeout=30)
print(f'PASS {len(sources)} production Java sources and explicit/default transport choice with current DLL hash')
