#!/usr/bin/env python3
"""Fail if an active old broadcaster or legacy SC broadcast serializer returns."""
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / 'src'
OUT = pathlib.Path(__file__).with_name('legacy_inventory.json')


def mask(source):
    """Blank comments and string/char contents, preserving offsets and lines."""
    chars = list(source)
    i = 0
    state = None
    while i < len(source):
        current = source[i]
        nxt = source[i + 1] if i + 1 < len(source) else ''
        if state == 'line':
            if current == '\n':
                state = None
            else:
                chars[i] = ' '
            i += 1
            continue
        if state == 'block':
            if current == '*' and nxt == '/':
                chars[i] = chars[i+1] = ' '
                i += 2
                state = None
            else:
                if current != '\n': chars[i] = ' '
                i += 1
            continue
        if state in ('str', 'char'):
            if current == '\\' and i + 1 < len(source):
                chars[i] = chars[i+1] = ' '
                i += 2
                continue
            if current == ('"' if state == 'str' else "'"):
                state = None
            if current != '\n': chars[i] = ' '
            i += 1
            continue
        if current == '/' and nxt == '/':
            chars[i] = chars[i+1] = ' '
            i += 2
            state = 'line'
            continue
        if current == '/' and nxt == '*':
            chars[i] = chars[i+1] = ' '
            i += 2
            state = 'block'
            continue
        if current in ('"', "'"):
            chars[i] = ' '
            state = 'str' if current == '"' else 'char'
            i += 1
            continue
        i += 1
    return ''.join(chars)


def closing(code, start, left, right):
    if start < 0 or code[start] != left: return -1
    depth = 1
    for i in range(start + 1, len(code)):
        if code[i] == left: depth += 1
        elif code[i] == right:
            depth -= 1
            if depth == 0: return i
    return -1

old = []
private_send_loops = []
sc_broadcast_methods = 0
sc_old_broadcast = []
java_files = list(SRC.rglob('*.java'))
for path in java_files:
    raw = path.read_text(encoding='utf-8', errors='replace')
    code = mask(raw)
    name = str(path.relative_to(SRC))
    for found in re.finditer(r'\b(?:broadcastPacket|wideBroadcastPacket|broadcastPacketToAll|broadcast_map)\s*\(', code):
        old.append([name, raw.count('\n', 0, found.start()) + 1, found.group(0)])
    for found in re.finditer(r'\bfor\s*\(', code):
        begin = code.index('(', found.start())
        end = closing(code, begin, '(', ')')
        if end < 0: continue
        head = code[begin+1:end]
        if 'L1PcInstance' not in head and 'L1Object' not in head: continue
        start = end + 1
        while start < len(code) and code[start].isspace(): start += 1
        if start >= len(code): continue
        if code[start] == '{': stop = closing(code, start, '{', '}')
        else: stop = code.find(';', start)
        if stop < 0: continue
        if re.search(r'\.sendPackets\s*\(|\.sendPacket\s*\(', code[start:stop+1]):
            private_send_loops.append([name, raw.count('\n', 0, found.start()) + 1,
                                       ' '.join(raw[begin+1:end].split())[:160]])
    if '/model/network/core/sc/packet/SC_' not in '/' + name: continue
    for found in re.finditer(r'\b(?:void|boolean|int)\s+(\w*(?:broadcast|Broadcast)\w*)\s*\(', code):
        op = code.find('(', found.start())
        ed = closing(code, op, '(', ')')
        if ed < 0: continue
        brace = code.find('{', ed)
        if brace < 0 or ';' in code[ed:brace]: continue
        end = closing(code, brace, '{', '}')
        if end < 0: continue
        sc_broadcast_methods += 1
        body = code[brace:end+1]
        if re.search(r'\.sendPackets\s*\(|\.sendPacket\s*\(|new\s+S_\w+\s*\(', body):
            sc_old_broadcast.append([name, raw.count('\n',0,found.start())+1, found.group(1)])

result = {
    'production_java_files': len(java_files),
    'active_old_broadcast_api_calls': old,
    'sc_broadcast_methods_examined': sc_broadcast_methods,
    'sc_broadcast_using_legacy_packet_send': sc_old_broadcast,
    'direct_player_loops_with_sendPackets': private_send_loops,
    'direct_player_loops_caveat': 'Candidates only: GM/user personal replies or stateful recipient-specific effects; not proof of a shared broadcast.',
}
OUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
assert not old, f'Active old broadcast API remains: {old[:20]}'
assert not sc_old_broadcast, f'Legacy SC broadcast serializer remains: {sc_old_broadcast[:20]}'
assert len(private_send_loops) == 10, f'Inspect changed per-player loops: {private_send_loops}'
assert sc_broadcast_methods >= 150, f'SC broadcast coverage changed: {sc_broadcast_methods}'
print(f"Legacy path audit PASS: {len(java_files)} production Java, 0 old APIs, "
      f"{sc_broadcast_methods} SC broadcast methods/0 old packet send; "
      f"{len(private_send_loops)} individualized send loops flagged for manual classification")
