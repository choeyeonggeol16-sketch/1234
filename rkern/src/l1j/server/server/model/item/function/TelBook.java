package l1j.server.server.model.item.function;

import static l1j.server.server.model.character.skill.L1SkillId.BONE_BREAK;
import static l1j.server.server.model.character.skill.L1SkillId.EARTH_BIND;
import static l1j.server.server.model.character.skill.L1SkillId.ICE_LANCE;
import static l1j.server.server.model.character.skill.L1SkillId.SHOCK_STUN;
import static l1j.server.server.model.character.skill.L1SkillId.THUNDER_GRAB;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class TelBook {
    private static final int[][] TOWN_BOOK = { 
						{ 34060, 32281, 4 }, // 오렌
						{ 33079, 33390, 4 }, // 은기사
						{ 32750, 32439, 4 }, // 오크숲
						{ 32612, 33188, 4 }, // 윈다우드
						{ 33720, 32492, 4 }, // 웰던
						{ 32872, 32912, 304 }, // 침묵의 동굴
						{ 32612, 32781, 4 }, // 글루디오
						{ 33067, 32803, 4 }, // 켄트
						{ 33933, 33358, 4 }, // 아덴
						{ 33601, 33232, 4 }, // 하이네
						{ 32574, 32942, 0 }, // 말하는 섬
						{ 33430, 32815, 4 }, };

    private static final int[][] DUNGEON_BOOK = { 
						/** 저 레벨 추천 사냥터 **/
						{ 34266, 32190, 4 }, // 그신입구
						{ 32507, 32924, 9 }, // 말섬 북쪽 섬
						{ 32491, 32854, 9 }, // 말섬 던전 입구
						{ 32409, 32938, 9 }, // 말섬 오크 망루 지대
						{ 32883, 32647, 4 }, // 본토 죽음의 폐허
						{ 32875, 32927, 4 }, // 본토 망자의 무덤
						{ 32726, 32928, 4 }, // 글던1층입구
						{ 32764, 32840, 77 }, // 상아탑4층
						{ 32708, 33150, 9 }, // 말섬 흑기사 전초 기지
						{ 32599, 32289, 4 }, // 본토 오크 부락
						{ 32908, 33222, 4 }, // 본토 사막(에르자베)
						{ 32761, 33167, 4 }, // 본토 사막(샌드웜)
						{ 32806, 32726, 19 }, // 요정 숲 던전 1층
						{ 32796, 32753, 809 }, // 글던3층입구
						{ 33429, 32826, 4 }, // 기란 감옥 입구
						{ 32809, 32729, 25 }, // 수련 던전 1층
						/** 중 레벨 추천 사냥터 **/
						{ 32745, 32427, 4 }, // 무시
						{ 33764, 33314, 4 }, // 본토 거울의 숲
						{ 33804, 32966, 4 }, // 본토 밀림 지대
						{ 32710, 32790, 59 }, // 에바왕국 1층
						{ 34251, 33453, 4 }, // 오만의 탑 입구
						{ 32811, 32909, 4 }, // 본토 흑기사 출몰 지역
						{ 32766, 32798, 20 }, // 요정 숲 던전 2층
						{ 32726, 32808, 61 }, // 에바 왕국 3층
						{ 32809, 32808, 30 }, // 용의 던전 1층
						{ 32809, 32767, 27 }, // 수련던전 3층
						{ 32801, 32928, 800 }, //
						/** 고 레벨 추천 사냥터 **/
						{ 32705, 32822, 32 }, // 용의 계곡 던전 3층
						{ 33436, 33475, 4 }, // 하이네 잊혀진 섬 배표소
						{ 33182, 33006, 4 }, // 본토 암흑용의 상흔
						{ 34126, 32799, 4 }, // 본토 풍룡의 둥지
						{ 34126, 32192, 4 }, // 본토 오렌 설벽
						{ 33331, 32459, 4 }, // 본토 용의 계곡 입구
						{ 34051, 32561, 4 }, // 본토 엘모어 격전지
						{ 33643, 32419, 4 }, // 본토 화룡의 둥지 입구
				};
    private static final int[][] OMAN_BOOK = { 
						{ 32735, 32798, 101 }, // 오만1
						{ 32727, 32803, 102 }, // 오만2
						{ 32726, 32803, 103 }, // 오만3
						{ 32620, 32859, 104 }, // 오만4
						{ 32601, 32866, 105 }, // 오만5
						{ 32611, 32863, 106 }, // 오만6
						{ 32618, 32866, 107 }, // 오만7
						{ 32602, 32867, 108 }, // 오만8
						{ 32613, 32866, 109 }, // 오만9
						{ 32730, 32803, 110 }, // 오만10
						{ 32646, 32808, 111 }, // 오만정상 시작지점
						{ 32801, 32963, 111 },};

    private static final int[][] ENCOUNTER_BOOK = {
						{ 0, 0, 0 }, /** 저 레벨 추천 사냥터 **/
						{ 32643, 32841, 9 }, // 말섬 북쪽 섬
						{ 32477, 32855, 9 }, // 말섬 던전 입구
						{ 32437, 32911, 9 }, // 말섬 오크 망루 지대
						{ 32874, 32653, 4 }, // 죽음의 폐허
						{ 32879, 32895, 4 }, // 망자의 무덤
						{ 32724, 32925, 4 }, // 글루디오던전 입구1층
						{ 32764, 32841, 77 }, // 상아탑 4층입구
						{ 32706, 33153, 9 }, // 말섬 흑기사 전초 기지
						{ 32723, 32398, 4 }, // 본토 오크 부락
						{ 32895, 33242, 4 }, // 에르자베
						{ 32767, 33164, 4 }, // 샌드웜
						{ 32937, 32277, 4 }, // 요정 숲 던전 1층 입구
						{ 32798, 32754, 809 }, // 글루디오던전 입구3층
						{ 33430, 32825, 4 }, // 기란감옥 1층 입구
						{ 32808, 32730, 25 }, // 수련던전 1층 입구
						
						{ 0, 0, 0 }, /** 중 레벨 추천 사냥터 **/
						{ 33768, 33312, 4 }, // 본토 거울숲 지대
						{ 33800, 32844, 4 }, // 본토 밀림지대
						{ 33628, 33505, 4 }, // 에바 왕국 1층 입구
						{ 34247, 33453, 4 }, // 오만의 탑 입구 1층
						{ 32900, 32909, 4 }, // 본토 흑기사 출몰 지역
						{ 32766, 32796, 20 }, // 요정 숲 던전 3층 입구
						{ 32726, 32807, 61 }, // 에바왕국 3층
						{ 32809, 32808, 30 }, // 용의 던전 1층
						{ 32809, 32767, 27 }, // 수련던전 3층
						
						{ 0, 0, 0 }, /** 고 레벨 추천 사냥터 **/
						{ 32706, 32821, 32 }, // 용의 던전 3층
						{ 33432, 33497, 4 }, // 하이네 잊혀진섬 배표
						{ 33159, 32969, 4 }, // 암흑룡의 상흔
						{ 34125, 32799, 4 }, // 본토 풍룡의 둥지 입구
						{ 34127, 32192, 4 }, // 본토 얼음 설벽 입구
						{ 33330, 32458, 4 }, // 본토 용의 계곡 입구
						{ 34056, 32547, 4 }, // 본토 엘모어 격전지
						{ 33645, 32418, 4 },  };

    private static final int[][] RULE_TALISMAN = { 
						{ 32728, 32800, 12852 }, // 지배의 탑 1층
						{ 32721, 32803, 12853 }, // 지배의 탑 2층
						{ 32724, 32802, 12854 }, // 지배의 탑 3층
						{ 32597, 32863, 12855 }, // 지배의 탑 4층
						{ 32592, 32866, 12856 }, // 지배의 탑 5층
						{ 32602, 32865, 12857 }, // 지배의 탑 6층
						{ 32604, 32866, 12858 }, // 지배의 탑 7층
						{ 32593, 32866, 12859 }, // 지배의 탑 8층
						{ 32602, 32866, 12860 }, // 지배의 탑 9층
						{ 32732, 32802, 12861 }, // 지배의 탑 10층
						};
    private TelBook() {}

    public static void clickItem(L1PcInstance pc, int itemId, int bookTel, L1ItemInstance item) {
        if (pc == null || item == null || item.getItemId() != itemId
                || pc.isParalyzed() || pc.isSleeped() || pc.isDead()) return;
        if (!pc.getMap().isEscapable()) {
            SC_SystemMessage.send(pc, "주위의 알수없는 마력에의해 텔레포트를 할 수 없습니다.");
            return;
        }
        if (pc.hasSkillEffect(SHOCK_STUN) || pc.hasSkillEffect(L1SkillId.EMPIRE)
                || pc.hasSkillEffect(ICE_LANCE) || pc.hasSkillEffect(BONE_BREAK)
                || pc.hasSkillEffect(THUNDER_GRAB) || pc.hasSkillEffect(EARTH_BIND)
                || pc.hasSkillEffect(L1SkillId.이터디스) || pc.hasSkillEffect(L1SkillId.쉐도우제약)) return;
        if (pc.hasSkillEffect(L1SkillId.버닝샷)) {
            SC_SystemMessage.send(pc, "\\fY버닝샷중에는 이동 및 귀환이 불가합니다.");
            return;
        }

        final int[][] destinations;
        final boolean consumable;
        switch (itemId) {
        case 560025: destinations = TOWN_BOOK; consumable = true; break;
        case 560027: destinations = DUNGEON_BOOK; consumable = true; break;
        case 560028: destinations = OMAN_BOOK; consumable = false; break;
        case 560029: destinations = ENCOUNTER_BOOK; consumable = true; break;
        case 4100135: destinations = RULE_TALISMAN; consumable = false; break;
        default: return;
        }
        // A malformed index, or one of the old menu heading rows (0/0/0),
        // must never consume a scroll or request a teleport to invalid tiles.
        if (bookTel < 0 || bookTel >= destinations.length) return;
        final int[] dest = destinations[bookTel];
        if (dest[0] == 0 && dest[1] == 0) return;
        if (consumable) {
            ItemReceiptOperations.UseDurableConsume(pc, item, () -> {
                try {
                    pc.start_teleport(dest[0], dest[1], dest[2],
                            pc.getHeading(), 169, true, true);
                } finally {
                    ItemActionCompletion.finishCommittedConsumable(pc, item);
                }
            });
        } else if (pc.getInventory().getItem(item.getId()) == item) {
            pc.start_teleport(dest[0], dest[1], dest[2],
                    pc.getHeading(), 169, true, true);
        }
    }
}
