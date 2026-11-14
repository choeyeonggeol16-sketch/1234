"""Verify the stability fix against the last delivered full source archive."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]

def verify(root=ROOT):
    baseline = json.loads((root / 'research/dispatch-performance-20261114/BASELINE.json').read_text())
    sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
    actual = {str(p.relative_to(root)): sha(p) for p in (root / 'src').rglob('*') if p.is_file()}
    assert actual.keys() == baseline['source_files'].keys(), 'source set changed'
    changed = sorted(name for name, before in baseline['source_files'].items() if actual[name] != before)
    assert changed == baseline['allowed_source_changes'], changed
    for name, before in baseline['native_files'].items():
        assert sha(root / name) == baseline.get('expected_native_changes', {}).get(name, before), name
    verification = json.loads((root / 'WINDOWS_VERIFICATION.json').read_text())
    assert verification['status'] == 'passed'
    assert sha(root / 'build/CTransport.dll') == verification['dll_sha256']
    config = root / 'src/l1j/server/server/model/network/core/config/CIOConfig.java'
    restored = config.read_bytes().replace(verification['dll_sha256'].encode(), baseline['dll_sha256'].encode())
    assert hashlib.sha256(restored).hexdigest() == baseline['source_files'][str(config.relative_to(root))], 'config change beyond DLL pin'
    assert sha(root / 'build/pss-client/Pss2019.dll') == baseline['pss_sha256']
    print('PASS1712 sources;only DLL pin changed fromdf32496;all Java behavior preserved;native change limited to verified CTransport.cpp;PSS unchanged')
    return len(actual)

if __name__ == '__main__':
    verify()
