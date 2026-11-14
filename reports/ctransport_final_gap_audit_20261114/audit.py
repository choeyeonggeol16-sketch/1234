"""Read-only original/source comparison; never executes the supplied EXE."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import struct
import sys
import pefile

p = argparse.ArgumentParser()
p.add_argument('--binary', type=Path, required=True)
p.add_argument('--source', type=Path, required=True)
p.add_argument('--out', type=Path, required=True)
args = p.parse_args()
args.out.mkdir(parents=True, exist_ok=True)
helper = args.source / 'research/object-memory-abi/audit_network_abi5.py'
sys.argv = ['helper', '--x64', str(args.binary), '--x86', str(args.binary), '--out', str(args.out)]
scope = {}
exec(compile(helper.read_text().split("x64=AuditPE(a.x64,'x64')")[0], str(helper), 'exec'), scope)
original = scope['AuditPE'](args.binary, 'x64')

targets = {
    'server_ctor': 0x8b0f1c,
    'server_dtor': 0x8b0e54,
    'server_add': 0x8b0404,
    'server_remove': 0x8b04fc,
    'server_shutdown': 0x8b0954,
    'server_aux': 0x8b0bac,
    'server_factory': 0x8b0738,
    'listener_start': 0x8af9cc,
    'listener_run': 0x8af8f4,
    'user_slot24': 0xa01790,
    'user_slot25': 0xa01798,
    'user_send_check_slot14': 0x9ef544,
    'user_send_check_slot15': 0x9ef620,
    'user_safe_send': 0x9ef41c,
    'socket_send_v': 0x90ee9c,
    'socket_write_packet': 0x90e1d0,
    'async_send': 0x90ed10,
    'socket_close': 0x45d41c,
    'user_timer': 0xa0d164,
    'user_on_close': 0xa12dec,
    'release_verifier_timer': 0xa1291c,
    'socket_io_dispatch': 0x54655c,
    'socket_write_completion': 0x545e38,
    'socket_read_submit': 0x5456d0,
}
functions = {}
for name, address in targets.items():
    data = original.dump(name, address)
    data['caller_count'] = len(data.pop('callers'))
    functions[name] = data

tables = {}
for name, address in [('CUserSocket', 0xdc1c18), ('CSocket', 0xd6ab88), ('Server', 0xd2a5f8)]:
    values = []
    for number in range(128):
        value = struct.unpack('<Q', original.pe.get_data(address-original.base+number*8, 8))[0]
        section = original.pe.get_section_by_rva(value-original.base) if value >= original.base else None
        if not section or not section.Characteristics & 0x20000000:
            break
        values.append(value)
    else:
        raise AssertionError('No non-executable table boundary: ' + name)
    tables[name] = {'address': hex(address), 'slots': [hex(v) for v in values],
                    'count': len(values), 'first_non_code': hex(value)}
assert tables['CUserSocket']['slots'][14:16] == ['0x9ef544', '0x9ef620']
assert [tables[n]['count'] for n in ('CUserSocket', 'CSocket', 'Server')] == [26, 24, 16]

checks = []
for address, mnemonic, operand in [
    (0x9ef620, 'cmp', 'qword ptr [rcx + 0x18], -1'),
    (0x9ef62a, 'mov', 'eax, dword ptr [rcx + 0x20]'),
    (0x9ef62d, 'lea', 'ecx, [rdx + rax]'),
    (0x9ef630, 'cmp', 'ecx, 0x200000'),
    (0x9ef636, 'setle', 'al'),
    (0x9ef5ca, 'cmp', 'dword ptr [rcx + 0x20], 0x200000'),
    (0x9ef5bc, 'mov', 'dword ptr [rbx + 0x53c], 5'),
    (0x9ef493, 'call', 'qword ptr [rax + 0x78]'),
    (0x8b0477, 'add', 'rcx, 0x60'),
    (0x8b04a7, 'lea', 'rcx, [rsi + 0x40]'),
    (0x90effc, 'call', 'qword ptr [rax + 0xb8]'),
    (0x8b0fcc, 'lea', 'rcx, [rdi + 0x60]'),
    (0x8b0fd0, 'mov', 'edx, 0xfa0'),
    (0x8b0f4b, 'lea', 'rcx, [rdi + 0x40]'),
    (0x8b0ecf, 'mov', 'rcx, qword ptr [rdi + 0x58]'),
    (0x8af969, 'mov', 'byte ptr [rcx + 0x88], r9b'),
    (0x8b07b5, 'cmp', 'byte ptr [rcx + 0x88], 0'),
    (0x8b07be, 'cmp', 'byte ptr [rdi + 4], 0xa'),
    (0x8b07c4, 'cmp', 'byte ptr [rdi + 5], 0xa'),
    (0xa01790, 'mov', 'rax, qword ptr [rcx + 0x4f0]'),
    (0xa01798, 'mov', 'eax, dword ptr [rcx + 0x4e8]'),
    (0x90e26f, 'call', 'qword ptr [rax + 0x70]'),
    (0x90e2e1, 'mov', 'rcx, qword ptr [rdi + 0xa8]'),
    (0x90e2f1, 'mov', 'qword ptr [rdi + 0xa8], rbx'),
]:
    ins = original.valid(address)
    assert ins and (ins.mnemonic, ins.op_str) == (mnemonic, operand), (hex(address), ins)
    checks.append({'address': hex(address), 'bytes': ins.bytes.hex(), 'instruction': mnemonic + ' ' + operand})

paths = ['native/CTransport.cpp', 'native/cio_core.hpp', 'native/io_server.hpp',
         'native/reference_trace.hpp', 'native/timer_wheel.hpp',
         'tests/ObjectAbiTest.cpp', 'tests/ObjectNetworkContract.cpp',
         'tests/verify_msvc_rtti.py',
         'src/l1j/server/server/model/network/core/CUserSocket.java',
         'src/l1j/server/server/model/network/core/sc/packet/SC_Emblem.java']
index = {}
for relative in paths:
    data = (args.source / relative).read_bytes()
    index[relative] = hashlib.sha256(data).hexdigest()

dll_path = args.source / 'build/CTransport.dll'
dll_raw = dll_path.read_bytes()
dll = pefile.PE(data=dll_raw)
dll_base = dll.OPTIONAL_HEADER.ImageBase
type_name = b'.?AVCUserSocket@@\0'
position = dll_raw.find(type_name)
assert position >= 0
type_rva = dll.get_rva_from_offset(position) - 16
actual_tables = []
for match in re.finditer(re.escape(struct.pack('<I', type_rva)), dll_raw):
    offset = match.start() - 12
    if offset < 0:
        continue
    try:
        locator_rva = dll.get_rva_from_offset(offset)
        signature, delta, ctor, typ, hierarchy, self_rva = struct.unpack_from('<6I', dll_raw, offset)
    except (ValueError, struct.error):
        continue
    if signature != 1 or self_rva != locator_rva or delta != 0:
        continue
    for table_match in re.finditer(re.escape(struct.pack('<Q', dll_base+locator_rva)), dll_raw):
        table_rva = dll.get_rva_from_offset(table_match.start()+8)
        slots = []
        for number in range(128):
            target = struct.unpack('<Q', dll.get_data(table_rva+number*8, 8))[0]
            section = dll.get_section_by_rva(target-dll_base) if target >= dll_base else None
            if not section or not section.Characteristics & 0x20000000:
                break
            slots.append(hex(target))
        if slots:
            actual_tables.append({'table_rva': hex(table_rva), 'count': len(slots), 'slots': slots})
assert len(actual_tables) == 1 and actual_tables[0]['count'] == 24, actual_tables

result = {'binary_sha256': hashlib.sha256(args.binary.read_bytes()).hexdigest(),
          'original_executed': False, 'architecture_scope': 'supplied x64 only',
          'source_index': index, 'vtables': tables, 'functions': functions,
          'release_dll': {'sha256': hashlib.sha256(dll_raw).hexdigest(), 'CUserSocket_tables': actual_tables},
          'verified_instructions': checks,
          'claim_limit': 'Selected function and field comparison, not all network branches or a restoration percentage.'}
(args.out / 'EVIDENCE.json').write_text(json.dumps(result, indent=2) + '\n')
print(f'PASS pinned original SHA, {len(functions)} selected functions, 3 independently bounded vtables, {len(checks)} exact instructions; source hashes recorded')
