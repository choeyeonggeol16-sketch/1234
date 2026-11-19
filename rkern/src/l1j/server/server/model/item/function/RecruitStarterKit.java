package l1j.server.server.model.item.function;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import static l1j.server.server.model.item.function.RecruitStarterItemGrant.봉인템;

public final class RecruitStarterKit {
    private static final Logger LOG = Logger.getLogger(RecruitStarterKit.class.getName());
    private RecruitStarterKit() {}

    /** One receipt owns the source debit AND every class-dependent reward. */
    public static void clickItem(L1PcInstance pc, int itemId, L1ItemInstance source) {
        if (pc == null || itemId != 3000366 || source == null
                || source.getItemId() != 3000366
                || pc.getInventory().getItem(source.getId()) != source) return;

        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
                CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(source, 1)) return;
            // Reuse only one small list for all post-COMMIT success messages.
            final ArrayList<String> announcements = new ArrayList<>(32);
            if (pc.isWarrior() && !grantWarrior(pc, tx, announcements)) return;
            if (pc.isKnight() && !grantKnight(pc, tx, announcements)) return;
            if (pc.isDragonknight() && !grantDragonknight(pc, tx, announcements)) return;
            if (pc.isCrown() && !grantCrown(pc, tx, announcements)) return;
            if (pc.isWizard() && !grantWizard(pc, tx, announcements)) return;
            if (pc.isBlackwizard() && !grantBlackwizard(pc, tx, announcements)) return;
            if (pc.isElf() && !grantElf(pc, tx, announcements)) return;
            if (pc.isDarkelf() && !grantDarkelf(pc, tx, announcements)) return;
            // Invalid/unrecognized class: do not consume a kit with no rewards.
            if (announcements.isEmpty()) return;
            tx.onFailure(() -> SC_SystemMessage.send(pc, "신규 영입 장비 저장에 실패했습니다. 아이템을 확인해 주세요."));
            CDB.RequestUseItem(pc, tx, () -> {
                for (int i = 0, end = announcements.size(); i < end; i++)
                    SC_ServerMessage.send(pc, 403, announcements.get(i));
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Recruit starter kit transaction failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "신규 영입 장비를 처리하지 못했습니다.");
        }
    }

    // Preserve original class flag order, reward ID, quantity, enchant and blessing.
	// All eight existing loadouts remain in their original grant order.
