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
changed=[p for p,b in current.items() if old.get(p)!=b]
# This file equals the pre-port baseline but must replace the last native-PSS patch.
forced='src/l1j/server/server/model/character/pss/window/PssWindowTransport.java'
if forced not in changed:changed.append(forced)
changed.sort()
deleted=sorted(set(old)-set(current))
assert deleted==sorted(['src/l1j/server/server/model/network/core/'+n for n in ['CIOTransportConfig.java','CIOWinServer.java','CIOWinSocket.java']]),deleted
for name in changed:
 out=PATCH/name;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(current[name])
shutil.copytree(ROOT/'native',PATCH/'native')
for name in ['native_engine_test.cpp','fake_windows.hpp','run_native_tests.py','test_java_batch.py','TransportConfigTest.java','verify_native_binary.py','CTransportWindowsSmokeTest.java','run_windows.ps1','windows-stubs','pss_window','test_pss_independent.py','pss_client_load.c']:
 dest=PATCH/'tests'/name;dest.parent.mkdir(exist_ok=True)
 if (ROOT/'tests'/name).is_dir():shutil.copytree(ROOT/'tests'/name,dest)
 else:shutil.copy2(ROOT/'tests'/name,dest)
for name in ['scope','trace_restore_scope.py','find_core_initializers.py','recover_server_core.py','server-rtti.json','L2Server.exe.rtti-disasm.txt','L2Server(10).exe.rtti-disasm.txt','hp-batch-worker-excerpt.txt']:
 dest=PATCH/'research'/name;dest.parent.mkdir(exist_ok=True)
 if (ROOT/'research'/name).is_dir():shutil.copytree(ROOT/'research'/name,dest)
 else:shutil.copy2(ROOT/'research'/name,dest)
for name in ['CTransport.dll','CTransport.lib','CTransport_SelfTest.exe','native-build.json']:
 out=PATCH/'build'/name;out.parent.mkdir(exist_ok=True);shutil.copy2(ROOT/'build'/name,out)
shutil.copytree(ROOT/'build/pss-client',PATCH/'build/pss-client')
for name in ['native-test.log','native-test-build.log','native-suite.log','tsan-test.log','tsan-test-build.log','tsan-suite.log','full-build.log','java-suite.log','transport-config-test.log','native-binary-test.log','windows-build.log','windows-selftest-build.log','build-manifest.log','scope-analysis.log','rtti-analysis.log','windows-smoke-compile.log','windows-actual-test.log','pss-independent-contract.log','pss-pairing-contract.log','pss-client-load-build.log']:
 out=PATCH/'logs'/name;out.parent.mkdir(exist_ok=True);shutil.copy2(ROOT/'logs'/name,out)
