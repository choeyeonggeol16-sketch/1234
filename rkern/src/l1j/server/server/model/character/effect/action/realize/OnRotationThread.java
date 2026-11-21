package l1j.server.server.model.character.effect.action.realize;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.character.effect.action.EffectModel;

public final class OnRotationThread extends OnEffectThread {
    private int range;
    private int outer;
    private int inner;
    private int x0, y0, x1, y1, x2, y2, x3, y3;
    public OnRotationThread(EffectModel model, L1NpcInstance owner) { super(model, owner); }

    @Override protected void initializePattern() {
        range = model.getRange();
        outer = 0;
        inner = 0;
        resetPoints();
    }

    private void resetPoints() {
        final int half = range / 2;
        final int ox = owner.getX();
        final int oy = owner.getY();
        x0 = ox - half; y0 = oy - half;
        x1 = ox + half; y1 = oy - half;
        x2 = ox + half; y2 = oy + half;
        x3 = ox - half; y3 = oy + half;
    }

    @Override protected long executeStep() {
        final int impact = model.getImpact();
        applyPoint(x0, y0, impact);
        applyPoint(x1, y1, impact);
        applyPoint(x2, y2, impact);
        applyPoint(x3, y3, impact);
        x0 += 2; y1 += 2; x2 -= 2; y3 -= 2;
        inner += 2;
        if (inner >= range) {
            outer += 2;
            range -= 2;
            inner = 0;
            resetPoints();
        }
        return outer < model.getRange() / 2 ? model.getDelay() : -1L;
    }
}
