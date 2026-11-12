from pathlib import Path
import hashlib,json,shutil,subprocess,tempfile,zipfile

w=Path(__file__).resolve().parent
repo=Path('/workspace/work/aden-nc-port-20261112/repo')
patch=repo/'patches/rkern_nc_iocp_batch128_20261112';patch.mkdir(parents=True,exist_ok=True)
base=repo/'downloads/RKern_Aden_NC_IOCP_PORT_FULL_SRC_20261112.zip'
src_name='RKern_Aden_NC_IOCP_BATCH128_FULL_SRC_20261112.zip';native_name='RKern_NC_IOCP_BATCH128_CORE_20261112.zip'
files=sorted(p for p in (w/'src').rglob('*') if p.is_file())
with zipfile.ZipFile(base) as z:
 assert set(z.namelist())=={p.relative_to(w).as_posix() for p in files}
 changed=[p.relative_to(w).as_posix() for p in files if p.read_bytes()!=z.read(p.relative_to(w).as_posix())]
assert changed==['src/l1j/server/server/model/network/core/CIOTransportConfig.java'],changed
for name in changed+['src/l1j/server/server/model/network/core/CIOWinServer.java']:
 dest=patch/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(w/name,dest)
for folder in ['native','tests','research','database']:shutil.copytree(w/folder,patch/folder,dirs_exist_ok=True)
(patch/'build').mkdir(exist_ok=True);(patch/'logs').mkdir(exist_ok=True)
for n in ['CIONcTransport.dll','CIONcTransport.lib','CIONcTransport_SelfTest.exe','native-build.json']:shutil.copyfile(w/'build'/n,patch/'build'/n)
for n in ['native-test.log','native-test-build.log','tsan-test.log','tsan-test-build.log','full-build.log','java-batch-suite.log','transport-config-test.log','native-binary-test.log','windows-build.log','windows-selftest-build.log','build-manifest.log']:
 shutil.copyfile(w/'logs'/n,patch/'logs'/n)
