package l1j.server.server.model.network.core.cs.packet;

import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.shared.GameRandom;

/** Template-specific command dispatch after the existing HTML/XML/listener precedence. */
final class NpcTemplateCommands {
    private static final Logger LOG = Logger.getLogger(NpcTemplateCommands.class.getName());
    private NpcTemplateCommands() {}

    static String dispatch(L1PcInstance pc, L1Object obj, String s, String htmlid) {
        final L1NpcInstance npc = (L1NpcInstance) obj;
        final int npcid = npc.getNpcId();
        try {
            return switch (npcid) {
                case 200201 -> NpcExchangeCommands.golem(pc, obj.getId(), s, htmlid);
                case 4200018 -> NpcExperienceCommands.levelSupport(pc, s, htmlid);
                case 5163 -> NpcCastleActions.redKnight(pc, s, htmlid);
                case 7000079 -> NpcGameEventActions.bossTraining(pc, npc, s, htmlid);
                case 7200022 -> NpcBuffCommands.birthday(pc, s, htmlid, obj);
                case 7000080 -> NpcGameEventActions.bossKey(pc, npc, s, htmlid, GameRandom.current());
                case 7210050 -> NpcQuestTransactions.oldBook(pc, s, htmlid);
                case 7210000 -> NpcTravelCommands.waterDragonDungeon(pc, s, htmlid);
                case 7310088 -> NpcTravelCommands.peter(pc, s, htmlid);
                case 505011 -> NpcTravelCommands.jeep(pc, s, htmlid, GameRandom.current());
                case 535 -> NpcQuestTransactions.exchangeProtonGun(pc, obj.getId(), s, htmlid);
                case 7210007 -> NpcQuestTransactions.hamo(pc, obj.getId(), s, htmlid);
                case 7210008 -> NpcGameEventActions.valakasReady(pc, npcid, s, htmlid);
                case 7210009 -> NpcGameEventActions.valakasRaid(pc, npcid, s, htmlid);
                case 7310093 -> NpcTravelCommands.piar(pc, s, htmlid, GameRandom.current());
                case 70611 -> NpcBuffCommands.lawfulBook(pc, s, htmlid);
                case 7320159 -> NpcBuffCommands.dragonHunter(pc, s, htmlid);
                case 50015 -> NpcTravelCommands.pcRoom50015(pc, s, htmlid);
                case 7320260 -> NpcBuffCommands.miso(pc, s, htmlid);
                case 5126 -> NpcBuffCommands.merisha(pc, s, htmlid);
                case 7310121, 7320030 -> NpcBuffCommands.clanBuff(pc, s, htmlid);
                case 6200008 -> NpcBuffCommands.basicBuff(pc, s, htmlid);
                case 900135 -> NpcGameEventActions.orim(pc, obj, s, htmlid);
                case 7000055, 7000054 -> NpcExchangeCommands.rune(pc, npcid, s, htmlid);
                case 1000001 -> NpcTravelCommands.antCave(pc, s, htmlid);
                case 1000002 -> NpcTravelCommands.dragonHerald(pc, s, htmlid);
                case 202057 -> NpcTravelCommands.lala(pc, s, htmlid);
                case 7320010 -> NpcExperienceCommands.arcFreePass(pc, s, htmlid);
                case 202065 -> NpcTravelCommands.gunterTravel(pc, s, htmlid);
                case 202066 -> NpcQuestTransactions.claudiaScroll(pc, s, htmlid);
                case 60169 -> NpcBuffCommands.gunter(pc, s, htmlid);
                case 7200026 -> NpcBuffCommands.cray(pc, s, htmlid);
                case 7135 -> NpcBuffCommands.vala(pc, s, htmlid);
                case 4039009 -> NpcBuffCommands.sael(pc, s, htmlid);
                case 7000097 -> NpcTravelCommands.tamShop(pc, s, htmlid);
                case 522 -> NpcBuffCommands.hunterBless(pc, s, htmlid);
                case 81210 -> NpcTravelCommands.townTravel(pc, s, htmlid);
                case 70798 -> NpcTravelCommands.ricky(pc, s, htmlid);
                case 50078 -> NpcTravelCommands.gardenNotice(pc, s, htmlid, GameRandom.current());
                case 7320034 -> NpcTravelCommands.zigur(pc, s, htmlid, GameRandom.current());
                case 7320033 -> NpcTravelCommands.siegeTravel(pc, s, htmlid, GameRandom.current());
                case 7310174 -> NpcExchangeCommands.eva(pc, obj.getId(), s, htmlid);
                case 70690 -> NpcExchangeCommands.bamut(pc, obj.getId(), s, htmlid);
                case 7310149 -> NpcExchangeCommands.memoryCrystal(pc, obj.getId(), s, htmlid);
                case 50045 -> NpcTravelCommands.unicornTemple(pc, s, htmlid);
                case 7200000 -> NpcExperienceCommands.ekins(pc, obj.getId(), s, htmlid);
                case 7200001 -> NpcGameEventActions.fantasyIsland(pc, s, htmlid);
                case 70641 -> NpcExchangeCommands.habert(pc, obj.getId(), s, htmlid);
                case 1231231 -> NpcTravelCommands.forgottenIsland(pc, s, htmlid, GameRandom.current());
                case 71180 -> NpcExchangeCommands.jape(pc, obj.getId(), s, htmlid);
                case 5066 -> NpcExchangeCommands.fireGolem(pc, obj, s, htmlid);
                case 80077 -> NpcTravelCommands.aldran(pc, s, htmlid);
                case 7000082, 7000083, 7000084, 7000085, 7000086, 7000087 -> NpcCastleActions.catapult(pc, obj, s, htmlid);
                default -> NpcTravelCommands.isPcRoomTeleporter(npcid)
                        ? NpcTravelCommands.pcRoomTeleport(pc, npcid, s, htmlid) : htmlid;
            };
        } catch (Exception e) {
            LOG.log(Level.WARNING, "NPC template command execution failed", e);
        }
        return htmlid;
    }
}
