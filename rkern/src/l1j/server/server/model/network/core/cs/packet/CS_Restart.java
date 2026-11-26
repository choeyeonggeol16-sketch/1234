package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.ActionCodes;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Getback;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CParty;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.TileOccupancy.TileOccupancy;
import l1j.server.server.model.character.combat.arena.CombatObserver;
import l1j.server.server.model.character.combat.arena.loader.CombatLoadManager;
import l1j.server.server.model.character.experience.amplifier.ExpAmplifierLoader;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_IvenBuffIcon;
import l1j.server.server.model.network.core.sc.packet.SC_NewCreateItem;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.network.core.sc.packet.SC_Weather;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.world.instance.raid.RaidSpace;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_MAP_ID;
import l1j.server.server.shared.EPcStatus;
import l1j.server.server.templates.L1Skills;
public final class CS_Restart {

    private static final int[] REMOVE_IDS = {
        203003, 810006, 810007, 30055, 40747, 30056
    };

    private static final int[] RE_LOAD_N_BUFF_SKILLS = {
        4075, 4076, 4077, 4078, 4079, 4080, 4081, 4082, 4083, 4084, 4085,
        4086, 4087, 4088, 4089, 4090, 4091, 4092, 4093, 4094, 4095
    };

    private static final int[][] TAM_BUFF_DATA = {
        {8265, 4181},
        {8266, 4182},
        {8267, 4183},
        {8268, 5046},
        {8269, 5047}
    };

    private CS_Restart() {
    }

    public static void handle(CUserSocket client) {
        if (client == null) {
            return;
        }

        final L1PcInstance pc = client.getActiveChar();
        if (pc == null) {
            return;
        }

        if (pc.is_combat_field()) {
            final CombatObserver observer =
                    CombatLoadManager.getInstance()
                            .get_current_observer(pc.get_current_combat_id());

            if (observer != null) {
                observer.remove(pc);
            }
        }

        RaidSpace.getInstance().getBackPc(pc);

        pc.setCurrentSprite(pc.getClassId());

        if (!pc.isDead()) {
            return;
        }

        if (pc.isGhost()) {
            pc.endGhost();
        }

        processRestart(pc);

        for (int itemId : REMOVE_IDS) {
            if (pc.getInventory().HaveAnyItemByClassId(itemId)) {
                pc.getInventory().DeleteItemInInventory(
                        itemId,
                        pc.getInventory().GetUnequippedItemAmount(itemId));
            }
        }
    }

    public static void processRestart(L1PcInstance pc) {
        if (pc == null) {
            return;
        }

        pc.InvalidateWarehouseAccess();
        final boolean restoreGroundSight = pc.suspendGroundSight();
        try {
            int[] loc = Getback.GetBack_Restart(pc);

            if (loc == null) {
                loc = Getback.GetBack_Location(pc, true);
            }

            pc.removeAllKnownObjects();
            SC_RemoveObject.broadcast(pc);

            L1World.getInstance().relocateVisibleObject(
                    pc, loc[0], loc[1], loc[2]);

            pc.setCurrentHp(pc.getLevel());
            pc.set_food(39);
            pc.setDead(false);

            reloadNBuff(pc);

            CIOThreadCallback.getInstance().clearPcState(pc);

            pc.set_is_non_action(false);
            pc.set_instance_status(EPcStatus.WORLD);
            pc.set_mark_status(0);
            pc.set_current_combat_id(0);
            pc.set_current_combat_team_id(-1);
            pc.setStatus(ActionCodes.ACTION_Walk);

            TileOccupancy.getInstance().tryOccupyNow(
                    pc.getMapId(),
                    pc.getX(),
                    pc.getY(),
                    pc.getId());

            SC_MAP_ID.sendCreate(pc, pc.getMap().getBaseMapId(), pc.getMap().isUnderwater());

            ExpAmplifierLoader.getInstance().set(pc);

            SC_WorldPutObject.broadcastPc(pc);

            if (!SC_WorldPutObject.put(pc.getNetConnection(), pc))
                throw new IllegalStateException("Restart self object not accepted");

            if (restoreGroundSight) pc.resumeGroundSight();
            pc.onWorldMove(
                    pc.getMapId(),
                    pc.getX(),
                    pc.getY());

            SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));
            SC_Weather.send(pc, L1World.getInstance().getWeather());

            final CParty party = pc.getParty();

            if (party != null) {
                party.refreshPartyMemberStatus(pc);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (restoreGroundSight && (pc.groundSightEpoch() & 1) == 0) {
                final CUserSocket client = pc.getNetConnection();
                if (client != null && client.isConnected()) client.kick();
            }
        }
    }

    private static void reloadNBuff(L1PcInstance pc) {
        if (pc == null || pc.getNetConnection() == null) {
            return;
        }

        for (int skillId : RE_LOAD_N_BUFF_SKILLS) {
            if (!pc.hasSkillEffect(skillId)) {
                continue;
            }

            final L1Skills skillTemplate =
                    SkillsTable.getInstance().getTemplate(skillId);

            if (skillTemplate == null) {
                continue;
            }

            final int remainingSec =
                    pc.getSkillEffectTimeSec(skillId);

            SC_IvenBuffIcon.send(pc, skillId,
                            true,
                            skillTemplate.getSysmsgIdHappen(),
                            remainingSec);
        }

        final int tamCount = pc.tamCount();

        if (tamCount >= 1 && tamCount <= 5) {
            final int tamTimeSec = (int) (pc.tamTime() / 1000);
            final int iconId = TAM_BUFF_DATA[tamCount - 1][0];
            final int msgId = TAM_BUFF_DATA[tamCount - 1][1];

            SC_NewCreateItem.sendBuffOrder(pc, SC_NewCreateItem.버프창, tamTimeSec, iconId, msgId);
        }
    }
}
