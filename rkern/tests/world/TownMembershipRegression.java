package l1j.server.server.model;

import java.math.BigDecimal;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.world.town.CResidenceSettlement;

public final class TownMembershipRegression {
    public static void main(String[] args) throws Exception {
        var maps = WorldVisibilityRegression.class.getDeclaredMethod("installMapMetadata");
        maps.setAccessible(true);
        maps.invoke(null);
        var period = CResidenceSettlement.class.getDeclaredField("period");
        period.setAccessible(true);
        long before = CResidenceSettlement.period();

        var users = new CWorldUsers();
        var resident = new L1PcInstance();
        resident.setName("TownResidentTest");
        resident.restoreHomeTown(4, 22, before);
        users.register(resident);
        check(users.residents().contains(resident), "loaded resident is indexed");

        var queued = resident.captureHomeTown();
        resident.addContribution(Integer.MAX_VALUE);
        check(resident.getContribution() == Integer.MAX_VALUE, "contribution saturates");
        resident.setHomeTownId(-1);
        users.townChanged(resident);
        check(users.residents().contains(resident), "resignation remains indexed until settlement");
        var resignation = resident.captureHomeTown();

        try {
            period.setLong(null, before + 1);
            check(queued.townIdFor(before + 1) == 4 && queued.contributionFor(before + 1) == 0,
                    "queued save keeps membership and cannot restore settled points");
            check(resignation.townIdFor(before + 1) == 0 && resident.getHomeTownId() == 0,
                    "queued resignation expires");
            users.townChanged(resident);
            check(users.residents().isEmpty(), "expired resignation leaves the index");

            resident.setHomeTownId(4);
            resident.addContribution(7);
            check(resident.getContribution() == 7, "new points belong to the new period");
            users.townChanged(resident);
            check(users.residents().contains(resident), "new registration enters the index");

            var replacement = new L1PcInstance();
            replacement.setName(resident.getName());
            users.register(replacement);
            users.townChanged(resident);
            check(users.residents().isEmpty(), "replaced character cannot restore its index entry");
            replacement.setHomeTownId(3);
            users.townChanged(replacement);
            users.remove(replacement);
            check(users.residents().isEmpty(), "logout removes the resident reference");

            var rate = CResidenceSettlement.class.getDeclaredMethod("payoutRate", long.class, long.class);
            rate.setAccessible(true);
            check(new BigDecimal("0.66").equals(rate.invoke(null, 2L, 3L))
                    && BigDecimal.ZERO.equals(rate.invoke(null, 2L, 0L)), "payout rate precision");
        } finally {
            period.setLong(null, before);
        }
        System.out.println("PASS town period fence, contribution overflow, resident index cleanup and payout rate");
    }

    private static void check(boolean valid, String reason) {
        if (!valid) throw new AssertionError(reason);
    }
}
