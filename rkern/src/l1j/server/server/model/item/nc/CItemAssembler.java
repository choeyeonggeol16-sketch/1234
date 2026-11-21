package l1j.server.server.model.item.nc;

import l1j.server.MJTemplate.ItemPresentator.ItemPresentatorFactory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

/** Packet-independent NC CItemAssembler role; L1 opcodes remain unchanged. */
public final class CItemAssembler {
    private CItemAssembler() {}

    public static byte[] AssembleImpl(L1ItemInstance item) {
        try {
            final byte[] custom = ItemPresentatorFactory.do_presentation(item);
            if (custom != null) return custom;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return CItem.AssembleItem(item);
    }

    public static byte[] AssembleBriefImpl(L1ItemInstance item) {
        try {
            final byte[] custom = ItemPresentatorFactory.do_presentation(item);
            if (custom != null) return custom;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return CItem.AssembleBriefItem(item);
    }

    public static byte[] AssembleItemForPc(L1ItemInstance item, L1PcInstance pc) {
        return CItem.AssembleItemForPc(item, pc);
    }
}
