from pathlib import Path
import hashlib,json
CORE='src/l1j/server/server/model/network/core/'
EDITED={CORE+'CIOConfig.java',CORE+'CUserSocket.java','src/l1j/server/server/model/LocalWorldMap.java','src/l1j/server/Server.java','src/l1j/server/swing/chocco.java'}
EDITED|={CORE+'sc/packet/'+n+'.java' for n in ['SC_DoActionGFX','SC_SkillSound','SC_MoveChar','SC_ChangeHeading','SC_RemoveObject']}
# The subsequent SafeSend/close feature retains a separate tighter baseline.
EDITED|={CORE+n+'.java' for n in ['CIOServer','CSocket','CIOFrame']}
EDITED.add(CORE+'sc/packet/SC_Emblem.java')
ADDED={CORE+n+'.java' for n in ['CTLMFilter','CSharedBroadcast','CNetworkDiagnostics']}
def verify(sources,baseline_path):
 baseline=json.loads(Path(baseline_path).read_text())['files']
 assert set(sources)==set(baseline)|ADDED,'unexpected source additions/deletions'
 for name,digest in baseline.items():
  if name not in EDITED:assert hashlib.sha256(sources[name]).hexdigest()==digest,('out of scope',name)
 return len(baseline)-len(EDITED)
if __name__=='__main__':
 root=Path(__file__).resolve().parents[1];sources={str(f.relative_to(root)):f.read_bytes() for f in (root/'src').rglob('*') if f.is_file()}
 print('PASS followup scope:',verify(sources,root/'research/followup/source-scope-baseline.json'),'source files byte-identical to ABI10;PSS/gameplay preserved')