private static boolean grantWarrior(L1PcInstance pc, L1InventoryTransaction tx, ArrayList<String> announcements) {//전사
					if (!봉인템(pc, tx, announcements, 203006, 1, 10, 130, 0, true)) return false; // 태풍의 도끼
					if (!봉인템(pc, tx, announcements, 203006, 1, 10, 130, 0, true)) return false; // 태풍의 도끼
					if (!봉인템(pc, tx, announcements, 22365, 1, 7, 130, 0, true)) return false; // 전사단 투구
					if (!봉인템(pc, tx, announcements, 20085, 1, 7, 130, 0, true)) return false; // 티셔츠
					if (!봉인템(pc, tx, announcements, 222300, 1, 1, 130, 0, true)) return false; // 멸마의 판금 갑옷
					if (!봉인템(pc, tx, announcements, 900043, 1, 7, 130, 0, true)) return false; // 전사단 갈색 망토
					if (!봉인템(pc, tx, announcements, 20187, 1, 7, 130, 0, true)) return false; // 파워 글로브
					if (!봉인템(pc, tx, announcements, 222327, 1, 7, 130, 0, true)) return false; // 강철 각반
//					봉인템(pc, 22254, 1, 2, 130, 0, true); // 쌍수시 불필요
					if (!봉인템(pc, tx, announcements, 222307, 1, 7, 130, 0, true)) return false; // 완력의 부츠
					if (!봉인템(pc, tx, announcements, 900020, 1, 3, 130, 0, true)) return false; // 성장의 문장
					if (!봉인템(pc, tx, announcements, 900021, 1, 3, 130, 0, true)) return false; // 회복의 문장
					if (!봉인템(pc, tx, announcements, 222340, 1, 3, 130, 0, true)) return false; // 룸티스의 검은빛 귀걸이
					if (!봉인템(pc, tx, announcements, 22229, 1, 3, 130, 0, true)) return false; // 룸티스의붉은빛귀걸이
					if (!봉인템(pc, tx, announcements, 20264, 1, 3, 130, 0, true)) return false; // 완력의 목걸이
					if (!봉인템(pc, tx, announcements, 222291, 1, 3, 130, 0, true)) return false; // 스냅퍼의 용사 반지
					if (!봉인템(pc, tx, announcements, 222291, 1, 3, 130, 0, true)) return false; // 스냅퍼의 용사 반지
					if (!봉인템(pc, tx, announcements, 22363, 1, 3, 130, 0, true)) return false; // 완력의 반지
					if (!봉인템(pc, tx, announcements, 22363, 1, 3, 130, 0, true)) return false; // 완력의 반지
					if (!봉인템(pc, tx, announcements, 20317, 1, 3, 130, 0, true)) return false; // 오우거의 벨트
					if (!봉인템(pc, tx, announcements, 60041, 1, 0, 130, 0, true)) return false; // 힘의 룬 주머니(70레벨)
					
					if (!봉인템(pc, tx, announcements, 40081, 10, 0, 1, 0, true)) return false; // 기란 마을 귀환 주문서
					if (!봉인템(pc, tx, announcements, 40088, 10, 0, 1, 0, true)) return false; // 변신주문서
					if (!봉인템(pc, tx, announcements, 40100, 300, 0, 1, 0, true)) return false; // 순간이동주문서
					if (!봉인템(pc, tx, announcements, 40024, 300, 0, 1, 0, true)) return false; // 신속 강력 체력 회복제
					if (!봉인템(pc, tx, announcements, 40014, 10, 0, 1, 0, true)) return false; // 용기의 물약
					if (!봉인템(pc, tx, announcements, 40013, 10, 0, 1, 0, true)) return false; // 초록향상 물약
					if (!봉인템(pc, tx, announcements, 3000421, 3, 0, 1, 0, true)) return false; // 신규변신주문서
					
					if (!봉인템(pc, tx, announcements, 600228, 5, 0, 1, 0, true)) return false; // 성장의 릴
					if (!봉인템(pc, tx, announcements, 1000004, 30, 0, 1, 0, true)) return false; // 드래곤의 다이아몬드
					if (!봉인템(pc, tx, announcements, 7241, 20, 0, 1, 0, true)) return false; // 토파즈
				
            return true;
    }

private static boolean grantKnight(L1PcInstance pc, L1InventoryTransaction tx, ArrayList<String> announcements) {//기사
					if (!봉인템(pc, tx, announcements, 505010, 1, 10, 130, 0, true)) return false; // 진 싸울아비 대검
					if (!봉인템(pc, tx, announcements, 20011, 1, 10, 130, 0, true)) return false; // 마법 방어 투구
					if (!봉인템(pc, tx, announcements, 20085, 1, 7, 130, 0, true)) return false; // 티셔츠
					if (!봉인템(pc, tx, announcements, 222300, 1, 1, 130, 0, true)) return false; // 멸마의 판금 갑옷
					if (!봉인템(pc, tx, announcements, 20056, 1, 7, 130, 0, true)) return false; // 마법 망토
					if (!봉인템(pc, tx, announcements, 20187, 1, 7, 130, 0, true)) return false; // 파워 글로브
					if (!봉인템(pc, tx, announcements, 222327, 1, 7, 130, 0, true)) return false; // 강철 각반
					if (!봉인템(pc, tx, announcements, 22254, 1, 3, 130, 0, true)) return false; // 수호의 가더
					if (!봉인템(pc, tx, announcements, 222307, 1, 7, 130, 0, true)) return false; // 완력의 부츠
					if (!봉인템(pc, tx, announcements, 900020, 1, 3, 130, 0, true)) return false; // 성장의 문장
					if (!봉인템(pc, tx, announcements, 900021, 1, 3, 130, 0, true)) return false; // 회복의 문장
					if (!봉인템(pc, tx, announcements, 222340, 1, 3, 130, 0, true)) return false; // 룸티스의 검은빛 귀걸이
					if (!봉인템(pc, tx, announcements, 22229, 1, 3, 130, 0, true)) return false; // 룸티스의붉은빛귀걸이
					if (!봉인템(pc, tx, announcements, 20264, 1, 3, 130, 0, true)) return false; // 완력의 목걸이
					if (!봉인템(pc, tx, announcements, 222291, 1, 3, 130, 0, true)) return false; // 스냅퍼의 용사 반지
					if (!봉인템(pc, tx, announcements, 222291, 1, 3, 130, 0, true)) return false; // 스냅퍼의 용사 반지
					if (!봉인템(pc, tx, announcements, 22363, 1, 3, 130, 0, true)) return false; // 완력의 반지
					if (!봉인템(pc, tx, announcements, 22363, 1, 3, 130, 0, true)) return false; // 완력의 반지
					if (!봉인템(pc, tx, announcements, 20317, 1, 3, 130, 0, true)) return false; // 오우거의 벨트
					if (!봉인템(pc, tx, announcements, 60041, 1, 0, 130, 0, true)) return false; // 힘의 룬 주머니(70레벨)
					
					if (!봉인템(pc, tx, announcements, 40081, 10, 0, 1, 0, true)) return false; // 기란 마을 귀환 주문서
					if (!봉인템(pc, tx, announcements, 40088, 10, 0, 1, 0, true)) return false; // 변신주문서
					if (!봉인템(pc, tx, announcements, 40100, 300, 0, 1, 0, true)) return false; // 순간이동주문서
					if (!봉인템(pc, tx, announcements, 40024, 300, 0, 1, 0, true)) return false; // 신속 강력 체력 회복제
					if (!봉인템(pc, tx, announcements, 40014, 10, 0, 1, 0, true)) return false; // 용기의 물약
					if (!봉인템(pc, tx, announcements, 40013, 10, 0, 1, 0, true)) return false; // 초록향상 물약
					if (!봉인템(pc, tx, announcements, 3000421, 3, 0, 1, 0, true)) return false; // 신규변신주문서
					
					if (!봉인템(pc, tx, announcements, 600228, 5, 0, 1, 0, true)) return false; // 성장의 릴
					if (!봉인템(pc, tx, announcements, 1000004, 30, 0, 1, 0, true)) return false; // 드래곤의 다이아몬드
					if (!봉인템(pc, tx, announcements, 7241, 20, 0, 1, 0, true)) return false; // 토파즈
				
            return true;
    }

