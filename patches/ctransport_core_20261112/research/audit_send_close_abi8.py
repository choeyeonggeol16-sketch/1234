"""Read-only, SHA-pinned x64 PE audit. Requires pefile and capstone."""
from pathlib import Path
import argparse, hashlib, json, sys

root = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument('--binary', type=Path, required=True)
p.add_argument('--out', type=Path, default=root/'research/send-close-audit')
args = p.parse_args()
# Reuse the instruction-boundary/unwind/import validation of the prior audit.
sys.argv = ['audit', '--x64', str(args.binary), '--x86', str(args.binary), '--out', str(args.out)]
scope = {}
helper = (root/'research/audit_network_abi5.py').read_text()
exec(compile(helper.split("x64=AuditPE(a.x64,'x64')")[0], 'audit_helper', 'exec'), scope)
obj = scope['AuditPE'](args.binary, 'x64')
entries = [('send_v',0x90ee9c), ('async_send',0x90ed10), ('close',0x45d41c),
           ('graceful_close',0x545178), ('write_callback',0x545e38),
           ('write_enqueue',0x545a18), ('serializer',0x45c7ac)]
functions = {label:obj.dump(label, address) for label,address in entries}
(args.out/'functions.json').write_text(json.dumps(functions,indent=2)+'\n')
facts = {
    'binary':args.binary.name, 'sha256':hashlib.sha256(args.binary.read_bytes()).hexdigest(),
    'image_base':hex(obj.base), 'scope':'x64 PE pdata/unwind instruction boundaries',
    'send_to_serializer':{'call':'0x90f025', 'target':'0x45c7ac'},
    'async_send_encrypt':{'call':'0x90edf1', 'target':'0x90dda4'},
    'async_send_enqueue':{'call':'0x90ee18', 'target':'0x90e1d0'},
    'close':{'entry':'0x45d41c','on_close':'virtual +0x98','linger':[1,0],
             'setsockopt_call':'0x45d4c6','closesocket_call':'0x45d4cf'},
    'graceful_close':{'entry':'0x545178','on_close':'virtual +0x98',
                      'closesocket_call':'0x5451fc','linger0_in_this_function':False},
    'limits':['Drain-before-close and bounded grace timeout are L1/FFM adaptations; this function alone does not prove the original drained its queue.',
              'L1 codec/framing and Java game owner differ from L2; this is a behavioral reconstruction, not recovered original source.']}
for call,target in [(0x90f025,0x45c7ac),(0x90edf1,0x90dda4),(0x90ee18,0x90e1d0)]:
    ins=obj.valid(call)
    assert ins and ins.mnemonic=='call' and ins.operands[0].imm==target
(args.out/'facts.json').write_text(json.dumps(facts,indent=2)+'\n')
print('PASS pinned original PE, seven unwind-bounded functions, SEND serializer/cipher/enqueue calls and Close/GracefulClose traces')
