package l1j.server.server.model.network.core.cs.packet;

import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;

/** Template-specific command dispatch after the existing HTML/XML/listener precedence. */
final class NpcTemplateCommands {
    private static final Logger LOG = Logger.getLogger(NpcTemplateCommands.class.getName());
    private static final Random _random = new Random(System.nanoTime());
    private NpcTemplateCommands() {}

    static String dispatch(L1PcInstance pc, L1Object obj, String s, String htmlid) {
        int npcid = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
        try {
            if (npcid == 200201) {
                return NpcExchangeCommands.golem(pc, obj.getId(), s, htmlid);
            }
            else if (npcid == 4200018) {
                return NpcExperienceCommands.levelSupport(pc, s, htmlid);
            }
            else if (npcid == 5163) {
                return NpcCastleActions.redKnight(pc, s, htmlid);
            }
            else if (npcid == 7000079) {
                return NpcGameEventActions.bossTraining(pc, s, htmlid);
            }
            else if (npcid == 7200022) {
                return NpcBuffCommands.birthday(pc, s, htmlid, obj);
            }
            else if (npcid == 7000080) {
                return NpcGameEventActions.bossKey(pc, (L1NpcInstance) obj, s, htmlid, _random);
            }
            else if (npcid == 7210050) {
                return NpcQuestTransactions.oldBook(pc, s, htmlid);
            }
            else if (npcid == 7210000) {
                return NpcTravelCommands.waterDragonDungeon(pc, s, htmlid);
            }
            else if (npcid == 7310088) {
                return NpcTravelCommands.peter(pc, s, htmlid);
            }
            else if (npcid == 505011) {
                return NpcTravelCommands.jeep(pc, s, htmlid, _random);
            }
            else if (npcid == 535) {
                return NpcQuestTransactions.exchangeProtonGun(pc, obj.getId(), s, htmlid);
            }
            else if (npcid == 7210007) {
                return NpcQuestTransactions.hamo(pc, obj.getId(), s, htmlid);
            }
            else if (npcid == 7210008) {
                return NpcGameEventActions.valakasReady(pc, s, htmlid);
            }
            else if (npcid == 7210009) {
                return NpcGameEventActions.valakasRaid(pc, s, htmlid);
            }
            else if (npcid == 7310093) {
                return NpcTravelCommands.piar(pc, s, htmlid, _random);
            }
            else if (npcid == 70611) {
                return NpcBuffCommands.lawfulBook(pc, s, htmlid);
            }
            else if (npcid == 7320159) {
                return NpcBuffCommands.dragonHunter(pc, s, htmlid);
            }
            else if (npcid == 50015) {
                return NpcTravelCommands.pcRoom50015(pc, s, htmlid);
            }
            else if (npcid == 7320260) {
                return NpcBuffCommands.miso(pc, s, htmlid);
            }
            else if (NpcTravelCommands.isPcRoomTeleporter(npcid)) {
                return NpcTravelCommands.pcRoomTeleport(pc, npcid, s, htmlid);
            }
            else if (npcid == 5126) {
                return NpcBuffCommands.merisha(pc, s, htmlid);
            }
            else if (npcid == 7310121
                    || npcid == 7320030) {
                return NpcBuffCommands.clanBuff(pc, s, htmlid);
            }
            else if (npcid == 6200008) {
                return NpcBuffCommands.basicBuff(pc, s, htmlid);
            }
            else if (npcid == 900135) {
                return NpcGameEventActions.orim(pc, obj, s, htmlid);
            }
            else if (npcid == 7000055 || npcid == 7000054) {
                return NpcExchangeCommands.rune(pc, npcid, s, htmlid);
            }
            else if (npcid == 1000001) {
                return NpcTravelCommands.antCave(pc, s, htmlid);
            }
            else if (npcid == 1000002) {
                return NpcTravelCommands.dragonHerald(pc, s, htmlid);
            }
            else if (npcid == 202057) {
                return NpcTravelCommands.lala(pc, s, htmlid);
            }
            else if (npcid == 7320010) {
                return NpcExperienceCommands.arcFreePass(pc, s, htmlid);
            }
            else if (npcid == 202065) {
                return NpcTravelCommands.gunterTravel(pc, s, htmlid);
            }
            else if (npcid == 202066) {
                return NpcQuestTransactions.claudiaScroll(pc, s, htmlid);
            }
            else if (npcid == 60169) {
                return NpcBuffCommands.gunter(pc, s, htmlid);
            }
            else if (npcid == 7200026) {
                return NpcBuffCommands.cray(pc, s, htmlid);
            }
            else if (npcid == 7135) {
                return NpcBuffCommands.vala(pc, s, htmlid);
            }
            else if (npcid == 4039009) {
                return NpcBuffCommands.sael(pc, s, htmlid);
            }
            else if (npcid == 7000097) {
                return NpcTravelCommands.tamShop(pc, s, htmlid);
            }
            else if (npcid == 522) {
                return NpcBuffCommands.hunterBless(pc, s, htmlid);
            }
            else if (npcid == 81210) {
                return NpcTravelCommands.townTravel(pc, s, htmlid);
            }
            else if (npcid == 70798) {
                return NpcTravelCommands.ricky(pc, s, htmlid);
            }
            else if (npcid == 50078) {
                return NpcTravelCommands.gardenNotice(pc, s, htmlid, _random);
            }
            else if (npcid == 7320034) {
                return NpcTravelCommands.zigur(pc, s, htmlid, _random);
            }
            else if (npcid == 7320033) {
                return NpcTravelCommands.siegeTravel(pc, s, htmlid, _random);
            }
            else if (npcid == 7310174) {
                return NpcExchangeCommands.eva(pc, obj.getId(), s, htmlid);
            }
            else if (npcid == 70690) {
                return NpcExchangeCommands.bamut(pc, obj.getId(), s, htmlid);
            }
            else if (npcid == 7310149) {
                return NpcExchangeCommands.memoryCrystal(pc, obj.getId(), s, htmlid);
            }
            else if (npcid == 50045) {
                return NpcTravelCommands.unicornTemple(pc, s, htmlid);
            }
            else if (npcid == 7200000) {
                return NpcExperienceCommands.ekins(pc, obj.getId(), s, htmlid);
            }
            else if (npcid == 7200001) {
                return NpcGameEventActions.fantasyIsland(pc, s, htmlid);
            }
            else if (npcid == 70641) {
                return NpcExchangeCommands.habert(pc, obj.getId(), s, htmlid);
            }
            else if (npcid == 1231231) {
                return NpcTravelCommands.forgottenIsland(pc, s, htmlid, _random);
            }
            else if (npcid == 71180) {
                return NpcExchangeCommands.jape(pc, obj.getId(), s, htmlid);
            }
            else if (npcid == 5066) {
                return NpcExchangeCommands.fireGolem(pc, obj, s, htmlid);
            }
            else if (npcid == 80077) {
                return NpcTravelCommands.aldran(pc, s, htmlid);
            }
            else if (npcid == 7000082 || npcid == 7000083 || npcid == 7000084 || npcid == 7000085 || npcid == 7000086
                    || npcid == 7000087) {
				return NpcCastleActions.catapult(pc, obj, s, htmlid);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "NPC template command execution failed", e);
        }
        return htmlid;
    }
}
