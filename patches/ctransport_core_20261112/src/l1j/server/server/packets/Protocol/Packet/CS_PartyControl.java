package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;

/**
 * CS_PARTY_CONTROL (0x033C)
 * 와이어:
 *   1 control_type        enum   (varint)  [required]
 *   2 target_object_id    uint32 (varint)
 *   3 target_user_name    string (MS949)
 *   4 target_mark         int32  (varint)
 */
public final class CS_PartyControl {
    private CS_PartyControl() {}

    public enum ePartyControlType {
        Command_InvatePartyNormalById(0, 0),	// 대상 클릭 초대 (자동분배 X)
        Command_InvatePratyRandomById(1, 1),	// 대상 클릭 초대 (자동분배 O)
        Command_InvateChatParty(2, 0),
        Command_TransforLeader(3, 0),
        Command_InvatePartyNormal(4, 0),		// /초대 (자동분배 X)
        Command_InvatePartyRandom(5, 1),		// /초대 (자동분배 O)
        Command_TransforMark(6, 0),
        Command_KickUser(7, 0);

        private final int value;
        private final int partyServerType;

        ePartyControlType(int val, int serverType) {
            value = val;
            partyServerType = serverType;
        }
        public int toInt() { return value; }
        public int getPartyServerType() { return partyServerType; }

        public static ePartyControlType fromInt(int i) {
            switch (i) {
            case 0: return Command_InvatePartyNormalById;
            case 1: return Command_InvatePratyRandomById;
            case 2: return Command_InvateChatParty;
            case 3: return Command_TransforLeader;
            case 4: return Command_InvatePartyNormal;
            case 5: return Command_InvatePartyRandom;
            case 6: return Command_TransforMark;
            case 7: return Command_KickUser;
            default: return null;
            }
        }
    }

    public static void handle(CIOClient client,  ProtoReader r) {
        // ── 파싱 ──
        int controlTypeVal = -1;
        int targetObjectId = 0;
        String targetUserName = null;
        int targetMark = 0;

        try {
            while (!r.isAtEnd()) {
                int tag = r.tag();
                switch (tag) {
                case 0x08: controlTypeVal = r.varint32(); break;
                case 0x10: targetObjectId = r.varint32(); break;
                case 0x1A: targetUserName = r.readString(); break;
                case 0x20: targetMark = r.varint32(); break;
                default:
                    r.skip(tag & 7);
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("[CS_PartyControl] parse fail: " + e);
            return;
        }
        // control_type은 required — 없으면 드랍 
        if (controlTypeVal < 0) return;
        ePartyControlType type = ePartyControlType.fromInt(controlTypeVal);
        if (type == null) return;	// 알 수 없는 enum값

        L1PcInstance pc = client.getActiveChar();
        if (pc == null) return;

        // 이름 필드 누락 대비 — getPlayer(null) 방지
        L1PcInstance target = targetUserName == null ? null : L1World.getInstance().getPlayer(targetUserName);

        try {
            switch (type) {
            case Command_InvatePartyNormalById:
            case Command_InvatePratyRandomById:
            case Command_InvatePartyNormal:
            case Command_InvatePartyRandom: {
                if (pc.getMapId() == 621 || !pc.is_world()) {
                    pc.sendPackets("해당 맵에서는 파티 기능을 사용 할 수 없습니다.");
                    return;
                }
                if (target == null || (targetObjectId > 0 && target.getId() != targetObjectId)) {
                    pc.sendPackets("알 수 없는 대상입니다.");
                    return;
                }
                if (target.getId() == pc.getId()) {
                    pc.sendPackets("자기 자신에게는 초대할 수 없습니다.");
                    return;
                }
                if (target.isInParty()) {
                    pc.sendPackets(415);
                    return;
                }
                L1Party party = pc.getParty();
                if (party != null) {
                    if (!party.isLeader(pc)) {
                        pc.sendPackets(416);	// 파티의 리더만을 초대할 수 있습니다.
                        return;
                    }
                    target.setPartyID(pc.getId());
                    SC_MessageYN.send(target, 953 + pc.getPartyType(), pc.getName());
                } else {
                    target.setPartyID(pc.getId());
                    pc.setPartyType(type.getPartyServerType());
                    SC_MessageYN.send(target, 953 + pc.getPartyType(), pc.getName());
                }
                break;
            }
            case Command_InvateChatParty: {
            	
                if (pc.getMapId() == 621 || !pc.is_world()) {
                    pc.sendPackets("해당 맵에서는 파티 기능을 사용 할 수 없습니다.");
                    return;
                }
                if (target == null || (targetObjectId > 0 && target.getId() != targetObjectId)) {
                    pc.sendPackets("알 수 없는 대상입니다.");
                    return;
                }
                if (target.getId() == pc.getId()) {
                    pc.sendPackets("자기 자신에게는 초대할 수 없습니다.");
                    return;
                }
                if (target.isInChatParty()) {
                    pc.sendPackets(415);
                    return;
                }
                L1Party party = pc.getParty();
                if (party != null) {
                    if (!party.isLeader(pc)) {
                        pc.sendPackets(416);
                        return;
                    }
                    SC_MessageYN.send(target, 951, pc.getName());
                } else {
                    target.setPartyID(pc.getId());
                    SC_MessageYN.send(target, 951, pc.getName());
                }
                break;
            }
            case Command_TransforLeader: {
                L1Party party = pc.getParty();
                if (party == null) {
                    System.out.println(String.format("파티 없는 사용자가 파티장 위임 : %s", pc.getName()));
                    return;
                }
                if (!party.isLeader(pc)) {
                    System.out.println(String.format("파티장 아닌 사용자가 파티장 위임 : %s", pc.getName()));
                    return;
                }
                if (target == null || target.getId() != targetObjectId) {
                    pc.sendPackets("알 수 없는 대상입니다.");
                    return;
                }
                party.passLeader(target);
                break;
            }
            case Command_TransforMark: {
                L1Party party = pc.getParty();
                if (party == null) {
                    System.out.println(String.format("파티 없는 사용자가 표식 설정 : %s", pc.getName()));
                    return;
                }
                if (!party.isLeader(pc)) {
                    System.out.println(String.format("파티장 아닌 사용자가 표식 설정 : %s", pc.getName()));
                    return;
                }
                if (target == null || target.getId() != targetObjectId) {
                    pc.sendPackets("알 수 없는 대상입니다.");
                    return;
                }
                party.setPartyMark(target, targetMark);
                break;
            }
            case Command_KickUser: {
                L1Party party = pc.getParty();
                if (party == null) {
                    System.out.println(String.format("파티 없는 사용자가 파티원 추방 : %s", pc.getName()));
                    return;
                }
                if (!party.isLeader(pc)) {
                    System.out.println(String.format("파티장 아닌 사용자가 파티원 추방 : %s", pc.getName()));
                    return;
                }
                if (target == null) {
                    pc.sendPackets("알 수 없는 대상입니다.");
                    return;
                }
                party.kickMember(target);
                break;
            }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(String.format("[CS_PartyControlReq] 케릭터 이름 : %s", pc.getName()));
        }
    }
}