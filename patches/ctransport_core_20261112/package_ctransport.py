from pathlib import Path
import hashlib,json,shutil,zipfile
ROOT=Path(__file__).resolve().parent
REPO=Path('/workspace/work/aden-nc-port-20261112/repo')
PATCH=REPO/'patches/ctransport_core_20261112'
BASE=REPO/'downloads/RKern_Aden_CIO_CORE_FULL_SRC_20261112.zip'
SHA=lambda data:hashlib.sha256(data).hexdigest()
if PATCH.exists():shutil.rmtree(PATCH)
PATCH.mkdir(parents=True)
with zipfile.ZipFile(BASE) as z:old={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
current={str(p.relative_to(ROOT)):p.read_bytes() for p in sorted((ROOT/'src').rglob('*')) if p.is_file()}
changed=[p for p,b in current.items() if old.get(p)!=b];deleted=sorted(set(old)-set(current))
assert deleted==sorted(['src/l1j/server/server/model/network/core/'+n for n in ['CIOTransportConfig.java','CIOWinServer.java','CIOWinSocket.java']]),deleted
for name in changed:
 out=PATCH/name;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(current[name])
shutil.copytree(ROOT/'native',PATCH/'native')
for name in ['native_engine_test.cpp','fake_windows.hpp','run_native_tests.py','test_java_batch.py','TransportConfigTest.java','verify_native_binary.py','CTransportWindowsSmokeTest.java','run_windows.ps1','windows-stubs']:
 dest=PATCH/'tests'/name;dest.parent.mkdir(exist_ok=True)
 if (ROOT/'tests'/name).is_dir():shutil.copytree(ROOT/'tests'/name,dest)
 else:shutil.copy2(ROOT/'tests'/name,dest)
for name in ['scope','trace_restore_scope.py','recover_server_core.py','server-rtti.json','L2Server.exe.rtti-disasm.txt','L2Server(10).exe.rtti-disasm.txt','hp-batch-worker-excerpt.txt']:
 dest=PATCH/'research'/name;dest.parent.mkdir(exist_ok=True)
 if (ROOT/'research'/name).is_dir():shutil.copytree(ROOT/'research'/name,dest)
 else:shutil.copy2(ROOT/'research'/name,dest)
for name in ['CTransport.dll','CTransport.lib','CTransport_SelfTest.exe','native-build.json']:
 out=PATCH/'build'/name;out.parent.mkdir(exist_ok=True);shutil.copy2(ROOT/'build'/name,out)
for name in ['native-test.log','native-test-build.log','native-suite.log','tsan-test.log','tsan-test-build.log','tsan-suite.log','full-build.log','java-suite.log','transport-config-test.log','native-binary-test.log','windows-build.log','windows-selftest-build.log','build-manifest.log','scope-analysis.log','rtti-analysis.log','windows-smoke-compile.log']:
 out=PATCH/'logs'/name;out.parent.mkdir(exist_ok=True);shutil.copy2(ROOT/'logs'/name,out)
shutil.copy2(ROOT/'README_CTRANSPORT.md',PATCH/'README.md')
shutil.copy2(ROOT/'package_ctransport.py',PATCH/'package_ctransport.py')
(PATCH/'changed-files.txt').write_text('\n'.join(changed)+'\n')
(PATCH/'deleted-files.txt').write_text('\n'.join(deleted)+'\n')
shutil.copy2(ROOT/'REPORT_CTRANSPORT.md',REPO/'reports/CTRANSPORT_CORE_20261112.md')
dllsha=SHA((ROOT/'build/CTransport.dll').read_bytes())
manifest={'base_revision':'c76e7b2753876fc8bb1aac4075e78a5d31bf522f','branch':'ctransport-core-20261112','dll':'CTransport.dll','dll_sha256':dllsha,'abi':'0x20000','mandatory':True,'fallback':False,'completion_batch':128,'source_files':len(current),'java_files':sum(n.endswith('.java') for n in current),'changed_files':changed,'deleted_files':deleted,'additional_sql':False,'recovered_structures':['owner-index CAS buffer rings','8K/16K families','shared receive COW/unique compaction','send FIFO','linger0 close','completion task objects','50ms/200-slot timer wheel and overflow heap'],'adaptations':['external FFM producer registration','bounded exhaustion rejection','indexed cancellation','64-bit monotonic clock','terminal callback after I/O drain','existing L1 codec/game-owner contract'],'windows_evidence':'GitHub Windows workflow; recorded separately after execution'}
(PATCH/'MANIFEST.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
checks=[]
for p in sorted(PATCH.rglob('*')):
 if p.is_file():checks.append(SHA(p.read_bytes())+'  '+str(p.relative_to(PATCH)))
(PATCH/'SHA256SUMS').write_text('\n'.join(checks)+'\n')
archives={}
for name,items in [('CTransport_Aden_FULL_SRC_20261112.zip',[(p,b) for p,b in current.items()]),('CTransport_CORE_20261112.zip',[(str(p.relative_to(PATCH.parent)),p.read_bytes()) for p in sorted(PATCH.rglob('*')) if p.is_file()])]:
 dest=REPO/'downloads'/name
 with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED,compresslevel=6)as z:
  for n,b in items:z.writestr(n,b)
 with zipfile.ZipFile(dest)as z:
  assert z.testzip() is None
  for n,b in items:assert z.read(n)==b
  if name.startswith('CTransport_Aden'):assert len(z.namelist())==len(current)
 digest=SHA(dest.read_bytes());Path(str(dest)+'.sha256').write_text(digest+'  '+name+'\n');archives[name]=digest
# Reproduce all sources from the previous full archive + updates + deletions.
patched=old.copy()
for name in deleted:patched.pop(name)
for name in changed:patched[name]=current[name]
assert patched==current
assert not any('hpsocket' in b.decode(errors='ignore') or 'rkern.cio.transport' in b.decode(errors='ignore') or 'AsynchronousSocketChannel' in b.decode(errors='ignore') or 'AsynchronousServerSocketChannel' in b.decode(errors='ignore') for n,b in current.items() if n.endswith('.java'))
(PATCH/'logs/package-test.log').write_text('PASS source CRC/every byte; previous archive + changes/deletions reproduces all files.\nPASS three obsolete backend source files deleted; no selector, fallback or NIO2 socket path.\n'+json.dumps({'source_archive_sha256':archives['CTransport_Aden_FULL_SRC_20261112.zip'],'dll_sha256':dllsha})+'\n')
# Package-test log is intentionally outside the archive checksum set; regenerate
# the manifest checksum after writing it, then refresh native bundle.
checks=[]
for p in sorted(PATCH.rglob('*')):
 if p.is_file() and p.name!='SHA256SUMS':checks.append(SHA(p.read_bytes())+'  '+str(p.relative_to(PATCH)))
(PATCH/'SHA256SUMS').write_text('\n'.join(checks)+'\n')
dest=REPO/'downloads/CTransport_CORE_20261112.zip'
with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED,compresslevel=6)as z:
 for p in sorted(PATCH.rglob('*')):
  if p.is_file():z.write(p,str(p.relative_to(PATCH.parent)))
