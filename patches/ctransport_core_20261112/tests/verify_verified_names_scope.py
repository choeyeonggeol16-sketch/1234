from pathlib import Path
import hashlib,json,re
ROOT=Path(__file__).resolve().parents[1]
def verify(root):
 data=json.loads((root/'research/verified-network-names/SOURCE_BASELINE.json').read_text())
 current={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest()for p in(root/'src').rglob('*')if p.is_file()}
 assert current.keys()==data['source_files'].keys()
 assert {n for n,h in current.items()if h!=data['source_files'][n]}==set(data['edited_source_files'])
 assert {n for n,h in data['native_files'].items()if hashlib.sha256((root/n).read_bytes()).hexdigest()!=h}==set(data['edited_native_files'])
 text=(root/'native/timer_wheel.hpp').read_text()
 assert 'class CThreadLocalTimer {' in text and 'struct CSlot {'in text and 'using SlotPool=CMwmrQueue<CSlot,4009>;'in text
 assert 'T* Pop()'in text and 'bool AddTimer(CIOObject* object,'in text and 'void Dispatch('in text
 assert not re.search(r'(?:class|struct|using)\s+(?:TimerWheel|TimerBlockPool|TimerBlock)\b',text)
 # The real RTTI callback performs World_Sync/GC and must not be confused with
 # the current worker host merely because their names resemble each other.
 assert 'class CIOThreadCallback {'not in (root/'native/io_server.hpp').read_text()
 assert hashlib.sha256((root/'build/CIOPacketCodec.dll').read_bytes()).hexdigest()=='60bde93c2ba20057e7e307594edef69ced273b69f923535ab658769e09f0cea9'
 assert hashlib.sha256((root/'build/pss-client/Pss2019.dll').read_bytes()).hexdigest()=='35b476815c56b578e5e12a3d500589e72220ec6493bbc0041da541c588b861fa'
 return len(current)-1
if __name__=='__main__':print('PASS verified-name scope:',verify(ROOT),'other source files unchanged;3 native files and Java DLL pin only;one actual timer/queue implementation,no old aliases;PSS/codec preserved')
