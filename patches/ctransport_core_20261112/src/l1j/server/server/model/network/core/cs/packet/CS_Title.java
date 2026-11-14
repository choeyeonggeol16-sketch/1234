package l1j.server.server.model.network.core.cs.packet;


import l1j.server.server.model.network.core.sc.packet.SC_CharTitle;
import l1j.server.Config;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class CS_Title {

    private CS_Title() {
    }

    public static void handle(
            CIOClient client,
            String charName,
            String title) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        if (title == null
                || title.length() > 16) {

            SC_SystemMessage.send(pc, "호칭으로 쓸 수 있는 글자 수를 초과하였습니다.");

            return;
        }

        if (charName == null
                || charName.isEmpty()
                || title.isEmpty()) {

            SC_ServerMessage.send(pc, 196);

            return;
        }

        final L1World world =
                L1World.getInstance();

        final L1PcInstance target =
                world.getPlayer(
                        charName);

        if (target == null) {
            return;
        }

        if (Config.수배온오프) {

            if (target.hasSkillEffect(
                        L1SkillId.USER_WANTED)
                    || target.hasSkillEffect(
                        L1SkillId.USER_WANTED1)) {

                pc.sendPackets(
                        "수배 중에는 호칭을 변경할 수 없습니다.");

                return;
            }
        }

        /*
         * GM은 즉시 변경.
         */
        if (pc.isGm()) {

            changeTitle(
                    target,
                    title);

            return;
        }

        final boolean self =
                pc.getId()
                        == target.getId();

        /*
         * 군주 / 수호 / 부군주 계열.
         */
        if (hasTitleManagementPermission(
                pc,
                target)) {

            if (self) {

                if (pc.getLevel() < 10) {

                    SC_ServerMessage.send(pc, 197);

                    return;
                }

                changeTitle(
                        pc,
                        title);

                return;
            }

            if (pc.getClanid()
                    != target.getClanid()) {

                SC_ServerMessage.send(pc, 199);

                return;
            }

            if (target.getLevel() < 10) {

                SC_ServerMessage.send(pc, 202, charName);

                return;
            }

            changeTitle(
                    target,
                    title);

            final L1Clan clan =
                    world.getClan(
                            pc.getClanid());

            if (clan != null) {

                for (L1PcInstance clanPc :
                        clan.getOnlineClanMember()) {

                    if (clanPc == null) {
                        continue;
                    }

                    SC_ServerMessage.send(clanPc, 203, pc.getName(), charName, title);
                }
            }

            return;
        }

        /*
         * 일반 유저.
         */
        if (self) {

            if (pc.getClanid() != 0
                    && !Config.CHANGE_TITLE_BY_ONESELF) {

                SC_ServerMessage.send(pc, 198);

                return;
            }

            if (target.getLevel() < 40) {

                SC_SystemMessage.send(pc, "해당 레벨에는 호칭을 가질 수 없습니다.");

                return;
            }

            changeTitle(
                    pc,
                    title);

            return;
        }

        /*
         * 원본 로직 그대로.
         *
         * 메시지 조건도 원본의 == 그대로 유지.
         */
        if (pc.isCrown()) {

            if (pc.getClanid()
                    == target.getClanid()) {

                SC_ServerMessage.send(pc, 201, pc.getClanname());
            }
        }
    }

    private static boolean hasTitleManagementPermission(
            L1PcInstance pc,
            L1PcInstance target) {

        if (pc.getClanid() <= 0
                || pc.getClanid()
                    != target.getClanid()) {

            return false;
        }

        final int rank =
                pc.getClanRank();

        /*
         * 원본 semantics 그대로.
         *
         * 여기서 getClan() null guard를 일부러 추가하지 않음.
         */
        final boolean leader =
                pc.isCrown()
                && pc.getId()
                    == L1World
                        .getInstance()
                        .getClan(
                                pc.getClanid())
                        .getLeaderId();

        final boolean guardianOrSubLeader =
                rank == L1Clan.수호
                || rank == L1Clan.부군주
                || rank == 6;

        return leader
                || guardianOrSubLeader;
    }

    private static void changeTitle(
            L1PcInstance pc,
            String title) {

        final int objectId =
                pc.getId();

        pc.setTitle(
                title);

        String broadcastTitle =
                title;

        if (Config.수배온오프) {

            if (pc.hasSkillEffect(
                    L1SkillId.USER_WANTED)) {

                broadcastTitle =
                        L1PcInstance.WANTED_TITLE;

            } else if (pc.hasSkillEffect(
                    L1SkillId.USER_WANTED1)) {

                broadcastTitle =
                        L1PcInstance.WANTED_TITLE1;
            }
        }

        SC_CharTitle.send(pc, objectId, broadcastTitle);

        SC_CharTitle.broadcast(pc, objectId, broadcastTitle);

 
        ThreadPool
                .getInstance()
                .execute(() -> {

                    try {

                        pc.save();

                    } catch (Exception e) {

                        e.printStackTrace();
                    }
                });
    }
}