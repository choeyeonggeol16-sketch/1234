package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.sc.packet.SC_AddSkill;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.templates.L1Skills;

public final class CS_BuySpell {

    private static final long COST_MASK =
            (1L << 20) - 1L;

    private CS_BuySpell() {
    }

    public static void handle(
            CIOClient client,
            int level1,
            int level2,
            int level3,
            int invalidSid,
            long packedCost) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        if (invalidSid != 0) {

            client.kick();
            client.close();

            return;
        }

        int level1Cost =
                (int) (
                        packedCost
                        & COST_MASK);

        int level2Cost =
                (int) (
                        (packedCost >>> 20)
                        & COST_MASK);

        int level3Cost =
                (int) (
                        (packedCost >>> 40)
                        & COST_MASK);

        final int pcType =
                pc.getType();

        final int pcLevel =
                pc.getLevel();

        /*
         * 클래스별 구매 가능 단계 제한.
         */
        switch (pcType) {

        case 0:
            // 군주
            if (pcLevel < 10) {

                level1 = 0;
                level2 = 0;
                level3 = 0;

                level1Cost = 0;
                level2Cost = 0;
                level3Cost = 0;

            } else if (pcLevel < 20) {

                level2 = 0;
                level3 = 0;

                level2Cost = 0;
                level3Cost = 0;

            } else {

                level3 = 0;
                level3Cost = 0;
            }

            break;

        case 1:
        case 7:
            // 기사 / 전사
            if (pcLevel < 50) {

                level1 = 0;
                level2 = 0;
                level3 = 0;

                level1Cost = 0;
                level2Cost = 0;
                level3Cost = 0;

            } else {

                level2 = 0;
                level3 = 0;

                level2Cost = 0;
                level3Cost = 0;
            }

            break;

        case 2:
            // 요정
            if (pcLevel < 8) {

                level1 = 0;
                level2 = 0;
                level3 = 0;

                level1Cost = 0;
                level2Cost = 0;
                level3Cost = 0;

            } else if (pcLevel < 16) {

                level2 = 0;
                level3 = 0;

                level2Cost = 0;
                level3Cost = 0;

            } else if (pcLevel < 24) {

                level3 = 0;
                level3Cost = 0;
            }

            break;

        case 3:
            // 마법사
            if (pcLevel < 4) {

                level1 = 0;
                level2 = 0;
                level3 = 0;

                level1Cost = 0;
                level2Cost = 0;
                level3Cost = 0;

            } else if (pcLevel < 8) {

                level2 = 0;
                level3 = 0;

                level2Cost = 0;
                level3Cost = 0;

            } else if (pcLevel < 12) {

                level3 = 0;
                level3Cost = 0;
            }

            break;

        case 4:
            // 다크엘프
            if (pcLevel < 12) {

                level1 = 0;
                level2 = 0;
                level3 = 0;

                level1Cost = 0;
                level2Cost = 0;
                level3Cost = 0;

            } else if (pcLevel < 24) {

                level2 = 0;
                level3 = 0;

                level2Cost = 0;
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

        final int totalPrice =
                level1Cost
                + level2Cost
                + level3Cost;

        if (!pc.getInventory()
                .checkItem(
                        L1ItemId.ADENA,
                        totalPrice)) {

            SC_ServerMessage.send(pc, 189);

            return;
        }

        pc.getInventory()
                .consumeItem(
                        L1ItemId.ADENA,
                        totalPrice);

        SC_SkillSound.send(pc, pc.getId(), 224);

        /*
         * S 쪽은 지금 유지.
         */
        if (pc.isElf()) {

            SC_AddSkill.send(pc, level1,
                            level2,
                            level3,
                            0, 0, 0, 0, 0, 0, 0,
                            0, 0, 0, 0, 0, 0, 0,
                            0, 0, 0, 0, 0, 0, 0,
                            0, 0, 0, 0, 0, 0,
                            pc.getElfAttr());

        } else {

            SC_AddSkill.send(pc, level1,
                            level2,
                            level3,
                            0, 0, 0, 0, 0, 0, 0,
                            0, 0, 0, 0, 0, 0, 0,
                            0, 0, 0, 0, 0, 0, 0,
                            0, 0, 0, 0, 0, 0,
                            0);
        }

        final SkillsTable skillsTable =
                SkillsTable.getInstance();

        registerSkills(
                skillsTable,
                pc,
                level1,
                1);

        registerSkills(
                skillsTable,
                pc,
                level2,
                9);

        registerSkills(
                skillsTable,
                pc,
                level3,
                17);
    }

    private static void registerSkills(
            SkillsTable skillsTable,
            L1PcInstance pc,
            int levelFlag,
            int startSkillId) {

        /*
         * flag가 0이면 8회 loop 자체 생략.
         */
        if (levelFlag == 0) {
            return;
        }

        for (int bit = 0;
             bit < 8;
             bit++) {

            if ((levelFlag
                    & (1 << bit)) == 0) {

                continue;
            }

            final int skillId =
                    startSkillId + bit;

            final L1Skills skill =
                    skillsTable.getTemplate(
                            skillId);

            if (skill == null) {
                continue;
            }

            skillsTable.spellMastery(
                    pc.getId(),
                    skill.getSkillId(),
                    skill.getName(),
                    0,
                    0);
        }
    }
}