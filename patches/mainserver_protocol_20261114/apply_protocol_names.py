"""Apply the documented naming migration to the last tested full source ZIP.

The schemas establish message names, not server function symbols or wire IDs.
Existing wire IDs and all serialization expressions are preserved.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parent
MANUAL = {
    'CS_UserPlayInfo': 'CS_USER_PLAY_INFO_REQ',
    'CS_PartyControl': 'CS_PARTY_CONTROL_REQ',
    'SC_PartyOperationResult': 'SC_PARTY_OPERATION_RESULT_NOTI',
    'SC_SiegeInjuryTime': 'SC_SIEGE_INJURY_TIME_NOIT',
    'SC_EventCountdownNoti': 'SC_EVENT_COUNTDOWN_NOTI_PACKET',
    'SC_PartyMemberMarkChange': 'SC_PARTY_MEMBER_MARK_CHANGE_NOTI',
    'SC_BoxAttrChangeNoti': 'SC_BOX_ATTR_CHANGE_NOTI_PACKET',
    'SC_Fishing_Proto': 'SC_FISHING_NOT',
    'SC_TelePortUi': 'SC_MAP_TELEPORT_INFO_NOTI',
}
# These five adapters have no single verified counterpart in the 2019 schemas.
LOCAL = {
    'SC_MapID': 'SC_MAP_ID', 'SC_SlotChange': 'SC_SLOT_CHANGE',
    'SC_DollOnOff': 'SC_DOLL_ON_OFF', 'SC_CharStat': 'SC_CHAR_STAT',
    'SC_TamWindow': 'SC_TAM_WINDOW',
}
CONSTANTS = {
    'CS_CLIENT_VERSION_INFO_REQ': 'CS_CLIENT_VERSION_INFO',
    'CS_HIBREED_AUTH_REQ': 'CS_HIBREED_AUTH_REQ_PACKET',
    'SC_SIEGE_INJURY_TIME_NOT': 'SC_SIEGE_INJURY_TIME_NOIT',
    'SC_ACHIEVEMENT_TELEPORT_ACK': 'SC_ACHIEVEMENT_TELEPORT_ACk',
    'SC_NOTIFICATION_MESSAGE_NOTI': 'SC_NOTIFICATION_MESSAGE_NOT',
    'SC_PARTY_OPERATION_RESULT': 'SC_PARTY_OPERATION_RESULT_NOTI',
    'SC_PARTY_MEMBER_MARK_CHANGE': 'SC_PARTY_MEMBER_MARK_CHANGE_NOTI',
    'SC_BOX_ATTR_CHANGE_NOTI': 'SC_BOX_ATTR_CHANGE_NOTI_PACKET',
    'SC_EVENT_COUNTDOWN_NOTI': 'SC_EVENT_COUNTDOWN_NOTI_PACKET',
    'SC_FISH_UI': 'SC_FISHING_NOT',
    'SC_TELEPORT_UI': 'SC_MAP_TELEPORT_INFO_NOTI',
}
INDUN = {
    'CS_LIST': 'CS_ARENACO_BYPASS_INDUN_ROOM_LIST_REQ',
    'SC_LIST': 'SC_ARENACO_BYPASS_INDUN_ROOM_LIST_ACK',
    'CS_DETAIL': 'CS_ARENACO_BYPASS_INDUN_ROOM_INFO_REQ',
    'SC_DETAIL': 'SC_ARENACO_BYPASS_INDUN_ROOM_INFO_ACK',
    'CS_CREATE': 'CS_ARENACO_CREATE_INDUN_ROOM_REQ',
    'SC_CREATE': 'SC_ARENACO_CREATE_INDUN_ROOM_ACK',
    'CS_CHANGE': 'CS_ARENACO_BYPASS_CHANGE_INDUN_ROOM_PROPERTY_REQ',
    'SC_CHANGE': 'SC_ARENACO_BYPASS_CHANGE_INDUN_ROOM_PROPERTY_ACK',
    'SC_STATUS': 'SC_ARENACO_BYPASS_CHANGE_INDUN_ROOM_STATUS_NOTI',
    'CS_ENTER': 'CS_ARENACO_ENTER_INDUN_ROOM_REQ',
    'SC_ENTER': 'SC_ARENACO_ENTER_INDUN_ROOM_ACK',
    'SC_MEMBER_ENTER': 'SC_ARENACO_BYPASS_ENTER_INDUN_ROOM_NOTI',
    'CS_READY': 'CS_ARENACO_BYPASS_CHANGE_INDUN_READY_ENTER_REQ',
    'SC_READY': 'SC_ARENACO_BYPASS_CHANGE_INDUN_READY_ENTER_ACK',
    'CS_START': 'CS_ARENACO_BYPASS_INDUN_GAME_START_REQ',
    'SC_START': 'SC_ARENACO_BYPASS_INDUN_GAME_START_ACK',
    'CS_INVITE': 'CS_ARENACO_INVITE_INDUN_ROOM_REQ',
    'SC_INVITE': 'SC_ARENACO_INVITE_INDUN_ROOM_ACK',
    'CS_NOTIFY': 'CS_ARENACO_BYPASS_INDUN_NOTI_REQ',
    'SC_NOTIFY': 'SC_ARENACO_BYPASS_INDUN_NOTI_ACK',
    'CS_EXIT': 'CS_ARENACO_EXIT_INDUN_ROOM_REQ',
    'SC_EXIT': 'SC_ARENACO_EXIT_INDUN_ROOM_ACK',
    'SC_MEMBER_EXIT': 'SC_ARENACO_BYPASS_EXIT_INDUN_ROOM_NOTI',
    'CS_KICK': 'CS_ARENACO_BYPASS_INDUN_KICK_REQ',
    'SC_KICK': 'SC_ARENACO_BYPASS_INDUN_KICK_ACK',
    'SC_GAME_INFO': 'SC_ARENA_GAME_INFO_NOTI',
    'SC_PLAY_STATUS': 'SC_ARENA_PLAY_STATUS_NOTI',
}
PSS_METHODS = {
    'settings': 'handleChangePlaySupportSettingRequest',
    'start': 'handleStartPlaySupportRequest',
    'finish': 'handleFinishPlaySupportRequest',
    'time': 'handlePssTimeCheckRequest',
    'startAck': 'sendStartPlaySupportAck',
    'forceFinish': 'sendForceFinishPlaySupportNoti',
}
INDUN_METHODS = {
    'handle': 'handleRequest', 'reply': 'sendRoomResult',
    'notification': 'sendIndunNotification', 'list': 'sendRoomList',
    'detail': 'sendRoomInfo', 'status': 'broadcastRoomStatus',
    'gameInfo': 'sendArenaGameInfo', 'time': 'sendArenaPlayStatus',
    'inviteResponse': 'handleInviteResponse',
}

def replace_words(text, names):
    pattern = re.compile(r'\b(?:' + '|'.join(map(re.escape, names)) + r')\b')
    return pattern.sub(lambda m: names[m[0]], text)

def scoped_members(text, owner, names, inside):
    for old, new in names.items():
        text = re.sub(r'\b' + owner + r'(\s*(?:\.|::)\s*)' + old + r'\b',
                      lambda m: owner + m[1] + new, text)
        if inside:
            text = re.sub(r'(?<![\w.])' + old + r'(?=\s*\()', new, text)
    return text

def migrate(files):
    schemas = {name: path.name for path in (ROOT / 'schemas').glob('MainServer*.proto')
               for name in re.findall(r'^message\s+(\w+)', path.read_text(), re.M)}
    normalize = lambda name: name.replace('_', '').lower()
    index = {normalize(name): name for name in schemas}
    classes = {}
    records = []
    for path, data in sorted(files.items()):
        name = Path(path).stem
        if not (path.endswith('.java') and '/Protocol/Packet/' in path
                and name.startswith(('CS_', 'SC_'))):
            continue
        text = data.decode('utf-8')
        ids = set(re.findall(r'ProtoMsg\.([A-Z][A-Z0-9_]+)', text))
        canonical = MANUAL.get(name) or index.get(normalize(name))
        if not canonical and len(ids) == 1:
            canonical = next(iter(ids))
            if canonical not in schemas:
                canonical = None
        target = canonical or LOCAL.get(name)
        if not target:
            raise ValueError('Unreviewed packet class: ' + path)
        if canonical:
            assert canonical in schemas, canonical
        classes[name] = target
        records.append({'old': name, 'new': target, 'path': path,
                        'schema': schemas.get(target),
                        'status': 'descriptor_name' if canonical else 'local_adapter_name'})
    assert len(classes) == 86 and len(set(classes.values())) == 86
    assert set(INDUN.values()) <= schemas.keys()
    assert set(CONSTANTS.values()) <= schemas.keys()
    names = classes | CONSTANTS
    output = {}
    for path, data in sorted(files.items()):
        if not path.endswith('.java'):
            output[path] = data
            continue
        text = replace_words(data.decode('utf-8'), names)
        for record in records:
            if record['old'].startswith('CS_'):
                owner = record['new']
                text = scoped_members(text, owner, {'handle': 'handleRequest'}, path == record['path'])
        text = scoped_members(text, 'PlaySupport2019', PSS_METHODS, path.endswith('/PlaySupport2019.java'))
        text = scoped_members(text, 'GludioDungeonPackets', INDUN_METHODS, path.endswith('/GludioDungeonPackets.java'))
        if path.endswith('/GludioDungeonPackets.java'):
            text = replace_words(text, INDUN)
        else:
            for old, new in INDUN.items():
                text = re.sub(r'\bGludioDungeonPackets\.' + old + r'\b',
                              'GludioDungeonPackets.' + new, text)
        newpath = str(Path(path).with_name(classes.get(Path(path).stem, Path(path).stem) + '.java'))
        assert newpath not in output, newpath
        output[newpath] = text.encode('utf-8')
    return output, records, schemas

def fix_reader(text):
    start = text.index('    public long varint64() {')
    end = text.index('    public int varint32()', start)
    text = text[:start] + '''    public long varint64() {
        long value = 0L;
        // The first nine bytes contribute bits 0..62.
        for (int shift = 0; shift < 63; shift += 7) {
            if (_p >= _end) throw malformed("truncated varint");
            final int b = _a[_p++] & 0xFF;
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        // Validate byte ten before shifting; never consume a following field.
        if (_p >= _end) throw malformed("truncated varint64");
        final int last = _a[_p++] & 0xFF;
        if ((last & 0xFE) != 0) throw malformed("varint64 overflow");
        return value | ((long) last << 63);
    }

''' + text[end:]
    start = text.index('    public int tag() {')
    end = text.index('    /**', start)
    text = text[:start] + '''    public int tag() {
        final long value = varint64();
        // A protobuf tag is uint32 and field numbers start at one.
        if (value <= 0L || value > 0xFFFF_FFFFL || (value >>> 3) == 0L) {
            throw malformed("invalid tag: " + value);
        }
        return (int) value;
    }

''' + text[end:]
    return text

def fix_notification_adapter():
    # The old manual writer omitted RGB length and used a single byte for
    # string lengths 128..255. Use the existing descriptor-aligned serializer.
    return '''package l1j.server.server.model.network.core.sc.packet;

import java.nio.charset.Charset;
import l1j.server.MJTemplate.MJSimpleRgb;
import l1j.server.server.model.Instance.L1PcInstance;

/** Compatibility entry point for survey notifications; shares the proto writer. */
public final class SC_NOTIFICATION_MESSAGE {
    public static final int DISPLAY_POSITION_TOP = 1;
    public static final int DISPLAY_POSITION_MIDDLE = 2;
    public static final int DISPLAY_POSITION_BOTTOM = 3;
    private static final Charset MS949 = Charset.forName("MS949");
    private SC_NOTIFICATION_MESSAGE() {}

    public static boolean send(L1PcInstance pc, int position, String message,
                               MJSimpleRgb rgb, int durationSecond) {
        return send(pc, position, message, rgb == null ? null : rgb.get_bytes(), durationSecond);
    }

    public static boolean send(L1PcInstance pc, int position, String message,
                               byte[] rgb, int durationSecond) {
        if (pc == null) return false;
        // Preserve the legacy caller's 255 encoded-byte admission limit.
        if (message != null && message.getBytes(MS949).length > 255) return false;
        return l1j.server.server.packets.Protocol.Packet.SC_NOTIFICATION_MESSAGE
                .sendFields(pc, position, message, rgb, durationSecond, 0, false);
    }
}
'''

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    assert not args.output.exists(), 'Use a new output directory'
    with zipfile.ZipFile(args.base) as archive:
        files = {n: archive.read(n) for n in archive.namelist() if not n.endswith('/')}
    assert len(files) == 1712
    updated, records, schemas = migrate(files)
    reader = 'src/l1j/server/server/packets/Protocol/ProtoReader.java'
    updated[reader] = fix_reader(updated[reader].decode()).encode()
    adapter = 'src/l1j/server/server/model/network/core/sc/packet/SC_NOTIFICATION_MESSAGE.java'
    updated[adapter] = fix_notification_adapter().encode()
    config = 'src/l1j/server/server/model/network/core/config/CIOConfig.java'
    override = ROOT / 'baseline-overrides/CIOConfig.java'
    if override.exists():
        updated[config] = override.read_bytes()
    for name, data in updated.items():
        destination = args.output / name
        assert destination.resolve().is_relative_to(args.output.resolve())
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
    manifest = {
        'base_commit': 'a1d0ea7b0c7a4b96112d054560dcca87b430e6f1',
        'base_archive_sha256': hashlib.sha256(args.base.read_bytes()).hexdigest(),
        'classes': records, 'constant_names': CONSTANTS, 'indun_names': INDUN,
        'handler_method_names': {'CS_packet_classes': {'handle': 'handleRequest'},
                                 'PlaySupport2019': PSS_METHODS, 'GludioDungeonPackets': INDUN_METHODS},
        'function_name_provenance': 'descriptive server adapter names; original server symbols are unavailable',
        'descriptor_message_count': len(schemas),
        'baseline_override': 'CIOConfig.java DLL pin synchronized to tested Windows run 43580772728',
        'deleted_files': sorted(set(files) - set(updated)),
        'changed_files': sorted(n for n, b in updated.items() if files.get(n) != b),
        'baseline_files': {n: hashlib.sha256(b).hexdigest() for n, b in files.items()},
        'output_files': {n: hashlib.sha256(b).hexdigest() for n, b in updated.items()},
    }
    (ROOT / 'MANIFEST.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'classes': len(records), 'descriptor_names': sum(r['status'] == 'descriptor_name' for r in records),
                      'changed_files': len(manifest['changed_files']), 'deleted_paths': len(manifest['deleted_files'])}))

if __name__ == '__main__':
    main()
