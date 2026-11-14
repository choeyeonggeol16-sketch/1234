from pathlib import Path
import hashlib,json,shutil,zipfile,runpy
ROOT=Path(__file__).resolve().parent
REPO=Path('/workspace/work/aden-nc-port-20261112/repo')
PATCH=REPO/'patches/ctransport_core_20261112'
BASE=REPO/'downloads/RKern_Aden_CIO_CORE_FULL_SRC_20261112.zip'
SHA=lambda data:hashlib.sha256(data).hexdigest()
if PATCH.exists():shutil.rmtree(PATCH)
PATCH.mkdir(parents=True)
with zipfile.ZipFile(BASE) as z:old={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
current={str(p.relative_to(ROOT)):p.read_bytes() for p in sorted((ROOT/'src').rglob('*')) if p.is_file()}
names_scope=runpy.run_path(str(ROOT/'tests/verify_extended_stability_scope.py'))
assert names_scope['verify'](ROOT)==1712
# Build the full source archive first; the Windows evidence and native bundle
# then name exactly this archive, avoiding a stale previous-source SHA.
source_archive=REPO/'downloads/CTransport_Aden_FULL_SRC_20261112.zip'
with zipfile.ZipFile(source_archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for name,data in current.items():
  info=zipfile.ZipInfo(name,date_time=(2026,11,13,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
  z.writestr(info,data,compress_type=zipfile.ZIP_DEFLATED,compresslevel=6)
verification=json.loads((ROOT/'WINDOWS_VERIFICATION.json').read_text())
assert verification['status']=='passed' and verification['abi']=='0xa0000'
verification['source_archive_sha256']=SHA(source_archive.read_bytes())
(ROOT/'WINDOWS_VERIFICATION.json').write_text(json.dumps(verification,ensure_ascii=False,indent=2)+'\n')
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
obsolete=['CIOTransportConfig','CIOWinServer','CIOWinSocket','CIOUserSocket','CTransportServer','CTransportSocket','CTransportEndpoint','CTransportTask','CTransportConfig','CUserSocketDispatch','CIOSocketFactory']
deleted=sorted((set(old)-set(current))|{'src/l1j/server/server/model/network/core/'+n+'.java' for n in obsolete})
deleted=sorted(set(deleted)|set(json.loads((ROOT/'research/package-layout-20261114/BASELINE.json').read_text())['path_mapping']))
assert not set(deleted)&set(current)
assert set(old)-set(current)<=set(deleted)
for name in changed:
 out=PATCH/name;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(current[name])
shutil.copytree(ROOT/'native',PATCH/'native')
shutil.copytree(ROOT/'config',PATCH/'config')
for name in ['native_engine_test.cpp','fake_windows.hpp','run_native_tests.py','test_java_batch.py','TransportConfigTest.java','verify_native_binary.py','CTransportWindowsSmokeTest.java','run_windows.ps1','windows-stubs','pss_window','test_pss_independent.py','pss_client_load.c','CUserSocketWindowsTest.java','CTransportSessionWindowsTest.java','CIOPacketCodecReference.java','CUserSocketOwnerWindowsTest.java','fake_l1_codec.hpp','owner-fixtures','owner-production-sources.txt','usersocket','send-copy-contract','test_send_copy_contract.py','verify_abi9_scope.py','verify_abi10_scope.py','CSharedBroadcastWindowsTest.java','verify_followup_scope.py','NetworkDiagnosticsTest.java','test_network_followup.py','broadcast-followup','CUserSocketSafeSendWindowsTest.java','verify_safesend_scope.py','ExtendedPacketDiagnosticsTest.java','ExtendedPacketDecoderTest.java','test_extended_diagnostics.py','verify_extended_scope.py','ManagerLogBufferTest.java','ManagerWindowTest.java','WindowsUiMemory.java','PoolSnapshotTest.cpp','PoolDiagnosticsTest.java','PoolReclamationWindowsTest.java','test_manager_memory.py','test_manager_window.py','verify_pool_manager_scope.py','manager-fixtures']:
 dest=PATCH/'tests'/name;dest.parent.mkdir(exist_ok=True)
 if (ROOT/'tests'/name).is_dir():shutil.copytree(ROOT/'tests'/name,dest)
 else:shutil.copy2(ROOT/'tests'/name,dest)
for name in ['NetworkFaultTest.cpp','run_network_fault_tests.py','verify_network_fault_scope.py','VerifiedTimerNamesTest.cpp','run_verified_timer_tests.py','verify_verified_names_scope.py','ReferenceTraceTest.cpp','run_reference_tests.py','ReferenceDiagnosticsTest.java','ReferenceDiagnosticsWindowsTest.java','verify_reference_scope.py','ObjectAllocatorTest.cpp','ObjectAbiTest.cpp','ObjectNetworkContract.cpp','run_object_memory_tests.py','verify_object_memory_scope.py','verify_msvc_rtti.py','verify_package_layout_scope.py','check_package_compile.py','StressMetrics.java','CTransportLoadWindowsTest.java','CUserSocketStabilityWindowsTest.java','JfrAllocationSummary.java','run_stability.ps1','verify_stability_scope.py','CTransportSplitWindowsTest.java','CUserSocketSoakWindowsTest.java','run_extended_stability.ps1','verify_extended_stability_scope.py']:
 shutil.copy2(ROOT/'tests'/name,PATCH/'tests'/name)
for name in ['scope','trace_restore_scope.py','find_core_initializers.py','recover_server_core.py','server-rtti.json','L2Server.exe.rtti-disasm.txt','L2Server(10).exe.rtti-disasm.txt','hp-batch-worker-excerpt.txt','recover_user_socket.py','user-socket','recover_registry.py','registry','network-audit','audit_network_abi5.py','build_network_audit_matrix.py','abi6','creature-serializer-audit','remaining-network-audit','send-close-audit','audit_send_close_abi8.py','abi9','abi10','followup','safesend-close','extended-diagnostics','pool-manager','network-fault-identity','verified-network-names','reference-restoration','object-memory-abi','package-layout-20261114','stability-20261114','stability-extended-20261114']:
 dest=PATCH/'research'/name;dest.parent.mkdir(exist_ok=True)
 if (ROOT/'research'/name).is_dir():shutil.copytree(ROOT/'research'/name,dest,ignore=shutil.ignore_patterns('__pycache__','reorganize.py','apply_visibility.py','compile.args','VISIBILITY.json'))
 else:shutil.copy2(ROOT/'research'/name,dest)
for name in ['CTransport.dll','CTransport.lib','CTransport_SelfTest.exe','ObjectAbiTest.exe','msvc-ci-artifact.json','native-build.json','CIOPacketCodec.dll']:
 out=PATCH/'build'/name;out.parent.mkdir(exist_ok=True);shutil.copy2(ROOT/'build'/name,out)
shutil.copytree(ROOT/'build/pss-client',PATCH/'build/pss-client')
for name in ['native-test.log','native-test-build.log','native-suite.log','tsan-test.log','tsan-test-build.log','tsan-suite.log','full-build.log','java-suite.log','transport-config-test.log','native-binary-test.log','windows-build.log','windows-selftest-build.log','build-manifest.log','scope-analysis.log','rtti-analysis.log','windows-smoke-compile.log','windows-actual-test.log','pss-independent-contract.log','pss-pairing-contract.log','pss-client-load-build.log','user-socket-analysis.log','user-socket-native-suite.log','user-socket-tsan-suite.log','user-socket-java-suite.log','user-socket-contract.log','user-socket-windows-compile.log','user-socket-binary-test.log','user-socket-build-manifest.log','user-socket-pss-preservation.log','abi5-native-suite.log','abi5-tsan-suite.log','abi5-java-suite.log','abi5-binary-test.log','abi5-build-manifest.log','abi5-windows-compile.log','abi5-owner-compile.log','abi5-pss-preservation.log','abi5-pss-contract.log','registry-analysis.log','abi5-windows-actual.log','network-audit.log']:
 out=PATCH/'logs'/name;out.parent.mkdir(exist_ok=True);shutil.copy2(ROOT/'logs'/name,out)
for source in sorted([*(ROOT/'logs').glob('abi6-*.log'),*(ROOT/'logs').glob('abi7-*.log'),*(ROOT/'logs').glob('abi8-*.log'),*(ROOT/'logs').glob('abi9-*.log'),*(ROOT/'logs').glob('abi10-*.log'),*(ROOT/'logs').glob('followup-*.log'),*(ROOT/'logs').glob('safesend-*.log'),*(ROOT/'logs').glob('extended-*.log'),*(ROOT/'logs').glob('pool-ui-*.log'),*(ROOT/'logs').glob('network-fault-*.log'),*(ROOT/'logs').glob('verified-names-*.log'),*(ROOT/'logs').glob('reference-restore-*.log'),*(ROOT/'logs').glob('object-memory-*.log'),*(ROOT/'logs').glob('slots-send-*.log'),*(ROOT/'logs').glob('package-layout-*.log'),*(ROOT/'logs').glob('stability-*.log')]):
 if source.name in ('stability-package.log','abi6-package.log','abi7-package.log','abi8-package.log','abi9-package.log','abi10-package.log','followup-package.log','safesend-package.log','extended-package.log','pool-ui-package.log','network-fault-package.log','verified-names-package.log','reference-restore-package.log','object-memory-package.log'):continue
 shutil.copy2(source,PATCH/'logs'/source.name)
shutil.copy2(ROOT/'WINDOWS_VERIFICATION.json',PATCH/'WINDOWS_VERIFICATION.json')
shutil.copy2(ROOT/'README_CTRANSPORT.md',PATCH/'README.md')
shutil.copy2(ROOT/'REPORT_L1_USERSOCKET.md',PATCH/'REPORT_L1_USERSOCKET.md')
shutil.copy2(ROOT/'REPORT_ORIGINAL_NETWORK_ABI6.md',PATCH/'REPORT_ORIGINAL_NETWORK_ABI6.md')
shutil.copy2(ROOT/'REPORT_REMAINING_NETWORK_ABI7.md',PATCH/'REPORT_REMAINING_NETWORK_ABI7.md')
shutil.copy2(ROOT/'REPORT_SEND_CLOSE_ABI8.md',PATCH/'REPORT_SEND_CLOSE_ABI8.md')
shutil.copy2(ROOT/'REPORT_ORIGINAL_NAMES_CONST_ABI9.md',PATCH/'REPORT_ORIGINAL_NAMES_CONST_ABI9.md')
shutil.copy2(ROOT/'REPORT_SHARED_BROADCAST_ABI10.md',PATCH/'REPORT_SHARED_BROADCAST_ABI10.md')
shutil.copy2(ROOT/'REPORT_NETWORK_FOLLOWUP.md',PATCH/'REPORT_NETWORK_FOLLOWUP.md')
shutil.copy2(ROOT/'REPORT_SAFESEND_CLOSE.md',PATCH/'REPORT_SAFESEND_CLOSE.md')
shutil.copy2(ROOT/'REPORT_EXTENDED_COMMANDS.md',PATCH/'REPORT_EXTENDED_COMMANDS.md')
shutil.copy2(ROOT/'REPORT_POOL_MANAGER.md',PATCH/'REPORT_POOL_MANAGER.md')
shutil.copy2(ROOT/'REPORT_NETWORK_FAULT_IDENTITY.md',PATCH/'REPORT_NETWORK_FAULT_IDENTITY.md')
shutil.copy2(ROOT/'REPORT_VERIFIED_NETWORK_NAMES.md',PATCH/'REPORT_VERIFIED_NETWORK_NAMES.md')
shutil.copy2(ROOT/'REPORT_REFERENCE_RESTORATION.md',PATCH/'REPORT_REFERENCE_RESTORATION.md')
shutil.copy2(ROOT/'REPORT_OBJECT_MEMORY_ABI.md',PATCH/'REPORT_OBJECT_MEMORY_ABI.md')
shutil.copy2(ROOT/'REPORT_SLOT_SEND_FIX.md',PATCH/'REPORT_SLOT_SEND_FIX.md')
shutil.copy2(ROOT/'REPORT_STABILITY_EXTENDED.md',PATCH/'REPORT_STABILITY_EXTENDED.md')
shutil.copy2(ROOT/'REPORT_STABILITY_EXTENDED.md',REPO/'reports/CTRANSPORT_STABILITY_EXTENDED_20261114.md')
shutil.copy2(ROOT/'REPORT_STABILITY.md',PATCH/'REPORT_STABILITY.md')
shutil.copy2(ROOT/'REPORT_STABILITY.md',REPO/'reports/CTRANSPORT_STABILITY_20261114.md')
shutil.copy2(ROOT/'REPORT_PACKAGE_LAYOUT.md',PATCH/'REPORT_PACKAGE_LAYOUT.md')
shutil.copy2(ROOT/'REPORT_PACKAGE_LAYOUT.md',REPO/'reports/CTRANSPORT_PACKAGE_LAYOUT_20261114.md')
shutil.copy2(ROOT/'REPORT_FINAL_NETWORK_GAPS.md',PATCH/'REPORT_FINAL_NETWORK_GAPS.md')
shutil.copy2(ROOT/'REPORT_SLOT_SEND_FIX.md',REPO/'reports/CTRANSPORT_SLOT_SEND_FIX_20261114.md')
shutil.copy2(ROOT/'REPORT_FINAL_NETWORK_GAPS.md',REPO/'reports/CTRANSPORT_FINAL_NETWORK_GAPS_20261114.md')
shutil.copy2(ROOT/'REPORT_OBJECT_MEMORY_ABI.md',REPO/'reports/CTRANSPORT_OBJECT_MEMORY_ABI_20261113.md')
shutil.copy2(ROOT/'REPORT_REFERENCE_RESTORATION.md',REPO/'reports/CTRANSPORT_REFERENCE_RESTORATION_20261113.md')
shutil.copy2(ROOT/'REPORT_VERIFIED_NETWORK_NAMES.md',REPO/'reports/CTRANSPORT_VERIFIED_NETWORK_NAMES_20261113.md')
shutil.copy2(ROOT/'REPORT_NETWORK_FAULT_IDENTITY.md',REPO/'reports/CTRANSPORT_NETWORK_FAULT_IDENTITY_20261113.md')
shutil.copy2(ROOT/'REPORT_POOL_MANAGER.md',REPO/'reports/CTRANSPORT_POOL_MANAGER_20261113.md')
shutil.copy2(ROOT/'REPORT_EXTENDED_COMMANDS.md',REPO/'reports/CTRANSPORT_EXTENDED_COMMANDS_20261113.md')
shutil.copy2(ROOT/'REPORT_SAFESEND_CLOSE.md',REPO/'reports/CTRANSPORT_SAFESEND_CLOSE_20261113.md')
shutil.copy2(ROOT/'REPORT_NETWORK_FOLLOWUP.md',REPO/'reports/CTRANSPORT_MANAGER_FILTERS_FOLLOWUP_20261113.md')
shutil.copy2(ROOT/'REPORT_SHARED_BROADCAST_ABI10.md',REPO/'reports/CTRANSPORT_SHARED_BROADCAST_ABI10_20261113.md')
shutil.copy2(ROOT/'REPORT_ORIGINAL_NAMES_CONST_ABI9.md',REPO/'reports/CTRANSPORT_ORIGINAL_NAMES_CONST_ABI9_20261113.md')
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
manifest={'base_revision':'c76e7b2753876fc8bb1aac4075e78a5d31bf522f','branch':'ctransport-core-20261112','dll':'CTransport.dll','dll_sha256':dllsha,'abi':'0xa0000','implementation_parent':verification['tested_source_commit'],'transport_scope':'game','mandatory':True,'pss':{'enabled':True,'independent':True,'backend':'dedicated NIO2 group','io_threads':2,'expiry_threads':1,'default_port':'GAME_SERVER_PORT+1','property':'pss.uiBridge.port','game_inband_open':False,'client_dll_sha256':'35b476815c56b578e5e12a3d500589e72220ec6493bbc0041da541c588b861fa'},'fallback':False,'completion_batch':128,'source_files':len(current),'java_files':sum(n.endswith('.java') for n in current),'changed_files':changed,'deleted_files':deleted,'additional_sql':False,'exports':67,'new_abi10_structures':['one frozen pooled CIOBufferEx source shared across visual recipients','CAsyncSendConstBuffer/ExceptSelf Java predicates and existing FIFO tickets','no private input vector for native shared requests; per-socket cipher output remains private','shared input references transferred to native before downcall return and released before wire completion','queue saturation and disconnect rollback; FFM arena pinned by live producer handles','followup source guard:1692 files byte-identical to previous ABI10;unchanged codec and independent PSS'],'new_abi9_structures':['original RTTI CDummyServerCallback/IServerCallback names wired in C++ and Java','global MemoryObject -> CIOObject and MemoryObject fast-serializer arguments','standalone Java CIOBufferEx replaces nested Lease view','constant-source frame gather uses pooled encrypted destination and owned chain adoption','64 class/member or explicit L1Interop entry points; no old HP-style aliases or two obsolete policy no-ops'],'new_abi8_structures':['native SEND request and WRITE completion share CExecutionSerializerFast','L1 framed native SEND: first-plain policy, each-frame crypto, copied source and aggregate admission cap','Close linger(1,0) versus GracefulClose; drain/SD_SEND/peer FIN/deadline adapted for L1','Java CSocket copy/close failure notification retains source leases until downcall exits'],'new_abi7_structures':['Playing variant27 key=socket number at authentication; EnterChar variant28 key=account ID during character entry','native CPerfMon dedicated owner15, process CPU/system+process memory and population sampling','MAC configuration/verified-provider API and unidentified authentication denial when enabled','L1 account,character-select,enter,restart,logout and close hooks','bounded real Windows concurrent-session stress'],'new_abi6_structures':['CIOServer/Server and native socket class hierarchy','FD_ACCEPT event -> RegisterWait64 -> ListenThread17 -> null-overlapped IOCP -> accept/factory/Add/Initialize','raw SOCKET map and 8192-bucket user-number CObjectMap','native IP/MAC CSocketLimiter','CExecutionSerializerFast for write/close callbacks','OS socket buffers0, minCPU8/affinity, 2MiB send cap','per-peer ReportSocket and per-thread diagnostics'],'recovered_structures':['pre-close/unregister/terminal phases','preallocated contiguous slab/pointer rings','registered-producer wheel ownership','100 inline+101st post / owner17 dispatch','1000-entry timer blocks and32-byte heap records','owner-index CAS buffer rings','8K/16K families','shared receive COW/unique compaction','send FIFO','linger0 close','completion task objects','50ms/200-slot timer wheel and overflow heap'],'adaptations':['slot+generation handles','unchanged L1 codec function table','network grace predicates upcalled to Java','external FFM producer registration','diagnosed exhaustion instead of intentional process crash','indexed cancellation','64-bit monotonic clock','terminal callback after I/O drain','existing L1 codec/game-owner contract'],'user_socket':{'enabled_for_game':True,'native_states':['Header','Body','Stopped'],'wire_profile':'L1 2019 LE16, empty2 or10..65535','codec':'C++ directly calls unchanged CIOPacketCodec.dll; Java verifies symbol table once','registry':'native ordered active/retiring maps, slot+generation, lookup retain','network_deadlines':'native wheel: login, partial read, write stall; Java grace predicates via native upcall','execution':'native SEND/crypto/WRITE serializer; native READ direct or IOCP repost; L1 session owner for transitions','lifetime':'close-start/native IO terminal/packet lease/L1 cleanup','thread_identity':'native TLS owner query and L1 owner assertion','pss_unchanged_from':'2dfce4f; one unused experimental packet only has CIOUserSocket type references renamed'},'windows_evidence':json.loads((ROOT/'WINDOWS_VERIFICATION.json').read_text())}
manifest['followup_structures']=['common L1 map/screen/radius/rectangle predicate','five shared broadcast serializers with common recipient traversal','bounded metadata packet history/worker context and manager reports','config file with JVM override;existing MAC provider enforcement;no DB','source scope1692 unchanged from previous ABI10;native DLL unchanged']
manifest['safesend_close_structures']=['optional emblem-only SafeSend512KiB margin under reservation lock','optional rejection/abort/pool rollback without disconnect or cipher/FIFO consumption','first native operation/error forwarded through FFM callbacks and late owner binding','separate L1 Java close reason and native cause in manager history','1702 outside combined allowlist unchanged from32b7f46;native DLL/codec/PSS unchanged']
manifest['extended_command_structures']=['actual decoder-selected EXTENDED/PROTOBUF/CRAFT/ACTION_UI identities','one-event128 history and active manager work;no payload or subtype aggregation','sequence-checked ring update;nested restoration and overflow isolation','1707 other sources unchanged fromd4ea0b9;DLL/codec/PSS unchanged']
manifest['pool_manager_structures']=['native pool snapshot from existing ring positions;legacy8 diagnostic prefix plus18 optional extension marker','Java bucket borrow/final-return counters and request-driven60 sample observations','manager screen queue1024 entries/256KiChars;document64KiChars/300lines;coalesced status','real Windows manager resize/move and pool recovery tests;game bindings are fixtures','1702 other sources unchanged from1f5ff36;unchanged independent PSS and codec']
manifest['network_fault_structures']=['invalid nonnull pool entries consumed and skipped;quarantined views never recycled by late returns','healthy lease counters exclude quarantined slots;legacy8/18 diagnostics plus fault24 optional marker','MemoryObject original32 object identity/ref1 copy;bounded process reference-fault attribution','checked decrement prevents zero-reference counter wrap;normal transport handles/cipher/PSS unchanged']
manifest['verified_timer_names']={'evidence':'research/verified-network-names/VERIFIED_NAMES.json','confirmed':['CThreadLocalTimer','CThreadLocalTimer::CSlot','CMwmrQueue<CThreadLocalTimer::CSlot,4009>::Pop','CThreadLocalTimer::AddTimer','CThreadLocalTimer::Dispatch'],'adapter_helpers':['CMwmrQueue::Return name unconfirmed','takeDue/cancel are L1 ownership adapters'],'excluded':['original CIOThreadCallback is World_Sync/GC,not the L1 worker host','CUserSocketReleaseVerifier L2 close state machine is analyzed,not blindly duplicated']}
manifest['reference_restoration']={'report':'REPORT_REFERENCE_RESTORATION.md','evidence':'research/reference-restoration/EVIDENCE.json','verifier_budget':128,'record_capacity':16,'manager_records':8,'first_delay_ms':5000,'pending_recheck_ms':25000,'reason_kinds':31,'legacy_diagnostics_words':[8,18,24],'extended_diagnostics_words':1720,'scope':'network references,timer flag,typed buffer parameter;original custom allocator/full C++ vtable ABI not copied;requested item4 excluded'}
manifest['object_memory_abi']={'report':'REPORT_OBJECT_MEMORY_ABI.md','evidence':'research/object-memory-abi/EVIDENCE.json','compiler_profile':'MSVC x64 C++20','allocator_classes':49,'small_requested_plus4_limit':32768,'physical_prefix':16,'original_size_field_offset':-4,'initial_queue_items':1,'initial_queue_slots':19,'queue_concurrency':18,'virtual_slots':{'MemoryObject':4,'CIOObject':12,'CISocket':21,'CIOSocketEx':21,'CSocket':24,'CUserSocket':26,'CIOServer':13,'Server':16,'CUserSocketReleaseVerifier':12,'CDummyServerCallback':2},'diagnostics_words':2030,'adaptations':'L1 protocol/ownership policies,16-byte aligned allocation prefix,external L1 state,ticket generations,bounded diagnostics;not original machine-code embedding or all L2 profiles'}
manifest['slot_send_fix']={'report':'REPORT_SLOT_SEND_FIX.md','items':[1,2],'user_socket_virtual_slots':26,'slot15_margin_argument':'int','slot24':'existing raw pointer at+0x4f0;zero in L1;original pointee/name unconfirmed','slot25':'actual socket number at+0x4e8','admission':'same gate,FIFO+reserved+margin,checked subtraction,existing graceful drain','unchanged':['original field placement','restricted IP mode','PSS','codec','DB']}
manifest['package_layout']={'report':'REPORT_PACKAGE_LAYOUT.md','classes':26,'packages':['transport','connection','session','server','broadcast','diagnostics','execution','config'],'scope':'historical package-layout change;subsequent CUserSocket ordering fix is recorded separately'}
manifest['stability_fix']={'report':'REPORT_STABILITY.md','baseline':'481ba8cfb4baed361e1f2955030150e9cc7d4b76','production_changes':['CUserSocket serializer ownership release uses setVolatile before readiness recheck'],'scope_verifier':'tests/verify_stability_scope.py','native_dll_unchanged':True}
manifest['extended_stability']={'report':'REPORT_STABILITY_EXTENDED.md','baseline':'60a85fafb868c4f38eafc82de0bdafd88e65f61f','source_scope':'all1712 production sources and native DLL unchanged','scope_verifier':'tests/verify_extended_stability_scope.py'}
manifest['source_scope']=verification['source_scope']
manifest['config_file']='config/ctransport.properties'
manifest['client_identity_status']='Server API/config existing;client identity transmitter/verifier not integrated;default limit0'
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
assert 'nativeTerminated(operation, error)' in current['src/l1j/server/server/model/network/core/transport/CIOServer.java'].decode()
assert '_channel.isNativeIoDrained()' in current['src/l1j/server/server/model/network/core/connection/CUserSocket.java'].decode()
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
readme=REPO/'README.md';text=readme.read_text();title='## 2026-11-14 — 별도 JVM 지연 비교·워커 정지 영향·재접속 지속 검사'
heading=title+'\n\n[**전체 src**](downloads/CTransport_Aden_FULL_SRC_20261112.zip) · [**DLL·C++·검사 묶음**](downloads/CTransport_CORE_20261112.zip) · [**추가 실측 보고서**](reports/CTRANSPORT_STABILITY_EXTENDED_20261114.md)\n\n실제 Windows DLL/FFM에서 동일 프로토콜의 클라이언트 JVM 분리 비교, IOCP worker 정지 주입, 지속 재접속과 소켓 GC·풀 회수를 검사했습니다. 직전 배포의 운영 소스와 DLL·PSS는 동일하며 시험 코드와 증거를 갱신했습니다.\n\n---\n\n'
if not text.startswith(title):readme.write_text(heading+text)
print(json.dumps({'archives':archives,'dll_sha256':dllsha,'source_files':len(current),'java_files':manifest['java_files'],'changed':len(changed),'deleted':deleted},indent=2))
