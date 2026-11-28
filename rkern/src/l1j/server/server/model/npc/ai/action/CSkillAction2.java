package l1j.server.server.model.npc.ai.action;

import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.inventory.L1PolyMorph;
import l1j.server.server.model.CWorld;

import java.util.ArrayList;
import java.util.Arrays;

import l1j.server.server.ActionCodes;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.MobSkillTable;
import l1j.server.server.model.character.skill.CSkillDB;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.character.animation.EActionCodes;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.templates.L1MobSkill;
import l1j.server.server.templates.L1MobSkill.SkillEntry;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1Skills;

public final class CSkillAction2 {

    /**
     * 물리 AoE 임시 버퍼는 NPC별 소유가 아니라 실행 스레드별 재사용.
     * NPC 수가 늘어나도 버퍼 수는 worker/thread 수에 비례한다.
     */
    private static final ThreadLocal<L1Character[]> AOE_BUF =
            new ThreadLocal<L1Character[]>() {
                @Override
                protected L1Character[] initialValue() {
                    return new L1Character[16];
                }
            };

    private CSkillAction2() {
    }

    // =========================================================
    // PUBLIC STATE ACCESS
    // =========================================================

    public static long getSleepTime(final L1NpcInstance npc) {
        return npc != null ? npc._mobSkillSleepTime : 0L;
    }

    public static L1MobSkill getMobSkillTemplate(final L1NpcInstance npc) {
        if (npc == null || npc.getNpcTemplate() == null) {
            return null;
        }
        return MobSkillTable.getInstance().getTemplate(
                npc.getNpcTemplate().get_npcId());
    }

    /**
     * AI 종료 시 사용횟수만 0으로 되돌린다.
     * 배열은 이미 생성된 경우 재사용한다.
     */
    public static void resetAllSkillUseCount(final L1NpcInstance npc) {
        if (npc == null) {
            return;
        }

        final int[] a = npc._mobSkillUseCount;
        if (a != null) {
            Arrays.fill(a, 0);
        }

        npc._mobSkillSleepTime = 0L;
    }

    /**
     * template 변경(transform 포함) 시 이전 template runtime을 폐기한다.
     */
    public static void resetRuntime(final L1NpcInstance npc) {
        if (npc == null) {
            return;
        }

        npc._mobSkillUseCount = null;
        npc._mobSkillSleepTime = 0L;
    }

    /**
     * NPC dispose 시 개체별 스킬 runtime 참조만 제거.
     */
    public static void dispose(final L1NpcInstance npc) {
        resetRuntime(npc);
    }

    // =========================================================
    // SKILL EXECUTE
    // =========================================================

    public static boolean skillUse(
            final L1NpcInstance attacker,
            final L1Character tg) {
        return skillUseImpl(attacker, tg, 0);
    }

    public static boolean skillUseSelected(
            final L1NpcInstance attacker,
            final L1Character tg,
            final int skillId) {
        return skillId > 0 && skillUseImpl(attacker, tg, skillId);
    }

    private static boolean skillUseImpl(
            final L1NpcInstance attacker,
            final L1Character tg,
            final int selectedSkillId) {

        if (attacker == null || tg == null) {
            return false;
        }

        final int npcId = attacker.getNpcTemplate().get_npcId();
        final L1MobSkill template = MobSkillTable.getInstance().getTemplate(npcId);

        if (template == null) {
            return false;
        }

        final SkillEntry[] entries = template.getSkills();
        if (entries == null || entries.length == 0) {
            return false;
        }

        for (int i = 0; i < entries.length; i++) {
            final SkillEntry e = entries[i];

            if (e == null || e.type == L1MobSkill.TYPE_NONE) {
                break;
            }
            if (selectedSkillId > 0 && e.skillId != selectedSkillId) {
                continue;
            }

            final L1Character baseTarget =
                    (e.changeTarget > 0)
                    ? changeTarget(attacker, e, tg)
                    : tg;

            if (baseTarget == null) {
                continue;
            }

            final L1Character realTarget =
                    resolveTargetByTriggers(
                            attacker,
                            i,
                            e,
                            baseTarget);

            if (realTarget == null) {
                continue;
            }

            final boolean ok;

            switch (e.type) {
            case L1MobSkill.TYPE_PHYSICAL_ATTACK:
                ok = physicalAttack(attacker, e, realTarget);
                break;

            case L1MobSkill.TYPE_MAGIC_ATTACK:
                ok = magicAttack(attacker, e, realTarget, npcId);
                break;

            case L1MobSkill.TYPE_SUMMON:
                ok = summon(attacker, e);
                break;

            case L1MobSkill.TYPE_POLY:
                ok = poly(attacker, e);
                break;

            default:
                continue;
            }

            if (!ok) {
                continue;
            }

            if (e.triCount > 0) {
                skillUseCountUp(attacker, i, entries.length);
            }

            return true;
        }

        return false;
    }

