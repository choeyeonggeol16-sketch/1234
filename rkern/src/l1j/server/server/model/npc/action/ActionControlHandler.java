package l1j.server.server.model.npc.action;

import l1j.server.server.model.character.transform.TransformType;
import l1j.server.server.model.character.transform.CTransform;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

import java.util.ArrayList;

import l1j.server.server.model.network.core.sc.packet.SC_Board;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import l1j.server.Config;
import l1j.server.server.model.item.teleport.NeigeTower;
import l1j.server.server.model.L1NeigeSaveChar;
import l1j.server.server.model.SeasonRewardHandler;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.Card.L1CardCollection;
import l1j.server.server.model.item.Card.L1CardHeroList;
import l1j.server.server.model.item.Card.L1CardLegendList;
import l1j.server.server.model.item.Card.L1CardMythClass;
import l1j.server.server.model.item.Card.L1CardMythList;
import l1j.server.server.model.item.Card.L1CardStep1;
import l1j.server.server.model.item.Card.L1CardStep3;
import l1j.server.server.model.item.Card.L1CardYuilList;
import l1j.server.server.model.item.Card.L1SkillTree;

/**
 * 게임 내 NPC 대화창 및 아이템 액션 통합 핸들러
 */
public class ActionControlHandler {

    private static final ActionControlHandler instance = new ActionControlHandler();
    public static ActionControlHandler getInstance() { return instance; }

    private final Map<String, GameAction> _actionMap = new HashMap<>();

    @FunctionalInterface
    interface GameAction {
        boolean run(L1PcInstance pc);
    }

    private ActionControlHandler() {
        init();
    }

