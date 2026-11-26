package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.item.doll.DollPotentialEffects;
import l1j.server.server.model.item.doll.DollRegistry;

/** Resolves the strongest configured regeneration from type and potential. */
final class DollTickBonuses {
    private DollTickBonuses() {}

    public static int calcMpTick(L1DollInstance doll) {
        if (doll == null) return 0;
        final DollRegistry.Profile profile = DollRegistry.byType(doll.getDollType());
        return Math.max(profile == null ? 0 : profile.mpTick(), DollPotentialEffects.mpTick(doll.getDollLevel()));
    }

    public static int calcHpTick(L1DollInstance doll) {
        if (doll == null) return 0;
        final DollRegistry.Profile profile = DollRegistry.byType(doll.getDollType());
        return Math.max(profile == null ? 0 : profile.hpTick(), DollPotentialEffects.hpTick(doll.getDollLevel()));
    }
}