    // =========================================================
    // SUMMON
    // =========================================================

    private static boolean summon(
            final L1NpcInstance attacker,
            final SkillEntry e) {

        final int summonId = e.summon;
        if (summonId == 0) {
            return false;
        }

        final int min = Math.max(0, e.summonMin);
        final int max = Math.max(0, e.summonMax);

        // 기존 semantics 그대로 유지.
        final int count =
                (max <= 0)
                ? min
                : (GameRandom.nextInt(max) + min);

        if (count <= 0) {
            return false;
        }

        mobspawn(attacker, summonId, count);

        SC_SkillSound.broadcast(attacker, attacker.getId(), 761);

        SC_DoActionGFX.broadcast(
                attacker,
                ActionCodes.ACTION_SkillBuff);

        attacker._mobSkillSleepTime =
                attacker.getCurrentSpriteInterval(
                        EActionCodes.spell_nodir);

        return true;
    }

    // =========================================================
    // POLY
    // =========================================================

    private static boolean poly(
            final L1NpcInstance attacker,
            final SkillEntry e) {

        final int polyId = e.polyId;
        if (polyId == 0) {
            return false;
        }

        boolean usePoly = false;

        for (L1PcInstance pc :
                CWorld.getInstance().getVisiblePlayer(attacker)) {

            if (pc == null || pc.isDead()) {
                continue;
            }

            if (pc.isGhost()) {
                continue;
            }

            if (!attacker.glanceCheck(pc.getX(), pc.getY())) {
                continue;
            }

            final int npcId =
                    attacker.getNpcTemplate().get_npcId();

            if (npcId == 81082) {
                pc.getInventory().takeoffEquip(945);
            }

            L1PolyMorph.doPoly(
                    pc,
                    polyId,
                    1800,
                    L1PolyMorph.MORPH_BY_NPC,
                    false);

            usePoly = true;
        }

        if (usePoly) {
            for (L1PcInstance pc :
                    CWorld.getInstance().getVisiblePlayer(attacker)) {

                if (pc == null) {
                    continue;
                }

                SC_SkillSound.send(pc, pc.getId(), 230);

                SC_SkillSound.broadcast(pc, pc.getId(), 230);

                break;
            }

            SC_DoActionGFX.broadcast(
                    attacker,
                    ActionCodes.ACTION_SkillBuff);

            attacker._mobSkillSleepTime =
                    attacker.getCurrentSpriteInterval(
                            EActionCodes.spell_nodir);
        }

        return usePoly;
    }

    // =========================================================
    // MAGIC
    // =========================================================

