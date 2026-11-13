from pathlib import Path
import hashlib,json
ROOT=Path(__file__).resolve().parents[1]
def verify(root):
 data=json.loads((root/'research/reference-restoration/SOURCE_BASELINE.json').read_text());sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
 current={str(p.relative_to(root)):sha(p)for p in(root/'src').rglob('*')if p.is_file()}
 assert current.keys()==data['source_files'].keys()
 assert {n for n,h in current.items()if h!=data['source_files'][n]}==set(data['edited_source_files'])
 assert {n for n,h in data['native_files'].items()if sha(root/n)!=h}==set(data['edited_native_files'])
 assert {str(p.relative_to(root))for p in(root/'native').glob('*')if p.is_file()}-set(data['native_files'])==set(data['new_native_files'])
 assert sha(root/'build/CIOPacketCodec.dll')=='60bde93c2ba20057e7e307594edef69ced273b69f923535ab658769e09f0cea9'
 assert sha(root/'build/pss-client/Pss2019.dll')=='35b476815c56b578e5e12a3d500589e72220ec6493bbc0041da541c588b861fa'
 return len(current)-len(data['edited_source_files'])
if __name__=='__main__':print('PASS source scope:',verify(ROOT),'unchanged source files;4 network Java files,6 native edits,1 native header;game/PSS/codec unchanged')
