from pathlib import Path
import hashlib,json,shutil,subprocess,tempfile,zipfile
w=Path(__file__).resolve().parent;repo=Path('/workspace/work/aden-nc-port-20261112/repo')
patch=repo/'patches/rkern_cio_core_20261112';patch.mkdir(parents=True,exist_ok=True)
base=repo/'downloads/RKern_Aden_NC_IOCP_BATCH128_FULL_SRC_20261112.zip'
src_name='RKern_Aden_CIO_CORE_FULL_SRC_20261112.zip';native_name='RKern_CIO_CORE_20261112.zip'
files=sorted(p for p in (w/'src').rglob('*') if p.is_file())
with zipfile.ZipFile(base) as z:
 assert set(z.namelist())=={p.relative_to(w).as_posix() for p in files}
 changed=[p.relative_to(w).as_posix() for p in files if p.read_bytes()!=z.read(p.relative_to(w).as_posix())]
assert len(changed)==2 and all(Path(n).name in ['CIOWinServer.java','CIOTransportConfig.java'] for n in changed)
old_native=repo/'patches/rkern_nc_iocp_batch128_20261112/native'
replacements=[('CIONcTransport','RKernCIOTransport'),('CIONC_Version','RKernCIO_Version'),('CIONC_TEST','RKERN_CIO_TEST'),('namespace cionc','namespace rkern_cio'),('nc_core.hpp','cio_core.hpp')]
for old,new in [('CIONcTransport.cpp','RKernCIOTransport.cpp'),('CIONcTransport.h','RKernCIOTransport.h'),('nc_core.hpp','cio_core.hpp'),('windows_loopback_test.cpp','windows_loopback_test.cpp')]:
 text=(old_native/old).read_text()
 for a,b in replacements:text=text.replace(a,b)
 assert text==(w/'native'/new).read_text(),old
for name in changed:
 dest=patch/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(w/name,dest)
for folder in ['native','tests','research','database']:shutil.copytree(w/folder,patch/folder,dirs_exist_ok=True)
(patch/'build').mkdir(exist_ok=True);(patch/'logs').mkdir(exist_ok=True)
for name in ['RKernCIOTransport.dll','RKernCIOTransport.lib','RKernCIOTransport_SelfTest.exe','native-build.json']:shutil.copyfile(w/'build'/name,patch/'build'/name)
for name in ['native-test.log','native-test-build.log','full-build.log','java-suite.log','transport-config-test.log','native-binary-test.log','windows-build.log','windows-selftest-build.log','build-manifest.log','scope-analysis.log']:shutil.copyfile(w/'logs'/name,patch/'logs'/name)
shutil.copyfile(w/'README_RKERN_CIO.md',patch/'README.md');shutil.copyfile(w/'REPORT_RESTORE_SCOPE.md',repo/'reports/RKERN_CIO_CORE_RESTORE_SCOPE_20261112.md')
(patch/'RUN_WINDOWS_SELFTEST.cmd').write_bytes(b'@echo off\r\npushd "%~dp0build"\r\nRKernCIOTransport_SelfTest.exe\r\nset "RKERN_TEST_EXIT=%errorlevel%"\r\npopd\r\nexit /b %RKERN_TEST_EXIT%\r\n')
(patch/'requirements.txt').write_text('pefile==2024.8.26\ncapstone==5.0.6\n')
(patch/'changed-files.txt').write_text('\n'.join(changed)+'\n')
with zipfile.ZipFile(repo/'downloads'/src_name,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in files:
  info=zipfile.ZipInfo(p.relative_to(w).as_posix(),(2026,11,12,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,p.read_bytes())
with tempfile.TemporaryDirectory(prefix='rkern-cio-diff-') as tmp:
 tmp=Path(tmp)
 with zipfile.ZipFile(base) as z:
  for n in changed:
   p=tmp/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n))
 subprocess.run(['git','init','-q'],cwd=tmp,check=True);subprocess.run(['git','add','src'],cwd=tmp,check=True)
 for n in changed:(tmp/n).write_bytes((w/n).read_bytes())
 (patch/'java-rkern-name.patch').write_bytes(subprocess.check_output(['git','diff','--no-ext-diff','--binary','--','src'],cwd=tmp))