private static boolean grantDragonknight(L1PcInstance pc, L1InventoryTransaction tx, ArrayList<String> announcements) {// 용기사
					if (!봉인템(pc, tx, announcements, 203017, 1, 10, 130, 0, true)) return false; // 섬멸자의 체인소드
					if (!봉인템(pc, tx, announcements, 20011, 1, 7, 130, 0, true)) return false; // 마법 방어 투구
					if (!봉인템(pc, tx, announcements, 20085, 1, 7, 130, 0, true)) return false; // 티셔츠
					if (!봉인템(pc, tx, announcements, 222302, 1, 1, 130, 0, true)) return false; // 멸마의 판금 갑옷
					if (!봉인템(pc, tx, announcements, 20056, 1, 7, 130, 0, true)) return false; // 마법 망토
					if (!봉인템(pc, tx, announcements, 20187, 1, 7, 130, 0, true)) return false; // 파워 글로브
					if (!봉인템(pc, tx, announcements, 222327, 1, 7, 130, 0, true)) return false; // 강철 각반
					if (!봉인템(pc, tx, announcements, 22254, 1, 3, 130, 0, true)) return false; // 수호의 가더
					if (!봉인템(pc, tx, announcements, 222307, 1, 7, 130, 0, true)) return false; // 완력의 부츠
					if (!봉인템(pc, tx, announcements, 900020, 1, 3, 130, 0, true)) return false; // 성장의 문장
					if (!봉인템(pc, tx, announcements, 900021, 1, 3, 130, 0, true)) return false; // 회복의 문장
					if (!봉인템(pc, tx, announcements, 222340, 1, 3, 130, 0, true)) return false; // 룸티스의 검은빛 귀걸이
					if (!봉인템(pc, tx, announcements, 22229, 1, 3, 130, 0, true)) return false; // 룸티스의붉은빛귀걸이
					if (!봉인템(pc, tx, announcements, 20264, 1, 3, 130, 0, true)) return false; // 완력의 목걸이
					if (!봉인템(pc, tx, announcements, 222291, 1, 3, 130, 0, true)) return false; // 스냅퍼의 용사 반지
					if (!봉인템(pc, tx, announcements, 222291, 1, 3, 130, 0, true)) return false; // 스냅퍼의 용사 반지
					if (!봉인템(pc, tx, announcements, 22363, 1, 3, 130, 0, true)) return false; // 완력의 반지
					if (!봉인템(pc, tx, announcements, 22363, 1, 3, 130, 0, true)) return false; // 완력의 반지
					if (!봉인템(pc, tx, announcements, 20317, 1, 3, 130, 0, true)) return false; // 오우거의 벨트
					if (!봉인템(pc, tx, announcements, 60041, 1, 0, 130, 0, true)) return false; // 힘의 룬 주머니(70레벨)
					
					if (!봉인템(pc, tx, announcements, 40081, 10, 0, 1, 0, true)) return false; // 기란 마을 귀환 주문서
					if (!봉인템(pc, tx, announcements, 210035, 30, 0, 1, 0, true)) return false; // 각인의 뼈조각
					if (!봉인템(pc, tx, announcements, 40088, 10, 0, 1, 0, true)) return false; // 변신주문서
					if (!봉인템(pc, tx, announcements, 40100, 300, 0, 1, 0, true)) return false; // 순간이동주문서
					if (!봉인템(pc, tx, announcements, 40024, 300, 0, 1, 0, true)) return false; // 신속 강력 체력 회복제
					if (!봉인템(pc, tx, announcements, 40013, 10, 0, 1, 0, true)) return false; // 초록향상 물약
					if (!봉인템(pc, tx, announcements, 3000421, 3, 0, 1, 0, true)) return false; // 신규변신주문서
					
					if (!봉인템(pc, tx, announcements, 600228, 5, 0, 1, 0, true)) return false; // 성장의 릴
					if (!봉인템(pc, tx, announcements, 1000004, 30, 0, 1, 0, true)) return false; // 드래곤의 다이아몬드
					if (!봉인템(pc, tx, announcements, 7241, 20, 0, 1, 0, true)) return false; // 토파즈
				
            return true;
    }