    private void init() {
        // --- 메뉴 및 UI 관련 ---
        _actionMap.put("neiyuilm", pc -> { L1CardYuilList.showSimpleList(pc); return true; });
        _actionMap.put("neigemenu01", pc -> { SC_NPCTalkReturn.send(pc, pc.getId(), "neigetele1"); return true; });
        _actionMap.put("neigemenu02", this::handleScheduleMenu); // 스케줄 메뉴 
        _actionMap.put("neigemenu03", pc -> { SC_NPCTalkReturn.send(pc, pc.getId(), "neigetele3", new String[0]); return true; });
        _actionMap.put("neigemenu04", pc -> { SC_NPCTalkReturn.send(pc, pc.getId(), "neigetele4"); return true; });
        _actionMap.put("neigesce", pc -> { SC_NPCTalkReturn.send(pc, pc.getId(), "rudcia4"); return true; });
        _actionMap.put("neifirla", pc -> { SC_NPCTalkReturn.send(pc, pc.getId(), "neiffsa"); return true; });

        // --- 카드 연금 및 초기화 ---
        _actionMap.put("neifirl", pc -> { if (pc.isItemTransferBusy()) return true; resetCardInfo(pc); L1CardStep1.LegendAlchemyF(pc); return true; });
        _actionMap.put("neifirmy", pc -> { if (pc.isItemTransferBusy()) return true; resetCardInfo(pc); L1CardStep3.LegendAlchemyF(pc); return true; });

        // --- 캐릭터 정보 저장 관련 ---
        _actionMap.put("savecharm1", pc -> { L1NeigeSaveChar.CharInfo(pc, pc.getFindchar()); return true; });
        _actionMap.put("savecharm2", pc -> { L1NeigeSaveChar.CharInventory(pc, pc.getFindchar()); return true; });
        _actionMap.put("savecharm3", pc -> { L1NeigeSaveChar.CharSkill(pc, pc.getFindchar()); return true; });
        _actionMap.put("request oman book", pc -> { NeigeTower.Create(pc); return false; });

        // --- 특수 변신 (Kir 포함) ---
        _actionMap.put("neikir", this::handleKirPolyAction);
        _actionMap.put("neicen", pc -> { pc.CancelTransform(); return false; });
        
        // 일반 변신 데이터 매핑
        _actionMap.put("neibapo", pc -> applySimplePoly(pc, pc.getAccount().getInfo_bapo_poly(), 13863));
        _actionMap.put("neids",   pc -> applySimplePoly(pc, pc.getAccount().getInfo_ds_poly(), 19094));
        _actionMap.put("neidru",  pc -> applySimplePoly(pc, pc.getAccount().getInfo_dru_poly(), 19168));
        _actionMap.put("neiga",   pc -> applySimplePoly(pc, pc.getAccount().getInfo_ga_poly(), 19995));
        _actionMap.put("neiger",  pc -> applySimplePoly(pc, pc.getAccount().getInfo_ger_poly(), 19955));
        _actionMap.put("neidev",  pc -> applySimplePoly(pc, pc.getAccount().getInfo_dev_poly(), 18291));
        _actionMap.put("neipen",  pc -> applySimplePoly(pc, pc.getAccount().getInfo_pen_poly(), 18273));
        _actionMap.put("neiatun", pc -> applySimplePoly(pc, pc.getAccount().getInfo_atun_poly(), 18269));
        _actionMap.put("neimoon", pc -> applySimplePoly(pc, pc.getAccount().getInfo_moon_poly(), 19510));
        _actionMap.put("neimar",  pc -> applySimplePoly(pc, pc.getAccount().getInfo_mar_poly(), 18295));
        _actionMap.put("classmythm", pc -> { L1CardMythClass.polyclass(pc); return false; });

        // --- 고정 좌표 텔레포트 (장비 상점 등) ---
        _actionMap.put("neigea1", pc -> directTele(pc, 33488, 32782, 4));
        _actionMap.put("neigea2", pc -> directTele(pc, 33469, 32780, 4));
        _actionMap.put("neigea3", pc -> directTele(pc, 33457, 32782, 4));
        _actionMap.put("neigea4", pc -> directTele(pc, 33497, 32785, 4));
        _actionMap.put("neigea5", pc -> directTele(pc, 33479, 32783, 4));
        _actionMap.put("neigea6", pc -> directTele(pc, 33444, 32758, 4));
        _actionMap.put("neigea7", pc -> directTele(pc, 33416, 32776, 4));
        _actionMap.put("neigea8", pc -> directTele(pc, 33445, 32768, 4));
        _actionMap.put("neigea9", pc -> directTele(pc, 33447, 32773, 4));
        _actionMap.put("neigea11",pc -> directTele(pc, 33444, 32745, 4));
        _actionMap.put("neigea12",pc -> directTele(pc, 33444, 32764, 4));
        _actionMap.put("neigea13",pc -> directTele(pc, 32736, 32798, 16896));

        // --- 사냥터 랜덤 텔레포트 ---
        _actionMap.put("neiget01", pc -> rndTele(pc, 33054, 32984, 4, 3));      // 오우거밭
        _actionMap.put("neiget02", pc -> rndTele(pc, 32732, 32768, 812, 3));    // 본던6
        _actionMap.put("neiget03", pc -> rndTele(pc, 32777, 32942, 420, 3));    // 지저호수
        _actionMap.put("neiget04", pc -> directTele(pc, 33430, 32821, 4));      // 기감
        _actionMap.put("neiget05", pc -> rndTele(pc, 34251, 33452, 4, 2));      // 오만1
        _actionMap.put("neiget06", pc -> rndTele(pc, 32703, 32820, 36, 3));     // 용던6
        _actionMap.put("neiget07", pc -> rndTele(pc, 32937, 32277, 4, 2));      // 요던
        _actionMap.put("neiget08", pc -> rndTele(pc, 32671, 32794, 284, 2));     // 상아탑8
        _actionMap.put("neiget09", pc -> directTele(pc, 32735, 32446, 4));      // 화전민마을
        _actionMap.put("neiget10", pc -> directTele(pc, 32863, 32915, 304));    // 라스타바드
        _actionMap.put("neiget11", pc -> directTele(pc, 33693, 32495, 4));      // 지배결계
        _actionMap.put("neilava",  pc -> directTele(pc, 32833, 32889, 320));    // 디아드요새
        _actionMap.put("neiget12", pc -> { 
            if(pc.getLevel() < 60) { SC_SystemMessage.send(pc, "60레벨부터 입장이 가능합니다."); return false; }
            return directTele(pc, 32538, 32958, 777); 
        });
        _actionMap.put("neiget13", pc -> {
            if(pc.getLevel() < 83) { SC_SystemMessage.send(pc, "83레벨부터 입장이 가능합니다."); return false; }
            return directTele(pc, 34001, 32330, 4);
        });

        // --- Config 기반 특수 던전 ---
        _actionMap.put("neiger01", pc -> configTele(pc, Config.잊섬오픈, 32785, 32762, 1710, "잊혀진 섬", 3));
        _actionMap.put("neiger02", pc -> configTele(pc, Config.거무오픈, 32769, 32877, 624, "고대인의 무덤", 2));
        _actionMap.put("neiger05", pc -> configTele(pc, Config.풍둥오픈, 34150, 32793, 15410, "풍룡의 둥지", 3));
        _actionMap.put("neiger06", pc -> configTele(pc, Config.오설오픈, 34139, 32190, 15420, "오렌 설벽", 3));
        _actionMap.put("neiger07", pc -> configTele(pc, Config.화둥오픈, 33664, 32400, 15440, "화룡의 둥지", 3));
        _actionMap.put("neiger08", pc -> configTele(pc, Config.용계오픈, 33332, 32445, 15430, "용의 계곡", 3));
        _actionMap.put("neiger09", pc -> configTele(pc, Config.테베오픈, 32692, 32893, 10502, "테베라스", 3));

        // --- 기타 메뉴 및 게시판 ---
        _actionMap.put("neigeguide", pc -> { openBoardUI(pc, 4200015); return false; });
        _actionMap.put("neitrees",   pc -> { L1SkillTree.Info4(pc); return true; });
        _actionMap.put("neiherot",   pc -> { L1CardHeroList.cardinfo(pc); return false; });
        _actionMap.put("neilegendt", pc -> { L1CardLegendList.cardinfo(pc); return false; });
        _actionMap.put("neimytht",   pc -> { L1CardMythList.cardinfo(pc); return false; });
        _actionMap.put("neiyuilt",   pc -> { L1CardYuilList.showDetailedInfo(pc); return false; });
        _actionMap.put("neicolle",   pc -> { L1CardCollection.Info(pc); return true; });
        _actionMap.put("neicolle2",  pc -> { L1CardCollection.Info2(pc); return true; });
        _actionMap.put("neihelp",    pc -> { SeasonRewardHandler.showSeasonInfo(pc); return true; });
        _actionMap.put("prisondef5", pc -> { SeasonRewardHandler.claimLevelReward(pc); return true; });
        _actionMap.put("prisondef6", pc -> { SC_SkillSound.send(pc, pc.getId(), 21178); openBoardUI(pc, 4200013); return true; });
        _actionMap.put("a1-1",       pc -> { openBoardUI(pc, 4200015); return false; });
        
        // 스킬 트리 관련 추가
        _actionMap.put("neigetreem01", pc -> { L1SkillTree.Info(pc); return true; });
        _actionMap.put("neigetreem02", pc -> { L1SkillTree.Info2(pc); return true; });
        _actionMap.put("neigetreem03", pc -> { L1SkillTree.Info3(pc); return true; });
        _actionMap.put("neigetreem04", pc -> { L1SkillTree.Info5(pc); return true; });
    }