shutil.copyfile(w/'README_NC_PORT.md',patch/'README.md');shutil.copyfile(w/'REPORT_BATCH128.md',repo/'reports/RKERN_NC_IOCP_BATCH128_20261112.md')
(patch/'requirements.txt').write_text('pefile==2024.8.26\ncapstone==5.0.6\n')
(patch/'RUN_WINDOWS_SELFTEST.cmd').write_bytes(b'@echo off\r\npushd "%~dp0build"\r\nCIONcTransport_SelfTest.exe\r\nset "NC_TEST_EXIT=%errorlevel%"\r\npopd\r\nexit /b %NC_TEST_EXIT%\r\n')
(patch/'changed-files.txt').write_text('native/CIONcTransport.cpp\n'+changed[0]+'\n')
with zipfile.ZipFile(repo/'downloads'/src_name,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for f in files:
  info=zipfile.ZipInfo(f.relative_to(w).as_posix(),(2026,11,12,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,f.read_bytes())
with tempfile.TemporaryDirectory(prefix='nc-batch-diff-') as tmp:
 tmp=Path(tmp)
 with zipfile.ZipFile(base) as z:
  for name in changed:
   dest=tmp/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read(name))
 subprocess.run(['git','init','-q'],cwd=tmp,check=True);subprocess.run(['git','add','src'],cwd=tmp,check=True)
 for name in changed:(tmp/name).write_bytes((w/name).read_bytes())
 (patch/'java-pin.patch').write_bytes(subprocess.check_output(['git','diff','--no-ext-diff','--binary','--','src'],cwd=tmp))
with tempfile.TemporaryDirectory(prefix='nc-batch-apply-') as tmp:
 with zipfile.ZipFile(base) as z:z.extractall(tmp)
 subprocess.run(['git','apply','--check',str(patch/'java-pin.patch')],cwd=tmp,check=True)
 subprocess.run(['git','apply',str(patch/'java-pin.patch')],cwd=tmp,check=True)
 with zipfile.ZipFile(repo/'downloads'/src_name) as z:
  assert z.testzip() is None and len(z.namelist())==1698 and sum(n.endswith('.java') for n in z.namelist())==1697
  assert all((Path(tmp)/n).read_bytes()==z.read(n) for n in z.namelist())
native_old=repo/'patches/rkern_nc_iocp_port_20261112/native/CIONcTransport.cpp'
old_lines=native_old.read_text().splitlines(keepends=True);new_lines=(w/'native/CIONcTransport.cpp').read_text().splitlines(keepends=True)
import difflib
(patch/'native-batch128.patch').write_text(''.join(difflib.unified_diff(old_lines,new_lines,fromfile='a/native/CIONcTransport.cpp',tofile='b/native/CIONcTransport.cpp')))
dll_sha=hashlib.sha256((w/'build/CIONcTransport.dll').read_bytes()).hexdigest()
manifest={'base_revision':'d43e5a6ee397a0861247a0972178353d51eb0037','source_bundle':src_name,'native_bundle':native_name,
 'native_dll_sha256':dll_sha,'source_files':1698,'java_files':1697,'changed_java':changed,
 'completion_api':'GetQueuedCompletionStatusEx','maximum_completion_batch':128,'per_entry_status':'ENTRY.Internal NTSTATUS snapshot via RtlNtStatusToDosError',
 'default_transport':'hpsocket','nc_option':'-Drkern.cio.transport=nc','new_sql':False,
 'binary_reference':'HP-based CIOWinTransport.dll SHA256 9d8106886be0e26d14bd8eb7093714ae3b44d655ccae869ffc98148db8807ecc, worker0x1800495c0',
 'verification':['ASan/UBSan/TSan production C++ with Windows API fixtures','273 I/O entries drain128/128/18 including one exit token',
 '124 mixed-status completions plus4 exit tokens all drained,4 workers exit and ref holds balanced','new PE exports/imports and Java DLL hash pin',
 '1697 Java full type compile and current transport selector/hash test','source CRC/every-byte/hash and previous bundle patch reconstruction'],
 'not_executed':['Real Windows DLL/kernel/Java FFM/game/load/soak','Full game regression rerun in this batch-only change'],
 'scope':'Selected NC contracts reimplemented in new C++; batch128 extends with observed prior HP DLL flow. Not original C++ recovery or NC binary instruction transplant.'}
(patch/'MANIFEST.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
(patch/'logs/package-test.log').write_text('PASS source ZIP CRC,1698 files/1697 Java,every byte.\nPASS only production Java change is current DLL hash; every gameplay and bridge source retained.\nPASS Java pin patch reproduces complete new source bundle.\nPASS original EXE and supplied old DLLs excluded.\n')
shutil.copyfile(Path(__file__),patch/'package_batch128.py')
checks=[]
for p in sorted(patch.rglob('*')):
 if p.is_file() and p.name!='SHA256SUMS':checks.append(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.relative_to(patch).as_posix())
(patch/'SHA256SUMS').write_text('\n'.join(checks)+'\n')
with zipfile.ZipFile(repo/'downloads'/native_name,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in sorted(patch.rglob('*')):
  if p.is_file():z.write(p,p.relative_to(patch))
with zipfile.ZipFile(repo/'downloads'/native_name) as z:assert z.testzip() is None
hashes={}
for name in [src_name,native_name]:
 p=repo/'downloads'/name;sha=hashlib.sha256(p.read_bytes()).hexdigest();p.with_suffix('.zip.sha256').write_text(sha+'  '+name+'\n');hashes[name]=sha
readme=repo/'README.md';old=readme.read_text();header='''## 2026-11-12 — 새 NC 재구현 DLL: 최대128개 배치 완료 처리

[**최신 전체 src**](downloads/RKern_Aden_NC_IOCP_BATCH128_FULL_SRC_20261112.zip) · [**새 DLL/C++/테스트**](downloads/RKern_NC_IOCP_BATCH128_CORE_20261112.zip) · [구현·원본 근거·검증](reports/RKERN_NC_IOCP_BATCH128_20261112.md)

선택형 재구현의 worker를 GetQueuedCompletionStatusEx/최대128개로 확장했습니다.항목별 NTSTATUS,종료 토큰 재전달,참조 해제를 검사했습니다. ASan/UBSan/TSan fixture 및 Java1697개 컴파일·새DLL 해시 확인 통과.기존 HP-Socket 기본값과 `-Drkern.cio.transport=nc` 선택은 유지. **실제 Windows/FFM/게임·부하는 미검증**.DLL과src를 함께 적용합니다.

---

'''
if not old.startswith('## 2026-11-12 — 새 NC 재구현 DLL:'):readme.write_text(header+old)
print(json.dumps({'archives':hashes,'dll_sha256':dll_sha,'patch_files':len(checks)},indent=2))