private static boolean grantCrown(L1PcInstance pc, L1InventoryTransaction tx, ArrayList<String> announcements) {// 군주
					if (!봉인템(pc, tx, announcements, 54, 1, 10, 130, 0, true)) return false; // 커츠의 검
					if (!봉인템(pc, tx, announcements, 20011, 1, 7, 130, 0, true)) return false; // 마법 방어 투구
					if (!봉인템(pc, tx, announcements, 20085, 1, 7, 130, 0, true)) return false; // 티셔츠
					if (!봉인템(pc, tx, announcements, 222301, 1, 1, 130, 0, true)) return false; // 멸마의 비늘 갑옷
					if (!봉인템(pc, tx, announcements, 20056, 1, 7, 130, 0, true)) return false; // 마법 망토
					if (!봉인템(pc, tx, announcements, 20187, 1, 7, 130, 0, true)) return false; // 파워 글로브
					if (!봉인템(pc, tx, announcements, 222327, 1, 7, 130, 0, true)) return false; // 강철 각반
					if (!봉인템(pc, tx, announcements, 22254, 1, 3, 130, 0, true)) return false; // 수호의 가더
					if (!봉인템(pc, tx, announcements, 222307, 1, 7, 130, 0, true)) return false; // 완력의 부츠
					if (!봉인템(pc, tx, announcements, 900020, 1, 3, 130, 0, true)) return false; // 성장의 문장
					if (!봉인템(pc, tx, announcements, 900021, 1, 3, 130, 0, true)) return false; // 회복의 문장
					if (!봉인템(pc, tx, announcements, 222340, 1, 3, 130, 0, true)) return false; // 룸티스의 검은빛 귀걸이
					if (!봉인템(pc, tx, announcements, 22229, 1, 3, 130, 0, true)) return false; // 룸티스의붉은빛귀걸이
					if (!봉인템(pc, tx, announcements, 20264, 1, 3, 130, 0, true)) return false; // 완력의 목걸이
					if (!봉인템(pc, tx, announcements, 222291, 1, 3, 130, 0, true)) return false; // 스냅퍼의 용사 반지
					if (!봉인템(pc, tx, announcements, 222291, 1, 3, 130, 0, true)) return false; // 스냅퍼의 용사 반지
					if (!봉인템(pc, tx, announcements, 22363, 1, 3, 130, 0, true)) return false; // 완력의 반지
					if (!봉인템(pc, tx, announcements, 22363, 1, 3, 130, 0, true)) return false; // 완력의 반지
					if (!봉인템(pc, tx, announcements, 20317, 1, 3, 130, 0, true)) return false; // 오우거의 벨트
					if (!봉인템(pc, tx, announcements, 60041, 1, 0, 130, 0, true)) return false; // 힘의 룬 주머니(70레벨)
					
					if (!봉인템(pc, tx, announcements, 40081, 10, 0, 1, 0, true)) return false; // 기란 마을 귀환 주문서
					if (!봉인템(pc, tx, announcements, 40031, 30, 0, 1, 0, true)) return false; // 악피
					if (!봉인템(pc, tx, announcements, 40088, 10, 0, 1, 0, true)) return false; // 변신주문서
					if (!봉인템(pc, tx, announcements, 40100, 300, 0, 1, 0, true)) return false; // 순간이동주문서
					if (!봉인템(pc, tx, announcements, 40024, 300, 0, 1, 0, true)) return false; // 신속 강력 체력 회복제
					if (!봉인템(pc, tx, announcements, 40013, 10, 0, 1, 0, true)) return false; // 초록향상 물약
					if (!봉인템(pc, tx, announcements, 3000421, 3, 0, 1, 0, true)) return false; // 신규변신주문서
					
					if (!봉인템(pc, tx, announcements, 600228, 5, 0, 1, 0, true)) return false; // 성장의 릴
					if (!봉인템(pc, tx, announcements, 1000004, 30, 0, 1, 0, true)) return false; // 드래곤의 다이아몬드
					if (!봉인템(pc, tx, announcements, 7241, 20, 0, 1, 0, true)) return false; // 토파즈
				
            return true;
    }

