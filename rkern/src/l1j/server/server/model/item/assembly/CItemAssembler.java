package l1j.server.server.model.item.assembly;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.presentation.ItemPresentatorFactory;

/** Packet-independent reference server CItemAssembler role; L1 opcodes remain unchanged. */
public final class CItemAssembler {
    private CItemAssembler() {}

    public static byte[] AssembleImpl(L1ItemInstance item) {
        final byte[] custom = assembleCustom(item);
        return custom != null ? custom : CItem.AssembleItem(item);
    }

    public static byte[] AssembleBriefImpl(L1ItemInstance item) {
        final byte[] custom = assembleCustom(item);
        return custom != null ? custom : CItem.AssembleBriefItem(item);
    }

    private static byte[] assembleCustom(L1ItemInstance item) {
        try {
            return ItemPresentatorFactory.do_presentation(item);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static byte[] AssembleItemForPc(L1ItemInstance item, L1PcInstance pc) {
        return CItem.AssembleItemForPc(item, pc);
    }
}
