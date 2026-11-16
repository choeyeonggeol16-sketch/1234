from pathlib import Path
import zipfile,hashlib
root=Path(__file__).resolve().parents[2]
p=root/'downloads/RKern_NC_SPacket_Assemble_Phase78_FULL_SRC_20261116.zip'
assert hashlib.sha256(p.read_bytes()).hexdigest()=='07bfc9631c56a9ea4dc6a6bce60824b0ce2a9065b0b8c64ced4451863c1fb943'
names={'SC_IconMessage','SC_MessageYN','SC_NewChat','SC_ServerMessage','SC_SystemMessage','SC_TopMessage','SC_War','SC_Weather','CSharedBroadcast'}
out=root/'reports/nc_phase79_exact';out.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(p) as z:
 for n in z.namelist():
  if Path(n).stem in names and n.endswith('.java'):
   (out/Path(n).name).write_bytes(z.read(n))
print('PHASE79_SC_EXTRACT',len(list(out.glob('*.java'))))