private static boolean grantWizard(L1PcInstance pc, L1InventoryTransaction tx, ArrayList<String> announcements) {// 마법사
					if (!봉인템(pc, tx, announcements, 202003, 1, 10, 130, 0, true)) return false; // 제로스의 지팡이
					if (!봉인템(pc, tx, announcements, 20011, 1, 7, 130, 0, true)) return false; // 마법 방어 투구
					if (!봉인템(pc, tx, announcements, 20085, 1, 7, 130, 0, true)) return false; // 티셔츠
					if (!봉인템(pc, tx, announcements, 222303, 1, 1, 130, 0, true)) return false; // 멸마의 로브
					if (!봉인템(pc, tx, announcements, 20055, 1, 7, 130, 0, true)) return false; // 마나 망토
					if (!봉인템(pc, tx, announcements, 20274, 1, 7, 130, 0, true)) return false; // 빛나는 마력의 장갑
					if (!봉인템(pc, tx, announcements, 222327, 1, 7, 130, 0, true)) return false; // 강철 각반
					if (!봉인템(pc, tx, announcements, 22255, 1, 3, 130, 0, true)) return false; // 마법사의 가더
					if (!봉인템(pc, tx, announcements, 222309, 1, 7, 130, 0, true)) return false; // 지식의 부츠
					if (!봉인템(pc, tx, announcements, 900020, 1, 3, 130, 0, true)) return false; // 성장의 문장
					if (!봉인템(pc, tx, announcements, 900021, 1, 3, 130, 0, true)) return false; // 회복의 문장
					if (!봉인템(pc, tx, announcements, 222340, 1, 3, 130, 0, true)) return false; // 룸티스의 검은빛 귀걸이
					if (!봉인템(pc, tx, announcements, 22229, 1, 3, 130, 0, true)) return false; // 룸티스의붉은빛귀걸이
					if (!봉인템(pc, tx, announcements, 20266, 1, 3, 130, 0, true)) return false; // 지식의 목걸이
					if (!봉인템(pc, tx, announcements, 22227, 1, 3, 130, 0, true)) return false; // 스냅퍼의 마나 반지
					if (!봉인템(pc, tx, announcements, 22227, 1, 3, 130, 0, true)) return false; // 스냅퍼의 마나 반지
					if (!봉인템(pc, tx, announcements, 22358, 1, 3, 130, 0, true)) return false; // 지식의 반지
					if (!봉인템(pc, tx, announcements, 22358, 1, 3, 130, 0, true)) return false; // 지식의 반지
					if (!봉인템(pc, tx, announcements, 20317, 1, 3, 130, 0, true)) return false; // 오우거의 벨트
					if (!봉인템(pc, tx, announcements, 60044, 1, 0, 130, 0, true)) return false; // 지식의 룬 주머니(70레벨)
					
					if (!봉인템(pc, tx, announcements, 40081, 10, 0, 1, 0, true)) return false; // 기란 마을 귀환 주문서
					if (!봉인템(pc, tx, announcements, 40088, 10, 0, 1, 0, true)) return false; // 변신주문서
					if (!봉인템(pc, tx, announcements, 40100, 300, 0, 1, 0, true)) return false; // 순간이동주문서
					if (!봉인템(pc, tx, announcements, 40024, 300, 0, 1, 0, true)) return false; // 신속 강력 체력 회복제
					if (!봉인템(pc, tx, announcements, 40013, 10, 0, 1, 0, true)) return false; // 초록향상 물약
					if (!봉인템(pc, tx, announcements, 3000421, 3, 0, 1, 0, true)) return false; // 신규변신주문서
					
					if (!봉인템(pc, tx, announcements, 600228, 5, 0, 1, 0, true)) return false; // 성장의 릴
					if (!봉인템(pc, tx, announcements, 1000004, 30, 0, 1, 0, true)) return false; // 드래곤의 다이아몬드
					if (!봉인템(pc, tx, announcements, 7241, 20, 0, 1, 0, true)) return false; // 토파즈
				
            return true;
    }