    private static boolean magicAttack(
            final L1NpcInstance attacker,
            final SkillEntry e,
            final L1Character target,
            final int npcId) {

        final int skillid = e.skillId;

        if (skillid <= 0) {
            return false;
        }

        if (attacker.hasSkillEffect(L1SkillId.SILENCE)) {
            return false;
        }

        final boolean predicateSkill =
                ((skillid >= 22020 && skillid <= 22029)
                || (skillid >= 22041 && skillid <= 22052)
                || (skillid >= 7001 && skillid <= 7050));

        if (predicateSkill) {
            if (attacker.hasSkillEffect(L1SkillId.PREDICATEDELAY)) {
                return false;
            }
            attacker.setSkillEffect(
                    L1SkillId.PREDICATEDELAY,
                    10 * 1000);
        }

        try {
            if (handleMonsterSkillChatAndSpecial(
                    attacker,
                    npcId,
                    skillid,
                    target)) {

                return true;
            }

            final boolean cast =
                    L1SkillUse.castNpc(
                            attacker,
                            skillid,
                            target.getId(),
                            target.getX(),
                            target.getY(),
                            null,
                            0,
                            L1SkillUse.TYPE_NORMAL,
                            e.leverage);

            if (!cast) {
                return false;
            }

            final L1Skills skill =
                    CSkillDB.getInstance().getTemplate(skillid);

            if (skill != null
                    && "attack".equals(skill.getTarget())
                    && skillid != 18) {

                attacker._mobSkillSleepTime =
                        attacker.getCurrentSpriteInterval(
                                EActionCodes.spell_dir);

            } else {
                attacker._mobSkillSleepTime =
                        attacker.getCurrentSpriteInterval(
                                EActionCodes.spell_nodir);
            }

            return true;

        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 보스몬스터 멘트...디비작업해야하는데 귀차니즘
     */
    private static boolean handleMonsterSkillChatAndSpecial(
            final L1NpcInstance attacker,
            final int npcId,
            final int skillid,
            final L1Character target) {

        switch (npcId) {
        case 45545:
            if (skillid == 7031) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$2966", 0);
            } else if (skillid == 7030) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$3717", 0);
            }
            break;

        case 5096:
        case 5097:
        case 5098:
        case 5099:
        case 5100:
            if (skillid == 7018 || skillid == 7023) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$10158", 0);

            } else if (skillid == 7001) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$10152", 0);

                if (target instanceof L1PcInstance) {
                    SC_PacketBox.send(((L1PcInstance) target), 
                                    SC_PacketBox.HADIN_DISPLAY,
                                    6);
                }

            } else if (skillid == 7013) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$10152", 0);