    /** 메인 실행 진입점 */
    public boolean handleAction(L1PcInstance pc, String actionStr) {
        if (pc == null) return false;
        if (pc.isGm()) SC_SystemMessage.send(pc, "실행 명령: " + actionStr);

        GameAction action = _actionMap.get(actionStr);
        if (action != null) {
            // 기본 클릭 사운드 (선택 사항)
            SC_SkillSound.send(pc, pc.getId(), 21174);
            return action.run(pc);
        }
        return false;
    }

    // --- 비즈니스 로직 헬퍼 메서드 ---

    private void resetCardInfo(L1PcInstance pc) {
        pc.getAccount().setInfo_card_a(0);
        pc.getAccount().setInfo_card_b(0);
        pc.getAccount().setInfo_card_c(0);
        pc.getAccount().setInfo_card_d(0);
    }

    private boolean applySimplePoly(L1PcInstance pc, int authCheck, int polyId) {
        if (authCheck != 0 && pc.Transform(polyId, 7200, TransformType.ITEM_MAGIC, true))
            soundAndBroadcast(pc, 6082);
        return false;
    }

    private boolean handleKirPolyAction(L1PcInstance pc) {
        if (!pc.getInventory().HaveItemByClassId(875640379, 1)) {
            SC_ChatPacket.sendDirect(pc, pc, "변신주문서가 부족합니다.");
            return false;
        }
        if (pc.isInvisble()) {
            return false;
        }
        if (pc.getAccount().getInfo_kir_poly() != 0) {
            int polyId = (pc.getCurrentSpriteId() != 21140) ? 21140 : 19514;
            CTransform.TransformWithCost(pc, polyId, -1, TransformType.ITEM_MAGIC, true,
                    875640379, 1, () -> soundAndBroadcast(pc, 6082));
        }
        return false;
    }

