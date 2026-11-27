package l1j.server.server.model.item.function;

import java.util.concurrent.RejectedExecutionException;
import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.effect.poison.DamagePoison;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.thread.CIOThreadCallback.WheelTask;
import l1j.server.server.shared.GameRandom;

/**
 * Legacy combat effects grouped by responsibility. 
 */
final class CombatSupportItems {
    
    private CombatSupportItems() {
    }
    
    public static int targetDirection(L1PcInstance pc, int tx, int ty) {
        float dis_x = Math.abs(pc.getX() - tx);
        float dis_y = Math.abs(pc.getY() - ty);
        float dis = Math.max(dis_x, dis_y);
        if (dis == 0) return pc.getHeading();
        int avg_x = (int)Math.floor((dis_x / dis) + 0.59F);
        int avg_y = (int)Math.floor((dis_y / dis) + 0.59F);
        int dir_x = 0;
        int dir_y = 0;
        if (pc.getX() < tx) dir_x = 1;
        if (pc.getX() > tx) dir_x = -1;
        if (pc.getY() < ty) dir_y = 1;
        if (pc.getY() > ty) dir_y = -1;
        if (avg_x == 0) dir_x = 0;
        if (avg_y == 0) dir_y = 0;
        if (dir_x == 1 && dir_y == -1) return 1;
        if (dir_x == 1 && dir_y == 0) return 2;
        if (dir_x == 1 && dir_y == 1) return 3;
        if (dir_x == 0 && dir_y == 1) return 4;
        if (dir_x == -1 && dir_y == 1) return 5;
        if (dir_x == -1 && dir_y == 0) return 6;
        if (dir_x == -1 && dir_y == -1) return 7;
        if (dir_x == 0 && dir_y == -1) return 0;
        return pc.getHeading();
    }
    
    public static void Start(L1PcInstance pc, L1Character target) {
        scheduleEffect(target, () -> {
            if (target.hasSkillEffect(L1SkillId.COUNTER_MAGIC)) return;
            int damage = target.hasSkillEffect(L1SkillId.리버레이션)
                    ? (int) (Config.이터제약 * Config.리버레이션절감) : randomSecondDamage();
            DamagePoison.doInfection(pc, target, 1000, damage, true, 1);
            SC_DoActionGFX.broadcast(pc, ActionCodes.ACTION_Damage);
        }, Config.이터제약);
    }

    public static void Start1(L1PcInstance pc, L1NpcInstance target) {
        scheduleEffect(target, () -> {
            if (!target.hasSkillEffect(L1SkillId.COUNTER_MAGIC))
                DamagePoison.doInfection(pc, target, 1000, randomSecondDamage(), true, 1);
        }, Config.이터제약);
    }

    private static int randomSecondDamage() {
        return GameRandom.nextIntOrFixed(Config.이터이차최소, Config.이터이차최대);
    }

    public static void 액션(L1PcInstance pc, L1Character target) {
        scheduleEffect(target, () -> {
            SC_DoActionGFX.broadcast(target, ActionCodes.ACTION_Damage);
            if (target instanceof L1PcInstance player)
                SC_DoActionGFX.send(player.getNetConnection(), player.getId(), ActionCodes.ACTION_Damage);
        }, 500L);
    }

    public static void 액션1(L1PcInstance pc, L1NpcInstance target) {
        scheduleEffect(target, () -> SC_DoActionGFX.broadcast(target, ActionCodes.ACTION_Damage), 500L);
    }

    private static void scheduleEffect(L1Character target, Runnable action, long delayMs) {
        final CIOThreadCallback callback = CIOThreadCallback.getInstance();
        final CombatSupportEffect effect = new CombatSupportEffect(target, action);
        if (!callback.scheduleCharacterEffectTick(target, effect, delayMs))
            throw new RejectedExecutionException("Combat support timer owner unavailable");
    }

    private static final class CombatSupportEffect implements WheelTask {
        private final L1Character target;
        private final Runnable action;
        private final int sessionStamp;

        CombatSupportEffect(L1Character target, Runnable action) {
            this.target = target;
            this.action = action;
            this.sessionStamp = target instanceof L1PcInstance pc ? pc.getSessionStamp() : 0;
        }

        @Override public boolean isCancelled() {
            if (target instanceof L1PcInstance pc)
                return pc.getSessionStamp() != sessionStamp || pc.isLogoutStarted()
                        || pc.getCreatureController() == null;
            return target instanceof L1NpcInstance npc && npc.isDestroyed();
        }

        @Override public void run() {
            final CIOThreadCallback callback = CIOThreadCallback.getInstance();
            if (!isCancelled() && !callback.postCharacterEffectOnOwner(target, this))
                callback.scheduleCharacterEffectTick(target, this, 50L);
        }

        @Override public void runOnController(L1PcInstance pc) { apply(pc); }

        @Override public void runOnNpc(L1NpcInstance npc) { apply(npc); }

        private void apply(L1Character actor) {
            if (target == actor && !isCancelled()) action.run();
        }
    }
}
