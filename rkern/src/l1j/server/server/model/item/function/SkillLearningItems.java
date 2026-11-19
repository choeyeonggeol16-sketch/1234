package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.item.function.ItemLearningRule.Gate;
import static l1j.server.server.model.item.function.ItemLearningRule.*;

/** Item rules are immutable; callbacks retain only the player until the existing commit completes. */
final class SkillLearningItems {
    private SkillLearningItems() {}

    private static final ItemLearningRule VISION_TELEPORT = exchange(90088, Gate.WIZARD, true, false,
            "이미 스킬이 있으므로 획득할 수 없습니다.", "현재 [비전 텔레포트]를 배울 수 없는 상태입니다");

    private static final ItemLearningRule ETERNITY = exchange(80888, Gate.WIZARD_1, true, false,
            "이미 이터니티 스킬이 있으므로 획득할 수 없습니다.", "현재 [이터니티]를 배울 수 없는 상태입니다");

    private static final ItemLearningRule BERSERK = exchange(70706, Gate.WARRIOR_1, false, false,
            "이미 버서크 스킬이 있으므로 획득할 수 없습니다.", "조건이 맞지 않아 버서크를 획득 할 수 없습니다.");

    private static final ItemLearningRule MASS_IMMUNE = exchange(70733, Gate.WIZARD_1, true, false,
            "이미 매스 이뮨 투 함 스킬이 있으므로 획득할 수 없습니다.", "조건이 맞지 않아 매스 이뮨 투 함을 획득 할 수 없습니다.");

    private static final ItemLearningRule PRIME = exchange(70708, Gate.CROWN_85, false, false,
            "이미 프라임 스킬이 있으므로 획득할 수 없습니다.", "조건이 맞지 않아 프라임을 획득 할 수 없습니다.");

    private static final ItemLearningRule HALPHAS = exchange(70710, Gate.DRAGON_KNIGHT_1, false, false,
            "이미 할파스 스킬이 있으므로 획득할 수 없습니다.", "조건이 맞지 않아 할파스를 획득 할 수 없습니다.");

    private static final ItemLearningRule POTENTIAL = exchange(70714, Gate.ILLUSIONIST_85, false, false,
            "이미 포텐셜 스킬이 있으므로 획득할 수 없습니다.", "조건이 맞지 않아 포텐셜을 획득 할 수 없습니다.");

    private static final ItemLearningRule CALL_CLAN_ADVANCE = exchange(70721, Gate.CROWN_ADVANCE, true, false,
            "이미 습득한 스킬입니다.", "현재 [콜 클랜 어드밴스]를 배울 수 없는 조건입니다");

    private static final ItemLearningRule BURNING_SHOT = exchange(44001, Gate.ELF_85, true, false,
            "이미 습득한 스킬입니다.", "현재 [버닝 샷]을 배울 수 없는 조건입니다");

    private static final ItemLearningRule LIBERATION = exchange(70735, Gate.ELF_85, true, false,
            "이미 리버레이션 스킬이 있으므로 획득할 수 없습니다.", "현재 [리버레이션]을 배울 수 없는 조건입니다");

    private static final ItemLearningRule MAGIC_SHIELD = exchange(220012, Gate.ELF_85, true, false,
            "이미 습득한 스킬입니다.", "현재 [매직실드]를 배울 수 없는 조건입니다");

    private static final ItemLearningRule ELVEN_STRIKE = exchange(70888, Gate.ELF_85, true, false,
            "이미 습득한 스킬입니다.", "현재 [엘븐 스트라이크]를 배울 수 없는 조건입니다");

    private static final ItemLearningRule SHADOW_STEP = exchange(707777, Gate.DARK_ELF_80, true, false,
            "이미 습득한 스킬입니다.", "현재 [쉐도우 스텝]을 배울 수 없는 조건입니다");

    private static final ItemLearningRule AVENGER = exchange(707779, Gate.DARK_ELF_80, true, false,
            "이미 습득한 스킬입니다.", "현재 [어벤저]를 배울 수 없는 조건입니다");

    private static final ItemLearningRule DEMOLITION = exchange(707781, Gate.WARRIOR_85, true, false,
            "이미 습득한 스킬입니다.", "현재 [데몰리션]을 배울 수 없는 조건입니다");

