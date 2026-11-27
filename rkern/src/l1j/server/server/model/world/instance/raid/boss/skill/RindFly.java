package l1j.server.server.model.world.instance.raid.boss.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.TileOccupancy.TileOccupancyNpc;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.model.world.instance.raid.loader.RaidLoadManager;

public class RindFly extends RaidBossSkill{
    @Override
    public String toString(){
        return RindFly.class.getName();
    }

    @Override
    public void action(L1NpcInstance own, ArrayList<L1PcInstance> pcs) {
        if(own == null || pcs == null || pcs.size() <= 0 || own.isParalyzed())
            return;

        if(!own.tryBeginCast())
            return;

        L1PcInstance 	pc		= null;
        int 			size 	= pcs.size();

        try{
            own.setParalyzed(true);
            if(_actid >= 0){
                own.allTargetClear();
                own.setHiddenStatus(L1MonsterInstance.HIDDEN_STATUS_FLY);
                SC_DoActionGFX.broadcast(own, getSafeActId());
                own.setStatus(_actid);
                SC_WorldPutObject.broadcastNpc(own);
            }

            Thread.sleep(
                    ThreadLocalRandom.current().nextInt(
                            (int) (RaidLoadManager.MRS_BS_RINDFLY_MAX
                                    - RaidLoadManager.MRS_BS_RINDFLY_MIN))
                            + RaidLoadManager.MRS_BS_RINDFLY_MIN);

            if (!land(own, pcs)) return;
            SC_WorldPutObject.broadcastNpc(own);
            own.setStatus(0);
            SC_WorldPutObject.broadcastNpc(own);

            if((_type & BSRTYPE_ONCE) > 0){
                pc = pcs.get(ThreadLocalRandom.current().nextInt(size));
                if(isSkillSet(own, pc))
                    processAction(own, pc);
            }else{
                for(int i=0; i<size; i++){
                    pc = pcs.get(i);
                    if(!isSkillSet(own, pc))
                        continue;

                    processAction(own, pc);
                }
            }
            sleepAction(own, _actid);
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            own.setParalyzed(false);
            own.endCast();
        }
    }

    @Override
    public void actionNoDelay(L1NpcInstance own, ArrayList<L1PcInstance> pcs) {
        if(own == null || pcs == null || pcs.size() <= 0)
            return;

        if(!own.tryBeginCast())
            return;

        L1PcInstance 	pc		= null;
        int 			size 	= pcs.size();

        try {
            if(_actid >= 0){
                own.allTargetClear();
                own.setHiddenStatus(L1MonsterInstance.HIDDEN_STATUS_FLY);
                SC_DoActionGFX.broadcast(own, getSafeActId());
                own.setStatus(_actid);
                SC_WorldPutObject.broadcastNpc(own);
            }

            Thread.sleep(
                    ThreadLocalRandom.current().nextInt(
                            (int) (RaidLoadManager.MRS_BS_RINDFLY_MAX
                                    - RaidLoadManager.MRS_BS_RINDFLY_MIN))
                            + RaidLoadManager.MRS_BS_RINDFLY_MIN);

            if (!land(own, pcs)) return;
            SC_WorldPutObject.broadcastNpc(own);
            own.setStatus(0);
            SC_WorldPutObject.broadcastNpc(own);

            if((_type & BSRTYPE_ONCE) > 0){
                pc = pcs.get(ThreadLocalRandom.current().nextInt(size));
                if(isSkillSet(own, pc))
                    processAction(own, pc);
            }else{
                for(int i=0; i<size; i++){
                    pc = pcs.get(i);
                    if(!isSkillSet(own, pc))
                        continue;

                    processAction(own, pc);
                }
            }
            sleepAction(own, _actid);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            own.endCast();
        }
    }

    static boolean land(L1NpcInstance boss, List<L1PcInstance> players) {
        final CWorld world = CWorld.getInstance();
        final int radius = Config.PC_RECOGNIZE_RANGE;
        final List<L1PcInstance> previous = world.borrowVisiblePlayerFast(boss, radius < 0 ? 22 : radius);
        try {
            if (!landAtTarget(boss, players)) return false;
            for (L1PcInstance viewer : previous) {
                if (viewer.knownsObject(boss) && !viewer.canSeeObject(boss))
                    viewer.getCreatureController().ReconcileSight(boss, false);
            }
            return true;
        } finally {
            world.releaseVisiblePlayerFast(previous);
        }
    }

    private static boolean landAtTarget(L1NpcInstance boss, List<L1PcInstance> players) {
        final CWorld world = CWorld.getInstance();
        final TileOccupancyNpc occupancy = TileOccupancyNpc.getInstance();
        synchronized (boss) {
            if (boss.isDead() || boss.isDestroyed() || world.findObject(boss.getId()) != boss) return false;
            final L1Map terrain = boss.getMap();
            final short mapId = boss.getMapId();
            if (terrain == null || boss.getClusterMapId() != mapId || L1WorldMap.getInstance().findMap(mapId) != terrain)
                return false;
            final int oldX = boss.getX(), oldY = boss.getY();
            // A flying NPC is ignored by collision readers. Reveal it before claiming a tile.
            // The two landing packets still serialize the flight action, then the normal action.
            final int previousHidden = boss.getHiddenStatus();
            boss.setHiddenStatus(L1MonsterInstance.HIDDEN_STATUS_NONE);
            for (int attempt = 0; attempt < 51; attempt++) {
                if (players.isEmpty()) break;
                final L1PcInstance player = players.get(ThreadLocalRandom.current().nextInt(players.size()));
                if (player == null || player.getMap() != terrain) continue;
                final int x = player.getX(), y = player.getY();
                if (!occupancy.claimLandingTile(boss, player, terrain, x, y)) continue;
                if (oldX == x && oldY == y) return true;
                boolean moved = false;
                try {
                    moved = world.relocateVisibleObject(boss, x, y, terrain);
                    if (moved) boss.ResetPassingPos(x, y, mapId);
                    return moved;
                } finally {
                    // The prior tile stays claimed until the world accepts the move.
                    if (!moved) boss.setHiddenStatus(previousHidden);
                    occupancy.releaseNow(mapId, moved ? oldX : x, moved ? oldY : y, boss.getId());
                }
            }
            // An airborne boss may have lost its claim to the original tile.
            if (occupancy.tryOccupyNow(mapId, oldX, oldY, boss.getId())) return true;
            boss.setHiddenStatus(previousHidden);
            return false;
        }
    }

    private void processAction(L1NpcInstance own, L1PcInstance pc){
        pc.receiveDamage(own, calcDamage(own, pc));
        SC_DoActionGFX.send(pc.getNetConnection(), pc.getId(), ActionCodes.ACTION_Damage);
        SC_DoActionGFX.broadcast(pc, ActionCodes.ACTION_Damage);
    }
}
