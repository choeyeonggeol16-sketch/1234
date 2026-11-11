package l1j.server.server.model.Instance;

import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Magic;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.templates.L1Npc;

public class L1EffectInstance extends L1NpcInstance {

    private static final long serialVersionUID = 1L;
    private static final long FW_DAMAGE_INTERVAL = 1000L;
    private static final int TIMER_FIRE_WALL = 81157;
    private static final int TIMER_FIRE_WALL_GENERATION = 1;

    public L1EffectInstance(L1Npc template) {
        super(template);
    }

    public void startDamageTimer() {
        if (!_destroyed && getNpcId() == 81157) {
            CIOThreadCallback.getInstance().scheduleNpcTimer(
                    this, TIMER_FIRE_WALL, TIMER_FIRE_WALL_GENERATION, 1L);
        }
    }

    @Override
    public void TimerExpired(int timerId, int generation) {
        if (timerId != TIMER_FIRE_WALL) {
            super.TimerExpired(timerId, generation);
            return;
        }
        if (generation != TIMER_FIRE_WALL_GENERATION || _destroyed) {
            return;
        }

        try {
            applyFireWallDamage();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (!_destroyed) {
                CIOThreadCallback.getInstance().scheduleNpcTimer(
                        this, TIMER_FIRE_WALL, TIMER_FIRE_WALL_GENERATION,
                        FW_DAMAGE_INTERVAL);
            }
        }
    }

    private void applyFireWallDamage() {
        for (L1Object obj : L1World.getInstance().getClusterGrid().queryNearby(this, 0)) {
            if (_destroyed) {
                return;
            }
            if (obj == null
                    || obj.getMapId() != getMapId()
                    || obj.getX() != getX()
                    || obj.getY() != getY()) {
                continue;
            }

            if (obj instanceof L1PcInstance) {
                L1PcInstance pc = (L1PcInstance) obj;
                if (pc.isDead()) {
                    continue;
                }

                if (pc.getZoneType() == 1) {
                    int castleId = L1CastleLocation.getCastleIdByArea(pc);
                    if (castleId <= 0 || !MJCastleWarBusiness.getInstance().isNowWar(castleId)) {
                        continue;
                    }
                }

                int damage = new L1Magic(this, pc).calcPcFireWallDamage();
                if (damage == 0) {
                    continue;
                }

                SC_DoActionGFX.send(
                        pc.getNetConnection(), pc.getId(), ActionCodes.ACTION_Damage);
                SC_DoActionGFX.broadcast(pc, ActionCodes.ACTION_Damage);
                pc.receiveDamage(this, damage, true);

            } else if (obj instanceof L1MonsterInstance) {
                L1MonsterInstance mob = (L1MonsterInstance) obj;
                if (mob.isDead() || mob.getHiddenStatus() > 0) {
                    continue;
                }

                int damage = new L1Magic(this, mob).calcNpcFireWallDamage();
                if (damage == 0) {
                    continue;
                }

                SC_DoActionGFX.broadcast(mob, ActionCodes.ACTION_Damage);
                mob.receiveDamage(this, damage);
            }
        }
    }

    @Override
    public void onAction(L1PcInstance pc) {
    }

    @Override
    public void deleteMe() {
    	_destroyed = true;
		if (getInventory() != null) {
			getInventory().clearItems();
		}
		allTargetClear();
		L1World.getInstance().removeVisibleObject(this);
		L1World.getInstance().removeObject(this);
		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayerFast(this)) {
			if (pc == null) continue;
			pc.removeKnownObject(this);
			SC_RemoveObject.send(pc.getNetConnection(), this);
		}
		removeAllKnownObjects();
        _master = null;
    }
}
