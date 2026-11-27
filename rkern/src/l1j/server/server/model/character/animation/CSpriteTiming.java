package l1j.server.server.model.character.animation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;

/** Shared SPR metadata and base timing for one sprite in a loaded table. */
public final class CSpriteTiming {
    private static final int MAX_ACTIONS = 256;
    private static final CActionTiming[] NO_ACTIONS = new CActionTiming[0];

    private final int spriteId;
    private final int width;
    private final int height;
    private final int numOfAction;
    private final int[] levelIndices;
    private volatile CActionTiming[] actions;

    public static CSpriteTiming read(ResultSet row, int[] levelIndices, List<CActionTiming> entries) throws SQLException {
        return new CSpriteTiming(row.getInt("spr_id"), row.getInt("width"), row.getInt("height"),
                row.getInt("action_count"), levelIndices, entries);
    }

    CSpriteTiming(int spriteId, int width, int height, int numOfAction,
            int[] levelIndices, List<CActionTiming> entries) {
        this.spriteId = spriteId;
        this.width = width;
        this.height = height;
        this.numOfAction = numOfAction;
        this.levelIndices = levelIndices;

        int length = 0;
        if (entries != null) {
            for (CActionTiming entry : entries) {
                int id = entry.actionId();
                if (id >= 0 && id < MAX_ACTIONS) length = Math.max(length, id + 1);
            }
        }
        actions = length == 0 ? NO_ACTIONS : new CActionTiming[length];
        if (length != 0) {
            for (CActionTiming entry : entries) {
                int id = entry.actionId();
                if (id >= 0 && id < length) actions[id] = entry;
            }
        }
    }

    public int getSpriteId() { return spriteId; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getNumOfAction() { return numOfAction; }

    public boolean belongsTo(int[] generation) {
        return levelIndices == generation;
    }

    private CActionTiming get(int actionId) {
        CActionTiming[] table = actions;
        return actionId >= 0 && actionId < table.length ? table[actionId] : null;
    }

    private void put(CActionTiming action) {
        int index = action.actionId();
        CActionTiming[] table = actions;
        CActionTiming[] updated = Arrays.copyOf(table, Math.max(table.length, index + 1));
        updated[index] = action;
        actions = updated;
    }

    private CActionTiming timing(EActionCodes code) {
        CActionTiming action = get(code.toInt());
        return action != null ? action : missingTiming(code);
    }

    private synchronized CActionTiming missingTiming(EActionCodes code) {
        CActionTiming action = get(code.toInt());
        if (action == null) {
            // Numeric aliases retain their existing first-use fallback.
            action = CActionTiming.defaultFor(code);
            put(action);
        }
        return action;
    }

    public synchronized void registerLevelRates(int actionId, Double[] rates) {
        CActionTiming action = get(actionId);
        if (action == null) {
            EActionCodes code = EActionCodes.fromInt(actionId);
            if (code == null) return;
            action = CActionTiming.defaultFor(code);
        }
        put(action.withLevelRates(rates));
    }

    public double getInterval(L1Character owner, EActionCodes code) {
        return code.decoration(owner, timing(code).baseMillis());
    }

    public double getInterval(L1PcInstance pc, EActionCodes code) {
        EActionCodes effective = forPc(code);
        CActionTiming action = timing(effective);
        double baseMillis = action.usesLevelRates()
                ? action.baseMillis(levelIndex(levelIndices, pc.getLevel(), spriteId))
                : action.baseMillis();
        return effective.decorationForPc(pc, baseMillis);
    }

    private EActionCodes forPc(EActionCodes code) {
        if (spriteId == 12015 || spriteId == 5641 || spriteId == 11685 || spriteId == 14491) {
            if (code.is_walk()) return EActionCodes.walk;
            if (code.is_attack()) return EActionCodes.attack;
            if (code.is_damage()) return EActionCodes.damage;
            if (code.is_breath()) return EActionCodes.breath;
        }
        return code;
    }

    public static int levelIndex(int[] levelIndices, int level, int spriteId) {
        int effective = CSpriteLevelBoundary.effectiveLevel(spriteId, level);
        return levelIndices[Math.clamp(effective, 0, levelIndices.length - 1)];
    }
}
