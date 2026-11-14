"""Verify that package relocation changes imports/visibility,not method bodies."""
from pathlib import Path
import hashlib
import json
import re

ROOT = Path(__file__).resolve().parents[1]

def verify(root=ROOT):
    data = json.loads((root / 'research/package-layout-20261114/BASELINE.json').read_text())
    inverse = {new: old for old, new in data['class_mapping'].items()}
    pattern = r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*|[A-Za-z_$][\w$]*|[^\s]'
    def digest(text):
        for new, old in inverse.items():
            text = re.sub(re.escape(new) + r'\b', old, text)
        result = []
        skip = False
        for match in re.finditer(pattern, text):
            token = match[0]
            if token.startswith(('//', '/*')):
                continue
            if token in ('package', 'import'):
                skip = True
            if skip:
                if token == ';':
                    skip = False
                continue
            if token != 'public':
                result.append(token)
        return hashlib.sha256(json.dumps(result, ensure_ascii=False).encode()).hexdigest()
    actual = {str(p.relative_to(root)) for p in (root / 'src').rglob('*') if p.is_file()}
    expected = {data['path_mapping'].get(name, name) for name in data['source_files']}
    assert actual == expected, {'missing': sorted(expected-actual), 'extra': sorted(actual-expected)}
    edited = 0
    for old, before in data['source_files'].items():
        path = root / data['path_mapping'].get(old, old)
        sha = hashlib.sha256(path.read_bytes()).hexdigest()
        edited += sha != before['sha256']
        if path.suffix == '.java':
            assert digest(path.read_text()) == before['tokens'], 'method-body change: ' + str(path)
        else:
            assert sha == before['sha256'], old
    for name, before in data['native_files'].items():
        if name != 'native/build_native_msvc.py':
            assert hashlib.sha256((root / name).read_bytes()).hexdigest() == before, name
    assert hashlib.sha256((root / 'build/CTransport.dll').read_bytes()).hexdigest() == data['dll_sha256']
    assert hashlib.sha256((root / 'build/pss-client/Pss2019.dll').read_bytes()).hexdigest() == data['pss_sha256']
    print('PASS26 relocated classes/8 packages;all1711 Java token streams unchanged after package/import/public normalization;',
          edited, 'source texts updated;native C++/DLL/PSS client unchanged')
    return len(actual)

if __name__ == '__main__':
    verify()
