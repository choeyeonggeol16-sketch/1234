package l1j.server.server.model.network.core.cs.packet;

import static l1j.server.server.model.character.skill.L1SkillId.SHAPE_CHANGE;
import l1j.server.server.model.network.core.sc.packet.SC_EffectLocation;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import l1j.server.Config;
import l1j.server.IndunSystem.Orim.OrimController;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;

public final class CS_ExtraCommand {

    private static final int ORIM_MAP_ID = 9101;

    private static final int ORIM_X = 32799;
    private static final int ORIM_Y = 32808;
    private static final int ORIM_HEADING = 4;

    private CS_ExtraCommand() {}

    public static void handle(CIOClient client, int actionId) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc = client.getActiveChar();

        if (pc == null || pc.isGhost()) {
            return;
        }

        if (pc.isInvisble()) {
            return;
        }

        if (pc.get_teleport()) {
            return;
        }

        if (pc.hasSkillEffect(SHAPE_CHANGE)) {
            final int gfxId = pc.getCurrentSpriteId();

            if (gfxId != 6080 && gfxId != 6094) {
                return;
            }

        } else {
            SC_DoActionGFX.broadcast(pc, actionId);
        }

        if (pc.getMapId() != ORIM_MAP_ID || !pc.isInParty()) {
            return;
        }

        if (!pc.getParty().isLeader(pc) || pc.getX() != ORIM_X || pc.getY() != ORIM_Y || pc.getHeading() != ORIM_HEADING) {
            return;
        }

        if (!pc.hasSkillEffect(67)) {
            SC_EffectLocation.send(pc, pc.getX(), pc.getY(), 3206);
        }

        final OrimController orim = OrimController.getInstance();

        if (actionId == 68) {
            orim.explain = true;
            Config.seadunshippass = 1;
            return;
        }

        if (actionId == 66 && orim.attackTrap().booleanValue()) {
            handleAttackTrap(orim);
            return;
        }

        if (actionId == 69 && orim.dependTrap().booleanValue()) {
            handleDefenceTrap(orim);
        }
    }

    private static void handleAttackTrap(OrimController orim) {
        final L1PcInstance[] members = orim.getPlayMemberArray();

        if (members == null) {
            return;
        }

        for (int i = 0; i < members.length; i++) {
            final L1PcInstance member = members[i];

            if (member == null) {
                continue;
            }

            final long sound = SC_SkillSound.pack(member.getId(), 2029);

            SC_SkillSound.sendPacked(member, sound);
            SC_SkillSound.broadcastPacked(member, sound);
        }

        final boolean firstShell = (orim.getAtCount() & 1) == 0;
        final int effectX;

        if (firstShell) {
            SC_DoActionGFX.broadcast(orim.getShell1(), 10242);
            effectX = 32789;

        } else {
            SC_DoActionGFX.broadcast(orim.getShell2(), 10242);
            effectX = 32795;
        }

        for (int i = 0; i < members.length; i++) {
            final L1PcInstance member = members[i];

            if (member == null) {
                continue;
            }

            SC_EffectLocation.send(member, effectX, 32817, 8233);
        }

        orim.addScore(50);
        orim.addAtCount();
    }

    private static void handleDefenceTrap(OrimController orim) {

        final L1PcInstance[] members = orim.getPlayMemberArray();

        if (members == null) {
            return;
        }

        for (int i = 0; i < members.length; i++) {
            final L1PcInstance member = members[i];

            if (member == null) {
                continue;
            }

            SC_SkillSound.send(member, member.getId(), 10165);

            final long sound = SC_SkillSound.pack(member.getId(), 2030);

            SC_SkillSound.sendPacked(member, sound);
            SC_SkillSound.broadcastPacked(member, sound);
        }

        orim.addDeCount();
    }
}
