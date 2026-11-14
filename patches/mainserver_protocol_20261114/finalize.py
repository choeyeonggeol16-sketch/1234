"""Package a verified source tree without changing the previous delivery."""
from pathlib import Path
import csv
import hashlib
import json
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[1]
SOURCE = Path(sys.argv[1]).resolve()
VALIDATION = Path(sys.argv[2]).resolve()
manifest = json.loads((ROOT / 'MANIFEST.json').read_text())
sha = lambda data: hashlib.sha256(data).hexdigest()
actual = {str(p.relative_to(SOURCE)): p.read_bytes() for p in (SOURCE / 'src').rglob('*') if p.is_file()}
assert {n: sha(b) for n, b in actual.items()} == manifest['output_files']
for name in manifest['changed_files']:
    dest = ROOT / name
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(actual[name])
(ROOT / 'deleted-files.txt').write_text('\n'.join(manifest['deleted_files']) + '\n')
(ROOT / 'changed-files.txt').write_text('\n'.join(manifest['changed_files']) + '\n')
runtime = ROOT / 'runtime'
runtime.mkdir(exist_ok=True)
dll = REPO / 'patches/ctransport_core_20261112/build/CTransport.dll'
shutil.copyfile(dll, runtime / 'CTransport.dll')
assert sha(dll.read_bytes()) in actual['src/l1j/server/server/model/network/core/config/CIOConfig.java'].decode()
shutil.copyfile(REPO / 'patches/ctransport_core_20261112/build/msvc-ci-artifact.json', runtime / 'WINDOWS_BASELINE.json')
with (ROOT / 'class-names.csv').open('w') as f:
    w = csv.writer(f); w.writerow(['old', 'new', 'status', 'schema', 'old_path'])
    for c in manifest['classes']: w.writerow([c['old'], c['new'], c['status'], c['schema'], c['path']])
schemas = {n: p.name for p in (ROOT / 'schemas').glob('MainServer*.proto')
           for n in __import__('re').findall(r'^message\s+(\w+)', p.read_text(), __import__('re').M)}
with (ROOT / 'all-messages.csv').open('w') as f:
    w = csv.writer(f); w.writerow(['message', 'schema', 'packet_class', 'existing_wire_constant'])
    constants = actual['src/l1j/server/server/packets/Protocol/ProtoMsg.java'].decode()
    indun = actual['src/l1j/server/server/packets/Protocol/Packet/GludioDungeonPackets.java'].decode()
    for n, schema in sorted(schemas.items()):
        w.writerow([n, schema, n if any(c['new'] == n and c['schema'] for c in manifest['classes']) else '',
                    n if n in constants or n in indun else ''])
logs = ROOT / 'logs'; logs.mkdir(exist_ok=True)
for name in ['baseline-compile.log', 'final-compile.log', 'baseline-wire.tsv', 'final-wire.tsv', 'dependencies.json', 'classes.tsv']:
    shutil.copyfile(VALIDATION / name, logs / name)
cp = lambda kind: str(VALIDATION / 'test-classes') + ':' + str(VALIDATION / (kind + '-classes')) + ':' + str(VALIDATION / 'lib/*')
tests = []
for kind in ['baseline', 'final']:
    command = ['java', '--enable-preview', '-cp', cp(kind), 'ProtoReaderContract']
    result = subprocess.run(command, capture_output=True, text=True, timeout=20)
    (logs / (kind + '-reader.log')).write_text(result.stdout + result.stderr)
    assert (result.returncode == 0) == (kind == 'final')
    tests.append({'test': kind + '-reader', 'exit': result.returncode, 'expected': 'pass' if kind == 'final' else 'reproduced original bug'})
    name = 'SC_NotificationMessage' if kind == 'baseline' else 'SC_NOTIFICATION_MESSAGE'
    classpath = str(VALIDATION / 'notification-tests') + ':' + str(VALIDATION / (kind + '-classes')) + ':' + str(VALIDATION / 'lib/*')
    result = subprocess.run(['java', '--enable-preview', '-cp', classpath, 'NotificationAdapterContract',
                             'l1j.server.server.model.network.core.sc.packet.' + name],
                            capture_output=True, text=True, timeout=20)
    (logs / (kind + '-notification.log')).write_text(result.stdout + result.stderr)
    assert (result.returncode == 0) == (kind == 'final')
    tests.append({'test': kind + '-notification', 'exit': result.returncode, 'expected': 'pass' if kind == 'final' else 'reproduced original bug'})
assert (logs / 'baseline-wire.tsv').read_bytes() == (logs / 'final-wire.tsv').read_bytes()
result = {'status': 'passed', 'production_java': 1711, 'source_files': 1712,
          'renamed_class_names': 86, 'renamed_class_files': 87, 'descriptor_names': 81, 'local_adapter_names': 5,
          'wire_cases_unchanged': 170, 'null_dto_cases_unchanged': 16,
          'reader_assertions': 20052, 'notification_field_cases': 15,
          'dll_sha256': sha(dll.read_bytes()), 'tests': tests,
          'scope': 'Java21 compile; actual serializers and buffer code with network/PC fixtures; Windows game/DB runtime not rerun'}
