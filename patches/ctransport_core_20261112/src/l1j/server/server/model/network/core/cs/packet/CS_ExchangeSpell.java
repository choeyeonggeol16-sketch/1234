package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.sc.packet.SC_HorunOK;

import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;
import l1j.server.server.templates.L1Skills;

public final class CS_ExchangeSpell {

    private CS_ExchangeSpell() {
    }

    public static void handle(
            CIOClient client,
            int count,
            int sid0,
            int sid1,
            int sid2,
            long packed34,
            long packed56,
            Object object) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        /*
         * 원본:
         *
         * if (_sids == null) {
         *     return;
         * }
         *
         * _sids는 count > 0에서만 생성된다.
         */
        if (count <= 0) {
            return;
        }

        final int sid3 =
                (int) (packed34 >> 32);

        final int sid4 =
                (int) packed34;

        final int sid5 =
                (int) (packed56 >> 32);

        final int sid6 =
                (int) packed56;

        final byte[] tail =
                object instanceof byte[]
                        ? (byte[]) object
                        : null;

        final int tailLength =
                tail != null
                        ? tail.length
                        : 0;

        int tailPos = 0;

        int level1 = 0;
        int level2 = 0;
        int level3 = 0;

        int level1Cost = 0;
        int level2Cost = 0;
        int level3Cost = 0;

        /*
         * 기존 int[]을 만들지 않고
         * 순차적으로 SID를 처리한다.
         *
         * 여기부터가 game-side semantics.
         */
        for (int i = 0;
                i < count;
                i++) {

            final int sid;

            switch (i) {

            case 0:
                sid = sid0;
                break;

            case 1:
                sid = sid1;
                break;

            case 2:
                sid = sid2;
                break;

            case 3:
                sid = sid3;
                break;

            case 4:
                sid = sid4;
                break;

            case 5:
                sid = sid5;
                break;

            case 6:
                sid = sid6;
                break;

            default: {

                int value = 0;

                /*
                 * legacy readD():
                 *
                 * 4 byte 미만이면 0이며
                 * position을 소비하지 않는다.
                 *
                 * 따라서 truncated tail이면
                 * 이후 SID도 계속 0이 된다.
                 */
                if (tailLength - tailPos >= 4) {

                    value =
                            readIntLE(
                                    tail,
                                    tailPos);

                    tailPos += 4;
                }

                sid = value;
                break;
            }
            }

            /*
             * 원본은 > 24만 disconnect.
             *
             * sid == 24:
             * disconnect 하지 않고 아무 효과 없음.
             *
             * sid < 0:
             * 역시 switch default로 아무 효과 없음.
             */
            if (sid > 24) {

                SC_Disconnect.send(pc);

                return;
            }

            if (sid >= 0
                    && sid <= 7) {

                /*
                 * 원본은 OR가 아니라 += 였다.
                 *
                 * 중복 SID에서 carry가 발생할 수 있으므로
                 * 반드시 덧셈으로 유지.
                 */
                level1 +=
                        1 << sid;

                level1Cost += 10;

            } else if (sid >= 8
                    && sid <= 15) {

                level2 +=
                        1 << (sid - 8);

                level2Cost += 400;

            } else if (sid >= 16
                    && sid <= 23) {

                level3 +=
                        1 << (sid - 16);

                level3Cost += 900;
            }
        }

        /*
         * 클래스/레벨 제한.
         *
         * 원본 순서 및 결과 그대로.
         */
        final int level =
                pc.getLevel();

