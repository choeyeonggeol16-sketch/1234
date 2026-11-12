from pathlib import Path
import argparse,hashlib,json
p=argparse.ArgumentParser();p.add_argument('--root',type=Path,required=True);a=p.parse_args()
expected=json.loads(Path(__file__).with_name('PSS_BASELINE_HASHES.json').read_text())
for name,sha in expected.items():
 data=(a.root/name).read_bytes()
 if name.endswith('/SC_PssNativeWindow.java'):data=data.replace(b'CUserSocket',b'CIOUserSocket')
 assert hashlib.sha256(data).hexdigest()==sha,name
print('PASS eight exact PSS files, one unused packet has only the permitted type-name substitution')
