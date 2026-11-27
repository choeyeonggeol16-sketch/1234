package l1j.server.server.model.world.transport;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.CGameTime;

public final class L1NeigeShip {
    private static final int BOARDING_DURATION = 1500;
    private static final int[] BOARDING_STARTS = {
        -83970, -78570, -73170, -67770, -62370, -56982, -51576, -46176,
        -40776, -35376, -29976, -24576, -19176, -13776, -8376, -2976
    };

    private L1NeigeShip() {}

    public static void join(L1PcInstance pc) {
        if (pc.getInventory().HaveItemByClassId(40303, 1) && isBoardingTime())
            pc.start_teleport(32731, 32797, 447, 4, 169, false, false);
    }

    public static void out(L1PcInstance pc) {
        if (isBoardingTime())
            pc.start_teleport(32751, 32873, 445, 7, 169, false, false);
    }

    private static boolean isBoardingTime() {
        int time = CGameTime.getSecondOfDay() - 86_400;
        for (int start : BOARDING_STARTS) {
            if (time >= start && time < start + BOARDING_DURATION) return true;
        }
        return false;
    }
}