        switch (pc.getType()) {

        /*
         * 군주
         */
        case 0:

            if (level < 10) {

                level1 = 0;
                level1Cost = 0;

                level2 = 0;
                level2Cost = 0;

                level3 = 0;
                level3Cost = 0;

            } else if (level <= 19) {

                level2 = 0;
                level2Cost = 0;

                level3 = 0;
                level3Cost = 0;

            } else {

                level3 = 0;
                level3Cost = 0;
            }

            break;

        /*
         * 기사
         */
        case 1:

            if (level < 50) {

                level1 = 0;
                level1Cost = 0;

                level2 = 0;
                level2Cost = 0;

                level3 = 0;
                level3Cost = 0;

            } else {

                level2 = 0;
                level2Cost = 0;

                level3 = 0;
                level3Cost = 0;
            }

            break;

        /*
         * ELF
         */
        case 2:

            if (level < 8) {

                level1 = 0;
                level1Cost = 0;

                level2 = 0;
                level2Cost = 0;

                level3 = 0;
                level3Cost = 0;

            } else if (level <= 15) {

                level2 = 0;
                level2Cost = 0;

                level3 = 0;
                level3Cost = 0;

            } else if (level <= 23) {

                level3 = 0;
                level3Cost = 0;
            }

            break;

        /*
         * WIZ
         */
        case 3:

            if (level < 4) {

                level1 = 0;
                level1Cost = 0;

                level2 = 0;
                level2Cost = 0;

                level3 = 0;
                level3Cost = 0;

            } else if (level <= 7) {

                level2 = 0;
                level2Cost = 0;

                level3 = 0;
                level3Cost = 0;

            } else if (level <= 11) {

                level3 = 0;
                level3Cost = 0;
            }

            break;

        /*
         * DE
         */
        case 4:

            if (level < 12) {

                level1 = 0;
                level1Cost = 0;

                level2 = 0;
                level2Cost = 0;

                level3 = 0;
                level3Cost = 0;

            } else if (level <= 23) {

                level2 = 0;
                level2Cost = 0;

                level3 = 0;
                level3Cost = 0;

            } else {

                level3 = 0;
                level3Cost = 0;
            }

            break;

        default:
            break;
        }

        if (level1 == 0
                && level2 == 0
                && level3 == 0) {

            return;
        }

        final int price =
                level1Cost
                + level2Cost
                + level3Cost;

        final SkillsTable skills =
                SkillsTable.getInstance();

        final L1PcInventory inventory =
                pc.getInventory();

        /*
         * 원본 skill_id는 0으로 시작하고,
         * 성공했을 때만 갱신된다.
         *
         * 따라서 아데나(?) 부족 시 SC_HorunOK에는
         * 직전에 성공한 skillId가 들어갈 수 있다.
         *
         * 이상하지만 그대로 유지.
         */
        int skillId = 0;

        /*
         * Level 1
         *
         * 원본은 선택된 각각의 skill마다
         * 전체 price를 다시 check/consume한다.
         *
         * 이것도 그대로 유지.
         */
        for (int bit = 0;
                bit < 8;
                bit++) {

            final int mask =
                    1 << bit;

            if ((level1 & mask) != mask) {
                continue;
            }

            if (inventory.checkItem(
                    L1ItemId.PANS_LEATHER,
                    price)) {

                inventory.consumeItem(
                        L1ItemId.PANS_LEATHER,
                        price);

                final L1Skills skill =
                        skills.getTemplate(
                                bit + 1);

                final String skillName =
                        skill.getName();

                skillId =
                        skill.getSkillId();

                skills.spellMastery(
                        pc.getId(),
                        skillId,
                        skillName,
                        0,
                        0);

            } else {

                SC_HorunOK.send(pc, skillId);
            }
        }

        /*
         * Level 2
         *
         * 원본은 여기서는 재화 check/consume 없이
         * 바로 spellMastery().
         */
        for (int bit = 0;
                bit < 8;
                bit++) {

            final int mask =
                    1 << bit;

            if ((level2 & mask) != mask) {
                continue;
            }

            final L1Skills skill =
                    skills.getTemplate(
                            9 + bit);

            final String skillName =
                    skill.getName();

            skillId =
                    skill.getSkillId();

            skills.spellMastery(
                    pc.getId(),
                    skillId,
                    skillName,
                    0,
                    0);
        }

        /*
         * Level 3
         *
         * 주의:
         * 원본에는 bit 0~6,
         * 즉 skill 17~23까지만 있다.
         *
         * level3 & 128 -> skill 24 처리가 없다.
         *
         * 따라서 bit < 7이 정확한 원본 semantics.
         */
        for (int bit = 0;
                bit < 7;
                bit++) {

            final int mask =
                    1 << bit;

            if ((level3 & mask) != mask) {
                continue;
            }

            final L1Skills skill =
                    skills.getTemplate(
                            17 + bit);

            final String skillName =
                    skill.getName();

            skillId =
                    skill.getSkillId();

            skills.spellMastery(
                    pc.getId(),
                    skillId,
                    skillName,
                    0,
                    0);
        }
    }

    private static int readIntLE(
            byte[] data,
            int offset) {

        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }
}