    private static final ItemLearningRule IMMUNE_BLADE = exchange(70740, Gate.IMMUNE_BLADE, true, true,
            "이미 습득한 스킬입니다.", "현재 [이뮨 투 함 블레이드]를 배울 수 없는 조건입니다");

    static void use(L1PcInstance pc, L1ItemInstance item, int itemId) {
        ItemLearningRule rule = ruleFor(itemId);
        if (rule != null && rule.canLearn(pc)) rule.learn(pc, item, effectFor(pc, itemId));
    }

    private static ItemLearningRule ruleFor(int itemId) {
        return switch (itemId) {
            case 90905 -> VISION_TELEPORT;
            case 80889 -> ETERNITY;
            case 70707 -> BERSERK;
            case 70734 -> MASS_IMMUNE;
            case 70709 -> PRIME;
            case 70711 -> HALPHAS;
            case 70715 -> POTENTIAL;
            case 70722 -> CALL_CLAN_ADVANCE;
            case 44002 -> BURNING_SHOT;
            case 70730 -> LIBERATION;
            case 220013 -> MAGIC_SHIELD;
            case 70731 -> ELVEN_STRIKE;
            case 707778 -> SHADOW_STEP;
            case 707780 -> AVENGER;
            case 707782 -> DEMOLITION;
            case 70741 -> IMMUNE_BLADE;
            default -> null;
        };
    }

    private static Runnable effectFor(L1PcInstance pc, int itemId) {
        return switch (itemId) {
            case 90905 -> () -> {
                playLearnSound(pc);
            };
            case 80889 -> () -> {
                playLearnSound(pc);
                pc.sendPackets("\\f3이터니티를 습득하였습니다.");
                pc.sendPackets("\\f3[첫번째 공격은 카운터 매직 무시. 확률적 적중 시 홀드 및 2차 데미지]");
            };
            case 70707 -> () -> {
                pc.sendPackets("\\aH전사의 인장 (버서크)를 획득하였습니다.");
            };
            case 70734 -> () -> {
                playLearnSound(pc);
                pc.sendPackets("\\aH마법서 (매스 이뮨 투 함)을 획득하였습니다.");
            };
            case 70709 -> () -> {
                playLearnSound(pc);
                pc.sendPackets("\\aH마법서 (프라임)을 획득하였습니다.");
            };
            case 70711 -> () -> {
                playLearnSound(pc);
                pc.sendPackets("\\aH[할파스]를 획득하였습니다.");
            };
            case 70715 -> () -> {
                pc.sendPackets("\\aH[포텐셜]을 획득하였습니다.");
            };
            case 70722 -> () -> {
                playLearnSound(pc);
                SC_SystemMessage.send(pc, "\\aH[콜 클랜 어드밴스]를 습득하였습니다.");
            };
            case 44002 -> () -> {
                playLearnSound(pc);
                SC_SystemMessage.send(pc, "\\aH[버닝 샷]을 습득하였습니다.");
            };
            case 70730 -> () -> {
                playLearnSound(pc);
                SC_SystemMessage.send(pc, "\\aH[리버레이션]을 습득하였습니다.");
            };
            case 220013 -> () -> {
                playLearnSound(pc);
                SC_SystemMessage.send(pc, "\\aH[매직실드]를 습득하였습니다.");
            };
            case 70731 -> () -> {
                playLearnSound(pc);
                SC_SystemMessage.send(pc, "\\aH[엘븐 스트라이크]를 습득하였습니다.");
            };
            case 707778 -> () -> {
                playLearnSound(pc);
                SC_SystemMessage.send(pc, "\\aH[쉐도우 스텝]을 습득하였습니다.");
            };
            case 707780 -> () -> {
                playLearnSound(pc);
                SC_SystemMessage.send(pc, "\\aH[어벤저]를 습득하였습니다.");
            };
            case 707782 -> () -> {
                playLearnSound(pc);
                SC_SystemMessage.send(pc, "\\aH[데몰리션]을 습득하였습니다.");
            };
            case 70741 -> () -> {
                playLearnSound(pc);
                SC_SystemMessage.send(pc, "\\aH[이뮨 투 함 블레이드]를 습득하였습니다.");
            };
            default -> throw new IllegalArgumentException("Unknown learning item: " + itemId);
        };
    }
}