private static boolean grantBlackwizard(L1PcInstance pc, L1InventoryTransaction tx, ArrayList<String> announcements) {// 환술사
					if (!봉인템(pc, tx, announcements, 1120, 1, 10, 130, 0, true)) return false; // 냉한의 키링크
					if (!봉인템(pc, tx, announcements, 20011, 1, 7, 130, 0, true)) return false; // 마법 방어 투구
					if (!봉인템(pc, tx, announcements, 20085, 1, 7, 130, 0, true)) return false; // 티셔츠
					if (!봉인템(pc, tx, announcements, 222303, 1, 1, 130, 0, true)) return false; // 멸마의 로브
					if (!봉인템(pc, tx, announcements, 20056, 1, 7, 130, 0, true)) return false; // 마법 망토
					if (!봉인템(pc, tx, announcements, 20274, 1, 7, 130, 0, true)) return false; // 빛나는 마력의 장갑
					if (!봉인템(pc, tx, announcements, 222327, 1, 7, 130, 0, true)) return false; // 강철 각반
					if (!봉인템(pc, tx, announcements, 22254, 1, 3, 130, 0, true)) return false; // 수호의 가더
					if (!봉인템(pc, tx, announcements, 222309, 1, 7, 130, 0, true)) return false; // 지식의 부츠
					if (!봉인템(pc, tx, announcements, 900020, 1, 3, 130, 0, true)) return false; // 성장의 문장
					if (!봉인템(pc, tx, announcements, 900021, 1, 3, 130, 0, true)) return false; // 회복의 문장
					if (!봉인템(pc, tx, announcements, 222340, 1, 3, 130, 0, true)) return false; // 룸티스의 검은빛 귀걸이
					if (!봉인템(pc, tx, announcements, 22229, 1, 3, 130, 0, true)) return false; // 룸티스의붉은빛귀걸이
					if (!봉인템(pc, tx, announcements, 20266, 1, 3, 130, 0, true)) return false; // 지식의 목걸이
					if (!봉인템(pc, tx, announcements, 22227, 1, 3, 130, 0, true)) return false; // 스냅퍼의 마나 반지
					if (!봉인템(pc, tx, announcements, 22227, 1, 3, 130, 0, true)) return false; // 스냅퍼의 마나 반지
					if (!봉인템(pc, tx, announcements, 22358, 1, 3, 130, 0, true)) return false; // 지식의 반지
					if (!봉인템(pc, tx, announcements, 22358, 1, 3, 130, 0, true)) return false; // 지식의 반지
					if (!봉인템(pc, tx, announcements, 20317, 1, 3, 130, 0, true)) return false; // 오우거의 벨트
					if (!봉인템(pc, tx, announcements, 60044, 1, 0, 130, 0, true)) return false; // 지식의 룬 주머니(70레벨)
					
					if (!봉인템(pc, tx, announcements, 40081, 10, 0, 1, 0, true)) return false; // 기란 마을 귀환 주문서
					if (!봉인템(pc, tx, announcements, 210036, 30, 0, 1, 0, true)) return false; // 유그드라 열매
					if (!봉인템(pc, tx, announcements, 40088, 10, 0, 1, 0, true)) return false; // 변신주문서
					if (!봉인템(pc, tx, announcements, 40100, 300, 0, 1, 0, true)) return false; // 순간이동주문서
					if (!봉인템(pc, tx, announcements, 40024, 300, 0, 1, 0, true)) return false; // 신속 강력 체력 회복제
					if (!봉인템(pc, tx, announcements, 40013, 10, 0, 1, 0, true)) return false; // 초록향상 물약
					if (!봉인템(pc, tx, announcements, 3000421, 3, 0, 1, 0, true)) return false; // 신규변신주문서
					
					if (!봉인템(pc, tx, announcements, 600228, 5, 0, 1, 0, true)) return false; // 성장의 릴
					if (!봉인템(pc, tx, announcements, 1000004, 30, 0, 1, 0, true)) return false; // 드래곤의 다이아몬드
					if (!봉인템(pc, tx, announcements, 7241, 20, 0, 1, 0, true)) return false; // 토파즈
				
            return true;
    }