with tempfile.TemporaryDirectory(prefix='rkern-cio-apply-') as tmp:
 with zipfile.ZipFile(base) as z:z.extractall(tmp)
 subprocess.run(['git','apply','--check',str(patch/'java-rkern-name.patch')],cwd=tmp,check=True)
 subprocess.run(['git','apply',str(patch/'java-rkern-name.patch')],cwd=tmp,check=True)
 with zipfile.ZipFile(repo/'downloads'/src_name) as z:
  assert z.testzip() is None and len(z.namelist())==1698 and sum(n.endswith('.java') for n in z.namelist())==1697
  assert all((Path(tmp)/n).read_bytes()==z.read(n) for n in z.namelist())
dll_hash=hashlib.sha256((w/'build/RKernCIOTransport.dll').read_bytes()).hexdigest()
(patch/'MANIFEST.json').write_text(json.dumps({'base_revision':'a4b3435dba7dc21725821d4415b3150bd2d3d913','branch':'rkern-cio-core-20261112','dll':'RKernCIOTransport.dll','dll_sha256':dll_hash,'version_export':'RKernCIO_Version','option':'-Drkern.cio.transport=rkern','legacy_option_alias':'nc','default_transport':'hpsocket','completion_batch':128,'source_files':1698,'java_files':1697,'changed_java':changed,'native_logic_unchanged_after_token_rename':True,'additional_core_features_implemented':False,'new_sql':False,'scope_candidates':['owner-index buffer ring and recycle','16KiB family','shared receive lifetime','socket close policy','completion work posting','periodic work/timers after more dependency tracing'],'validation':['native PE64/27 exports/hash/imports','production C++ ASan/UBSan with Windows API fixtures','1697 Java compile and canonical/legacy/default/hash selection','native token-rename equivalence to previous implementation','source CRC/every byte/hash and patch reproduction'],'not_tested':['real Windows DLL/FFM/game/load','TSan/game regressions not rerun for rename-only change']},ensure_ascii=False,indent=2)+'\n')
(patch/'logs/package-test.log').write_text('PASS source ZIP CRC:1698 files/1697 Java,every byte.\nPASS two Java files changed; all game sources unchanged.\nPASS native implementation/header/core/self-test equal prior source after name-only token replacement.\nPASS Java patch reproduces new full source.\nPASS current DLL hash and product names verified.\n')
shutil.copyfile(Path(__file__),patch/'package_rkern_cio.py')
checks=[]
for p in sorted(patch.rglob('*')):
 if p.is_file() and p.name!='SHA256SUMS':checks.append(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.relative_to(patch).as_posix())
(patch/'SHA256SUMS').write_text('\n'.join(checks)+'\n')
with zipfile.ZipFile(repo/'downloads'/native_name,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in sorted(patch.rglob('*')):
  if p.is_file():z.write(p,p.relative_to(patch))
with zipfile.ZipFile(repo/'downloads'/native_name) as z:
 assert z.testzip() is None
 assert not any(Path(n).name in ['CIONcTransport.dll','CIOWinTransport.dll','CIOPacketCodec.dll','L2Server.exe','L2Server(10).exe'] for n in z.namelist())
hashes={}
for name in [src_name,native_name]:
 p=repo/'downloads'/name;sha=hashlib.sha256(p.read_bytes()).hexdigest();p.with_suffix('.zip.sha256').write_text(sha+'  '+name+'\n');hashes[name]=sha
readme=repo/'README.md';old=readme.read_text();header='''## 2026-11-12 — RKern CIO 명칭 정리 / 추가 복원 범위

[**전체 src**](downloads/RKern_Aden_CIO_CORE_FULL_SRC_20261112.zip) · [**RKern CIO DLL·C++·검사 자료**](downloads/RKern_CIO_CORE_20261112.zip) · [추가 복원 범위·원본 근거](reports/RKERN_CIO_CORE_RESTORE_SCOPE_20261112.md)

현재 DLL·source·version export·실행 옵션을 RKern으로 통일했습니다. `-Drkern.cio.transport=rkern`으로 선택,기존 옵션은 호환 별칭 유지.128배치와 I/O 동작 동일. 추가 pool/16KB/종료/타이머 후보는 원본 명령을 더 읽어 구분했고 아직 구현하지 않았습니다.실제 Windows/FFM/게임·부하는 미검증인 실험본입니다.

---

'''
if not old.startswith('## 2026-11-12 — RKern CIO 명칭'):readme.write_text(header+old)
print(json.dumps({'archives':hashes,'dll_sha256':dll_hash,'patch_files':len(checks)},indent=2))
