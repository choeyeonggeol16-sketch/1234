package l1j.server.server.model.character.effect;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

/**
 * Passive stat changes, party refreshes and icons in their original order. 
 */
public final class PcPassiveEffects {
    
    private PcPassiveEffects() {
    }
    
    public enum Kind {
        포우브레이브 (L1SkillId.포우브레이브, 3890),
        램페이지 (L1SkillId.램페이지, 3891),
        래스트 (L1SkillId.래스트, 3889),
        엠파이어오버로드 (L1SkillId.엠파이어오버로드, 5399),
        기간틱 (L1SkillId.기간틱, 5405),
        샤이닝아머 (L1SkillId.샤이닝아머, 5400),
        마제스티 (L1SkillId.마제스티, 5401),
        포스스턴 (L1SkillId.포스스턴, 5385),
        네메시스 (L1SkillId.네메시스, 5375),
        가드피어 (L1SkillId.가드피어, 5096),
        가드피어호러 (L1SkillId.가드피어호러, 5097),
        일루션리치 (L1SkillId.일루션리치, 5477),
        일루션골렘 (L1SkillId.일루션골렘, 5478),
        솔리드캐리지 (L1SkillId.솔리드캐리지, 5115),
        버닝 (L1SkillId.버닝, 6080),
        이베이젼 (L1SkillId.이베이젼, 6081),
        에테리얼 (L1SkillId.에테리얼, 5098),
        비욘드 (L1SkillId.비욘드, 5124),
        어드밴스 (L1SkillId.어드밴스, 5122),
        게일샷 (L1SkillId.게일샷, 6913),
        프라이드 (L1SkillId.프라이드, 5114),
        세인트 (0, 0),
        레이징포스 (L1SkillId.레이징포스, 6082);
        private final int skillId;
        private final int iconId;
        
        Kind(int skillId, int iconId) {
            this.skillId = skillId;
            this.iconId = iconId;
        }
    }
    
    public static void apply(L1PcInstance pc, Kind kind) {
        switch (kind) {
        case 기간틱 -> {
            double percent = pc.getLevel() / 2;
            int addHp = (int)Math.round(pc.getBaseMaxHp() * (percent * 0.01));
            pc.setMagicBuffHp(addHp);
            pc.addMaxHp(pc.getMagicBuffHp());
            if (pc.isInParty()) {
                pc.getParty().refreshPartyMemberStatus(pc);
            }
            SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
        }
        case 샤이닝아머 -> {
            pc.addEffectedER(10);
            SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
        }
        case 일루션리치 -> {
            pc.getAbility().addSp(4);
            SC_SPMR.send(pc);
        }
        case 일루션골렘 -> {
            pc.getAC().addAc(-10);
            SC_OwnCharAttrDef.send(pc);
        }
        case 솔리드캐리지 -> {
            pc.addEffectedER(15);
            SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
        }
        case 이베이젼 -> {
            pc.addEffectedER(18);
            SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
        }
        case 비욘드 -> {
            if (pc.hasSkillEffect(L1SkillId.MEDITATION)) {
                pc.removeSkillEffect(L1SkillId.MEDITATION);
            }
        }
        case 어드밴스 -> {
            pc.setAdvenHp(pc.getBaseMaxHp() / 5);
            pc.setAdvenMp(pc.getBaseMaxMp() / 5);
            pc.addMaxHp(pc.getAdvenHp());
            pc.addMaxMp(pc.getAdvenMp());
            if (pc.isInParty()) {
                pc.getParty().refreshPartyMemberStatus(pc);
            }
            SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
            SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
        }
        case 프라이드 -> {
            double percent = pc.getLevel() / 4;
            int addHp = (int)Math.round(pc.getBaseMaxHp() * (percent * 0.01));
            pc.setMagicBuffHp(addHp);
            pc.addMaxHp(pc.getMagicBuffHp());
            if (pc.isInParty()) {
                pc.getParty().refreshPartyMemberStatus(pc);
            }
            SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
        }
        case 세인트 -> {
            pc.sendPackets("\\aH이뮨 투 함:세인트 PVP대미지 감소 효과 적용(술자)");
            if (pc.getLevel() == 80 || pc.getLevel() == 81) {
                pc.sendPackets("\\aH현재 레벨 : " + pc.getLevel() + "   술자 PVP데미지감소: 1% ");
            } else if (pc.getLevel() == 82 || pc.getLevel() == 83) {
                pc.sendPackets("\\aH현재 레벨 : " + pc.getLevel() + "   PVP데미지감소: 2% ");
            } else if (pc.getLevel() == 84 || pc.getLevel() == 85) {
                pc.sendPackets("\\aH현재 레벨 : " + pc.getLevel() + "   PVP데미지감소: 3% ");
            } else if (pc.getLevel() == 86 || pc.getLevel() == 87) {
                pc.sendPackets("\\aH현재 레벨 : " + pc.getLevel() + "   PVP데미지감소: 4% ");
            } else if (pc.getLevel() == 88 || pc.getLevel() == 89) {
                pc.sendPackets("\\aH현재 레벨 : " + pc.getLevel() + "   PVP데미지감소: 5% ");
            } else if (pc.getLevel() == 90) {
                pc.sendPackets("\\aH현재 레벨 : " + pc.getLevel() + "   PVP데미지감소: 6% ");
            } else if (pc.getLevel() == 91) {
                pc.sendPackets("\\aH현재 레벨 : " + pc.getLevel() + "   PVP데미지감소: 7% ");
            } else if (pc.getLevel() == 92) {
                pc.sendPackets("\\aH현재 레벨 : " + pc.getLevel() + "   PVP데미지감소: 8% ");
            } else if (pc.getLevel() == 93) {
                pc.sendPackets("\\aH현재 레벨 : " + pc.getLevel() + "   PVP데미지감소: 9% ");
            } else if (pc.getLevel() >= 94) {
                pc.sendPackets("\\aH현재 레벨 : " + pc.getLevel() + "   PVP데미지감소: 10% ");
            }
        }
        case 레이징포스 -> {
            if (pc.getLevel() >= 80 && pc.getLevel() <= 84) {
                pc.sendPackets("\\aH레이징 포스: (Lv:80~84) PVP 대미지 감소 +1 추가");
            } else if (pc.getLevel() >= 85 && pc.getLevel() <= 88) {
                pc.sendPackets("\\aH레이징 포스: (Lv:85~88) PVP 대미지 감소 +2 추가");
            } else if (pc.getLevel() >= 89 && pc.getLevel() <= 92) {
                pc.sendPackets("\\aH레이징 포스: (Lv:89~92) PVP 대미지 감소 +3 추가");
            } else if (pc.getLevel() >= 93 && pc.getLevel() <= 96) {
                pc.sendPackets("\\aH레이징 포스: (Lv:93~96) PVP 대미지 감소 +4 추가");
            } else if (pc.getLevel() >= 97) {
                pc.sendPackets("\\aH레이징 포스: (Lv:97이상) PVP 대미지 감소 +5 추가");
            }
        }
        default -> {
        }
        }
        if (kind.iconId != 0) SC_InventoryIcon.iconNewUnLimit(pc, kind.skillId, kind.iconId, true);
    }
}
