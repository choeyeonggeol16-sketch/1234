/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 */
package l1j.server.server.model;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1EtcItem;

public final class L1ItemDelay {

    private L1ItemDelay() {
    }

    public static void onItemUse(L1PcInstance pc, L1ItemInstance item) {
        if (pc == null || item == null || item.getItem() == null) {
            return;
        }

        final int type2 = item.getItem().getType2();

        if (type2 == 0) {
            final L1EtcItem etc = (L1EtcItem) item.getItem();
            final int delayId = etc.get_delayid();
            final int delayTime = etc.get_delaytime();

            if (delayId > 0 && delayTime > 0) {
                pc.addItemDelay(delayId, delayTime);
            }
            return;
        }

        if (type2 == 1) {
            return;
        }

        if (type2 == 2) {
            final int itemId = item.getItem().getItemId();

            if (itemId == 20077
                    || itemId == 20062
                    || itemId == 120077) {

                if (item.isEquipped() && !pc.isInvisble()) {
                    pc.beginInvisTimer();
                }
            }
        }
    }
}
