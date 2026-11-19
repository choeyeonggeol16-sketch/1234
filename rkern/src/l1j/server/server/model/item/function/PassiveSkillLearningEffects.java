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
final class PassiveSkillLearningEffects {
    private PassiveSkillLearningEffects() {}



    static Runnable effectFor(L1PcInstance pc, int itemId) {
        return switch (itemId) {
            case 70701 -> () -> {
                pc.램페이지(true);
                finishPassive(pc, L1SkillId.램페이지);
            };
            case 70702 -> () -> {
                pc.래스트(true);
                finishPassive(pc, L1SkillId.래스트);
            };
            case 80805 -> () -> {
                pc.에테리얼(true);
                finishPassive(pc, L1SkillId.에테리얼);
            };
            case 80804 -> () -> {
                finishPassive(pc, L1SkillId.에볼루션);
                if (pc.hasSkillEffect(L1SkillId.HOLY_WALK)) {
                    pc.removeSkillEffect(L1SkillId.HOLY_WALK);
                    pc.setSkillEffect(L1SkillId.STATUS_ELFBRAVE, 300 * 1000);
                    SC_SkillBrave.send(pc, pc.getId(), 3, 300 * 1000);
                    SC_SkillBrave.broadcast(pc, pc.getId(), 3, 0);
                    SC_InventoryIcon.icoNew(pc, L1SkillId.에볼루션, 3907, 300, true);
                    pc.setBraveSpeed(1);
                }
                SC_SystemMessage.send(pc, "\\aH홀리워크 사용 시 이동 및 공격속도가 증가됩니다.");
            };
            case 70716 -> () -> {
                pc.네메시스(true);
                finishPassive(pc, L1SkillId.네메시스);
            };
            case 70704 -> () -> {
                finishPassive(pc, L1SkillId.맥시멈);
                if (pc.hasSkillEffect(L1SkillId.MOVING_ACCELERATION)) {
                    pc.removeSkillEffect(L1SkillId.MOVING_ACCELERATION);
                    pc.setSkillEffect(L1SkillId.STATUS_ELFBRAVE, 960 * 1000);
                    SC_SkillBrave.send(pc, pc.getId(), 3, 960 * 1000);
                    SC_SkillBrave.broadcast(pc, pc.getId(), 3, 0);
                    SC_InventoryIcon.icoNew(pc, L1SkillId.맥시멈, 3902, 960, true);
                    pc.setBraveSpeed(1);
                }
                SC_SystemMessage.send(pc, "\\aH무빙악셀레이션 사용 시 공격속도가 증가됩니다.");
            };
            case 70705 -> () -> {
                finishPassive(pc, L1SkillId.아머데스티니);
                SC_SystemMessage.send(pc, "\\aH[쉐도우 아머:데스티니]를 습득하였습니다.");
                SC_SystemMessage.send(pc, "\\aH쉐도우 아머를 사용 시 데스티니의 효과가 지속됩니다.");
            };
            case 210122 -> () -> {
                SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
                pc.기간틱(true);
                finishPassive(pc, L1SkillId.기간틱);
                SC_SystemMessage.send(pc, "\\aH[기간틱] 패시브를 습득하였습니다.");
            };
            case 50512 -> () -> {
                pc.엠파이어오버로드(true);
                finishPassive(pc, L1SkillId.엠파이어오버로드);
            };
            case 40228 -> () -> {
                pc.샤이닝아머(true);
                finishPassive(pc, L1SkillId.샤이닝아머);
            };
            case 40231 -> () -> {
                pc.마제스티(true);
                finishPassive(pc, L1SkillId.마제스티);
            };
            case 70712 -> () -> {
                pc.포스스턴(true);
                finishPassive(pc, L1SkillId.포스스턴);
            };
            case 70713 -> () -> {
                pc.sendPackets("\\aH[레이징 포스] 패시브를 습득하였습니다.");
                pc.sendPackets("\\aH80레벨부터 4레벨마다 PVP 대미지 감소 +1 추가");
                pc.setSkillEffect(L1SkillId.레이징포스, -1);
                SC_InventoryIcon.iconNewUnLimit(pc, L1SkillId.레이징포스, 6082, true);
                playLearnSound(pc);
            };
            case 70717 -> () -> {
                finishPassive(pc, L1SkillId.세인트);
                SC_SystemMessage.send(pc, "\\aH[이뮨 투함:세인트]를 습득하였습니다.");
                SC_SystemMessage.send(pc, "\\aH이뮨 투 함을 사용 시 세인트의 효과가 지속됩니다.");
            };
            case 70718 -> () -> {
                pc.가드피어(true);
                finishPassive(pc, L1SkillId.가드피어);
                SC_SystemMessage.send(pc, "\\aH[디스트로이:피어]를 습득하였습니다.");
                SC_SystemMessage.send(pc, "\\aH가드 브레이크를 사용 시 디스트로이:피어 효과가 지속됩니다.");
            };
            case 70719 -> () -> {
                if (pc.hasSkillEffect(L1SkillId.가드피어)) {
                    SC_InventoryIcon.icoEnd(pc, L1SkillId.가드피어);
                    pc.removeSkillEffect(L1SkillId.가드피어);
                }
                pc.가드피어호러(true);
                finishPassive(pc, L1SkillId.가드피어호러);
                SC_SystemMessage.send(pc, "\\aH[디스트로이:호러]를 습득하였습니다.");
                SC_SystemMessage.send(pc, "\\aH가드 브레이크를 사용 시 디스트로이:호러 효과가 지속됩니다.");
            };
            case 10889 -> () -> {
                finishPassive(pc, L1SkillId.카배마스터);
                SC_SystemMessage.send(pc, "\\aH[카운터 배리어:마스터]를 습득하였습니다.");
                SC_SystemMessage.send(pc, "\\aH카운터 배리어 발동 시 HP의 일정량을 회복 합니다.");
            };
            case 10888 -> () -> {
                SC_InventoryIcon.iconNewUnLimit(pc, L1SkillId.게일샷, 6913, true);
                finishPassive(pc, L1SkillId.게일샷);
                SC_SystemMessage.send(pc, "\\aH[스트라이커 게일:샷]을 습득하였습니다.");
                SC_SystemMessage.send(pc, "\\aH스트라이커 게일 사용 시 기존 효과 및 게일 적중 확률 증가, 홀드효과, 치유 회복 효과 감소가 추가됩니다.");
            };
            case 70732 -> () -> {
                pc.비욘드(true);
                finishPassive(pc, L1SkillId.비욘드);
                SC_SystemMessage.send(pc, "\\aH[메디테이션:비욘드]를 습득하였습니다.");
            };
            case 40271 -> () -> {
                pc.버닝(true);
                finishPassive(pc, L1SkillId.버닝);
                SC_SystemMessage.send(pc, "\\aH[버닝 스피릿츠]를 습득하였습니다.");
            };
            case 40279 -> () -> {
                pc.이베이젼(true);
                finishPassive(pc, L1SkillId.이베이젼);
                SC_SystemMessage.send(pc, "\\aH[드레스 이베이젼]을 습득하였습니다.");
            };
            case 210008 -> () -> {
                pc.일루션리치(true);
                finishPassive(pc, L1SkillId.일루션리치);
                SC_SystemMessage.send(pc, "\\aH[일루션:리치]를 습득하였습니다.");
            };
            case 210013 -> () -> {
                pc.일루션골렘(true);
                finishPassive(pc, L1SkillId.일루션골렘);
                SC_SystemMessage.send(pc, "\\aH[일루션:골렘]을 습득하였습니다.");
            };
            case 41147 -> () -> {
                pc.솔리드캐리지(true);
                finishPassive(pc, L1SkillId.솔리드캐리지);
                SC_SystemMessage.send(pc, "\\aH[솔리드 캐리지]를 습득하였습니다.");
            };
            case 70720 -> () -> {
                if (pc.hasSkillEffect(L1SkillId.REDUCTION_ARMOR)) {
                    pc.removeSkillEffect(L1SkillId.REDUCTION_ARMOR);
                }
                finishPassive(pc, L1SkillId.리덕션베테랑);
                SC_SystemMessage.send(pc, "\\aH[리덕션아머:베테랑]을 습득하였습니다.");
                SC_SystemMessage.send(pc, "\\aH리덕션 아머 사용 시 공포내성 +3 효과가 적용됩니다.");
            };
            case 40224 -> () -> {
                if (pc.hasSkillEffect(L1SkillId.ADVANCE_SPIRIT)) {
                    pc.removeSkillEffect(L1SkillId.ADVANCE_SPIRIT);
                }
                pc.어드밴스(true);
                finishPassive(pc, L1SkillId.어드밴스);
                SC_SystemMessage.send(pc, "\\aH[어드밴스 스피릿]이 적용되었습니다.");
            };
            case 4100100 -> () -> {
                SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
                pc.프라이드(true);
                finishPassive(pc, L1SkillId.프라이드);
                SC_SystemMessage.send(pc, "\\aH[프라이드]를 습득하였습니다.");
            };
            default -> throw new IllegalArgumentException("Unknown learning item: " + itemId);
        };
    }
}
