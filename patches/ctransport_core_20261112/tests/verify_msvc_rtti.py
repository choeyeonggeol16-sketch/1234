"""Compare emitted concrete MSVC RTTI chains and table lengths to the original."""
from pathlib import Path
import argparse,json,re,struct
import pefile
r=Path(__file__).resolve().parents[1];p=argparse.ArgumentParser();p.add_argument('--root',type=Path,default=r);a=p.parse_args();r=a.root
raw=(r/'build/CTransport.dll').read_bytes();pe=pefile.PE(data=raw);base=pe.OPTIONAL_HEADER.ImageBase
original=json.loads((r/'research/object-memory-abi/EVIDENCE.json').read_text());actual={}
for name in ['CUserSocket','Server','CUserSocketReleaseVerifier','CDummyServerCallback']:
 expected=original['hierarchies'][name];pos=raw.find(expected[0].encode()+b'\0');assert pos>=0,name
 td=pe.get_rva_from_offset(pos)-16;candidates=[]
 for m in re.finditer(re.escape(struct.pack('<I',td)),raw):
  off=m.start()-12
  if off<0:continue
  try:col=pe.get_rva_from_offset(off);signature,delta,ctor,typ,hier,selfr=struct.unpack_from('<6I',raw,off)
  except (ValueError,struct.error):continue
  if signature!=1 or selfr!=col or delta!=0:continue
  _,_,count,bases=struct.unpack('<4I',pe.get_data(hier,16));assert 1<=count<=32
  chain=[]
  for n in range(count):
   descriptor=struct.unpack('<I',pe.get_data(bases+n*4,4))[0];typ=struct.unpack('<I',pe.get_data(descriptor,4))[0]
   chain.append(pe.get_data(typ+16,256).split(b'\0')[0].decode('ascii'))
  for v in re.finditer(re.escape(struct.pack('<Q',base+col)),raw):
   va=pe.get_rva_from_offset(v.start()+8);slots=[]
   for i in range(40):
    target=struct.unpack('<Q',pe.get_data(va+i*8,8))[0];sec=pe.get_section_by_rva(target-base)if target>=base else None
    if not sec or not sec.Characteristics&0x20000000:break
    slots.append(target)
   if slots:candidates.append((chain,len(slots)))
 assert any(chain==expected and count==len(original['vtables'][name]['slots'])for chain,count in candidates),(name,candidates,expected)
 actual[name]={'hierarchy':expected,'virtual_slots':len(original['vtables'][name]['slots'])}
assert b'.?AVCIOSocket@@\0'not in raw,'extra adapter class remains in RTTI'
print('PASS actual DLL MSVC RTTI:original CUserSocket6/Server4/verifier3/callback2 base chains;26/16/12/2 virtual slots;CISocket present,no extra CIOSocket class')
print(json.dumps(actual,indent=2))
