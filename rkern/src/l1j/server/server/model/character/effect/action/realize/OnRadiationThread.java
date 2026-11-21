package l1j.server.server.model.character.effect.action.realize;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.character.effect.action.EffectModel;

public final class OnRadiationThread extends OnEffectThread {
    private int x;
    private int y;
    private int range;
    public OnRadiationThread(EffectModel model, L1NpcInstance owner) { super(model, owner); }

    @Override protected void initializePattern() {
        x = owner.getX();
        y = owner.getY();
        range = 0;
    }

    @Override protected long executeStep() {
        final int impact = model.getImpact();
        for (int h = 0; h < 8; h++) {
            final int dx = RADIATION[h][0];
            final int dy = RADIATION[h][1];
            applyPoint(x + dx * (1 + range), y + dy * (1 + range), impact);
        }
        range += impact;
        return range < model.getRange() ? model.getDelay() : -1L;
    }
}
