package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

/** ID-to-HTML mapping for item-triggered quest and NPC dialogue, with original keys retained. */
final class QuestDialogueItems {
    private QuestDialogueItems() {}

    static void send(L1PcInstance pc, int itemId) {
        final String html = switch (itemId) {
            case 41146 -> "ei001";
            case 41209 -> "ei002";
            case 41210 -> "ei003";
            case 41211 -> "ei004";
            case 41212 -> "ei005";
            case 41213 -> "ei006";
            case 41214 -> "ei012";
            case 41215 -> "ei010";
            case 41216 -> "ei011";
            case 41222 -> "ei008";
            case 41223 -> "ei007";
            case 41224 -> "ei009";
            case 41225 -> "ei013";
            case 41226 -> "ei014";
            case 41227 -> "ei033";
            case 41228 -> "ei034";
            case 41229 -> "ei025";
            case 41230 -> "ei020";
            case 41231 -> "ei021";
            case 41233 -> "ei019";
            case 41234 -> "ei023";
            case 41235 -> "ei024";
            case 41239 -> "ei018";
            case 41240 -> "ei022";
            case 41060 -> "nonames";
            case 41061 -> "kames";
            case 41062 -> "bakumos";
            case 41063 -> "bukas";
            case 41064 -> "huwoomos";
            case 41065 -> "noas";
            case 41356 -> "rparum3";
            case 40663 -> "sonsletter";
            case 40630 -> "diegodiary";
            case 41340 -> "tion";
            case 41317 -> "rarson";
            case 41318 -> "kuen";
            case 41329 -> "anirequest";
            case 41346 -> "robinscroll";
            case 41347 -> "robinscroll2";
            case 41348 -> "robinhood";
            case 41007 -> "erisscroll";
            case 41009 -> "erisscroll2";
            case 41019 -> "lashistory1";
            case 41020 -> "lashistory2";
            case 41021 -> "lashistory3";
            case 41022 -> "lashistory4";
            case 41023 -> "lashistory5";
            case 41024 -> "lashistory6";
            case 41025 -> "lashistory7";
            case 41026 -> "lashistory8";
            case 210087 -> "first_p";
            case 210093 -> "silrein1lt";
            case 410106 -> "j_ep0s00";
            case 410101 -> "j_ep0s01";
            case 410103 -> "j_ep0s02";
            case 410105 -> "j_ep0s03";
            case 410098 -> "j_ep0s04";
            case 410099 -> "j_ep0s05";
            case 410100 -> "j_ep0s06";
            case 410097 -> "j_ep0s07";
            case 410102 -> "j_ep0s08";
            case 410104 -> "j_ep0s09";
            case 410107 -> "j_ep0s10";
            default -> null;
        };
        if (html == null) throw new IllegalArgumentException("Not a dialogue item: " + itemId);
        SC_NPCTalkReturn.send(pc, pc.getId(), html);
    }
}
