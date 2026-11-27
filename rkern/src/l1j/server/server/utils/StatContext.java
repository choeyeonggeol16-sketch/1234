package l1j.server.server.utils;

import java.util.Arrays;
import l1j.server.server.model.Instance.L1PcInstance;

public class StatContext {
    private final int classType;
    private final int statValue;
    private int targetType;
    private final int[] stats = new int[6]; // [0]STR, [1]INT, [2]WIS, [3]DEX, [4]CON, [5]CHA

    public StatContext(int classType, int statValue) {
        this.classType = classType;
        this.statValue = statValue;
        Arrays.fill(this.stats, -1);
    }

    public int getClassType() { return classType; }
    public int getStatValue() { return statValue; }
    public int getTargetType() { return targetType; }

    public void setStat(int index, int value) {
        if (index >= 0 && index < stats.length) {
            this.stats[index] = value;
        }
    }

    public boolean resolve(L1PcInstance pc) {
        if (pc == null) return false;
        
        final boolean redistributing = pc.getReturnStat() != 0;
        final var stage = redistributing ? pc.rst : null;
        final int[] currentStats = stage == null ? new int[] {
            pc.GetTotalStatStr(), pc.GetTotalStatInt(), pc.GetTotalStatWis(),
            pc.GetTotalStatDex(), pc.GetTotalStatCon(), pc.GetTotalStatCha()
        } : new int[] {
            effective(stage.basestr + stage.str + pc.GetAddedStatStr()),
            effective(stage.baseint + stage.Int + pc.GetAddedStatInt()),
            effective(stage.basewis + stage.wis + pc.GetAddedStatWis()),
            effective(stage.basedex + stage.dex + pc.GetAddedStatDex()),
            effective(stage.basecon + stage.con + pc.GetAddedStatCon()),
            effective(stage.basecha + stage.cha + pc.GetAddedStatCha())
        };

        this.targetType = 0;
        for (int i = 0; i < stats.length; i++) {
            if (stats[i] == -1) {
                stats[i] = currentStats[i];
            } else {
                stats[i] = effective(stats[i]);
                if (targetType == 0 && stats[i] != currentStats[i]) targetType = i + 1;
            }
        }

        return redistributing || targetType > 0;
    }
    
    private static int effective(int stat) { return Math.clamp(stat, 0, 127); }

    public int[] getStats() { 
        return stats.clone(); 
    }
}