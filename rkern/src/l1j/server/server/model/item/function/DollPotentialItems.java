package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

final class DollPotentialItems {
    private DollPotentialItems() {}

    static void apply(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance doll) {
        // Both objects must still be the same live inventory instances selected
        // by the client. A missing target must not crash the item handler.
        if (pc == null || scroll == null || doll == null || scroll == doll
                || scroll.getItem() == null || doll.getItem() == null
                || pc.getInventory().GetByServerID(scroll.getId()) != scroll
                || pc.getInventory().GetByServerID(doll.getId()) != doll) {
            if (pc != null) SC_ServerMessage.send(pc, 79);
            return;
        }
        switch (doll.getItem().getItemId()) {
            case 950000:
            case 950001:
            case 950002:
            case 950003:
            case 950004:
            case 950005:
            case 950006:
            case 950007:
            case 950008:
            case 950009:
            case 950010:
            case 950011:
            case 950012:
            case 950013:
            case 950014:
            case 950015:
            case 950016:
            case 950017:
            case 950018:
            case 950019:
            case 510219:
            case 510220:
            case 510221:
            case 510222:
            case 742:
            case 743:
            case 744:
            case 745:
            case 746:
            case 755:
            case 756:
            case 757:
            case 3000086:
            case 3000087:
            case 3000088:
            case 3000351:
            case 3000352:
            case 4100007:
            case 4100008:
            case 4100009:
            case 4100010:
            case 4200010:
            case 4200020:
            case 4200030:
            case 4200040:
            case 4200050:
            case 4200060:
            case 447016:
            case 447017:
            case 410173:
            case 4200051:
                DollPotentialTransactions.useDollPotential(pc, scroll, doll);
                break;
            default:
                break;
        }
    }
}
