package l1j.server.server.model.item.assembly;

import java.io.IOException;
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
            } else if (type == 0 && compact) {
                CItemConstInfo.AssembleAttribute(item, out);
            } else if (type == 0) {
                CItemConstInfo.AssembleEtcItem(item, out, type, item.getItemId(), template.getGrade());
            }
            if (!compact) appendExpiry(item, out);
        } catch (Exception failure) {
            failure.printStackTrace();
        }
        return out.getBytes();
    }

    static byte[] AssembleBriefItem(L1ItemInstance item) {
		int itemType2 = item.getItem().getType2();

		ItemPresentOutStream os = new ItemPresentOutStream();

		try {
			if (itemType2 == 0) { 
			switch (item.getItem().getItemId()) {
			case 875640331:
			case 875640332:
			case 875640333:
			case 875640334:
			case 875640335:
				os.writeC(39);
				os.writeS("구분: \\fB조우");
				break;
			case 875640336:
			case 875640337:
			case 875640338:
				os.writeC(39);
				os.writeS("구분: \\fF크리스터");
				break;
			case 875640339:
			case 875640340:
			case 875640341:
				os.writeC(39);
				os.writeS("구분: \\fD이실로테");
				break;
			case 875640446:
			case 875640447:
			case 875640448:
				os.writeC(39);
				os.writeS("구분: \\aG아툰");
				break;
				
			}
			}
            appendExpiry(item, os);

		} catch (

		Exception e) {
			e.printStackTrace();
		} finally {
			try {
				os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return os.getBytes();
	
    }

    private static void appendExpiry(L1ItemInstance item, ItemPresentOutStream output) {
        final var endTime = item.getEndTime();
        if (endTime != null && item.isIdentified()) {
            output.writeC(61);
            output.writeD((endTime.getTime() - LEGACY_TIME_ORIGIN_MS) / 1_000L * 6L);
        }
    }
}
