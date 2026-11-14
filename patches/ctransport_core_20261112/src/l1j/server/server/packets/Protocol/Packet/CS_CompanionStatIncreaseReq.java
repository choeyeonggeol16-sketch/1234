package l1j.server.server.packets.Protocol.Packet;

import l1j.server.MJCompanion.Instance.MJCompanionUpdateFlag;
import l1j.server.MJCompanion.CompanionSettings;
import l1j.server.server.model.Ability;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.ProtoReader;

/**
 * CS_COMPANION_STAT_INCREASE_REQ (0x07D3) — 컴패니언 스탯 분배 요청
 * wire: [1]=str uint32, [2]=int uint32, [3]=con uint32 (모두 optional, 기본 0)
 */
public final class CS_CompanionStatIncreaseReq {

    private CS_CompanionStatIncreaseReq() {}

    public static void handle(CIOClient client, ProtoReader r) {
        int str = 0, in = 0, con = 0;
        try {
            while (!r.isAtEnd()) {
                int tag = r.tag();
                switch (tag) {
                case (1 << 3) | 0: str = r.varint32(); break;
                case (2 << 3) | 0: in  = r.varint32(); break;
                case (3 << 3) | 0: con = r.varint32(); break;
                default: r.skip(tag & 7);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // ── 처리 (구 readFrom 로직 이식) ──
        try {
            L1PcInstance pc = client.getActiveChar();
            if (pc == null) return;

            L1CompanionInstance companion = pc.get_companion();
            if (companion == null) return;

            if (companion.isDead()) {
                SC_CompanionStatusNoti.send(pc, companion, MJCompanionUpdateFlag.UPDATE_STATS);
                pc.sendPackets("죽어있는 펫에게 스탯을 부여할 수 없습니다.");
                return;
            }

            if (!valid_min_stat(pc, companion, str, "힘") ||
                !valid_min_stat(pc, companion, con, "콘") ||
                !valid_min_stat(pc, companion, in, "인트"))
                return;

            int remainStats = companion.get_remain_stats();
            int addedTotal = str + con + in;
            if (remainStats < addedTotal) {
                SC_CompanionStatusNoti.send(pc, companion, MJCompanionUpdateFlag.UPDATE_STATS);
                pc.sendPackets("스탯이 부족합니다.");
                System.out.println(String.format(
                        "%s님의 펫 %s(%d)에게 잘못된 스탯을 부여하려고 했습니다.\r\n남아있는 스탯 : %d\r\n부여 시도한 스탯 : %d",
                        pc.getName(), companion.getName(), companion.getId(), remainStats, addedTotal));
                return;
            }
            Ability ability = companion.getAbility();
            if (!valid_max_stat(pc, companion, ability.getTotalStr(), ability.getStr(), str, "힘") ||
                !valid_max_stat(pc, companion, ability.getTotalCon(), ability.getCon(), con, "콘") ||
                !valid_max_stat(pc, companion, ability.getTotalInt(), ability.getInt(), in, "인트"))
                return;

            ability.addAddedStr(str);
            ability.addAddedCon(con);
            ability.addAddedInt(in);
            companion.update_stats();
            SC_CompanionStatusNoti.send(pc, companion, MJCompanionUpdateFlag.UPDATE_ALL);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean valid_max_stat(L1PcInstance pc, L1CompanionInstance companion, int totalStat, int baseStat, int addedStat, String section) {
        int sumStat = totalStat + addedStat;
        if (sumStat > CompanionSettings.MAX_STAT) {
            SC_CompanionStatusNoti.send(pc, companion, MJCompanionUpdateFlag.UPDATE_STATS);
            pc.sendPackets(String.format("한 스탯에 최종 %d개 이상의 스탯을 부여할 수 없습니다.", CompanionSettings.MAX_STAT));
            System.out.println(String.format(
                    "%s님의 펫 %s(%d)에게 %d이상의 %s을(를) 부여하려고 했습니다.\r\n현재 %s : %d\r\n추가 시도한 %s : %d\r\n합계 %s : %d",
                    pc.getName(), companion.getName(), companion.getId(), CompanionSettings.MAX_STAT, section,
                    section, totalStat, section, addedStat, section, sumStat));
            return false;
        }
        if (sumStat < baseStat) {
            SC_CompanionStatusNoti.send(pc, companion, MJCompanionUpdateFlag.UPDATE_STATS);
            pc.sendPackets(String.format("%s 스탯은 %d 이하로 부여할 수 없습니다.", section, baseStat));
            System.out.println(String.format(
                    "%s님의 펫 %s(%d)에게 %d미만의 %s을(를) 부여하려고 했습니다.\r\n현재 %s : %d\r\n추가 시도한 %s : %d\r\n합계 %s : %d\r\n베이스 %s : %d",
                    pc.getName(), companion.getName(), companion.getId(), baseStat, section,
                    section, totalStat, section, addedStat, section, sumStat, section, baseStat));
            return false;
        }
        return true;
    }

    private static boolean valid_min_stat(L1PcInstance pc, L1CompanionInstance companion, int addedStat, String section) {
        if (addedStat < 0) {
            SC_CompanionStatusNoti.send(pc, companion, MJCompanionUpdateFlag.UPDATE_STATS);
            pc.sendPackets("잘못된 스탯입니다.");
            System.out.println(String.format(
                    "%s님의 펫 %s(%d)에게 마이너스 %s 스탯을 부여하려고 했습니다.\r\n추가 시도한 %s : %d",
                    pc.getName(), companion.getName(), companion.getId(), section, section, addedStat));
            return false;
        }
        return true;
    }
}