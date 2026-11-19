package l1j.server.server.utils;

import java.util.Arrays;
import l1j.server.server.model.Ability;
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
        
        Ability ability = pc.getAbility();
        
        int[] currentStats = {
            ability.getStr() + ability.getAddedStr(),
            ability.getInt() + ability.getAddedInt(),
            ability.getWis() + ability.getAddedWis(),
            ability.getDex() + ability.getAddedDex(),
            ability.getCon() + ability.getAddedCon(),
            ability.getCha() + ability.getAddedCha()
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
            ability.setStr((byte) (this.stats[0] - ability.getAddedStr()));
            ability.setInt((byte) (this.stats[1] - ability.getAddedInt()));
            ability.setWis((byte) (this.stats[2] - ability.getAddedWis()));
            ability.setDex((byte) (this.stats[3] - ability.getAddedDex()));
            ability.setCon((byte) (this.stats[4] - ability.getAddedCon()));
            ability.setCha((byte) (this.stats[5] - ability.getAddedCha()));
            return true;
        }

        return this.targetType > 0;
    }
    
    public int[] getStats() { 
        return stats.clone(); 
    }
}