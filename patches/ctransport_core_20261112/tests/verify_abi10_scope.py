"""Shared broadcast changes only the network adapters and two visual serializers."""
from pathlib import Path
import hashlib,json,argparse
CORE='src/l1j/server/server/model/network/core/'
EDITED={CORE+n+'.java' for n in ['CIOConfig','CIOServer','CSocket','CUserSocket','CIOBufferPool','CIOBufferEx']}
EDITED|={CORE+'sc/packet/'+n+'.java' for n in ['SC_DoActionGFX','SC_SkillSound']}
ADDED={CORE+n+'.java' for n in ['CAsyncSendConstBuffer','CAsyncSendConstBufferExceptSelf']}
def verify(sources,baseline_path):
    baseline=json.loads(Path(baseline_path).read_text())['files']
    assert set(sources)==set(baseline)|ADDED,'unexpected added/deleted sources'
    for name,digest in baseline.items():
        if name not in EDITED:assert hashlib.sha256(sources[name]).hexdigest()==digest,('outside shared broadcast scope',name)
    return len(baseline)-len(EDITED)
if __name__=='__main__':
    root=Path(__file__).resolve().parents[1]
    p=argparse.ArgumentParser();p.add_argument('--root',type=Path,default=root);a=p.parse_args()
    sources={str(f.relative_to(a.root)):f.read_bytes() for f in (a.root/'src').rglob('*') if f.is_file()}
    print('PASS ABI10 scope:',verify(sources,a.root/'research/abi10/source-scope-baseline.json'),'source files outside explicit shared broadcast scope unchanged from ABI9; PSS/codec preserved')
