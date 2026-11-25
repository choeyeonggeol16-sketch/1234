package l1j.server.server.model.character.skill.helper;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1MerchantInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.Instance.L1SupportInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.character.skill.passive.PassiveID;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeHeading;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_RangeSkill;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_Sound;
import l1j.server.server.model.network.core.sc.packet.SC_UseAttackSkill;
import l1j.server.server.templates.L1Skills;
public final class SkillGfxHelper {

    private SkillGfxHelper() {}

    private static final int PC_PC  = 1;
    private static final int PC_NPC = 2;

    private static final int HIT_FLAG  = 6;
    private static final int MISS_FLAG = 0;

    public static void sendAttackHitFromProcessor(final L1SkillUse su, final L1Character target, final int finalDmg) {
        if (su == null || su.exSkill() == null || target == null) return;

        final L1PcInstance player = su.hdPlayer();
        if (player == null) return;

        final L1Skills skill = su.exSkill();
        if (!"attack".equals(skill.getTarget())) return;
        if (skill.getArea() != 0) return;

        int castgfx = skill.getCastGfx();
        if (castgfx == 0) return;

        castgfx = applyCritCastGfx(su.hdSkillId(), castgfx, su.exIsCriticalDamage());

        final int actionId = skill.getActionId();
        final int isHit = (finalDmg > 0) ? HIT_FLAG : MISS_FLAG;

        final int targetId = target.getId();
        final int tx = su.exTargetX();
        final int ty = su.exTargetY();

        SC_UseAttackSkill.sendAndBroadcast(player, targetId, castgfx, tx, ty, actionId, isHit);
    }

