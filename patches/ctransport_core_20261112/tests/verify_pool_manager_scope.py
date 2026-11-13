from pathlib import Path
import hashlib,json
CORE='src/l1j/server/server/model/network/core/'
EDITED={CORE+n+'.java' for n in ['CIOBufferPool','CIOBufferEx','CIOServer','CIOConfig','Server']}
EDITED|={'src/l1j/server/Server.java','src/l1j/server/swing/chocco.java','src/l1j/server/server/monitor/FileLogger.java'}
ADDED={CORE+'CPoolDiagnostics.java','src/l1j/server/swing/ManagerLogBuffer.java'}
def verify(root):
    b=json.loads((root/'research/pool-manager/source-scope-baseline.json').read_text());current={str(p.relative_to(root)):p for p in (root/'src').rglob('*') if p.is_file()}
    assert current.keys()==b['files'].keys()|ADDED,'unexpected source additions/deletions'
    for name,digest in b['files'].items():
        if name not in EDITED:assert hashlib.sha256(current[name].read_bytes()).hexdigest()==digest,('source scope',name)
    changed={'native/CTransport.cpp','native/cio_core.hpp','native/build_native.py'}
    for name,digest in b['native_files'].items():
        if name not in changed:assert hashlib.sha256((root/name).read_bytes()).hexdigest()==digest,('native scope',name)
    return len(b['files'])-len(EDITED)
if __name__=='__main__':
    print('PASS pool/manager scope:',verify(Path(__file__).resolve().parents[1]),'other sources unchanged;PSS/gameplay and native I/O engine preserved')