(ROOT / 'VALIDATION.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
readme = '''# MainServer 프로토콜 이름 정리·파서 수정 (2026-11-14)

기준: 마지막 네트워크 개선 검증 브랜치 `ctransport-stability-43580772728`, commit `a1d0ea7`.
기존 full-src ZIP의 DLL pin이 오래되어 같은 브랜치의 실제 Windows 검증 DLL과 설정으로 맞췄다.

패킷 클래스 파일 87개(이름 86종)의 생성자·import·호출부를 일괄 수정했다. 81종은 복원 proto의 정확한 메시지명이며 `NOIT`, `ACk`도 descriptor의 철자를 보존한다. 나머지 5종은 대응이 확인되지 않은 서버 자체 어댑터 이름이다. 요청 처리 22종, PSS 함수 6종, 인던 함수 9종을 설명 가능한 이름으로 통일했고, proto 상수 11개와 인던 상수 27개도 맞췄다. 실제 wire ID 숫자는 유지한다.

`class-names.csv`는 이전→현재 이름, `all-messages.csv`는 보고서의 최상위 메시지 429개 전체를 담는다. 빈 구현 칸은 이번 팩에 해당 패킷 클래스가 없다는 뜻이다. 원본 서버 함수 심볼은 보고서에 없어 임의의 이름을 원본이라고 주장하지 않는다. 서버 처리 함수명은 이 팩의 설명용 이름이다.

코드 수정: ProtoReader의 열 번째 varint 바이트 초과/다음 필드 소비와 잘못된 tag 범위를 거부한다. 설문 화면 알림의 RGB 길이 누락·128바이트 이상 문자열 길이 오류는 공통 SC_NOTIFICATION_MESSAGE 직렬화 코드로 연결해 수정한다. 기존 255바이트 문자열 상한과 전송 실패 버퍼 반납을 유지한다.

## 적용

전체 소스 ZIP의 `src/`로 기존 소스를 교체하고 Java21 clean build한다. ZIP 루트의 `CTransport.dll`을 서버 실행 폴더에 함께 교체한다. ABI10/67exports의 마지막 검증 DLL을 재사용한다. 기존 설정·DB·독립 PSS 클라이언트는 그대로 사용한다.

부분 적용 시 `src/` 파일을 복사하고 `deleted-files.txt`의 이전 경로 87개를 제거한다. 같은 클래스의 이전/새 파일을 함께 빌드하지 않는다. `runtime/CTransport.dll`도 서버 실행 폴더로 복사한다.

## 검증

전체 production Java 1,711개 컴파일 오류 0(기존 deprecated 경고 5개). 송신 직렬화 170개 입력 사례 바이트 동일, null DTO 예외 16개 동일. 파서 20,052개 단언과 알림 필드 15개 사례·문자열 상한·실패 버퍼 반납 통과. 두 오류 모두 변경 전 실제 코드에서 재현했다. 상세 로그·검증 의존성 hash는 `logs/`, 결과는 `VALIDATION.json`에 있다. 실제 Windows 게임·DB 통합 실행은 이번 수정 후 재검증하지 않았다.

`apply_protocol_names.py --base <이전 full-src.zip> --output <새 폴더>`로 동일 소스 변경을 재생성할 수 있다. `schemas/`는 보고서에서 복원된 계약이며 원본 서버 소스가 아니다.
'''
(ROOT / 'README_KO.md').write_text(readme)
shutil.copyfile(ROOT / 'README_KO.md', REPO / 'reports/MAINSERVER_PROTOCOL_NAMES_20261114.md')
archives = {}
for filename, entries in [
    ('CTransport_MainServer_Protocol_FULL_SRC_20261114.zip', list(actual.items()) + [('CTransport.dll', dll.read_bytes()), ('README_KO.md', readme.encode())]),
    ('CTransport_MainServer_Protocol_PATCH_20261114.zip', [(str(p.relative_to(ROOT)), p.read_bytes()) for p in sorted(ROOT.rglob('*')) if p.is_file()]),
]:
    dest = REPO / 'downloads' / filename
    with zipfile.ZipFile(dest, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name, data in entries: archive.writestr(name, data)
    with zipfile.ZipFile(dest) as archive:
        assert archive.testzip() is None
        assert all(archive.read(n) == b for n, b in entries)
    digest = sha(dest.read_bytes())
    dest.with_name(filename + '.sha256').write_text(digest + '  ' + filename + '\n')
    archives[filename] = digest
readme_path = REPO / 'README.md'
heading = '''## 2026-11-14 — MainServer proto 이름 정리·수신 파서/알림 수정

[**전체 소스·호환 DLL**](downloads/CTransport_MainServer_Protocol_FULL_SRC_20261114.zip) · [**패치·이름 대응표·검증**](downloads/CTransport_MainServer_Protocol_PATCH_20261114.zip) · [**적용 안내**](patches/mainserver_protocol_20261114/README_KO.md)

마지막 네트워크 개선 검증 팩 기준으로 패킷 클래스·상수·요청 함수와 모든 호출부를 정리했습니다. varint 경계와 알림 길이 직렬화 오류도 수정했습니다. 전체 Java 1,711개 컴파일, 송신 170사례 동일, 파서·알림 회귀 통과. 전체 src 교체·clean build와 함께 동봉 CTransport.dll을 적용합니다.

---

'''
if not readme_path.read_text().startswith(heading): readme_path.write_text(heading + readme_path.read_text())
print(json.dumps({'validation': result, 'archives': archives}, ensure_ascii=False))
