package l1j.server.server.model.world.instance.raid.boss.skill;

import java.util.ArrayList;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.shared.GameRandom;

public class AttackSkill extends RaidBossSkill {
    @Override
    public String toString() {
        return AttackSkill.class.getName();
    }

    @Override
    public void action(L1NpcInstance owner, ArrayList<L1PcInstance> targets) {
        cast(owner, targets, true);
    }

    @Override
    public void actionNoDelay(L1NpcInstance owner, ArrayList<L1PcInstance> targets) {
        cast(owner, targets, false);
    }

    private void cast(L1NpcInstance owner, ArrayList<L1PcInstance> targets, boolean paralyze) {
        if (owner == null || targets == null || targets.isEmpty() || (paralyze && owner.isParalyzed())) return;
        if (!owner.tryBeginCast()) return;

        final int size = targets.size();
        try {
            if (paralyze) owner.setParalyzed(true);
            if (_actid >= 0) SC_DoActionGFX.broadcast(owner, getSafeActId());

            if ((_type & BSRTYPE_ONCE) > 0) {
                attackTarget(owner, targets.get(GameRandom.nextInt(size)));
            } else {
                for (int i = 0; i < size; i++) {
                    attackTarget(owner, targets.get(i));
                }
            }
            sleepAction(owner, _actid);
        } catch (Exception failure) {
            failure.printStackTrace();
        } finally {
            if (paralyze) owner.setParalyzed(false);
            owner.endCast();
        }
    }

    private void attackTarget(L1NpcInstance owner, L1PcInstance target) {
        if (!isSkillSet(owner, target)) return;
        target.receiveDamage(owner, calcDamage(owner, target));
        SC_DoActionGFX.send(target.getNetConnection(), target.getId(), ActionCodes.ACTION_Damage);
        SC_DoActionGFX.broadcast(target, ActionCodes.ACTION_Damage);
    }
}