    public static void sendGrfx(
            final boolean isSkillAction,
            final L1Skills skill,
            final int skillId,
            final L1Character user,
            final L1PcInstance player,
            final L1Character target,
            final int targetListSize,
            final L1Character[] targetsArr,
            final int targetX,
            final int targetY,
            final int getBuffIconDuration,
            final int skillTime,
            final int calcType
    ) {
        if (skill == null || user == null) return;

        int actionId = skill.getActionId();
        int castgfx  = skill.getCastGfx();
        if (castgfx == 0) return;

        // UNCANNY_DODGE
        if (skillId == L1SkillId.UNCANNY_DODGE) {
            if (!(target instanceof L1PcInstance)) return;
            final L1PcInstance pc = (L1PcInstance) target;
            if (pc.getAc() <= -100) castgfx = 11766;
        } else {
            if (castgfx != skill.getCastGfx()) return;
        }

        final int arrSize =
                (targetsArr != null)
                ? Math.min(targetListSize, targetsArr.length)
                : 0;

        final boolean isAttackTarget = "attack".equals(skill.getTarget());
        final boolean isNoneTarget   = "none".equals(skill.getTarget());
        final boolean hasArea        = (skill.getArea() != 0);

        int fx = targetX;
        int fy = targetY;
        if (fx == 0 && fy == 0) {
            fx = user.getX();
            fy = user.getY();
        }

        // 단일 공격마법인데 타겟리스트가 0으로 떨어진 케이스(카운터매직/무효화 등)
        final boolean needSingleAttackMissileFallback =
                (isAttackTarget && !hasArea && skillId != 18 && target != null);

        // =========================================================
        // 1) PC 시전자
        // =========================================================
        if (user instanceof L1PcInstance) {
            if (player == null) return;

            // FIRE_WALL / LIFE_STREAM
            if (skillId == L1SkillId.FIRE_WALL || skillId == L1SkillId.LIFE_STREAM) {
                final L1PcInstance pc = (L1PcInstance) user;

                if (skillId == L1SkillId.FIRE_WALL) {
                    if (!(targetX == 0 && targetY == 0) && !(targetX == pc.getX() && targetY == pc.getY())) {
                        pc.setHeading(pc.targetDirection(targetX, targetY));
                        final int heading = pc.getHeading();
                        SC_ChangeHeading.send(pc, pc.getId(), heading);
                        SC_ChangeHeading.broadcastFast(pc, pc.getId(), heading);
                    }
                }
                SC_DoActionGFX.send(pc.getNetConnection(), pc.getId(), actionId);
                SC_DoActionGFX.broadcast(pc, actionId);
                return;
            }

            final int targetId = (target != null ? target.getId() : 0);

            // ===== 스턴/엠파/그룹스턴 등 특례 유지 =====
            if (SkillUseHelper.isEmpireSkill(skillId)) {
                sendEffectToTarget(target, 17569);
                return;
            }
            if (skillId == L1SkillId.SHOCK_STUN) {
                sendStunSoundToTarget(target, arrSize, false);
                return;
            }
            if (skillId == L1SkillId.DESPERADO && arrSize == 0) {
                return;
            }
            if (SkillUseHelper.isGroupedStunSkill(skillId)) {
                sendStunSoundToTarget(target, arrSize, true);
                return;
            }

            // LIGHT 사운드
            if (skillId == L1SkillId.LIGHT && target instanceof L1PcInstance) {
                SC_Sound.send(((L1PcInstance) target), 145);
            }

            // SOUL_OF_FLAME 버프아이콘/사운드
            if (skillId == L1SkillId.SOUL_OF_FLAME && target instanceof L1PcInstance) {
                final L1PcInstance pc = (L1PcInstance) target;
                SC_PacketBox.send(pc, SC_PacketBox.BUFFICON, 11778, getBuffIconDuration);
                final long ss = SC_SkillSound.pack(pc.getId(), 11778, 19);
                SC_SkillSound.broadcastFastPacked(pc, ss);
                SC_SkillSound.sendPacked(pc, ss);
            }

            // UNCANNY_DODGE 사운드
            if (skillId == L1SkillId.UNCANNY_DODGE && target instanceof L1PcInstance) {
                final L1PcInstance pc = (L1PcInstance) target;
                final int snd = (pc.getAc() <= -100) ? 11766 : 11765;
                final long ss = SC_SkillSound.pack(pc.getId(), snd, 19);
                SC_SkillSound.sendPacked(pc, ss);
                SC_SkillSound.broadcastFastPacked(pc, ss);
            }

            // 타겟 없는 버프/디버프(단일) 액션 처리
            if (arrSize == 0 && !isNoneTarget && !(isAttackTarget && hasArea) && !needSingleAttackMissileFallback) {
                final int sprite = player.getCurrentSpriteId();
                if (sprite == 5727 || sprite == 5730) actionId = ActionCodes.ACTION_SkillBuff;
                else if (sprite == 5733 || sprite == 5736) actionId = ActionCodes.ACTION_Attack;

                if (isSkillAction) {
                    SC_DoActionGFX.send(player.getNetConnection(), player.getId(), actionId);
                    SC_DoActionGFX.broadcast(player, actionId);
                }
                return;
            }

            // =========================================================
            // 공격계열
            // =========================================================
            if (isAttackTarget && skillId != L1SkillId.TURN_UNDEAD) {

                // PVP 금지 / 논피브이피: 미스 패킷만
                if (isPcSummonPet(calcType, target)) {
                    if (player.getZoneType() == 1
                            || (target != null && target.getZoneType() == 1)
                            || (target != null && player.checkNonPvP(player, target))) {

                        SC_UseAttackSkill.sendAndBroadcast(player, 0, castgfx, fx, fy, actionId, MISS_FLAG);
                        return;
                    }
                }

                // 범위 스킬은 여기서 처리
                if (hasArea) {
                    if (arrSize > 0) {
                        SC_RangeSkill.sendAndBroadcastFast(player, targetsArr, arrSize, castgfx, actionId, SC_RangeSkill.TYPE_DIR);
                    } else {
                        SC_UseAttackSkill.sendAndBroadcast(player, 0, castgfx, fx, fy, actionId, MISS_FLAG);
                    }
                    return;
                }

                if (arrSize == 0 && targetId != 0) {
                    SC_UseAttackSkill.sendAndBroadcast(player, targetId, castgfx, fx, fy, actionId, MISS_FLAG);
                }
                return;
            }

            // =========================================================
            // none 타겟 공격
            // =========================================================
            if (isNoneTarget && skill.getType() == L1Skills.TYPE_ATTACK) {
                if (arrSize > 0) {
                	SC_RangeSkill.sendAndBroadcastFast(player, targetsArr, arrSize, castgfx, actionId, SC_RangeSkill.TYPE_NODIR);
                } else {
                    SC_UseAttackSkill.sendAndBroadcast(player, 0, castgfx, fx, fy, actionId, MISS_FLAG);
                }
                return;
            }

            // =========================================================
            // 그 외(버프/디버프/일반)
            // =========================================================
            if (skillId != L1SkillId.TELEPORT && skillId != L1SkillId.MASS_TELEPORT && skillId != L1SkillId.TELEPORT_TO_MATHER) {

                if (isSkillAction && skillId != L1SkillId.DESPERADO) {
                    SC_DoActionGFX.send(player.getNetConnection(), player.getId(), skill.getActionId());
                    SC_DoActionGFX.broadcast(player, skill.getActionId());
                }

                if (skillId == L1SkillId.COUNTER_MAGIC) {
                    sendSkillSoundToSelfAndWorld(player, targetId, castgfx);

                } else if (skillId == L1SkillId.TRUE_TARGET) {
                    return;
         
                } else if (skillId == L1SkillId.ARMOR_BRAKE) {
                    if (target != null) target.setSkillEffect(L1SkillId.ARMOR_BRAKE, 10 * 1000);

                    int sndGfx = castgfx;
                    if (player.isPassive(PassiveID.ARMOR_BREAK_DESTINY.toInt())) sndGfx = 17226;

                    final long snd = SC_SkillSound.pack(targetId, sndGfx);
                    if (player.isInParty()) {
                        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenArrayBestEffort(
                                player.getParty().getMembers(),
                                SC_SkillSound.nativeWriter((int) (snd >> 32), (int) snd & 0xffff));
                    } else {
                        SC_SkillSound.sendPacked(player, snd);
                    }

                } else if (skillId == L1SkillId.THUNDER_GRAB) {
                    int eff = castgfx;
                    if (player.isPassive(PassiveID.THUNDER_GRAP_BRAVE.toInt())) eff = 17229;
                    player.send_effect(eff);

                } else if (skillId == L1SkillId.DOUBLE_BRAKE) {
                    int eff = castgfx;
                    if (player.isPassive(PassiveID.DOUBLE_BREAK_DESTINY.toInt())) eff = 17224;
                    player.send_effect(eff);

                } else if (skillId == L1SkillId.COUNTER_BARRIER) {
                    int eff = castgfx;
                    if (player.isPassive(PassiveID.COUNTER_BARRIER_VETERAN.toInt())) eff = 17219;
                    player.send_effect(eff);

                } else if (skillId == L1SkillId.DESPERADO) {
                    int eff = castgfx;
                    if (player.isPassive(PassiveID.DESPERADO_ABSOLUTE.toInt())) eff = 17233;
                    if (target != null) target.send_effect(eff);

                } else {
                    if (SkillUseHelper.shouldHideCancellationEffect(skillId, target)) return;
                    sendSkillSoundToSelfAndWorld(player, targetId, castgfx);
                }
            }
            return;
        }

        // =========================================================
        // 2) NPC 시전자
        // =========================================================
        if (user instanceof L1NpcInstance) {

            if (target == null
                    && !(isAttackTarget && hasArea)
                    && !(isNoneTarget && skill.getType() == L1Skills.TYPE_ATTACK)) {
                return;
            }

            final int targetId = (target != null ? target.getId() : 0);

            if (user instanceof L1MerchantInstance) {
                if (SkillUseHelper.shouldHideCancellationEffect(skillId, target)) return;
                SC_SkillSound.broadcastFast(user, targetId, castgfx);
                return;
            }
            if (arrSize == 0 && !isNoneTarget && !(isAttackTarget && hasArea) && !(isAttackTarget && !hasArea && targetId != 0 && skillId != 18)) {
                SC_DoActionGFX.broadcast(user, skill.getActionId());
                return;
            }

            if (isAttackTarget && skillId != L1SkillId.TURN_UNDEAD) {
                if (!hasArea) {
                    if (skillId == 20030 && target != null) target.send_effect(castgfx);
                    SC_UseAttackSkill.broadcast(user, targetId, castgfx, fx, fy, actionId);
                } else {
                    if (arrSize > 0) {
                        SC_RangeSkill.broadcastFast(user, targetsArr, arrSize, castgfx, actionId, SC_RangeSkill.TYPE_DIR);
                    } else {
                        SC_UseAttackSkill.broadcast(user, 0, castgfx, fx, fy, actionId);
                    }
                }
                return;
            }

            if (isNoneTarget && skill.getType() == L1Skills.TYPE_ATTACK) {
                if (arrSize > 0) {
                    SC_RangeSkill.broadcastFast(user, targetsArr, arrSize, castgfx, actionId, SC_RangeSkill.TYPE_NODIR);
                } else {
                    SC_UseAttackSkill.broadcast(user, 0, castgfx, fx, fy, actionId);
                }
                return;
            }

            if (skillId != L1SkillId.TELEPORT && skillId != L1SkillId.MASS_TELEPORT && skillId != L1SkillId.TELEPORT_TO_MATHER) {
                SC_DoActionGFX.broadcast(user, skill.getActionId());

                if (skillId == L1SkillId.OMAN_CANCELLATION || skillId == L1SkillId.ANTA_MESSAGE_1 || skillId == L1SkillId.ANTA_CANCELLATION
                        || skillId == L1SkillId.PAP_PREDICATE7 || skillId == L1SkillId.PAP_PREDICATE11 || skillId == L1SkillId.PAP_PREDICATE12
                        || skillId == L1SkillId.BLACKELDER_DEATH_HELL || skillId == L1SkillId.PHOENIX_CANCELLATION) {
                    return;
                } else {
                    if (SkillUseHelper.shouldHideCancellationEffect(skillId, target)) return;
                    SC_SkillSound.broadcastFast(user, targetId, castgfx);
                }
            }
        }
    }