with zipfile.ZipFile(dest)as z:assert z.testzip() is None
archives[dest.name]=SHA(dest.read_bytes());Path(str(dest)+'.sha256').write_text(archives[dest.name]+'  '+dest.name+'\n')
readme=REPO/'README.md';text=readme.read_text();heading='## 2026-11-12 — CTransport.dll 필수 코어 / 기존 전송 엔진 제거\n\n[**전체 src**](downloads/CTransport_Aden_FULL_SRC_20261112.zip) · [**DLL·C++·검사**](downloads/CTransport_CORE_20261112.zip) · [구현·근거·적용 안내](reports/CTRANSPORT_CORE_20261112.md)\n\n스레드별8K/16K 원형 풀, 공유 RX/COW,128배치, 작업 큐와50ms/200칸 타이머 휠을 구현했습니다. 게임 접속과 PSS UI 연결 모두 같은 DLL을 필수 사용합니다. 기존 전송 엔진 선택/NIO2 경로는 제거했습니다. 실제 게임 클라이언트·운영 DB 부하는 별도 검증 대상입니다.\n\n---\n\n'
if not text.startswith(heading):readme.write_text(heading+text)
print(json.dumps({'archives':archives,'dll_sha256':dllsha,'source_files':len(current),'java_files':manifest['java_files'],'changed':len(changed),'deleted':deleted},indent=2))