shutil.copy2(ROOT/'WINDOWS_VERIFICATION.json',PATCH/'WINDOWS_VERIFICATION.json')
shutil.copy2(ROOT/'README_CTRANSPORT.md',PATCH/'README.md')
shutil.copy2(ROOT/'package_ctransport.py',PATCH/'package_ctransport.py')
(PATCH/'changed-files.txt').write_text('\n'.join(changed)+'\n')
(PATCH/'deleted-files.txt').write_text('\n'.join(deleted)+'\n')
shutil.copy2(ROOT/'REPORT_PSS_INDEPENDENT.md',REPO/'reports/PSS_INDEPENDENT_RESTORE_20261112.md')
dllsha=SHA((ROOT/'build/CTransport.dll').read_bytes())
manifest={'base_revision':'c76e7b2753876fc8bb1aac4075e78a5d31bf522f','branch':'ctransport-core-20261112','dll':'CTransport.dll','dll_sha256':dllsha,'abi':'0x30000','implementation_parent':'749a305e3b662852733fee766825e645870c33fc','transport_scope':'game','mandatory':True,'pss':{'enabled':True,'independent':True,'backend':'dedicated NIO2 group','io_threads':2,'expiry_threads':1,'default_port':'GAME_SERVER_PORT+1','property':'pss.uiBridge.port','game_inband_open':False,'client_dll_sha256':'35b476815c56b578e5e12a3d500589e72220ec6493bbc0041da541c588b861fa'},'fallback':False,'completion_batch':128,'source_files':len(current),'java_files':sum(n.endswith('.java') for n in current),'changed_files':changed,'deleted_files':deleted,'additional_sql':False,'recovered_structures':['pre-close/unregister/terminal phases','preallocated contiguous slab/pointer rings','registered-producer wheel ownership','100 inline+101st post / owner17 dispatch','1000-entry timer blocks and32-byte heap records','owner-index CAS buffer rings','8K/16K families','shared receive COW/unique compaction','send FIFO','linger0 close','completion task objects','50ms/200-slot timer wheel and overflow heap'],'adaptations':['external FFM producer registration','diagnosed exhaustion instead of intentional process crash','indexed cancellation','64-bit monotonic clock','terminal callback after I/O drain','existing L1 codec/game-owner contract'],'windows_evidence':json.loads((ROOT/'WINDOWS_VERIFICATION.json').read_text())}
(PATCH/'MANIFEST.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
checks=[]
for p in sorted(PATCH.rglob('*')):
 if p.is_file():checks.append(SHA(p.read_bytes())+'  '+str(p.relative_to(PATCH)))
(PATCH/'SHA256SUMS').write_text('\n'.join(checks)+'\n')
archives={}
for name,items in [('CTransport_Aden_FULL_SRC_20261112.zip',[(p,b) for p,b in current.items()]),('CTransport_CORE_20261112.zip',[(str(p.relative_to(PATCH.parent)),p.read_bytes()) for p in sorted(PATCH.rglob('*')) if p.is_file()])]:
 dest=REPO/'downloads'/name
 same=False
 if dest.exists() and name.startswith('CTransport_Aden'):
  with zipfile.ZipFile(dest)as z:same=len(z.namelist())==len(items) and all(z.read(n)==b for n,b in items)
 if not same:
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
assert not any('hpsocket' in b.decode(errors='ignore') or 'rkern.cio.transport' in b.decode(errors='ignore') for n,b in current.items() if n.endswith('.java'))
assert not any('AsynchronousSocketChannel' in b.decode(errors='ignore') or 'AsynchronousServerSocketChannel' in b.decode(errors='ignore') for n,b in current.items() if '/network/core/' in n and n.endswith('.java'))
pss=current['src/l1j/server/server/model/character/pss/window/PssWindowTransport.java'].decode()
assert 'AsynchronousChannelGroup' in pss and 'CTransport' not in pss
window=current['src/l1j/server/server/model/character/pss/window/PlaySupportWindow.java'].decode()
assert 'SC_PssNativeWindow' not in window and 'peer.requestOpen()' in window
assert 'PlaySupportWindow.startIndependent();' in current['src/l1j/server/Server.java'].decode()
(PATCH/'logs/package-test.log').write_text('PASS source CRC/every byte; previous archive + changes/deletions reproduces all files.\nPASS game backend has no selector/fallback; independent PSS NIO2 listener enabled with authenticated OPEN; no game in-band window command.\n'+json.dumps({'source_archive_sha256':archives['CTransport_Aden_FULL_SRC_20261112.zip'],'dll_sha256':dllsha})+'\n')
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
readme=REPO/'README.md';text=readme.read_text();heading='## 2026-11-12 — PSS 독립 연결 재활성화 / 게임 CTransport 유지\n\n[**전체 src**](downloads/CTransport_Aden_FULL_SRC_20261112.zip) · [**DLL·C++·검사**](downloads/CTransport_CORE_20261112.zip) · [PSS 복구·적용 안내](reports/PSS_INDEPENDENT_RESTORE_20261112.md)\n\nPSS 전용 포트·NIO2 그룹2개 스레드·만료 스레드1개를 복구하고 서버 시작 시 별도로 활성화합니다. 토큰 등록과 전용 채널 OPEN을 복구했으며 게임 연결의 설정창 명령을 사용하지 않습니다. 게임은 기존 CTransport.dll ABI3를 유지하고 PSS 기동/종료 실패와 분리합니다. 실제 게임 클라이언트·운영 DB 부하는 별도 검증 대상입니다.\n\n---\n\n'
if not text.startswith(heading.split('\n',1)[0]):readme.write_text(heading+text)
print(json.dumps({'archives':archives,'dll_sha256':dllsha,'source_files':len(current),'java_files':manifest['java_files'],'changed':len(changed),'deleted':deleted},indent=2))
