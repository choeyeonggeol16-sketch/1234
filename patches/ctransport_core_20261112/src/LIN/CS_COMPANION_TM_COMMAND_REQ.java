package LIN;

import LIN.CompanionT.eCommand;
import l1j.server.MJCompanion.Basic.Joke.MJCompanionJokeInfo;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.ProtoReader;


/**
 * CS_COMPANION_TM_COMMAND_REQ (0x07D4) — 컴패니언 명령 (공격대상지정/소환/장난/기쁨/태세변경)
 * wire: [1]=command enum(varint), [2]=target_id uint32(optional)
 */
public final class CS_COMPANION_TM_COMMAND_REQ {

    private CS_COMPANION_TM_COMMAND_REQ() {}

    public static void handle(CIOClient client, ProtoReader r) {
        // ── 파싱 ──
        LIN.CompanionT.eCommand command = null;
        int targetId = 0;
        boolean hasCommand = false;
        try {
            while (!r.isAtEnd()) {
                int tag = r.tag();
                switch (tag) {
                case (1 << 3) | 0:                       // field 1, enum(varint)
                    command = LIN.CompanionT.eCommand.fromInt(r.varint32());
                    hasCommand = true;
                    break;
                case (2 << 3) | 0:                       // field 2, uint32
                    targetId = r.varint32();
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
        if (!hasCommand || command == null) return;

        // ── 처리 (구 readFrom 로직 이식) ──
        try {
            L1PcInstance master = client.getActiveChar();
            if (master == null) return;

            L1CompanionInstance companion = master.get_companion();
            if (companion == null) return;

            switch (command) {
            case TM_Attack: {
                L1Object obj = L1World.getInstance().findObject(targetId);
                if (obj != null && obj instanceof L1Character)
                    companion.set_target((L1Character) obj);
                break;
            }
            case TM_PullBack:
                companion.allTargetClear();
                break;
            case Joke: {
                companion.send_action(67);
                MJCompanionJokeInfo jInfo = MJCompanionJokeInfo.find_joke_info(
                        companion.get_class_info().get_class_name(), companion.getLevel());
                if (jInfo != null && jInfo.get_joke_effect() > 0)
                    companion.send_effect(jInfo.get_joke_effect());
                break;
            }
            case Happy:
                companion.send_action(68);
                break;
            case TM_Aggressive:
            case TM_GetItem:
            case TM_Defensive:
                companion.set_command_state(command);
                break;
            default:
                System.out.println(command);
                break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}