                if (target instanceof L1PcInstance) {
                    SC_PacketBox.send(((L1PcInstance) target), 
                                    SC_PacketBox.HADIN_DISPLAY,
                                    8);
                }

            } else if (skillid == 7002 || skillid == 7004) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$10162", 0);

            } else if (skillid == 7003) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$10150", 0);

            } else if (skillid == 7022) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$10165", 0);

            } else if (skillid == 7009
                    || skillid == 7007
                    || skillid == 7010
                    || skillid == 7008) {

                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$10162", 0);
            }
            break;

        case 7310015:
            if (skillid == 249) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "가소롭구나 먹이주제에", 0);
            } else if (skillid == 10004) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "네 이놈들 네 놈들을 모두 녹여버릴테다!", 0);
            }
            break;

        case 7310028:
        case 7310041:
            if (skillid == 10006) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "이 정도로는 어림없습니다.", 0);
            } else if (skillid == 28) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "이익..노예 주제에 건방지구나 죽어라!!", 0);
            }
            break;

        case 45601:
            if (skillid == 10056) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "환상에서 벗어날 시간이다..", 0);
            } else if (skillid == 28) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "크윽..나에게 치명상을 입히다니..", 0);
            }
            break;

        case 45752:
        case 45753:
            if (skillid == 80006) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$26286", 0);
            } else if (skillid == 10070 || skillid == 10069) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$26285", 0);
            } else if (skillid == 707040) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$26284", 0);
            }
            break;

        case 7310092:
            if (skillid == 707031) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "네 녀석이 어디까지 나를 방해할 수 있을까? 후후후..!", 0);
            } else if (skillid == 707027) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "어둠의 힘을 보여주마..", 0);
            }
            break;

        case 45610:
            if (skillid == 10099) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "모두 저주 할꺼야", 0);
            } else if (skillid == 10019) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "으으..이놈들 !!", 0);
            }
            break;

        case 7310051:
            if (skillid == 707071) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "내앞에 무릎을 꿇어라", 0);
            }
            break;

        case 7310056:
            if (skillid == 22055) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "이 정도로 나를 이길 수 있겠느냐?", 0);
            } else if (skillid == 27) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "진정한 기사의 힘을 보여주마 버텨보아라!!", 0);
            }
            break;

        case 7310061:
            if (skillid == 10010) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "죽음의 기운을 느껴보아라..", 0);
            } else if (skillid == 10061) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "영생을 얻는 나에게 도전하는가 !!", 0);
            }
            break;

        case 7310066:
            if (skillid == 20061) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "겨우 이 정도 힘으로 이 곳까지 왔느냐?", 0);
            } else if (skillid == 20091) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "나의 분노를 자극하지 마라", 0);
            }
            break;

        case 7310077:
            if (skillid == 10062) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "감히 신에게 도전하는가?", 0);
            } else if (skillid == 10012) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "힘의 차이를 느껴보아라", 0);
            }
            break;

        case 91200:
            if (skillid == 20086) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "죽는건 한 순간이다..어서 가거라!!", 0);
            } else if (skillid == 20089) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "곧 너희들은 죽을 것이다!!", 0);
            }
            break;

        case 5136:
            if (skillid == 7048) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$14234", 0);
            } else if (skillid == 7044) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$14233", 0);
            } else if (skillid == 7049) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$14236", 0);
            } else if (skillid == 7042) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$14237", 0);
            } else if (skillid == 7041) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$14238", 0);
            } else if (skillid == 7050) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$14235", 0);
            }
            break;

        case 5135:
            if (skillid == 7045) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$14055", 0);
            } else if (skillid == 7046) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$14056", 0);
            } else if (skillid == 7047) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$14057", 0);
            } else if (skillid == 7044) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$14058", 0);
            } else if (skillid == 7004
                    || skillid == 7055
                    || skillid == 7056
                    || skillid == 7057
                    || skillid == 7058) {

                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "$14059", 0);
            }
            break;

        case 8500044:
            if (skillid == 707076) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "네놈들의 선물. 잘 받겠다", 0);
            } else if (skillid == 707077) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "죽어라아아아!!!", 0);
            }
            break;

        case 8500060:
            if (skillid == 707079) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "날 자극하지 마라, 내 먹이가 되기 전에..", 0);
            } else if (skillid == 707080) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "네놈들이 자초한 일이다.. 모조리 불타올라라", 0);
            }
            break;

        case 8500063:
            if (skillid == 64) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "살아있는 존재.. 죽인다.. 파묻는다..", 0);
            }
            break;

        case 8500071:
            if (skillid == 7059) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "캬릉 도망가지 못한다!", 0);
            }
            break;

        case 8500090:
            if (skillid == 707082) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "얌전히 있어라.. 가지고 놀기 어렵단다..", 0);
            }
            break;

        case 8500095:
            if (skillid == 220551) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "애송이들 싸움은 이렇게 하는 것이다", 0);
            } else if (skillid == 707083) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "내가 지금 시범을 보여주지, 받아 보아라", 0);
            } else if (skillid == 7060) {
                SC_NpcChatPacket.broadcastNpc(attacker, attacker, "못봐주겠군, 이쯤에서 끝내주지", 0);
            }
            break;

        case 460000362:
            if (skillid == 707086) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "어둠의 마법은 영원하다", 0);
            } else if (skillid == 707085) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "죽어라. 이것이 위대한 왕의 힘이다", 0);
            }
            break;

        case 8500111:
            if (skillid == 707090) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "이것이 감히 신에게 도전하는 불손한 자에게 내리는 징벌이다", 0);
            }
            break;

        case 8500129:
            if (skillid == 707091) {
                SC_NpcChatPacket.broadcastNpc(attacker, 
                                attacker, "오만한 자들이여 그 영혼을 내가 거두어주마", 0);
            }
            break;

        default:
            break;
        }

        return false;
    }

    // =========================================================
    // PHYSICAL
    // =========================================================

    private static boolean physicalAttack(
            final L1NpcInstance attacker,
            final SkillEntry e,
            final L1Character mainTarget) {

        final int dist =
                attacker.getLocation()
                        .getTileLineDistance(mainTarget.getLocation());

        if (dist > e.range) {
            return false;
        }

        if (!attacker.glanceCheck(
                mainTarget.getX(),
                mainTarget.getY())) {

            return false;
        }

        attacker.setHeading(
                attacker.targetDirection(
                        mainTarget.getX(),
                        mainTarget.getY()));

        L1Character[] buf = AOE_BUF.get();
        int sz = 0;
        boolean includesMainTarget = false;

        if (e.areaHeight > 0) {
            final ArrayList<L1Object> olist =
                    CWorld.getInstance()
                            .getVisibleBoxObjects(
                                    attacker,
                                    attacker.getHeading(),
                                    e.areaWidth,
                                    e.areaHeight);

            if (olist != null) {
                for (int k = 0, osz = olist.size();
                        k < osz;
                        k++) {

                    final L1Object obj = olist.get(k);

                    if (!(obj instanceof L1Character)) {
                        continue;
                    }

                    final L1Character cha =
                            (L1Character) obj;

                    if (cha == null || cha.isDead()) {
                        continue;
                    }

                    if (!attacker.glanceCheck(
                            cha.getX(),
                            cha.getY())) {
                        continue;
                    }

                    if (!filterPhysicalAoETarget(
                            attacker,
                            mainTarget,
                            cha)) {
                        continue;
                    }

                    boolean dup = false;

                    for (int j = 0; j < sz; j++) {
                        if (buf[j] == cha) {
                            dup = true;
                            break;
                        }
                    }

                    if (dup) {
                        continue;
                    }

                    if (sz >= buf.length) {
                        buf = growCharBuf(buf);
                        AOE_BUF.set(buf);
                    }

                    buf[sz++] = cha;
                    if (cha == mainTarget) includesMainTarget = true;
                }
            }

        } else {
            if (sz >= buf.length) {
                buf = growCharBuf(buf);
                AOE_BUF.set(buf);
            }

            buf[sz++] = mainTarget;
        }

        if (sz == 0) {
            return false;
        }

        // The designated target can be excluded by AoE geometry/LOS filtering.
        // Render once against the first eligible victim instead of a silent hit.
        final L1Character animationTarget = (e.areaHeight > 0 && !includesMainTarget)
                ? buf[0] : mainTarget;
        try {
            for (int i = 0; i < sz; i++) {
                final L1Character t = buf[i];

                if (t == null) {
                    continue;
                }

                l1j.server.server.model.character.action.CAttackAction.attackNpcPhysicalAoE(
                        attacker, t, e.leverage, e.actid, e.gfxid, t == animationTarget);
            }

        } finally {
            for (int i = 0; i < sz; i++) {
                buf[i] = null;
            }
        }

        attacker._mobSkillSleepTime =
                attacker.getCurrentSpriteInterval(
                        EActionCodes.attack);

        return true;
    }

    private static L1Character[] growCharBuf(
            final L1Character[] old) {

        final L1Character[] n =
                new L1Character[old.length << 1];

        System.arraycopy(
                old,
                0,
                n,
                0,
                old.length);

        return n;
    }

    private static boolean filterPhysicalAoETarget(
            final L1NpcInstance attacker,
            final L1Character mainTarget,
            final L1Character cha) {

        final boolean playerSide =
                (mainTarget instanceof L1PcInstance)
                || (mainTarget instanceof L1SummonInstance)
                || (mainTarget instanceof L1CompanionInstance);

        if (playerSide) {
            if (cha instanceof L1PcInstance) {
                final L1PcInstance pc =
                        (L1PcInstance) cha;

                if (pc.isGhost() || pc.isGmInvis()) {
                    return false;
                }

                if ((attacker instanceof L1SummonInstance)
                        || (mainTarget instanceof L1CompanionInstance)) {

                    if (attacker.getMaster() != null) {
                        if (cha.getId()
                                == attacker.getMaster().getId()) {
                            return false;
                        }
                    }

                    if (cha.getZoneType() == 1) {
                        return false;
                    }
                }

                return true;
            }

            return (cha instanceof L1SummonInstance)
                    || (cha instanceof L1CompanionInstance);
        }

        return cha instanceof L1MonsterInstance;
    }

    // =========================================================
    // TRIGGERS
    // =========================================================

    private static L1Character resolveTargetByTriggers(
            final L1NpcInstance attacker,
            final int skillIdx,
            final SkillEntry e,
            L1Character target) {

        if (e.triRnd > 0) {
            final int chance =
                    GameRandom.nextInt(100) + 1;

            if (chance > e.triRnd) {

                return null;
            }
        }

        if (e.triHp > 0) {
            final int hpRatio =
                    (attacker.getCurrentHp() * 100)
                    / Math.max(1, attacker.getMaxHp());

            if (hpRatio > e.triHp) {

                return null;
            }
        }

        if (e.triCompanionHp > 0) {
            final L1NpcInstance companionNpc =
                    searchMinCompanionHp(attacker);

            if (companionNpc == null) {

                return null;
            }

            final int hpRatio =
                    (companionNpc.getCurrentHp() * 100)
                    / Math.max(1, companionNpc.getMaxHp());

            if (hpRatio <= e.triCompanionHp) {
                target = companionNpc;

            } else {

                return null;
            }
        }

        if (e.triRange != 0) {
            final int distance =
                    attacker.getLocation()
                            .getTileLineDistance(
                                    target.getLocation());

            final int tr = e.triRange;

            final boolean ok =
                    (tr < 0)
                    ? (distance <= Math.abs(tr))
                    : (distance >= tr);

            if (!ok) {

                return null;
            }
        }

        if (e.triCount > 0) {
            final int used =
                    getSkillUseCount(
                            attacker,
                            skillIdx);

            if (used >= e.triCount) {

                return null;
            }
        }

        return target;
    }

    private static L1NpcInstance searchMinCompanionHp(
            final L1NpcInstance attacker) {

        L1NpcInstance minHpNpc = null;
        int bestRatio = 101;

        final int family =
                attacker.getNpcTemplate().get_family();

        final ArrayList<L1Object> objects = CWorld.getInstance().getVisibleObjects(attacker);
        for (int i = 0, size = objects.size(); i < size; i++) {
            final L1Object object = objects.get(i);

            if (!(object instanceof L1NpcInstance)) {
                continue;
            }

            final L1NpcInstance npc =
                    (L1NpcInstance) object;

            if (npc.getNpcTemplate().get_family()
                    != family) {
                continue;
            }

            final int ratio =
                    (npc.getCurrentHp() * 100)
                    / Math.max(1, npc.getMaxHp());

            if (ratio < bestRatio) {
                bestRatio = ratio;
                minHpNpc = npc;
            }
        }

        return minHpNpc;
    }

    // =========================================================
    // COUNT RUNTIME
    // =========================================================

    private static int getSkillUseCount(
            final L1NpcInstance npc,
            final int idx) {

        final int[] a = npc._mobSkillUseCount;

        return (a == null
                || idx < 0
                || idx >= a.length)
                ? 0
                : a[idx];
    }

    private static void skillUseCountUp(
            final L1NpcInstance npc,
            final int idx,
            final int skillCount) {

        int[] a = npc._mobSkillUseCount;

        if (a == null || a.length != skillCount) {
            a = new int[skillCount];
            npc._mobSkillUseCount = a;
        }

        if (idx >= 0 && idx < a.length) {
            a[idx]++;
        }
    }

    // =========================================================
    // SUMMON SPAWN
    // =========================================================

    private static void mobspawn(
            final L1NpcInstance attacker,
            final int summonId,
            final int count) {

        for (int i = 0; i < count; i++) {
            mobspawnOne(attacker, summonId);
        }
    }

    private static void mobspawnOne(
            final L1NpcInstance attacker,
            final int summonId) {

        try {
            final L1Npc spawnmonster =
                    CObjectDB.LoadNPC()
                            .getTemplate(summonId);

            if (spawnmonster == null) {
                return;
            }

            final L1NpcInstance mob =
                    CObjectDB.LoadNPC()
                            .newNpcInstance(spawnmonster);

            mob.setId(
                    IdFactory.getInstance()
                            .nextId());

            final L1Location loc =
                    attacker.getLocation()
                            .randomLocation(8, false);

            mob.setX(loc.getX());
            mob.setY(loc.getY());
            mob.setHomeX(loc.getX());
            mob.setHomeY(loc.getY());

            final short mapid =
                    attacker.getMapId();

            mob.setMap(mapid);

            mob.setHeading(
                    GameRandom.nextInt(8));

            CWorld.getInstance().storeAndEnterWorld(mob);

            if (mob instanceof L1MonsterInstance) {
                final L1MonsterInstance newnpc =
                        (L1MonsterInstance) mob;

                newnpc.set_storeDroped(true);

                if (summonId == 45061
                        || summonId == 45161
                        || summonId == 45181
                        || summonId == 45455) {

                    SC_DoActionGFX.broadcast(
                            newnpc,
                            ActionCodes.ACTION_Hide);

                    newnpc.setStatus(13);

                    SC_WorldPutObject.broadcastNpc(newnpc);

                    SC_DoActionGFX.broadcast(
                            newnpc,
                            ActionCodes.ACTION_Appear);

                    newnpc.setStatus(0);

                    SC_WorldPutObject.broadcastNpc(newnpc);
                }

                newnpc.onNpcAI();
                newnpc.refreshLight();

                newnpc.startChat(
                        L1NpcInstance.CHAT_TIMING_APPEARANCE);

            } else {
                mob.onNpcAI();
                mob.refreshLight();

                mob.startChat(
                        L1NpcInstance.CHAT_TIMING_APPEARANCE);
            }

        } catch (Throwable t) {
        }
    }

    // =========================================================
    // CHANGE TARGET
    // =========================================================

    private static L1Character changeTarget(
            final L1NpcInstance attacker,
            final SkillEntry e,
            final L1Character originalTarget) {

        switch (e.changeTarget) {
        case L1MobSkill.CHANGE_TARGET_ME:
            return attacker;

        case L1MobSkill.CHANGE_TARGET_RANDOM: {
            L1Character picked = null;
            int seen = 0;

            final ArrayList<L1Object> objects = CWorld.getInstance().getVisibleObjects(attacker);
            for (int i = 0, size = objects.size(); i < size; i++) {
                final L1Object obj = objects.get(i);

                if (!(obj instanceof L1Character)) {
                    continue;
                }

                if (!(obj instanceof L1PcInstance
                        || obj instanceof L1SummonInstance
                        || obj instanceof L1CompanionInstance)) {
                    continue;
                }

                final L1Character cha =
                        (L1Character) obj;

                if (cha == null || cha.isDead()) {
                    continue;
                }

                if (e.triRange != 0) {
                    final int d =
                            attacker.getLocation()
                                    .getTileLineDistance(
                                            cha.getLocation());

                    final int tr = e.triRange;

                    final boolean ok =
                            (tr < 0)
                            ? (d <= Math.abs(tr))
                            : (d >= tr);

                    if (!ok) {
                        continue;
                    }
                }

                if (!attacker.glanceCheck(
                        cha.getX(),
                        cha.getY())) {
                    continue;
                }

                if (!attacker.getHateList()
                        .containsKey(cha)) {
                    continue;
                }

                if (cha instanceof L1PcInstance) {
                    if (((L1PcInstance) cha).isGhost()) {
                        continue;
                    }
                }

                seen++;

                if (seen == 1) {
                    picked = cha;

                } else if (GameRandom.nextInt(seen) == 0) {

                    picked = cha;
                }
            }

            return (picked != null)
                    ? picked
                    : originalTarget;
        }

        case L1MobSkill.CHANGE_TARGET_COMPANION:
            /*
             * 기존 CSkillAction2도 이 타입 자체에서 직접 companion을 고르지 않고
             * triCompanionHp trigger에서 실제 companion target을 결정했다.
             * 따라서 기존 semantics 그대로 originalTarget 유지.
             */
            return originalTarget;

        case L1MobSkill.CHANGE_TARGET_NO:
        default:
            return originalTarget;
        }
    }
}
