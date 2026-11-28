package l1j.server.server.model.character.skill.acquire;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.CSkillDB;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.passive.PassiveID;
import l1j.server.server.model.character.skill.passive.PassiveInfo;
import l1j.server.server.model.character.skill.passive.PassiveLoader;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_AddSkill;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_ADD_ACTIVE_SPELL_EX_NOTI;
import l1j.server.server.storage.mysql.CSkillAcquireStorage;
import l1j.server.server.templates.L1Skills;

public final class CSkillMaster {
    private static final Logger LOG = Logger.getLogger(CSkillMaster.class.getName());
    private static final int[][] SKILL_RANGES = {
        {1, 16, 113, 122},
        {1, 8, 87, 94},
        {1, 48, 129, 176},
        {1, 80},
        {1, 16, 97, 112, 234, 234},
        {177, 200},
        {201, 224},
        {1, 8, 225, 240},
    };

    private CSkillMaster() {}

    public static void Grant(L1PcInstance gm, L1PcInstance target) {
        CUserSocket session = target.getNetConnection();
        CIOThreadCallback.getInstance().completeSession(session, () -> {
            if (session.getActiveChar() != target || !session.ownsReadyGameSession())
                throw new IllegalStateException("Skill grant target changed session");
            grantOnOwner(gm, target);
        }, failure -> {
            if (failure != null) {
                LOG.log(Level.WARNING, "GM skill grant owner rejected: " + target.getId(), failure);
                SC_SystemMessage.send(gm, "스킬마스터를 처리하지 못했습니다.");
            }
        });
    }

    private static void grantOnOwner(L1PcInstance gm, L1PcInstance pc) {
        int type = pc.getType();
        if (type < 0 || type >= SKILL_RANGES.length) return;
        try (L1InventoryTransaction tx = L1InventoryTransaction.beginCharacterStat(pc)) {
            CSkillDB definitions = CSkillDB.getInstance();
            int[] ranges = SKILL_RANGES[type];
            List<L1Skills> skills = new ArrayList<>();
            for (int i = 0; i < ranges.length; i += 2) {
                for (int id = ranges[i]; id <= ranges[i + 1]; id++) {
                    L1Skills skill = definitions.getTemplate(id);
                    if (skill != null && !pc.HaveSkill(id)) skills.add(skill);
                }
            }
            ArrayList<PassiveInfo> classPassives = PassiveLoader.getInstance().fromClassType(type);
            List<PassiveInfo> passives = classPassives == null ? List.of()
                    : classPassives.stream().filter(info -> !pc.isPassive(info.getPassiveId())).toList();
            int[] effects = Arrays.stream(legacyEffects(type)).filter(id -> !pc.hasSkillEffect(id)).toArray();
            tx.requireTables("character_skills", "passive_user_info", "character_buff");
            tx.sql(con -> {
                CSkillAcquireStorage.insertBatch(con, pc.getId(), skills, true);
                CSkillAcquireStorage.insertPassives(con, pc.getId(), passives);
                CSkillAcquireStorage.insertPermanentEffects(con, pc.getId(), effects);
            });
            tx.onFailure(() -> SC_SystemMessage.send(gm, "스킬마스터를 저장하지 못했습니다."));
            tx.commit(() -> {
                CSkillAcquisition.publish(pc, skills);
                for (PassiveInfo info : passives) pc.addPassive(info);
                applyLegacyEffects(pc, effects, passives);
                SC_SkillSound.send(pc, pc.getId(), 227);
                SC_SkillSound.broadcast(pc, pc.getId(), 227);
                sendLegacySpellList(pc, type);
                for (PassiveInfo info : passives) {
                    int id = info.getPassiveId();
                    SC_ADD_ACTIVE_SPELL_EX_NOTI.send(pc, id, id == PassiveID.ARMOR_GUARD.toInt() ? 10 : 0);
                }
                SC_SystemMessage.send(gm, "해당 캐릭터 스킬마스터 완료");
            });
        }
    }

    private static int[] legacyEffects(int type) {
        return switch (type) {
            case 1 -> new int[] {L1SkillId.프라이드, L1SkillId.솔리드캐리지};
            case 4 -> new int[] {L1SkillId.버닝, L1SkillId.이베이젼};
            case 6 -> new int[] {L1SkillId.일루션리치, L1SkillId.일루션골렘};
            default -> new int[0];
        };
    }

    private static void applyLegacyEffects(L1PcInstance pc, int[] effects, List<PassiveInfo> passives) {
        for (int effect : effects) {
            switch (effect) {
                case L1SkillId.프라이드 -> pc.프라이드(true);
                case L1SkillId.솔리드캐리지 -> pc.솔리드캐리지(true);
                case L1SkillId.버닝 -> pc.버닝(true);
                case L1SkillId.이베이젼 -> pc.이베이젼(true);
                case L1SkillId.일루션리치 -> pc.일루션리치(true);
                case L1SkillId.일루션골렘 -> pc.일루션골렘(true);
                default -> throw new IllegalArgumentException("Unexpected class skill effect " + effect);
            }
            pc.setSkillEffect(effect, -1);
        }
        for (PassiveInfo passive : passives) {
            if (passive.getPassiveId() == PassiveID.DOUBLE_BREAK_DESTINY.toInt()
                    && pc.hasSkillEffect(L1SkillId.DOUBLE_BRAKE)) {
                pc.removeSkillEffect(L1SkillId.DOUBLE_BRAKE);
                break;
            }
        }
    }

    private static void sendLegacySpellList(L1PcInstance pc, int type) {
        int elfAttr = pc.getElfAttr();
        switch (type) {
            case 0 -> SC_AddSkill.send(pc, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case 1 -> SC_AddSkill.send(pc, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 192, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case 2 -> SC_AddSkill.send(pc, 255, 255, 127, 255, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 127, 3, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, elfAttr);
            case 3 -> SC_AddSkill.send(pc, 255, 255, 127, 255, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case 4 -> SC_AddSkill.send(pc, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 127, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case 5 -> SC_AddSkill.send(pc, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 0, 0, 0, 0, 0, 0);
            case 6 -> SC_AddSkill.send(pc, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 0, 0, 0);
            case 7 -> SC_AddSkill.send(pc, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 192, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            default -> { }
        }
    }
}