private static boolean grantElf(L1PcInstance pc, L1InventoryTransaction tx, ArrayList<String> announcements) {
					if (!봉인템(pc, tx, announcements, 1136, 1, 10, 130, 0, true)) return false; // 악몽의 장궁
					if (!봉인템(pc, tx, announcements, 20033, 1, 7, 130, 0, true)) return false; // 엘름의 축복
					if (!봉인템(pc, tx, announcements, 20085, 1, 7, 130, 0, true)) return false; // 티셔츠
					if (!봉인템(pc, tx, announcements, 222302, 1, 7, 130, 0, true)) return false; // 멸마의 가죽 갑옷
					if (!봉인템(pc, tx, announcements, 20056, 1, 7, 130, 0, true)) return false; // 마법 망토
					if (!봉인템(pc, tx, announcements, 22262, 1, 7, 130, 0, true)) return false; // 명궁의 장갑
					if (!봉인템(pc, tx, announcements, 222327, 1, 7, 130, 0, true)) return false; // 강철 각반
					if (!봉인템(pc, tx, announcements, 22000, 1, 3, 130, 0, true)) return false; // 고대 명궁의 가더
					if (!봉인템(pc, tx, announcements, 222308, 1, 7, 130, 0, true)) return false; // 민첩의 부츠
					if (!봉인템(pc, tx, announcements, 900020, 1, 3, 130, 0, true)) return false; // 성장의 문장
					if (!봉인템(pc, tx, announcements, 900021, 1, 3, 130, 0, true)) return false; // 회복의 문장
					if (!봉인템(pc, tx, announcements, 222340, 1, 3, 130, 0, true)) return false; // 룸티스의 검은빛 귀걸이
					if (!봉인템(pc, tx, announcements, 22229, 1, 3, 130, 0, true)) return false; // 룸티스의붉은빛귀걸이
					if (!봉인템(pc, tx, announcements, 20256, 1, 3, 130, 0, true)) return false; // 민첩의 목걸이
					if (!봉인템(pc, tx, announcements, 222291, 1, 3, 130, 0, true)) return false; // 스냅퍼의 용사 반지
					if (!봉인템(pc, tx, announcements, 222291, 1, 3, 130, 0, true)) return false; // 스냅퍼의 용사 반지
					if (!봉인템(pc, tx, announcements, 22364, 1, 3, 130, 0, true)) return false; // 민첩의 반지
					if (!봉인템(pc, tx, announcements, 22364, 1, 3, 130, 0, true)) return false; // 민첩의 반지
					if (!봉인템(pc, tx, announcements, 20317, 1, 3, 130, 0, true)) return false; // 오우거의 벨트
					if (!봉인템(pc, tx, announcements, 60042, 1, 0, 130, 0, true)) return false; // 민첩의 룬 주머니(70레벨)
					
					if (!봉인템(pc, tx, announcements, 40081, 10, 0, 1, 0, true)) return false; // 기란 마을 귀환 주문서
					if (!봉인템(pc, tx, announcements, 40748, 3000, 0, 1, 0, true)) return false; // 화살
					if (!봉인템(pc, tx, announcements, 40068, 30, 0, 1, 0, true)) return false; // 와퍼
					if (!봉인템(pc, tx, announcements, 40088, 10, 0, 1, 0, true)) return false; // 변신주문서
					if (!봉인템(pc, tx, announcements, 40100, 300, 0, 1, 0, true)) return false; // 순간이동주문서
					if (!봉인템(pc, tx, announcements, 40024, 300, 0, 1, 0, true)) return false; // 신속 강력 체력 회복제
					if (!봉인템(pc, tx, announcements, 40013, 10, 0, 1, 0, true)) return false; // 초록향상 물약
					if (!봉인템(pc, tx, announcements, 3000421, 3, 0, 1, 0, true)) return false; // 신규변신주문서
					
					if (!봉인템(pc, tx, announcements, 600228, 5, 0, 1, 0, true)) return false; // 성장의 릴
					if (!봉인템(pc, tx, announcements, 1000004, 30, 0, 1, 0, true)) return false; // 드래곤의 다이아몬드
					if (!봉인템(pc, tx, announcements, 7241, 20, 0, 1, 0, true)) return false; // 토파즈
				
            return true;
    }

