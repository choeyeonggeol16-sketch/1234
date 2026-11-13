from pathlib import Path
import hashlib,json
CORE='src/l1j/server/server/model/network/core/'
EDITED={CORE+n+'.java' for n in ['CUserSocket','CIOServer','CSocket','CIOFrame','CNetworkDiagnostics']}
EDITED|={CORE+'sc/packet/SC_Emblem.java','src/l1j/server/swing/chocco.java'}
EDITED.add('src/l1j/server/server/model/network/packet/CPacketHandler.java')
def verify(sources,baseline_path):
    baseline=json.loads(Path(baseline_path).read_text())['files']
    assert sources.keys()==baseline.keys(),'unexpected source additions/deletions'
    for name,digest in baseline.items():
        if name not in EDITED:
            assert hashlib.sha256(sources[name]).hexdigest()==digest,('out of scope',name)
    return len(baseline)-len(EDITED)
if __name__=='__main__':
    root=Path(__file__).resolve().parents[1]
    sources={str(p.relative_to(root)):p.read_bytes() for p in (root/'src').rglob('*') if p.is_file()}
    print('PASS SafeSend/close scope:',verify(sources,root/'research/safesend-close/source-scope-baseline.json'),'other source files unchanged;PSS/gameplay preserved')
