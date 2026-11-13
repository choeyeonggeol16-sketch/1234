"""Verify every source outside the approved network rename scope byte-for-byte."""
from pathlib import Path
import argparse,hashlib,json,zipfile

def verify(sources,baseline_path):
    baseline=json.loads(Path(baseline_path).read_text())['files']
    core='src/l1j/server/server/model/network/core'
    def allowed(name):return name=='src/l1j/server/Server.java' or str(Path(name).parent)==core
    unchanged=0
    for name,digest in baseline.items():
        if allowed(name):continue
        assert name in sources and hashlib.sha256(sources[name]).hexdigest()==digest,('outside network scope',name)
        unchanged+=1
    for name in set(sources)-set(baseline):assert allowed(name),('unexpected added source',name)
    for name in ['CDummyServerCallback.java','IServerCallback.java','CIOBufferEx.java']:assert core+'/'+name in sources
    assert core+'/CIOSocketFactory.java' not in sources
    return unchanged

if __name__=='__main__':
    root=Path(__file__).resolve().parents[1]
    p=argparse.ArgumentParser();p.add_argument('--root',type=Path,default=root);p.add_argument('--archive',type=Path);a=p.parse_args()
    if a.archive:
        with zipfile.ZipFile(a.archive) as z:sources={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    else:sources={str(f.relative_to(a.root)):f.read_bytes() for f in (a.root/'src').rglob('*') if f.is_file()}
    count=verify(sources,a.root/'research/abi9/source-scope-baseline.json')
    print(f'PASS ABI9 scope: {count} sources outside network leaf classes/root Server are byte-identical to ABI8;new types/old factory removal verified')
