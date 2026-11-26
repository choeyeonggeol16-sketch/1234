package l1j.server.server.model.item.assembly;

import java.util.Date;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.ItemPresentOutStream;

public final class CItem {
    @SuppressWarnings("deprecation")
    private static final long LEGACY_TIME_ORIGIN_MS = Date.parse("January 1, 2017 0:00:00");

    private CItem() {}

    static byte[] assemble(L1ItemInstance item, L1PcInstance owner, boolean compact) {
        final ItemPresentOutStream out = new ItemPresentOutStream();
        final L1Item template = item.getItem();
        try (out) {
            final int type = template.getType2();
            if (type == 1 || type == 2) {
                EquipmentOptionPresentation.write(item, owner, out, compact);
            } else if (type == 0) {
                if (compact) EtcItemPresentation.writeBase(item, out);
                else EtcItemPresentation.writeFull(item, owner, out);
            }
            if (!compact) appendExpiry(item, out);
        } catch (Exception failure) {
            failure.printStackTrace();
        }
        return out.getBytes();
    }

    static byte[] assembleBriefItem(L1ItemInstance item) {
        final ItemPresentOutStream out = new ItemPresentOutStream();
        try (out) {
            if (item.getItem().getType2() == 0) EtcItemPresentation.writeBrief(item, out);
            appendExpiry(item, out);
        } catch (Exception failure) {
            failure.printStackTrace();
        }
        return out.getBytes();
    }

    private static void appendExpiry(L1ItemInstance item, ItemPresentOutStream output) {
        final var endTime = item.getEndTime();
        if (endTime != null && item.isIdentified()) {
            output.writeC(61);
            output.writeD((endTime.getTime() - LEGACY_TIME_ORIGIN_MS) / 1_000L * 6L);
        }
    }
}
