package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;

/** Existing item-domain API for removing all doll potential effects. */
public final class DollEffectItems {
    private DollEffectItems() {}

    public static void RemoveDollPotential(L1PcInstance pc) {
        DollPotentialRemoval.remove(pc);
        SC_OwnCharAttrDef.send(pc);
    }
}
