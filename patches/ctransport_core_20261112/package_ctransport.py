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
forced_files=['src/l1j/server/server/model/character/pss/window/PssWindowTransport.java',
              'src/l1j/server/server/Opcodes.java',
              'src/l1j/server/server/model/network/utill/ConnectionState.java']
for forced in forced_files:
 if forced not in changed:changed.append(forced)
for forced in (ROOT/'tests/owner-production-sources.txt').read_text().splitlines():
 if forced not in changed:changed.append(forced)
changed.sort()
obsolete=['CIOTransportConfig','CIOWinServer','CIOWinSocket','CIOUserSocket','CTransportServer','CTransportSocket','CTransportEndpoint','CTransportTask','CTransportConfig','CUserSocketDispatch']
deleted=sorted((set(old)-set(current))|{'src/l1j/server/server/model/network/core/'+n+'.java' for n in obsolete})
assert not set(deleted)&set(current)
assert set(old)-set(current)<=set(deleted)
for name in changed:
 out=PATCH/name;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(current[name])
shutil.copytree(ROOT/'native',PATCH/'native')
for name in ['native_engine_test.cpp','fake_windows.hpp','run_native_tests.py','test_java_batch.py','TransportConfigTest.java','verify_native_binary.py','CTransportWindowsSmokeTest.java','run_windows.ps1','windows-stubs','pss_window','test_pss_independent.py','pss_client_load.c','CUserSocketWindowsTest.java','CTransportSessionWindowsTest.java','CIOPacketCodecReference.java','CUserSocketOwnerWindowsTest.java','fake_l1_codec.hpp','owner-fixtures','owner-production-sources.txt','usersocket','send-copy-contract','test_send_copy_contract.py']:
 dest=PATCH/'tests'/name;dest.parent.mkdir(exist_ok=True)
 if (ROOT/'tests'/name).is_dir():shutil.copytree(ROOT/'tests'/name,dest)
 else:shutil.copy2(ROOT/'tests'/name,dest)
for name in ['scope','trace_restore_scope.py','find_core_initializers.py','recover_server_core.py','server-rtti.json','L2Server.exe.rtti-disasm.txt','L2Server(10).exe.rtti-disasm.txt','hp-batch-worker-excerpt.txt','recover_user_socket.py','user-socket','recover_registry.py','registry','network-audit','audit_network_abi5.py','build_network_audit_matrix.py','abi6','creature-serializer-audit','remaining-network-audit','send-close-audit','audit_send_close_abi8.py']:
 dest=PATCH/'research'/name;dest.parent.mkdir(exist_ok=True)
 if (ROOT/'research'/name).is_dir():shutil.copytree(ROOT/'research'/name,dest)
 else:shutil.copy2(ROOT/'research'/name,dest)
for name in ['CTransport.dll','CTransport.lib','CTransport_SelfTest.exe','native-build.json','CIOPacketCodec.dll']:
 out=PATCH/'build'/name;out.parent.mkdir(exist_ok=True);shutil.copy2(ROOT/'build'/name,out)
shutil.copytree(ROOT/'build/pss-client',PATCH/'build/pss-client')
for name in ['native-test.log','native-test-build.log','native-suite.log','tsan-test.log','tsan-test-build.log','tsan-suite.log','full-build.log','java-suite.log','transport-config-test.log','native-binary-test.log','windows-build.log','windows-selftest-build.log','build-manifest.log','scope-analysis.log','rtti-analysis.log','windows-smoke-compile.log','windows-actual-test.log','pss-independent-contract.log','pss-pairing-contract.log','pss-client-load-build.log','user-socket-analysis.log','user-socket-native-suite.log','user-socket-tsan-suite.log','user-socket-java-suite.log','user-socket-contract.log','user-socket-windows-compile.log','user-socket-binary-test.log','user-socket-build-manifest.log','user-socket-pss-preservation.log','abi5-native-suite.log','abi5-tsan-suite.log','abi5-java-suite.log','abi5-binary-test.log','abi5-build-manifest.log','abi5-windows-compile.log','abi5-owner-compile.log','abi5-pss-preservation.log','abi5-pss-contract.log','registry-analysis.log','abi5-windows-actual.log','network-audit.log']:
 out=PATCH/'logs'/name;out.parent.mkdir(exist_ok=True);shutil.copy2(ROOT/'logs'/name,out)
for source in sorted([*(ROOT/'logs').glob('abi6-*.log'),*(ROOT/'logs').glob('abi7-*.log'),*(ROOT/'logs').glob('abi8-*.log')]):
 if source.name in ('abi6-package.log','abi7-package.log','abi8-package.log'):continue
 shutil.copy2(source,PATCH/'logs'/source.name)
