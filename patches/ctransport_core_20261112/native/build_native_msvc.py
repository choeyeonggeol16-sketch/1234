"""Build the shipping Windows x64 DLL with the MSVC C++ object ABI."""
from pathlib import Path
import argparse,hashlib,json,re,subprocess
root=Path(__file__).resolve().parents[1];p=argparse.ArgumentParser();p.add_argument('--reuse',action='store_true');a=p.parse_args()
out=root/'build';logs=root/'logs';out.mkdir(exist_ok=True);logs.mkdir(exist_ok=True)
sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
files={f.name:sha(f)for f in sorted((root/'native').glob('*'))if f.is_file()}
common=['cl','/nologo','/std:c++20','/O2','/EHsc','/MT','/permissive-','/Zc:__cplusplus','/utf-8','/DNOMINMAX','/D_CRT_SECURE_NO_WARNINGS','/Brepro']
commands=[common+['/LD','native/CTransport.cpp','/Fobuild/CTransport.obj','/Febuild/CTransport.dll','/link','/Brepro','/IMPLIB:build/CTransport.lib','Ws2_32.lib','Psapi.lib'],common+['native/windows_loopback_test.cpp','/Fobuild/CTransport_SelfTest.obj','/Febuild/CTransport_SelfTest.exe','/link','/Brepro','build/CTransport.lib','Ws2_32.lib']]
previous=json.loads((out/'native-build.json').read_text())if(out/'native-build.json').exists()else{}
reuse=a.reuse and previous.get('target')=='x86_64-windows-msvc' and previous.get('files')==files and all((out/n).exists()and sha(out/n)==previous.get(k)for n,k in [('CTransport.dll','dll_sha256'),('CTransport_SelfTest.exe','selftest_sha256')])
if not reuse:
 for command,name in zip(commands,['object-memory-msvc-build.log','object-memory-msvc-selftest-build.log']):
  with(logs/name).open('w')as log:subprocess.run(command,cwd=root,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=180)
 dllsha=sha(out/'CTransport.dll');java=root/'src/l1j/server/server/model/network/core/CIOConfig.java'
 text,count=re.subn(r'(DLL_SHA256 = ")[0-9a-f]{64}(";)',lambda m:m[1]+dllsha+m[2],java.read_text());assert count==1;java.write_text(text)
 manifest={'compiler':'MSVC x64 C++20 /MT /Brepro','target':'x86_64-windows-msvc','commands':commands,'files':files,'dll_sha256':dllsha,'selftest_sha256':sha(out/'CTransport_SelfTest.exe'),'windows_execution_tested':False,'internal_vtable_profile':'MSVC x64 MemoryObject4,CIOObject12,CSocket24,CUserSocket26;slot15 int margin,slot24 raw pointer getter,slot25 socket number'}
 (out/'native-build.json').write_text(json.dumps(manifest,indent=2)+'\n')
else:
 assert previous['dll_sha256']in(root/'src/l1j/server/server/model/network/core/CIOConfig.java').read_text(),'shipped Java DLL pin mismatch'
abi_command=common+['tests/ObjectAbiTest.cpp','/UNDEBUG','/Fobuild/ObjectAbiTest.obj','/Febuild/ObjectAbiTest.exe','/link','/Brepro','Ws2_32.lib','Psapi.lib']
with(logs/'object-memory-msvc-abi-build.log').open('w')as log:subprocess.run(abi_command,cwd=root,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=180)
with(logs/'object-memory-msvc-abi.log').open('w')as log:subprocess.run([str(out/'ObjectAbiTest.exe')],cwd=root,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=30)
print(json.dumps({'reused':reuse,'dll_sha256':sha(out/'CTransport.dll'),'compiler':'MSVC x64','abi_test':'passed'}))
