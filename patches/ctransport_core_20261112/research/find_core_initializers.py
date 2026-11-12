"""Read-only code xrefs for pool setup and timer insertion; does not execute PEs."""
from pathlib import Path
import capstone,pefile,struct,json,argparse,hashlib
parser=argparse.ArgumentParser();parser.add_argument('--binary',type=Path,required=True);binary=parser.parse_args().binary
assert hashlib.sha256(binary.read_bytes()).hexdigest()=='9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588'
pe=pefile.PE(str(binary));base=pe.OPTIONAL_HEADER.ImageBase
md=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64)
found={k:[] for k in ['pool_displacement','timer_insert_calls']}
for section in pe.sections:
 if not section.Characteristics&0x20000000:continue
 data=section.get_data();start=base+section.VirtualAddress
 for needle in [struct.pack('<I',0x4851020)]:
  at=0
  while True:
   pos=data.find(needle,at)
   if pos<0:break
   found['pool_displacement'].append(hex(start+pos));at=pos+1
 at=0
 while True:
  pos=data.find(b'\xe8',at)
  if pos<0 or pos+5>len(data):break
  target=start+pos+5+struct.unpack_from('<i',data,pos+1)[0]
  if target==0x92533c:found['timer_insert_calls'].append(hex(start+pos))
  at=pos+1
out=Path(__file__).parent/'scope/xrefs.json';out.write_text(json.dumps(found,indent=2)+'\n');print(json.dumps(found))