shutil.copy2(ROOT/'WINDOWS_VERIFICATION.json',PATCH/'WINDOWS_VERIFICATION.json')
shutil.copy2(ROOT/'README_CTRANSPORT.md',PATCH/'README.md')
shutil.copy2(ROOT/'REPORT_L1_USERSOCKET.md',PATCH/'REPORT_L1_USERSOCKET.md')
shutil.copy2(ROOT/'REPORT_ORIGINAL_NETWORK_ABI6.md',PATCH/'REPORT_ORIGINAL_NETWORK_ABI6.md')
shutil.copy2(ROOT/'REPORT_REMAINING_NETWORK_ABI7.md',PATCH/'REPORT_REMAINING_NETWORK_ABI7.md')
shutil.copy2(ROOT/'REPORT_SEND_CLOSE_ABI8.md',PATCH/'REPORT_SEND_CLOSE_ABI8.md')
shutil.copy2(ROOT/'REPORT_SEND_CLOSE_ABI8.md',REPO/'reports/CTRANSPORT_SEND_CLOSE_ABI8_20261113.md')
shutil.copy2(ROOT/'REPORT_REMAINING_NETWORK_ABI7.md',REPO/'reports/CTRANSPORT_REMAINING_NETWORK_ABI7_20261112.md')
shutil.copy2(ROOT/'REPORT_CTRANSPORT_BINARY_AUDIT_20261112.md',PATCH/'REPORT_CTRANSPORT_BINARY_AUDIT_20261112.md')
shutil.copy2(ROOT/'REPORT_ORIGINAL_NETWORK_ABI6.md',REPO/'reports/CTRANSPORT_ORIGINAL_STRUCTURE_ABI6_20261112.md')
shutil.copy2(ROOT/'package_ctransport.py',PATCH/'package_ctransport.py')
(PATCH/'changed-files.txt').write_text('\n'.join(changed)+'\n')
(PATCH/'deleted-files.txt').write_text('\n'.join(deleted)+'\n')
shutil.copy2(ROOT/'REPORT_PSS_INDEPENDENT.md',REPO/'reports/PSS_INDEPENDENT_RESTORE_20261112.md')
shutil.copy2(ROOT/'REPORT_L1_USERSOCKET.md',REPO/'reports/CTRANSPORT_L1_USERSOCKET_20261112.md')
dllsha=SHA((ROOT/'build/CTransport.dll').read_bytes())
manifest={'base_revision':'c76e7b2753876fc8bb1aac4075e78a5d31bf522f','branch':'ctransport-core-20261112','dll':'CTransport.dll','dll_sha256':dllsha,'abi':'0x80000','implementation_parent':'859396fe01585e72f88a750a1989d4279f886341','transport_scope':'game','mandatory':True,'pss':{'enabled':True,'independent':True,'backend':'dedicated NIO2 group','io_threads':2,'expiry_threads':1,'default_port':'GAME_SERVER_PORT+1','property':'pss.uiBridge.port','game_inband_open':False,'client_dll_sha256':'35b476815c56b578e5e12a3d500589e72220ec6493bbc0041da541c588b861fa'},'fallback':False,'completion_batch':128,'source_files':len(current),'java_files':sum(n.endswith('.java') for n in current),'changed_files':changed,'deleted_files':deleted,'additional_sql':False,'new_abi8_structures':['native SEND request and WRITE completion share CExecutionSerializerFast','L1 framed native SEND: first-plain policy, each-frame crypto, copied source and aggregate admission cap','Close linger(1,0) versus GracefulClose; drain/SD_SEND/peer FIN/deadline adapted for L1','Java CSocket copy/close failure notification retains source leases until downcall exits'],'new_abi7_structures':['Playing variant27 key=socket number at authentication; EnterChar variant28 key=account ID during character entry','native CPerfMon dedicated owner15, process CPU/system+process memory and population sampling','MAC configuration/verified-provider API and unidentified authentication denial when enabled','L1 account,character-select,enter,restart,logout and close hooks','bounded real Windows concurrent-session stress'],'new_abi6_structures':['CIOServer/Server and native socket class hierarchy','FD_ACCEPT event -> RegisterWait64 -> ListenThread17 -> null-overlapped IOCP -> accept/factory/Add/Initialize','raw SOCKET map and 8192-bucket user-number CObjectMap','native IP/MAC CSocketLimiter','CExecutionSerializerFast for write/close callbacks','OS socket buffers0, minCPU8/affinity, 2MiB send cap','per-peer ReportSocket and per-thread diagnostics'],'recovered_structures':['pre-close/unregister/terminal phases','preallocated contiguous slab/pointer rings','registered-producer wheel ownership','100 inline+101st post / owner17 dispatch','1000-entry timer blocks and32-byte heap records','owner-index CAS buffer rings','8K/16K families','shared receive COW/unique compaction','send FIFO','linger0 close','completion task objects','50ms/200-slot timer wheel and overflow heap'],'adaptations':['slot+generation handles','unchanged L1 codec function table','network grace predicates upcalled to Java','external FFM producer registration','diagnosed exhaustion instead of intentional process crash','indexed cancellation','64-bit monotonic clock','terminal callback after I/O drain','existing L1 codec/game-owner contract'],'user_socket':{'enabled_for_game':True,'native_states':['Header','Body','Stopped'],'wire_profile':'L1 2019 LE16, empty2 or10..65535','codec':'C++ directly calls unchanged CIOPacketCodec.dll; Java verifies symbol table once','registry':'native ordered active/retiring maps, slot+generation, lookup retain','network_deadlines':'native wheel: login, partial read, write stall; Java grace predicates via native upcall','execution':'native SEND/crypto/WRITE serializer; native READ direct or IOCP repost; L1 session owner for transitions','lifetime':'close-start/native IO terminal/packet lease/L1 cleanup','thread_identity':'native TLS owner query and L1 owner assertion','pss_unchanged_from':'2dfce4f; one unused experimental packet only has CIOUserSocket type references renamed'},'windows_evidence':json.loads((ROOT/'WINDOWS_VERIFICATION.json').read_text())}
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
for name in deleted:patched.pop(name,None)
for name in changed:patched[name]=current[name]
assert patched==current
assert not any('hpsocket' in b.decode(errors='ignore') or 'rkern.cio.transport' in b.decode(errors='ignore') for n,b in current.items() if n.endswith('.java'))
assert not any('AsynchronousSocketChannel' in b.decode(errors='ignore') or 'AsynchronousServerSocketChannel' in b.decode(errors='ignore') for n,b in current.items() if '/network/core/' in n and n.endswith('.java'))
pss=current['src/l1j/server/server/model/character/pss/window/PssWindowTransport.java'].decode()
assert 'AsynchronousChannelGroup' in pss and 'CTransport' not in pss
window=current['src/l1j/server/server/model/character/pss/window/PlaySupportWindow.java'].decode()
assert 'SC_PssNativeWindow' not in window and 'peer.requestOpen()' in window
assert 'PlaySupportWindow.startIndependent();' in current['src/l1j/server/Server.java'].decode()
assert 'nativeTerminated()' in current['src/l1j/server/server/model/network/core/CIOServer.java'].decode()
assert '_channel.isNativeIoDrained()' in current['src/l1j/server/server/model/network/core/CUserSocket.java'].decode()
assert 'class CUserSocketParser' in (ROOT/'native/user_socket.hpp').read_text()
assert 'class CUserSocket final : public CSocket' in (ROOT/'native/CTransport.cpp').read_text()
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
readme=REPO/'README.md';text=readme.read_text();title='## 2026-11-13 — CTransport ABI8 / native SEND·암호화·정상 종료'
heading=title+'\n\n[**전체 src**](downloads/CTransport_Aden_FULL_SRC_20261112.zip) · [**같은 버전 DLL·C++·검사**](downloads/CTransport_CORE_20261112.zip) · [**분석·구현 보고서**](reports/CTRANSPORT_SEND_CLOSE_ABI8_20261113.md)\n\n원본 SEND/WRITE 실행 순서와 Close/GracefulClose 차이를 확인해 반영했습니다. native 직렬 실행기에서 L1 프레임별 암호화·송신을 처리하고, 마지막 패킷 전송 후 정상 종료합니다. 복사 중 종료 때 Java 버퍼가 먼저 재사용되는 경합도 수정했습니다. ABI0x80000/66 exports의 src와 DLL을 함께 적용하고 clean build합니다. PSS 독립 연결·L1 codec DLL 유지, 추가 SQL 없음. [Windows 검증 기록](patches/ctransport_core_20261112/WINDOWS_VERIFICATION.json)을 확인하세요. 실게임·운영 DB·장시간 부하는 미검증입니다.\n\n이하 ABI7 이전 설명은 당시 구현의 이력입니다. 최신 역할·미검증 범위는 위 ABI8 보고서 기준입니다.\n\n---\n\n'
if not text.startswith(title):readme.write_text(heading+text)
print(json.dumps({'archives':archives,'dll_sha256':dllsha,'source_files':len(current),'java_files':manifest['java_files'],'changed':len(changed),'deleted':deleted},indent=2))
