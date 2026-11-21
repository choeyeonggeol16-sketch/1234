package l1j.server.server.model.character.equip;

import l1j.server.server.model.Instance.L1PcInstance;

public final class EquipResult {
    public static final EquipResult OK = new EquipResult(true, null);
    private static final EquipResult NO_MATCH = new EquipResult(false, null);

    public final boolean ok;
    public final EquipFail fail;

    private EquipResult(boolean ok, EquipFail fail) {
        this.ok = ok;
        this.fail = fail;
    }

    public static EquipResult fail(EquipFail fail) {
        return fail == null ? NO_MATCH : new EquipResult(false, fail);
    }

    public void applyFail(L1PcInstance pc) {
        if (fail != null) fail.apply(pc);
    }
}
