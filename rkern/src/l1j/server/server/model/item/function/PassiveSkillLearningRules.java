package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillBrave;
import l1j.server.server.model.item.function.ItemLearningRule.Gate;
import l1j.server.server.model.item.function.ItemLearningRule.Requirement;
import static l1j.server.server.model.item.function.ItemLearningRule.*;

/** Item-use behavior extracted without changing invocation order. */
final class PassiveSkillLearningRules {
    private PassiveSkillLearningRules() {}



    private static final ItemLearningRule 램페이지 = passive(L1SkillId.램페이지, 0, Gate.DRAGON_KNIGHT_1, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [램페이지] 패시브를 배울 수 없는 상태입니다");

    private static final ItemLearningRule 래스트 = passive(L1SkillId.래스트, 0, Gate.ILLUSIONIST_1, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [본브레이크:래스트] 패시브를 배울 수 없는 상태입니다");

    private static final ItemLearningRule 에테리얼 = passive(L1SkillId.에테리얼, 0, Gate.WIZARD, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [에테리얼 서클] 패시브를 배울 수 없는 상태입니다");

    private static final ItemLearningRule 에볼루션 = passive(L1SkillId.에볼루션, 0, Gate.WIZARD_1, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [홀리워크:에볼루션] 패시브를 배울 수 없는 상태입니다");

    private static final ItemLearningRule 네메시스 = passive(L1SkillId.네메시스, 0, Gate.WIZARD_1, Requirement.DISINTEGRATE,
            "이미 습득한 스킬입니다.", "현재 [디스인티그레이트:네메시스]를 배울 수 없는 조건입니다");

    private static final ItemLearningRule 맥시멈 = passive(L1SkillId.맥시멈, 0, Gate.DARK_ELF_1, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [무빙 악셀레이션:맥시멈] 패시브를 배울 수 없는 상태입니다");

    private static final ItemLearningRule 아머데스티니 = passive(L1SkillId.아머데스티니, 0, Gate.DARK_ELF_1, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [쉐도우 아머:데스티니] 패시브를 배울 수 없는 상태입니다");

    private static final ItemLearningRule 기간틱 = passive(L1SkillId.기간틱, 0, Gate.WARRIOR, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [기간틱] 패시브를 배울 수 없는 조건입니다");

    private static final ItemLearningRule 엠파이어오버로드 = passive(L1SkillId.엠파이어오버로드, 0, Gate.CROWN_1, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [엠파이어:오버로드] 패시브를 배울 수 없는 상태입니다");

    private static final ItemLearningRule 샤이닝아머 = passive(L1SkillId.샤이닝아머, 0, Gate.CROWN_1, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [샤이닝 아머] 패시브를 배울 수 없는 상태입니다");

    private static final ItemLearningRule 마제스티 = passive(L1SkillId.마제스티, 0, Gate.CROWN_1, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [마제스티] 패시브를 배울 수 없는 상태입니다");

    private static final ItemLearningRule 포스스턴 = passive(L1SkillId.포스스턴, 0, Gate.KNIGHT_1, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [포스 스턴] 패시브를 배울 수 없는 상태입니다");

    private static final ItemLearningRule 레이징포스 = passive(L1SkillId.레이징포스, 0, Gate.KNIGHT_1, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [레이징 포스] 패시브를 배울 수 없는 상태입니다");

    private static final ItemLearningRule 세인트 = passive(L1SkillId.세인트, 0, Gate.WIZARD_80, Requirement.IMMUNE,
            "이미 습득한 스킬입니다.", "현재 [이뮨투함:세인트]를 배울 수 없는 조건입니다");

    private static final ItemLearningRule 가드피어 = passive(L1SkillId.가드피어, 0, Gate.DRAGON_KNIGHT, Requirement.NO_DESTROY_HORROR,
            "이미 습득한 스킬입니다.", "현재 [디스트로이:피어]를 배울 수 없는 조건입니다");

    private static final ItemLearningRule 가드피어호러 = passive(L1SkillId.가드피어호러, L1SkillId.가드피어, Gate.DRAGON_KNIGHT, Requirement.DESTROY_FEAR,
            "이미 습득한 스킬입니다.", "현재 [디스트로이:호러]를 배울 수 없는 조건입니다");

    private static final ItemLearningRule 카배마스터 = passive(L1SkillId.카배마스터, 0, Gate.KNIGHT_86, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [카운터 배리어:마스터]를 배울 수 없는 조건입니다");

    private static final ItemLearningRule 게일샷 = passive(L1SkillId.게일샷, 0, Gate.GALE_85, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [스트라이커 게일:샷]을 배울 수 없는 조건입니다");

    private static final ItemLearningRule 비욘드 = passive(L1SkillId.비욘드, 0, Gate.WIZARD_85, Requirement.NONE,
            "이미 습득한 스킬입니다.", "현재 [메디테이션:비욘드]를 배울 수 없는 조건입니다");

    private static final ItemLearningRule 버닝 = passive(L1SkillId.버닝, 0, Gate.DARK_ELF, Requirement.NONE,
            "이미 습득한 스킬입니다.", "현재 [버닝 스피릿츠]를 배울 수 없는 조건입니다");

    private static final ItemLearningRule 이베이젼 = passive(L1SkillId.이베이젼, 0, Gate.DARK_ELF, Requirement.NONE,
            "이미 습득한 스킬입니다.", "현재 [드레스 이베이젼]을 배울 수 없는 조건입니다");

    private static final ItemLearningRule 일루션리치 = passive(L1SkillId.일루션리치, 0, Gate.ILLUSIONIST, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [일루션:리치]를 배울 수 없는 조건입니다");

    private static final ItemLearningRule 일루션골렘 = passive(L1SkillId.일루션골렘, 0, Gate.ILLUSIONIST, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [일루션:골렘]을 배울 수 없는 조건입니다");

    private static final ItemLearningRule 솔리드캐리지 = passive(L1SkillId.솔리드캐리지, 0, Gate.KNIGHT, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [솔리드 캐리지]을 배울 수 없는 조건입니다");

    private static final ItemLearningRule 리덕션베테랑 = passive(L1SkillId.리덕션베테랑, 0, Gate.KNIGHT, Requirement.REDUCTION_ARMOR,
            "이미 습득한 패시브입니다.", "현재 [리덕션아머:베테랑]을 배울 수 없는 조건입니다");

    private static final ItemLearningRule 어드밴스 = passive(L1SkillId.어드밴스, 0, Gate.WIZARD, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [어드밴스 스피릿]을 배울 수 없는 조건입니다");

    private static final ItemLearningRule 프라이드 = passive(L1SkillId.프라이드, 0, Gate.KNIGHT, Requirement.NONE,
            "이미 습득한 패시브입니다.", "현재 [프라이드]을 배울 수 없는 조건입니다");



    static ItemLearningRule ruleFor(int itemId) {
        return switch (itemId) {
            case 70701 -> 램페이지;
            case 70702 -> 래스트;
            case 80805 -> 에테리얼;
            case 80804 -> 에볼루션;
            case 70716 -> 네메시스;
            case 70704 -> 맥시멈;
            case 70705 -> 아머데스티니;
            case 210122 -> 기간틱;
            case 50512 -> 엠파이어오버로드;
            case 40228 -> 샤이닝아머;
            case 40231 -> 마제스티;
            case 70712 -> 포스스턴;
            case 70713 -> 레이징포스;
            case 70717 -> 세인트;
            case 70718 -> 가드피어;
            case 70719 -> 가드피어호러;
            case 10889 -> 카배마스터;
            case 10888 -> 게일샷;
            case 70732 -> 비욘드;
            case 40271 -> 버닝;
            case 40279 -> 이베이젼;
            case 210008 -> 일루션리치;
            case 210013 -> 일루션골렘;
            case 41147 -> 솔리드캐리지;
            case 70720 -> 리덕션베테랑;
            case 40224 -> 어드밴스;
            case 4100100 -> 프라이드;
            default -> null;
        };
    }
}