private static boolean grantDarkelf(L1PcInstance pc, L1InventoryTransaction tx, ArrayList<String> announcements) {
					if (!봉인템(pc, tx, announcements, 203018, 1, 10, 130, 0, true)) return false; // 포효의 이도류
					if (!봉인템(pc, tx, announcements, 20011, 1, 7, 130, 0, true)) return false; // 마법 방어 투구
					if (!봉인템(pc, tx, announcements, 20085, 1, 7, 130, 0, true)) return false; // 티셔츠
					if (!봉인템(pc, tx, announcements, 222302, 1, 1, 130, 0, true)) return false; // 멸마의 가죽 갑옷
					if (!봉인템(pc, tx, announcements, 20056, 1, 7, 130, 0, true)) return false; // 마법 망토
					if (!봉인템(pc, tx, announcements, 20187, 1, 7, 130, 0, true)) return false; // 파워 글로브
					if (!봉인템(pc, tx, announcements, 222327, 1, 7, 130, 0, true)) return false; // 강철 각반
					if (!봉인템(pc, tx, announcements, 22254, 1, 3, 130, 0, true)) return false; // 수호의 가더
					if (!봉인템(pc, tx, announcements, 222307, 1, 7, 130, 0, true)) return false; // 완력의 부츠
					if (!봉인템(pc, tx, announcements, 900020, 1, 3, 130, 0, true)) return false; // 성장의 문장
					if (!봉인템(pc, tx, announcements, 900021, 1, 3, 130, 0, true)) return false; // 회복의 문장
					if (!봉인템(pc, tx, announcements, 222340, 1, 3, 130, 0, true)) return false; // 룸티스의 검은빛 귀걸이
					if (!봉인템(pc, tx, announcements, 22229, 1, 3, 130, 0, true)) return false; // 룸티스의붉은빛귀걸이
					if (!봉인템(pc, tx, announcements, 20264, 1, 3, 130, 0, true)) return false; // 완력의 목걸이
					if (!봉인템(pc, tx, announcements, 222291, 1, 3, 130, 0, true)) return false; // 스냅퍼의 용사 반지
					if (!봉인템(pc, tx, announcements, 222291, 1, 3, 130, 0, true)) return false; // 스냅퍼의 용사 반지
					if (!봉인템(pc, tx, announcements, 22363, 1, 3, 130, 0, true)) return false; // 완력의 반지
					if (!봉인템(pc, tx, announcements, 22363, 1, 3, 130, 0, true)) return false; // 완력의 반지
					if (!봉인템(pc, tx, announcements, 20317, 1, 3, 130, 0, true)) return false; // 오우거의 벨트
					if (!봉인템(pc, tx, announcements, 60041, 1, 0, 130, 0, true)) return false; // 힘의 룬 주머니(70레벨)
					
					if (!봉인템(pc, tx, announcements, 40081, 10, 0, 1, 0, true)) return false; // 기란 마을 귀환 주문서
					if (!봉인템(pc, tx, announcements, 40088, 10, 0, 1, 0, true)) return false; // 변신주문서
					if (!봉인템(pc, tx, announcements, 40100, 300, 0, 1, 0, true)) return false; // 순간이동주문서
					if (!봉인템(pc, tx, announcements, 40024, 300, 0, 1, 0, true)) return false; // 신속 강력 체력 회복제
					if (!봉인템(pc, tx, announcements, 40013, 10, 0, 1, 0, true)) return false; // 초록향상 물약
					if (!봉인템(pc, tx, announcements, 3000421, 3, 0, 1, 0, true)) return false; // 신규변신주문서
					
					if (!봉인템(pc, tx, announcements, 600228, 5, 0, 1, 0, true)) return false; // 성장의 릴
					if (!봉인템(pc, tx, announcements, 1000004, 30, 0, 1, 0, true)) return false; // 드래곤의 다이아몬드
					if (!봉인템(pc, tx, announcements, 7241, 20, 0, 1, 0, true)) return false; // 토파즈
				
        return true;
    }
}