    // ===== 내부 유틸 =====


    private static void sendEffectToTarget(final L1Character target, final int gfx) {
        if (target == null) return;
        if (target instanceof L1PcInstance) ((L1PcInstance) target).send_effect(gfx);
        else target.send_effect(gfx);
    }

    private static void sendStunSoundToTarget(final L1Character target, final int targetListSize, final boolean sendFailMsg280IfPc) {
        if (target == null) return;

        if (target instanceof L1PcInstance pc) {
            final long ss = SC_SkillSound.pack(pc.getId(), 4434);
            SC_SkillSound.sendPacked(pc, ss);
            SC_SkillSound.broadcastFastPacked(pc, ss);
            if (sendFailMsg280IfPc && targetListSize == 0) {
                SC_ServerMessage.send(pc, 280);
            }
        } else if (target instanceof L1NpcInstance) {
            SC_SkillSound.broadcastFast(target, target.getId(), 4434);
        }
    }

    private static void sendSkillSoundToSelfAndWorld(final L1PcInstance player, final int targetId, final int gfx) {
        if (player == null) return;
        final long ss = SC_SkillSound.pack(targetId, gfx);
        SC_SkillSound.sendPacked(player, ss);
        SC_SkillSound.broadcastFastPacked(player, ss);
    }

    private static boolean isPcSummonPet(final int calcType, final L1Character cha) {
        if (cha == null) return false;

        if (calcType == PC_PC) return true;
        if (calcType != PC_NPC) return false;

        if (cha instanceof L1SummonInstance pet) {
            return pet.isExsistMaster();
        }
        return (cha instanceof L1PetInstance)
                || (cha instanceof L1CompanionInstance)
                || (cha instanceof L1SupportInstance);
    }

    private static int applyCritCastGfx(final int skillId, final int castgfx, final boolean crit) {
        if (!crit) return castgfx;

        switch (skillId) {
        case L1SkillId.CALL_LIGHTNING: return 11737;
        case L1SkillId.SUNBURST:       return 11760;
        case L1SkillId.CONE_OF_COLD:   return 11742;
        case L1SkillId.DISINTEGRATE:   return 11748;
        case L1SkillId.ERUPTION:       return 11754;
        default:                       return castgfx;
        }
    }
}
