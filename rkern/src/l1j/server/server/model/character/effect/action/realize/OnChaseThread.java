package l1j.server.server.model.character.effect.action.realize;

import l1j.server.server.shared.GameRandom;


import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.effect.action.EffectModel;

public final class OnChaseThread extends OnEffectThread {
    private L1PcInstance target;
    private int count;
    private boolean initialDelayElapsed;
    public OnChaseThread(EffectModel model, L1NpcInstance owner) { super(model, owner); }

    @Override protected void initializePattern() {
        if (players == null || players.isEmpty()) return;
        for (int i = 0; i < 10; i++) {
            final L1PcInstance pc = players.get(GameRandom.nextInt(players.size()));
            if (pc != null && !pc.isDead() && pc.getMapId() == owner.getMapId()) {
                target = pc;
                return;
            }
        }
    }

    @Override protected long executeStep() {
        if (target == null || target.isDead() || target.getMapId() != owner.getMapId()) return -1L;
        // The original first chase hit happened after the first delay.
        if (!initialDelayElapsed) {
            initialDelayElapsed = true;
            return model.getDelay();
        }
        applyPoint(target.getX(), target.getY(), model.getImpact());
        return ++count < model.getRange() ? model.getDelay() : -1L;
    }
}
