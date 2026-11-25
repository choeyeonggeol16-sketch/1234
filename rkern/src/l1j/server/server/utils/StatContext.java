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
        
        var ability = pc;
        
        int[] currentStats = {
            ability.GetStatStr() + ability.GetAddedStatStr(),
            ability.GetStatInt() + ability.GetAddedStatInt(),
            ability.GetStatWis() + ability.GetAddedStatWis(),
            ability.GetStatDex() + ability.GetAddedStatDex(),
            ability.GetStatCon() + ability.GetAddedStatCon(),
            ability.GetStatCha() + ability.GetAddedStatCha()
        };

        this.targetType = 0;
        for (int i = 0; i < stats.length; i++) {
            if (this.stats[i] == -1) {
                this.stats[i] = currentStats[i];
            } else if (this.targetType == 0 && this.stats[i] != currentStats[i]) {
                this.targetType = i + 1;
            }
        }

        if (pc.getReturnStat() != 0) {
            ability.SetStatStr((byte) (this.stats[0] - ability.GetAddedStatStr()));
            ability.SetStatInt((byte) (this.stats[1] - ability.GetAddedStatInt()));
            ability.SetStatWis((byte) (this.stats[2] - ability.GetAddedStatWis()));
            ability.SetStatDex((byte) (this.stats[3] - ability.GetAddedStatDex()));
            ability.SetStatCon((byte) (this.stats[4] - ability.GetAddedStatCon()));
            ability.SetStatCha((byte) (this.stats[5] - ability.GetAddedStatCha()));
            return true;
        }

        return this.targetType > 0;
    }
    
    public int[] getStats() { 
        return stats.clone(); 
    }
}