package l1j.server.server.model.item.assembly;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public final class CItemAssembler {
    private CItemAssembler() {}

    public static void initialize() {
        EtcItemPresentation.initialize();
    }

    public static String AssembleViewName(L1ItemInstance item, int count) {
        return CItemName.view(item, count);
    }

    public static String AssembleNumberedName(L1ItemInstance item, int count) {
        return CItemName.numbered(item, count);
    }

    public static byte[] AssembleImpl(L1ItemInstance item) {
        return CItem.assemble(item, item._cha, false);
    }

    public static byte[] AssembleBriefImpl(L1ItemInstance item) {
        return item.getItem().getType2() == 0
                ? CItem.assembleBriefItem(item) : CItem.assemble(item, item._cha, true);
    }

    public static byte[] assembleInventoryStatus(L1ItemInstance item) {
        if (item.getItem().getType2() == 0 && EtcItemPresentation.hasBrief(item.getItemId())) {
            return CItem.assembleBriefItem(item);
        }
        return AssembleImpl(item);
    }

    public static byte[] AssembleItemForPc(L1ItemInstance item, L1PcInstance pc) {
        return CItem.assemble(item, pc, true);
    }

    /** Legacy packets carry the tooltip length in a single unsigned byte. */
    public static byte[] AssembleLegacyStatus(L1ItemInstance item, L1PcInstance pc) {
        if (item == null || !item.isIdentified()) return null;
        final byte[] full = CItem.assemble(item, item._cha != null ? item._cha : pc, false);
        if (full.length <= 255) return full;
        if (pc == null) return null;
        final byte[] compact = AssembleItemForPc(item, pc);
        return compact.length <= 255 ? compact : null;
    }
}
