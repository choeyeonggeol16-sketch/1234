from pathlib import Path
import hashlib,json
ROOT=Path(__file__).resolve().parents[1]
def verify(root):
 data=json.loads((root/'research/object-memory-abi/SOURCE_BASELINE.json').read_text());sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
 current={str(p.relative_to(root)):sha(p)for p in(root/'src').rglob('*')if p.is_file()}
 java={'src/l1j/server/server/model/network/core/'+n+'.java'for n in ['CIOConfig','CIOServer','Server','CPoolDiagnostics']}
 native={'native/'+n for n in ['CTransport.cpp','cio_core.hpp','execution_serializer.hpp','io_server.hpp','reference_trace.hpp','user_registry.hpp','build_native.py']}
 added={'native/'+n for n in ['object_allocator.hpp','packet_format.hpp','build_native_msvc.py']}
 assert current.keys()==data['source_files'].keys()
 assert {n for n,h in current.items()if h!=data['source_files'][n]}==java
 assert {n for n,h in data['native_files'].items()if sha(root/n)!=h}==native
 assert {str(p.relative_to(root))for p in(root/'native').glob('*')if p.is_file()}-set(data['native_files'])==added
 assert sha(root/'build/CIOPacketCodec.dll')=='60bde93c2ba20057e7e307594edef69ced273b69f923535ab658769e09f0cea9'
 assert sha(root/'build/pss-client/Pss2019.dll')=='35b476815c56b578e5e12a3d500589e72220ec6493bbc0041da541c588b861fa'
 return len(current)-len(java)
if __name__=='__main__':print('PASS object-memory source scope:',verify(ROOT),'unchanged files;4 network Java,7 native edits,3 native additions;PSS/codec unchanged')
