from pathlib import Path
import hashlib,json
ROOT=Path(__file__).resolve().parents[1]
def verify(root):
 data=json.loads((root/'research/network-fault-identity/SOURCE_BASELINE.json').read_text())
 files={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest()for p in (root/'src').rglob('*')if p.is_file()}
 assert files.keys()==data['source_files'].keys(),'source addition/deletion outside network scope'
 changed={n for n,h in files.items()if h!=data['source_files'][n]}
 assert changed==set(data['edited_source_files']),changed
 native={n for n,h in data['native_files'].items()if hashlib.sha256((root/n).read_bytes()).hexdigest()!=h}
 assert native==set(data['edited_native_files']),native
 assert hashlib.sha256((root/'build/CIOPacketCodec.dll').read_bytes()).hexdigest()=='60bde93c2ba20057e7e307594edef69ced273b69f923535ab658769e09f0cea9'
 assert hashlib.sha256((root/'build/pss-client/Pss2019.dll').read_bytes()).hexdigest()=='35b476815c56b578e5e12a3d500589e72220ec6493bbc0041da541c588b861fa'
 return len(files)-len(changed)
if __name__=='__main__':print('PASS network fault scope:',verify(ROOT),'other source files unchanged;only3 Java/2 native production files edited;independent PSS/codec preserved')
