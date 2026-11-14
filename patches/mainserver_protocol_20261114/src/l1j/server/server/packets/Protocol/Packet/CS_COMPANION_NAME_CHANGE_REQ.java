package l1j.server.server.packets.Protocol.Packet;

import java.util.HashMap;

import l1j.server.MJCompanion.Instance.MJCompanionNameHandler;
import l1j.server.server.BadNamesList;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.packets.Protocol.Packet.SC_COMPANION_NAME_CHANGE_ACK.eResult;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/**
 * CS_COMPANION_NAME_CHANGE_REQ (0x07D1) — 컴패니언(펫) 이름 변경 요청
 * wire: [1]=desired_name bytes (LEN, MS949 문자열)
 */
public final class CS_COMPANION_NAME_CHANGE_REQ {

    private CS_COMPANION_NAME_CHANGE_REQ() {}

    /** 구 generated의 한글 자모/금칙어 필터 — 값 컬렉션은 구와 1:1 동일 (Byte.MIN_VALUE는 더미값) */
    private static final HashMap<Character, Byte> INVALID_KO_CHARACTERS = new HashMap<>();
    static {
        final String invalid = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
                + "ㅛㅕㅑㅐㅔ"
                + "ㅗㅓㅏㅣ"
                + "ㅠㅜㅡ"
                + "ㅒㅖ"
                + "ㅢㅟㅝㅞㅙㅚ";
        for (char c : invalid.toCharArray()) {
            INVALID_KO_CHARACTERS.put(c, Byte.MIN_VALUE);
        }
        INVALID_KO_CHARACTERS.put('씹', Byte.MIN_VALUE);
        INVALID_KO_CHARACTERS.put('좆', Byte.MIN_VALUE);
        INVALID_KO_CHARACTERS.put('좃', Byte.MIN_VALUE);
    }

    public static void handleRequest(CIOClient client, ProtoReader r) {
        // ── 파싱 ──
        byte[] desiredName = null;
        boolean hasName = false;
        try {
          
            while (!r.isAtEnd()) {
                int tag = r.tag();
                switch (tag) {
                case (1 << 3) | 2:                      // field 1, bytes
                    desiredName = r.readBytes();
                    hasName = true;
                    break;
                default:
                    r.skip(tag & 7);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // required 계약 (구 isInitialized 대응)
        if (!hasName) return;

        // ── 처리 (구 readFrom(GameClient, byte[]) 로직 이식) ──
        try {
            L1PcInstance pc = client.getActiveChar();
            if (pc == null) return;

            L1CompanionInstance companion = pc.get_companion();
            if (companion == null) return;

            if (companion.get_name_changed_count() > 0) {
                pc.sendPackets("펫 이름 변경 아이템을 사용하여 이름을 초기화해주세요.");
                return;
            }

            if (desiredName == null || desiredName.length <= 0) {
                SC_COMPANION_NAME_CHANGE_ACK.send(client, eResult.SystemError, null);
                return;
            }

            if (desiredName.length > 40) {
                SC_SystemMessage.send(client.getActiveChar(), "이름이 너무 깁니다.");
                SC_COMPANION_NAME_CHANGE_ACK.send(client, eResult.SystemError, null);
                return;
            }

            String replaceName = new String(desiredName, "MS949");
            if (BadNamesList.getInstance().isBadName(replaceName) || 5 < desiredName.length - replaceName.length()) {
                SC_SystemMessage.send(client.getActiveChar(), "사용할 수 없는 이름입니다.");
                SC_COMPANION_NAME_CHANGE_ACK.send(client, eResult.SystemError, null);
                return;
            }
            for (int i = replaceName.length() - 1; i >= 0; --i) {
                char c = replaceName.charAt(i);
                if (!Character.isLetterOrDigit(c) || INVALID_KO_CHARACTERS.containsKey(c)) {
                    SC_SystemMessage.send(client.getActiveChar(), "사용할 수 없는 문자가 포함되어 있습니다.");
                    SC_COMPANION_NAME_CHANGE_ACK.send(client, eResult.SystemError, null);
                    return;
                }
            }
            if (MJCompanionNameHandler.exists_name(replaceName)) {
                SC_COMPANION_NAME_CHANGE_ACK.send(client, eResult.SameNameExisted, null);
                return;
            }
            companion.increase_name_changed_count();
            companion.do_name_change(replaceName);
            companion.setName(replaceName);
            companion.update_name();
            SC_COMPANION_NAME_CHANGE_ACK.send_bytes(client, eResult.Success, desiredName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}