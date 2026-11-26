package l1j.server.server.model.item.function;

import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;

import static l1j.server.server.model.item.function.CreatureItemActions._random;

/** Both legacy boxes use one receipt: source debit + guaranteed Adena + optional random reward. */
final class LegacyGiftBoxes {
    private static final Logger LOG = Logger.getLogger(LegacyGiftBoxes.class.getName());
    private LegacyGiftBoxes() {}

    static ItemActionResult open(ItemActionContext use, int boxId) {
        final L1PcInstance pc = use.player();
        final L1ItemInstance source = use.item();
        if (pc == null || source == null || (boxId != 41303 && boxId != 41304)
                || source.getItemId() != boxId
                || pc.getInventory().GetByServerID(source.getId()) != source)
            return ItemActionResult.STOP;

        // Sample EXACTLY once, with the same shared RNG and bounds as the original boxes.
        final int roll = _random.nextInt(boxId == 41303 ? 120 : 170) + 1;
        final int adena = boxId == 41303 ? 500000 : 5000000;
        final String adenaNotice = boxId == 41303
                ? "아데나 (500,000)을 얻었습니다." : "아데나 (5,000,000)을 얻었습니다.";
        int bonusId = 0;
        String bonusNotice = "아이템을 획득하지 못하였습니다.";
        if (boxId == 41303) {
            if (roll <= 12)       { bonusId = 20315;  bonusNotice = "영양 만점 허리끈 을 얻었습니다."; }
            else if (roll <= 24)  { bonusId = 20262;  bonusNotice = "영양 만점 목걸이 을 얻었습니다."; }
            else if (roll <= 36)  { bonusId = 20291;  bonusNotice = "영양 만점 반지 을 얻었습니다."; }
            else if (roll <= 48)  { bonusId = 40087;  bonusNotice = "무기 마법 주문서 을 얻었습니다."; }
            else if (roll <= 59)  { bonusId = 40074;  bonusNotice = "갑옷 마법 주문서 을 얻었습니다."; }
            else if (roll <= 65)  { bonusId = 41248;  bonusNotice = "마법인형(버그베어) 을 얻었습니다."; }
            else if (roll <= 71)  { bonusId = 210096; bonusNotice = "마법인형(에티) 을 얻었습니다."; }
            else if (roll <= 74)  { bonusId = 210105; bonusNotice = "마법인형(코카트리스) 을 얻었습니다."; }
            else if (roll <= 77)  { bonusId = 20422;  bonusNotice = "빛나는 고대 목걸이 을 얻었습니다."; }
            else if (roll <= 79)  { bonusId = 22000;  bonusNotice = "고대명궁가더 을 얻었습니다."; }
            else if (roll <= 81)  { bonusId = 22003;  bonusNotice = "고대투사가더 을 얻었습니다."; }
            else if (roll <= 86)  { bonusId = 30127;  bonusNotice = "52레벨 퀘스트 아이템 상자를 얻었습니다."; }
        } else {
            if (roll <= 25)       { bonusId = 41249;  bonusNotice = "마법인형(서큐버스) 을 얻었습니다."; }
            else if (roll <= 51)  { bonusId = 41250;  bonusNotice = "마법인형(늑대인간) 을 얻었습니다."; }
            else if (roll <= 77)  { bonusId = 210070; bonusNotice = "마법인형(돌 골렘) 을 얻었습니다."; }
            else if (roll <= 88)  { bonusId = 40038;  bonusNotice = "엘릭서(CHA) 을 얻었습니다."; }
            else if (roll <= 99)  { bonusId = 140087; bonusNotice = "축복받은 무기 마법 주문서 을 얻었습니다."; }
            else if (roll <= 110) { bonusId = 140074; bonusNotice = "축복받은 갑옷 마법 주문서 을 얻었습니다."; }
            else if (roll <= 112) { bonusId = 202002; bonusNotice = "붉은 기사의 대검 을 얻었습니다."; }
            else if (roll <= 114) { bonusId = 504;    bonusNotice = "흑요석 키링크 을 얻었습니다."; }
            else if (roll <= 116) { bonusId = 205;    bonusNotice = "달의 장궁 을 얻었습니다."; }
            else if (roll <= 118) { bonusId = 20165;  bonusNotice = "데몬의 장갑 을 얻었습니다."; }
            else if (roll <= 120) { bonusId = 20197;  bonusNotice = "데몬의 부츠 을 얻었습니다."; }
            else if (roll <= 122) { bonusId = 20160;  bonusNotice = "흑장로의 로브 을 얻었습니다."; }
            else if (roll <= 124) { bonusId = 20218;  bonusNotice = "흑장로의 샌달 을 얻었습니다."; }
            else if (roll <= 126) { bonusId = 20298;  bonusNotice = "제니스의 반지 을 얻었습니다."; }
            else if (roll <= 131) { bonusId = 30127;  bonusNotice = "52레벨 퀘스트 아이템 상자를 얻었습니다."; }
        }

        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
                CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(source, 1)) return ItemActionResult.STOP;
            // Do not expose any reward or success message before the receipt is committed.
            if (tx.grant(40308, adena) == null
                    || (bonusId != 0 && tx.grant(bonusId, 1) == null)) {
                SC_ServerMessage.send(pc, 82);
                return ItemActionResult.STOP;
            }
            final String committedBonusNotice = bonusNotice;
            tx.onFailure(() -> SC_SystemMessage.send(pc, "선물상자 보상을 저장하지 못했습니다. 아이템을 확인해 주세요."));
            CDB.RequestUseItem(pc, tx, () -> {
                try {
                    try {
                        SC_SystemMessage.send(pc, adenaNotice);
                    } catch (RuntimeException outputFailure) {
                        LOG.log(Level.WARNING, "Committed gift-box adena notice failed: " + boxId,
                                outputFailure);
                    }
                    try {
                        SC_SystemMessage.send(pc, committedBonusNotice);
                    } catch (RuntimeException outputFailure) {
                        LOG.log(Level.WARNING, "Committed gift-box bonus notice failed: " + boxId,
                                outputFailure);
                    }
                } finally {
                    ItemActionCompletion.finishCommittedConsumable(pc, source);
                }
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Legacy gift box receipt failed: " + pc.getId() + " / " + boxId, failure);
            SC_SystemMessage.send(pc, "선물상자 사용을 처리하지 못했습니다.");
        }
        return ItemActionResult.STOP;
    }
}
