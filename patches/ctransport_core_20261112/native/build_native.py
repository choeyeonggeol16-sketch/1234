from pathlib import Path
import argparse,hashlib,json,re,subprocess

root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--zig',type=Path,required=True);p.add_argument('--write-java-hash',action='store_true');args=p.parse_args()
out=root/'build';out.mkdir(exist_ok=True);logs=root/'logs';logs.mkdir(exist_ok=True)
common=[str(args.zig.resolve()),'c++','-target','x86_64-windows-gnu','-std=c++17','-O2','-g0','-static']
commands=[common+['-shared','-fno-sanitize=undefined',str(root/'native/CTransport.cpp'),'-lws2_32','-o',str(out/'CTransport.dll')],
 common+[str(root/'native/windows_loopback_test.cpp'),str(out/'CTransport.lib'),'-lws2_32','-o',str(out/'CTransport_SelfTest.exe')]]
for command,name in zip(commands,['windows-build.log','windows-selftest-build.log']):
 with (logs/name).open('w') as log:subprocess.run(command,cwd=root,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=180)
sha=hashlib.sha256((out/'CTransport.dll').read_bytes()).hexdigest()
java=root/'src/l1j/server/server/model/network/core/CIOConfig.java'
if args.write_java_hash:
 text=java.read_text();text,count=re.subn(r'(DLL_SHA256 = ")[0-9a-f]{64}(";)',lambda m:m[1]+sha+m[2],text)
 assert count==1;java.write_text(text)
version=subprocess.check_output([str(args.zig.resolve()),'version'],text=True).strip()
manifest={'compiler':'zig '+version,'target':'x86_64-windows-gnu','commands':commands,
 'files':{f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted((root/'native').glob('*')) if f.is_file()},
 'dll_sha256':sha,'selftest_sha256':hashlib.sha256((out/'CTransport_SelfTest.exe').read_bytes()).hexdigest(),
 'windows_execution_tested':False}
(out/'native-build.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps({'dll_sha256':sha,'compiler':manifest['compiler'],'java_pin_updated':args.write_java_hash}))
