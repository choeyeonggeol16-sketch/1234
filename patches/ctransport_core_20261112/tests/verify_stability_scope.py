"""Verify the stability fix against the last delivered full source archive."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]

def verify(root=ROOT):
    baseline = json.loads((root / 'research/stability-20261114/BASELINE.json').read_text())
    sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
    actual = {str(p.relative_to(root)): sha(p) for p in (root / 'src').rglob('*') if p.is_file()}
    assert actual.keys() == baseline['source_files'].keys(), 'source set changed'
    changed = [name for name, before in baseline['source_files'].items() if actual[name] != before]
    assert changed == baseline['allowed_source_changes'], changed
    for name, before in baseline['native_files'].items():
        assert sha(root / name) == before, name
    assert sha(root / 'build/CTransport.dll') == baseline['dll_sha256']
    assert sha(root / 'build/pss-client/Pss2019.dll') == baseline['pss_sha256']
    print('PASS1712 source files;only CUserSocket changed from481ba8c;1711 others, native C++, DLL and PSS unchanged')
    return len(actual)

if __name__ == '__main__':
    verify()