    private boolean directTele(L1PcInstance pc, int x, int y, int map) {
        pc.start_teleport(x, y, map, pc.getHeading(), 169, true, pc.getAutoHunt());
        return false;
    }

    private boolean rndTele(L1PcInstance pc, int x, int y, int map, int range) {
        int tx = x + GameRandom.nextInt(range);
        int ty = y + GameRandom.nextInt(range);
        pc.start_teleport(tx, ty, map, pc.getHeading(), 169, true, pc.getAutoHunt());
        return false;
    }

    private boolean configTele(L1PcInstance pc, int config, int x, int y, int map, String name, int range) {
        if (config == 1) {
            return rndTele(pc, x, y, map, range);
        } else {
            pc.sendPackets(name + "은(는) 현재 준비 중입니다.");
            return false;
        }
    }

    private void openBoardUI(L1PcInstance pc, int npcId) {
        L1NpcInstance board = CWorld.getInstance().findNpcByNpcId(npcId);
        if (board != null) SC_Board.send(pc, board);
    }

    private void soundAndBroadcast(L1PcInstance pc, int soundId) {
        SC_SkillSound.send(pc, pc.getId(), soundId);
        SC_SkillSound.broadcast(pc, pc.getId(), soundId);
    }

    /** 복잡한 요일별 스케줄 로직 */
    private boolean handleScheduleMenu(L1PcInstance pc) {
        ArrayList<String> res = new ArrayList<>();
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        
        // 요일별 시간표 데이터
        String[][] timeTable = {
            {}, 
            {"00/09/18시", "01/10/19시", "02/11/20시", "03/12/21시", "04/13/22시", "05/14/23시", "06/15시", "07/16시", "08/17시"}, // 일(1)
            {"06/15/21시", "07/16/22시", "08/17/23시", "00/09/18시", "01/10/19시", "02/11/20시", "03/12시", "04/13시", "05/14시"}, // 월(2)
            {"03/12/18시", "04/13/19시", "05/14/20시", "06/15/21시", "07/16/22시", "08/17/23시", "00/09시", "01/10시", "02/11시"}, // 화(3)
            {"00/09/15시", "01/10/16시", "02/11/17시", "03/12/18시", "04/13/19시", "05/14/20시", "06/21시", "07/22시", "08/23시"}, // 수(4)
            {"06/12/21시", "07/13/22시", "08/14/23시", "00/09/15시", "01/10/16시", "02/11/17시", "03/18시", "04/19시", "05/20시"}, // 목(5)
            {"03/09/18시", "04/10/19시", "05/11/20시", "06/12/21시", "07/13/22시", "08/14/23시", "00/15시", "01/16시", "02/17시"}, // 금(6)
            {"00/06/15시", "01/07/16시", "02/08/17시", "03/09/18시", "04/10/19시", "05/11/20시", "12/21시", "13/22시", "14/23시"}  // 토(7)
        };

        for (String t : timeTable[day]) res.add("   [" + t + "]");

        // 오픈 여부 
        int[] configs = {Config.잊섬오픈, Config.라스타오픈, Config.풍둥오픈, Config.오설오픈, Config.화둥오픈, Config.용계오픈, Config.테베오픈, Config.지배오픈, Config.거무오픈};
        for (int c : configs) res.add(c == 1 ? "401" : "399");

        SC_NPCTalkReturn.sendList(pc, pc.getId(), "neigetele2", res);
        return true;
    }
}