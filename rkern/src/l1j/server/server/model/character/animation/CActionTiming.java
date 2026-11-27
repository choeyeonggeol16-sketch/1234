package l1j.server.server.model.character.animation;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.server.shared.frame.FrameElement;

/** Precomputed, unmodified SPR timing in milliseconds for one action. */
public final class CActionTiming {
    private static final CActionTiming[] DEFAULTS = defaultTimings();

    private final int actionId;
    private final double frameCount;
    private final double baseMillis;
    private final double[] levelMillis;

    private CActionTiming(int actionId, double frameCount, double baseMillis, double[] levelMillis) {
        this.actionId = actionId;
        this.frameCount = frameCount;
        this.baseMillis = baseMillis;
        this.levelMillis = levelMillis;
    }

    private static CActionTiming[] defaultTimings() {
        EActionCodes[] codes = EActionCodes.values();
        CActionTiming[] timings = new CActionTiming[codes.length];
        for (EActionCodes code : codes) {
            timings[code.ordinal()] = fromRate(code.toInt(), code.getAmount(), 24.0);
        }
        return timings;
    }

    static CActionTiming defaultFor(EActionCodes code) {
        return DEFAULTS[code.ordinal()];
    }

    public static CActionTiming read(ResultSet row) throws SQLException {
        return fromRate(row.getInt("act_id"), row.getDouble("framecount"), row.getDouble("framerate"));
    }

    static CActionTiming fromRate(int actionId, double frames, double fps) {
        return new CActionTiming(actionId, frames, frames * FrameElement.calcRps(fps), null);
    }

    CActionTiming withLevelRates(Double[] rates) {
        double[] times = new double[rates.length];
        for (int i = 0; i < rates.length; i++) {
            times[i] = rates[i] == null ? baseMillis : frameCount * rates[i];
        }
        return new CActionTiming(actionId, frameCount, baseMillis, times);
    }

    int actionId() {
        return actionId;
    }

    double baseMillis() {
        return baseMillis;
    }

    boolean usesLevelRates() {
        return levelMillis != null;
    }

    double baseMillis(int levelIndex) {
        return levelMillis == null || levelIndex < 0 || levelIndex >= levelMillis.length
                ? baseMillis : levelMillis[levelIndex];
    }
}
